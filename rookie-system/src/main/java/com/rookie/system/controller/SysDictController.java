package com.rookie.system.controller;


import com.github.pagehelper.PageInfo;
import com.rookie.common.annotation.Log;
import com.rookie.common.enums.BusinessType;
import com.rookie.common.pojo.Result;
import com.rookie.system.pojo.quarry.DictQuarry;
import com.rookie.system.pojo.vo.SysDictVO;
import com.rookie.system.service.SysDictService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/sys/dict")
public class SysDictController {

    @Autowired
    SysDictService sysDictService;

    @GetMapping("/list")
    @PreAuthorize("hasAuthority('system:dict:quarry')")
    public Result<PageInfo<SysDictVO>> quarrySysDict(DictQuarry quarry){
        PageInfo<SysDictVO> sysDictVOPageInfo = sysDictService.quarrySysDict(quarry);
        return Result.success(sysDictVOPageInfo);
    }

    @PostMapping()
    @Log(title = "字典管理", businessType = BusinessType.INSERT)
    @PreAuthorize("hasAuthority('system:dict:add')")
    public Result<Boolean> addSysDict(@RequestBody SysDictVO dictVo){
        Boolean b = sysDictService.addSysDict(dictVo);
        return Result.success(b);
    }

    @PutMapping()
    @Log(title = "字典管理", businessType = BusinessType.UPDATE)
    @PreAuthorize("hasAuthority('system:dict:edit')")
    public Result<Boolean> editSysDict(@RequestBody SysDictVO dictVo){
        Boolean b = sysDictService.editSysDictInfo(dictVo);
        return Result.success(b);
    }

    @DeleteMapping("/{dictId}")
    @Log(title = "字典管理", businessType = BusinessType.DELETE)
    @PreAuthorize("hasAuthority('system:dict:delete')")
    public Result<Boolean> deleteSysDictById(@PathVariable Long dictId){
        Boolean b = sysDictService.deleteSysDictById(dictId);
        return Result.success(b);
    }

    @GetMapping("/{dictId}")
    @PreAuthorize("hasAuthority('system:dict:info')")
    public Result<SysDictVO> getSysDictInfo(@PathVariable Long dictId){
        SysDictVO sysDictById = sysDictService.getSysDictById(dictId);
        return Result.success(sysDictById);
    }

    /**
     * 全量查询启用字典类型，供前端登录后初始化消费。
     * <p>
     * 公共读取接口：不分页、不加按钮权限、不记操作日志，仅需登录即可调用。
     * 返回字典类型基础信息（dictId/dictName/dictKey/status 等，不含字典数据项），
     * 供前端拿到 dictKey 后再逐个调 {@code GET /sys/dist/data/type/{dictKey}} 拉数据项。
     * 与 {@code GET /sys/dict/list}（需 system:dict:quarry 权限）的区别：本接口面向所有登录用户，
     * 让没有字典管理权限的普通用户也能正常使用字典下拉/标签等基础功能。
     */
    @GetMapping("/all")
    public Result<List<SysDictVO>> listAllEnabledDict(){
        List<SysDictVO> list = sysDictService.listAllEnabledDict();
        return Result.success(list);
    }


}
