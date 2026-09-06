package com.rookie.common.pojo.entity;

import com.rookie.common.pojo.BaseEntity;

import java.util.Date;

/**
 * 定时任务实体。
 * <p>
 * 任务元数据落库，由调度器（SysJobScheduler）动态注册到 Spring TaskScheduler：
 * 启用任务启动即注册（CronTrigger 驱动），增删改/启停时动态注册或取消。
 * 调用目标为 Spring Bean 方法（beanName + methodName，限定 com.rookie.system.task 包，
 * 无参或单个 String 参数），cron 为 Spring 6 段式表达式。
 */
public class SysJob extends BaseEntity {

    /** 任务主键 */
    private Long jobId;

    /** 任务名称 */
    private String jobName;

    /** Spring Bean 名称（限定 com.rookie.system.task 包下） */
    private String beanName;

    /** 执行方法名（无参或单个 String 参数） */
    private String methodName;

    /** cron 表达式（Spring 6 段式） */
    private String cronExpression;

    /** 执行参数（可选；方法为 String 单参时传入） */
    private String params;

    /** 状态：1启用 0停用 */
    private Integer status;

    /** 备注说明 */
    private String remark;

    public SysJob() {
    }

    public SysJob(Date createTime, Date updateTime, String createBy, String updateBy,
                  Long jobId, String jobName, String beanName, String methodName,
                  String cronExpression, String params, Integer status, String remark) {
        super(createTime, updateTime, createBy, updateBy);
        this.jobId = jobId;
        this.jobName = jobName;
        this.beanName = beanName;
        this.methodName = methodName;
        this.cronExpression = cronExpression;
        this.params = params;
        this.status = status;
        this.remark = remark;
    }

    public Long getJobId() {
        return jobId;
    }

    public void setJobId(Long jobId) {
        this.jobId = jobId;
    }

    public String getJobName() {
        return jobName;
    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    public String getBeanName() {
        return beanName;
    }

    public void setBeanName(String beanName) {
        this.beanName = beanName;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public String getCronExpression() {
        return cronExpression;
    }

    public void setCronExpression(String cronExpression) {
        this.cronExpression = cronExpression;
    }

    public String getParams() {
        return params;
    }

    public void setParams(String params) {
        this.params = params;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
