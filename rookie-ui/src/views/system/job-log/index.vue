/**
 * 文件作用：
 * 承接系统模块下的定时任务执行日志页面，
 * 负责任务执行日志的分页查询与展示（只读，无新增/编辑）。
 * 日志由后端调度器在每次任务执行（自动/手动）时落库。
 */
<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { getSysJobLogPageApi } from '@/api/system/job'
import BaseCard from '@/components/BaseCard.vue'
import SearchFilterPanel from '@/components/SearchFilterPanel.vue'
import SharedTablePanel from '@/components/SharedTablePanel.vue'
import type { NormalizedPageResult } from '@/types/api/system/common'
import type { SysJobLogListQuery, SysJobLogPageResult, SysJobLogRecord } from '@/types/api/system/job'
import type { SharedFieldSchemaMap } from '@/types/components/data-display'
import {
  createDefaultJobLogQuery,
  createJobLogQuerySchema,
  createJobLogSchema,
  type JobLogQueryFormState,
} from './config'

const jobLogQueryForm = reactive<JobLogQueryFormState>(createDefaultJobLogQuery())
const jobLogListLoading = ref(false)
const jobLogPageState = ref<SysJobLogPageResult>({
  records: [],
  pageNum: 1,
  pageSize: 10,
  pages: 0,
  total: 0,
})

const jobLogQuerySchema = computed<SharedFieldSchemaMap<JobLogQueryFormState>>(() =>
  createJobLogQuerySchema(),
)

const jobLogSchema = computed<SharedFieldSchemaMap<SysJobLogRecord>>(() => createJobLogSchema())

const jobLogTablePagination = computed<NormalizedPageResult<Record<string, unknown>>>(() => ({
  records: jobLogPageState.value.records,
  pageNum: jobLogPageState.value.pageNum,
  pageSize: jobLogPageState.value.pageSize,
  pages: jobLogPageState.value.pages,
  total: jobLogPageState.value.total,
}))

/**
 * 方法效果：
 * 接收筛选组件回传的新条件对象，并逐项同步到当前页面的查询表单。
 * 参数：
 * - `nextValue`：筛选组件回传的最新查询条件。
 * 返回值：
 * - 无返回值；副作用是更新当前页的查询表单状态。
 */
const handleJobLogQueryFormUpdate = (nextValue: Record<string, unknown>) => {
  jobLogQueryForm.jobName = String(nextValue.jobName ?? '')
  jobLogQueryForm.triggerType =
    nextValue.triggerType === undefined || nextValue.triggerType === null || nextValue.triggerType === ''
      ? undefined
      : String(nextValue.triggerType)
  jobLogQueryForm.status =
    nextValue.status === undefined || nextValue.status === null || nextValue.status === ''
      ? undefined
      : Number(nextValue.status)
  jobLogQueryForm.dateRange = Array.isArray(nextValue.dateRange)
    ? nextValue.dateRange.map((item) => String(item))
    : []
}

const buildJobLogListParams = (): SysJobLogListQuery => {
  const [beginTime, endTime] = jobLogQueryForm.dateRange

  return {
    pageNum: jobLogPageState.value.pageNum,
    pageSize: jobLogPageState.value.pageSize,
    jobName: jobLogQueryForm.jobName.trim() || undefined,
    triggerType: jobLogQueryForm.triggerType,
    status: jobLogQueryForm.status,
    beginTime,
    endTime,
  }
}

/**
 * 方法效果：
 * 拉取执行日志分页列表，并更新当前表格和分页状态。
 * 参数：
 * - 无，直接使用当前页的查询条件和分页参数。
 * 返回值：
 * - 无返回值；副作用是刷新表格数据和分页信息。
 */
const fetchJobLogPage = async () => {
  jobLogListLoading.value = true
  try {
    jobLogPageState.value = await getSysJobLogPageApi(buildJobLogListParams())
  } finally {
    jobLogListLoading.value = false
  }
}

/**
 * 方法效果：
 * 执行日志查询，并从第一页重新拉取列表。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是刷新列表。
 */
const handleSearchJobLog = async () => {
  jobLogPageState.value.pageNum = 1
  await fetchJobLogPage()
}

/**
 * 方法效果：
 * 重置查询条件，并恢复初始分页后重新查询。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是清空筛选条件并刷新列表。
 */
const handleResetJobLogQuery = async () => {
  Object.assign(jobLogQueryForm, createDefaultJobLogQuery())
  jobLogPageState.value.pageNum = 1
  jobLogPageState.value.pageSize = 10
  await fetchJobLogPage()
}

/**
 * 方法效果：
 * 处理表格分页切换，并根据新的页码和每页条数重新拉取数据。
 * 参数：
 * - `payload`：分页组件回传的页码和每页条数。
 * 返回值：
 * - 无返回值；副作用是刷新列表。
 */
const handleJobLogPaginationChange = async (payload: { pageNum: number; pageSize: number }) => {
  jobLogPageState.value.pageNum = payload.pageNum
  jobLogPageState.value.pageSize = payload.pageSize
  await fetchJobLogPage()
}

onMounted(async () => {
  await fetchJobLogPage()
})
</script>

<template>
  <section class="job-log-view">
    <BaseCard>
      <SearchFilterPanel
        :schema="jobLogQuerySchema"
        :model-value="jobLogQueryForm as unknown as Record<string, unknown>"
        :columns="4"
        label-width="72px"
        @update:model-value="handleJobLogQueryFormUpdate"
        @search="handleSearchJobLog"
        @reset="handleResetJobLogQuery"
      />
    </BaseCard>

    <BaseCard title="执行日志列表">
      <SharedTablePanel
        :rows="jobLogPageState.records as Record<string, unknown>[]"
        :schema="jobLogSchema"
        :loading="jobLogListLoading"
        :pagination="jobLogTablePagination"
        :table-max-height="480"
        row-key="logId"
        @pagination-change="handleJobLogPaginationChange"
      />
    </BaseCard>
  </section>
</template>

<style scoped>
.job-log-view {
  display: grid;
  gap: 18px;
}
</style>
