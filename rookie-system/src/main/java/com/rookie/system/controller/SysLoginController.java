package com.rookie.system.controller;


import com.rookie.common.annotation.Log;
import com.rookie.common.enums.BusinessType;
import com.rookie.common.pojo.Result;
import com.rookie.framework.security.pojo.UserInfo;
import com.rookie.system.pojo.LoginBody;
import com.rookie.system.pojo.ModifyPasswordBody;
import com.rookie.system.pojo.vo.SysMenuVo;
import com.rookie.system.pojo.vo.SysUserVo;
import com.rookie.system.service.SysLoginService;
import com.rookie.system.service.SysUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * TODO:重构登录系统，理清UserInfo,SysUserVO的区分，确定是用SysLoginService还是SysUserService抑或其他
 * */

@RestController
@Tag(name = "系统登录",description = "用于测试登录相关接口")
public class SysLoginController {

    @Autowired
    SysLoginService sysLoginService;


    @PostMapping("/login")
    @Operation(summary = "登录")
    @Log(title = "登录管理", businessType = BusinessType.OTHER)
    public Result<String> login(@RequestBody LoginBody loginBody){
        String token = sysLoginService.loginVerification(loginBody);
        return Result.success(token);
    }

    /**
     * 退出登录（需要登录）：从在线集合移除并删除登录态缓存，旧 token 立即失效。
     * 前端退出时调用后清空本地登录态；幂等，重复调用直接返回成功。
     */
    @PostMapping("/logout")
    @Operation(summary = "退出登录")
    @Log(title = "登录管理", businessType = BusinessType.OTHER)
    public Result<Boolean> logout() {
        UserInfo userInfo = (UserInfo) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Boolean b = sysLoginService.logout(userInfo.getUsername());
        return Result.success(b);
    }


    @GetMapping("/person")
    @Operation(summary = "获取个人数据")
    public Result<SysUserVo> getPersonalDetail() {
        UserInfo userInfo = (UserInfo) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        SysUserVo personalDetails = sysLoginService.getPersonalDetails(userInfo.getUserId());
        return Result.success(personalDetails);
    }

    @PutMapping("/person")
    @Operation(summary = "更改个人数据")
    @Log(title = "个人信息", businessType = BusinessType.UPDATE)
    public Result<Boolean> modifyPersonalDetails(@RequestBody SysUserVo sysUserVo) {
        UserInfo userInfo = (UserInfo) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        sysUserVo.setUserId(userInfo.getUserId());
        Boolean b = sysLoginService.modifyPersonalDetails(sysUserVo);
        return Result.success(b);
    }

    @PutMapping("/person/password")
    @Operation(summary = "修改个人密码")
    @Log(title = "个人信息", businessType = BusinessType.UPDATE)
    public Result<Boolean> modifyPersonalPassword(@RequestBody ModifyPasswordBody modifyPasswordBody) {
        UserInfo userInfo = (UserInfo) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Boolean b = sysLoginService.modifyPersonalPassword(userInfo.getUserId(), modifyPasswordBody);
        return Result.success(b);
    }

    /**
     * 上传当前用户头像（multipart/form-data，字段名 file，仅需登录）。
     * 不标注 @Log：操作日志切面会序列化方法参数，MultipartFile 的 getBytes() 会把图片整体读入内存
     * 再转 JSON，开销不可接受（与 SysFileController 上传接口同一原因）。
     */
    @PostMapping("/person/avatar")
    @Operation(summary = "上传个人头像")
    public Result<String> uploadPersonalAvatar(@RequestParam("file") MultipartFile file) {
        UserInfo userInfo = (UserInfo) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String storedName = sysLoginService.uploadPersonalAvatar(userInfo.getUserId(), file);
        return Result.success(storedName);
    }

    /**
     * 读取当前用户头像图片流（inline，仅需登录）。
     * 前端以 blob 方式请求并转 objectURL 展示（&lt;img&gt; 标签无法携带 Token 请求头）。
     */
    @GetMapping("/person/avatar")
    @Operation(summary = "获取个人头像")
    public ResponseEntity<Resource> getPersonalAvatar() {
        UserInfo userInfo = (UserInfo) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return sysLoginService.getPersonalAvatar(userInfo.getUserId());
    }

    @GetMapping("/person/routers")
    @Operation(summary = "获取当前用户路由树")
    public Result<List<SysMenuVo>> getUserMenuTree() {
        UserInfo userInfo = (UserInfo) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        List<SysMenuVo> userMenuTreeByUserId = sysLoginService.getUserMenuTreeByUserId(userInfo.getUserId());
        return Result.success(userMenuTreeByUserId);
    }

}
