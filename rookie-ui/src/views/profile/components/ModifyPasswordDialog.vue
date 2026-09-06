<script setup lang="ts">
/**
 * 文件作用：
 * 个人中心"修改密码"独立弹窗：原密码 / 新密码 / 确认新密码，
 * 提交走独立接口 PUT /person/password，与资料编辑（PUT /person）完全分离。
 * 已接入弹窗栈（useDialogStack）：从页面打开时无上一个弹窗，行为与普通弹窗一致。
 */
import { onBeforeUnmount, reactive, ref, watch } from 'vue'
import { ElButton, ElDialog, ElForm, ElFormItem, ElInput, ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { modifyPersonalPasswordApi } from '@/api/system/user'
import { useDialogStack } from '@/composables/useDialogStack'
import type { ModifyPasswordRequestData } from '@/types/api/system/user'

const emit = defineEmits<{
  success: []
}>()

const visible = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const rules: FormRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度需为6-20位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule, value: string, callback) => {
        if (value !== form.newPassword) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
}

// ==================== 弹窗栈（栈式互斥） ====================
// 语义：打开本弹窗时顶掉当前栈顶弹窗（如有）；用户关闭时出栈并恢复上一个。
// 本弹窗从个人中心页面直接打开，通常栈中无其他弹窗，行为与普通弹窗一致。
const dialogStack = useDialogStack()
const stackKey = Symbol('modify-password-dialog')
/** 栈隐藏守卫：区分「被栈顶掉（hide）」与「用户关闭」，避免隐藏触发的关闭误出栈 */
let hidingByStack = false

/**
 * 方法效果：
 * 仅隐藏弹窗（被更上层弹窗顶掉时调用）：置 visible=false，保留已填内容。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是隐藏弹窗。
 */
const hide = () => {
  hidingByStack = true
  visible.value = false
}

/**
 * 方法效果：
 * 仅恢复显示弹窗（栈恢复上一个时调用）：置 visible=true，不重置表单。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是重新显示弹窗。
 */
const show = () => {
  visible.value = true
}

/**
 * 方法效果：
 * 打开修改密码弹窗：注册到弹窗栈（自动隐藏当前栈顶弹窗）并重置表单。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是重置表单并展示弹窗。
 */
const open = () => {
  form.oldPassword = ''
  form.newPassword = ''
  form.confirmPassword = ''
  formRef.value?.clearValidate()
  dialogStack.open({ key: stackKey, hide, show })
  visible.value = true
}

/**
 * 方法效果：
 * 提交修改密码：校验通过后调用 PUT /person/password，
 * 成功提示并关闭弹窗（不自动登出，旧 token 有效期不变）。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是调用接口并关闭弹窗。
 */
const handleSubmit = async () => {
  if (!formRef.value) {
    return
  }
  const isValid = await formRef.value.validate().catch(() => false)
  if (!isValid) {
    return
  }

  saving.value = true

  try {
    const payload: ModifyPasswordRequestData = {
      oldPassword: form.oldPassword,
      newPassword: form.newPassword,
    }
    await modifyPersonalPasswordApi(payload)
    ElMessage.success('密码修改成功')
    visible.value = false
    emit('success')
  } finally {
    saving.value = false
  }
}

// 弹窗可见性变化统一在 watch 语义中处理：用户关闭（取消/X/Esc/遮罩）时出栈恢复上一个
watch(visible, (next) => {
  if (next) {
    return
  }
  if (hidingByStack) {
    hidingByStack = false
    return
  }
  dialogStack.close(stackKey)
})

onBeforeUnmount(() => {
  // 组件卸载时只出栈不恢复，避免误恢复正在卸载的弹窗
  dialogStack.remove(stackKey)
})

defineExpose({ open })
</script>

<template>
  <ElDialog
    v-model="visible"
    title="修改密码"
    width="480px"
    destroy-on-close
    class="modify-password-dialog"
  >
    <ElForm ref="formRef" :model="form" :rules="rules" label-width="92px">
      <ElFormItem label="原密码" prop="oldPassword">
        <ElInput
          v-model="form.oldPassword"
          type="password"
          show-password
          autocomplete="current-password"
          placeholder="请输入当前登录密码"
        />
      </ElFormItem>
      <ElFormItem label="新密码" prop="newPassword">
        <ElInput
          v-model="form.newPassword"
          type="password"
          show-password
          autocomplete="new-password"
          placeholder="6-20位"
        />
      </ElFormItem>
      <ElFormItem label="确认新密码" prop="confirmPassword">
        <ElInput
          v-model="form.confirmPassword"
          type="password"
          show-password
          autocomplete="new-password"
          placeholder="再次输入新密码"
        />
      </ElFormItem>
    </ElForm>

    <template #footer>
      <ElButton @click="visible = false">取消</ElButton>
      <ElButton type="primary" :loading="saving" @click="handleSubmit">确认修改</ElButton>
    </template>
  </ElDialog>
</template>

<style scoped>
.modify-password-dialog :deep(.el-form-item) {
  margin-bottom: 18px;
}
</style>
