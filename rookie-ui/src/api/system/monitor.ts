/**
 * 文件作用：
 * 集中管理服务监控相关的后端接口。
 * 监控采用可插拔的监控源设计（MonitorProvider），聚合接口一次返回全部监控源数据，
 * 新增中间件监控（Redis/MySQL 等）时后端加 Provider、前端登记渲染组件即可。
 */
import { get } from '@/utils/http'
import type { ApiResult } from '@/types/api/system/common'
import type { MonitorItemRecord } from '@/types/api/system/monitor'

/**
 * 方法效果：
 * 获取全部监控源的实时数据（服务器、以及后续接入的中间件等）。
 * 参数：
 * - 无。
 * 返回值：
 * - 后端 Result 包裹的监控数据项数组（type / title / data）。
 * 说明：
 * - 单个监控源采集失败不影响其他监控源（该项 data 为 null，前端占位）；
 * - 服务器监控源会做一次 CPU 双采样（约 300ms），接口整体延迟略高，属正常。
 */
export const getMonitorItemsApi = () => get<ApiResult<MonitorItemRecord[]>>('/sys/monitor/items')
