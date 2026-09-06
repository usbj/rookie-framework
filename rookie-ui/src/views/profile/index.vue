<script setup lang="ts">
/**
 * 文件作用：
 * 承接个人中心页面的资料展示与编辑，
 * 并与当前登录用户的个人信息接口保持同步。
 */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import SharedFormPanel from '@/components/SharedFormPanel.vue'
import UserAvatar from '@/components/UserAvatar.vue'
import { updatePersonalProfileApi } from '@/api/system/user'
import { useUserStore } from '@/stores/user'
import type { UpdatePersonalProfilePayload } from '@/types/api/system/user'
import type { SharedFieldSchemaMap } from '@/types/components/data-display'
import ModifyPasswordDialog from './components/ModifyPasswordDialog.vue'

const userStore = useUserStore()
const saving = ref(false)

const form = reactive<UpdatePersonalProfilePayload>({
  nickName: '',
  phoneNumber: '',
  sex: '0',
})

const profileSummary = computed(() => userStore.profileSummary)

const profileFormSchema = computed<SharedFieldSchemaMap<UpdatePersonalProfilePayload>>(() => ({
  nickName: {
    label: '昵称',
    inputType: 'text',
    placeholder: '请输入昵称',
    formVisible: true,
    tableVisible: false,
    formOrder: 1,
  },
  phoneNumber: {
    label: '手机号',
    inputType: 'text',
    placeholder: '请输入手机号',
    formVisible: true,
    tableVisible: false,
    formOrder: 2,
  },
  sex: {
    label: '性别',
    inputType: 'select',
    placeholder: '请选择性别',
    formVisible: true,
    tableVisible: false,
    formOrder: 3,
    // 取值与 sys_user_sex 字典对齐：'0' 男 / '1' 女 / '3' 未知
    options: [
      { label: '男', value: '0' },
      { label: '女', value: '1' },
    ],
  },
}))

/**
 * store 中的个人资料一旦更新，就同步回表单。
 * 这样页面首屏读取和保存成功后的回显都走同一条数据流。
 */
watch(
  profileSummary,
  (profile) => {
    if (!profile) {
      return
    }

    form.nickName = profile.nickName || ''
    form.phoneNumber = profile.phoneNumber || ''
    form.sex = profile.sex || '0'
  },
  { immediate: true },
)

onMounted(async () => {
  if (!userStore.userInfo) {
    await userStore.fetchUserProfile()
  }
})

/**
 * 方法效果：
 * 保存个人资料表单，并在接口成功后刷新当前登录用户资料。
 * 参数：
 * - 无，直接读取当前页面中的表单状态。
 * 返回值：
 * - 无返回值；副作用是更新后端个人资料并刷新 store 中的用户信息。
 */
const handleSaveProfile = async () => {
  if (!form.nickName.trim()) {
    ElMessage.warning('请输入昵称')
    return
  }

  saving.value = true

  try {
    const payload: UpdatePersonalProfilePayload = {
      nickName: form.nickName.trim(),
      phoneNumber: form.phoneNumber.trim(),
      sex: form.sex,
    }

    const result = await updatePersonalProfileApi(payload)

    if (result.data) {
      await userStore.fetchUserProfile()
      ElMessage.success('个人资料已更新')
    }
  } finally {
    saving.value = false
  }
}

/**
 * 修改密码弹窗引用，供"修改密码"按钮打开。
 */
const passwordDialogRef = ref<InstanceType<typeof ModifyPasswordDialog>>()

/**
 * 方法效果：
 * 打开修改密码弹窗（独立弹窗，与资料编辑分离）。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是展示弹窗。
 */
const handleOpenPasswordDialog = () => {
  passwordDialogRef.value?.open()
}

/**
 * 头像上传状态：上传中禁用再次点击。
 */
const avatarUploading = ref(false)

/**
 * 隐藏的文件选择框引用，点击头像区时触发其选择文件。
 */
const avatarFileInput = ref<HTMLInputElement>()

/**
 * 方法效果：
 * 点击头像区，触发隐藏文件选择框。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是打开系统文件选择框。
 */
const handleClickAvatar = () => {
  if (avatarUploading.value) {
    return
  }
  avatarFileInput.value?.click()
}

/**
 * 方法效果：
 * 校验并上传新头像：前端先做类型/大小预检（与后端口径一致），
 * 通过后交给 user store 上传并刷新资料与头像展示。
 * 参数：
 * - `event`：文件选择框的 change 事件。
 * 返回值：
 * - 无返回值；副作用是上传头像并更新界面展示。
 */
const handleAvatarFileChange = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  // 每次选择后清空 input，保证连续选择同一文件也能触发 change
  input.value = ''

  if (!file) {
    return
  }

  const ALLOWED_EXTS = ['png', 'jpg', 'jpeg', 'gif', 'webp']
  const ext = file.name.includes('.') ? file.name.split('.').pop()!.toLowerCase() : ''
  if (!ALLOWED_EXTS.includes(ext)) {
    ElMessage.warning('头像仅支持 png/jpg/jpeg/gif/webp 格式')
    return
  }
  if (file.size > 2 * 1024 * 1024) {
    ElMessage.warning('头像大小不能超过2MB')
    return
  }

  avatarUploading.value = true
  try {
    await userStore.updateAvatar(file)
    ElMessage.success('头像已更新')
  } finally {
    avatarUploading.value = false
  }
}

/**
 * 方法效果：
 * 接收公共表单组件提交的新模型，并同步回当前页面的响应式表单对象。
 * 参数：
 * - `nextFormValue`：公共表单组件回传的最新表单值。
 * 返回值：
 * - 无返回值；副作用是覆盖当前页的本地表单状态。
 */
const handleFormModelUpdate = (nextFormValue: Record<string, unknown>) => {
  /**
   * 这里显式逐项回写，而不是直接替换整个 reactive 对象，
   * 是为了保持当前页面中已有的响应式引用不丢失。
   */
  form.nickName = String(nextFormValue.nickName ?? '')
  form.phoneNumber = String(nextFormValue.phoneNumber ?? '')
  form.sex = String(nextFormValue.sex ?? '0')
}
</script>

<template>
  <!-- 个人中心页面区域 -->
  <section class="profile-view">
    <header class="profile-view__header">
      <div>
        <h1>个人中心</h1>
        <p>维护当前登录账号的基础资料与登录密码。</p>
      </div>
      <button
        class="profile-view__avatar"
        type="button"
        aria-label="更换头像"
        :title="avatarUploading ? '头像上传中…' : '点击更换头像'"
        :disabled="avatarUploading"
        @click="handleClickAvatar"
      >
        <UserAvatar
          :name="profileSummary?.nickName || profileSummary?.username || 'U'"
          :src="userStore.avatarUrl ?? undefined"
        />
        <span class="profile-view__avatar-hint">{{ avatarUploading ? '上传中…' : '更换头像' }}</span>
      </button>
      <input
        ref="avatarFileInput"
        class="profile-view__avatar-input"
        type="file"
        accept=".png,.jpg,.jpeg,.gif,.webp,image/png,image/jpeg,image/gif,image/webp"
        @change="handleAvatarFileChange"
      />
    </header>

    <div class="profile-view__content">
      <section class="profile-view__panel">
        <header class="profile-view__panel-head">
          <h2>账号信息</h2>
          <p>只保留基础系统里真正常用的信息。</p>
        </header>

        <dl class="profile-view__summary-list">
          <div class="profile-view__summary-row">
            <dt>登录账号</dt>
            <dd>{{ profileSummary?.username || '--' }}</dd>
          </div>
          <div class="profile-view__summary-row">
            <dt>当前昵称</dt>
            <dd>{{ profileSummary?.nickName || '--' }}</dd>
          </div>
          <div class="profile-view__summary-row">
            <dt>手机号</dt>
            <dd>{{ profileSummary?.phoneNumber || '--' }}</dd>
          </div>
          <div class="profile-view__summary-row">
            <dt>角色</dt>
            <dd>{{ profileSummary?.roleNames.join('、') || '未分配角色' }}</dd>
          </div>
        </dl>
      </section>

      <section class="profile-view__panel">
        <header class="profile-view__panel-head">
          <h2>资料修改</h2>
          <p>保存后会同步更新顶部当前用户展示信息。</p>
          <div class="profile-view__panel-toolbar">
            <button class="profile-view__password-btn" type="button" @click="handleOpenPasswordDialog">
              修改密码
            </button>
          </div>
        </header>

        <SharedFormPanel
          :schema="profileFormSchema"
          :model-value="form as unknown as Record<string, unknown>"
          :loading="saving"
          :columns="2"
          @update:model-value="handleFormModelUpdate"
          @submit="handleSaveProfile"
        />
      </section>
    </div>

    <!-- 修改密码弹窗：独立接口 PUT /person/password，与资料编辑分离 -->
    <ModifyPasswordDialog ref="passwordDialogRef" />
  </section>
</template>

<style scoped>
.profile-view {
  display: grid;
  gap: 18px;
}

.profile-view__header,
.profile-view__panel {
  border: 1px solid var(--rookie-border);
  border-radius: var(--rookie-radius-lg);
  background: var(--rookie-card-bg);
  box-shadow: var(--rookie-shadow);
}

.profile-view__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 22px 24px;
}

.profile-view__header h1,
.profile-view__panel-head h2 {
  margin: 0;
  color: var(--rookie-text);
}

.profile-view__header p,
.profile-view__panel-head p {
  margin: 0;
  color: var(--rookie-text-secondary);
}

.profile-view__avatar {
  position: relative;
  width: 52px;
  height: 52px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  background: var(--rookie-avatar-bg);
  color: var(--rookie-primary-strong);
  font-size: var(--rookie-font-size-xl);
  font-weight: 700;
  flex: none;
  overflow: hidden;
  padding: 0;
  border: 1px solid var(--rookie-border);
  cursor: pointer;
  transition: border-color 0.2s ease;
}

.profile-view__avatar:hover {
  border-color: var(--rookie-primary);
}

.profile-view__avatar:disabled {
  cursor: default;
  opacity: 0.7;
}

/* 悬停遮罩提示"更换头像"，上传中显示"上传中…" */
.profile-view__avatar-hint {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.45);
  color: #fff;
  font-size: var(--rookie-font-size-xs);
  font-weight: 500;
  opacity: 0;
  transition: opacity 0.2s ease;
}

.profile-view__avatar:hover .profile-view__avatar-hint,
.profile-view__avatar:disabled .profile-view__avatar-hint {
  opacity: 1;
}

/* 隐藏的文件选择框：不占布局、不可见，由头像按钮触发 */
.profile-view__avatar-input {
  display: none;
}

.profile-view__content {
  display: grid;
  grid-template-columns: minmax(280px, 0.88fr) minmax(0, 1.12fr);
  gap: 18px;
}

.profile-view__panel {
  padding: 22px 24px;
  display: grid;
  gap: 18px;
}

.profile-view__panel-head {
  display: grid;
  gap: 8px;
}

.profile-view__panel-toolbar {
  display: flex;
  justify-content: flex-end;
}

.profile-view__password-btn {
  border: 1px solid var(--rookie-primary-border);
  border-radius: var(--rookie-radius-md);
  padding: 7px 14px;
  background: var(--rookie-primary-soft);
  color: var(--rookie-primary-strong);
  font-size: var(--rookie-font-size-sm);
  cursor: pointer;
  transition:
    background 0.2s ease,
    color 0.2s ease;
}

.profile-view__password-btn:hover {
  background: var(--rookie-primary);
  color: var(--rookie-text-inverse);
}

.profile-view__summary-list {
  display: grid;
  gap: 14px;
  margin: 0;
}

.profile-view__summary-row {
  display: grid;
  grid-template-columns: 92px minmax(0, 1fr);
  gap: 12px;
  padding: 14px 0;
  border-bottom: 1px solid var(--rookie-border);
}

.profile-view__summary-row:last-child {
  border-bottom: 0;
  padding-bottom: 0;
}

.profile-view__summary-row:first-child {
  padding-top: 0;
}

.profile-view__summary-row dt {
  color: var(--rookie-text-secondary);
  font-size: var(--rookie-font-size-sm);
  margin: 0;
}

.profile-view__summary-row dd {
  color: var(--rookie-text);
  margin: 0;
  word-break: break-word;
}

@media (max-width: 1024px) {
  .profile-view__header,
  .profile-view__content {
    display: grid;
    grid-template-columns: 1fr;
  }
}
</style>
