package com.rookie.framework.security.pojo;


import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class UserInfo implements UserDetails {
    private Long userId;

    private String username;

    private String password;

    private String nickName;

    private Integer status;

    private Long loginTime;

    private Long expireTime;

    /** 登录 IP（登录时从请求头解析，随 UserInfo 缓存进 Redis，供在线用户列表展示） */
    private String loginIp;

    private List<Permission> permissions;

    /**
     * 是否超级管理员标记（角色 roleKey == "admin" 时为 true）。
     * <p>
     * 由 {@code UserDetailServiceImpl} 在加载用户时设置，随 UserInfo 一起缓存进 Redis。
     * 鉴权层（自定义 MethodSecurityExpressionHandler）据此短路放行所有 @PreAuthorize，
     * 让 admin 不依赖缓存的权限快照即可访问任意权限，新建菜单/权限后无需更新缓存、无需改角色授权。
     * 该标记只随 admin 角色的授予/撤销变化，不随菜单增删变化，故无缓存陈旧问题。
     */
    private boolean admin;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getNickName() {
        return nickName;
    }

    public void setNickName(String nickName) {
        this.nickName = nickName;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Long getLoginTime() {
        return loginTime;
    }

    public void setLoginTime(Long loginTime) {
        this.loginTime = loginTime;
    }

    public Long getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(Long expireTime) {
        this.expireTime = expireTime;
    }

    public String getLoginIp() {
        return loginIp;
    }

    public void setLoginIp(String loginIp) {
        this.loginIp = loginIp;
    }

    public List<Permission> getPermissions() {
        return permissions;
    }

    public void setPermissions(List<Permission> permissions) {
        this.permissions = permissions;
    }

    public boolean isAdmin() {
        return admin;
    }

    public void setAdmin(boolean admin) {
        this.admin = admin;
    }

    @Override
    public String toString() {
        return "UserInfo{" +
                "userId=" + userId +
                ", username='" + username + '\'' +
                ", password='" + password + '\'' +
                ", nickName='" + nickName + '\'' +
                ", status=" + status +
                ", admin=" + admin +
                ", loginIp='" + loginIp + '\'' +
                ", permissions=" + permissions +
                '}';
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return permissions;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isEnabled() {
        return status == 1;
    }
}
