/**
 * 文件作用：
 * 定义系统设置（system_config）模块与后端对接的类型，
 * 与后端 SysConfigVo / SysConfigQuarry 对齐，供前端 api 与页面消费。
 */
import type { NormalizedPageResult, PageQueryParams } from '@/types/api/system/common'

/**
 * 系统设置记录，对齐后端 SysConfigVo。
 * - valueType：值类型，取值 STRING / BOOLEAN / NUMBER / JSON，前端走字典 sys_config_value_type 翻译。
 * - isSystem：是否系统内置项（1是 0否），内置项前端禁用删除、禁用改键与类型。
 */
export interface SysConfigRecord {
  configId?: number
  configKey: string
  configName: string
  configValue: string
  valueType: string
  isSystem?: number
  remark?: string
  status: number
  createTime?: string
  updateTime?: string
}

/** 系统设置列表查询参数，对齐后端 SysConfigQuarry + 分页参数 + 创建时间范围 */
export interface SysConfigListQuery extends Partial<PageQueryParams> {
  configKey?: string
  configName?: string
  status?: number | undefined
  beginTime?: string
  endTime?: string
}

/** 系统设置分页结果 */
export type SysConfigPageResult = NormalizedPageResult<SysConfigRecord>
