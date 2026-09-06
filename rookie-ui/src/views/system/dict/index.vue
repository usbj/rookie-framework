/**
 * 文件作用：
 * 承接系统模块下的字典管理页面，
 * 负责字典列表查询、分页展示、新增、编辑、删除和跳转字典数据管理。
 */
<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElButton, ElMessage, ElMessageBox } from 'element-plus'
import {
  clearDictDataCacheApi,
  createSysDictApi,
  deleteSysDictApi,
  getSysDictDetailApi,
  getSysDictPageApi,
  updateSysDictApi,
} from '@/api/system/dict'
import BaseCard from '@/components/BaseCard.vue'
import SearchFilterPanel from '@/components/SearchFilterPanel.vue'
import SharedTablePanel from '@/components/SharedTablePanel.vue'
import { useDictStore } from '@/stores/dict'
import { SYSTEM_PERMISSION_KEYS } from '@/constants/systemPermissions'
import type { NormalizedPageResult } from '@/types/api/system/common'
import type { SysDictListQuery, SysDictPageResult, SysDictRecord } from '@/types/api/system/dict'
import type { SharedActionConfig, SharedFieldSchemaMap } from '@/types/components/data-display'
import {
  createDefaultDictForm,
  createDefaultDictQuery,
  createDictQuerySchema,
  createDictSchema,
  dictFormRules,
  type DictQueryFormState,
} from './config'

type DictDialogMode = 'create' | 'edit'

const router = useRouter()
const dictStore = useDictStore()
const dictQueryForm = reactive<DictQueryFormState>(createDefaultDictQuery())
const dictListLoading = ref(false)
const dictSubmitLoading = ref(false)
const dictRefreshLoading = ref(false)
const dictDialogVisible = ref(false)
const dictDialogMode = ref<DictDialogMode>('create')
const dictFormModel = ref<SysDictRecord>(createDefaultDictForm())
const dictPageState = ref<SysDictPageResult>({
  records: [],
  pageNum: 1,
  pageSize: 10,
  pages: 0,
  total: 0,
})

const dictQuerySchema = computed<SharedFieldSchemaMap<DictQueryFormState>>(() => createDictQuerySchema())
const dictSchema = computed<SharedFieldSchemaMap<SysDictRecord>>(() => createDictSchema())
const dictDialogTitle = computed(() => (dictDialogMode.value === 'create' ? '新增字典' : '编辑字典'))
const dictDialogSubmitText = computed(() => (dictDialogMode.value === 'create' ? '创建字典' : '保存修改'))
const dictTablePagination = computed<NormalizedPageResult<Record<string, unknown>>>(() => ({
  records: dictPageState.value.records,
  pageNum: dictPageState.value.pageNum,
  pageSize: dictPageState.value.pageSize,
  pages: dictPageState.value.pages,
  total: dictPageState.value.total,
}))

const dictTableActions = computed<SharedActionConfig<Record<string, unknown>>[]>(() => [
  {
    key: 'manage-data',
    label: '数据项',
    buttonType: 'primary',
    onClick: async (row) => {
      await openDictDataPage(Number(row.dictId), String(row.dictKey ?? ''))
    },
  },
  {
    key: 'edit',
    label: '编辑',
    permKey: SYSTEM_PERMISSION_KEYS.dict.edit,
    buttonType: 'primary',
    onClick: async (row) => {
      await openEditDictDialog(Number(row.dictId))
    },
  },
  {
    key: 'delete',
    label: '删除',
    permKey: SYSTEM_PERMISSION_KEYS.dict.delete,
    buttonType: 'danger',
    onClick: async (row) => {
      await handleDeleteDict(Number(row.dictId))
    },
  },
])

/**
 * 方法效果：
 * 跳转到字典数据管理页面，并把当前字典主键和字典键值带过去用于快速筛选。
 * 参数：
 * - `dictId`：当前字典主键。
 * - `dictKey`：当前字典键值。
 * 返回值：
 * - Promise<void>，用于等待路由跳转完成。
 */
const openDictDataPage = async (dictId: number, dictKey: string) => {
  await router.push({
    path: '/system/dict-data',
    query: {
      dictId: String(dictId),
      dictKey,
    },
  })
}

/**
 * 方法效果：
 * 接收筛选组件回传的新条件对象，并逐项同步到当前页面的查询表单。
 * 参数：
 * - `nextValue`：筛选组件回传的最新查询条件。
 * 返回值：
 * - 无返回值；副作用是更新当前页的查询表单状态。
 */
const handleDictQueryFormUpdate = (nextValue: Record<string, unknown>) => {
  dictQueryForm.dictName = String(nextValue.dictName ?? '')
  dictQueryForm.dictKey = String(nextValue.dictKey ?? '')
  dictQueryForm.status =
    nextValue.status === undefined || nextValue.status === null || nextValue.status === ''
      ? undefined
      : Number(nextValue.status)
  dictQueryForm.dateRange = Array.isArray(nextValue.dateRange)
    ? nextValue.dateRange.map((item) => String(item))
    : []
}

const buildDictListParams = (): SysDictListQuery => {
  const [beginTime, endTime] = dictQueryForm.dateRange

  return {
    pageNum: dictPageState.value.pageNum,
    pageSize: dictPageState.value.pageSize,
    dictName: dictQueryForm.dictName.trim() || undefined,
    dictKey: dictQueryForm.dictKey.trim() || undefined,
    status: dictQueryForm.status,
    beginTime,
    endTime,
  }
}

/**
 * 方法效果：
 * 拉取字典分页列表，并更新当前表格和分页状态。
 * 参数：
 * - 无，直接使用当前页的查询条件和分页参数。
 * 返回值：
 * - 无返回值；副作用是刷新表格数据和分页信息。
 */
const fetchDictPage = async () => {
  dictListLoading.value = true

  try {
    dictPageState.value = await getSysDictPageApi(buildDictListParams())
  } finally {
    dictListLoading.value = false
  }
}

/**
 * 方法效果：
 * 执行字典查询，并从第一页重新拉取列表。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是刷新列表。
 */
const handleSearchDict = async () => {
  dictPageState.value.pageNum = 1
  await fetchDictPage()
}

/**
 * 方法效果：
 * 重置字典查询条件，并恢复初始分页后重新查询。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是清空筛选条件并刷新列表。
 */
const handleResetDictQuery = async () => {
  Object.assign(dictQueryForm, createDefaultDictQuery())
  dictPageState.value.pageNum = 1
  dictPageState.value.pageSize = 10
  await fetchDictPage()
}

/**
 * 方法效果：
 * 打开新增字典弹窗，并准备一份干净的表单模型。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是切换弹窗状态并重置表单。
 */
const openCreateDictDialog = () => {
  dictDialogMode.value = 'create'
  dictFormModel.value = createDefaultDictForm()
  dictDialogVisible.value = true
}

/**
 * 方法效果：
 * 打开编辑字典弹窗，并先拉取字典详情用于完整回显。
 * 参数：
 * - `dictId`：待编辑字典主键。
 * 返回值：
 * - 无返回值；副作用是更新表单模型并展示弹窗。
 */
const openEditDictDialog = async (dictId: number) => {
  const result = await getSysDictDetailApi(dictId)

  dictDialogMode.value = 'edit'
  dictFormModel.value = {
    ...createDefaultDictForm(),
    ...result.data,
  }
  dictDialogVisible.value = true
}

/**
 * 方法效果：
 * 接收公共表单回传的新模型，并同步为当前弹窗表单状态。
 * 参数：
 * - `nextValue`：公共表单组件回传的新表单对象。
 * 返回值：
 * - 无返回值；副作用是覆盖当前弹窗表单状态。
 */
const handleDictFormModelUpdate = (nextValue: Record<string, unknown>) => {
  dictFormModel.value = {
    ...dictFormModel.value,
    ...nextValue,
    status: Number(nextValue.status ?? dictFormModel.value.status),
  }
}

/**
 * 方法效果：
 * 处理表格分页切换，并根据新的页码和每页条数重新拉取数据。
 * 参数：
 * - `payload`：分页组件回传的页码和每页条数。
 * 返回值：
 * - 无返回值；副作用是刷新字典列表。
 */
const handleDictPaginationChange = async (payload: { pageNum: number; pageSize: number }) => {
  dictPageState.value.pageNum = payload.pageNum
  dictPageState.value.pageSize = payload.pageSize
  await fetchDictPage()
}

/**
 * 方法效果：
 * 提交新增或编辑字典表单。
 * 参数：
 * - 无，直接读取当前弹窗表单模型和弹窗模式。
 * 返回值：
 * - 无返回值；副作用是调用保存接口、关闭弹窗并刷新列表。
 */
const handleSubmitDictForm = async () => {
  dictSubmitLoading.value = true

  try {
    const payload: SysDictRecord = {
      ...dictFormModel.value,
      dictName: dictFormModel.value.dictName.trim(),
      dictKey: dictFormModel.value.dictKey.trim(),
      remake: dictFormModel.value.remake.trim(),
    }

    if (dictDialogMode.value === 'create') {
      await createSysDictApi(payload)
      ElMessage.success('字典创建成功')
    } else {
      await updateSysDictApi(payload)
      ElMessage.success('字典更新成功')
    }

    dictDialogVisible.value = false
    await fetchDictPage()
  } finally {
    dictSubmitLoading.value = false
  }
}

/**
 * 方法效果：
 * 删除指定字典，并在删除成功后自动处理当前分页是否需要回退。
 * 参数：
 * - `dictId`：待删除字典主键。
 * 返回值：
 * - 无返回值；副作用是调用删除接口并刷新列表。
 */
const handleDeleteDict = async (dictId: number) => {
  await ElMessageBox.confirm('删除后不可恢复，确认继续吗？', '删除字典', {
    type: 'warning',
  })

  await deleteSysDictApi(dictId)
  ElMessage.success('字典删除成功')

  if (dictPageState.value.records.length === 1 && dictPageState.value.pageNum > 1) {
    dictPageState.value.pageNum -= 1
  }

  await fetchDictPage()
}

/**
 * 方法效果：
 * 强制刷新前端字典缓存，并重新拉取所有启用字典的数据项。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是清空并重建本地字典缓存。
 */
const handleRefreshDictCache = async () => {
  dictRefreshLoading.value = true

  try {
    // 后端 getSysDictDataByDictKey 走 Redis 缓存命中即返回，先调 clearDictDataCacheApi 清后端缓存，
    // 再清前端并 initializeDictionaries(true) 重拉，下一次按 dictKey 取值才会强制重新查库。
    await clearDictDataCacheApi()
    dictStore.clearDictCache()
    await dictStore.initializeDictionaries(true)
    ElMessage.success('字典缓存刷新成功')
  } finally {
    dictRefreshLoading.value = false
  }
}

onMounted(async () => {
  await fetchDictPage()
})
</script>

<template>
  <section class="system-dict-view">
    <BaseCard>
      <SearchFilterPanel
        :schema="dictQuerySchema"
        :model-value="dictQueryForm as unknown as Record<string, unknown>"
        :columns="4"
        label-width="72px"
        create-button-text="新增字典"
        :create-permission-key="SYSTEM_PERMISSION_KEYS.dict.create"
        @update:model-value="handleDictQueryFormUpdate"
        @search="handleSearchDict"
        @reset="handleResetDictQuery"
        @create="openCreateDictDialog"
      />
    </BaseCard>

    <BaseCard title="字典列表">
      <div class="system-dict-view__toolbar">
        <ElButton :loading="dictRefreshLoading" @click="handleRefreshDictCache">
          刷新字典缓存
        </ElButton>
      </div>

      <SharedTablePanel
        :rows="dictPageState.records as Record<string, unknown>[]"
        :schema="dictSchema"
        :actions="dictTableActions"
        :loading="dictListLoading"
        :form-loading="dictSubmitLoading"
        :show-selection="true"
        :pagination="dictTablePagination"
        :table-max-height="480"
        :form-visible="dictDialogVisible"
        :form-model-value="dictFormModel as unknown as Record<string, unknown>"
        :form-title="dictDialogTitle"
        :form-submit-text="dictDialogSubmitText"
        :form-columns="2"
        :form-rules="dictFormRules"
        row-key="dictId"
        @pagination-change="handleDictPaginationChange"
        @update:form-visible="dictDialogVisible = $event"
        @update:form-model-value="handleDictFormModelUpdate"
        @form-submit="handleSubmitDictForm"
        @form-cancel="dictDialogVisible = false"
      />
    </BaseCard>
  </section>
</template>

<style scoped>
.system-dict-view {
  display: grid;
  gap: 18px;
}

.system-dict-view__toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 14px;
}
</style>
