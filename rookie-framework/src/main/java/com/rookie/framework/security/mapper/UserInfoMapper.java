package com.rookie.framework.security.mapper;

import com.rookie.common.pojo.entity.SysRole;
import com.rookie.framework.security.pojo.UserInfo;
import org.apache.ibatis.annotations.Mapper;

import java.util.ArrayList;
import java.util.List;


@Mapper
public interface UserInfoMapper {

    UserInfo selectUserByUsername(String username);

    ArrayList<SysRole> selectRoleByUserId(Long userId);

    ArrayList<Integer> selectMenuIdByRoleId(List<Long> roles);

    ArrayList<String> getPermKeyById(ArrayList<Integer> menus);

    /**
     * 查询全部启用且未删除的按钮型权限标识（不限前缀，任意模块的按钮权限都会加载），供 admin 直通兜底加载。
     *
     * @return 全部按钮权限 perm_key 列表；无结果时返回空集合
     */
    ArrayList<String> selectAllPermKey();

}
