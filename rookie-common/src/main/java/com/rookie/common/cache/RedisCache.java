package com.rookie.common.cache;


import cn.hutool.json.JSONUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;


/**
 *
 * redis的key集中管制
 * */

@Component
public class RedisCache {

    @Autowired
    StringRedisTemplate redisTemplate;

    @Value("${redis.base-key}")
    private String BASE_KEY;

    @Value("${redis.expire-time}")
    private String EXPIRE_TIME;

    public boolean setCache(String key, String value, long time, TimeUnit type) {
        if (key.isEmpty()) {
            return false;
        }
        String integrity = BASE_KEY+key;
        redisTemplate.opsForValue().set(integrity,value,time,type);
        return true;
    }

    public boolean setCacheToSetTime(String key, String value) {
        return setCache(key,value,Long.parseLong(EXPIRE_TIME),TimeUnit.MINUTES);
    }


    public boolean persistentSetCache(String key, String value) {
        if (key.isEmpty()) {
            return false;
        }
        String integrity = BASE_KEY+key;
        redisTemplate.opsForValue().set(integrity,value);
        return true;
    }

    public String getCacheJson(String key) {
        if (key.isEmpty()) {
            return null;
        }
        String integrity = BASE_KEY+key;
        return redisTemplate.opsForValue().get(integrity);
    }

    public <T> T getObjectCache(String key, Class<T> tClass) {
        return JSONUtil.toBean(getCacheJson(key), tClass);
    }

//    public UserInfo getUserInfoCache(String key) {
//        return getObjectCache(key, UserInfo.class);
//    }

    public boolean expire(String key,Long time,TimeUnit type) {
        if (key.isEmpty()) {
            return false;
        }
        String integrity = BASE_KEY+key;
        return redisTemplate.expire(integrity,time,type);
    }

    public boolean changeTimeToSetTime(String key) {
        return expire(key,Long.parseLong(EXPIRE_TIME),TimeUnit.MINUTES);
    }

    public boolean changeTimeInSeconds(String key, Long time) {
        return expire(key,time,TimeUnit.SECONDS);
    }

    public boolean deleteCache(String key) {
        if (key.isEmpty()) {
            return false;
        }
        String integrity = BASE_KEY+key;
        return redisTemplate.delete(integrity);
    }

    public boolean deleteCaches(Collection<String> keys){
        return redisTemplate.delete(keys) > 0;
    }

    public <T> List<T> getListCache(String key, Class<T> tClass){
        String json = getCacheJson(key);
        return JSONUtil.toList(json, tClass);
    }

    public Collection<String> keys(String pattern){
        return redisTemplate.keys(BASE_KEY+pattern);
    }

    // ==================== ZSET（有序集合）操作 ====================
    // 用于在线用户统计等按分数排序/区间计数的场景。

    /**
     * 向有序集合写入成员分数（已存在则更新分数，等同 ZADD 语义）。
     *
     * @param key    业务 key（自动加 base-key 前缀）
     * @param member 成员
     * @param score  分数（在线统计中为最后活跃时间戳）
     */
    public void zAdd(String key, String member, double score) {
        redisTemplate.opsForZSet().add(BASE_KEY + key, member, score);
    }

    /**
     * 统计有序集合中分数落在 [min, max] 区间内的成员数量（含边界）。
     * 在线统计：min = 阈值时间戳，max = Double.POSITIVE_INFINITY，即"最近活跃过"的人数。
     *
     * @param key 业务 key
     * @param min 分数下界（含）
     * @param max 分数上界（含）
     * @return 区间内成员数；key 不存在返回 0
     */
    public long zCount(String key, double min, double max) {
        Long count = redisTemplate.opsForZSet().count(BASE_KEY + key, min, max);
        return count == null ? 0L : count;
    }

    /**
     * 按分数从大到小取有序集合成员及其分数（倒序区间）。
     *
     * @param key   业务 key
     * @param start 起始下标（0 起，含）
     * @param end   结束下标（含，-1 表示取到末尾）
     * @return 成员与分数的元组集合（分数即最后活跃时间戳）
     */
    public Set<ZSetOperations.TypedTuple<String>> zRevRangeWithScores(String key, long start, long end) {
        return redisTemplate.opsForZSet().reverseRangeWithScores(BASE_KEY + key, start, end);
    }

    /**
     * 从有序集合移除成员（不存在时静默忽略）。
     *
     * @param key     业务 key
     * @param members 待移除成员
     */
    public void zRem(String key, String... members) {
        redisTemplate.opsForZSet().remove(BASE_KEY + key, members);
    }

    /**
     * 按分数区间批量移除有序集合成员（含边界）。
     * 在线统计用：把 score 早于阈值的离线成员一次性清掉，防止集合无限累积历史成员。
     *
     * @param key 业务 key
     * @param min 分数下界（含）
     * @param max 分数上界（含）
     */
    public void zRemRangeByScore(String key, double min, double max) {
        redisTemplate.opsForZSet().removeRangeByScore(BASE_KEY + key, min, max);
    }
}
