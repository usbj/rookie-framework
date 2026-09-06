package com.rookie.common.util;

import cn.hutool.json.JSONUtil;
import com.rookie.common.cache.RedisCache;
import com.rookie.common.pojo.entity.SysConfig;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * 系统设置缓存工具（对标 {@link DictUtil}）。
 * <p>
 * 缓存策略：<b>数据库为唯一源，Redis 为永久缓存副本，工具类只读缓存、不走数据库。</b>
 * 真实 key = {@code rookie:framework:} 前缀（配置 {@code redis.base-key}）+ {@code sys_config:<configKey>}。
 * 启动时由预热钩子把全部设置项加载进缓存；增改删由 Service 层同步重写/删除缓存；
 * 「刷新缓存」接口清空后立即重新预热全部。
 * <p>
 * 因此 {@link #getConfig(String)} 只从 Redis 读取，缓存未命中返回 {@code null}，
 * 不会回源数据库。业务侧应使用带默认值的类型化方法（{@link #getString} 等），
 * 在缓存缺失或转换失败时回落到默认值，避免空指针。
 * <p>
 * 所有方法为 static，通过 {@link SpringUtil} 获取 {@link RedisCache} 实例，
 * 与 {@link DictUtil} 保持一致的静态工具风格。
 */
public class SysConfigUtil {

    /** Redis key 业务前缀，真实 key = BASE_KEY + CONFIG_KEY + configKey */
    private static final String CONFIG_KEY = "sys_config:";

    /** 默认值类型常量，与表 value_type 字段取值对齐 */
    private static final String TYPE_STRING = "STRING";
    private static final String TYPE_BOOLEAN = "BOOLEAN";
    private static final String TYPE_NUMBER = "NUMBER";
    private static final String TYPE_JSON = "JSON";

    /**
     * 写入单条设置项到缓存（永久，无过期）。
     * Service 层增/改后调用，启动预热与刷新预热也复用此方法逐条写入。
     *
     * @param config 设置项实体；为 null 或 key 为空时跳过
     */
    public static void setConfig(SysConfig config) {
        if (config == null || config.getConfigKey() == null || config.getConfigKey().isEmpty()) {
            return;
        }
        SpringUtil.getBean(RedisCache.class).persistentSetCache(
                CONFIG_KEY + config.getConfigKey(), JSONUtil.toJsonStr(config));
    }

    /**
     * 批量写入设置项到缓存（供启动预热 / 刷新预热使用），逐条永久缓存。
     *
     * @param configs 设置项实体列表；为 null 或空时跳过
     */
    public static void setConfigs(List<SysConfig> configs) {
        if (configs == null || configs.isEmpty()) {
            return;
        }
        for (SysConfig config : configs) {
            setConfig(config);
        }
    }

    /**
     * 只读缓存获取单条设置项实体。<b>不走数据库</b>，缓存未命中返回 null。
     *
     * @param configKey 设置键
     * @return 缓存中的设置项实体；不存在或 key 为空时返回 null
     */
    public static SysConfig getConfig(String configKey) {
        if (configKey == null || configKey.isEmpty()) {
            return null;
        }
        String json = SpringUtil.getBean(RedisCache.class).getCacheJson(CONFIG_KEY + configKey);
        if (json == null || json.isEmpty()) {
            return null;
        }
        return JSONUtil.toBean(json, SysConfig.class);
    }

    /**
     * 删除单条设置项缓存。Service 层删除设置项后调用。
     *
     * @param configKey 设置键
     */
    public static void removeConfig(String configKey) {
        if (configKey == null || configKey.isEmpty()) {
            return;
        }
        SpringUtil.getBean(RedisCache.class).deleteCache(CONFIG_KEY + configKey);
    }

    /**
     * 清空全部系统设置缓存（按 sys_config:* 前缀扫描删除）。
     * 「刷新缓存」接口调用，清空后由调用方立即重新预热。
     */
    public static void clearAllConfig() {
        RedisCache redisCache = SpringUtil.getBean(RedisCache.class);
        Collection<String> keys = redisCache.keys(CONFIG_KEY + "*");
        if (keys != null && !keys.isEmpty()) {
            redisCache.deleteCaches(keys);
        }
    }

    // ==================== 类型化读取（带默认值回落） ====================

    /**
     * 读取字符串类型设置项。
     * 缓存未命中、设置项停用、值类型不符或值为空时，回落到 {@code defaultValue}。
     *
     * @param configKey    设置键
     * @param defaultValue 默认值
     * @return 设置值或默认值
     */
    public static String getString(String configKey, String defaultValue) {
        SysConfig config = getConfig(configKey);
        if (config == null || !isEnabled(config) || !TYPE_STRING.equals(config.getValueType())) {
            return defaultValue;
        }
        return config.getConfigValue() != null ? config.getConfigValue() : defaultValue;
    }

    /**
     * 读取布尔类型设置项。支持 "true"/"1"（不区分大小写）解析为 true，其余为 false。
     * 缓存未命中、停用、类型不符或值为空时回落到 {@code defaultValue}。
     *
     * @param configKey    设置键
     * @param defaultValue 默认值
     * @return 布尔值或默认值
     */
    public static Boolean getBoolean(String configKey, Boolean defaultValue) {
        SysConfig config = getConfig(configKey);
        if (config == null || !isEnabled(config) || !TYPE_BOOLEAN.equals(config.getValueType())) {
            return defaultValue;
        }
        String value = config.getConfigValue();
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    /**
     * 读取数值类型设置项，返回 Long。
     * 缓存未命中、停用、类型不符或值无法解析为数字时回落到 {@code defaultValue}。
     *
     * @param configKey    设置键
     * @param defaultValue 默认值
     * @return Long 值或默认值
     */
    public static Long getNumber(String configKey, Long defaultValue) {
        SysConfig config = getConfig(configKey);
        if (config == null || !isEnabled(config) || !TYPE_NUMBER.equals(config.getValueType())) {
            return defaultValue;
        }
        String value = config.getConfigValue();
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 读取 JSON 类型设置项并反序列化为指定类型对象。
     * 缓存未命中、停用、类型不符或解析失败时回落到 {@code defaultValue}。
     *
     * @param configKey    设置键
     * @param clazz        目标类型
     * @param defaultValue 默认值
     * @param <T>          目标类型
     * @return 反序列化对象或默认值
     */
    public static <T> T getObject(String configKey, Class<T> clazz, T defaultValue) {
        SysConfig config = getConfig(configKey);
        if (config == null || !isEnabled(config) || !TYPE_JSON.equals(config.getValueType())) {
            return defaultValue;
        }
        String value = config.getConfigValue();
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return JSONUtil.toBean(value, clazz);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * 读取 JSON 数组类型设置项并反序列化为指定元素类型的 List。
     * 缓存未命中、停用、类型不符或解析失败时回落到 {@code defaultValue}。
     *
     * @param configKey    设置键
     * @param clazz        元素类型
     * @param defaultValue 默认值
     * @param <T>          元素类型
     * @return 反序列化 List 或默认值
     */
    public static <T> List<T> getList(String configKey, Class<T> clazz, List<T> defaultValue) {
        SysConfig config = getConfig(configKey);
        if (config == null || !isEnabled(config) || !TYPE_JSON.equals(config.getValueType())) {
            return defaultValue;
        }
        String value = config.getConfigValue();
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            List<T> list = JSONUtil.toList(value, clazz);
            return list != null ? list : Collections.emptyList();
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * 判断设置项是否处于启用状态（status=1）。
     * 停用的设置项读取时回落默认值，等同缓存不存在。
     */
    private static boolean isEnabled(SysConfig config) {
        return config.getStatus() != null && config.getStatus() == 1;
    }
}
