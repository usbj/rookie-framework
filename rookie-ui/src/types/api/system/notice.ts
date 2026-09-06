/**
 * 文件作用：
 * 定义通知管理与通知分组管理对接后端所需的接口类型，
 * 与后端 SysNoticeVo / SysNoticeGroupVo / SysNoticeGroupMember / NoticeQuarry / NoticeGroupQuarry 对齐，
 * 供 api 层和页面层统一消费。
 */
import type { NormalizedPageResult, PageQueryParams } from './common'

/* 通知指定成员回显用的展示信息，与后端 NoticeTargetUserVo 对齐。 */
export interface NoticeTargetUserRecord {
  userId: number
  username?: string
  nickName?: string
  phoneNumber?: string
  status?: number
}

/**
 * 通知主体记录，与后端 SysNoticeVo 字段对齐。
 * isTop / needConfirm 后端为 Integer，前端统一用 number（0/1）。
 * groupIds 用于发布范围为分组时携带关联分组主键集合；
 * targetUserIds / targetUsers 用于发布范围为"指定成员"(USER) 时携带目标用户主键与回显展示信息。
 */
export interface SysNoticeRecord {
  noticeId?: number
  title: string
  content: string
  noticeType: string
  level: string
  publishScope: string
  status: string
  isTop: number
  needConfirm: number
  publishTime?: string
  /** 发布者（后端 create_by），详情弹窗展示用 */
  createBy?: string
  createTime?: string
  remark?: string
  groupIds?: number[]
  noticeGroups?: Array<{ groupId: number; groupName: string; groupCode: string }>
  /** 指定成员(USER)范围下携带的目标用户主键数组 */
  targetUserIds?: number[]
  /** 详情/编辑回显用：已选指定成员的展示信息（后端 NoticeTargetUserVo 列表） */
  targetUsers?: NoticeTargetUserRecord[]
  hasRead?: boolean
  hasConfirmed?: boolean
}

/**
 * 通知列表查询参数，与后端 NoticeQuarry + 分页参数对齐。
 * beginTime / endTime 由页面把日期范围控件拆成两个字段回传后端。
 */
export interface SysNoticeListQuery extends Partial<PageQueryParams> {
  title?: string
  noticeType?: string
  level?: string
  publishScope?: string
  status?: string
  beginTime?: string
  endTime?: string
}

export type SysNoticePageResult = NormalizedPageResult<SysNoticeRecord>

/**
 * 通知分组成员记录，与后端 SysNoticeGroupMember 对齐。
 * nickName / username / phoneNumber / status 不对应 sys_notice_group_member 表列，
 * 由后端 getSysNoticeGroupMemberByGroupId 关联 sys_user 查询时填充，供成员表格展示。
 */
export interface SysNoticeGroupMemberRecord {
  id: number
  groupId: number
  userId: number
  nickName?: string
  username?: string
  phoneNumber?: string
  status?: number
}

/**
 * 通知分组记录，与后端 SysNoticeGroupVo 字段对齐。
 * members 为分组当前成员列表，详情接口返回。
 */
export interface SysNoticeGroupRecord {
  groupId?: number
  groupName: string
  groupCode: string
  groupDesc?: string
  status: number
  createTime?: string
  members?: SysNoticeGroupMemberRecord[]
}

/**
 * 通知分组列表查询参数，与后端 NoticeGroupQuarry + 分页参数对齐。
 */
export interface SysNoticeGroupListQuery extends Partial<PageQueryParams> {
  groupName?: string
  groupCode?: string
  status?: number
}

export type SysNoticeGroupPageResult = NormalizedPageResult<SysNoticeGroupRecord>
