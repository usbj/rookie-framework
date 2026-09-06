package com.rookie.system.pojo.vo;

import com.rookie.common.pojo.entity.SysRole;

import java.util.Date;
import java.util.List;

public class SysUserVo {

    private Long userId;

    private String username;

    private String password;

    private String nickName;

    private String phoneNumber;

    private String sex;

    /** 头像存储名（上传路径 avatar/ 子目录下的文件名；无头像为 null，前端按 null 显示字母占位） */
    private String avatar;

    private Integer status;

    private Date createTime;

    private List<SysRole> userRole;

    private List<Long> roleId;

    public SysUserVo() {
    }

    public SysUserVo(Long userId, String username, String password, String nickName, String phoneNumber, String sex, Integer status, Date createTime, List<SysRole> userRole, List<Long> roleId) {
        this.userId = userId;
        this.username = username;
        this.password = password;
        this.nickName = nickName;
        this.phoneNumber = phoneNumber;
        this.sex = sex;
        this.status = status;
        this.createTime = createTime;
        this.userRole = userRole;
        this.roleId = roleId;
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

    public String getSex() {
        return sex;
    }

    public void setSex(String sex) {
        this.sex = sex;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public List<SysRole> getUserRole() {
        return userRole;
    }

    public void setUserRole(List<SysRole> userRole) {
        this.userRole = userRole;
    }

    public List<Long> getRoleId() {
        return roleId;
    }

    public void setRoleId(List<Long> roleId) {
        this.roleId = roleId;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public String toString() {
        return "SysUserVo{" +
                "userId=" + userId +
                ", username='" + username + '\'' +
                ", nickName='" + nickName + '\'' +
                ", phoneNumber='" + phoneNumber + '\'' +
                ", sex='" + sex + '\'' +
                ", status=" + status +
                ", createTime=" + createTime +
                ", userRole=" + userRole +
                ", roleId=" + roleId +
                '}';
    }
}
