package com.rookie.framework.security.filter;

import com.rookie.common.cache.RedisCache;
import com.rookie.framework.security.service.TokenService;
import com.rookie.framework.security.pojo.UserInfo;
import com.rookie.framework.service.OnlineUserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * TODO:验证成功后增加token时长，可以尝试双token
 * */

@Component
public class TokenVerifyFilter extends OncePerRequestFilter {

    @Autowired
    TokenService tokenService;

    @Autowired
    RedisCache redisCache;

    @Autowired
    OnlineUserService onlineUserService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        UserInfo userInfo = tokenService.getUserInfoByToken(request.getHeader("Token"));
        /**
         * TODO:【文档说明】userInfo!=null在前的原因
         * */
        if (userInfo!=null && userInfo.getExpireTime()!=null && userInfo.getUserId()!=null && userInfo.getUsername()!=null  ) {
            tokenService.jwtVerification(userInfo);
            UsernamePasswordAuthenticationToken authenticationToken =
                    new UsernamePasswordAuthenticationToken(userInfo,null,userInfo.getAuthorities());
            authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authenticationToken);
            // 在线统计：校验通过即视为活跃，写入在线集合（内部容错，绝不干扰鉴权主流程）
            try {
                onlineUserService.recordActivity(userInfo.getUsername());
            } catch (Exception ignore) {
                // 在线统计失败不影响请求继续（Redis 抖动时降级为不计入在线）
            }
        }
        filterChain.doFilter(request,response);
    }
}
