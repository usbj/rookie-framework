import type { ApiResult } from '@/types/api/system/common'
export type { ApiResult } from '@/types/api/system/common'

/**
 * 登录表单提交参数。
 * 与后端 LoginBody 中的 username、password 字段保持一致。
 */
export interface LoginRequestData {
  username: string
  password: string
}

/**
 * 注册表单提交参数。
 * 与后端 RegisterBody 中的字段保持一致；
 * nickName 为空时后端默认取 username，sex 为空时后端默认 '0'。
 */
export interface RegisterRequestData {
  /** 用户账号（必填，≤12 位字母数字下划线） */
  username: string
  /** 用户密码（必填，6-20 位） */
  password: string
  /** 用户昵称（可选，空则默认 username） */
  nickName?: string
  /** 手机号（可选，11 位） */
  phoneNumber?: string
  /** 性别（可选，'0' 男 / '1' 女 / '3' 未知，空则默认 '0'） */
  sex?: string
}

/**
 * 当前后端登录接口返回的 data 实际上只有 token 字符串。
 */
export type LoginResponseData = string
