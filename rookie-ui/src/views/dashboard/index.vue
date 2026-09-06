<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { Document, Setting, Tools, User } from '@element-plus/icons-vue'
import BaseCard from '@/components/BaseCard.vue'
import { useLayoutNavigationStore } from '@/stores/navigation'
import { useUserStore } from '@/stores/user'
import type { NavigationMenuItem } from '@/types/components/navigation'

const userStore = useUserStore()
const layoutNavigationStore = useLayoutNavigationStore()
const router = useRouter()

/**
 * 方法效果：
 * 将树形菜单扁平化成一维数组，便于统计与按类型过滤。
 * 参数：
 * - `menus`：树形菜单节点。
 * 返回值：
 * - 含自身与所有后代节点的一维数组。
 */
const flattenMenus = (menus: NavigationMenuItem[]): NavigationMenuItem[] =>
  menus.flatMap((menu) => [menu, ...flattenMenus(menu.children ?? [])])

const flatMenus = computed(() => flattenMenus(layoutNavigationStore.menuTree))

const pageCount = computed(() => flatMenus.value.filter((item) => item.menuType === 2).length)
const directoryCount = computed(() => flatMenus.value.filter((item) => item.menuType === 1).length)
const permissionCount = computed(() => layoutNavigationStore.buttonPermissionKeys.size)
const roleCount = computed(() => userStore.profileSummary?.roleNames.length ?? 0)

/**
 * 概览 KPI 卡：当前账号的量化概览，提供整页的第一眼信息密度。
 * 每张卡固定「图标 + 数值 + 标题 + 说明」四要素，深浅色统一走主题变量。
 */
const summaryCards = computed(() => [
  {
    title: '可访问页面',
    value: pageCount.value,
    caption: '当前账号已加载的菜单页面',
    icon: Document,
  },
  {
    title: '目录分组',
    value: directoryCount.value,
    caption: '侧边导航中的目录数量',
    icon: Tools,
  },
  {
    title: '按钮权限',
    value: permissionCount.value,
    caption: '当前登录角色拥有的权限点',
    icon: Setting,
  },
  {
    title: '账号角色',
    value: roleCount.value,
    caption: '当前账号挂载的角色数量',
    icon: User,
  },
])

/**
 * 快捷入口：作为与"模块概览"对称的一栏，这里放与管理动作挂钩的高频入口，
 * 而不与模块概览抢同一批菜单项，避免一边挤一边空。
 * 两栏共用同一种"小卡 + auto-fill 网格"渲染，项数各自自适应，密度天然对齐。
 * 当前所有动作都可在前端闭环：个人中心、刷新概览、退出登录。
 */
type QuickEntry = {
  title: string
  description: string
  action: 'navigate' | 'reload' | 'logout'
  route?: string
}

const quickEntries = computed<QuickEntry[]>(() => [
  {
    title: '个人中心',
    description: '维护昵称、手机号与登录密码',
    action: 'navigate',
    route: '/account/profile',
  },
  {
    title: '刷新概览',
    description: '重新拉取菜单与个人资料',
    action: 'reload',
  },
  {
    title: '退出登录',
    description: '清除当前登录态并返回登录页',
    action: 'logout',
  },
])

/**
 * 方法效果：
 * 统一处理快捷入口点击，按动作类型分派到导航、刷新或登出，
 * 保持每个入口的交互语义在前端闭环，不依赖后端接口。
 * 参数：
 * - `entry`：被点击的快捷入口配置。
 * 返回值：
 * - 无返回值；副作用是触发路由跳转、页面刷新或登出流程。
 */
const handleQuickEntry = async (entry: QuickEntry) => {
  if (entry.action === 'navigate' && entry.route) {
    router.push(entry.route).catch(() => undefined)
    return
  }

  if (entry.action === 'reload') {
    window.location.reload()
    return
  }

  if (entry.action === 'logout') {
    // 先等 store 完成本地清理（内部先调后端退出接口）再跳登录页，
    // 避免路由守卫因 token 尚未清除把用户弹回应用内
    await userStore.logout()
    router.replace({ path: '/login' })
  }
}

/**
 * 模块概览：展示当前账号菜单树下所有可见的一级模块，
 * 每项标注其下页面入口数量，点击进入该模块第一个可访问页面。
 * 不再截断到固定数量，让多账号场景都能自然排布。
 */
const topModules = computed(() =>
  layoutNavigationStore.menuTree.map((item) => ({
    menuId: item.menuId,
    icon: item.iconComponent,
    title: item.menuName,
    pageTotal: item.children.filter((child) => child.menuType === 2).length,
    route:
      item.menuType === 2
        ? item.route
        : item.children.find((child) => child.menuType === 2)?.route || '',
  })),
)

/**
 * 方法效果：
 * 点击模块项时若该模块存在可访问页面再跳转，避免跳到空路由。
 * 参数：
 * - `route`：目标模块可访问页面路由，可能为空字符串。
 * 返回值：
 * - 无返回值；副作用是有路由时触发跳转。
 */
const handleModuleClick = (route: string) => {
  if (!route) {
    return
  }
  router.push(route).catch(() => undefined)
}

/**
 * 顶部账户信息行：把原 hero 右侧散落的两个身份盒子收敛为一行 pill 标签，
 * 与问候语同行展示，结构更克制、信息密度更高。
 */
const accountPills = computed(() => [
  { label: '登录账号', value: userStore.profileSummary?.username || '--' },
  { label: '角色', value: userStore.profileSummary?.roleNames.join('、') || '未分配角色' },
  { label: '可访问页面', value: String(pageCount.value) },
  { label: '按钮权限', value: String(permissionCount.value) },
])

/**
 * 方法效果：
 * 按当前小时生成问候语，让落地页首行更有温度。
 * 参数：
 * - 无。
 * 返回值：
 * - 「上午/下午/晚上」的问候词。
 */
const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '凌晨好'
  if (hour < 12) return '上午好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})
</script>

<template>
  <section class="dashboard-view">
    <!-- 顶部欢迎区：问候 + 账户信息收敛为一行 pill，结构克制有重心 -->
    <section class="dashboard-view__hero">
      <div class="dashboard-view__hero-copy">
        <span class="dashboard-view__eyebrow">系统首页</span>
        <h1>{{ greeting }}，{{ userStore.displayName }}</h1>
        <p>已授权模块与权限概览集中在这里，可作为后台工作起点。</p>
      </div>
      <ul class="dashboard-view__pills">
        <li v-for="pill in accountPills" :key="pill.label" class="dashboard-view__pill">
          <span>{{ pill.label }}</span>
          <strong>{{ pill.value }}</strong>
        </li>
      </ul>
    </section>

    <!-- 量化概览：四张 KPI 卡，深浅色统一走主题变量 -->
    <section class="dashboard-view__metrics">
      <article v-for="item in summaryCards" :key="item.title" class="dashboard-view__metric">
        <div class="dashboard-view__metric-head">
          <span>{{ item.title }}</span>
          <component :is="item.icon" class="dashboard-view__metric-icon" />
        </div>
        <strong>{{ item.value }}</strong>
        <p>{{ item.caption }}</p>
      </article>
    </section>

    <!-- 快捷入口 / 模块概览：同一套小卡网格，auto-fill 自适应排布，密度天然对齐 -->
    <section class="dashboard-view__grid">
      <BaseCard title="快捷入口" description="常驻后台的高频动作入口。">
        <div class="dashboard-view__tiles">
          <button
            v-for="entry in quickEntries"
            :key="entry.title"
            type="button"
            class="dashboard-view__tile"
            @click="handleQuickEntry(entry)"
          >
            <span class="dashboard-view__tile-title">{{ entry.title }}</span>
            <span class="dashboard-view__tile-desc">{{ entry.description }}</span>
          </button>
        </div>
      </BaseCard>

      <BaseCard title="模块概览" description="当前账号菜单树中可见的一级模块。">
        <div class="dashboard-view__tiles">
          <div
            v-for="item in topModules"
            :key="item.menuId"
            class="dashboard-view__tile"
            :class="{ 'is-clickable': Boolean(item.route) }"
            :role="item.route ? 'link' : undefined"
            :tabindex="item.route ? 0 : undefined"
            @click="handleModuleClick(item.route)"
            @keydown.enter="handleModuleClick(item.route)"
          >
            <component :is="item.icon" class="dashboard-view__tile-icon" />
            <span class="dashboard-view__tile-title">{{ item.title }}</span>
            <span class="dashboard-view__tile-desc">{{ item.pageTotal }} 个页面入口</span>
          </div>
        </div>
      </BaseCard>
    </section>
  </section>
</template>

<style scoped>
.dashboard-view {
  display: grid;
  gap: 18px;
}

.dashboard-view__hero {
  display: grid;
  gap: 18px;
  padding: 24px 26px;
  border: 1px solid var(--rookie-border);
  border-radius: var(--rookie-radius-lg);
  background: var(--rookie-card-bg);
  box-shadow: var(--rookie-shadow);
}

.dashboard-view__hero-copy {
  display: grid;
  gap: 8px;
}

.dashboard-view__eyebrow {
  color: var(--rookie-primary);
  font-size: var(--rookie-font-size-sm);
  font-weight: 700;
}

.dashboard-view__hero-copy h1 {
  margin: 0;
  color: var(--rookie-text);
  font-size: 26px;
  line-height: 1.2;
}

.dashboard-view__hero-copy p {
  margin: 0;
  color: var(--rookie-text-secondary);
  max-width: 680px;
}

/* 账户信息收敛为一行 pill：替代原右侧散落的两个身份盒子 */
.dashboard-view__pills {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.dashboard-view__pill {
  display: grid;
  gap: 2px;
  padding: 10px 16px;
  border: 1px solid var(--rookie-border);
  border-radius: var(--rookie-radius-md);
  background: var(--rookie-surface-weak);
}

.dashboard-view__pill span {
  color: var(--rookie-text-tertiary);
  font-size: var(--rookie-font-size-xs);
}

.dashboard-view__pill strong {
  color: var(--rookie-text);
  font-size: var(--rookie-font-size-md);
  font-weight: 600;
}

.dashboard-view__metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.dashboard-view__metric {
  display: grid;
  gap: 10px;
  padding: 18px;
  border: 1px solid var(--rookie-border);
  border-radius: var(--rookie-radius-md);
  background: var(--rookie-card-bg);
  box-shadow: var(--rookie-shadow);
}

.dashboard-view__metric-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  color: var(--rookie-text-secondary);
  font-size: var(--rookie-font-size-sm);
}

.dashboard-view__metric-icon {
  width: 18px;
  height: 18px;
  color: var(--rookie-primary);
}

.dashboard-view__metric strong {
  color: var(--rookie-text);
  font-size: 28px;
  line-height: 1;
}

.dashboard-view__metric p {
  margin: 0;
  color: var(--rookie-text-secondary);
  font-size: var(--rookie-font-size-sm);
}

.dashboard-view__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
}

/*
  统一的小卡网格：快捷入口与模块概览共用，auto-fill 自适应排布。
  项多则多排、项少则少排，两栏密度天然对齐，从根本上解决"一边挤一边空"。
*/
.dashboard-view__tiles {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 12px;
}

.dashboard-view__tile {
  display: grid;
  align-content: start;
  gap: 6px;
  padding: 16px;
  border: 1px solid var(--rookie-border);
  border-radius: var(--rookie-radius-md);
  background: var(--rookie-surface-weak);
  text-align: left;
  cursor: pointer;
  font: inherit;
  color: inherit;
  transition:
    border-color 0.2s ease,
    background-color 0.2s ease;
}

.dashboard-view__tile.is-clickable:hover,
.dashboard-view__tile:hover {
  border-color: var(--rookie-primary-border);
  background: var(--rookie-hover-bg);
}

/* 模块概览项带图标，与标题横向排布 */
.dashboard-view__tile-icon {
  width: 18px;
  height: 18px;
  color: var(--rookie-primary);
}

.dashboard-view__tile-title {
  color: var(--rookie-text);
  font-size: var(--rookie-font-size-md);
  font-weight: 600;
}

.dashboard-view__tile-desc {
  color: var(--rookie-text-secondary);
  font-size: var(--rookie-font-size-sm);
}

@media (max-width: 1024px) {
  .dashboard-view__metrics,
  .dashboard-view__grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .dashboard-view__metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
