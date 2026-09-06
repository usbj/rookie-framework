package com.rookie.common.pojo.entity;

import java.util.Date;

/**
 * 定时任务执行日志实体。
 * <p>
 * 每次任务执行（自动触发或手动执行）记录一条：触发方式、调用目标、耗时、结果与异常信息。
 * 任务删除后日志仍保留（jobName/invokeTarget/jobParams 冗余），便于事后排查。
 */
public class SysJobLog {

    /** 日志主键 */
    private Long logId;

    /** 任务主键 */
    private Long jobId;

    /** 任务名称（冗余，任务删除后日志仍可读） */
    private String jobName;

    /** 触发方式：AUTO 自动 MANUAL 手动 */
    private String triggerType;

    /** 调用目标（bean.method） */
    private String invokeTarget;

    /** 执行参数（冗余） */
    private String jobParams;

    /** 执行状态：0成功 1失败 */
    private Integer status;

    /** 耗时（毫秒） */
    private Long costTime;

    /** 异常信息（失败时） */
    private String exceptionMsg;

    /** 执行时间 */
    private Date executeTime;

    public SysJobLog() {
    }

    public Long getLogId() {
        return logId;
    }

    public void setLogId(Long logId) {
        this.logId = logId;
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

    public String getTriggerType() {
        return triggerType;
    }

    public void setTriggerType(String triggerType) {
        this.triggerType = triggerType;
    }

    public String getInvokeTarget() {
        return invokeTarget;
    }

    public void setInvokeTarget(String invokeTarget) {
        this.invokeTarget = invokeTarget;
    }

    public String getJobParams() {
        return jobParams;
    }

    public void setJobParams(String jobParams) {
        this.jobParams = jobParams;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Long getCostTime() {
        return costTime;
    }

    public void setCostTime(Long costTime) {
        this.costTime = costTime;
    }

    public String getExceptionMsg() {
        return exceptionMsg;
    }

    public void setExceptionMsg(String exceptionMsg) {
        this.exceptionMsg = exceptionMsg;
    }

    public Date getExecuteTime() {
        return executeTime;
    }

    public void setExecuteTime(Date executeTime) {
        this.executeTime = executeTime;
    }
}
