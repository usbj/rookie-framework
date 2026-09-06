package com.rookie.system.service;

import com.github.pagehelper.PageInfo;
import com.rookie.system.pojo.quarry.DictQuarry;
import com.rookie.system.pojo.vo.SysDictVO;

import java.util.List;

public interface SysDictService {

    PageInfo<SysDictVO> quarrySysDict(DictQuarry quarry);

    Boolean addSysDict(SysDictVO dictVo);

    Boolean editSysDictInfo(SysDictVO dictVo);

    Boolean deleteSysDictById(Long dictId);

    SysDictVO getSysDictById(Long dictId);

    /**
     * 查询全部启用字典类型，供前端登录后初始化消费。
     * <p>
     * 不分页、不加按钮权限，仅需登录即可调用。返回字典类型基础信息
     * （dictId/dictName/dictKey/status 等，不含字典数据项），供前端拿到 dictKey 后
     * 再逐个调 {@code GET /sys/dist/data/type/{dictKey}} 拉数据项。
     */
    List<SysDictVO> listAllEnabledDict();

}
