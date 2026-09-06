package com.rookie.system.service;

import com.github.pagehelper.PageInfo;
import com.rookie.common.pojo.entity.SysDictData;
import com.rookie.system.pojo.quarry.DictDataQuarry;
import com.rookie.system.pojo.vo.SysDictDataVo;

import java.util.List;

public interface SysDictDataService {

    PageInfo<SysDictDataVo> quarrySysDictData(DictDataQuarry quarry);

    Boolean addSysDictData(SysDictDataVo dictDataVo);

    Boolean editSysDictDataInfo(SysDictDataVo dictDataVo);

    Boolean deleteSysDictDataByDataId(Long dictDataId);

    SysDictDataVo getSysDictDataByDataId(Long dictDataId);

    List<SysDictDataVo> getSysDictDataByDictKey(String dictKey);

/**
 * 清空后端 Redis 中的全部字典数据缓存（sys_dict_name:*）。
 * 供前端"刷新字典缓存"显式调用：清缓存后下次按 dictKey 取值才会重新查库，
 * 解决 SQL 直插/外部改库导致后端缓存与 DB 不一致、接口仍返回旧字典的问题。
 */
Boolean clearDictDataCache();
}
