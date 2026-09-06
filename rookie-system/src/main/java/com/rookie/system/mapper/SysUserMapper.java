package com.rookie.system.mapper;


import com.rookie.common.pojo.entity.SysUser;
import com.rookie.system.pojo.quarry.UserQuarry;
import com.rookie.system.pojo.vo.SysUserVo;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface SysUserMapper {

    List<SysUser> quarryUser(UserQuarry userQuarry);

    Boolean editUserInfo(SysUser user);

    SysUserVo selectSysUserById(Long userId);

    Boolean addSysUser(SysUser user);

    Boolean usernameIsExistOrNot(String username);

    Boolean phoneIsExistOrNot(String phoneNumber);

    Boolean deleteSysUserById(Long userId);

    Boolean changeSysUserStatus(Long userId,Integer status);

    SysUser getSysUserInfoById(Long userId);

    Boolean resetSysUserPassword(SysUser sysUser);

    /**
     * 仅更新头像字段（独立于 editUserInfo 白名单，避免资料编辑路径误改头像）。
     * 由头像上传服务调用，updateBy 必须显式填充（update_by 为 NOT NULL 列）。
     */
    Boolean updateSysUserAvatar(SysUser sysUser);

}
