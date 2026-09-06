package com.rookie.system.controller;

import com.rookie.common.pojo.Result;
import com.rookie.system.monitor.MonitorProvider;
import com.rookie.system.pojo.vo.MonitorItemVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务监控 Controller。
 * <p>
 * 采用可插拔的监控源设计：聚合所有 {@link MonitorProvider} 的实时数据返回，
 * 新增监控源（如 Redis / MySQL 等中间件）只需实现 MonitorProvider 并注册为 Bean，
 * 本接口与前端会自动纳入（未知类型由前端占位兜底）。
 * 仅需登录 + {@code system:monitor:quarry} 权限（与 sql/sys_monitor.sql 按钮权限对齐），
 * 只读接口不记操作日志。注意：服务器监控源会做一次 CPU 双采样（约 300ms），属正常延迟。
 */
@RestController
@RequestMapping("/sys/monitor")
@Tag(name = "服务监控", description = "服务器与中间件运行状态监控")
public class SysMonitorController {

    @Autowired
    List<MonitorProvider> monitorProviders;

    @GetMapping("/items")
    @PreAuthorize("hasAuthority('system:monitor:quarry')")
    @Operation(summary = "监控数据（聚合全部监控源）")
    public Result<List<MonitorItemVo>> getMonitorItems() {
        List<MonitorItemVo> items = new ArrayList<>();
        for (MonitorProvider provider : monitorProviders) {
            try {
                items.add(new MonitorItemVo(provider.type(), provider.title(), provider.collect()));
            } catch (Exception e) {
                // 单个监控源采集失败不影响其他监控源，该项数据置 null 由前端占位
                items.add(new MonitorItemVo(provider.type(), provider.title(), null));
            }
        }
        return Result.success(items);
    }
}
