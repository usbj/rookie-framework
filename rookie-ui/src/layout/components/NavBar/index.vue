<script setup lang="ts">
import { useRouter, useRoute } from 'vue-router'
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import {
  ArrowLeftBold,
  ArrowRightBold,
  ArrowDown,
  Bell,
  Close,
  MoonNight,
  Refresh,
  Search,
  Setting,
  Sunny,
} from '@element-plus/icons-vue'
import { ElBadge, ElDropdown, ElDropdownItem, ElDropdownMenu } from 'element-plus'
import UserAvatar from '@/components/UserAvatar.vue'
import { useUserStore } from '@/stores/user'
import { useLayoutNavigationStore } from '@/stores/navigation'
import { useThemePreferenceStore } from '@/stores/themePreference'
import { useNoticeStore } from '@/stores/notice'
import { useDict } from '@/composables/useDict'
import { unregisterDynamicRoutes } from '@/router/dynamicRoutes'
import { formatDateTime } from '@/utils/format'
import type { NotificationItem } from '@/types/components/theme'
import type { SysNoticeRecord } from '@/types/api/system/notice'
import NavBreadcrumb from './components/NavBreadcrumb.vue'
import NavTabs from './components/NavTabs.vue'

defineProps<{
  collapsed: boolean
}>()

const router = useRouter()
const route = useRoute()
/**
 * 当前用户状态由 user store 统一维护。
 * 顶部导航只读取展示，不直接处理登录接口。
 */
const userStore = useUserStore()
const layoutNavigationStore = useLayoutNavigationStore()
const themePreferenceStore = useThemePreferenceStore()
const noticeStore = useNoticeStore()
const { resolveDictLabel } = useDict()
const emit = defineEmits<{
  openSettings: []
  toggleSidebar: []
  refreshView: []
  openNotice: [item: NotificationItem]
}>()

/**
 * 通知类型到中文标签的映射，供下拉项分类展示复用。
 * 与通知内容管理页的 noticeTypeOptions 保持同口径。
 */
const NOTICE_TYPE_LABEL: Record<string, string> = {
  NOTICE: '公告',
  NOTIFY: '通知',
  REMIND: '提醒',
}

/**
 * 方法效果：
 * 把后端 SysNoticeRecord 映射成头导航下拉使用的 NotificationItem，
 * 摘要取正文前 40 字，时间格式化为发布时间，
 * 分类/级别通过字典系统翻译为中文标签。
 * 参数：
 * - `notice`：后端通知记录。
 * 返回值：
 * - 供下拉与详情弹窗消费的展示项。
 */
const toNotificationItem = (notice: SysNoticeRecord): NotificationItem => ({
  id: Number(notice.noticeId),
  title: notice.title,
  summary: (notice.content ?? '').slice(0, 40),
  time: formatDateTime(notice.publishTime),
  unread: !notice.hasRead,
  category: (resolveDictLabel('sys_notice_type', notice.noticeType) as string) ?? notice.noticeType,
  content: notice.content,
  needConfirm: Number(notice.needConfirm) === 1,
  isTop: Number(notice.isTop) === 1,
})

/**
 * 头导航通知下拉直接消费 notice store 的"我的通知"列表，
 * 未读数由 store 统一计算，保证铃铛徽标与列表一致。
 */
const notifications = computed<NotificationItem[]>(() =>
  noticeStore.myNotices.map(toNotificationItem),
)

const unreadNotificationCount = computed(() => noticeStore.unreadCount)

/**
 * 通知下拉滚动容器选择器。
 * EP 2.14 的 ElDropdown 内部用 ElScrollbar 包裹内容，真正的滚动容器是
 * `.el-scrollbar__wrap`（由 ElDropdown 的 max-height prop 驱动产生滚动），
 * 滚动监听需要挂在它上面，而不是内层的 `.el-dropdown-menu`。
 */
const NOTICE_MENU_SELECTOR = '.nav-bar-notice-dropdown .el-scrollbar__wrap'

/**
 * 方法效果：
 * 通知下拉滚动到接近底部时触发下一页懒加载。
 * 参数：
 * - `event`：菜单容器的滚动事件。
 * 返回值：
 * - 无返回值；副作用是按需追加通知列表。
 */
const handleNoticeMenuScroll = (event: Event) => {
  const el = event.currentTarget as HTMLElement
  if (el.scrollTop + el.clientHeight >= el.scrollHeight - 8) {
    noticeStore.loadMoreNotices().catch(() => undefined)
  }
}

/**
 * 方法效果：
 * 下拉打开期间补页直到菜单可滚动：首屏 10 条在 60vh 内往往放得下，
 * 菜单没有滚动条时滚轮无处可滚、懒加载触发不了，这里自动加载下一页，
 * 直到内容溢出可滚动或没有更多为止（之后交给用户滚动触发）。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是按需追加通知列表。
 */
const ensureNoticeMenuScrollable = async () => {
  const menu = document.querySelector<HTMLElement>(NOTICE_MENU_SELECTOR)
  if (!menu || !noticeStore.hasMore || noticeStore.loadingMore) {
    return
  }
  if (menu.scrollHeight - menu.clientHeight <= 2) {
    try {
      await noticeStore.loadMoreNotices()
    } catch {
      // 补页失败时停止自动补页，滚动/重新打开时会重试
      return
    }
    await ensureNoticeMenuScrollable()
  }
}

/** 通知下拉当前是否显示，控制列表变化后的自动补页只在打开期间生效 */
const isNoticeDropdownVisible = ref(false)

/**
 * 方法效果：
 * 通知下拉显示时拉取首屏列表并刷新未读数（已加载则只刷未读数），
 * 在菜单容器上挂载滚动懒加载监听并尝试自动补页；隐藏时卸载监听。
 * 参数：
 * - `visible`：下拉是否显示。
 * 返回值：
 * - 无返回值；副作用是更新通知状态与滚动监听。
 */
const handleNoticeDropdownVisible = (visible: boolean) => {
  isNoticeDropdownVisible.value = visible
  if (visible) {
    noticeStore.fetchMyNotices().catch(() => undefined)
    noticeStore.fetchUnreadCount().catch(() => undefined)
    // popper 挂载到 body 后再找菜单容器，避免取到未渲染节点
    nextTick(() => {
      document.querySelector<HTMLElement>(NOTICE_MENU_SELECTOR)?.addEventListener('scroll', handleNoticeMenuScroll)
      void ensureNoticeMenuScrollable()
    })
  } else {
    document.querySelector<HTMLElement>(NOTICE_MENU_SELECTOR)?.removeEventListener('scroll', handleNoticeMenuScroll)
  }
}

// 首屏数据返回 / 自动补页追加后重新检查：下拉仍打开且内容仍不满一屏则继续补页
watch(
  [() => noticeStore.myNotices.length, () => noticeStore.loadingMore],
  () => {
    if (isNoticeDropdownVisible.value) {
      nextTick(() => void ensureNoticeMenuScrollable())
    }
  },
)

onBeforeUnmount(() => {
  document.querySelector<HTMLElement>(NOTICE_MENU_SELECTOR)?.removeEventListener('scroll', handleNoticeMenuScroll)
})

/**
 * 关闭标签后，如果关掉的是当前页，就自动切到相邻标签，
 * 这样顶部标签栏和主内容区不会出现“当前页面还开着但标签没了”的断裂状态。
 */
const handleCloseTab = async (targetRoute: string) => {
  const nextRoute = layoutNavigationStore.closeTab(targetRoute)

  if (targetRoute === layoutNavigationStore.currentPath && nextRoute) {
    await router.push(nextRoute)
  }
}

/**
 * 当前页刷新通过重新挂载视图完成，不改动当前路由与参数。
 */
const refreshCurrentTab = () => {
  emit('refreshView')
}

/**
 * 方法效果：
 * 处理标签栏批量操作命令，例如关闭其他标签或关闭全部标签。
 * 参数：
 * - `command`：下拉菜单返回的操作命令。
 * 返回值：
 * - 无返回值；副作用是更新导航 store 中的标签状态，并在必要时触发路由跳转。
 */
const handleTabAction = async (command: string) => {
  if (command === 'close-other') {
    layoutNavigationStore.closeOtherTabs(layoutNavigationStore.currentPath)
    return
  }

  if (command === 'close-all') {
    const nextRoute = layoutNavigationStore.closeAllTabs()
    if (nextRoute && nextRoute !== route.path) {
      await router.push(nextRoute)
    }
  }
}

/**
 * 方法效果：
 * 处理当前用户下拉菜单命令，包括跳转个人中心和退出登录。
 * 参数：
 * - `command`：下拉菜单返回的操作命令。
 * 返回值：
 * - 无返回值；副作用是执行对应跳转或清理登录状态。
 */
const handleProfileCommand = async (command: string) => {
  if (command === 'profile') {
    await router.push('/account/profile')
    return
  }

  if (command === 'logout') {
    // 先等 store 完成本地清理（内部先调后端退出接口）再跳登录页，
    // 否则路由守卫可能因 token 尚未清除把用户弹回应用内
    await userStore.logout()
    layoutNavigationStore.resetNavigationState()
    unregisterDynamicRoutes(router)
    await router.replace('/login')
  }
}
</script>

<template>
  <!-- 头导航区域 -->
  <header class="nav-bar">
    <div class="nav-bar__top-row">
      <div class="nav-bar__identity">
        <button
          class="nav-bar__icon-button nav-bar__collapse-button"
          type="button"
          aria-label="切换侧边栏"
          @click="emit('toggleSidebar')"
        >
          <ArrowRightBold v-if="$props.collapsed" />
          <ArrowLeftBold v-else />
        </button>

        <NavBreadcrumb :items="layoutNavigationStore.breadcrumbs" />
      </div>

      <div class="nav-bar__actions">
        <label class="nav-bar__search" aria-label="搜索">
          <Search class="nav-bar__search-icon" />
          <input type="text" placeholder="搜索菜单、页面或操作" />
          <span class="nav-bar__shortcut">Ctrl K</span>
        </label>

        <button class="nav-bar__icon-button" type="button" aria-label="切换明暗主题" @click="themePreferenceStore.toggleThemeMode()">
          <Sunny v-if="themePreferenceStore.isDarkMode" />
          <MoonNight v-else />
        </button>

        <ElDropdown
          trigger="click"
          placement="bottom-end"
          popper-class="nav-bar-notice-dropdown"
          :show-arrow="false"
          max-height="60vh"
          @visible-change="handleNoticeDropdownVisible"
        >
          <button class="nav-bar__icon-button" type="button" aria-label="通知">
            <ElBadge :value="unreadNotificationCount" :hidden="unreadNotificationCount === 0">
              <Bell />
            </ElBadge>
          </button>

          <template #dropdown>
            <ElDropdownMenu class="nav-bar__notice-menu">
              <div class="nav-bar__notice-head">通知</div>
              <div v-if="!noticeStore.loaded" class="nav-bar__notice-empty">加载中…</div>
              <div v-else-if="notifications.length === 0" class="nav-bar__notice-empty">暂无通知</div>
              <ElDropdownItem
                v-for="item in notifications"
                :key="item.id"
                class="nav-bar__notice-item"
                @click="emit('openNotice', item)"
              >
                <div class="nav-bar__notice-copy">
                  <strong>
                    {{ item.title }}
                    <span v-if="item.isTop" class="nav-bar__notice-top">置顶</span>
                    <span v-if="item.unread" class="nav-bar__notice-dot"></span>
                  </strong>
                  <span>{{ item.summary }}</span>
                  <small>{{ item.time }}</small>
                </div>
              </ElDropdownItem>
              <div v-if="notifications.length > 0" class="nav-bar__notice-loading">
                {{ noticeStore.loadingMore ? '加载中…' : noticeStore.hasMore ? '继续滚动加载更多' : '已加载全部' }}
              </div>
            </ElDropdownMenu>
          </template>
        </ElDropdown>

        <button class="nav-bar__icon-button" type="button" aria-label="界面设置" @click="emit('openSettings')">
          <Setting />
        </button>

        <ElDropdown
          trigger="click"
          placement="bottom-end"
          popper-class="nav-bar-profile-dropdown"
          @command="handleProfileCommand"
        >
          <button class="nav-bar__profile" type="button" aria-label="当前用户菜单">
            <span class="nav-bar__profile-avatar">
              <UserAvatar
                :name="userStore.displayName"
                :src="userStore.avatarUrl ?? undefined"
              />
            </span>
            <span class="nav-bar__profile-copy">
              <strong>{{ userStore.displayName }}</strong>
            </span>
            <ArrowDown class="nav-bar__profile-arrow" />
          </button>
          <template #dropdown>
            <ElDropdownMenu>
              <ElDropdownItem command="profile">个人中心</ElDropdownItem>
              <ElDropdownItem command="logout">退出登录</ElDropdownItem>
            </ElDropdownMenu>
          </template>
        </ElDropdown>
      </div>
    </div>

    <div class="nav-bar__tab-row">
      <NavTabs
        :tabs="layoutNavigationStore.visitedTabs"
        :active-route="layoutNavigationStore.currentPath"
        @close="handleCloseTab"
      />

      <div class="nav-bar__tab-actions">
        <button class="nav-bar__tab-button" type="button" aria-label="刷新当前标签页" @click="refreshCurrentTab">
          <Refresh />
        </button>

        <ElDropdown trigger="click" placement="bottom-end" popper-class="nav-bar-profile-dropdown" @command="handleTabAction">
          <button class="nav-bar__tab-button" type="button" aria-label="标签操作">
            <Close />
          </button>
          <template #dropdown>
            <ElDropdownMenu>
              <ElDropdownItem command="close-other">关闭其他标签</ElDropdownItem>
              <ElDropdownItem command="close-all">关闭所有标签</ElDropdownItem>
            </ElDropdownMenu>
          </template>
        </ElDropdown>
      </div>
    </div>

  </header>
</template>

<style scoped>
.nav-bar {
  display: grid;
  background: var(--rookie-surface-strong);
  backdrop-filter: blur(14px);
  position: sticky;
  top: 0;
  z-index: 20;
}

.nav-bar__top-row,
.nav-bar__tab-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding-inline: 24px;
}

.nav-bar__top-row {
  padding-top: 16px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--rookie-border);
}

.nav-bar__tab-row {
  min-height: 54px;
  align-items: flex-end;
  border-bottom: 1px solid var(--rookie-border);
}

.nav-bar__identity {
  min-width: 0;
  flex: 1;
  display: flex;
  align-items: center;
  gap: 14px;
}

.nav-bar__collapse-button {
  flex: none;
}

.nav-bar__search {
  width: min(304px, 22vw);
  height: 42px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 14px;
  border: 1px solid var(--rookie-border);
  border-radius: var(--rookie-radius-md);
  background: var(--rookie-bg-elevated);
  box-shadow: var(--rookie-input-shadow);
}

.nav-bar__search:focus-within {
  border-color: var(--rookie-primary-border);
  box-shadow: var(--rookie-input-focus-shadow);
}

.nav-bar__search input {
  flex: 1;
  min-width: 0;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--rookie-text);
}

.nav-bar__search input::placeholder {
  color: var(--rookie-text-tertiary);
}

.nav-bar__search-icon,
.nav-bar__icon-button :deep(svg) {
  width: 18px;
  height: 18px;
}

.nav-bar__search-icon {
  color: var(--rookie-text-tertiary);
  flex: none;
}

.nav-bar__shortcut {
  flex: none;
  padding: 2px 7px;
  border-radius: 999px;
  background: var(--rookie-card-bg);
  border: 1px solid var(--rookie-border);
  color: var(--rookie-text-tertiary);
  font-size: var(--rookie-font-size-xs);
  font-weight: 600;
}

.nav-bar__icon-button {
  width: 42px;
  height: 42px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid transparent;
  border-radius: var(--rookie-radius-md);
  background: var(--rookie-surface-weak);
  color: var(--rookie-text-secondary);
  cursor: pointer;
  transition:
    color 0.2s ease,
    background-color 0.2s ease,
    border-color 0.2s ease;
}

.nav-bar__icon-button:hover,
.nav-bar__icon-button:focus-visible {
  color: var(--rookie-primary);
  border-color: var(--rookie-primary-border);
  background: var(--rookie-hover-bg);
  outline: none;
}

.nav-bar__actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  padding-right: 1rem;
}

.nav-bar__profile {
  min-width: 0;
  height: 42px;
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 0 12px;
  border: 1px solid var(--rookie-border);
  border-radius: var(--rookie-radius-md);
  background: var(--rookie-surface-weak);
  color: var(--rookie-text-secondary);
  cursor: pointer;
}

.nav-bar__profile-avatar {
  width: 28px;
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  flex: none;
  overflow: hidden;
}

.nav-bar__profile-copy {
  display: flex;
  align-items: center;
  text-align: left;
  line-height: 1.15;
}

.nav-bar__profile-copy strong {
  color: var(--rookie-text);
  font-size: var(--rookie-font-size-sm);
}

.nav-bar__profile-arrow {
  width: 14px;
  height: 14px;
  color: var(--rookie-text-tertiary);
}

.nav-bar__tab-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-bottom: 8px;
}

.nav-bar__tab-button {
  width: 34px;
  height: 34px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--rookie-border);
  border-radius: var(--rookie-radius-md);
  background: var(--rookie-surface-weak);
  color: var(--rookie-text-secondary);
  cursor: pointer;
}

.nav-bar__tab-button:hover {
  background: var(--rookie-hover-bg);
  color: var(--rookie-text);
}

.nav-bar__notice-head {
  padding: 8px 14px 4px;
  color: var(--rookie-text-tertiary);
  font-size: var(--rookie-font-size-xs);
  font-weight: 700;
}

.nav-bar__notice-item {
  min-width: 280px;
}

.nav-bar__notice-empty {
  min-width: 280px;
  padding: 14px;
  color: var(--rookie-text-tertiary);
  font-size: var(--rookie-font-size-sm);
}

.nav-bar__notice-loading {
  padding: 10px 14px;
  color: var(--rookie-text-tertiary);
  font-size: var(--rookie-font-size-xs);
  text-align: center;
}

.nav-bar__notice-copy {
  display: grid;
  gap: 4px;
  white-space: normal;
}

.nav-bar__notice-copy strong {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--rookie-text);
}

.nav-bar__notice-copy span,
.nav-bar__notice-copy small {
  color: var(--rookie-text-secondary);
}

.nav-bar__notice-dot {
  width: 6px;
  height: 6px;
  border-radius: 999px;
  background: var(--rookie-primary);
}

.nav-bar__notice-top {
  padding: 1px 6px;
  border-radius: var(--rookie-radius-sm);
  font-size: var(--rookie-font-size-xs);
  font-weight: 600;
  color: var(--rookie-primary-strong);
  background: var(--rookie-hover-bg);
  border: 1px solid var(--rookie-primary-border);
}

@media (max-width: 1024px) {
  .nav-bar__top-row,
  .nav-bar__tab-row {
    flex-wrap: wrap;
  }

  .nav-bar__actions {
    width: 100%;
    justify-content: space-between;
  }

  .nav-bar__search {
    width: 100%;
  }
}
</style>

<!--
  通知下拉的 popper 通过 popper-class 传 nav-bar-notice-dropdown，
  teleport 到 body 后 scoped 样式命不中，这里用全局样式覆写。
 -->
<style>
/* 防止鼠标移出下拉后最后划过的通知项残留 hover/focus 高亮 */
.nav-bar-notice-dropdown .el-dropdown-menu__item:not(:hover):not(:focus) {
  background-color: transparent !important;
  color: var(--el-text-color-regular) !important;
}

/*
 * 通知下拉滚动：滚动容器是 ElDropdown 的 max-height prop 驱动的
 * `.el-scrollbar__wrap`（EP 原生滚动机制，自带滚动条），
 * 不能再给内层 `.el-dropdown-menu` 加 max-height/overflow，
 * 否则形成双层滚动容器、滚轮事件被外层吞掉、内层滚不动。
 */

/* 通知头部「通知」标题在滚动时吸顶（背景与 popper 同色，盖住滚过的内容） */
.nav-bar-notice-dropdown .nav-bar__notice-head {
  position: sticky;
  top: 0;
  z-index: 1;
  background: var(--el-bg-color-overlay);
}
</style>
