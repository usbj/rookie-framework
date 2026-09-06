package com.rookie.system.pojo;

/**
 * 修改个人密码请求体。
 * <p>
 * 承载 {@code PUT /person/password} 的入参：oldPassword（原密码，BCrypt matches 校验）、
 * newPassword（新密码，6-20 位，BCrypt 加密后落库）。
 * 修改密码与资料编辑（{@code PUT /person}）完全分离，不混入 editUserInfo 白名单。
 */
public class ModifyPasswordBody {

    /** 原密码（必填，用于校验当前登录用户身份） */
    private String oldPassword;

    /** 新密码（必填，6-20 位） */
    private String newPassword;

    public String getOldPassword() {
        return oldPassword;
    }

    public void setOldPassword(String oldPassword) {
        this.oldPassword = oldPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
