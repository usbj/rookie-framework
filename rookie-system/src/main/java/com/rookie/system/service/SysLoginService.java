package com.rookie.system.service;

import com.rookie.common.pojo.Result;
import com.rookie.system.pojo.LoginBody;
import com.rookie.system.pojo.ModifyPasswordBody;
import com.rookie.system.pojo.RegisterBody;
import com.rookie.system.pojo.vo.SysMenuVo;
import com.rookie.system.pojo.vo.SysUserVo;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;


public interface SysLoginService {

    String loginVerification(LoginBody loginBody);

    /**
     * 退出登录：从在线集合移除并删除登录态缓存，旧 token 立即失效（幂等）。
     *
     * @param username 当前登录用户名
     * @return 是否退出成功
     */
    Boolean logout(String username);

    /**
     * 用户自助注册。
     * <p>
     * 受系统设置 {@code sys.user.registerEnabled}（BOOLEAN）开关控制，关闭时直接拒绝；
     * 注册成功后自动绑定系统默认角色（sys_role.is_default=1），注册即启用（status=1），
     * 不自动登录，由前端引导跳转登录页。
     *
     * @param registerBody 注册请求体（username/password 必填，nickName/phoneNumber/sex 可选）
     * @return 是否注册成功
     */
    Boolean register(RegisterBody registerBody);

    /**
     * 修改当前用户密码（独立于资料编辑）。
     * <p>
     * 校验原密码（BCrypt matches）通过后加密落库；不更新其他任何资料字段。
     *
     * @param userId 当前登录用户主键
     * @param body   修改密码请求体（oldPassword / newPassword）
     * @return 是否修改成功
     */
    Boolean modifyPersonalPassword(Long userId, ModifyPasswordBody body);

    Boolean modifyPersonalDetails(SysUserVo sysUserVo);

    SysUserVo getPersonalDetails(Long userId);

    Boolean resetSysUserPassword(Long userId,String password);

    /**
     * 上传当前用户头像：校验图片类型与大小 → 存入上传路径 avatar/ 子目录 →
     * 更新 sys_user.avatar → 清理旧头像文件。头像存储名仅落用户表，不落文件表。
     *
     * @param userId 当前登录用户主键
     * @param file   头像图片（png/jpg/jpeg/gif/webp，≤2MB）
     * @return 新头像存储名
     */
    String uploadPersonalAvatar(Long userId, MultipartFile file);

    /**
     * 读取当前用户头像图片流（inline 响应），供前端 blob 加载展示（&lt;img&gt; 无法携带 Token 请求头）。
     *
     * @param userId 当前登录用户主键
     * @return 头像图片流响应；未设置头像或文件缺失时抛 {@link com.rookie.common.exception.ServiceException}
     */
    ResponseEntity<Resource> getPersonalAvatar(Long userId);

    List<SysMenuVo> getUserMenuTreeByUserId(Long userId);
}
