<script setup lang="ts">
import { computed } from 'vue'
import { ElScrollbar } from 'element-plus'
import { House } from '@element-plus/icons-vue'
import appLogo from '@/assets/logo.svg'
import { useLayoutNavigationStore } from '@/stores/navigation'
import type { NavigationMenuItem } from '@/types/components/navigation'
import SideBarSection from './components/SideBarSection.vue'

defineProps<{
  collapsed: boolean
}>()

const layoutNavigationStore = useLayoutNavigationStore()

const staticEntries = computed<NavigationMenuItem[]>(() => [
  {
    menuId: -1,
    menuName: '系统首页',
    permKey: '',
    parentId: 0,
    menuType: 2,
    routeSegment: '',
    route: '/',
    path: 'dashboard/index',
    backlinks: 0,
    icon: 'House',
    iconComponent: House,
    status: 1,
    children: [],
  },
])
</script>

<template>
  <!-- 侧边栏区域 -->
  <aside class="side-bar" :class="{ 'is-collapsed': collapsed }">
    <div class="side-bar__brand">
      <img class="side-bar__brand-logo" :src="appLogo" alt="Rookie 标志" />

      <div v-if="!collapsed" class="side-bar__brand-copy">
        <strong>Rookie Admin</strong>
        <span>基础管理系统</span>
      </div>
    </div>

    <ElScrollbar class="side-bar__scroll">
      <nav class="side-bar__nav" aria-label="主导航">
        <SideBarSection
          v-for="section in staticEntries"
          :key="section.menuId"
          :item="section"
          :collapsed="collapsed"
          :current-path="layoutNavigationStore.currentPath"
          :expanded="false"
          :expanded-directory-ids="layoutNavigationStore.expandedDirectoryIds"
          @toggle="layoutNavigationStore.toggleDirectory"
        />

        <SideBarSection
          v-for="section in layoutNavigationStore.menuTree"
          :key="section.menuId"
          :item="section"
          :collapsed="collapsed"
          :current-path="layoutNavigationStore.currentPath"
          :expanded="layoutNavigationStore.isDirectoryExpanded(section.menuId)"
          :expanded-directory-ids="layoutNavigationStore.expandedDirectoryIds"
          @toggle="layoutNavigationStore.toggleDirectory"
        />
      </nav>
    </ElScrollbar>
  </aside>
</template>

<style scoped>
.side-bar {
  width: var(--rookie-sidebar-width);
  height: 100vh;
  display: flex;
  flex-direction: column;
  padding: 1.125rem 1rem;
  background: var(--rookie-card-bg);
  border-right: 1px solid var(--rookie-border);
  box-shadow: var(--rookie-shadow);
  transition: width 0.24s ease;
  overflow: hidden;
  flex: none;
}

.side-bar.is-collapsed {
  width: var(--rookie-sidebar-collapsed-width);
}

.side-bar__brand {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  min-height: 2.625rem;
  margin-bottom: 1.125rem;
}

.side-bar__brand-logo {
  width: 2.625rem;
  height: 2.625rem;
  flex: none;
}

.side-bar__brand-copy {
  min-width: 0;
  flex: 1;
  display: grid;
}

.side-bar__brand-copy strong {
  font-size: var(--rookie-font-size-lg);
  font-weight: 700;
  color: var(--rookie-text);
}

.side-bar__brand-copy span {
  font-size: var(--rookie-font-size-xs);
  color: var(--rookie-text-tertiary);
}

.side-bar__scroll {
  flex: 1;
  min-height: 0;
}

/* 侧边栏滚动时隐藏滚动条：内容仍可滚动，仅不显示滚动条轨道/滑块 */
.side-bar__scroll :deep(.el-scrollbar__bar) {
  display: none;
}

.side-bar__nav {
  display: grid;
  gap: 0.625rem;
  padding: 0.5rem 0 0.75rem;
}

.is-collapsed .side-bar__brand {
  justify-content: center;
}

.is-collapsed .side-bar__brand-logo {
  margin-right: 0;
}
</style>
