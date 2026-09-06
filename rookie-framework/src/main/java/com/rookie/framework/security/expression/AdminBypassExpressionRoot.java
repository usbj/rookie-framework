package com.rookie.framework.security.expression;

import com.rookie.framework.security.pojo.UserInfo;
import org.springframework.security.access.expression.method.MethodSecurityExpressionOperations;
import org.springframework.security.core.Authentication;

/**
 * admin 短路鉴权 root：仅用于超级管理员命中的 {@code @PreAuthorize} 表达式求值。
 * <p>
 * 设计：admin 命中时，{@code hasAuthority} / {@code hasAnyAuthority} / {@code hasRole} / {@code hasAnyRole}
 * 直接返回 true（新建权限无需更新缓存、无需给 admin 角色授权即可访问）；其余方法（{@code permitAll} /
 * {@code isAuthenticated} / {@code hasPermission} / filter/return object 等）委托给默认 root
 * （由 {@code DefaultMethodSecurityExpressionHandler#createSecurityExpressionRoot} 创建的 {@code MethodSecurityExpressionRoot}），
 * 保留 Spring Security 原生语义，不重复实现易错的 ROLE_ 前缀、PermissionEvaluator 等逻辑。
 * <p>
 * 非_admin 用户不走本类（{@link AdminBypassMethodSecurityExpressionHandler} 非 admin 时直接委托给 super），
 * 故本类只承担 admin 短路职责，最小且安全。
 */
public class AdminBypassExpressionRoot implements MethodSecurityExpressionOperations {

    private final Authentication authentication;
    private final MethodSecurityExpressionOperations delegate;

    public AdminBypassExpressionRoot(Authentication authentication, MethodSecurityExpressionOperations delegate) {
        this.authentication = authentication;
        this.delegate = delegate;
    }

    /**
     * admin 短路：超级管理员的权限判定一律通过，不再比对缓存的权限快照。
     */
    private boolean isAdmin() {
        Object principal = authentication == null ? null : authentication.getPrincipal();
        return principal instanceof UserInfo userInfo && userInfo.isAdmin();
    }

    @Override
    public boolean hasAuthority(String authority) {
        return isAdmin() || (delegate != null && delegate.hasAuthority(authority));
    }

    @Override
    public boolean hasAnyAuthority(String... authorities) {
        return isAdmin() || (delegate != null && delegate.hasAnyAuthority(authorities));
    }

    @Override
    public boolean hasRole(String role) {
        return isAdmin() || (delegate != null && delegate.hasRole(role));
    }

    @Override
    public boolean hasAnyRole(String... roles) {
        return isAdmin() || (delegate != null && delegate.hasAnyRole(roles));
    }

    // ==================== 其余方法转发默认 root，保留原生语义 ====================

    @Override
    public Authentication getAuthentication() {
        return delegate != null ? delegate.getAuthentication() : authentication;
    }

    @Override
    public boolean permitAll() {
        return delegate != null && delegate.permitAll();
    }

    @Override
    public boolean denyAll() {
        return delegate != null && delegate.denyAll();
    }

    @Override
    public boolean isAnonymous() {
        return delegate != null && delegate.isAnonymous();
    }

    @Override
    public boolean isAuthenticated() {
        return delegate != null && delegate.isAuthenticated();
    }

    @Override
    public boolean isRememberMe() {
        return delegate != null && delegate.isRememberMe();
    }

    @Override
    public boolean isFullyAuthenticated() {
        return delegate != null && delegate.isFullyAuthenticated();
    }

    @Override
    public boolean hasPermission(Object target, Object permission) {
        return isAdmin() || (delegate != null && delegate.hasPermission(target, permission));
    }

    @Override
    public boolean hasPermission(Object targetId, String permissionType, Object permission) {
        return isAdmin() || (delegate != null && delegate.hasPermission(targetId, permissionType, permission));
    }

    @Override
    public void setFilterObject(Object filterObject) {
        if (delegate != null) {
            delegate.setFilterObject(filterObject);
        }
    }

    @Override
    public Object getFilterObject() {
        return delegate != null ? delegate.getFilterObject() : null;
    }

    @Override
    public void setReturnObject(Object returnObject) {
        if (delegate != null) {
            delegate.setReturnObject(returnObject);
        }
    }

    @Override
    public Object getReturnObject() {
        return delegate != null ? delegate.getReturnObject() : null;
    }

    @Override
    public Object getThis() {
        return delegate != null ? delegate.getThis() : null;
    }
}