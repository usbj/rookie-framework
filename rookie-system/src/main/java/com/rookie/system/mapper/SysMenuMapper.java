package com.rookie.system.mapper;

import com.rookie.common.pojo.entity.SysMenu;
import com.rookie.system.pojo.quarry.MenuQuarry;
import com.rookie.system.pojo.vo.SysMenuVo;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;


@Mapper
public interface SysMenuMapper {

    List<SysMenuVo> quarrySysMenu(MenuQuarry menuQuarry);

    boolean addSysMenu(SysMenu sysMenu);

    boolean editSysMenuInfo(SysMenu sysMenu);

    SysMenu getSysMenuInfo(Integer menuId);

    boolean deleteSysMenuInfo(Integer menuId);

    boolean changeSysMenuStatus(Integer menuId,Integer status);

    List<SysMenuVo> getSysMenuByMenuIds(List<Long> menuIds);

    /**
     * 查询全部启用且未删除的菜单（目录/菜单/按钮均含），供超级管理员菜单树兜底使用。
     * 与 {@link #getSysMenuByMenuIds} 的区别：不限 menuId，直接取全表启用项。
     *
     * @return 全部启用菜单 VO 列表，按 parent_id、menu_id 排序
     */
    List<SysMenuVo> getSysMenuAllEnabled();

}
