<script setup lang="ts">
import { computed, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import PageProgressBar from '@/components/PageProgressBar.vue'
import { pingOnlineApi } from '@/api/system/online'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const userStore = useUserStore()

/**
 * 根级页面切换 key。
 * 这里只取第一层匹配路由，让登录页和后台主布局之间有整体过渡，
 * 同时避免后台内部菜单切换触发整页动画。
 */
const rootRouteKey = computed(() => route.matched[0]?.path || route.fullPath)

/**
 * 在线心跳定时器句柄（无登录态时不启动，退出登录由页面跳转离开触发组件卸载清理）。
 */
let heartbeatTimer: number | undefined

onMounted(() => {
  // 在线统计依赖请求活跃时间：用户挂机不操作时没有请求，需定时心跳维持在线状态。
  // 仅登录后启动；静默请求（失败不弹提示），401 仍会触发登录失效跳转。
  heartbeatTimer = window.setInterval(async () => {
    if (!userStore.isAuthenticated) {
      return
    }
    try {
      await pingOnlineApi()
    } catch {
      // 心跳失败静默降级（网络抖动等），下次心跳继续尝试
    }
  }, 60_000)
})

onUnmounted(() => {
  if (heartbeatTimer !== undefined) {
    window.clearInterval(heartbeatTimer)
    heartbeatTimer = undefined
  }
})
</script>

<template>
  <!-- 页面根区域 -->
  <div class="app-shell">
    <PageProgressBar />

    <!-- 根路由内容区域 -->
    <RouterView v-slot="{ Component }">
      <div :key="rootRouteKey" class="app-shell__stage">
        <component :is="Component" />
      </div>
    </RouterView>
  </div>
</template>

<style scoped>
.app-shell {
  min-height: 100vh;
}

.app-shell__stage {
  min-height: 100vh;
  animation: app-shell-stage-enter 0.28s ease;
}

@keyframes app-shell-stage-enter {
  0% {
    opacity: 0;
    transform: translateY(14px);
  }

  100% {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
