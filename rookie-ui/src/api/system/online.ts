/**
 * 文件作用：
 * 集中管理在线用户统计相关的后端接口，
 * 包括心跳（维持挂机在线）、在线人数、在线列表与强制下线。
 */
import { get, post } from '@/utils/http'
import type { ApiResult } from '@/types/api/system/common'
import type { OnlineUserRecord } from '@/types/api/system/online'

/**
 * 方法效果：
 * 在线心跳：前端登录后定时调用，触发后端记录当前用户活跃时间。
 * 参数：
 * - 无。
 * 返回值：
 * - 后端 Result 包裹的布尔成功标记。
 * 说明：
 * - 实际活跃记录由后端 TokenVerifyFilter 在每个已登录请求写入，本接口仅作定时触发点；
 * - 静默请求（silent）：心跳失败不弹错误提示，避免断网时每分钟弹一次。
 */
export const pingOnlineApi = () =>
  get<ApiResult<boolean>>('/sys/online/ping', { silent: true })

/**
 * 方法效果：
 * 获取当前在线人数。
 * 参数：
 * - 无。
 * 返回值：
 * - 后端 Result 包裹的在线人数（最后活跃时间在阈值内的用户数）。
 */
export const getOnlineCountApi = () => get<ApiResult<number>>('/sys/online/count')

/**
 * 方法效果：
 * 获取在线用户列表（按最后活跃时间倒序）。
 * 参数：
 * - 无。
 * 返回值：
 * - 后端 Result 包裹的在线用户展示条目数组。
 */
export const getOnlineListApi = () => get<ApiResult<OnlineUserRecord[]>>('/sys/online/list')

/**
 * 方法效果：
 * 强制下线指定账号（移除在线集合 + 删除登录态缓存，旧 token 立即失效）。
 * 参数：
 * - `username`：被强制下线的登录账号。
 * 返回值：
 * - 后端 Result 包裹的布尔成功标记。
 */
export const kickOnlineUserApi = (username: string) =>
  post<ApiResult<boolean>>(`/sys/online/logout/${encodeURIComponent(username)}`)
