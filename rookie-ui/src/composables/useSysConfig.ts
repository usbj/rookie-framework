/**
 * 文件作用：
 * 提供组件级系统设置读取能力，对标若依 config store 的按需异步拉取模式。
 * 与字典 useDict 的「同步读内存缓存」不同：系统设置按 `configKey` 异步调接口取值，
 * 避免全量预加载暴露关键设置。
 * 页面调用时需 `await`，例如 `const enabled = await useSysConfig().getBoolean('xxx')`。
 * 类型转换规则：
 * - STRING：原样返回字符串。
 * - BOOLEAN：`"true"` / `"1"` 视为 true，`"false"` / `"0"` 视为 false，其余回落默认值。
 * - NUMBER：`Number(value)`，NaN 回落默认值。
 * - JSON：`JSON.parse`，失败回落默认值。
 * 关键参数：
 * - `configKey`：设置项键值，对应后端 `system_config.config_key`。
 * - `defaultValue`：未命中、停用或转换失败时的回退值。
 * 返回值：各方法均返回 Promise，调用方需 await。
 */
import { useSysConfigStore } from '@/stores/system-config'

export const useSysConfig = () => {
  const sysConfigStore = useSysConfigStore()

  /**
   * 方法效果：
   * 按 `configKey` 异步读取字符串设置值。
   * 参数：
   * - `configKey`：设置项键值。
   * - `defaultValue`：未命中或停用时的回退值。
   * 返回值：
   * - Promise<string>，命中返回值，否则返回 defaultValue。
   */
  const getString = async (configKey: string, defaultValue = ''): Promise<string> => {
    const value = await sysConfigStore.fetchSysConfig(configKey)
    if (value === null || value === undefined || value === '') {
      return defaultValue
    }
    return value
  }

  /**
   * 方法效果：
   * 按 `configKey` 异步读取布尔设置值。
   * 参数：
   * - `configKey`：设置项键值。
   * - `defaultValue`：未命中、停用或无法识别时的回退值。
   * 返回值：
   * - Promise<boolean>。
   */
  const getBoolean = async (configKey: string, defaultValue = false): Promise<boolean> => {
    const value = await sysConfigStore.fetchSysConfig(configKey)
    if (value === null || value === undefined || value === '') {
      return defaultValue
    }
    const raw = String(value).trim().toLowerCase()
    if (raw === 'true' || raw === '1') {
      return true
    }
    if (raw === 'false' || raw === '0') {
      return false
    }
    return defaultValue
  }

  /**
   * 方法效果：
   * 按 `configKey` 异步读取数值设置值。
   * 参数：
   * - `configKey`：设置项键值。
   * - `defaultValue`：未命中、停用或解析失败时的回退值。
   * 返回值：
   * - Promise<number>。
   */
  const getNumber = async (configKey: string, defaultValue = 0): Promise<number> => {
    const value = await sysConfigStore.fetchSysConfig(configKey)
    if (value === null || value === undefined || value === '') {
      return defaultValue
    }
    const parsed = Number(value)
    return Number.isNaN(parsed) ? defaultValue : parsed
  }

  /**
   * 方法效果：
   * 按 `configKey` 异步读取 JSON 对象设置值并反序列化为指定类型。
   * 参数：
   * - `configKey`：设置项键值。
   * - `defaultValue`：未命中、停用或解析失败时的回退值。
   * 返回值：
   * - Promise<T>。
   */
  const getObject = async <T>(configKey: string, defaultValue: T): Promise<T> => {
    const value = await sysConfigStore.fetchSysConfig(configKey)
    if (value === null || value === undefined || value === '') {
      return defaultValue
    }
    try {
      return JSON.parse(value) as T
    } catch {
      return defaultValue
    }
  }

  /**
   * 方法效果：
   * 按 `configKey` 异步读取 JSON 数组设置值并反序列化为指定元素类型的数组。
   * 参数：
   * - `configKey`：设置项键值。
   * - `defaultValue`：未命中、停用或解析失败时的回退值。
   * 返回值：
   * - Promise<T[]>。
   */
  const getList = async <T>(configKey: string, defaultValue: T[] = []): Promise<T[]> => {
    const parsed = await getObject<T[] | null>(configKey, null)
    return Array.isArray(parsed) ? parsed : defaultValue
  }

  return {
    sysConfigStore,
    getString,
    getBoolean,
    getNumber,
    getObject,
    getList,
  }
}
