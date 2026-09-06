/**
 * 文件作用：
 * 统一定义当前登录用户、个人中心和用户路由树相关的接口类型，
 * 供用户 store、个人中心页面和导航接口共同复用。
 */
import type { NormalizedPageResult, PageQueryParams } from '@/types/api/system/common'

export interface SysRoleRecord {
  roleId: number
  roleName: string
  roleLevel?: number
  roleKey?: string
  status?: number
  isDefault?: number
}

export interface SysUserProfile {
  userId: number
  username: string
  password?: string
  nickName: string
  phoneNumber: string
  sex: string
  /** 头像存储名（上传路径 avatar/ 子目录下的文件名；无头像为 undefined，前端显示字母占位） */
  avatar?: string
  status: number
  createTime?: string
  userRole: SysRoleRecord[]
  roleId: number[]
}

export interface UpdatePersonalProfilePayload {
  nickName: string
  phoneNumber: string
  sex: string
}

/**
 * 修改密码请求体，与后端 ModifyPasswordBody 字段保持一致。
 * 修改密码走独立接口 PUT /person/password，与资料编辑（PUT /person）分离。
 */
export interface ModifyPasswordRequestData {
  /** 原密码（必填，后端 BCrypt 校验） */
  oldPassword: string
  /** 新密码（必填，6-20 位） */
  newPassword: string
}

/**
 * 用户管理列表查询参数，与后端 UserQuarry 和 PageUtil 分页参数保持一致。
 */
export interface SysUserListQuery extends Partial<PageQueryParams> {
  username?: string
  nickName?: string
  phoneNumber?: string
  status?: number | undefined
  beginTime?: string
  endTime?: string
}

/**
 * 用户管理的增改表单结构。
 * 这里直接与后端 SysUserVo 对齐，方便详情回显和保存复用同一份模型。
 */
export interface SysUserFormData {
  userId?: number
  username: string
  password?: string
  nickName: string
  phoneNumber: string
  sex: string
  status: number
  roleId: number[]
  userRole?: SysRoleRecord[]
  createTime?: string
}

/**
 * 用户管理分页结果。
 */
export type SysUserPageResult = NormalizedPageResult<SysUserFormData>
