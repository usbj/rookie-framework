package com.rookie.system.scheduler;

import cn.hutool.core.exceptions.ExceptionUtil;
import cn.hutool.core.util.StrUtil;
import com.rookie.common.exception.ServiceException;
import com.rookie.common.pojo.entity.SysJob;
import com.rookie.common.pojo.entity.SysJobLog;
import com.rookie.common.util.SpringUtil;
import com.rookie.system.mapper.SysJobLogMapper;
import com.rookie.system.mapper.SysJobMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 定时任务调度器（方案 A：Spring TaskScheduler 动态注册，不落调度框架）。
 * <p>
 * 机制：
 * <ul>
 *   <li>任务元数据在 sys_job 表，本组件启动时（ApplicationRunner）把启用任务按 CronTrigger
 *       注册进独立线程池（job-scheduler-，5 线程）；</li>
 *   <li>增删改/启停由 Service 层在落库后调用 {@link #registerJob} / {@link #cancelJob} 动态调整；</li>
 *   <li>每次执行（自动或手动）记 sys_job_log：耗时/结果/异常；</li>
 *   <li>防重：同一任务执行中再次触发直接跳过（ConcurrentHashMap + AtomicBoolean，单机语义）。</li>
 * </ul>
 * 安全约束：
 * <ul>
 *   <li>调用目标限定 {@code task.bean-package-prefixes} 配置项所列前缀包下的 Spring Bean（白名单前缀校验，
 *       默认 {@code com.rookie.system.task}；二开项目可追加自身任务包前缀），
 *       任务方法须为 public、无参或单个 String 参数（参数来自 sys_job.params）；</li>
 *   <li>cron 合法性由 Service 层在保存前用 CronExpression 校验，本组件仅按已入库配置调度。</li>
 * </ul>
 * 边界：单机调度。多实例部署时任务会在每个实例重复执行，需另行引入分布式锁或任务平台。
 */
@Component
public class SysJobScheduler implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SysJobScheduler.class);

    /** 任务 Bean 白名单包前缀列表：只有这些前缀下的 Spring Bean 允许被调度（反射调用安全边界）。
     *  由配置项 {@code task.bean-package-prefixes} 提供，默认 {@code com.rookie.system.task}；
     *  二开项目可追加自身任务包前缀。 */
    @Value("${task.bean-package-prefixes:com.rookie.system.task}")
    private List<String> taskBeanPackagePrefixes;

    /** 异常信息落库最大长度（sys_job_log.exception_msg varchar(2000)） */
    private static final int MAX_EXCEPTION_MSG_LENGTH = 2000;

    @Autowired
    SysJobMapper sysJobMapper;

    @Autowired
    SysJobLogMapper sysJobLogMapper;

    /** 动态调度线程池（独立于 Spring 默认调度器） */
    private final ThreadPoolTaskScheduler taskScheduler;

    /** jobId → 已注册的调度任务句柄（用于取消未触发的调度） */
    private final Map<Long, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    /** jobId → 执行中标志（防重：执行中再次触发直接跳过） */
    private final Map<Long, AtomicBoolean> runningFlags = new ConcurrentHashMap<>();

    public SysJobScheduler() {
        taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(5);
        taskScheduler.setThreadNamePrefix("job-scheduler-");
        taskScheduler.setWaitForTasksToCompleteOnShutdown(true);
        taskScheduler.initialize();
    }

    /**
     * 启动时注册全部启用任务（Spring 上下文就绪后执行）。
     * 注册失败不阻断启动，仅记录错误日志，可重启恢复。
     */
    @Override
    public void run(ApplicationArguments args) {
        try {
            List<SysJob> enabledJobs = sysJobMapper.getAllEnabledJobs();
            if (enabledJobs == null || enabledJobs.isEmpty()) {
                log.info("定时任务启动注册：无启用任务");
                return;
            }
            for (SysJob job : enabledJobs) {
                try {
                    registerJob(job);
                } catch (Exception e) {
                    log.error("定时任务启动注册失败，jobId={}, name={} >>> {}", job.getJobId(), job.getJobName(), e.getMessage());
                }
            }
            log.info("定时任务启动注册完成，共 {} 个启用任务", enabledJobs.size());
        } catch (Exception e) {
            log.error("定时任务启动注册失败，可通过重启恢复", e);
        }
    }

    /**
     * 注册任务调度（先取消旧调度再按新配置注册；停用任务只取消不注册）。
     * 由 Service 层在新增/编辑/启停落库后调用，保证数据库与调度器状态一致。
     *
     * @param job 任务（status=1 时注册，status=0 时仅取消）
     */
    public void registerJob(SysJob job) {
        cancelJob(job.getJobId());
        if (job.getStatus() == null || job.getStatus() != 1) {
            return;
        }
        CronTrigger trigger = new CronTrigger(job.getCronExpression());
        ScheduledFuture<?> task = taskScheduler.schedule(() -> executeJob(job, "AUTO"), trigger);
        scheduledTasks.put(job.getJobId(), task);
        log.info("定时任务已注册，jobId={}, name={}, cron={}", job.getJobId(), job.getJobName(), job.getCronExpression());
    }

    /**
     * 取消任务调度并清理执行中标志（删除/停用/重新注册时调用，幂等）。
     *
     * @param jobId 任务主键
     */
    public void cancelJob(Long jobId) {
        ScheduledFuture<?> task = scheduledTasks.remove(jobId);
        if (task != null) {
            // cancel(false)：只取消未触发的调度，不中断正在执行的任务
            task.cancel(false);
        }
        runningFlags.remove(jobId);
    }

    /**
     * 立即执行一次（手动触发，不走 cron 调度）。
     * 若任务正处于执行中（自动触发尚未结束），本次手动触发会被防重拦截并记录日志。
     *
     * @param job 任务
     */
    public void runOnce(SysJob job) {
        executeJob(job, "MANUAL");
    }

    /**
     * 执行任务并记录执行日志（自动/手动共用）：
     * 防重检查 → 反射调用（白名单包 + 无参/String 单参）→ 记日志（耗时/结果/异常）。
     * 任务执行结果只落 sys_job_log，不向上抛出（调度线程不应被业务异常中断）。
     */
    private void executeJob(SysJob job, String triggerType) {
        AtomicBoolean flag = runningFlags.computeIfAbsent(job.getJobId(), k -> new AtomicBoolean(false));
        if (!flag.compareAndSet(false, true)) {
            saveLog(job, triggerType, 1, null, "任务正在执行中，本次触发跳过");
            return;
        }
        long start = System.currentTimeMillis();
        try {
            invoke(job);
            saveLog(job, triggerType, 0, System.currentTimeMillis() - start, null);
        } catch (Throwable e) {
            log.error("定时任务执行失败，jobId={}, invokeTarget={}.{} >>> {}",
                    job.getJobId(), job.getBeanName(), job.getMethodName(), e.getMessage());
            saveLog(job, triggerType, 1, System.currentTimeMillis() - start, ExceptionUtil.getMessage(e));
        } finally {
            flag.set(false);
        }
    }

    /**
     * 校验任务配置合法性（Service 层新增/编辑保存前调用，配置即拦截，避免执行时才报错）：
     * cron 表达式合法（Spring 6 段式）→ Bean 存在且在白名单包 → 方法存在（String 单参或无参）。
     *
     * @param job 待校验任务
     */
    public void validateJobConfig(SysJob job) {
        String cron = job.getCronExpression();
        if (StrUtil.isBlank(cron)) {
            throw new ServiceException(500, "cron 表达式不能为空");
        }
        try {
            CronExpression.parse(cron);
        } catch (IllegalArgumentException e) {
            throw new ServiceException(500, "cron 表达式不合法：" + cron);
        }
        if (StrUtil.isBlank(job.getBeanName()) || StrUtil.isBlank(job.getMethodName())) {
            throw new ServiceException(500, "任务 Bean 名称与方法名称不能为空");
        }
        Object bean;
        try {
            bean = SpringUtil.getBean(job.getBeanName());
        } catch (BeansException e) {
            throw new ServiceException(500, "任务 Bean 不存在：" + job.getBeanName());
        }
        // 与执行期同一安全边界：只允许调度白名单包（task.bean-package-prefixes）下的 Bean
        String beanClassName = bean.getClass().getName();
        if (taskBeanPackagePrefixes.stream().noneMatch(p -> beanClassName.startsWith(p))) {
            throw new ServiceException(500, "任务目标 Bean 不在白名单包内（" + taskBeanPackagePrefixes + "）");
        }
        if (findTaskMethod(bean.getClass(), job.getMethodName()) == null) {
            throw new ServiceException(500, "任务方法不存在：" + job.getBeanName() + "." + job.getMethodName()
                    + "（须为 public，无参或单个 String 参数）");
        }
    }

    /**
     * 反射调用任务目标：白名单包校验 → 优先 String 单参方法，其次无参方法 → 执行。
     * 业务异常经 InvocationTargetException 解包后原样上抛，由 executeJob 记录。
     */
    private void invoke(SysJob job) throws Throwable {
        Object bean;
        try {
            bean = SpringUtil.getBean(job.getBeanName());
        } catch (BeansException e) {
            throw new ServiceException(500, "任务 Bean 不存在：" + job.getBeanName());
        }
        // 安全边界：只允许调度白名单包（task.bean-package-prefixes）下的 Bean，防止任意 Bean 方法被反射调用
        String beanClassName = bean.getClass().getName();
        if (taskBeanPackagePrefixes.stream().noneMatch(p -> beanClassName.startsWith(p))) {
            throw new ServiceException(500, "任务目标 Bean 不在白名单包内（" + taskBeanPackagePrefixes + "）");
        }
        Method method = findTaskMethod(bean.getClass(), job.getMethodName());
        if (method == null) {
            throw new ServiceException(500, "任务方法不存在：" + job.getBeanName() + "." + job.getMethodName()
                    + "（须为 public，无参或单个 String 参数）");
        }
        try {
            if (method.getParameterCount() == 1) {
                method.invoke(bean, job.getParams());
            } else {
                method.invoke(bean);
            }
        } catch (InvocationTargetException e) {
            // 反射调用包装了真实业务异常，解包后上抛，保证日志记录的是原始原因
            throw e.getCause() != null ? e.getCause() : e;
        }
    }

    /**
     * 查找任务方法：先找 String 单参方法，再找无参方法。
     * 仅匹配 public 方法（getMethod 语义），与安全约束一致。
     */
    private Method findTaskMethod(Class<?> beanClass, String methodName) {
        try {
            return beanClass.getMethod(methodName, String.class);
        } catch (NoSuchMethodException e) {
            try {
                return beanClass.getMethod(methodName);
            } catch (NoSuchMethodException e2) {
                return null;
            }
        }
    }

    /**
     * 记录执行日志（AUTO/MANUAL 共用）。日志落库失败绝不影响任务执行，仅告警。
     */
    private void saveLog(SysJob job, String triggerType, Integer status, Long costTime, String exceptionMsg) {
        try {
            SysJobLog jobLog = new SysJobLog();
            jobLog.setJobId(job.getJobId());
            jobLog.setJobName(job.getJobName());
            jobLog.setTriggerType(triggerType);
            jobLog.setInvokeTarget(job.getBeanName() + "." + job.getMethodName());
            jobLog.setJobParams(job.getParams());
            jobLog.setStatus(status);
            jobLog.setCostTime(costTime);
            jobLog.setExceptionMsg(StrUtil.maxLength(exceptionMsg, MAX_EXCEPTION_MSG_LENGTH));
            jobLog.setExecuteTime(new Date());
            sysJobLogMapper.addSysJobLog(jobLog);
        } catch (Exception e) {
            log.warn("定时任务执行日志落库失败，jobId={} >>> {}", job.getJobId(), e.getMessage());
        }
    }
}
