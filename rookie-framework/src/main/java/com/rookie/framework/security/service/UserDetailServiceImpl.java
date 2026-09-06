package com.rookie.framework.security.service;

import com.rookie.common.pojo.entity.SysRole;
import com.rookie.framework.security.mapper.UserInfoMapper;
import com.rookie.framework.security.pojo.Permission;
import com.rookie.framework.security.pojo.UserInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class UserDetailServiceImpl implements UserDetailsService {

    @Autowired
    UserInfoMapper userInfoMapper;



    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        //获取用户信息
        UserInfo userInfo = userInfoMapper.selectUserByUsername(username);
        if(userInfo==null) {
            return null;
        }

        //加载用户的权限标识集合，最终转换为 Permission（GrantedAuthority）写入 UserInfo
        // 超级管理员（roleKey == "admin"）走直通兜底：直接加载 sys_menu 中全部按钮权限，
        // 不再依赖 sys_role_menu 逐菜单授权，避免新增菜单后忘记给 admin 授权导致无法访问；
        // 其余角色走"角色 → 已启用角色的 menuId → perm_key"的常规链路。
        ArrayList<String> permKeyById;
        boolean isAdmin;
        try {
            ArrayList<SysRole> roles = userInfoMapper.selectRoleByUserId(userInfo.getUserId());
            //判断当前用户是否拥有超级管理员角色（roleKey == "admin"）
            isAdmin = roles != null && roles.stream()
                    .anyMatch(sysRole -> sysRole.getStatus() != 0 && "admin".equals(sysRole.getRoleKey()));
            if (isAdmin) {
                //admin 直通：加载全部按钮权限，忽略具体角色-菜单授权
                permKeyById = userInfoMapper.selectAllPermKey();
            } else {
                //普通用户：仅取状态为启用的角色，再经 role-menu 关联取出其菜单，最后汇总 perm_key
                List<Long> roleIds = roles.stream()
                        .filter(sysRole -> sysRole.getStatus() != 0)
                        .map(SysRole::getRoleId)
                        .toList();
                ArrayList<Integer> menus = userInfoMapper.selectMenuIdByRoleId(roleIds);
                permKeyById = userInfoMapper.getPermKeyById(menus);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        try {
            //将用户权限的标识符转换为permission对象
            List<Permission> list = permKeyById.stream().filter(Objects::nonNull).map(Permission::new).toList();
            userInfo.setPermissions(list);
            //记录 admin 标记，供自定义鉴权层短路放行所有 @PreAuthorize（不依赖缓存的权限快照）
            userInfo.setAdmin(isAdmin);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        //返回
        return userInfo;
    }
}
