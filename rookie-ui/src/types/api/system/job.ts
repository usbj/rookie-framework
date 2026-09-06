/**
 * 文件作用：
 * 定义定时任务管理模块的接口类型，与后端 SysJobVo / SysJobLog 字段保持一致。
 */

import type { NormalizedPageResult, PageQueryParams } from '@/types/api/system/common'

/**
 * 定时任务记录，与后端 SysJobVo 对齐。
 */
export interface SysJobRecord {
  jobId?: number
  /** 任务名称 */
  jobName: string
  /** Spring Bean 名称（限定 com.rookie.system.task 包下） */
  beanName: string
  /** 执行方法名（无参或单个 String 参数） */
  methodName: string
  /** cron 表达式（Spring 6 段式） */
  cronExpression: string
  /** 执行参数（可选；方法为 String 单参时传入） */
  params?: string
  /** 状态：1启用 0停用 */
  status: number
  /** 备注说明 */
  remark?: string
  createTime?: string
  updateTime?: string
}

/**
 * 定时任务列表查询参数，与后端 SysJobQuarry 保持一致。
 */
export interface SysJobListQuery extends Partial<PageQueryParams> {
  jobName?: string
  status?: number | undefined
  beginTime?: string
  endTime?: string
}

/**
 * 定时任务分页结果。
 */
export type SysJobPageResult = NormalizedPageResult<SysJobRecord>

/**
 * 定时任务执行日志记录，与后端 SysJobLog 对齐。
 */
export interface SysJobLogRecord {
  logId: number
  jobId: number
  jobName: string
  /** 触发方式：AUTO 自动 MANUAL 手动 */
  triggerType: string
  /** 调用目标（bean.method） */
  invokeTarget: string
  jobParams?: string
  /** 执行状态：0成功 1失败 */
  status: number
  /** 耗时（毫秒） */
  costTime?: number
  /** 异常信息（失败时） */
  exceptionMsg?: string
  executeTime?: string
}

/**
 * 执行日志查询参数，与后端 SysJobLogQuarry 保持一致。
 */
export interface SysJobLogListQuery extends Partial<PageQueryParams> {
  jobId?: number
  jobName?: string
  triggerType?: string
  status?: number | undefined
  beginTime?: string
  endTime?: string
}

/**
 * 执行日志分页结果。
 */
export type SysJobLogPageResult = NormalizedPageResult<SysJobLogRecord>
