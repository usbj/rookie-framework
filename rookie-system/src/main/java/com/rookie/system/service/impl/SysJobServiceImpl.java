package com.rookie.system.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.github.pagehelper.PageInfo;
import com.rookie.common.exception.ServiceException;
import com.rookie.common.pojo.entity.SysJob;
import com.rookie.common.pojo.entity.SysJobLog;
import com.rookie.common.util.PageUtil;
import com.rookie.framework.security.pojo.UserInfo;
import com.rookie.system.mapper.SysJobLogMapper;
import com.rookie.system.mapper.SysJobMapper;
import com.rookie.system.pojo.quarry.SysJobLogQuarry;
import com.rookie.system.pojo.quarry.SysJobQuarry;
import com.rookie.system.pojo.vo.SysJobVo;
import com.rookie.system.scheduler.SysJobScheduler;
import com.rookie.system.service.SysJobService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 定时任务服务实现。
 * <p>
 * 写操作（增/改/删/启停）先落库再同步调度器，保证数据库与调度状态一致：
 * 新增/编辑/启用 → {@link SysJobScheduler#registerJob}（内部先取消旧调度）；
 * 停用/删除 → {@link SysJobScheduler#cancelJob}。
 * 保存前统一 {@link SysJobScheduler#validateJobConfig} 校验 cron / Bean 白名单 / 方法存在性。
 */
@Service
public class SysJobServiceImpl implements SysJobService {

    @Autowired
    SysJobMapper sysJobMapper;

    @Autowired
    SysJobLogMapper sysJobLogMapper;

    @Autowired
    SysJobScheduler sysJobScheduler;

    @Override
    public PageInfo<SysJobVo> quarrySysJob(SysJobQuarry quarry) {
        PageUtil.startPage();
        List<SysJob> jobs = sysJobMapper.quarrySysJob(quarry);
        PageInfo<SysJob> jobPageInfo = PageUtil.packagedPageInfo(jobs);
        PageInfo<SysJobVo> voPageInfo = PageUtil.copyPageInfo(jobPageInfo, SysJobVo.class);
        voPageInfo.setList(toVoList(jobs));
        return voPageInfo;
    }

    @Override
    public SysJobVo getSysJobById(Long jobId) {
        SysJob job = sysJobMapper.getSysJobById(jobId);
        if (job == null) {
            throw new ServiceException(500, "任务不存在");
        }
        return toVo(job);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean addSysJob(SysJobVo jobVo) {
        SysJob job = toEntity(jobVo);
        // 配置即校验：cron / Bean 白名单 / 方法存在性，非法直接拒绝，避免执行时才报错
        sysJobScheduler.validateJobConfig(job);
        UserInfo userInfo = currentUserInfo();
        job.setCreateBy(userInfo.getUsername());
        job.setUpdateBy(userInfo.getUsername());
        Boolean ok = sysJobMapper.addSysJob(job);
        if (Boolean.TRUE.equals(ok)) {
            // 落库成功后同步调度（status=1 注册，status=0 仅取消——新增停用任务不调度）
            sysJobScheduler.registerJob(job);
        }
        return ok;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean editSysJob(SysJobVo jobVo) {
        if (jobVo.getJobId() == null || sysJobMapper.getSysJobById(jobVo.getJobId()) == null) {
            throw new ServiceException(500, "任务不存在");
        }
        SysJob job = toEntity(jobVo);
        sysJobScheduler.validateJobConfig(job);
        UserInfo userInfo = currentUserInfo();
        job.setUpdateBy(userInfo.getUsername());
        Boolean ok = sysJobMapper.editSysJob(job);
        if (Boolean.TRUE.equals(ok)) {
            // 编辑后重新注册（registerJob 内部先取消旧调度；停用则只取消）
            sysJobScheduler.registerJob(job);
        }
        return ok;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteSysJobById(Long jobId) {
        SysJob existed = sysJobMapper.getSysJobById(jobId);
        if (existed == null) {
            throw new ServiceException(500, "任务不存在");
        }
        // 先取消调度再删库，避免删除期间任务仍被触发
        sysJobScheduler.cancelJob(jobId);
        Boolean ok = sysJobMapper.deleteSysJobById(jobId);
        if (Boolean.TRUE.equals(ok)) {
            // 级联清理该任务的执行日志（日志保留无意义，且避免无主日志堆积）
            sysJobLogMapper.deleteSysJobLogByJobId(jobId);
        }
        return ok;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean changeSysJobStatus(Long jobId, Integer status) {
        SysJob existed = sysJobMapper.getSysJobById(jobId);
        if (existed == null) {
            throw new ServiceException(500, "任务不存在");
        }
        if (status == null || (status != 0 && status != 1)) {
            throw new ServiceException(500, "任务状态不合法");
        }
        Boolean ok = sysJobMapper.changeSysJobStatus(jobId, status);
        if (Boolean.TRUE.equals(ok)) {
            // 状态变更后同步调度：启用注册（基于最新配置，需把实体状态置 1，registerJob 按它判断）、停用取消
            if (status == 1) {
                existed.setStatus(1);
                sysJobScheduler.registerJob(existed);
            } else {
                sysJobScheduler.cancelJob(jobId);
            }
        }
        return ok;
    }

    @Override
    public Boolean runSysJob(Long jobId) {
        SysJob job = sysJobMapper.getSysJobById(jobId);
        if (job == null) {
            throw new ServiceException(500, "任务不存在");
        }
        // 立即执行一次（MANUAL）；若正处于执行中，调度器防重会拦截并记录日志
        sysJobScheduler.runOnce(job);
        return true;
    }

    @Override
    public PageInfo<SysJobLog> quarrySysJobLog(SysJobLogQuarry quarry) {
        PageUtil.startPage();
        List<SysJobLog> jobLogs = sysJobLogMapper.quarrySysJobLog(quarry);
        return PageUtil.packagedPageInfo(jobLogs);
    }

    /**
     * 从 SecurityContext 取当前登录用户信息，用于填充审计字段。
     */
    private UserInfo currentUserInfo() {
        return (UserInfo) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    private SysJob toEntity(SysJobVo jobVo) {
        return BeanUtil.toBean(jobVo, SysJob.class);
    }

    private SysJobVo toVo(SysJob job) {
        return BeanUtil.toBean(job, SysJobVo.class);
    }

    private List<SysJobVo> toVoList(List<SysJob> jobs) {
        List<SysJobVo> jobVos = new ArrayList<>();
        if (jobs == null || jobs.isEmpty()) {
            return jobVos;
        }
        for (SysJob job : jobs) {
            jobVos.add(toVo(job));
        }
        return jobVos;
    }
}
