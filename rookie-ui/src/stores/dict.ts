import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { getSysDictAllApi, getSysDictDataByTypeApi } from '@/api/system/dict'
import type { SysDictDataRecord } from '@/types/api/system/dict'

export const DICT_CACHE_STORAGE_KEY = 'rookie-dict-cache'

type DictDataMap = Record<string, SysDictDataRecord[]>
type DictTagType = 'primary' | 'success' | 'warning' | 'danger' | 'info'
type DictTagEffect = 'dark' | 'light' | 'plain'

/**
 * 读取本地缓存的字典数据。
 * 这里优先恢复一份最近一次可用的字典内容，避免刷新后所有页面先空一拍。
 */
const readStoredDictCache = (): DictDataMap => {
  const rawCache = localStorage.getItem(DICT_CACHE_STORAGE_KEY)

  if (!rawCache) {
    return {}
  }

  try {
    return JSON.parse(rawCache) as DictDataMap
  } catch {
    localStorage.removeItem(DICT_CACHE_STORAGE_KEY)
    return {}
  }
}

const sortDictRecords = (records: SysDictDataRecord[]) =>
  [...records].sort((previous, next) => Number(previous.dictDataSort) - Number(next.dictDataSort))

const normalizeTagType = (tagType?: string): DictTagType => {
  const normalizedTagType = String(tagType ?? '').trim()

  if (
    normalizedTagType === 'primary' ||
    normalizedTagType === 'success' ||
    normalizedTagType === 'warning' ||
    normalizedTagType === 'danger' ||
    normalizedTagType === 'info'
  ) {
    return normalizedTagType
  }

  return 'info'
}

const normalizeTagEffect = (tagEffect?: string): DictTagEffect => {
  const normalizedTagEffect = String(tagEffect ?? '').trim()

  if (
    normalizedTagEffect === 'dark' ||
    normalizedTagEffect === 'light' ||
    normalizedTagEffect === 'plain'
  ) {
    return normalizedTagEffect
  }

  return 'plain'
}

export const useDictStore = defineStore('dict', () => {
  /**
   * 当前前端已缓存的所有字典数据，按 `dictKey` 分组存储。
   */
  const dictDataMap = ref<DictDataMap>(readStoredDictCache())

  /**
   * 标记当前会话是否已经完成过一次字典预加载。
   * 即使本地有缓存，也会在登录后的首轮导航中再刷新一次。
   */
  const initialized = ref(false)
  const loading = ref(false)
  const loadedKeys = ref<string[]>(Object.keys(dictDataMap.value))
  const pendingRequests = new Map<string, Promise<SysDictDataRecord[]>>()

  const loadedKeySet = computed(() => new Set(loadedKeys.value))

  const persistDictCache = () => {
    localStorage.setItem(DICT_CACHE_STORAGE_KEY, JSON.stringify(dictDataMap.value))
  }

  /**
   * 方法效果：
   * 用最新字典数组覆盖指定 `dictKey` 的缓存，并同步到本地存储。
   * 参数：
   * - `dictKey`：字典类型键值。
   * - `records`：该字典下的全部数据项。
   * 返回值：
   * - 无返回值；副作用是更新 store 内存状态和本地缓存。
   */
  const setDictData = (dictKey: string, records: SysDictDataRecord[]) => {
    const normalizedKey = dictKey.trim()

    if (!normalizedKey) {
      return
    }

    dictDataMap.value = {
      ...dictDataMap.value,
      [normalizedKey]: sortDictRecords(records),
    }

    if (!loadedKeySet.value.has(normalizedKey)) {
      loadedKeys.value = [...loadedKeys.value, normalizedKey]
    }

    persistDictCache()
  }

  /**
   * 方法效果：
   * 读取指定字典键值当前已经缓存的数据项数组。
   * 参数：
   * - `dictKey`：字典类型键值。
   * 返回值：
   * - 当前字典对应的数据项数组；未命中时返回空数组。
   */
  const getDictData = (dictKey: string) => {
    const normalizedKey = dictKey.trim()
    return dictDataMap.value[normalizedKey] ?? []
  }

  /**
   * 方法效果：
   * 根据字典键值和实际值读取完整字典项，供标签样式等展示逻辑复用。
   * 参数：
   * - `dictKey`：字典类型键值。
   * - `value`：待匹配的实际值。
   * 返回值：
   * - 命中时返回完整字典数据对象，未命中返回 `null`。
   */
  const getDictRecord = (dictKey: string, value: unknown) => {
    const records = getDictData(dictKey)

    return (
      records.find((item) => String(item.dictDataValue) === String(value)) ??
      null
    )
  }

  /**
   * 方法效果：
   * 把字典数据转换成通用下拉选项数组，供 `ElSelect` 或公共表单使用。
   * 参数：
   * - `dictKey`：字典类型键值。
   * - `valueType`：控制返回值是字符串还是数字。
   * 返回值：
   * - `{ label, value }[]` 结构的选项数组。
   */
  const getDictOptions = (dictKey: string, valueType: 'string' | 'number' = 'string') =>
    getDictData(dictKey).map((item) => ({
      label: item.dictDataLabel,
      value: valueType === 'number' ? Number(item.dictDataValue) : item.dictDataValue,
    }))

  /**
   * 方法效果：
   * 根据字典键值和实际值，解析出对应展示文本。
   * 参数：
   * - `dictKey`：字典类型键值。
   * - `value`：待匹配的实际值，支持单值或数组。
   * 返回值：
   * - 单值时返回文本；
   * - 数组时返回文本数组；
   * - 未命中时回退原始值字符串。
   */
  const getDictLabel = (dictKey: string, value: unknown): string | string[] => {
    const records = getDictData(dictKey)

    const resolveSingleLabel = (currentValue: unknown) => {
      const matchedRecord = records.find(
        (item) => String(item.dictDataValue) === String(currentValue),
      )

      return matchedRecord?.dictDataLabel ?? String(currentValue ?? '')
    }

    if (Array.isArray(value)) {
      return value.map((item) => resolveSingleLabel(item))
    }

    return resolveSingleLabel(value)
  }

  /**
   * 方法效果：
   * 按单个 `dictKey` 拉取远端字典数据，并写入缓存。
   * 参数：
   * - `dictKey`：目标字典类型键值。
   * - `force`：是否忽略当前内存缓存强制刷新。
   * 返回值：
   * - Promise，resolve 为该字典的最新数据项数组。
   */
  const fetchDictDataByKey = async (dictKey: string, force = false) => {
    const normalizedKey = dictKey.trim()

    if (!normalizedKey) {
      return []
    }

    if (!force && loadedKeySet.value.has(normalizedKey)) {
      return getDictData(normalizedKey)
    }

    const existingRequest = pendingRequests.get(normalizedKey)
    if (existingRequest) {
      return existingRequest
    }

    const request = getSysDictDataByTypeApi(normalizedKey)
      .then((result) => {
        const records = sortDictRecords(result.data ?? [])
        setDictData(normalizedKey, records)
        return records
      })
      .finally(() => {
        pendingRequests.delete(normalizedKey)
      })

    pendingRequests.set(normalizedKey, request)
    return request
  }

  /**
   * 方法效果：
   * 登录后预加载全部启用字典类型对应的数据项，供全局页面直接消费。
   * 走无权限的 GET /sys/dict/all 拿字典类型列表（让没有字典管理权限的普通用户也能用字典功能），
   * 再逐个按 dictKey 调 GET /sys/dist/data/type/{dictKey} 拉数据项。
   * 参数：
   * - `force`：是否强制重新拉取所有字典。
   * 返回值：
   * - Promise<void>，在全部拉取完成后结束。
   */
  const initializeDictionaries = async (force = false) => {
    if (initialized.value && !force) {
      return
    }

    loading.value = true

    try {
      const result = await getSysDictAllApi()

      const dictKeys = Array.from(
        new Set(
          (result.data ?? [])
            .map((item) => item.dictKey?.trim())
            .filter((item): item is string => Boolean(item)),
        ),
      )

      // 初始化阶段总是从后端拉取最新字典数据，
      // 避免 localStorage 缓存导致新增字典项（如 REVOKED 状态）无法进入前端。
      await Promise.allSettled(
        dictKeys.map((dictKey) => fetchDictDataByKey(dictKey, true)),
      )
      initialized.value = true
    } finally {
      loading.value = false
    }
  }

  /**
   * 方法效果：
   * 清空当前会话和本地持久化的全部字典缓存。
   * 参数：
   * - 无。
   * 返回值：
   * - 无返回值；副作用是重置字典缓存状态。
   */
  const clearDictCache = () => {
    dictDataMap.value = {}
    loadedKeys.value = []
    initialized.value = false
    loading.value = false
    pendingRequests.clear()
    localStorage.removeItem(DICT_CACHE_STORAGE_KEY)
  }

  return {
    dictDataMap,
    initialized,
    loading,
    loadedKeys,
    setDictData,
    getDictData,
    getDictRecord,
    getDictOptions,
    getDictLabel,
    normalizeTagType,
    normalizeTagEffect,
    fetchDictDataByKey,
    initializeDictionaries,
    clearDictCache,
  }
})
