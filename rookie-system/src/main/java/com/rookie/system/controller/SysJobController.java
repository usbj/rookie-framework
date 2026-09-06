package com.rookie.system.controller;

import com.github.pagehelper.PageInfo;
import com.rookie.common.annotation.Log;
import com.rookie.common.enums.BusinessType;
import com.rookie.common.pojo.Result;
import com.rookie.common.pojo.entity.SysJobLog;
import com.rookie.system.pojo.quarry.SysJobLogQuarry;
import com.rookie.system.pojo.quarry.SysJobQuarry;
import com.rookie.system.pojo.vo.SysJobVo;
import com.rookie.system.service.SysJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 定时任务管理 Controller。
 * <p>
 * 提供任务的分页查询、详情、新增、编辑、删除、启停、立即执行七个管理接口，
 * 以及执行日志的分页查询。写操作加 {@link Log} 采集操作日志，管理接口加
 * {@code @PreAuthorize} 鉴权，权限码与 sql/sys_job.sql 中按钮权限对齐（system:job:*）。
 * 任务调度由 SysJobScheduler 动态注册（CronTrigger），改配置即生效，无需重启。
 */
@RestController
@RequestMapping("/sys/job")
@Tag(name = "定时任务", description = "定时任务管理与执行日志")
public class SysJobController {

    @Autowired
    SysJobService sysJobService;

    @GetMapping("/list")
    @PreAuthorize("hasAuthority('system:job:quarry')")
    @Operation(summary = "定时任务列表")
    public Result<PageInfo<SysJobVo>> quarrySysJob(SysJobQuarry quarry) {
        PageInfo<SysJobVo> pageInfo = sysJobService.quarrySysJob(quarry);
        return Result.success(pageInfo);
    }

    @GetMapping("/{jobId}")
    @PreAuthorize("hasAuthority('system:job:info')")
    @Operation(summary = "定时任务详情")
    public Result<SysJobVo> getSysJobInfo(@PathVariable Long jobId) {
        SysJobVo jobVo = sysJobService.getSysJobById(jobId);
        return Result.success(jobVo);
    }

    @PostMapping()
    @Log(title = "定时任务", businessType = BusinessType.INSERT)
    @PreAuthorize("hasAuthority('system:job:add')")
    @Operation(summary = "新增定时任务")
    public Result<Boolean> addSysJob(@RequestBody SysJobVo jobVo) {
        Boolean ok = sysJobService.addSysJob(jobVo);
        return Result.success(ok);
    }

    @PutMapping()
    @Log(title = "定时任务", businessType = BusinessType.UPDATE)
    @PreAuthorize("hasAuthority('system:job:edit')")
    @Operation(summary = "编辑定时任务")
    public Result<Boolean> editSysJob(@RequestBody SysJobVo jobVo) {
        Boolean ok = sysJobService.editSysJob(jobVo);
        return Result.success(ok);
    }

    @DeleteMapping("/{jobId}")
    @Log(title = "定时任务", businessType = BusinessType.DELETE)
    @PreAuthorize("hasAuthority('system:job:delete')")
    @Operation(summary = "删除定时任务")
    public Result<Boolean> deleteSysJobById(@PathVariable Long jobId) {
        Boolean ok = sysJobService.deleteSysJobById(jobId);
        return Result.success(ok);
    }

    @PutMapping("/status")
    @Log(title = "定时任务", businessType = BusinessType.UPDATE)
    @PreAuthorize("hasAuthority('system:job:status')")
    @Operation(summary = "定时任务启停")
    public Result<Boolean> changeSysJobStatus(@RequestParam Long jobId, @RequestParam Integer status) {
        Boolean ok = sysJobService.changeSysJobStatus(jobId, status);
        return Result.success(ok);
    }

    @PostMapping("/run/{jobId}")
    @Log(title = "定时任务", businessType = BusinessType.OTHER)
    @PreAuthorize("hasAuthority('system:job:run')")
    @Operation(summary = "立即执行")
    public Result<Boolean> runSysJob(@PathVariable Long jobId) {
        Boolean ok = sysJobService.runSysJob(jobId);
        return Result.success(ok);
    }

    @GetMapping("/log/list")
    @PreAuthorize("hasAuthority('system:jobLog:quarry')")
    @Operation(summary = "执行日志列表")
    public Result<PageInfo<SysJobLog>> quarrySysJobLog(SysJobLogQuarry quarry) {
        PageInfo<SysJobLog> pageInfo = sysJobService.quarrySysJobLog(quarry);
        return Result.success(pageInfo);
    }
}
