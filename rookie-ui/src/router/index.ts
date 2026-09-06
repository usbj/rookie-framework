import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useDictStore } from '@/stores/dict'
import { useLayoutNavigationStore } from '@/stores/navigation'
import { useNoticeStore } from '@/stores/notice'
import { finishPageTransition, startPageTransition } from '@/composables/usePageTransition'
import Layout from '@/layout/index.vue'
import LoginView from '@/views/login.vue'
import {
  getFirstAccessibleMenuPath,
  registerDynamicRoutes,
  unregisterDynamicRoutes,
} from './dynamicRoutes'

const DictDataView = () => import('@/views/system/dict-data/index.vue')
const DashboardView = () => import('@/views/dashboard/index.vue')
const ProfileView = () => import('@/views/profile/index.vue')

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: LoginView,
      meta: {
        public: true,
      },
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('@/views/register.vue'),
      meta: {
        public: true,
      },
    },
    {
      path: '/',
      name: 'layout',
      component: Layout,
      meta: {
        requiresAuth: true,
      },
      children: [
        {
          path: '',
          alias: ['/dashboard'],
          name: 'dashboard-home',
          component: DashboardView,
          meta: {
            requiresAuth: true,
            title: '系统首页',
          },
        },
        {
          path: 'account/profile',
          alias: ['/account/profile'],
          name: 'account-profile',
          component: ProfileView,
          meta: {
            requiresAuth: true,
            title: '个人中心',
          },
        },
        {
          path: 'system/dict-data',
          alias: ['/system/dictData'],
          name: 'system-dict-data',
          component: DictDataView,
          meta: {
            requiresAuth: true,
            title: '字典数据',
          },
        },
      ],
    },
  ],
})

/**
 * 方法效果：
 * 路由前置守卫，统一处理登录校验、用户资料恢复、菜单拉取和动态路由注册。
 * 参数：
 * - `to`：当前即将进入的目标路由。
 * 返回值：
 * - `true` 表示允许进入；
 * - 字符串或路由对象表示改道跳转。
 */
router.beforeEach(async (to) => {
  /**
   * 所有路由切换统一先开启整页进度条。
   * 真实结束时机交给页面挂载后的动画完成逻辑处理。
   */
  startPageTransition()

  /**
   * 每次路由切换时读取当前用户登录状态。
   * 这里继续沿用最基础的 token 鉴权判断。
   */
  const userStore = useUserStore()
  const dictStore = useDictStore()
  const layoutNavigationStore = useLayoutNavigationStore()
  const noticeStore = useNoticeStore()

  if (to.meta.public) {
    if (userStore.isAuthenticated && (to.path === '/login' || to.path === '/register')) {
      return '/'
    }

    return true
  }

  if (!userStore.isAuthenticated) {
    return {
      path: '/login',
      query: to.fullPath === '/' ? undefined : { redirect: to.fullPath },
    }
  }

  try {
    if (!userStore.userInfo) {
      await userStore.fetchUserProfile()
    }

    if (!layoutNavigationStore.menuLoaded) {
      await layoutNavigationStore.fetchUserMenuTree()
    }

    if (!layoutNavigationStore.dynamicRoutesReady) {
      /**
       * 每次以最新菜单树重新注册动态路由，先清再加，
       * 避免角色切换或登录态恢复时残留旧账号的页面访问入口。
       */
      unregisterDynamicRoutes(router)
      registerDynamicRoutes(router, layoutNavigationStore.rawMenuTree)
      layoutNavigationStore.markDynamicRoutesReady()
      await dictStore.initializeDictionaries().catch(() => undefined)
      // 首屏即拉取当前用户的通知，让头导航铃铛在网站加载时就显示最新未读
      await noticeStore.fetchMyNotices().catch(() => undefined)

      const fallbackPath = getFirstAccessibleMenuPath(layoutNavigationStore.rawMenuTree)

      /**
       * 动态路由刚注入完成时，需要重新用完整路由表解析一次目标地址。
       * 如果解析后仍然没有命中，说明当前目标已经不属于本次菜单树，直接回退到首个可访问页面。
       */
      if (router.resolve(to.fullPath).matched.length === 0) {
        return fallbackPath
      }

      return to.fullPath
    }

    if (!dictStore.initialized) {
      await dictStore.initializeDictionaries().catch(() => undefined)
    }
  } catch {
    userStore.logout()
    layoutNavigationStore.resetNavigationState()
    noticeStore.resetNoticeState()
    unregisterDynamicRoutes(router)

    return {
      path: '/login',
      query: to.fullPath === '/' ? undefined : { redirect: to.fullPath },
    }
  }

  return true
})

/**
 * 方法效果：
 * 路由后置守卫，统一结束顶部页面切换进度条。
 * 参数：
 * - 无显式业务参数，回调由 Vue Router 在路由确认后触发。
 * 返回值：
 * - 无返回值；副作用是结束页面过渡状态。
 */
router.afterEach(() => {
  /**
   * 路由确认完成后统一结束顶部进度条。
   * 这样菜单切换不再依赖具体页面组件内部的收尾钩子，能避免停在最后一小段。
   */
  finishPageTransition(260)
})

export default router
