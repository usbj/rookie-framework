/**
 * 文件作用：
 * 承接系统模块下的系统设置页面，
 * 负责系统设置列表查询、分页展示、新增、编辑、删除与刷新缓存。
 * 刷新缓存走后端接口（清空 Redis 并立即重新预热），与字典刷新走前端本地缓存不同。
 */
<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElButton, ElMessage, ElMessageBox } from 'element-plus'
import {
  createSysConfigApi,
  deleteSysConfigApi,
  getSysConfigDetailApi,
  getSysConfigPageApi,
  refreshSysConfigCacheApi,
  updateSysConfigApi,
} from '@/api/system/system-config'
import BaseCard from '@/components/BaseCard.vue'
import SearchFilterPanel from '@/components/SearchFilterPanel.vue'
import SharedTablePanel from '@/components/SharedTablePanel.vue'
import { useDict } from '@/composables/useDict'
import { SYSTEM_PERMISSION_KEYS } from '@/constants/systemPermissions'
import type { NormalizedPageResult } from '@/types/api/system/common'
import type {
  SysConfigListQuery,
  SysConfigPageResult,
  SysConfigRecord,
} from '@/types/api/system/system-config'
import type { SharedActionConfig, SharedFieldSchemaMap } from '@/types/components/data-display'
import {
  CONFIG_VALUE_TYPE_DICT_KEY,
  createDefaultSysConfigForm,
  createDefaultSysConfigQuery,
  createSysConfigQuerySchema,
  createSysConfigSchema,
  sysConfigFormRules,
  type SysConfigFormModel,
  type SysConfigQueryFormState,
} from './config'

type SysConfigDialogMode = 'create' | 'edit'

const { ensureDictLoaded } = useDict()

const sysConfigQueryForm = reactive<SysConfigQueryFormState>(createDefaultSysConfigQuery())
const sysConfigListLoading = ref(false)
const sysConfigSubmitLoading = ref(false)
const sysConfigRefreshLoading = ref(false)
const sysConfigDialogVisible = ref(false)
const sysConfigDialogMode = ref<SysConfigDialogMode>('create')
const sysConfigFormModel = ref<SysConfigFormModel>(createDefaultSysConfigForm())
// 程序化整体替换表单模型时（如打开编辑弹窗回显）跳过 valueType watch 的重置，
// 避免 watch 把刚回显的 configValue 重置为类型初值（BOOLEAN 会被压成 false 导致开关不回显）
const skipValueTypeWatch = ref(false)
const sysConfigPageState = ref<SysConfigPageResult>({
  records: [],
  pageNum: 1,
  pageSize: 10,
  pages: 0,
  total: 0,
})

const sysConfigQuerySchema = computed<SharedFieldSchemaMap<SysConfigQueryFormState>>(() =>
  createSysConfigQuerySchema(),
)

/**
 * 表格 + 表单 schema：按当前弹窗模式、是否内置项、当前值类型动态生成。
 * - configValue 控件随 valueType 切换（文本/数字/开关/多行文本）。
 * - 编辑内置项时 configKey/valueType/status 禁用。
 */
const sysConfigSchema = computed<SharedFieldSchemaMap<SysConfigFormModel>>(() =>
  createSysConfigSchema(
    sysConfigDialogMode.value,
    Number(sysConfigFormModel.value.isSystem ?? 0),
    String(sysConfigFormModel.value.valueType ?? 'STRING'),
  ),
)

const sysConfigDialogTitle = computed(() =>
  sysConfigDialogMode.value === 'create' ? '新增系统设置' : '编辑系统设置',
)
const sysConfigDialogSubmitText = computed(() =>
  sysConfigDialogMode.value === 'create' ? '创建设置' : '保存修改',
)
const sysConfigTablePagination = computed<NormalizedPageResult<Record<string, unknown>>>(() => ({
  records: sysConfigPageState.value.records,
  pageNum: sysConfigPageState.value.pageNum,
  pageSize: sysConfigPageState.value.pageSize,
  pages: sysConfigPageState.value.pages,
  total: sysConfigPageState.value.total,
}))

const sysConfigTableActions = computed<SharedActionConfig<Record<string, unknown>>[]>(() => [
  {
    key: 'edit',
    label: '编辑',
    permKey: SYSTEM_PERMISSION_KEYS.systemConfig.edit,
    buttonType: 'primary',
    onClick: async (row) => {
      await openEditSysConfigDialog(Number(row.configId))
    },
  },
  {
    key: 'delete',
    label: '删除',
    permKey: SYSTEM_PERMISSION_KEYS.systemConfig.delete,
    buttonType: 'danger',
    // 内置项禁止删除，隐藏删除按钮（后端亦有校验兜底）
    visible: (row) => Number(row.isSystem) !== 1,
    onClick: async (row) => {
      await handleDeleteSysConfig(Number(row.configId))
    },
  },
])

/**
 * 监听表单值类型变化，重置 configValue 为对应类型合适的初值，
 * 避免从 STRING 切到 NUMBER 时残留非数字字符串导致校验失败或缓存解释错乱。
 * 数据流转：valueType 变化 → 按新类型给出 configValue 初值 → 同步回表单模型。
 */
watch(
  () => sysConfigFormModel.value.valueType,
  (nextType, prevType) => {
    if (skipValueTypeWatch.value) {
      skipValueTypeWatch.value = false
      return
    }
    if (nextType === prevType) {
      return
    }
    sysConfigFormModel.value = {
      ...sysConfigFormModel.value,
      configValue: defaultConfigValueForType(String(nextType ?? 'STRING')),
    }
  },
)

/**
 * 方法效果：
 * 按值类型返回 configValue 的合适初值。BOOLEAN 用布尔 false（开关控件），
 * NUMBER 用 undefined（数字控件空态），其余用空字符串。
 * 参数：
 * - `valueType`：值类型 STRING/BOOLEAN/NUMBER/JSON。
 * 返回值：
 * - configValue 初值（类型随控件需要）。
 */
const defaultConfigValueForType = (valueType: string): string | number | boolean => {
  switch (valueType) {
    case 'BOOLEAN':
      return false
    case 'NUMBER':
      return 0
    default:
      return ''
  }
}

/**
 * 方法效果：
 * 接收筛选组件回传的新条件对象，并逐项同步到当前页面的查询表单。
 * 参数：
 * - `nextValue`：筛选组件回传的最新查询条件。
 * 返回值：
 * - 无返回值；副作用是更新当前页的查询表单状态。
 */
const handleSysConfigQueryFormUpdate = (nextValue: Record<string, unknown>) => {
  sysConfigQueryForm.configKey = String(nextValue.configKey ?? '')
  sysConfigQueryForm.configName = String(nextValue.configName ?? '')
  sysConfigQueryForm.status =
    nextValue.status === undefined || nextValue.status === null || nextValue.status === ''
      ? undefined
      : Number(nextValue.status)
  sysConfigQueryForm.dateRange = Array.isArray(nextValue.dateRange)
    ? nextValue.dateRange.map((item) => String(item))
    : []
}

const buildSysConfigListParams = (): SysConfigListQuery => {
  const [beginTime, endTime] = sysConfigQueryForm.dateRange

  return {
    pageNum: sysConfigPageState.value.pageNum,
    pageSize: sysConfigPageState.value.pageSize,
    configKey: sysConfigQueryForm.configKey.trim() || undefined,
    configName: sysConfigQueryForm.configName.trim() || undefined,
    status: sysConfigQueryForm.status,
    beginTime,
    endTime,
  }
}

/**
 * 方法效果：
 * 拉取系统设置分页列表，并更新当前表格和分页状态。
 * 参数：
 * - 无，直接使用当前页的查询条件和分页参数。
 * 返回值：
 * - 无返回值；副作用是刷新表格数据和分页信息。
 */
const fetchSysConfigPage = async () => {
  sysConfigListLoading.value = true

  try {
    sysConfigPageState.value = await getSysConfigPageApi(buildSysConfigListParams())
  } finally {
    sysConfigListLoading.value = false
  }
}

/**
 * 方法效果：
 * 执行系统设置查询，并从第一页重新拉取列表。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是刷新列表。
 */
const handleSearchSysConfig = async () => {
  sysConfigPageState.value.pageNum = 1
  await fetchSysConfigPage()
}

/**
 * 方法效果：
 * 重置查询条件，并恢复初始分页后重新查询。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是清空筛选条件并刷新列表。
 */
const handleResetSysConfigQuery = async () => {
  Object.assign(sysConfigQueryForm, createDefaultSysConfigQuery())
  sysConfigPageState.value.pageNum = 1
  sysConfigPageState.value.pageSize = 10
  await fetchSysConfigPage()
}

/**
 * 方法效果：
 * 打开新增弹窗，并准备一份干净的表单模型（valueType 默认 STRING）。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是切换弹窗状态并重置表单。
 */
const openCreateSysConfigDialog = () => {
  sysConfigDialogMode.value = 'create'
  sysConfigFormModel.value = createDefaultSysConfigForm()
  sysConfigDialogVisible.value = true
}

/**
 * 方法效果：
 * 打开编辑弹窗，先拉取详情用于完整回显，并按值类型归一化 configValue。
 * BOOLEAN 类型 configValue 在表单中用布尔值（开关控件需要），其余保持字符串。
 * 参数：
 * - `configId`：待编辑设置项主键。
 * 返回值：
 * - 无返回值；副作用是更新表单模型并展示弹窗。
 */
const openEditSysConfigDialog = async (configId: number) => {
  const result = await getSysConfigDetailApi(configId)
  const record = result.data

  sysConfigDialogMode.value = 'edit'
  // BOOLEAN 类型把字符串 "true"/"false" 归一化为布尔，配合开关控件；其余类型保持原字符串
  const normalizedValue: string | boolean =
    String(record.valueType) === 'BOOLEAN'
      ? String(record.configValue) === 'true' || String(record.configValue) === '1'
      : String(record.configValue ?? '')

  // 整体替换表单模型时 valueType 会从默认 STRING 变成 record.valueType，
  // 置标志让上方 watch 跳过这次程序化赋值，避免回显的 configValue 被重置为类型初值
  skipValueTypeWatch.value = true
  sysConfigFormModel.value = {
    ...createDefaultSysConfigForm(),
    ...record,
    configValue: normalizedValue,
  }
  sysConfigDialogVisible.value = true
}

/**
 * 方法效果：
 * 接收公共表单回传的新模型，并同步为当前弹窗表单状态。
 * 参数：
 * - `nextValue`：公共表单组件回传的新表单对象。
 * 返回值：
 * - 无返回值；副作用是覆盖当前弹窗表单状态。
 */
const handleSysConfigFormModelUpdate = (nextValue: Record<string, unknown>) => {
  sysConfigFormModel.value = {
    ...sysConfigFormModel.value,
    ...nextValue,
    status: Number(nextValue.status ?? sysConfigFormModel.value.status),
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
const handleSysConfigPaginationChange = async (payload: { pageNum: number; pageSize: number }) => {
  sysConfigPageState.value.pageNum = payload.pageNum
  sysConfigPageState.value.pageSize = payload.pageSize
  await fetchSysConfigPage()
}

/**
 * 方法效果：
 * 提交新增或编辑表单。提交前对 configValue 做类型归一化：
 * BOOLEAN 把布尔转回 "true"/"false" 字符串（与后端存储格式一致），
 * NUMBER 保证为字符串形式，其余 trim。
 * 参数：
 * - 无，直接读取当前弹窗表单模型和弹窗模式。
 * 返回值：
 * - 无返回值；副作用是调用保存接口、关闭弹窗并刷新列表。
 */
const handleSubmitSysConfigForm = async () => {
  sysConfigSubmitLoading.value = true

  try {
    const valueType = String(sysConfigFormModel.value.valueType)
    // BOOLEAN：布尔 → "true"/"false" 字符串；其余：统一转字符串并 trim（JSON 不 trim 以保留格式）
    const normalizedValue =
      valueType === 'BOOLEAN'
        ? sysConfigFormModel.value.configValue
          ? 'true'
          : 'false'
        : valueType === 'JSON'
          ? String(sysConfigFormModel.value.configValue ?? '')
          : String(sysConfigFormModel.value.configValue ?? '').trim()

    const payload: SysConfigRecord = {
      ...sysConfigFormModel.value,
      configKey: sysConfigFormModel.value.configKey.trim(),
      configName: sysConfigFormModel.value.configName.trim(),
      remark: (sysConfigFormModel.value.remark ?? '').trim(),
      configValue: normalizedValue,
    }

    if (sysConfigDialogMode.value === 'create') {
      await createSysConfigApi(payload)
      ElMessage.success('系统设置创建成功')
    } else {
      await updateSysConfigApi(payload)
      ElMessage.success('系统设置更新成功')
    }

    sysConfigDialogVisible.value = false
    await fetchSysConfigPage()
  } finally {
    sysConfigSubmitLoading.value = false
  }
}

/**
 * 方法效果：
 * 删除指定系统设置，并在删除成功后自动处理当前分页是否需要回退。
 * 参数：
 * - `configId`：待删除设置项主键。
 * 返回值：
 * - 无返回值；副作用是调用删除接口并刷新列表。
 */
const handleDeleteSysConfig = async (configId: number) => {
  await ElMessageBox.confirm('删除后不可恢复，确认继续吗？', '删除系统设置', {
    type: 'warning',
  })

  await deleteSysConfigApi(configId)
  ElMessage.success('系统设置删除成功')

  if (sysConfigPageState.value.records.length === 1 && sysConfigPageState.value.pageNum > 1) {
    sysConfigPageState.value.pageNum -= 1
  }

  await fetchSysConfigPage()
}

/**
 * 方法效果：
 * 调后端刷新系统设置缓存：后端清空 Redis 中全部设置项并立即重新预热。
 * 与字典刷新（前端本地缓存）不同，系统设置缓存在后端 Redis，必须走后端接口。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是后端重建缓存并提示结果。
 */
const handleRefreshSysConfigCache = async () => {
  sysConfigRefreshLoading.value = true

  try {
    await refreshSysConfigCacheApi()
    ElMessage.success('系统设置缓存刷新成功')
    await fetchSysConfigPage()
  } finally {
    sysConfigRefreshLoading.value = false
  }
}

onMounted(async () => {
  // 值类型字典预加载，保证表格 valueType 列与表单下拉能正确渲染
  await ensureDictLoaded(CONFIG_VALUE_TYPE_DICT_KEY, true)
  await fetchSysConfigPage()
})
</script>

<template>
  <section class="system-config-view">
    <BaseCard>
      <SearchFilterPanel
        :schema="sysConfigQuerySchema"
        :model-value="sysConfigQueryForm as unknown as Record<string, unknown>"
        :columns="4"
        label-width="72px"
        create-button-text="新增设置"
        :create-permission-key="SYSTEM_PERMISSION_KEYS.systemConfig.create"
        @update:model-value="handleSysConfigQueryFormUpdate"
        @search="handleSearchSysConfig"
        @reset="handleResetSysConfigQuery"
        @create="openCreateSysConfigDialog"
      />
    </BaseCard>

    <BaseCard title="系统设置列表">
      <div class="system-config-view__toolbar">
        <ElButton :loading="sysConfigRefreshLoading" @click="handleRefreshSysConfigCache">
          刷新缓存
        </ElButton>
      </div>

      <SharedTablePanel
        :rows="sysConfigPageState.records as Record<string, unknown>[]"
        :schema="sysConfigSchema"
        :actions="sysConfigTableActions"
        :loading="sysConfigListLoading"
        :form-loading="sysConfigSubmitLoading"
        :show-selection="true"
        :pagination="sysConfigTablePagination"
        :table-max-height="480"
        :form-visible="sysConfigDialogVisible"
        :form-model-value="sysConfigFormModel as unknown as Record<string, unknown>"
        :form-title="sysConfigDialogTitle"
        :form-submit-text="sysConfigDialogSubmitText"
        :form-columns="2"
        :form-rules="sysConfigFormRules"
        row-key="configId"
        @pagination-change="handleSysConfigPaginationChange"
        @update:form-visible="sysConfigDialogVisible = $event"
        @update:form-model-value="handleSysConfigFormModelUpdate"
        @form-submit="handleSubmitSysConfigForm"
        @form-cancel="sysConfigDialogVisible = false"
      />
    </BaseCard>
  </section>
</template>

<style scoped>
.system-config-view {
  display: grid;
  gap: 18px;
}

.system-config-view__toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 14px;
}
</style>
