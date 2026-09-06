/**
 * 文件作用：
 * 承接系统模块下的定时任务管理页面，
 * 负责任务列表查询、新增、编辑、删除、启停与立即执行。
 * 任务调度由后端动态注册（CronTrigger），保存/启停后新配置立即生效，无需重启。
 */
<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  changeSysJobStatusApi,
  createSysJobApi,
  deleteSysJobApi,
  getSysJobDetailApi,
  getSysJobPageApi,
  runSysJobApi,
  updateSysJobApi,
} from '@/api/system/job'
import BaseCard from '@/components/BaseCard.vue'
import SearchFilterPanel from '@/components/SearchFilterPanel.vue'
import SharedTablePanel from '@/components/SharedTablePanel.vue'
import { SYSTEM_PERMISSION_KEYS } from '@/constants/systemPermissions'
import type { NormalizedPageResult } from '@/types/api/system/common'
import type { SysJobListQuery, SysJobPageResult, SysJobRecord } from '@/types/api/system/job'
import type { SharedActionConfig, SharedFieldSchemaMap } from '@/types/components/data-display'
import JobDetailDialog from './components/JobDetailDialog.vue'
import {
  createDefaultJobForm,
  createDefaultJobQuery,
  createJobFormRules,
  createJobQuerySchema,
  createJobSchema,
  type JobQueryFormState,
} from './config'

type JobDialogMode = 'create' | 'edit'

const jobQueryForm = reactive<JobQueryFormState>(createDefaultJobQuery())
const jobListLoading = ref(false)
const jobSubmitLoading = ref(false)
const jobRunLoading = ref<number | null>(null)
const jobDialogVisible = ref(false)
const jobDialogMode = ref<JobDialogMode>('create')
const jobFormModel = ref<SysJobRecord>(createDefaultJobForm())
/** 任务详情弹窗引用 */
const jobDetailDialogRef = ref<InstanceType<typeof JobDetailDialog>>()
const jobPageState = ref<SysJobPageResult>({
  records: [],
  pageNum: 1,
  pageSize: 10,
  pages: 0,
  total: 0,
})

const jobQuerySchema = computed<SharedFieldSchemaMap<JobQueryFormState>>(() =>
  createJobQuerySchema(),
)

const jobSchema = computed<SharedFieldSchemaMap<SysJobRecord>>(() => createJobSchema(jobDialogMode.value))

const jobDialogTitle = computed(() => (jobDialogMode.value === 'create' ? '新增定时任务' : '编辑定时任务'))
const jobDialogSubmitText = computed(() => (jobDialogMode.value === 'create' ? '创建任务' : '保存修改'))
const jobFormRules = computed(() => createJobFormRules())

const jobTablePagination = computed<NormalizedPageResult<Record<string, unknown>>>(() => ({
  records: jobPageState.value.records,
  pageNum: jobPageState.value.pageNum,
  pageSize: jobPageState.value.pageSize,
  pages: jobPageState.value.pages,
  total: jobPageState.value.total,
}))

/**
 * 行操作：详情 / 编辑 / 启停 / 立即执行 / 删除。
 * 启停按当前状态拆成两个互斥按钮（停用仅启用时显示、启用仅停用时显示），
 * 因为公共操作按钮配置不支持动态文案。
 */
const jobTableActions = computed<SharedActionConfig<Record<string, unknown>>[]>(() => [
  {
    key: 'detail',
    label: '详情',
    permKey: SYSTEM_PERMISSION_KEYS.job.info,
    buttonType: 'primary',
    plain: true,
    onClick: (row) => {
      jobDetailDialogRef.value?.open(row as unknown as SysJobRecord)
    },
  },
  {
    key: 'edit',
    label: '编辑',
    permKey: SYSTEM_PERMISSION_KEYS.job.edit,
    buttonType: 'primary',
    onClick: async (row) => {
      await openEditJobDialog(Number(row.jobId))
    },
  },
  {
    key: 'disable',
    label: '停用',
    permKey: SYSTEM_PERMISSION_KEYS.job.status,
    buttonType: 'warning',
    visible: (row) => Number(row.status) === 1,
    onClick: async (row) => {
      await handleToggleJobStatus(Number(row.jobId), Number(row.status))
    },
  },
  {
    key: 'enable',
    label: '启用',
    permKey: SYSTEM_PERMISSION_KEYS.job.status,
    buttonType: 'success',
    visible: (row) => Number(row.status) === 0,
    onClick: async (row) => {
      await handleToggleJobStatus(Number(row.jobId), Number(row.status))
    },
  },
  {
    key: 'run',
    label: '立即执行',
    permKey: SYSTEM_PERMISSION_KEYS.job.run,
    buttonType: 'success',
    onClick: async (row) => {
      await handleRunJob(Number(row.jobId))
    },
  },
  {
    key: 'delete',
    label: '删除',
    permKey: SYSTEM_PERMISSION_KEYS.job.delete,
    buttonType: 'danger',
    onClick: async (row) => {
      await handleDeleteJob(Number(row.jobId))
    },
  },
])

/**
 * 方法效果：
 * 接收筛选组件回传的新条件对象，并逐项同步到当前页面的查询表单。
 * 参数：
 * - `nextValue`：筛选组件回传的最新查询条件。
 * 返回值：
 * - 无返回值；副作用是更新当前页的查询表单状态。
 */
const handleJobQueryFormUpdate = (nextValue: Record<string, unknown>) => {
  jobQueryForm.jobName = String(nextValue.jobName ?? '')
  jobQueryForm.status =
    nextValue.status === undefined || nextValue.status === null || nextValue.status === ''
      ? undefined
      : Number(nextValue.status)
  jobQueryForm.dateRange = Array.isArray(nextValue.dateRange)
    ? nextValue.dateRange.map((item) => String(item))
    : []
}

const buildJobListParams = (): SysJobListQuery => {
  const [beginTime, endTime] = jobQueryForm.dateRange

  return {
    pageNum: jobPageState.value.pageNum,
    pageSize: jobPageState.value.pageSize,
    jobName: jobQueryForm.jobName.trim() || undefined,
    status: jobQueryForm.status,
    beginTime,
    endTime,
  }
}

/**
 * 方法效果：
 * 拉取定时任务分页列表，并更新当前表格和分页状态。
 * 参数：
 * - 无，直接使用当前页的查询条件和分页参数。
 * 返回值：
 * - 无返回值；副作用是刷新表格数据和分页信息。
 */
const fetchJobPage = async () => {
  jobListLoading.value = true
  try {
    jobPageState.value = await getSysJobPageApi(buildJobListParams())
  } finally {
    jobListLoading.value = false
  }
}

/**
 * 方法效果：
 * 执行任务查询，并从第一页重新拉取列表。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是刷新列表。
 */
const handleSearchJob = async () => {
  jobPageState.value.pageNum = 1
  await fetchJobPage()
}

/**
 * 方法效果：
 * 重置查询条件，并恢复初始分页后重新查询。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是清空筛选条件并刷新列表。
 */
const handleResetJobQuery = async () => {
  Object.assign(jobQueryForm, createDefaultJobQuery())
  jobPageState.value.pageNum = 1
  jobPageState.value.pageSize = 10
  await fetchJobPage()
}

/**
 * 方法效果：
 * 打开新增弹窗，并准备一份干净的表单模型。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是切换弹窗状态并重置表单。
 */
const openCreateJobDialog = () => {
  jobDialogMode.value = 'create'
  jobFormModel.value = createDefaultJobForm()
  jobDialogVisible.value = true
}

/**
 * 方法效果：
 * 打开编辑弹窗，先拉取详情用于完整回显。
 * 参数：
 * - `jobId`：待编辑任务主键。
 * 返回值：
 * - 无返回值；副作用是更新表单模型并展示弹窗。
 */
const openEditJobDialog = async (jobId: number) => {
  const result = await getSysJobDetailApi(jobId)
  jobDialogMode.value = 'edit'
  jobFormModel.value = { ...createDefaultJobForm(), ...result.data }
  jobDialogVisible.value = true
}

/**
 * 方法效果：
 * 接收公共表单回传的新模型，并同步为当前弹窗表单状态。
 * 参数：
 * - `nextValue`：公共表单组件回传的新表单对象。
 * 返回值：
 * - 无返回值；副作用是覆盖当前弹窗表单状态。
 */
const handleJobFormModelUpdate = (nextValue: Record<string, unknown>) => {
  jobFormModel.value = {
    ...jobFormModel.value,
    ...nextValue,
    status: Number(nextValue.status ?? jobFormModel.value.status),
  }
}

/**
 * 方法效果：
 * 处理表格分页切换，并根据新的页码和每页条数重新拉取数据。
 * 参数：
 * - `payload`：分页组件回传的页码和每页条数。
 * 返回值：
 * - 无返回值；副作用是刷新列表。
 */
const handleJobPaginationChange = async (payload: { pageNum: number; pageSize: number }) => {
  jobPageState.value.pageNum = payload.pageNum
  jobPageState.value.pageSize = payload.pageSize
  await fetchJobPage()
}

/**
 * 方法效果：
 * 提交新增或编辑表单，成功后关闭弹窗并刷新列表。
 * 参数：
 * - 无，直接读取当前弹窗表单模型和弹窗模式。
 * 返回值：
 * - 无返回值；副作用是调用保存接口、关闭弹窗并刷新列表。
 */
const handleSubmitJobForm = async () => {
  jobSubmitLoading.value = true
  try {
    const payload: SysJobRecord = {
      ...jobFormModel.value,
      jobName: jobFormModel.value.jobName.trim(),
      beanName: jobFormModel.value.beanName.trim(),
      methodName: jobFormModel.value.methodName.trim(),
      cronExpression: jobFormModel.value.cronExpression.trim(),
      params: (jobFormModel.value.params ?? '').trim() || undefined,
      remark: (jobFormModel.value.remark ?? '').trim(),
    }

    if (jobDialogMode.value === 'create') {
      await createSysJobApi(payload)
      ElMessage.success('定时任务创建成功')
    } else {
      await updateSysJobApi(payload)
      ElMessage.success('定时任务更新成功（调度已同步）')
    }

    jobDialogVisible.value = false
    await fetchJobPage()
  } finally {
    jobSubmitLoading.value = false
  }
}

/**
 * 方法效果：
 * 切换任务启停状态（1启用 0停用），成功后刷新列表。
 * 参数：
 * - `jobId`：任务主键。
 * - `currentStatus`：当前状态（用于提示文案）。
 * 返回值：
 * - 无返回值；副作用是调用启停接口并刷新列表。
 */
const handleToggleJobStatus = async (jobId: number, currentStatus: number) => {
  const nextStatus = currentStatus === 1 ? 0 : 1
  const actionText = nextStatus === 1 ? '启用' : '停用'

  await ElMessageBox.confirm(`确认${actionText}该任务？${nextStatus === 1 ? '将按 cron 恢复调度。' : '调度将立即取消，正在执行的任务不受影响。'}`, `${actionText}任务`, {
    type: 'warning',
  })

  await changeSysJobStatusApi(jobId, nextStatus)
  ElMessage.success(`任务已${actionText}`)
  await fetchJobPage()
}

/**
 * 方法效果：
 * 立即执行一次任务（手动触发），提示后刷新列表。
 * 参数：
 * - `jobId`：任务主键。
 * 返回值：
 * - 无返回值；副作用是触发任务执行并刷新列表。
 */
const handleRunJob = async (jobId: number) => {
  await ElMessageBox.confirm('确认立即执行该任务？执行结果可在「执行日志」中查看。', '立即执行', {
    type: 'info',
    confirmButtonText: '立即执行',
    cancelButtonText: '取消',
  })

  jobRunLoading.value = jobId
  try {
    await runSysJobApi(jobId)
    ElMessage.success('已触发执行，请到执行日志查看结果')
  } finally {
    jobRunLoading.value = null
  }
}

/**
 * 方法效果：
 * 删除定时任务（取消调度 + 删除记录 + 级联清理执行日志）。
 * 参数：
 * - `jobId`：待删除任务主键。
 * 返回值：
 * - 无返回值；副作用是调用删除接口并刷新列表。
 */
const handleDeleteJob = async (jobId: number) => {
  await ElMessageBox.confirm('删除后任务调度将立即取消，其执行日志也会一并清理，确认继续吗？', '删除定时任务', {
    type: 'warning',
  })

  await deleteSysJobApi(jobId)
  ElMessage.success('定时任务删除成功')

  if (jobPageState.value.records.length === 1 && jobPageState.value.pageNum > 1) {
    jobPageState.value.pageNum -= 1
  }

  await fetchJobPage()
}

onMounted(async () => {
  await fetchJobPage()
})
</script>

<template>
  <section class="job-view">
    <BaseCard>
      <SearchFilterPanel
        :schema="jobQuerySchema"
        :model-value="jobQueryForm as unknown as Record<string, unknown>"
        :columns="4"
        label-width="72px"
        create-button-text="新增任务"
        :create-permission-key="SYSTEM_PERMISSION_KEYS.job.create"
        @update:model-value="handleJobQueryFormUpdate"
        @search="handleSearchJob"
        @reset="handleResetJobQuery"
        @create="openCreateJobDialog"
      />
    </BaseCard>

    <BaseCard title="定时任务列表">
      <SharedTablePanel
        :rows="jobPageState.records as Record<string, unknown>[]"
        :schema="jobSchema"
        :actions="jobTableActions"
        :loading="jobListLoading"
        :form-loading="jobSubmitLoading"
        :pagination="jobTablePagination"
        :table-max-height="480"
        :form-visible="jobDialogVisible"
        :form-model-value="jobFormModel as unknown as Record<string, unknown>"
        :form-title="jobDialogTitle"
        :form-submit-text="jobDialogSubmitText"
        :form-columns="2"
        :form-rules="jobFormRules"
        row-key="jobId"
        @pagination-change="handleJobPaginationChange"
        @update:form-visible="jobDialogVisible = $event"
        @update:form-model-value="handleJobFormModelUpdate"
        @form-submit="handleSubmitJobForm"
        @form-cancel="jobDialogVisible = false"
      />
    </BaseCard>

    <!-- 任务详情只读弹窗（列表行数据直接展示，无需再拉详情接口） -->
    <JobDetailDialog ref="jobDetailDialogRef" />
  </section>
</template>

<style scoped>
.job-view {
  display: grid;
  gap: 18px;
}
</style>
