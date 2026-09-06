package com.rookie.system.controller;

import com.rookie.common.annotation.Log;
import com.rookie.common.enums.BusinessType;
import com.rookie.common.pojo.Result;
import com.rookie.framework.security.pojo.UserInfo;
import com.rookie.framework.service.OnlineUserEntry;
import com.rookie.framework.service.OnlineUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 在线用户统计 Controller。
 * <p>
 * 基于 Redis 在线集合（TokenVerifyFilter 每请求写入活跃时间）：
 * <ul>
 *   <li>ping：前端心跳接口，仅需登录（登录后每 60s 调用一次，维持挂机用户在线状态）；</li>
 *   <li>count / list：管理接口，权限码 {@code system:online:quarry}，与 sys_menu 按钮权限对齐；</li>
 *   <li>logout：强制下线指定账号，权限码 {@code system:online:kick}，删除其登录态缓存后旧 token 立即失效。</li>
 * </ul>
 * 在线阈值由系统设置 {@code sys.online.timeout}（分钟，默认 30）控制，见 sql/sys_online.sql。
 */
@RestController
@RequestMapping("/sys/online")
@Tag(name = "在线用户", description = "在线人数统计与在线用户管理")
public class SysOnlineController {

    @Autowired
    OnlineUserService onlineUserService;

    /**
     * 心跳接口：前端登录后定时调用，刷新当前用户活跃时间。
     * 不加按钮权限（个人向，对标 /person 系列）；实际活跃记录由 TokenVerifyFilter 写入，
     * 本接口仅作为前端定时触发点，返回成功即可。
     */
    @GetMapping("/ping")
    @Operation(summary = "在线心跳")
    public Result<Boolean> ping() {
        return Result.success(true);
    }

    /**
     * 当前在线人数（阈值内活跃用户数）。
     */
    @GetMapping("/count")
    @PreAuthorize("hasAuthority('system:online:quarry')")
    @Operation(summary = "在线人数")
    public Result<Long> getOnlineCount() {
        long count = onlineUserService.getOnlineCount();
        return Result.success(count);
    }

    /**
     * 在线用户列表（按最后活跃时间倒序），含昵称/登录IP/登录时间/最后活跃时间。
     */
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('system:online:quarry')")
    @Operation(summary = "在线用户列表")
    public Result<List<OnlineUserEntry>> getOnlineList() {
        List<OnlineUserEntry> onlineUsers = onlineUserService.getOnlineUsers();
        return Result.success(onlineUsers);
    }

    /**
     * 强制下线指定账号：移除在线集合 + 删除登录态缓存（旧 token 立即失效，下次请求 401）。
     * 不能对当前登录账号操作（后端校验兜底）。
     */
    @PostMapping("/logout/{username}")
    @Log(title = "在线用户", businessType = BusinessType.DELETE)
    @PreAuthorize("hasAuthority('system:online:kick')")
    @Operation(summary = "强制下线")
    public Result<Boolean> kickOffline(@PathVariable String username) {
        UserInfo userInfo = (UserInfo) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        onlineUserService.kickOfflineUser(username, userInfo.getUsername());
        return Result.success(true);
    }
}
