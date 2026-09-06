package com.rookie.system.pojo.quarry;

/**
 * 系统设置列表查询条件。
 * <p>
 * 用于「系统设置」分页查询接口，字段均可空，空值表示不参与过滤。
 */
public class SysConfigQuarry {

    /** 设置键（模糊匹配） */
    private String configKey;

    /** 设置项名称（模糊匹配） */
    private String configName;

    /** 状态：1启用 0停用 */
    private Integer status;

    public SysConfigQuarry() {
    }

    public SysConfigQuarry(String configKey, String configName, Integer status) {
        this.configKey = configKey;
        this.configName = configName;
        this.status = status;
    }

    public String getConfigKey() {
        return configKey;
    }

    public void setConfigKey(String configKey) {
        this.configKey = configKey;
    }

    public String getConfigName() {
        return configName;
    }

    public void setConfigName(String configName) {
        this.configName = configName;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "SysConfigQuarry{" +
                "configKey='" + configKey + '\'' +
                ", configName='" + configName + '\'' +
                ", status=" + status +
                '}';
    }
}
