package com.rookie.system.controller;

import com.rookie.common.annotation.Log;
import com.rookie.common.enums.BusinessType;
import com.rookie.common.pojo.Result;
import com.rookie.common.util.SysConfigUtil;
import com.rookie.system.pojo.RegisterBody;
import com.rookie.system.service.SysLoginService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户自助注册 Controller。
 * <p>
 * 注册接口 {@code POST /register} 与注册开关查询接口 {@code GET /register/enabled}
 * 均为公开接口（SecurityConfig 已 permitAll）。
 * 注册开关由系统设置 {@code sys.user.registerEnabled}（BOOLEAN）控制，
 * 关闭时由 {@link SysLoginService#register} 直接拒绝；注册成功后绑定系统默认角色，
 * 不自动登录，由前端引导跳转登录页。
 * <p>
 * 注意：注册开关查询走本控制器专用接口，而不是放开系统设置按 key 读取接口
 * （{@code /sys/system-config/configKey/**} 保持仅需登录），避免游客任意读取系统设置。
 */
@RestController
@Tag(name = "用户注册", description = "用户自助注册相关接口")
public class SysRegisterController {

    @Autowired
    SysLoginService sysLoginService;

    @PostMapping("/register")
    @Operation(summary = "用户自助注册")
    @Log(title = "用户注册", businessType = BusinessType.INSERT)
    public Result<Boolean> register(@RequestBody RegisterBody registerBody) {
        Boolean ok = sysLoginService.register(registerBody);
        return Result.success(ok);
    }

    @GetMapping("/register/enabled")
    @Operation(summary = "获取注册开关状态")
    public Result<Boolean> registerEnabled() {
        // 读取系统设置 sys.user.registerEnabled（BOOLEAN），缺省（未配置/停用）视为关闭
        Boolean enabled = SysConfigUtil.getBoolean("sys.user.registerEnabled", false);
        return Result.success(enabled);
    }
}
