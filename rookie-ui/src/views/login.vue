<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Lock, User } from '@element-plus/icons-vue'
import { getRegisterEnabledApi, loginApi } from '@/api/system/login'
import { useUserStore } from '@/stores/user'
import type { LoginRequestData } from '@/types/api/system/login'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

/**
 * 控制登录按钮的加载状态。
 */
const loading = ref(false)

/**
 * 注册是否开放：由后端公开接口 GET /register/enabled 返回
 * （后端读系统设置 sys.user.registerEnabled，BOOLEAN，默认 false）。
 * 前端不直接读取系统设置接口；读取失败按"未开放"处理（与后端默认关闭一致），
 * 此时隐藏"注册账号"入口。
 */
const registerEnabled = ref(false)

/**
 * 登录表单数据。
 * 账号字段最终会映射为后端需要的 username。
 * 不再提供"记住本次登录"：后端 token 自带过期时间，前端切换存储范围无意义；
 * 账号也不回填本地浏览器，避免在本机暴露登录账号带来安全问题。
 */
const form = reactive({
  username: 'admin',
  password: 'rookie',
})

/**
 * 处理登录操作。
 * 当前直接调用后端 /login，
 * 登录成功后交给路由守卫继续拉取个人资料、菜单并注册动态路由。
 */
const handleLogin = async () => {
  const username = form.username.trim()
  const password = form.password.trim()

  if (!username || !password) {
    ElMessage.warning('请输入账号和密码')
    return
  }

  loading.value = true

  try {
    const loginPayload: LoginRequestData = {
      username,
      password,
    }
    const loginResult = await loginApi(loginPayload)
    userStore.setLoginSession(loginResult.data)
    ElMessage.success('登录成功')

    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    await router.replace(redirect)
  } catch (error) {
    /**
     * 通用报错提示已经在 http 工具里处理过，
     * 这里保留 catch 主要用于兜住异步流程并确保 loading 正常收起。
     */
    console.error('login failed', error)
  } finally {
    loading.value = false
  }
}

/**
 * 处理"忘记密码"操作。
 * 当前本系统后端未提供自助找回 / 重置密码接口，也无邮件、短信等通知通道，
 * 因此不引导用户进入任何"自助找回"表单，避免提交后接口报错形成误导。
 * 这里只给出明确的兜底提示，让用户联系系统管理员重置密码。
 */
const handleForgotPassword = () => {
  ElMessageBox.alert(
    '本系统暂未开放自助找回密码通道，请联系系统管理员重置密码。',
    '忘记密码',
    {
      confirmButtonText: '我知道了',
      type: 'info',
    },
  )
}

/**
 * 方法效果：
 * 调用公开接口 GET /register/enabled 判断注册开关是否开放，控制"注册账号"入口显隐。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是更新 registerEnabled 状态。
 */
const loadRegisterEnabled = async () => {
  try {
    const result = await getRegisterEnabledApi()
    registerEnabled.value = result.data === true
  } catch {
    registerEnabled.value = false
  }
}

/**
 * 方法效果：
 * 跳转注册页（/register 为 public 路由，无需登录）。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是触发路由跳转。
 */
const handleGoRegister = () => {
  router.push('/register')
}

onMounted(loadRegisterEnabled)
</script>

<template>
  <!-- 登录页面区域 -->
  <section class="login-view">
    <!-- 登录页左侧品牌与系统说明区域 -->
    <div class="login-view__hero">
      <div class="login-view__brand">
        <div class="login-view__brand-mark">R</div>
        <div>
          <strong>Rookie Admin</strong>
          <span>基础管理系统</span>
        </div>
      </div>

      <div class="login-view__hero-copy">
        <p class="login-view__eyebrow">统一管理入口</p>
        <h1>稳定、清晰地进入系统工作区</h1>
        <p class="login-view__summary">
          面向用户、角色、菜单与系统配置等日常管理场景，保持清晰的信息入口和一致的操作体验。
        </p>
      </div>

      <div class="login-view__overview">
        <div class="login-view__overview-card">
          <span>系统定位</span>
          <strong>基础管理后台</strong>
          <p>用于承接权限、配置、流程与基础数据维护。</p>
        </div>

        <div class="login-view__overview-card">
          <span>登录方式</span>
          <strong>统一账号认证</strong>
          <p>当前登录会调用后端接口，并按返回 token 写入本地登录态。</p>
        </div>
      </div>

      <ul class="login-view__feature-list">
        <li>统一导航结构，适合承接角色动态菜单与标签页工作流</li>
        <li>基础主题变量统一管理，便于后续做品牌化和明暗切换</li>
        <li>界面信息层级收敛，适合作为管理系统的长期基础壳</li>
      </ul>
    </div>

    <!-- 登录表单区域 -->
    <div class="login-view__panel">
      <div class="login-view__panel-head">
        <h2>账号登录</h2>
        <p>输入账号与密码后进入系统主页。</p>
      </div>

      <form class="login-view__form" @submit.prevent="handleLogin">
        <label class="login-view__field">
          <span>账号</span>
          <div class="login-view__input-wrap">
            <User class="login-view__field-icon" />
            <input v-model="form.username" type="text" autocomplete="username" placeholder="请输入账号" />
          </div>
        </label>

        <label class="login-view__field">
          <span>密码</span>
          <div class="login-view__input-wrap">
            <Lock class="login-view__field-icon" />
            <input
              v-model="form.password"
              type="password"
              autocomplete="current-password"
              placeholder="请输入密码"
            />
          </div>
        </label>

        <div class="login-view__options">
          <button
            v-if="registerEnabled"
            class="login-view__text-action"
            type="button"
            @click="handleGoRegister"
          >
            注册账号
          </button>
          <button
            class="login-view__text-action"
            type="button"
            @click="handleForgotPassword"
          >
            忘记密码
          </button>
        </div>

        <button class="login-view__submit" type="submit" :disabled="loading">
          {{ loading ? '登录中...' : '进入系统' }}
        </button>
      </form>
    </div>
  </section>
</template>

<style scoped>
.login-view {
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(360px, 440px);
  background: linear-gradient(180deg, var(--rookie-bg) 0%, var(--rookie-login-gradient-end) 100%);
}

.login-view__hero {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 48px 56px;
  color: var(--rookie-text);
  background:
    linear-gradient(180deg, color-mix(in srgb, var(--rookie-surface) 88%, transparent), transparent),
    linear-gradient(90deg, color-mix(in srgb, var(--rookie-primary) 4%, transparent), transparent 58%);
}

.login-view__brand {
  display: inline-flex;
  align-items: center;
  gap: 14px;
}

.login-view__brand-mark {
  width: 42px;
  height: 42px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--rookie-radius-md);
  background: var(--rookie-primary-soft);
  color: var(--rookie-primary-strong);
  font-size: var(--rookie-font-size-xl);
  font-weight: 700;
}

.login-view__brand strong,
.login-view__overview strong {
  display: block;
}

.login-view__brand span,
.login-view__overview span {
  color: var(--rookie-text-secondary);
  font-size: var(--rookie-font-size-sm);
}

.login-view__hero-copy {
  max-width: 520px;
  display: grid;
  gap: 18px;
}

.login-view__eyebrow {
  margin: 0;
  color: var(--rookie-primary);
  font-size: var(--rookie-font-size-sm);
  font-weight: 700;
}

.login-view__hero-copy h1 {
  margin: 0;
  max-width: 620px;
  font-size: clamp(2.3rem, 3.4vw, 3.6rem);
  line-height: 1.12;
}

.login-view__summary {
  margin: 0;
  max-width: 560px;
  color: var(--rookie-text-secondary);
  font-size: calc(var(--rookie-font-size-lg) + 1px);
}

.login-view__overview {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.login-view__overview-card {
  padding: 18px 20px;
  border: 1px solid var(--rookie-border);
  border-radius: var(--rookie-radius-lg);
  background: color-mix(in srgb, var(--rookie-surface) 92%, transparent);
  box-shadow: var(--rookie-shadow);
}

.login-view__overview-card p {
  margin: 8px 0 0;
  color: var(--rookie-text-secondary);
  font-size: var(--rookie-font-size-sm);
}

.login-view__feature-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: 12px;
}

.login-view__feature-list li {
  position: relative;
  padding-left: 18px;
  color: var(--rookie-text-secondary);
}

.login-view__feature-list li::before {
  content: '';
  position: absolute;
  top: 10px;
  left: 0;
  width: 6px;
  height: 6px;
  border-radius: 999px;
  background: var(--rookie-primary);
}

.login-view__panel {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 40px;
  background: color-mix(in srgb, var(--rookie-card-bg) 96%, transparent);
  border-left: 1px solid var(--rookie-border);
  backdrop-filter: blur(10px);
}

.login-view__panel-head {
  display: grid;
  gap: 8px;
  margin-bottom: 28px;
}

.login-view__panel-head h2 {
  margin: 0;
  color: var(--rookie-text);
  font-size: calc(var(--rookie-font-size-2xl) + 2px);
}

.login-view__panel-head p {
  margin: 0;
  color: var(--rookie-text-secondary);
}

.login-view__form {
  display: grid;
  gap: 18px;
}

.login-view__field {
  display: grid;
  gap: 8px;
}

.login-view__field span {
  color: var(--rookie-text-secondary);
  font-size: var(--rookie-font-size-sm);
}

.login-view__input-wrap {
  height: 48px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 14px;
  border: 1px solid var(--rookie-border);
  border-radius: var(--rookie-radius-md);
  background: var(--rookie-bg-elevated);
  box-shadow: var(--rookie-input-shadow);
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease;
}

.login-view__input-wrap:focus-within {
  border-color: var(--rookie-primary-border);
  box-shadow: var(--rookie-input-focus-shadow);
}

.login-view__field-icon {
  width: 18px;
  height: 18px;
  color: var(--rookie-text-tertiary);
  flex: none;
}

.login-view__input-wrap input {
  flex: 1;
  min-width: 0;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--rookie-text);
}

.login-view__options {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.login-view__text-action {
  border: 0;
  background: transparent;
  color: var(--rookie-primary);
  cursor: pointer;
}

.login-view__submit {
  height: 48px;
  border: 1px solid var(--rookie-primary-border);
  border-radius: var(--rookie-radius-md);
  background: var(--rookie-primary);
  color: var(--rookie-text-inverse);
  font-weight: 700;
  cursor: pointer;
  transition:
    transform 0.2s ease,
    box-shadow 0.2s ease,
    opacity 0.2s ease;
}

.login-view__submit:hover {
  transform: translateY(-1px);
  background: var(--rookie-primary-strong);
  box-shadow: 0 14px 28px color-mix(in srgb, var(--rookie-primary) 18%, transparent);
}

.login-view__submit:disabled {
  cursor: wait;
  opacity: 0.7;
  transform: none;
  box-shadow: none;
}

@media (max-width: 980px) {
  .login-view {
    grid-template-columns: 1fr;
  }

  .login-view__hero {
    gap: 40px;
    padding: 32px 24px 12px;
  }

  .login-view__overview {
    grid-template-columns: 1fr;
  }

  .login-view__panel {
    padding: 24px;
    border-left: 0;
    border-top: 1px solid var(--rookie-border);
  }

  .login-view__hero-copy h1 {
    font-size: clamp(2.1rem, 9vw, 3.2rem);
  }
}
</style>
