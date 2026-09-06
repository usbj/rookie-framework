package com.rookie.system.pojo.vo;

/**
 * 监控数据项（聚合接口返回单元）。
 * <p>
 * 由各 {@code MonitorProvider} 提供：type 标识监控源类型（前端据此选择渲染组件），
 * title 为展示名称，data 为该监控源的实时数据（结构由各提供者自定）。
 */
public class MonitorItemVo {

    /** 监控源类型（如 server / redis / mysql，全局唯一） */
    private String type;

    /** 监控源展示名称（如「服务器」「Redis」） */
    private String title;

    /** 该监控源的实时数据 */
    private Object data;

    public MonitorItemVo() {
    }

    public MonitorItemVo(String type, String title, Object data) {
        this.type = type;
        this.title = title;
        this.data = data;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }
}
