package com.rookie.system.pojo.vo;

/**
 * 通知指定成员回显用 VO。
 * userId 对应 sys_notice_user_rel.user_id；username/nickName/phoneNumber/status
 * 由后端按 user_id 关联 sys_user 查询时填充，供编辑弹窗回显已选成员的展示信息。
 * status 为 sys_user.status（0停用 1启用），保留以便前端在已选成员里标识停用账号。
 */
public class NoticeTargetUserVo {

    private Long userId;

    private String username;

    private String nickName;

    private String phoneNumber;

    private Integer status;

    public NoticeTargetUserVo() {
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
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

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}