/**
 * 文件作用：
 * 集中管理当前登录用户侧的通知状态，
 * 包括"我的通知"分页懒加载、未读计数、标记已读和详情查询，
 * 供头导航通知按钮、通知详情弹窗统一消费。
 * 关键状态：
 * - `myNotices`：当前用户可见的通知数组（分页累积，含 hasRead/hasConfirmed 与完整正文）。
 * - `unreadCount`：未读数，来自独立计数接口（懒加载后不能依赖已加载列表）。
 * - `hasMore` / `loadingMore`：滚动懒加载的"是否还有下一页 / 是否正在加载"标记。
 */
import { ref } from 'vue'
import { defineStore } from 'pinia'
import { confirmNoticeApi, getMyNoticesPageApi, getUnreadCountApi, markAsReadApi } from '@/api/system/notice'
import type { SysNoticeRecord } from '@/types/api/system/notice'

/** 每次懒加载拉取的条数，与后端默认分页大小一致 */
const PAGE_SIZE = 10

export const useNoticeStore = defineStore('notice', () => {
  const myNotices = ref<SysNoticeRecord[]>([])
  const loaded = ref(false)
  const hasMore = ref(false)
  const loadingMore = ref(false)
  /** 未读通知数量，驱动头导航铃铛徽标，由独立计数接口维护 */
  const unreadCount = ref(0)

  /**
   * 方法效果：
   * 拉取当前用户通知的第一页并重置累积列表，同时刷新未读数。
   * 参数：
   * - `force`：是否强制重新拉取，忽略已加载标记。
   * 返回值：
   * - 无返回值；副作用是更新通知列表、未读数与分页标记。
   */
  const fetchMyNotices = async (force = false) => {
    if (loaded.value && !force) {
      return
    }

    const result = await getMyNoticesPageApi({ pageNum: 1, pageSize: PAGE_SIZE })
    myNotices.value = result.records
    hasMore.value = result.records.length < result.total
    loaded.value = true
    await fetchUnreadCount()
  }

  /**
   * 方法效果：
   * 滚动到底部时拉取下一页并追加到列表尾部（懒加载）。
   * 已到最后或正在加载时直接返回，避免并发重复请求。
   * 参数：
   * - 无。
   * 返回值：
   * - 无返回值；副作用是追加通知列表并更新 hasMore。
   */
  const loadMoreNotices = async () => {
    if (!hasMore.value || loadingMore.value) {
      return
    }

    loadingMore.value = true
    try {
      const pageNum = Math.floor(myNotices.value.length / PAGE_SIZE) + 1
      const result = await getMyNoticesPageApi({ pageNum, pageSize: PAGE_SIZE })
      myNotices.value.push(...result.records)
      hasMore.value = myNotices.value.length < result.total
    } finally {
      loadingMore.value = false
    }
  }

  /**
   * 方法效果：
   * 单独刷新未读数，供铃铛徽标展示。
   * 参数：
   * - 无。
   * 返回值：
   * - 无返回值；副作用是更新 unreadCount。
   */
  const fetchUnreadCount = async () => {
    const result = await getUnreadCountApi()
    unreadCount.value = result.data ?? 0
  }

  /**
   * 方法效果：
   * 按通知主键从已加载列表中取单条通知，供详情弹窗展示完整正文。
   * 参数：
   * - `noticeId`：通知主键。
   * 返回值：
   * - 命中的通知记录，未命中返回 null。
   */
  const getNoticeById = (noticeId: number): SysNoticeRecord | null =>
    myNotices.value.find((item) => Number(item.noticeId) === noticeId) ?? null

  /**
   * 方法效果：
   * 标记指定通知为已读。乐观更新本地 hasRead=true 并同步递减未读数，
   * 避免等待刷新；已读则跳过请求，减少无意义调用。
   * 参数：
   * - `noticeId`：通知主键。
   * 返回值：
   * - 无返回值；副作用是调用已读接口并更新本地未读状态。
   */
  const markAsRead = async (noticeId: number) => {
    const target = getNoticeById(noticeId)

    if (!target || target.hasRead) {
      return
    }

    // 先乐观置为已读，让铃铛徽标即时减少；接口失败时由下次拉取纠正
    target.hasRead = true
    if (unreadCount.value > 0) {
      unreadCount.value -= 1
    }
    await markAsReadApi(noticeId)
  }

  /**
   * 方法效果：
   * 确认指定通知（用于 needConfirm=1 的需确认通知）。乐观更新本地
   * hasConfirmed=true，让详情弹窗确认按钮即时隐藏；未确认才请求。
   * 参数：
   * - `noticeId`：通知主键。
   * 返回值：
   * - 无返回值；副作用是调用确认接口并更新本地确认状态。
   */
  const confirmNotice = async (noticeId: number) => {
    const target = getNoticeById(noticeId)

    if (!target || target.hasConfirmed) {
      return
    }

    // 先乐观置为已确认，让详情弹窗确认按钮即时消失；接口失败时由下次拉取纠正
    target.hasConfirmed = true
    await confirmNoticeApi(noticeId)
  }

  /**
   * 方法效果：
   * 退出登录或登录态失效时清空通知状态，避免残留旧账号数据。
   * 参数：
   * - 无。
   * 返回值：
   * - 无返回值；副作用是重置通知列表、未读数与分页标记。
   */
  const resetNoticeState = () => {
    myNotices.value = []
    loaded.value = false
    hasMore.value = false
    loadingMore.value = false
    unreadCount.value = 0
  }

  return {
    myNotices,
    loaded,
    hasMore,
    loadingMore,
    unreadCount,
    fetchMyNotices,
    loadMoreNotices,
    fetchUnreadCount,
    getNoticeById,
    markAsRead,
    confirmNotice,
    resetNoticeState,
  }
})
