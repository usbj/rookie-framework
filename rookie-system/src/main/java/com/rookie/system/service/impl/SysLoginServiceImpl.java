package com.rookie.system.service.impl;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.servlet.JakartaServletUtil;
import com.rookie.common.enums.ResultEnum;
import com.rookie.common.exception.ServiceException;
import com.rookie.common.pojo.Result;
import com.rookie.common.pojo.entity.SysRole;
import com.rookie.common.pojo.entity.SysUser;
import com.rookie.common.util.ServletUtil;
import com.rookie.common.util.SysConfigUtil;
import com.rookie.framework.security.mapper.UserInfoMapper;
import com.rookie.framework.security.service.TokenService;
import com.rookie.framework.security.pojo.UserInfo;
import com.rookie.framework.service.OnlineUserService;
import com.rookie.system.mapper.SysRoleMapper;
import com.rookie.system.mapper.SysUserMapper;
import com.rookie.system.mapper.SysUserRoleMapper;
import com.rookie.system.pojo.LoginBody;
import com.rookie.system.pojo.ModifyPasswordBody;
import com.rookie.system.pojo.RegisterBody;
import com.rookie.system.pojo.SysUserRole;
import com.rookie.system.pojo.vo.FileUploadVo;
import com.rookie.system.pojo.vo.SysMenuVo;
import com.rookie.system.pojo.vo.SysUserVo;
import com.rookie.system.service.SysFileService;
import com.rookie.system.service.SysLoginService;
import com.rookie.system.service.SysMenuService;
import com.rookie.system.service.SysRoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


@Service
public class SysLoginServiceImpl implements SysLoginService {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    TokenService tokenService;

    @Autowired
    SysUserMapper sysUserMapper;

    @Autowired
    UserInfoMapper userInfoMapper;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    SysRoleService sysRoleService;

    @Autowired
    SysMenuService sysMenuService;

    @Autowired
    SysRoleMapper sysRoleMapper;

    @Autowired
    SysUserRoleMapper sysUserRoleMapper;

    @Autowired
    SysFileService sysFileService;

    @Autowired
    OnlineUserService onlineUserService;



    @Override
    public String loginVerification(LoginBody loginBody) {
        //根据用户输入的账号密码来获取验证以及身份信息
        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken
                        (loginBody.getUsername(),loginBody.getPassword());
        Authentication authenticate = authenticationManager.authenticate(authenticationToken);
        if (authenticate == null){
            throw new ServiceException(ResultEnum.COMMON_ERROR);
        }
        //获取用户信息
        UserInfo userInfo = (UserInfo) authenticate.getPrincipal();
        // 记录登录 IP（随 UserInfo 缓存，供在线用户列表展示；解析失败时留空不影响登录）
        try {
            userInfo.setLoginIp(JakartaServletUtil.getClientIP(ServletUtil.getRequest()));
        } catch (Exception ignore) {
        }
        //生成JWT（createJwt 内部会把 UserInfo 写入 Redis 登录态缓存）
        String token = tokenService.createJwt(userInfo);
        return token;
    }

    /**
     * 方法效果：
     * 退出登录：从在线集合移除并删除登录态缓存，旧 token 立即失效。
     * 参数：
     * - `username`：当前登录用户名。
     * 返回值：
     * - 退出成功返回 true。
     * 副作用：
     * - 在线集合移除 + 删除 Redis 登录态缓存；重复退出/缓存已不存在时幂等。
     */
    @Override
    public Boolean logout(String username) {
        if (StrUtil.isBlank(username)) {
            return true;
        }
        onlineUserService.removeOnline(username);
        tokenService.deleteToken(username);
        return true;
    }

    /**
     * 方法效果：
     * 用户自助注册：开关兜底校验 → 参数校验 → 唯一性校验 → 加密落库（sys_user）→ 绑定默认角色（sys_user_role）。
     * 参数：
     * - `registerBody`：注册请求体（username/password 必填，nickName/phoneNumber/sex 可选）。
     * 返回值：
     * - 注册成功返回 true。
     * 副作用：
     * - 新增用户记录与默认角色关联（同一事务）；注册即启用（status=1），不自动登录。
     */
    @Override
    @Transactional
    public Boolean register(RegisterBody registerBody) {
        // 1. 注册开关兜底：系统设置 sys.user.registerEnabled（BOOLEAN）未开启直接拒绝，防绕过前端入口
        if (!SysConfigUtil.getBoolean("sys.user.registerEnabled", false)) {
            throw new ServiceException(500, "注册功能未开放");
        }

        // 2. 参数校验（username 对齐 sys_user.username varchar(12)，nick_name varchar(18) NOT NULL）
        String username = registerBody.getUsername() == null ? "" : registerBody.getUsername().trim();
        if (username.isEmpty()) {
            throw new ServiceException(500, "用户名不能为空");
        }
        if (username.length() > 12) {
            throw new ServiceException(500, "用户名不能超过12个字符");
        }
        if (!username.matches("^[a-zA-Z0-9_]+$")) {
            throw new ServiceException(500, "用户名只能包含字母、数字和下划线");
        }
        String password = registerBody.getPassword();
        if (password == null || password.length() < 6 || password.length() > 20) {
            throw new ServiceException(500, "密码长度需为6-20位");
        }
        String phoneNumber = registerBody.getPhoneNumber();
        if (phoneNumber != null && !phoneNumber.trim().isEmpty()) {
            phoneNumber = phoneNumber.trim();
            if (!phoneNumber.matches("^1\\d{10}$")) {
                throw new ServiceException(500, "手机号格式不正确");
            }
        } else {
            phoneNumber = null;
        }
        String nickName = registerBody.getNickName() == null ? "" : registerBody.getNickName().trim();
        if (nickName.isEmpty()) {
            nickName = username;
        }
        String sex = registerBody.getSex() == null ? "" : registerBody.getSex().trim();
        if (sex.isEmpty()) {
            sex = "0";
        }

        // 3. 唯一性校验（sys_user 表 username/phone_number 均为唯一键，先查后插）
        if (Boolean.TRUE.equals(sysUserMapper.usernameIsExistOrNot(username))) {
            throw new ServiceException(500, "用户名已存在");
        }
        if (phoneNumber != null && Boolean.TRUE.equals(sysUserMapper.phoneIsExistOrNot(phoneNumber))) {
            throw new ServiceException(500, "手机号已存在");
        }

        // 4. 组装并落库：密码 BCrypt 加密（与 resetSysUserPassword 同源）；注册场景无登录态，
        //    createBy/updateBy 用用户名，status 走表默认 1（注册即启用）
        SysUser sysUser = new SysUser();
        sysUser.setUsername(username);
        sysUser.setPassword(passwordEncoder.encode(password));
        sysUser.setNickName(nickName);
        sysUser.setPhoneNumber(phoneNumber);
        sysUser.setSex(sex);
        sysUser.setCreateBy(username);
        sysUser.setUpdateBy(username);
        try {
            sysUserMapper.addSysUser(sysUser);
        } catch (Exception e) {
            throw new ServiceException(500, "注册失败", e.getMessage());
        }

        // 5. 绑定系统默认角色（sys_role.is_default=1），不硬编码角色 id
        SysRole defaultRole = sysRoleMapper.getDefaultRole();
        if (defaultRole == null) {
            throw new ServiceException(500, "系统未配置默认角色，请联系管理员");
        }
        List<SysUserRole> userRoles = new ArrayList<>();
        userRoles.add(new SysUserRole(sysUser.getUserId(), defaultRole.getRoleId()));
        try {
            sysUserRoleMapper.addUserRoleInfo(userRoles);
        } catch (Exception e) {
            throw new ServiceException(500, "注册失败", e.getMessage());
        }

        return true;
    }


    @Override
    public Boolean modifyPersonalDetails(SysUserVo sysUserVo) {
        SysUser sysUser = BeanUtil.toBean(sysUserVo, SysUser.class);
        // update_by 为 NOT NULL 列，editUserInfo 固定更新该列：必须显式填充当前登录用户名，
        // 否则 SQL 写入 null 违反非空约束导致"用户信息更改失败"
        UserInfo userInfo = (UserInfo) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        sysUser.setUpdateBy(userInfo.getUsername());
        try {
            sysUserMapper.editUserInfo(sysUser);
        } catch (Exception e) {
            throw new ServiceException(500,"用户信息更改失败");
        }
        return true;
    }

    /**
     * 方法效果：
     * 修改当前用户密码：校验原密码（BCrypt matches）后，复用 resetSysUserPassword 加密落库。
     * 与资料编辑（PUT /person）完全分离，不混入 editUserInfo 白名单。
     * 参数：
     * - `userId`：当前登录用户主键。
     * - `body`：修改密码请求体（oldPassword / newPassword）。
     * 返回值：
     * - 修改成功返回 true。
     * 副作用：
     * - 更新 sys_user.password；不自动登出（旧 token 有效期不变，下次登录用新密码）。
     */
    @Override
    public Boolean modifyPersonalPassword(Long userId, ModifyPasswordBody body) {
        String oldPassword = body.getOldPassword();
        String newPassword = body.getNewPassword();
        if (oldPassword == null || oldPassword.isEmpty()) {
            throw new ServiceException(500, "原密码不能为空");
        }
        if (newPassword == null || newPassword.length() < 6 || newPassword.length() > 20) {
            throw new ServiceException(500, "新密码长度需为6-20位");
        }
        SysUser sysUser = sysUserMapper.getSysUserInfoById(userId);
        if (sysUser == null || sysUser.getPassword() == null) {
            throw new ServiceException(500, "用户不存在");
        }
        if (!passwordEncoder.matches(oldPassword, sysUser.getPassword())) {
            throw new ServiceException(500, "原密码错误");
        }
        return resetSysUserPassword(userId, newPassword);
    }

    @Override
    public SysUserVo getPersonalDetails(Long userId) {
        SysUser sysUser = sysUserMapper.getSysUserInfoById(userId);
        SysUserVo userVo = BeanUtil.toBean(sysUser, SysUserVo.class);
        return userVo;
    }

    /**
     * 方法效果：
     * 上传当前用户头像：文件存储（类型/大小校验）→ 更新 sys_user.avatar → 清理旧头像文件。
     * 参数：
     * - `userId`：当前登录用户主键。
     * - `file`：头像图片（png/jpg/jpeg/gif/webp，≤2MB）。
     * 返回值：
     * - 新头像存储名。
     * 副作用：
     * - 头像文件落在上传根路径 avatar/ 子目录（不落文件表）；avatar 列更新失败时回滚删除刚上传的文件；
     *   旧头像文件删除失败仅告警，不影响主流程。
     */
    @Override
    public String uploadPersonalAvatar(Long userId, MultipartFile file) {
        SysUser sysUser = sysUserMapper.getSysUserInfoById(userId);
        if (sysUser == null) {
            throw new ServiceException(500, "用户不存在");
        }
        // 1. 存储新头像（校验失败抛 ServiceException，不会产生残留文件）
        FileUploadVo uploadVo = sysFileService.uploadAvatar(file);
        String newAvatar = uploadVo.getStoredName();

        // 2. 更新用户表头像列（update_by 为 NOT NULL 列，必须显式填充当前登录用户名）
        SysUser update = new SysUser();
        update.setUserId(userId);
        update.setAvatar(newAvatar);
        update.setUpdateBy(currentUsername());
        Boolean ok = sysUserMapper.updateSysUserAvatar(update);
        if (!Boolean.TRUE.equals(ok)) {
            // 更新失败回滚：删掉刚上传的新文件，避免残留孤儿文件
            sysFileService.deleteAvatar(newAvatar);
            throw new ServiceException(500, "头像更新失败");
        }

        // 3. 清理旧头像文件（幂等；失败不影响主流程，仅告警）
        String oldAvatar = sysUser.getAvatar();
        if (StrUtil.isNotBlank(oldAvatar) && !oldAvatar.equals(newAvatar)) {
            sysFileService.deleteAvatar(oldAvatar);
        }
        return newAvatar;
    }

    /**
     * 方法效果：
     * 读取当前用户头像图片流（inline 响应）。
     * 参数：
     * - `userId`：当前登录用户主键。
     * 返回值：
     * - 头像图片响应；未设置头像或存储文件缺失时抛 ServiceException。
     */
    @Override
    public ResponseEntity<Resource> getPersonalAvatar(Long userId) {
        SysUser sysUser = sysUserMapper.getSysUserInfoById(userId);
        if (sysUser == null) {
            throw new ServiceException(500, "用户不存在");
        }
        if (StrUtil.isBlank(sysUser.getAvatar())) {
            throw new ServiceException(500, "尚未设置头像");
        }
        return sysFileService.downloadAvatar(sysUser.getAvatar());
    }

    /**
     * 从 SecurityContext 取当前登录用户名，供头像更新填充 update_by。
     */
    private String currentUsername() {
        UserInfo userInfo = (UserInfo) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return userInfo.getUsername();
    }

    @Override
    public Boolean resetSysUserPassword(Long userId, String password) {
        SysUser sysUser = new SysUser();
        sysUser.setPassword(password);
        sysUser.setUserId(userId);
        sysUser.setPassword(passwordEncoder.encode(sysUser.getPassword()));
        try {
            sysUserMapper.resetSysUserPassword(sysUser);
        } catch (Exception e) {
            throw new ServiceException(500,"密码重置失败");
        }
        return true;
    }

    @Override
    public List<SysMenuVo> getUserMenuTreeByUserId(Long userId) {
        List<SysRole> sysRoles = sysRoleService.getSysRoleByUserId(userId);
        if (sysRoles == null || sysRoles.isEmpty()) {
            return new ArrayList<>();
        }
        // 超级管理员（roleKey == "admin"，状态启用）直通兜底：直接返回全部启用菜单（目录/菜单/按钮），
        // 不再依赖 role_menu 逐菜单授权，避免新增菜单后忘记给 admin 角色授权导致前端看不到目录/菜单/按钮。
        boolean isAdmin = sysRoles.stream()
                .anyMatch(role -> role.getStatus() != null && role.getStatus() != 0 && "admin".equals(role.getRoleKey()));
        List<SysMenuVo> sysMenuVos = isAdmin
                ? sysMenuService.getSysMenuAllEnabled()
                : sysMenuService.getSysMenuByRoleList(sysRoles);
        if (sysMenuVos == null || sysMenuVos.isEmpty()) {
            return new ArrayList<>();
        }
        return buildMenuTree(sysMenuVos);
    }

    private List<SysMenuVo> buildMenuTree(List<SysMenuVo> sysMenuVos) {
        HashMap<Long,SysMenuVo> hashMap = new HashMap<>();
        for (SysMenuVo sysMenuVo : sysMenuVos) {
            if(sysMenuVo.getSonMenus()==null){
                sysMenuVo.setSonMenus(new ArrayList<>());
            }
            hashMap.put(sysMenuVo.getMenuId(),sysMenuVo);
        }
        List<SysMenuVo> menuVos = new ArrayList<>();
        for (SysMenuVo sysMenuVo : sysMenuVos) {
            Long id = sysMenuVo.getParentId();
            if (id == null || id == -1L){
                menuVos.add(sysMenuVo);
                continue;
            }
            SysMenuVo parentMenu = hashMap.get(sysMenuVo.getParentId());
            if (parentMenu != null) {
                parentMenu.getSonMenus().add(sysMenuVo);
            } else {
                // 父节点不在当前结果集（如按名称/状态过滤时父节点被滤掉）：作为顶级节点保留，
                // 避免整棵子树被静默丢弃，导致前端列表"数据消失"
                menuVos.add(sysMenuVo);
            }
        }
        return menuVos;
    }
}
