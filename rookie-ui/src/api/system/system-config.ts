/**
 * 文件作用：
 * 封装系统设置（system_config）模块的后端接口请求方法，
 * 对齐后端 SysConfigController（/sys/system-config），供页面消费。
 * 包含分页查询、详情、新增、编辑、删除、刷新缓存与按 key 取值七个接口。
 */
import { del, get, getPage, post, put } from '@/utils/http'
import type { ApiResult } from '@/types/api/system/common'
import type {
  SysConfigListQuery,
  SysConfigPageResult,
  SysConfigRecord,
} from '@/types/api/system/system-config'

/** 分页查询系统设置列表 */
export const getSysConfigPageApi = (params: SysConfigListQuery) =>
  getPage<SysConfigRecord>('/sys/system-config/list', { params }) as Promise<SysConfigPageResult>

/** 查询系统设置详情 */
export const getSysConfigDetailApi = (configId: number) =>
  get<ApiResult<SysConfigRecord>>(`/sys/system-config/${configId}`)

/** 新增系统设置 */
export const createSysConfigApi = (data: SysConfigRecord) =>
  post<ApiResult<boolean>, SysConfigRecord>('/sys/system-config', data)

/** 编辑系统设置 */
export const updateSysConfigApi = (data: SysConfigRecord) =>
  put<ApiResult<boolean>, SysConfigRecord>('/sys/system-config', data)

/** 删除系统设置 */
export const deleteSysConfigApi = (configId: number) =>
  del<ApiResult<boolean>>(`/sys/system-config/${configId}`)

/**
 * 刷新系统设置缓存。
 * 与字典刷新不同：系统设置缓存在后端 Redis，此接口让后端清空并立即重新预热全部启用项，
 * 不走前端本地缓存刷新。
 */
export const refreshSysConfigCacheApi = () =>
  post<ApiResult<boolean>>('/sys/system-config/refresh')

/**
 * 按设置键获取当前设置值，供前端按需读取运用（对标若依 getConfigKey）。
 * 公共读取接口，仅需登录即可，不返回 valueType 等元信息，避免全量暴露关键设置。
 * 命中返回值字符串，未命中或停用返回 null。
 */
export const getSysConfigValueApi = (configKey: string) =>
  get<ApiResult<string | null>>(`/sys/system-config/configKey/${configKey}`)
