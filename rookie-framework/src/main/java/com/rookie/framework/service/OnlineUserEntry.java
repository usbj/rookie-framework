package com.rookie.framework.service;

/**
 * 在线用户展示条目（在线统计只读模型，不落库）。
 * <p>
 * 数据来源：Redis 在线集合（ZSET，member=username，score=最后活跃时间戳）
 * + 用户登录态缓存（UserInfo JSON，含昵称/登录IP/登录时间）。
 */
public class OnlineUserEntry {

    /** 登录账号（在线集合成员） */
    private String username;

    /** 昵称（来自登录态缓存） */
    private String nickName;

    /** 登录 IP（来自登录态缓存，无则空） */
    private String loginIp;

    /** 登录时间（毫秒时间戳，来自登录态缓存） */
    private Long loginTime;

    /** 最后活跃时间（毫秒时间戳，来自在线集合分数） */
    private Long lastActive;

    public OnlineUserEntry() {
    }

    public OnlineUserEntry(String username, String nickName, String loginIp, Long loginTime, Long lastActive) {
        this.username = username;
        this.nickName = nickName;
        this.loginIp = loginIp;
        this.loginTime = loginTime;
        this.lastActive = lastActive;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getNickName() {
        return nickName;
    }

    public void setNickName(String nickName) {
        this.nickName = nickName;
    }

    public String getLoginIp() {
        return loginIp;
    }

    public void setLoginIp(String loginIp) {
        this.loginIp = loginIp;
    }

    public Long getLoginTime() {
        return loginTime;
    }

    public void setLoginTime(Long loginTime) {
        this.loginTime = loginTime;
    }

    public Long getLastActive() {
        return lastActive;
    }

    public void setLastActive(Long lastActive) {
        this.lastActive = lastActive;
    }
}
