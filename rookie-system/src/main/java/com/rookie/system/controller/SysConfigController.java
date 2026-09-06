package com.rookie.system.controller;

import com.github.pagehelper.PageInfo;
import com.rookie.common.annotation.Log;
import com.rookie.common.enums.BusinessType;
import com.rookie.common.pojo.Result;
import com.rookie.system.pojo.quarry.SysConfigQuarry;
import com.rookie.system.pojo.vo.SysConfigVo;
import com.rookie.system.service.SysConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 系统设置 Controller。
 * <p>
 * 提供系统设置的分页查询、详情、新增、编辑、删除、刷新缓存六个管理接口，
 * 以及一个按设置键取值的公共读取接口（仅需登录，供前端按需读取运用）。
 * 写操作加 {@link Log} 采集操作日志，管理接口加 {@code @PreAuthorize} 鉴权，
 * 权限码与 {@code sys_menu.perm_key} 对齐：{@code system:systemConfig:*}。
 */
@RestController
@RequestMapping("/sys/system-config")
public class SysConfigController {

    @Autowired
    SysConfigService sysConfigService;

    @GetMapping("/list")
    @PreAuthorize("hasAuthority('system:systemConfig:quarry')")
    public Result<PageInfo<SysConfigVo>> quarrySysConfig(SysConfigQuarry quarry) {
        PageInfo<SysConfigVo> pageInfo = sysConfigService.quarrySysConfig(quarry);
        return Result.success(pageInfo);
    }

    @GetMapping("/{configId}")
    @PreAuthorize("hasAuthority('system:systemConfig:info')")
    public Result<SysConfigVo> getSysConfigInfo(@PathVariable Long configId) {
        SysConfigVo configVo = sysConfigService.getSysConfigById(configId);
        return Result.success(configVo);
    }

    @PostMapping()
    @Log(title = "系统设置", businessType = BusinessType.INSERT)
    @PreAuthorize("hasAuthority('system:systemConfig:add')")
    public Result<Boolean> addSysConfig(@RequestBody SysConfigVo configVo) {
        Boolean ok = sysConfigService.addSysConfig(configVo);
        return Result.success(ok);
    }

    @PutMapping()
    @Log(title = "系统设置", businessType = BusinessType.UPDATE)
    @PreAuthorize("hasAuthority('system:systemConfig:edit')")
    public Result<Boolean> editSysConfig(@RequestBody SysConfigVo configVo) {
        Boolean ok = sysConfigService.editSysConfig(configVo);
        return Result.success(ok);
    }

    @DeleteMapping("/{configId}")
    @Log(title = "系统设置", businessType = BusinessType.DELETE)
    @PreAuthorize("hasAuthority('system:systemConfig:delete')")
    public Result<Boolean> deleteSysConfigById(@PathVariable Long configId) {
        Boolean ok = sysConfigService.deleteSysConfigById(configId);
        return Result.success(ok);
    }

    @PostMapping("/refresh")
    @Log(title = "系统设置", businessType = BusinessType.OTHER)
    @PreAuthorize("hasAuthority('system:systemConfig:refresh')")
    public Result<Boolean> refreshCache() {
        Boolean ok = sysConfigService.refreshCache();
        return Result.success(ok);
    }

    /**
     * 按设置键获取当前设置值，供前端按需读取运用（对标若依 getConfigKey）。
     * <p>
     * 公共读取接口：不分页、不加按钮权限、不记操作日志，仅需登录即可调用。
     * 只返回 {@code configValue} 字符串，不暴露 valueType/isSystem/remark 等元信息，
     * 避免前端一次性全量拉取所有设置项导致关键信息泄露。
     * 命中且启用返回值，未命中或停用返回 {@code null}（业务码仍 200），前端按 null 判空。
     */
    @GetMapping("/configKey/{configKey}")
    public Result<String> getConfigValueByKey(@PathVariable String configKey) {
        String value = sysConfigService.getConfigValueByKey(configKey);
        return Result.success(value);
    }
}
