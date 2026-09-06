package com.rookie.system.service;

import com.github.pagehelper.PageInfo;
import com.rookie.system.pojo.quarry.SysConfigQuarry;
import com.rookie.system.pojo.vo.SysConfigVo;

/**
 * 系统设置服务接口。
 * <p>
 * 提供分页查询、详情、新增、编辑、删除、刷新缓存六类管理操作。
 * 增改删在操作数据库后同步维护 Redis 缓存（与字典服务缓存一致性写法对齐）；
 * 内置项（is_system=1）受保护，禁止删除、禁止修改 configKey 与 valueType。
 * 另提供按设置键取值的公共读取方法，供前端按需读取运用。
 */
public interface SysConfigService {

    /** 分页查询系统设置列表 */
    PageInfo<SysConfigVo> quarrySysConfig(SysConfigQuarry quarry);

    /** 按主键查询系统设置详情 */
    SysConfigVo getSysConfigById(Long configId);

    /** 新增系统设置并写入缓存 */
    Boolean addSysConfig(SysConfigVo configVo);

    /** 编辑系统设置并刷新缓存（内置项受保护，仅可改值/名称/备注/状态） */
    Boolean editSysConfig(SysConfigVo configVo);

    /** 按主键删除系统设置并清除缓存（内置项禁止删除） */
    Boolean deleteSysConfigById(Long configId);

    /** 刷新缓存：清空全部设置项缓存后立即从数据库重新预热全部启用项 */
    Boolean refreshCache();

    /**
     * 按设置键读取当前设置值，供前端按需运用。
     * <p>
     * 只读缓存（{@link com.rookie.common.util.SysConfigUtil}），不走数据库；
     * 命中且启用返回 {@code configValue}，未命中或停用返回 {@code null}。
     * 仅返回值字符串，不暴露 valueType 等元信息。
     */
    String getConfigValueByKey(String configKey);
}
