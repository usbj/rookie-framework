/**
 * 文件作用：
 * 承接系统模块下的在线用户管理页面，
 * 负责在线人数统计展示、在线用户列表查询与强制下线操作。
 * 在线判定由后端 Redis 在线集合维护（最后活跃时间在阈值内视为在线），
 * 本页每 30 秒自动刷新，也可手动刷新。
 */
<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { ElButton, ElMessage, ElMessageBox } from 'element-plus'
import { getOnlineListApi, kickOnlineUserApi } from '@/api/system/online'
import BaseCard from '@/components/BaseCard.vue'
import SharedTablePanel from '@/components/SharedTablePanel.vue'
import { SYSTEM_PERMISSION_KEYS } from '@/constants/systemPermissions'
import { useUserStore } from '@/stores/user'
import type { NormalizedPageResult } from '@/types/api/system/common'
import type { OnlineUserRecord } from '@/types/api/system/online'
import type { SharedActionConfig } from '@/types/components/data-display'
import { createOnlineSchema } from './config'

const userStore = useUserStore()

const onlineList = ref<OnlineUserRecord[]>([])
const listLoading = ref(false)
const kicking = ref(false)

/** 自动刷新定时器句柄（页面卸载时清理） */
let refreshTimer: number | undefined

const onlineSchema = createOnlineSchema()

/**
 * 当前在线人数：在线列表长度即阈值内活跃用户数（与后端 count 口径一致）。
 */
const onlineCount = computed(() => onlineList.value.length)

const onlineRows = computed(() => onlineList.value as unknown as Record<string, unknown>[])

/**
 * 单页分页信息：在线列表不分页（全量展示），仅用于表格底部展示总条数。
 */
const onlinePagination = computed<NormalizedPageResult<Record<string, unknown>>>(() => ({
  records: onlineRows.value,
  pageNum: 1,
  pageSize: Math.max(onlineRows.value.length, 1),
  pages: 1,
  total: onlineRows.value.length,
}))

/**
 * 行操作：强制下线。对当前登录账号隐藏操作按钮（后端亦有校验兜底）。
 */
const onlineActions = computed<SharedActionConfig<Record<string, unknown>>[]>(() => [
  {
    key: 'kick',
    label: '强制下线',
    permKey: SYSTEM_PERMISSION_KEYS.online.kick,
    buttonType: 'danger',
    visible: (row) => row.username !== userStore.profileSummary?.username,
    onClick: async (row) => {
      await handleKickOffline(String(row.username))
    },
  },
])

/**
 * 方法效果：
 * 拉取在线用户列表并更新当前页面状态。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是刷新在线列表与人数。
 */
const fetchOnlineList = async () => {
  listLoading.value = true
  try {
    const result = await getOnlineListApi()
    onlineList.value = result.data ?? []
  } finally {
    listLoading.value = false
  }
}

/**
 * 方法效果：
 * 手动刷新在线列表。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是重新拉取列表。
 */
const handleRefresh = async () => {
  await fetchOnlineList()
}

/**
 * 方法效果：
 * 强制下线指定账号：二次确认后调用后端接口（移除在线集合 + 删除登录态缓存），
 * 成功后刷新列表。
 * 参数：
 * - `username`：被强制下线的登录账号。
 * 返回值：
 * - 无返回值；副作用是调用强踢接口并刷新列表。
 */
const handleKickOffline = async (username: string) => {
  await ElMessageBox.confirm(`确认强制下线账号「${username}」？该账号将立即退出登录。`, '强制下线', {
    type: 'warning',
    confirmButtonText: '强制下线',
    cancelButtonText: '取消',
  })

  kicking.value = true
  try {
    await kickOnlineUserApi(username)
    ElMessage.success(`账号「${username}」已强制下线`)
    await fetchOnlineList()
  } finally {
    kicking.value = false
  }
}

onMounted(async () => {
  await fetchOnlineList()
  // 在线状态随时间变化（阈值内活跃即在线），页面停留时每 30 秒自动刷新
  refreshTimer = window.setInterval(() => {
    fetchOnlineList().catch(() => undefined)
  }, 30_000)
})

onUnmounted(() => {
  if (refreshTimer !== undefined) {
    window.clearInterval(refreshTimer)
    refreshTimer = undefined
  }
})
</script>

<template>
  <section class="online-view">
    <BaseCard>
      <div class="online-view__stat">
        <div class="online-view__stat-copy">
          <strong>{{ onlineCount }}</strong>
          <span>当前在线人数（阈值内活跃用户，可在线用户列表查看明细）</span>
        </div>
        <div class="online-view__stat-actions">
          <span class="online-view__stat-hint">每 30 秒自动刷新</span>
          <ElButton :loading="listLoading" @click="handleRefresh">刷新</ElButton>
        </div>
      </div>
    </BaseCard>

    <BaseCard title="在线用户列表">
      <SharedTablePanel
        :rows="onlineRows"
        :schema="onlineSchema"
        :actions="onlineActions"
        :loading="listLoading || kicking"
        :pagination="onlinePagination"
        :table-max-height="480"
        row-key="username"
      />
    </BaseCard>
  </section>
</template>

<style scoped>
.online-view {
  display: grid;
  gap: 18px;
}

.online-view__stat {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.online-view__stat-copy {
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.online-view__stat-copy strong {
  color: var(--rookie-primary-strong);
  font-size: 32px;
  line-height: 1;
}

.online-view__stat-copy span {
  color: var(--rookie-text-secondary);
  font-size: var(--rookie-font-size-sm);
}

.online-view__stat-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.online-view__stat-hint {
  color: var(--rookie-text-tertiary);
  font-size: var(--rookie-font-size-xs);
}
</style>
