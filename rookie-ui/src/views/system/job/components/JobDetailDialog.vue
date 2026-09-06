/**
 * 文件作用：
 * 定时任务「查看详情」只读弹窗：展示任务的完整配置信息。
 * 已接入弹窗栈（useDialogStack）：从任务列表页直接打开，无上一个弹窗时行为与普通弹窗一致。
 */
<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { ElDescriptions, ElDescriptionsItem, ElDialog, ElTag } from 'element-plus'
import { useDialogStack } from '@/composables/useDialogStack'
import { formatDateTime } from '@/utils/format'
import type { SysJobRecord } from '@/types/api/system/job'

const visible = ref(false)
const record = ref<SysJobRecord | null>(null)

// ==================== 弹窗栈（栈式互斥） ====================
const dialogStack = useDialogStack()
const stackKey = Symbol('job-detail-dialog')
/** 栈隐藏守卫：区分「被栈顶掉（hide）」与「用户关闭」 */
let hidingByStack = false

const hide = () => {
  hidingByStack = true
  visible.value = false
}

const show = () => {
  visible.value = true
}

/**
 * 方法效果：
 * 打开详情弹窗：记录待展示的任务数据并注册到弹窗栈。
 * 参数：
 * - `job`：任务行数据（列表接口已返回完整字段，无需再拉详情接口）。
 * 返回值：
 * - 无返回值；副作用是展示弹窗。
 */
const open = (job: SysJobRecord) => {
  record.value = job
  dialogStack.open({ key: stackKey, hide, show })
  visible.value = true
}

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
  dialogStack.remove(stackKey)
})

defineExpose({ open })
</script>

<template>
  <ElDialog v-model="visible" title="任务详情" width="560px" class="job-detail-dialog">
    <ElDescriptions v-if="record" :column="2" border>
      <ElDescriptionsItem label="任务编号">{{ record.jobId ?? '--' }}</ElDescriptionsItem>
      <ElDescriptionsItem label="任务名称">{{ record.jobName || '--' }}</ElDescriptionsItem>
      <ElDescriptionsItem label="Bean 名称">{{ record.beanName || '--' }}</ElDescriptionsItem>
      <ElDescriptionsItem label="方法名">{{ record.methodName || '--' }}</ElDescriptionsItem>
      <ElDescriptionsItem label="cron 表达式" :span="2">{{ record.cronExpression || '--' }}</ElDescriptionsItem>
      <ElDescriptionsItem label="执行参数" :span="2">{{ record.params || '--' }}</ElDescriptionsItem>
      <ElDescriptionsItem label="状态">
        <ElTag :type="Number(record.status) === 1 ? 'success' : 'info'" size="small">
          {{ Number(record.status) === 1 ? '启用' : '停用' }}
        </ElTag>
      </ElDescriptionsItem>
      <ElDescriptionsItem label="备注">{{ record.remark || '--' }}</ElDescriptionsItem>
      <ElDescriptionsItem label="创建时间">{{ formatDateTime(record.createTime) }}</ElDescriptionsItem>
      <ElDescriptionsItem label="更新时间">{{ formatDateTime(record.updateTime) }}</ElDescriptionsItem>
    </ElDescriptions>
  </ElDialog>
</template>
