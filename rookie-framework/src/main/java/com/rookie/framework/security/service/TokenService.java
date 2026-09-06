package com.rookie.framework.security.service;

import cn.hutool.json.JSONUtil;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.rookie.common.cache.RedisCache;
import com.rookie.framework.security.pojo.UserInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;



/**
 * TODO：user需要设置专门的key，生成临时ID组成
 * */
@Component
public class TokenService {

    @Value("${redis.expire-time}")
    private String EXPIRE;

    @Autowired
    RedisCache redisCache;

    private final Long MIN_DIFFERENCE=10*60*1000L;


    public String createJwt(UserInfo userInfo) {
        //设置创建时间
        userInfo.setLoginTime(System.currentTimeMillis());
        userInfo.setExpireTime(System.currentTimeMillis()+Long.parseLong(EXPIRE)*60*1000L);
        //缓存
        redisCache.setCacheToSetTime(userInfo.getUsername(), JSONUtil.toJsonStr(userInfo));
        JWTCreator.Builder builder = JWT.create();
        return builder.withClaim("username",userInfo.getUsername())
                .sign(Algorithm.HMAC256("rookie:"+userInfo.getPassword()));

    }

    public void jwtVerification(UserInfo userInfo) {
        if (userInfo.getExpireTime()-System.currentTimeMillis()<=MIN_DIFFERENCE){
            userInfo.setExpireTime(System.currentTimeMillis()+Long.parseLong(EXPIRE)*60*1000L);
            redisCache.setCacheToSetTime(userInfo.getUsername(), JSONUtil.toJsonStr(userInfo));
        }

    }

    public String getUsernameByJwt(String token){
        try {
            return JWT.decode(token).getClaim("username").asString();
        } catch (JWTDecodeException e) {
            return null;
        }
    }

    public UserInfo getUserInfoByToken(String token) {
//        System.out.println(token.isEmpty());
        if (token==null|| token.isEmpty()) {
            return null;
        }
        return redisCache.getObjectCache(getUsernameByJwt(token), UserInfo.class);
    }

    /**
     * 按登录账号直接读取登录态缓存（不经过 JWT 解码）。
     * 供在线用户列表等场景读取展示信息；缓存不存在（未登录/已过期/被下线）返回 null。
     *
     * @param username 登录账号（缓存 key）
     * @return 缓存中的 UserInfo；不存在返回 null
     */
    public UserInfo getUserInfoByUsername(String username) {
        if (username == null || username.isEmpty()) {
            return null;
        }
        return redisCache.getObjectCache(username, UserInfo.class);
    }

    /**
     * 删除指定用户的登录态缓存（退出登录 / 强制下线用）。
     * 删除后该用户携带旧 token 的下一次请求将无法通过 TokenVerifyFilter 校验（401 → 前端跳登录）。
     *
     * @param username 登录账号（缓存 key）
     */
    public void deleteToken(String username) {
        if (username == null || username.isEmpty()) {
            return;
        }
        redisCache.deleteCache(username);
    }

}
