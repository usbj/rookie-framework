/**
 * 文件作用：
 * 定义在线用户统计模块的接口类型，与后端 OnlineUserEntry 字段保持一致。
 * 在线判定基于后端 Redis 在线集合（最后活跃时间在阈值内视为在线）。
 */

/**
 * 在线用户展示条目，与后端 OnlineUserEntry 对齐。
 */
export interface OnlineUserRecord {
  /** 登录账号（在线集合成员） */
  username: string
  /** 昵称（来自登录态缓存，无则空） */
  nickName?: string
  /** 登录 IP（来自登录态缓存，无则空） */
  loginIp?: string
  /** 登录时间（毫秒时间戳） */
  loginTime?: number
  /** 最后活跃时间（毫秒时间戳） */
  lastActive?: number
}
