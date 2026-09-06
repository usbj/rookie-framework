package com.rookie.system.pojo.quarry;

/**
 * 定时任务执行日志查询条件。
 * <p>
 * 用于「执行日志」分页查询接口，字段均可空，空值表示不参与过滤。
 */
public class SysJobLogQuarry {

    /** 任务主键（精确匹配，从任务页跳转时带） */
    private Long jobId;

    /** 任务名称（模糊匹配） */
    private String jobName;

    /** 触发方式：AUTO 自动 MANUAL 手动 */
    private String triggerType;

    /** 执行状态：0成功 1失败 */
    private Integer status;

    /** 执行时间范围起（yyyy-MM-dd，与表 execute_time 比较） */
    private String beginTime;

    /** 执行时间范围止（yyyy-MM-dd） */
    private String endTime;

    public SysJobLogQuarry() {
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

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getBeginTime() {
        return beginTime;
    }

    public void setBeginTime(String beginTime) {
        this.beginTime = beginTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }
}
