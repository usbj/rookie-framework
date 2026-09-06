package com.rookie.common.pojo.entity;

import com.rookie.common.pojo.BaseEntity;

import java.util.Date;

/**
 * 系统设置实体（键值型）。
 * <p>
 * 一个设置项对应一个值，值的解释方式由 {@link #valueType} 决定
 * （STRING 字符串 / BOOLEAN 布尔 / NUMBER 数值 / JSON 对象或数组）。
 * 数据库为唯一数据源，Redis 作永久缓存副本，工具类 {@code SysConfigUtil} 只读缓存。
 * <p>
 * {@code isSystem}=1 表示系统内置项，业务上禁止删除、禁止修改 configKey 与 valueType，
 * 仅允许修改 configValue / configName / remark / status，避免误删代码硬依赖项导致功能异常。
 */
public class SysConfig extends BaseEntity {

    /** 设置项主键 */
    private Long configId;

    /** 设置键（业务唯一，推荐使用「模块.子项.用途」点号分层，如 sys.user.initPassword） */
    private String configKey;

    /** 设置项名称（展示用） */
    private String configName;

    /** 设置值（按 valueType 解释；JSON 类型时存 JSON 字符串） */
    private String configValue;

    /** 值类型：STRING / BOOLEAN / NUMBER / JSON */
    private String valueType;

    /** 是否系统内置：0否 1是（内置项不可删、key 与类型不可改，仅可改值） */
    private Integer isSystem;

    /** 备注说明 */
    private String remark;

    /** 状态：1启用 0停用（停用后工具类读取回落默认值） */
    private Integer status;

    public SysConfig() {
    }

    public SysConfig(Date createTime, Date updateTime, String createBy, String updateBy,
                     Long configId, String configKey, String configName, String configValue,
                     String valueType, Integer isSystem, String remark, Integer status) {
        super(createTime, updateTime, createBy, updateBy);
        this.configId = configId;
        this.configKey = configKey;
        this.configName = configName;
        this.configValue = configValue;
        this.valueType = valueType;
        this.isSystem = isSystem;
        this.remark = remark;
        this.status = status;
    }

    public Long getConfigId() {
        return configId;
    }

    public void setConfigId(Long configId) {
        this.configId = configId;
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

    public String getConfigValue() {
        return configValue;
    }

    public void setConfigValue(String configValue) {
        this.configValue = configValue;
    }

    public String getValueType() {
        return valueType;
    }

    public void setValueType(String valueType) {
        this.valueType = valueType;
    }

    public Integer getIsSystem() {
        return isSystem;
    }

    public void setIsSystem(Integer isSystem) {
        this.isSystem = isSystem;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}
