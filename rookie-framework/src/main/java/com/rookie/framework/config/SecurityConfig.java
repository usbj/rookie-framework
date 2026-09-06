package com.rookie.framework.config;


import com.rookie.framework.security.expression.AdminBypassMethodSecurityExpressionHandler;
import com.rookie.framework.security.filter.TokenVerifyFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.RegexRequestMatcher;

@Configuration
//下面两个注解允许security进行配置的安全行为
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Autowired
    AccessDeniedHandler accessDeniedHandler;

    @Autowired
    AuthenticationEntryPoint authenticationEntryPoint;

    @Autowired
    TokenVerifyFilter tokenVerifyFilter;

    /**
     * anyRequest          |   匹配所有请求路径
     * access              |   SpringEl表达式结果为true时可以访问
     * anonymous           |   匿名可以访问
     * denyAll             |   用户不能访问
     * fullyAuthenticated  |   用户完全认证可以访问（非remember-me下自动登录）
     * hasAnyAuthority     |   如果有参数，参数表示权限，则其中任何一个权限可以访问
     * hasAnyRole          |   如果有参数，参数表示角色，则其中任何一个角色可以访问
     * hasAuthority        |   如果有参数，参数表示权限，则其权限可以访问
     * hasIpAddress        |   如果有参数，参数表示IP地址，如果用户IP和参数匹配，则可以访问
     * hasRole             |   如果有参数，参数表示角色，则其角色可以访问
     * permitAll           |   用户可以任意访问
     * rememberMe          |   允许通过remember-me登录的用户访问
     * authenticated       |   用户登录后可访问
     */

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session->session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorization -> authorization
                        .requestMatchers("/hello").authenticated()
                        .requestMatchers("/login").permitAll()
                        // 用户自助注册：注册接口与注册开关查询接口允许游客访问
                        // （注册开关查询走专用接口，不放宽系统设置按 key 读取，避免游客任意读取系统设置）
                        .requestMatchers("/register", "/register/enabled").permitAll()
                        // 公告详情公开访问（门户/游客场景）：仅数字 ID 详情路径放行，
                        // /my、/list、/read、/confirm 仍要求认证。
                        // 用 RegexRequestMatcher 精确限定，避免 MvcRequestMatcher 对 {var:regex} 匹配行为不确定。
                        .requestMatchers(RegexRequestMatcher.regexMatcher(HttpMethod.GET, "^/sys/notice/\\d+$")).permitAll()
                        //swagger相关配置
                        .requestMatchers("/doc.html/**").permitAll()
                        .requestMatchers("/swagger-ui.html/**").permitAll()
                        .requestMatchers("/*/api-docs/**").permitAll()
                        .requestMatchers("/swagger-resources/**").permitAll()
                        .requestMatchers("/webjars/**").permitAll()
                        //其他接口 ps:必须要加，要不然登陆了也访问不了
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exception->exception
                    .accessDeniedHandler(accessDeniedHandler)
                    .authenticationEntryPoint(authenticationEntryPoint)
                )
                .addFilterBefore(tokenVerifyFilter,
                        UsernamePasswordAuthenticationFilter.class
                );
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder(){
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManagerBean(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    /**
     * 注册自定义方法级安全表达式处理器，让超级管理员（admin）短路放行所有 @PreAuthorize。
     * <p>
     * Spring Security 6 的 {@code @EnableMethodSecurity} 检测到容器中存在自定义
     * {@link MethodSecurityExpressionHandler} Bean 时，自动用它替换默认实现，无需额外配置。
     * 效果：admin 命中任意 {@code hasAuthority/hasAnyAuthority/hasRole/hasAnyRole} 直接返回 true，
     * 不再依赖缓存的权限快照——新建菜单/权限后无需更新缓存、无需给 admin 角色授权即可访问。
     */
    @Bean
    public MethodSecurityExpressionHandler methodSecurityExpressionHandler() {
        return new AdminBypassMethodSecurityExpressionHandler();
    }
}
