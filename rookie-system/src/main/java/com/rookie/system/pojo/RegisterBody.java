package com.rookie.system.pojo;

/**
 * 用户自助注册请求体。
 * <p>
 * 与 {@link LoginBody} 同级，承载注册接口 {@code POST /register} 的入参：
 * username（必填，≤12 位字母数字下划线）、password（必填，6-20 位）、
 * nickName（可选，空则默认取 username）、phoneNumber（可选，11 位手机号）、
 * sex（可选，空则默认 '0'）。
 * <p>
 * 注册开关由系统设置 {@code sys.user.registerEnabled}（BOOLEAN）控制，
 * 关闭时注册接口直接拒绝；注册成功后自动绑定系统默认角色（sys_role.is_default=1）。
 */
public class RegisterBody {

    /** 用户账号（必填，≤12 位字母数字下划线） */
    private String username;

    /** 用户密码（必填，6-20 位，BCrypt 加密后入库） */
    private String password;

    /** 用户昵称（可选，空则默认取 username） */
    private String nickName;

    /** 手机号（可选，11 位） */
    private String phoneNumber;

    /** 性别（可选，'0' 男 / '1' 女 / '3' 未知，空则默认 '0'） */
    private String sex;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
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

    public String getSex() {
        return sex;
    }

    public void setSex(String sex) {
        this.sex = sex;
    }
}
