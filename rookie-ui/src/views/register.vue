<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Lock, Postcard, User } from '@element-plus/icons-vue'
import { getRegisterEnabledApi, registerApi } from '@/api/system/login'
import type { RegisterRequestData } from '@/types/api/system/login'

const router = useRouter()

/**
 * 注册是否开放：由后端公开接口 GET /register/enabled 返回
 * （后端读系统设置 sys.user.registerEnabled，BOOLEAN，默认 false）。
 * 前端不直接读取系统设置接口；请求失败按"未开放"处理（与后端默认关闭一致）。
 */
const registerEnabled = ref(false)

/**
 * 控制注册按钮的加载状态。
 */
const loading = ref(false)

/**
 * 注册表单数据，与后端 RegisterBody 字段对齐。
 * 性别取值与 sys_user_sex 字典值对齐：'0' 男 / '1' 女 / '3' 未知。
 */
const form = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  nickName: '',
  phoneNumber: '',
  sex: '0',
})

/**
 * 方法效果：
 * 调用公开接口 GET /register/enabled 判断注册开关是否开放。
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
 * 提交注册：前端校验通过后调用后端 /register，
 * 成功后提示并跳转登录页（注册不自动登录）。
 * 参数：
 * - 无，直接读取当前表单状态。
 * 返回值：
 * - 无返回值；副作用是调用注册接口并跳转登录页。
 */
const handleRegister = async () => {
  const username = form.username.trim()
  const password = form.password
  const confirmPassword = form.confirmPassword
  const nickName = form.nickName.trim()
  const phoneNumber = form.phoneNumber.trim()

  // 前端校验（与后端校验口径对齐，后端仍有兜底）
  if (!username) {
    ElMessage.warning('请输入用户名')
    return
  }
  if (!/^[a-zA-Z0-9_]+$/.test(username) || username.length > 12) {
    ElMessage.warning('用户名需为12位以内的字母、数字或下划线')
    return
  }
  if (!password || password.length < 6 || password.length > 20) {
    ElMessage.warning('密码长度需为6-20位')
    return
  }
  if (password !== confirmPassword) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }
  if (phoneNumber && !/^1\d{10}$/.test(phoneNumber)) {
    ElMessage.warning('请输入正确的11位手机号')
    return
  }

  loading.value = true

  try {
    const payload: RegisterRequestData = {
      username,
      password,
      nickName: nickName || undefined,
      phoneNumber: phoneNumber || undefined,
      sex: form.sex,
    }
    await registerApi(payload)
    ElMessage.success('注册成功，请登录')
    await router.replace('/login')
  } catch (error) {
    // 后端错误文案（含"注册功能未开放"/重复提示）已由 http 工具统一弹出，这里只兜住 loading
    console.error('register failed', error)
  } finally {
    loading.value = false
  }
}

onMounted(loadRegisterEnabled)
</script>

<template>
  <!-- 注册页面区域（视觉与登录页对齐） -->
  <section class="register-view">
    <!-- 左侧品牌与系统说明区域 -->
    <div class="register-view__hero">
      <div class="register-view__brand">
        <div class="register-view__brand-mark">R</div>
        <div>
          <strong>Rookie Admin</strong>
          <span>基础管理系统</span>
        </div>
      </div>

      <div class="register-view__hero-copy">
        <p class="register-view__eyebrow">创建账号</p>
        <h1>注册后即可进入系统工作区</h1>
        <p class="register-view__summary">
          注册账号将自动获得系统默认角色，注册后使用新账号登录。
        </p>
      </div>

      <ul class="register-view__feature-list">
        <li>账号信息由系统统一管理，注册即启用</li>
        <li>默认角色权限由系统配置，管理员可在后台调整</li>
        <li>注册开关由系统设置控制，关闭时无法注册</li>
      </ul>
    </div>

    <!-- 注册表单区域 -->
    <div class="register-view__panel">
      <div class="register-view__panel-head">
        <h2>注册账号</h2>
        <p>填写以下信息完成注册。</p>
      </div>

      <!-- 注册未开放提示条 -->
      <div v-if="!registerEnabled" class="register-view__disabled-tip">
        注册功能暂未开放，请联系系统管理员。
      </div>

      <form class="register-view__form" @submit.prevent="handleRegister">
        <label class="register-view__field">
          <span>用户名 <em>*</em></span>
          <div class="register-view__input-wrap">
            <User class="register-view__field-icon" />
            <input
              v-model="form.username"
              type="text"
              autocomplete="username"
              placeholder="12位以内的字母、数字或下划线"
            />
          </div>
        </label>

        <label class="register-view__field">
          <span>昵称</span>
          <div class="register-view__input-wrap">
            <Postcard class="register-view__field-icon" />
            <input v-model="form.nickName" type="text" autocomplete="nickname" placeholder="不填则默认使用用户名" />
          </div>
        </label>

        <label class="register-view__field">
          <span>手机号</span>
          <div class="register-view__input-wrap">
            <input
              v-model="form.phoneNumber"
              type="tel"
              autocomplete="tel"
              placeholder="选填，用于账号找回等场景"
            />
          </div>
        </label>

        <label class="register-view__field">
          <span>性别</span>
          <div class="register-view__input-wrap register-view__input-wrap--select">
            <!-- 取值与 sys_user_sex 字典对齐：'0' 男 / '1' 女 / '3' 未知 -->
            <select v-model="form.sex" class="register-view__select">
              <option value="0">男</option>
              <option value="1">女</option>
              <option value="3">未知</option>
            </select>
          </div>
        </label>

        <label class="register-view__field">
          <span>密码 <em>*</em></span>
          <div class="register-view__input-wrap">
            <Lock class="register-view__field-icon" />
            <input
              v-model="form.password"
              type="password"
              autocomplete="new-password"
              placeholder="6-20位"
            />
          </div>
        </label>

        <label class="register-view__field">
          <span>确认密码 <em>*</em></span>
          <div class="register-view__input-wrap">
            <Lock class="register-view__field-icon" />
            <input
              v-model="form.confirmPassword"
              type="password"
              autocomplete="new-password"
              placeholder="再次输入密码"
            />
          </div>
        </label>

        <button
          class="register-view__submit"
          type="submit"
          :disabled="loading || !registerEnabled"
        >
          {{ loading ? '注册中...' : '注册' }}
        </button>

        <div class="register-view__options">
          <span>已有账号？</span>
          <button class="register-view__text-action" type="button" @click="router.replace('/login')">
            返回登录
          </button>
        </div>
      </form>
    </div>
  </section>
</template>

<style scoped>
.register-view {
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(360px, 440px);
  background: linear-gradient(180deg, var(--rookie-bg) 0%, var(--rookie-login-gradient-end) 100%);
}

.register-view__hero {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 48px 56px;
  color: var(--rookie-text);
  background:
    linear-gradient(180deg, color-mix(in srgb, var(--rookie-surface) 88%, transparent), transparent),
    linear-gradient(90deg, color-mix(in srgb, var(--rookie-primary) 4%, transparent), transparent 58%);
}

.register-view__brand {
  display: inline-flex;
  align-items: center;
  gap: 14px;
}

.register-view__brand-mark {
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

.register-view__brand strong {
  display: block;
}

.register-view__brand span {
  color: var(--rookie-text-secondary);
  font-size: var(--rookie-font-size-sm);
}

.register-view__hero-copy {
  max-width: 520px;
  display: grid;
  gap: 18px;
}

.register-view__eyebrow {
  margin: 0;
  color: var(--rookie-primary);
  font-size: var(--rookie-font-size-sm);
  font-weight: 700;
}

.register-view__hero-copy h1 {
  margin: 0;
  max-width: 620px;
  font-size: clamp(2.3rem, 3.4vw, 3.6rem);
  line-height: 1.12;
}

.register-view__summary {
  margin: 0;
  max-width: 560px;
  color: var(--rookie-text-secondary);
  font-size: calc(var(--rookie-font-size-lg) + 1px);
}

.register-view__feature-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: 12px;
}

.register-view__feature-list li {
  position: relative;
  padding-left: 18px;
  color: var(--rookie-text-secondary);
}

.register-view__feature-list li::before {
  content: '';
  position: absolute;
  top: 10px;
  left: 0;
  width: 6px;
  height: 6px;
  border-radius: 999px;
  background: var(--rookie-primary);
}

.register-view__panel {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 40px;
  background: color-mix(in srgb, var(--rookie-card-bg) 96%, transparent);
  border-left: 1px solid var(--rookie-border);
  backdrop-filter: blur(10px);
}

.register-view__panel-head {
  display: grid;
  gap: 8px;
  margin-bottom: 20px;
}

.register-view__panel-head h2 {
  margin: 0;
  color: var(--rookie-text);
  font-size: calc(var(--rookie-font-size-2xl) + 2px);
}

.register-view__panel-head p {
  margin: 0;
  color: var(--rookie-text-secondary);
}

.register-view__disabled-tip {
  margin-bottom: 16px;
  padding: 10px 14px;
  border: 1px solid var(--rookie-warning-border, var(--rookie-border));
  border-radius: var(--rookie-radius-md);
  background: var(--rookie-warning-soft, var(--rookie-bg-elevated));
  color: var(--rookie-warning-strong, var(--rookie-text-secondary));
  font-size: var(--rookie-font-size-sm);
}

.register-view__form {
  display: grid;
  gap: 14px;
}

.register-view__field {
  display: grid;
  gap: 6px;
}

.register-view__field span {
  color: var(--rookie-text-secondary);
  font-size: var(--rookie-font-size-sm);
}

.register-view__field em {
  color: var(--rookie-danger, var(--rookie-text-tertiary));
  font-style: normal;
}

.register-view__input-wrap {
  height: 44px;
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

.register-view__input-wrap:focus-within {
  border-color: var(--rookie-primary-border);
  box-shadow: var(--rookie-input-focus-shadow);
}

.register-view__field-icon {
  width: 18px;
  height: 18px;
  color: var(--rookie-text-tertiary);
  flex: none;
}

.register-view__input-wrap input {
  flex: 1;
  min-width: 0;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--rookie-text);
}

.register-view__input-wrap--select {
  padding-right: 8px;
}

.register-view__select {
  flex: 1;
  min-width: 0;
  height: 100%;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--rookie-text);
  cursor: pointer;
}

.register-view__submit {
  height: 46px;
  margin-top: 4px;
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

.register-view__submit:hover {
  transform: translateY(-1px);
  background: var(--rookie-primary-strong);
  box-shadow: 0 14px 28px color-mix(in srgb, var(--rookie-primary) 18%, transparent);
}

.register-view__submit:disabled {
  cursor: not-allowed;
  opacity: 0.55;
  transform: none;
  box-shadow: none;
}

.register-view__options {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  color: var(--rookie-text-secondary);
  font-size: var(--rookie-font-size-sm);
}

.register-view__text-action {
  border: 0;
  background: transparent;
  color: var(--rookie-primary);
  cursor: pointer;
}

@media (max-width: 980px) {
  .register-view {
    grid-template-columns: 1fr;
  }

  .register-view__hero {
    gap: 40px;
    padding: 32px 24px 12px;
  }

  .register-view__panel {
    padding: 24px;
    border-left: 0;
    border-top: 1px solid var(--rookie-border);
  }

  .register-view__hero-copy h1 {
    font-size: clamp(2.1rem, 9vw, 3.2rem);
  }
}
</style>
