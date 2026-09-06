package com.rookie.common.pojo.entity;

import com.rookie.common.pojo.BaseEntity;

import java.util.Date;

public class SysMenu extends BaseEntity {

    private Long menuId;

    private String menuName;

    private String permKey;

    private Long parentId;

    private Integer menuType;

    private String route;

    private Integer backlinks;

    private String path;

    private String icon;

    /** 排序号（越小越靠前，同一父级内生效；相同则按 menu_id 兜底） */
    private Integer sort;

    private Integer status;

    private Integer delete;

    public SysMenu() {
    }

    public SysMenu(Date createTime, Date updateTime, String createBy, String updateBy, Long menuId, String menuName, String permKey, Long parentId, Integer menuType, String route, Integer backlinks, String path, String icon, Integer status, Integer delete) {
        super(createTime, updateTime, createBy, updateBy);
        this.menuId = menuId;
        this.menuName = menuName;
        this.permKey = permKey;
        this.parentId = parentId;
        this.menuType = menuType;
        this.route = route;
        this.backlinks = backlinks;
        this.path = path;
        this.icon = icon;
        this.status = status;
        this.delete = delete;
    }

    public Long getMenuId() {
        return menuId;
    }

    public void setMenuId(Long menuId) {
        this.menuId = menuId;
    }

    public String getMenuName() {
        return menuName;
    }

    public void setMenuName(String menuName) {
        this.menuName = menuName;
    }

    public String getPermKey() {
        return permKey;
    }

    public void setPermKey(String permKey) {
        this.permKey = permKey;
    }

    public Integer getMenuType() {
        return menuType;
    }

    public void setMenuType(Integer menuType) {
        this.menuType = menuType;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public Integer getSort() {
        return sort;
    }

    public void setSort(Integer sort) {
        this.sort = sort;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getDelete() {
        return delete;
    }

    public void setDelete(Integer delete) {
        this.delete = delete;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public String getRoute() {
        return route;
    }

    public void setRoute(String route) {
        this.route = route;
    }

    public Integer getBacklinks() {
        return backlinks;
    }

    public void setBacklinks(Integer backlinks) {
        this.backlinks = backlinks;
    }

    @Override
    public String toString() {
        return "SysMenu{" +
                "menuId=" + menuId +
                ", menuName='" + menuName + '\'' +
                ", permKey='" + permKey + '\'' +
                ", parentId=" + parentId +
                ", menuType=" + menuType +
                ", route='" + route + '\'' +
                ", backlinks=" + backlinks +
                ", path='" + path + '\'' +
                ", icon='" + icon + '\'' +
                ", status=" + status +
                ", delete=" + delete +
                '}';
    }
}
