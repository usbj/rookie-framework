import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { logoutApi } from '@/api/system/login'
import {
  fetchPersonalAvatarApi,
  getPersonalProfileApi,
  uploadPersonalAvatarApi,
} from '@/api/system/user'
import { useDictStore } from '@/stores/dict'
import { useSysConfigStore } from '@/stores/system-config'
import type { LoginResponseData } from '@/types/api/system/login'
import type { SysUserProfile } from '@/types/api/system/user'

export const USER_TOKEN_STORAGE_KEY = 'rookie-user-token'
export const USER_INFO_STORAGE_KEY = 'rookie-user-info'

/**
 * 读取本地缓存的用户信息。
 * 这里单独拆方法，是为了让初始化逻辑更清楚，也便于后续替换成更完整的鉴权恢复逻辑。
 */
const readStoredUserInfo = (): SysUserProfile | null => {
  const rawUserInfo = localStorage.getItem(USER_INFO_STORAGE_KEY)

  if (!rawUserInfo) {
    return null
  }

  try {
    return JSON.parse(rawUserInfo) as SysUserProfile
  } catch {
    localStorage.removeItem(USER_INFO_STORAGE_KEY)
    return null
  }
}

export const useUserStore = defineStore('user', () => {
  /**
   * 当前登录 token。
   * 后端 /login 返回的 data 目前就是这个字符串。
   */
  const token = ref<string>(localStorage.getItem(USER_TOKEN_STORAGE_KEY) || '')

  /**
   * 当前登录用户信息。
   * 这里存储 /person 返回的真实个人资料，用于头导航与个人中心复用。
   */
  const userInfo = ref<SysUserProfile | null>(readStoredUserInfo())

  /**
   * 当前用户头像的 objectURL（blob 转临时地址）。
   * `<img>` 标签无法携带 Token 请求头，头像统一走 blob 拉取后转 objectURL 展示；
   * 无头像时为 null，界面显示字母占位。
   */
  const avatarUrl = ref<string | null>(null)

  /**
   * 是否处于已登录状态。
   * 目前以 token 是否存在作为最基础的登录判断条件。
   */
  const isAuthenticated = computed(() => Boolean(token.value))

  /**
   * 顶部导航展示名。
   * 优先使用昵称，没有昵称时退回账号名。
   */
  const displayName = computed(() => userInfo.value?.nickName || userInfo.value?.username || '未登录')

  /**
   * 给头导航和个人中心提供一份更轻的展示数据。
   * 这样页面层不需要反复处理原始接口结构。
   */
  const profileSummary = computed(() => {
    if (!userInfo.value) {
      return null
    }

    return {
      userId: userInfo.value.userId,
      username: userInfo.value.username,
      nickName: userInfo.value.nickName,
      phoneNumber: userInfo.value.phoneNumber,
      sex: userInfo.value.sex,
      status: userInfo.value.status,
      createTime: userInfo.value.createTime,
      roleNames: userInfo.value.userRole?.map((role) => role.roleName).filter(Boolean) ?? [],
    }
  })

  /**
   * 登录成功后先写入 token。
   * 个人资料改为通过 /person 拉取，避免继续依赖本地 mock 数据。
   */
  const setLoginSession = (loginToken: LoginResponseData) => {
    token.value = loginToken

    localStorage.setItem(USER_TOKEN_STORAGE_KEY, loginToken)
  }

  /**
   * 使用接口返回的个人资料更新 store 和本地缓存。
   */
  const setUserProfile = (profile: SysUserProfile) => {
    userInfo.value = profile
    localStorage.setItem(USER_INFO_STORAGE_KEY, JSON.stringify(profile))
  }

  const fetchUserProfile = async () => {
    const result = await getPersonalProfileApi()
    setUserProfile(result.data)
    // 资料拉取后同步加载头像（内部容错，失败回退字母占位，不影响主流程）
    await refreshAvatar()
    return result.data
  }

  /**
   * 方法效果：
   * 释放当前头像 objectURL（换头像/退出登录时调用，避免内存泄漏）。
   * 参数：
   * - 无。
   * 返回值：
   * - 无返回值；副作用是清空 avatarUrl 并 revoke 旧 URL。
   */
  const clearAvatarUrl = () => {
    if (avatarUrl.value) {
      URL.revokeObjectURL(avatarUrl.value)
      avatarUrl.value = null
    }
  }

  /**
   * 方法效果：
   * 按当前用户资料的 avatar 字段拉取头像 blob 并转 objectURL。
   * 无头像或拉取失败时清空头像（失败不抛错，界面回退字母占位）。
   * 参数：
   * - 无。
   * 返回值：
   * - 无返回值；副作用是更新 avatarUrl。
   */
  const refreshAvatar = async () => {
    if (!userInfo.value?.avatar) {
      clearAvatarUrl()
      return
    }
    try {
      const blob = await fetchPersonalAvatarApi()
      const nextUrl = URL.createObjectURL(blob)
      // 先释放旧 URL 再替换，避免旧头像 URL 泄漏
      if (avatarUrl.value) {
        URL.revokeObjectURL(avatarUrl.value)
      }
      avatarUrl.value = nextUrl
    } catch {
      // 拉取失败（如存储文件缺失）回退字母占位，不向上抛错影响主流程
      clearAvatarUrl()
    }
  }

  /**
   * 方法效果：
   * 上传当前用户头像：调后端接口 → 刷新个人资料 → 重新拉取头像。
   * 参数：
   * - `file`：头像图片文件。
   * 返回值：
   * - 无返回值；副作用是更新后端头像并刷新 store 中的用户信息与头像 URL。
   */
  const updateAvatar = async (file: File) => {
    const result = await uploadPersonalAvatarApi(file)
    if (result.data) {
      // 后端返回新存储名，重新拉资料保证 avatar 字段与本地一致（内部会同步刷新头像）
      await fetchUserProfile()
    }
  }

  /**
   * 清空当前登录态。
   * 先通知后端退出（移除在线集合 + 删除登录态缓存，旧 token 失效），
   * 再清空本地 token 与用户信息；后端调用失败不阻塞本地退出（静默降级）。
   */
  const logout = async () => {
    const dictStore = useDictStore()
    const sysConfigStore = useSysConfigStore()

    try {
      await logoutApi()
    } catch {
      // 后端退出失败（网络异常等）不阻塞本地清理，token 仍会在有效期内自然过期
    }

    token.value = ''
    userInfo.value = null

    localStorage.removeItem(USER_TOKEN_STORAGE_KEY)
    localStorage.removeItem(USER_INFO_STORAGE_KEY)
    dictStore.clearDictCache()
    sysConfigStore.clearSysConfigCache()
    clearAvatarUrl()
  }

  return {
    token,
    userInfo,
    avatarUrl,
    isAuthenticated,
    displayName,
    profileSummary,
    setLoginSession,
    setUserProfile,
    fetchUserProfile,
    refreshAvatar,
    updateAvatar,
    logout,
  }
})
