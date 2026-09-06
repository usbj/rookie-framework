package com.rookie.framework.service.impl;

import cn.hutool.core.util.StrUtil;
import com.rookie.common.cache.RedisCache;
import com.rookie.common.exception.ServiceException;
import com.rookie.common.util.SysConfigUtil;
import com.rookie.framework.security.pojo.UserInfo;
import com.rookie.framework.security.service.TokenService;
import com.rookie.framework.service.OnlineUserEntry;
import com.rookie.framework.service.OnlineUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 在线用户统计服务实现（纯 Redis，不落库）。
 * <p>
 * 在线集合 key = {@code rookie:framework:online:users}（BASE_KEY 前缀 + "online:users"），
 * 有序集合语义：member = username，score = 最后活跃时间戳（毫秒）。
 * 在线判定阈值 = 系统设置 {@code sys.online.timeout}（分钟，默认 30，管理员可在系统设置页调整，
 * 无需重启）；统计时先按分数区间剪枝离线成员（防集合无限累积），再按阈值内成员计数/取列表。
 */
@Service
public class OnlineUserServiceImpl implements OnlineUserService {

    /** 在线集合业务 key（真实 key = redis.base-key 前缀 + 本值） */
    private static final String ONLINE_KEY = "online:users";

    /** 在线阈值设置键（sys_config，NUMBER，分钟） */
    private static final String ONLINE_TIMEOUT_CONFIG_KEY = "sys.online.timeout";

    /** 设置项缺失时的默认在线阈值（分钟），与 token 缓存有效期（redis.expire-time）一致 */
    private static final long DEFAULT_ONLINE_TIMEOUT_MINUTES = 30L;

    @Autowired
    RedisCache redisCache;

    @Autowired
    TokenService tokenService;

    @Override
    public void recordActivity(String username) {
        if (StrUtil.isBlank(username)) {
            return;
        }
        // 无条件 ZADD：同一 member 重复写入只更新分数，天然幂等；
        // 已登录请求才可能走到这里，频率受用户操作与前端心跳限制，开销可忽略
        redisCache.zAdd(ONLINE_KEY, username, System.currentTimeMillis());
    }

    @Override
    public long getOnlineCount() {
        long threshold = System.currentTimeMillis() - onlineTimeoutMinutes() * 60_000L;
        pruneOfflineMembers(threshold);
        return redisCache.zCount(ONLINE_KEY, threshold, Double.POSITIVE_INFINITY);
    }

    @Override
    public List<OnlineUserEntry> getOnlineUsers() {
        long threshold = System.currentTimeMillis() - onlineTimeoutMinutes() * 60_000L;
        pruneOfflineMembers(threshold);
        // 只取阈值内的成员（倒序：最近活跃的在前）
        Set<ZSetOperations.TypedTuple<String>> tuples =
                redisCache.zRevRangeWithScores(ONLINE_KEY, 0, -1);

        List<OnlineUserEntry> entries = new ArrayList<>();
        if (tuples == null || tuples.isEmpty()) {
            return entries;
        }
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            String username = tuple.getValue();
            Double lastActive = tuple.getScore();
            if (StrUtil.isBlank(username) || lastActive == null || lastActive < threshold) {
                continue;
            }
            // 展示信息从用户登录态缓存取（昵称/登录IP/登录时间），缓存过期即离线，取不到时留空
            UserInfo userInfo = tokenService.getUserInfoByUsername(username);
            String nickName = userInfo != null ? userInfo.getNickName() : null;
            String loginIp = userInfo != null ? userInfo.getLoginIp() : null;
            Long loginTime = userInfo != null ? userInfo.getLoginTime() : null;
            entries.add(new OnlineUserEntry(username, nickName, loginIp, loginTime, lastActive.longValue()));
        }
        return entries;
    }

    @Override
    public void removeOnline(String username) {
        if (StrUtil.isBlank(username)) {
            return;
        }
        redisCache.zRem(ONLINE_KEY, username);
    }

    @Override
    public void kickOfflineUser(String username, String currentUsername) {
        if (StrUtil.isBlank(username)) {
            throw new ServiceException(500, "账号不能为空");
        }
        // 阻止对自己操作，避免操作人把自己踢下线后无法继续管理
        if (username.equals(currentUsername)) {
            throw new ServiceException(500, "不能强制下线当前登录账号");
        }
        // 1. 从在线集合移除；2. 删除登录态缓存，旧 token 立即失效（下一次请求 401）
        removeOnline(username);
        tokenService.deleteToken(username);
    }

    /**
     * 在线阈值（分钟）：优先读系统设置 sys.online.timeout，缺失或非法时回落默认 30。
     * 设置项经 SysConfigUtil 走 Redis 缓存，管理员在系统设置页修改后立即生效，无需重启。
     */
    private long onlineTimeoutMinutes() {
        Long minutes = SysConfigUtil.getNumber(ONLINE_TIMEOUT_CONFIG_KEY, DEFAULT_ONLINE_TIMEOUT_MINUTES);
        return minutes != null && minutes > 0 ? minutes : DEFAULT_ONLINE_TIMEOUT_MINUTES;
    }

    /**
     * 剪枝：把 score 早于阈值的离线成员从在线集合移除（分数为毫秒时间戳，threshold-1 即阈值前一刻），
     * 防止集合无限累积历史成员。剪枝失败静默忽略（下次统计再试），不影响主流程。
     */
    private void pruneOfflineMembers(long threshold) {
        try {
            redisCache.zRemRangeByScore(ONLINE_KEY, Double.NEGATIVE_INFINITY, threshold - 1);
        } catch (Exception ignore) {
        }
    }
}
