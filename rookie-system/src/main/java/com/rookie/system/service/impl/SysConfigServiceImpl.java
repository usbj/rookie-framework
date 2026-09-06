package com.rookie.system.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.github.pagehelper.PageInfo;
import com.rookie.common.exception.ServiceException;
import com.rookie.common.pojo.entity.SysConfig;
import com.rookie.common.util.PageUtil;
import com.rookie.common.util.SysConfigUtil;
import com.rookie.framework.security.pojo.UserInfo;
import com.rookie.system.mapper.SysConfigMapper;
import com.rookie.system.pojo.quarry.SysConfigQuarry;
import com.rookie.system.pojo.vo.SysConfigVo;
import com.rookie.system.service.SysConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 系统设置服务实现。
 * <p>
 * 缓存一致性写法对标 {@code SysDictDataServiceImpl}：
 * 增改删在事务内操作数据库后，立即通过 {@link SysConfigUtil} 重写或删除对应缓存。
 * <p>
 * 内置项保护（is_system=1）：删除抛 {@link ServiceException}；编辑时禁止修改 configKey 与 valueType，
 * 仅允许修改 configValue / configName / remark / status，避免误改代码硬依赖项导致功能异常。
 * 新增时强制 is_system=0（内置项只能由系统初始化脚本写入，不通过业务接口创建）。
 */
@Service
public class SysConfigServiceImpl implements SysConfigService {

    @Autowired
    SysConfigMapper sysConfigMapper;

    @Override
    public PageInfo<SysConfigVo> quarrySysConfig(SysConfigQuarry quarry) {
        PageUtil.startPage();
        List<SysConfig> sysConfigs = sysConfigMapper.quarrySysConfig(quarry);
        PageInfo<SysConfig> sysConfigPageInfo = PageUtil.packagedPageInfo(sysConfigs);
        PageInfo<SysConfigVo> voPageInfo = PageUtil.copyPageInfo(sysConfigPageInfo, SysConfigVo.class);
        voPageInfo.setList(toConfigVoList(sysConfigs));
        return voPageInfo;
    }

    @Override
    public SysConfigVo getSysConfigById(Long configId) {
        SysConfig config = sysConfigMapper.getSysConfigById(configId);
        return toConfigVo(config);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean addSysConfig(SysConfigVo configVo) {
        // 设置值为必填，新增时不能为空（空字符串或 null 均拦截；BOOLEAN 的 "false"、NUMBER 的 "0" 非空，放行）
        if (configVo.getConfigValue() == null || configVo.getConfigValue().trim().isEmpty()) {
            throw new ServiceException(500, "设置值不能为空");
        }
        SysConfig config = toConfigEntity(configVo);
        UserInfo userInfo = currentUserInfo();
        config.setCreateBy(userInfo.getUsername());
        config.setUpdateBy(userInfo.getUsername());
        // 新增只允许业务项，内置项由系统初始化脚本写入，强制置 0 防止越权
        config.setIsSystem(0);
        Boolean ok = sysConfigMapper.addSysConfig(config);
        if (ok) {
            // 写库成功后立即写入缓存，保证后续工具类读取可命中
            SysConfigUtil.setConfig(sysConfigMapper.getSysConfigById(config.getConfigId()));
        }
        return ok;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean editSysConfig(SysConfigVo configVo) {
        SysConfig existed = sysConfigMapper.getSysConfigById(configVo.getConfigId());
        if (existed == null) {
            throw new ServiceException(500, "设置项不存在");
        }
        // 设置值为必填：编辑时若显式传入空字符串则拦截；传 null 表示不更新该字段，放行
        // （BOOLEAN 的 "false"、NUMBER 的 "0" 均非空字符串，放行）
        if (configVo.getConfigValue() != null && configVo.getConfigValue().trim().isEmpty()) {
            throw new ServiceException(500, "设置值不能为空");
        }
        // 内置项保护：禁止修改 configKey 与 valueType，仅可改值/名称/备注/状态
        if (existed.getIsSystem() != null && existed.getIsSystem() == 1) {
            if (configVo.getConfigKey() != null && !configVo.getConfigKey().equals(existed.getConfigKey())) {
                throw new ServiceException(500, "系统内置项的设置键不可修改");
            }
            if (configVo.getValueType() != null && !configVo.getValueType().equals(existed.getValueType())) {
                throw new ServiceException(500, "系统内置项的值类型不可修改");
            }
            // 内置项状态不允许通过编辑停用，防止代码依赖的设置项被禁用导致功能异常
            if (configVo.getStatus() != null && configVo.getStatus() == 0) {
                throw new ServiceException(500, "系统内置项不可停用");
            }
        }
        SysConfig config = toConfigEntity(configVo);
        UserInfo userInfo = currentUserInfo();
        config.setUpdateBy(userInfo.getUsername());
        Boolean ok = sysConfigMapper.editSysConfig(config);
        if (ok) {
            // 编辑后用最新记录重写缓存，确保缓存与数据库一致
            SysConfigUtil.setConfig(sysConfigMapper.getSysConfigById(config.getConfigId()));
        }
        return ok;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteSysConfigById(Long configId) {
        SysConfig existed = sysConfigMapper.getSysConfigById(configId);
        if (existed == null) {
            throw new ServiceException(500, "设置项不存在");
        }
        // 内置项禁止删除，防止误删代码硬依赖项
        if (existed.getIsSystem() != null && existed.getIsSystem() == 1) {
            throw new ServiceException(500, "系统内置项不可删除");
        }
        Boolean ok = sysConfigMapper.deleteSysConfigById(configId);
        if (ok) {
            SysConfigUtil.removeConfig(existed.getConfigKey());
        }
        return ok;
    }

    @Override
    public Boolean refreshCache() {
        // 清空后立即从数据库重新预热全部启用项，保证工具类只读缓存语义成立
        SysConfigUtil.clearAllConfig();
        List<SysConfig> all = sysConfigMapper.getAllSysConfig();
        SysConfigUtil.setConfigs(all);
        return true;
    }

    @Override
    public String getConfigValueByKey(String configKey) {
        // 只读缓存、不走数据库：命中且启用返回值，未命中或停用返回 null
        SysConfig config = SysConfigUtil.getConfig(configKey);
        if (config == null || config.getStatus() == null || config.getStatus() != 1) {
            return null;
        }
        return config.getConfigValue();
    }

    /**
     * 从 SecurityContext 取当前登录用户信息，用于填充审计字段 createBy/updateBy。
     * 与 {@code SysDictDataServiceImpl} 的取值方式一致。
     */
    private UserInfo currentUserInfo() {
        return (UserInfo) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    private SysConfig toConfigEntity(SysConfigVo configVo) {
        return BeanUtil.toBean(configVo, SysConfig.class);
    }

    private SysConfigVo toConfigVo(SysConfig configEntity) {
        return BeanUtil.toBean(configEntity, SysConfigVo.class);
    }

    private List<SysConfigVo> toConfigVoList(List<SysConfig> configEntities) {
        List<SysConfigVo> configVos = new ArrayList<>();
        if (configEntities == null || configEntities.isEmpty()) {
            return configVos;
        }
        for (SysConfig configEntity : configEntities) {
            configVos.add(toConfigVo(configEntity));
        }
        return configVos;
    }
}
