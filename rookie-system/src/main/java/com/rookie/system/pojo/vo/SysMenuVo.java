package com.rookie.system.pojo.vo;

import java.util.Date;
import java.util.List;

public class SysMenuVo {

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

    private List<SysMenuVo> sonMenus;

    private Integer status;

    private Date createTime;

    public SysMenuVo() {
    }

    public SysMenuVo(Long menuId, String menuName, String permKey, Long parentId, Integer menuType, String route, Integer backlinks, String path, String icon, List<SysMenuVo> sonMenus, Integer status, Date createTime) {
        this.menuId = menuId;
        this.menuName = menuName;
        this.permKey = permKey;
        this.parentId = parentId;
        this.menuType = menuType;
        this.route = route;
        this.backlinks = backlinks;
        this.path = path;
        this.icon = icon;
        this.sonMenus = sonMenus;
        this.status = status;
        this.createTime = createTime;
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

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public Integer getMenuType() {
        return menuType;
    }

    public void setMenuType(Integer menuType) {
        this.menuType = menuType;
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

    public List<SysMenuVo> getSonMenus() {
        return sonMenus;
    }

    public void setSonMenus(List<SysMenuVo> sonMenus) {
        this.sonMenus = sonMenus;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    @Override
    public String toString() {
        return "SysMenuVo{" +
                "menuId=" + menuId +
                ", menuName='" + menuName + '\'' +
                ", permKey='" + permKey + '\'' +
                ", parentId=" + parentId +
                ", menuType=" + menuType +
                ", route='" + route + '\'' +
                ", backlinks=" + backlinks +
                ", path='" + path + '\'' +
                ", icon='" + icon + '\'' +
                ", sonMenus=" + sonMenus +
                ", status=" + status +
                ", createTime=" + createTime +
                '}';
    }
}
