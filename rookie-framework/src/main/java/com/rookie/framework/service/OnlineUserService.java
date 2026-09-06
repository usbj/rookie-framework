package com.rookie.framework.service;

import java.util.List;

/**
 * 在线用户统计服务。
 * <p>
 * 无状态 JWT 体系没有服务端 session，"在线"由 Redis 在线集合（ZSET）判定：
 * <ul>
 *   <li>member = username，score = 最后活跃时间戳（毫秒）；</li>
 *   <li>每次已登录请求经 {@code TokenVerifyFilter} 校验通过后写入活跃时间；</li>
 *   <li>score 距今超过在线阈值（系统设置 {@code sys.online.timeout}，分钟，默认 30）
 *       视为离线，统计时按分数区间实时计算，无需定时清理。</li>
 * </ul>
 * 阈值与用户登录态缓存（TokenService 维护）有效期保持一致，未活跃用户的
 * 登录态缓存过期后自然无法再通过校验，与在线判定口径一致。
 */
public interface OnlineUserService {

    /**
     * 记录用户活跃时间（写入在线集合）。调用方：TokenVerifyFilter 校验通过后。
     *
     * @param username 登录账号
     */
    void recordActivity(String username);

    /**
     * 当前在线人数：在线集合中最后活跃时间在阈值内的成员数。
     *
     * @return 在线人数
     */
    long getOnlineCount();

    /**
     * 在线用户列表（按最后活跃时间倒序），并附带昵称/登录IP/登录时间等展示信息。
     *
     * @return 在线用户展示条目
     */
    List<OnlineUserEntry> getOnlineUsers();

    /**
     * 从在线集合移除用户（退出登录用，不做任何限制）。
     *
     * @param username 登录账号
     */
    void removeOnline(String username);

    /**
     * 强制下线：从在线集合移除，并删除该用户登录态缓存（旧 token 立即失效）。
     *
     * @param username        被强制下线的登录账号
     * @param currentUsername 当前操作人（用于阻止对自己操作）
     */
    void kickOfflineUser(String username, String currentUsername);
}
