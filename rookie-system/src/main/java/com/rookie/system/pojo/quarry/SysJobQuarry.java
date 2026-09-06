package com.rookie.system.pojo.quarry;

/**
 * 定时任务列表查询条件。
 * <p>
 * 用于「定时任务」分页查询接口，字段均可空，空值表示不参与过滤。
 */
public class SysJobQuarry {

    /** 任务名称（模糊匹配） */
    private String jobName;

    /** 状态：1启用 0停用 */
    private Integer status;

    /** 创建时间范围起（yyyy-MM-dd，与表 create_time 比较） */
    private String beginTime;

    /** 创建时间范围止（yyyy-MM-dd） */
    private String endTime;

    public SysJobQuarry() {
    }

    public String getJobName() {
        return jobName;
    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
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
