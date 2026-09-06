package com.rookie.system.runner;

import com.rookie.common.pojo.entity.SysConfig;
import com.rookie.common.util.SysConfigUtil;
import com.rookie.system.mapper.SysConfigMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 系统设置缓存启动预热。
 * <p>
 * 设计背景：{@link SysConfigUtil} 工具类只读 Redis 缓存、不走数据库，
 * 因此必须在应用启动时把数据库中的全部启用设置项加载进缓存，
 * 否则工具类首次读取会因缓存未命中而回落默认值。
 * <p>
 * 本 Runner 在 Spring 上下文就绪后执行：查全部启用设置项 → 逐条写入 Redis。
 * 预热失败不阻断启动（例如 Redis 暂时不可用），仅记录错误日志，
 * 后续可通过「系统设置」页的「刷新缓存」按钮或重启恢复。
 */
@Component
public class SysConfigWarmUpRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SysConfigWarmUpRunner.class);

    @Autowired
    SysConfigMapper sysConfigMapper;

    @Override
    public void run(ApplicationArguments args) {
        try {
            List<SysConfig> all = sysConfigMapper.getAllSysConfig();
            SysConfigUtil.setConfigs(all);
            log.info("系统设置缓存预热完成，共加载 {} 条启用设置项", all == null ? 0 : all.size());
        } catch (Exception e) {
            // 预热失败不阻断启动，待 Redis 恢复后可通过刷新缓存接口补齐
            log.error("系统设置缓存预热失败，将通过刷新缓存接口或重启恢复", e);
        }
    }
}
