package com.rookie.system.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.github.pagehelper.PageInfo;
import com.rookie.common.pojo.entity.SysDict;
import com.rookie.common.util.PageUtil;
import com.rookie.framework.security.pojo.UserInfo;
import com.rookie.system.mapper.SysDictMapper;
import com.rookie.system.pojo.quarry.DictQuarry;
import com.rookie.system.pojo.vo.SysDictVO;
import com.rookie.system.service.SysDictService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class SysDictServiceImpl implements SysDictService {

    @Autowired
    SysDictMapper sysDictMapper;

    @Override
    public PageInfo<SysDictVO> quarrySysDict(DictQuarry quarry) {
        PageUtil.startPage();
        List<SysDict> sysDicts = sysDictMapper.quarrySysDict(quarry);
        PageInfo<SysDict> sysDictPageInfo = PageUtil.packagedPageInfo(sysDicts);
        return PageUtil.copyPageInfo(sysDictPageInfo, SysDictVO.class);
    }

    @Override
    public Boolean addSysDict(SysDictVO dictVo) {
        SysDict sysDict = BeanUtil.toBean(dictVo, SysDict.class);
        UserInfo userInfo = (UserInfo) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        sysDict.setCreateBy(userInfo.getUsername());
        sysDict.setUpdateBy(userInfo.getUsername());
        return sysDictMapper.addSysDict(sysDict);
    }

    @Override
    public Boolean editSysDictInfo(SysDictVO dictVo) {
        SysDict sysDict = BeanUtil.toBean(dictVo, SysDict.class);
        UserInfo userInfo = (UserInfo) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        sysDict.setUpdateBy(userInfo.getUsername());
        return sysDictMapper.editSysDictInfo(sysDict);
    }

    @Override
    public Boolean deleteSysDictById(Long dictId) {
        return sysDictMapper.deleteSysDictById(dictId);
    }

    @Override
    public SysDictVO getSysDictById(Long dictId) {
        SysDict sysDict = sysDictMapper.getSysDictInfoById(dictId);
        return BeanUtil.toBean(sysDict, SysDictVO.class);
    }

    @Override
    public List<SysDictVO> listAllEnabledDict() {
        // 复用 quarrySysDict 的动态 SQL，传 status=1、其余条件为空，取全部启用字典类型。
        // 直接调 mapper 不走 PageUtil，避免被分页插件截断为第一页。
        DictQuarry quarry = new DictQuarry();
        quarry.setStatus(1);
        List<SysDict> sysDicts = sysDictMapper.quarrySysDict(quarry);
        return toDictVoList(sysDicts);
    }

    private List<SysDictVO> toDictVoList(List<SysDict> sysDicts) {
        List<SysDictVO> dictVos = new ArrayList<>();
        if (sysDicts == null || sysDicts.isEmpty()) {
            return dictVos;
        }
        for (SysDict sysDict : sysDicts) {
            dictVos.add(BeanUtil.toBean(sysDict, SysDictVO.class));
        }
        return dictVos;
    }

}
