package com.rookie.framework.security.expression;

import com.rookie.framework.security.pojo.UserInfo;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.expression.EvaluationContext;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionOperations;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.function.Supplier;

/**
 * admin 短路鉴权表达式处理器。
 * <p>
 * 注册为 {@code MethodSecurityExpressionHandler} Bean 后，{@code @EnableMethodSecurity} 自动用它替换默认实现。
 * 重写 public {@code createEvaluationContext(Supplier, MethodInvocation)}（Spring Security 6.5 实际走的入口）：
 * <ul>
 *   <li>admin 用户：用默认 root 作为委托，包成 {@link AdminBypassExpressionRoot}，使 {@code hasAuthority} 等短路返回 true。</li>
 *   <li>非 admin 用户：直接委托 {@code super.createEvaluationContext(...)}，走 Spring Security 原生鉴权，行为零变化。</li>
 * </ul>
 * 不修改任何现有 {@code @PreAuthorize} 注解即可生效。
 * <p>
 * 注意：6.5 里 {@code @PreAuthorize} 走 {@code createEvaluationContext(Supplier, MethodInvocation)}（public），
 * 内部调 private 的 {@code createSecurityExpressionRoot(Supplier, MethodInvocation)}，会绕过 protected 的同名 hook，
 * 因此必须在 public 入口处拦截，而非重写 protected 的 createSecurityExpressionRoot。
 */
public class AdminBypassMethodSecurityExpressionHandler extends DefaultMethodSecurityExpressionHandler {

    @Override
    public EvaluationContext createEvaluationContext(
            Supplier<Authentication> authentication, MethodInvocation invocation) {
        Authentication auth = authentication != null ? authentication.get() : null;
        // 兜底：Supplier 为 null 或尚未装配时从上下文取，保持与 TokenVerifyFilter 写入的认证态一致
        if (auth == null) {
            auth = SecurityContextHolder.getContext().getAuthentication();
        }
        if (isAdmin(auth)) {
            // admin：用默认 root 作委托，包一层短路 root，再装入公共 StandardEvaluationContext。
            // 不用 package-private 的 MethodSecurityEvaluationContext（外部包不可见）；
            // StandardEvaluationContext 设 root + beanResolver 即可支撑 hasAuthority 等 SpEL 求值。
            MethodSecurityExpressionOperations delegate = createSecurityExpressionRoot(auth, invocation);
            AdminBypassExpressionRoot bypassRoot = new AdminBypassExpressionRoot(auth, delegate);
            StandardEvaluationContext context = new StandardEvaluationContext(bypassRoot);
            context.setBeanResolver(getBeanResolver());
            return context;
        }
        // 非 admin：完整走 Spring Security 默认鉴权
        return super.createEvaluationContext(authentication, invocation);
    }

    /**
     * 判断当前认证主体是否超级管理员。principal 即 TokenVerifyFilter 写入的 UserInfo。
     */
    private boolean isAdmin(Authentication auth) {
        if (auth == null) {
            return false;
        }
        Object principal = auth.getPrincipal();
        return principal instanceof UserInfo userInfo && userInfo.isAdmin();
    }
}