/**
 * 文件作用：
 * 集中管理定时任务模块相关的后端接口，
 * 包括任务 CRUD、启停、立即执行与执行日志查询。
 */
import { del, get, getPage, post, put } from '@/utils/http'
import type { ApiResult } from '@/types/api/system/common'
import type {
  SysJobListQuery,
  SysJobLogListQuery,
  SysJobLogPageResult,
  SysJobLogRecord,
  SysJobPageResult,
  SysJobRecord,
} from '@/types/api/system/job'

/**
 * 方法效果：
 * 获取定时任务分页列表，并在请求层完成分页结果归一化。
 * 参数：
 * - `params`：列表查询条件与分页参数。
 * 返回值：
 * - 归一化后的任务分页结果。
 */
export const getSysJobPageApi = (params: SysJobListQuery) =>
  getPage<SysJobRecord>('/sys/job/list', { params }) as Promise<SysJobPageResult>

/**
 * 方法效果：
 * 根据任务主键获取任务详情。
 * 参数：
 * - `jobId`：任务主键。
 * 返回值：
 * - 后端 Result 包裹的任务详情。
 */
export const getSysJobDetailApi = (jobId: number) =>
  get<ApiResult<SysJobRecord>>(`/sys/job/${jobId}`)

/**
 * 方法效果：
 * 新增定时任务（保存前后端校验 cron / Bean 白名单 / 方法存在性；启用则立即注册调度）。
 * 参数：
 * - `data`：任务表单数据。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const createSysJobApi = (data: SysJobRecord) =>
  post<ApiResult<boolean>, SysJobRecord>('/sys/job', data)

/**
 * 方法效果：
 * 编辑定时任务（保存后重新注册调度，新配置立即生效）。
 * 参数：
 * - `data`：任务表单数据（含 jobId）。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const updateSysJobApi = (data: SysJobRecord) =>
  put<ApiResult<boolean>, SysJobRecord>('/sys/job', data)

/**
 * 方法效果：
 * 删除定时任务（取消调度 + 删除记录 + 级联清理执行日志）。
 * 参数：
 * - `jobId`：任务主键。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const deleteSysJobApi = (jobId: number) =>
  del<ApiResult<boolean>>(`/sys/job/${jobId}`)

/**
 * 方法效果：
 * 修改任务状态（1启用 0停用），动态注册/取消调度。
 * 参数：
 * - `jobId`：任务主键。
 * - `status`：目标状态。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const changeSysJobStatusApi = (jobId: number, status: number) =>
  put<ApiResult<boolean>>('/sys/job/status', undefined, {
    params: { jobId, status },
  })

/**
 * 方法效果：
 * 立即执行一次任务（手动触发，不走 cron 调度；执行中会被防重拦截）。
 * 参数：
 * - `jobId`：任务主键。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const runSysJobApi = (jobId: number) =>
  post<ApiResult<boolean>>(`/sys/job/run/${jobId}`)

/**
 * 方法效果：
 * 获取任务执行日志分页列表（可带 jobId 只看某任务的日志）。
 * 参数：
 * - `params`：日志查询条件与分页参数。
 * 返回值：
 * - 归一化后的执行日志分页结果。
 */
export const getSysJobLogPageApi = (params: SysJobLogListQuery) =>
  getPage<SysJobLogRecord>('/sys/job/log/list', { params }) as Promise<SysJobLogPageResult>
