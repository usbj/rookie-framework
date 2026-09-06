/**
 * 文件作用：
 * 集中管理通知管理与通知分组管理对接后端的接口方法，
 * 包括通知主体 CRUD、发布/撤回，分组 CRUD、分组成员增删，
 * 以及当前用户侧的"我的通知"拉取、标记已读、确认通知。
 */
import { del, get, getPage, post, put } from '@/utils/http'
import type { ApiResult, PageQueryParams } from '@/types/api/system/common'
import type {
  SysNoticeGroupListQuery,
  SysNoticeGroupPageResult,
  SysNoticeGroupRecord,
  SysNoticeListQuery,
  SysNoticePageResult,
  SysNoticeRecord,
} from '@/types/api/system/notice'

/* ---------------- 通知主体 ---------------- */

/**
 * 方法效果：
 * 分页查询通知列表，并在请求层完成分页结果归一化。
 * 参数：
 * - `params`：通知查询条件与分页参数。
 * 返回值：
 * - 归一化后的通知分页结果。
 */
export const getSysNoticePageApi = (params: SysNoticeListQuery) =>
  getPage<SysNoticeRecord>('/sys/notice/list', {
    params,
  }) as Promise<SysNoticePageResult>

/**
 * 方法效果：
 * 根据通知主键获取通知详情。
 * 参数：
 * - `noticeId`：通知主键。
 * 返回值：
 * - 后端 Result 包裹的通知详情对象。
 */
export const getSysNoticeDetailApi = (noticeId: number) =>
  get<ApiResult<SysNoticeRecord>>(`/sys/notice/${noticeId}`)

/**
 * 方法效果：
 * 新增通知。
 * 参数：
 * - `data`：通知表单数据。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const createSysNoticeApi = (data: SysNoticeRecord) =>
  post<ApiResult<boolean>, SysNoticeRecord>('/sys/notice', data)

/**
 * 方法效果：
 * 编辑通知。
 * 参数：
 * - `data`：通知表单数据。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const updateSysNoticeApi = (data: SysNoticeRecord) =>
  put<ApiResult<boolean>, SysNoticeRecord>('/sys/notice', data)

/**
 * 方法效果：
 * 批量删除通知。后端路径变量接收 Long[]，按逗号自动分割。
 * 参数：
 * - `noticeIds`：待删除的通知主键数组。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const deleteSysNoticesApi = (noticeIds: number[]) =>
  del<ApiResult<boolean>>(`/sys/notice/${noticeIds.join(',')}`)

/**
 * 方法效果：
 * 发布通知，把草稿或已撤回状态改为已发布。
 * 参数：
 * - `noticeId`：通知主键。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const publishSysNoticeApi = (noticeId: number) =>
  put<ApiResult<boolean>>(`/sys/notice/publish/${noticeId}`)

/**
 * 方法效果：
 * 撤回已发布的通知。
 * 参数：
 * - `noticeId`：通知主键。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const revokeSysNoticeApi = (noticeId: number) =>
  put<ApiResult<boolean>>(`/sys/notice/revoke/${noticeId}`)

/* ---------------- 通知分组 ---------------- */

/**
 * 方法效果：
 * 分页查询通知分组列表，并在请求层完成分页结果归一化。
 * 参数：
 * - `params`：分组查询条件与分页参数。
 * 返回值：
 * - 归一化后的分组分页结果。
 */
export const getSysNoticeGroupPageApi = (params: SysNoticeGroupListQuery) =>
  getPage<SysNoticeGroupRecord>('/sys/notice/group/list', {
    params,
  }) as Promise<SysNoticeGroupPageResult>

/**
 * 方法效果：
 * 根据分组主键获取分组详情（含成员列表）。
 * 参数：
 * - `groupId`：分组主键。
 * 返回值：
 * - 后端 Result 包裹的分组详情对象。
 */
export const getSysNoticeGroupDetailApi = (groupId: number) =>
  get<ApiResult<SysNoticeGroupRecord>>(`/sys/notice/group/${groupId}`)

/**
 * 方法效果：
 * 新增通知分组。
 * 参数：
 * - `data`：分组表单数据。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const createSysNoticeGroupApi = (data: SysNoticeGroupRecord) =>
  post<ApiResult<boolean>, SysNoticeGroupRecord>('/sys/notice/group', data)

/**
 * 方法效果：
 * 编辑通知分组。
 * 参数：
 * - `data`：分组表单数据。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const updateSysNoticeGroupApi = (data: SysNoticeGroupRecord) =>
  put<ApiResult<boolean>, SysNoticeGroupRecord>('/sys/notice/group', data)

/**
 * 方法效果：
 * 批量删除通知分组。后端路径变量接收 Long[]，按逗号自动分割。
 * 参数：
 * - `groupIds`：待删除的分组主键数组。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const deleteSysNoticeGroupsApi = (groupIds: number[]) =>
  del<ApiResult<boolean>>(`/sys/notice/group/${groupIds.join(',')}`)

/**
 * 方法效果：
 * 向指定分组批量添加成员。
 * 参数：
 * - `groupId`：分组主键。
 * - `userIds`：待添加的用户主键数组。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const addNoticeGroupMembersApi = (groupId: number, userIds: number[]) =>
  post<ApiResult<boolean>, number[]>(`/sys/notice/group/${groupId}/members`, userIds)

/**
 * 方法效果：
 * 从指定分组批量移除成员。
 * 参数：
 * - `groupId`：分组主键。
 * - `memberIds`：待移除的分组成员主键数组（注意是成员记录 id，而非 userId）。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const removeNoticeGroupMembersApi = (groupId: number, memberIds: number[]) =>
  del<ApiResult<boolean>>(`/sys/notice/group/${groupId}/members`, {
    data: memberIds,
  })

/* ---------------- 当前用户侧：我的通知 ---------------- */

/**
 * 方法效果：
 * 分页获取当前登录用户可见的通知列表（含已读/已确认状态与完整正文），
 * 供头导航通知下拉滚动懒加载使用，请求层完成分页结果归一化。
 * 参数：
 * - `params`：分页参数（pageNum / pageSize）。
 * 返回值：
 * - 归一化后的分页结果（records / total / pages），后端从 SecurityContextHolder 取当前用户。
 */
export const getMyNoticesPageApi = (params: PageQueryParams) =>
  getPage<SysNoticeRecord>('/sys/notice/my', { params })

/**
 * 方法效果：
 * 获取当前登录用户的未读通知数，驱动铃铛徽标。
 * 懒加载后未读数与已加载分页列表解耦，必须走独立计数接口。
 * 参数：
 * - 无，后端从 SecurityContextHolder 取当前用户。
 * 返回值：
 * - 后端 Result 包裹的未读数量。
 */
export const getUnreadCountApi = () => get<ApiResult<number>>('/sys/notice/unread-count')

/**
 * 方法效果：
 * 标记指定通知为当前用户已读。
 * 参数：
 * - `noticeId`：通知主键。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const markAsReadApi = (noticeId: number) =>
  post<ApiResult<boolean>>(`/sys/notice/read/${noticeId}`)

/**
 * 方法效果：
 * 确认指定通知（用于 needConfirm=1 的需确认通知）。
 * 参数：
 * - `noticeId`：通知主键。
 * 返回值：
 * - 后端 Result 包裹的布尔结果。
 */
export const confirmNoticeApi = (noticeId: number) =>
  post<ApiResult<boolean>>(`/sys/notice/confirm/${noticeId}`)
