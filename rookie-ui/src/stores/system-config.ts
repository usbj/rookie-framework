/**
 * 文件作用：
 * 系统设置（system_config）Pinia store，对标若依 config store 的按需拉取模式。
 * 与字典 store 的「登录后全量预加载」不同：系统设置可能含不宜整体暴露的关键信息，
 * 故改为按 `configKey` 单项异步拉取，内存只缓存「已请求过的 key 的值」，
 * 未请求的 key 不会进缓存、也不会被下发，避免全量暴露。
 * 关键能力：
 * - `configMap`：按 `configKey` 存放已拉取过的设置值（仅值字符串，不存整条记录）。
 * - `fetchSysConfig(key, force)`：按 key 拉取设置值，命中且非 force 时复用缓存，否则调接口并写缓存（含 null，避免重复请求未命中项）。
 * - `clearSysConfigCache()`：退出登录时清空内存与本地缓存。
 */
import { ref } from 'vue'
import { defineStore } from 'pinia'
import { getSysConfigValueApi } from '@/api/system/system-config'

export const SYS_CONFIG_CACHE_STORAGE_KEY = 'rookie-system-config-cache'

type SysConfigValueMap = Record<string, string | null>

/**
 * 读取本地缓存的系统设置值。
 * 优先恢复最近一次已拉取的设置值，避免刷新后对已知 key 重复请求。
 */
const readStoredSysConfigCache = (): SysConfigValueMap => {
  const rawCache = localStorage.getItem(SYS_CONFIG_CACHE_STORAGE_KEY)

  if (!rawCache) {
    return {}
  }

  try {
    return JSON.parse(rawCache) as SysConfigValueMap
  } catch {
    localStorage.removeItem(SYS_CONFIG_CACHE_STORAGE_KEY)
    return {}
  }
}

export const useSysConfigStore = defineStore('system-config', () => {
  /**
   * 当前前端已缓存的所有系统设置值，按 `configKey` 索引存储。
   * 仅记录「已请求过的 key」，不是全量；null 表示该 key 已确认未命中/停用。
   */
  const configMap = ref<SysConfigValueMap>(readStoredSysConfigCache())

  const persistSysConfigCache = () => {
    localStorage.setItem(SYS_CONFIG_CACHE_STORAGE_KEY, JSON.stringify(configMap.value))
  }

  /**
   * 方法效果：
   * 按 `configKey` 异步拉取设置值，命中且非强制时复用内存缓存，否则调后端接口并写缓存。
   * 未命中/停用的 key 也会把 null 写入缓存，避免同一未命中 key 被重复请求。
   * 参数：
   * - `configKey`：设置项键值。
   * - `force`：是否强制重新拉取（绕过缓存）。
   * 返回值：
   * - 设置值字符串；未命中或停用时返回 null。
   */
  const fetchSysConfig = async (configKey: string, force = false): Promise<string | null> => {
    const normalizedKey = configKey.trim()
    if (!normalizedKey) {
      return null
    }

    if (!force && Object.prototype.hasOwnProperty.call(configMap.value, normalizedKey)) {
      return configMap.value[normalizedKey] ?? null
    }

    const result = await getSysConfigValueApi(normalizedKey)
    const value = result.data ?? null

    configMap.value = { ...configMap.value, [normalizedKey]: value }
    persistSysConfigCache()
    return value
  }

  /**
   * 方法效果：
   * 清空当前会话和本地持久化的全部系统设置缓存。
   * 参数：
   * - 无。
   * 返回值：
   * - 无返回值；副作用是重置系统设置缓存状态。
   */
  const clearSysConfigCache = () => {
    configMap.value = {}
    localStorage.removeItem(SYS_CONFIG_CACHE_STORAGE_KEY)
  }

  return {
    configMap,
    fetchSysConfig,
    clearSysConfigCache,
  }
})
