package com.rookie.system.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.github.pagehelper.PageInfo;
import com.rookie.common.cache.RedisCache;
import com.rookie.common.pojo.entity.SysDictData;
import com.rookie.common.util.DictUtil;
import com.rookie.common.util.PageUtil;
import com.rookie.framework.security.pojo.UserInfo;
import com.rookie.system.mapper.SysDictDataMapper;
import com.rookie.system.pojo.quarry.DictDataQuarry;
import com.rookie.system.pojo.vo.SysDictDataVo;
import com.rookie.system.service.SysDictDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;


@Service
public class SysDictDataServiceImpl implements SysDictDataService {

    @Autowired
    SysDictDataMapper sysDictDataMapper;

    @Autowired
    RedisCache redisCache;

    @Override
    public PageInfo<SysDictDataVo> quarrySysDictData(DictDataQuarry quarry) {
        PageUtil.startPage();
        List<SysDictData> sysDictData = sysDictDataMapper.quarrySysDictData(quarry);
        PageInfo<SysDictData> sysDictDataPageInfo = PageUtil.packagedPageInfo(sysDictData);
        PageInfo<SysDictDataVo> dictDataVoPageInfo = PageUtil.copyPageInfo(sysDictDataPageInfo,SysDictDataVo.class);
        dictDataVoPageInfo.setList(toDictDataVoList(sysDictData));
        return dictDataVoPageInfo;
    }

    @Override
    public Boolean addSysDictData(SysDictDataVo dictDataVo) {
        SysDictData dictDataEntity = toDictDataEntity(dictDataVo);
        UserInfo userInfo = (UserInfo) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        dictDataEntity.setCreateBy(userInfo.getUsername());
        dictDataEntity.setUpdateBy(userInfo.getUsername());
        Boolean b = sysDictDataMapper.addSysDictData(dictDataEntity);
        if (b) {
            List<SysDictData> dictDataList = sysDictDataMapper.getSysDictDataByDictKey(dictDataVo.getDictKey());
            DictUtil.setDictData(dictDataVo.getDictKey(), dictDataList);
        }
        return b;
    }

    @Override
    public Boolean editSysDictDataInfo(SysDictDataVo dictDataVo) {
        SysDictData dictDataEntity = toDictDataEntity(dictDataVo);
        UserInfo userInfo = (UserInfo) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        dictDataEntity.setCreateBy(userInfo.getUsername());
        dictDataEntity.setUpdateBy(userInfo.getUsername());
        Boolean b = sysDictDataMapper.editSysDictDataInfo(dictDataEntity);
        if (b){
            List<SysDictData> dictDataList = sysDictDataMapper.getSysDictDataByDictKey(dictDataVo.getDictKey());
            DictUtil.setDictData(dictDataVo.getDictKey(),dictDataList);
        }
        return b;
    }

    @Override
    public Boolean deleteSysDictDataByDataId(Long dictDataId) {
        return sysDictDataMapper.deleteSysDictDataByDataId(dictDataId);
    }

    @Override
    public SysDictDataVo getSysDictDataByDataId(Long dictDataId) {
        SysDictData sysDictData = sysDictDataMapper.getSysDictDataInfoByDataId(dictDataId);
        return toDictDataVo(sysDictData);
    }

    @Override
    public List<SysDictDataVo> getSysDictDataByDictKey(String dictKey) {
        List<SysDictData> listCacheEntity = DictUtil.getDictData(dictKey);
        List<SysDictDataVo> listCache = toDictDataVoList(listCacheEntity);
        if (!listCache.isEmpty()){
            return listCache;
        }
        List<SysDictData> sysDictDataByDictKey = sysDictDataMapper.getSysDictDataByDictKey(dictKey);
        DictUtil.setDictData(dictKey,sysDictDataByDictKey);
        return toDictDataVoList(sysDictDataByDictKey);
    }

    @Override
    public Boolean clearDictDataCache() {
        // 清空 Redis 中全部 sys_dict_name:* 缓存键，下次按 dictKey 取值会重新查库并回填缓存。
        // 解决"接口读旧缓存、DB 已更新"的不一致：见 SysDictDataController 顶部 TODO。
        DictUtil.clearDictData();
        return true;
    }

    private SysDictData toDictDataEntity(SysDictDataVo dictDataVo) {
        return BeanUtil.toBean(dictDataVo, SysDictData.class);
    }

    private SysDictDataVo toDictDataVo(SysDictData dictDataEntity) {
        return BeanUtil.toBean(dictDataEntity, SysDictDataVo.class);
    }

    private List<SysDictDataVo> toDictDataVoList(List<SysDictData> dictDataEntities) {
        List<SysDictDataVo> dictDataVos = new ArrayList<>();
        if (dictDataEntities == null || dictDataEntities.isEmpty()) {
            return dictDataVos;
        }
        for (SysDictData dictDataEntity : dictDataEntities) {
            dictDataVos.add(toDictDataVo(dictDataEntity));
        }
        return dictDataVos;
    }
}
