package com.rookie.system.mapper;

import com.rookie.common.pojo.entity.SysConfig;
import com.rookie.system.pojo.quarry.SysConfigQuarry;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 系统设置 Mapper。
 * <p>
 * 提供分页查询、新增、编辑、删除、详情、全量查询六类操作。
 * {@link #getAllSysConfig} 供启动预热与「刷新缓存」接口预热使用。
 */
@Mapper
public interface SysConfigMapper {

    /** 按条件分页查询系统设置（分页由 PageHelper 在 Service 层劫持） */
    List<SysConfig> quarrySysConfig(SysConfigQuarry quarry);

    /** 新增系统设置（insert 用 trim+if 动态列，审计四列固定写入） */
    Boolean addSysConfig(SysConfig config);

    /** 编辑系统设置（update 用 set+if 动态列，审计两列固定写入） */
    Boolean editSysConfig(SysConfig config);

    /** 按主键删除系统设置 */
    Boolean deleteSysConfigById(Long configId);

    /** 按主键查询单条系统设置（详情/编辑回显用） */
    SysConfig getSysConfigById(Long configId);

    /** 查询全部启用设置项（启动预热与刷新缓存预热使用） */
    List<SysConfig> getAllSysConfig();
}
