package com.rookie.system.service;

import com.rookie.common.pojo.Result;
import com.rookie.common.pojo.entity.SysRole;
import com.rookie.system.pojo.quarry.MenuQuarry;
import com.rookie.system.pojo.vo.SysMenuVo;

import java.util.List;

public interface SysMenuService {

    List<SysMenuVo> quarrySysMenu(MenuQuarry menuQuarry);

    Boolean addSysMenu(SysMenuVo sysMenuVo);

    Boolean editSysMenu(SysMenuVo sysMenuVo);

    SysMenuVo getSysMenuInfo(Integer menuId);

    Boolean deleteSysMenuInfo(Integer[] menuId);

    Boolean changeSysMenuStatus(Integer menuId, Integer status);

    List<SysMenuVo> getSysMenuByRoleList(List<SysRole> roles);

    /**
     * 查询全部启用菜单（目录/菜单/按钮均含），供超级管理员菜单树兜底。
     * admin 不经 role_menu 逐菜单授权，直接拿全部启用菜单，避免新增菜单后忘记给 admin 角色授权导致看不到。
     */
    List<SysMenuVo> getSysMenuAllEnabled();

}
