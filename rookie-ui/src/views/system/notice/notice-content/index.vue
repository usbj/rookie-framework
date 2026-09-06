/**
 * 文件作用：
 * 承接通知内容管理页面，
 * 负责通知列表查询、分页展示、新增、编辑、发布、撤回、删除和只读详情查看。
 * 关键状态：
 * - `groupOptions`：可关联的分组下拉选项，供弹窗内关联分组多选复用。
 * - `queryForm` / `formModel`：当前查询条件与弹窗表单模型。
 * - `pageState` / `dialogVisible` / `detailVisible`：列表分页、编辑弹窗、详情弹窗状态。
 * 关键依赖：
 * - 复用公共表格、公共表单、筛选面板，对齐字典管理页结构。
 */
<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, ElSelect, ElOption, ElTag, ElButton, ElEmpty } from 'element-plus'
import {
  createSysNoticeApi,
  deleteSysNoticesApi,
  getSysNoticeDetailApi,
  getSysNoticePageApi,
  getSysNoticeGroupPageApi,
  publishSysNoticeApi,
  revokeSysNoticeApi,
  updateSysNoticeApi,
} from '@/api/system/notice'
import BaseCard from '@/components/BaseCard.vue'
import SearchFilterPanel from '@/components/SearchFilterPanel.vue'
import SharedTablePanel from '@/components/SharedTablePanel.vue'
import NoticeDetailDialog from '@/components/NoticeDetailDialog.vue'
import GroupMemberAddDialog from '../notice-group/components/GroupMemberAddDialog.vue'
import { SYSTEM_PERMISSION_KEYS } from '@/constants/systemPermissions'
import type { NormalizedPageResult } from '@/types/api/system/common'
import type {
  NoticeTargetUserRecord,
  SysNoticeListQuery,
  SysNoticePageResult,
  SysNoticeRecord,
} from '@/types/api/system/notice'
import type { SysUserFormData } from '@/types/api/system/user'
import type { SharedActionConfig, SharedFieldSchemaMap } from '@/types/components/data-display'
import {
  createDefaultNoticeForm,
  createDefaultNoticeQuery,
  createNoticeQuerySchema,
  createNoticeSchema,
  noticeFormRules,
  type NoticeQueryFormState,
} from './config'

type NoticeDialogMode = 'create' | 'edit'

const queryForm = reactive<NoticeQueryFormState>(createDefaultNoticeQuery())
const groupOptions = ref<Array<{ label: string; value: number }>>([])
const listLoading = ref(false)
const submitLoading = ref(false)
const dialogVisible = ref(false)
const dialogMode = ref<NoticeDialogMode>('create')
const formModel = ref<SysNoticeRecord>(createDefaultNoticeForm())
const detailVisible = ref(false)
const detailRecord = ref<SysNoticeRecord | null>(null)
/**
 * 指定成员本地维护集合：作为 USER 范围下的单一真源，保存已选成员的展示信息。
 * 提交时由它派生 targetUserIds；编辑回显时由后端 targetUsers 初始化。
 * 与 groupIds 走后端回显不同，这里本地维护是为了在弹窗内即时展示昵称/用户名、
 * 并复用 GroupMemberAddDialog 的"搜索+加入"交互而无需另起新接口。
 */
const targetMembers = ref<NoticeTargetUserRecord[]>([])
const targetUserAddDialog = ref<InstanceType<typeof GroupMemberAddDialog> | null>(null)
const pageState = ref<SysNoticePageResult>({
  records: [],
  pageNum: 1,
  pageSize: 10,
  pages: 0,
  total: 0,
})

const querySchema = computed<SharedFieldSchemaMap<NoticeQueryFormState>>(() => createNoticeQuerySchema())
const tableSchema = computed<SharedFieldSchemaMap<SysNoticeRecord>>(() =>
  createNoticeSchema(groupOptions.value),
)
const dialogTitle = computed(() => (dialogMode.value === 'create' ? '新增通知' : '编辑通知'))
const dialogSubmitText = computed(() => (dialogMode.value === 'create' ? '创建通知' : '保存修改'))
const tablePagination = computed<NormalizedPageResult<Record<string, unknown>>>(() => ({
  records: pageState.value.records,
  pageNum: pageState.value.pageNum,
  pageSize: pageState.value.pageSize,
  pages: pageState.value.pages,
  total: pageState.value.total,
}))

const tableActions = computed<SharedActionConfig<Record<string, unknown>>[]>(() => [
  {
    key: 'edit',
    label: '编辑',
    permKey: SYSTEM_PERMISSION_KEYS.notice.edit,
    buttonType: 'primary',
    onClick: async (row) => {
      await openEditDialog(Number(row.noticeId))
    },
  },
  {
    key: 'publish',
    label: '发布',
    permKey: SYSTEM_PERMISSION_KEYS.notice.publish,
    buttonType: 'success',
    visible: (row) => String(row.status) !== 'PUBLISHED',
    onClick: async (row) => {
      await handlePublishNotice(Number(row.noticeId))
    },
  },
  {
    key: 'revoke',
    label: '撤回',
    permKey: SYSTEM_PERMISSION_KEYS.notice.revoke,
    buttonType: 'warning',
    visible: (row) => String(row.status) === 'PUBLISHED',
    onClick: async (row) => {
      await handleRevokeNotice(Number(row.noticeId))
    },
  },
  {
    key: 'detail',
    label: '详情',
    buttonType: 'primary',
    onClick: async (row) => {
      await openDetailDialog(Number(row.noticeId))
    },
  },
  {
    key: 'delete',
    label: '删除',
    permKey: SYSTEM_PERMISSION_KEYS.notice.delete,
    buttonType: 'danger',
    onClick: async (row) => {
      await handleDeleteNotice(Number(row.noticeId))
    },
  },
])

/**
 * 方法效果：
 * 拉取分组下拉选项，供弹窗内关联分组多选复用。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是更新分组选项数据源。
 */
const fetchGroupOptions = async () => {
  const result = await getSysNoticeGroupPageApi({
    pageNum: 1,
    pageSize: 500,
    status: 1,
  })

  groupOptions.value = result.records.map((item) => ({
    label: item.groupName,
    value: Number(item.groupId),
  }))
}

/**
 * 方法效果：
 * 接收筛选组件回传的新条件对象，并逐项同步到当前页面的查询表单。
 * 参数：
 * - `nextValue`：筛选组件回传的最新查询条件。
 * 返回值：
 * - 无返回值；副作用是更新当前页的查询表单状态。
 */
const handleQueryFormUpdate = (nextValue: Record<string, unknown>) => {
  queryForm.title = String(nextValue.title ?? '')
  queryForm.noticeType = nextValue.noticeType ? String(nextValue.noticeType) : undefined
  queryForm.level = nextValue.level ? String(nextValue.level) : undefined
  queryForm.status = nextValue.status ? String(nextValue.status) : undefined
  queryForm.dateRange = Array.isArray(nextValue.dateRange)
    ? nextValue.dateRange.map((item) => String(item))
    : []
}

const buildListParams = (): SysNoticeListQuery => {
  const [beginTime, endTime] = queryForm.dateRange

  return {
    pageNum: pageState.value.pageNum,
    pageSize: pageState.value.pageSize,
    title: queryForm.title.trim() || undefined,
    noticeType: queryForm.noticeType,
    level: queryForm.level,
    status: queryForm.status,
    beginTime,
    endTime,
  }
}

/**
 * 方法效果：
 * 拉取通知分页列表，并更新当前表格与分页状态。
 * 参数：
 * - 无，直接使用当前页的查询条件和分页参数。
 * 返回值：
 * - 无返回值；副作用是刷新表格数据和分页信息。
 */
const fetchPage = async () => {
  listLoading.value = true

  try {
    pageState.value = await getSysNoticePageApi(buildListParams())
  } finally {
    listLoading.value = false
  }
}

/**
 * 方法效果：
 * 执行通知查询，并从第一页重新拉取列表。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是刷新列表。
 */
const handleSearch = async () => {
  pageState.value.pageNum = 1
  await fetchPage()
}

/**
 * 方法效果：
 * 重置通知查询条件，并恢复初始分页后重新查询。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是清空筛选条件并刷新列表。
 */
const resetQueryForm = async () => {
  Object.assign(queryForm, createDefaultNoticeQuery())
  pageState.value.pageNum = 1
  pageState.value.pageSize = 10
  await fetchPage()
}

/**
 * 方法效果：
 * 打开新增通知弹窗，并准备一份干净的表单模型。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是切换弹窗状态并重置表单。
 */
const openCreateDialog = () => {
  dialogMode.value = 'create'
  formModel.value = createDefaultNoticeForm()
  targetMembers.value = []
  dialogVisible.value = true
}

/**
 * 方法效果：
 * 打开编辑通知弹窗，并先拉取详情用于完整回显。
 * 参数：
 * - `noticeId`：待编辑通知主键。
 * 返回值：
 * - 无返回值；副作用是更新表单模型并展示弹窗。
 */
const openEditDialog = async (noticeId: number) => {
  const result = await getSysNoticeDetailApi(noticeId)

  dialogMode.value = 'edit'
  formModel.value = {
    ...createDefaultNoticeForm(),
    ...result.data,
    groupIds: Array.isArray(result.data.groupIds) ? result.data.groupIds.map((item) => Number(item)) : [],
    targetUserIds: Array.isArray(result.data.targetUserIds)
      ? result.data.targetUserIds.map((item) => Number(item))
      : [],
  }
  // 指定成员展示信息由后端 targetUsers 回显；缺失时退回仅按 targetUserIds 重建（仅含 userId）
  const remoteTargets = result.data.targetUsers ?? []
  targetMembers.value = remoteTargets.length
    ? remoteTargets.map((item) => ({
        userId: Number(item.userId),
        username: item.username,
        nickName: item.nickName,
        phoneNumber: item.phoneNumber,
        status: item.status,
      }))
    : (formModel.value.targetUserIds ?? []).map((userId) => ({ userId }))
  dialogVisible.value = true
}

/**
 * 方法效果：
 * 打开只读详情弹窗，拉取通知详情后展示正文等长内容。
 * 参数：
 * - `noticeId`：待查看通知主键。
 * 返回值：
 * - 无返回值；副作用是更新详情数据并展示弹窗。
 */
const openDetailDialog = async (noticeId: number) => {
  const result = await getSysNoticeDetailApi(noticeId)

  detailRecord.value = result.data
  detailVisible.value = true
}

/**
 * 方法效果：
 * 接收公共表单回传的新模型，并同步为当前弹窗表单状态。
 * 参数：
 * - `nextValue`：公共表单组件回传的新表单对象。
 * 返回值：
 * - 无返回值；副作用是覆盖当前弹窗表单状态。
 */
const handleFormModelUpdate = (nextValue: Record<string, unknown>) => {
  formModel.value = {
    ...formModel.value,
    ...nextValue,
    isTop: Number(nextValue.isTop ?? formModel.value.isTop),
    needConfirm: Number(nextValue.needConfirm ?? formModel.value.needConfirm),
    groupIds: Array.isArray(nextValue.groupIds) ? (nextValue.groupIds as number[]) : formModel.value.groupIds,
    // 指定成员主键集合由本地 targetMembers 派生（见 handleSubmitForm），此处仅作字段保护，
    // 不接受公共表单回传覆盖，避免 custom 字段在 update 时被清空。
    targetUserIds: formModel.value.targetUserIds,
  }
}

/**
 * 方法效果：
 * 处理表格分页切换，并根据新的页码和每页条数重新拉取数据。
 * 参数：
 * - `payload`：分页组件回传的页码和每页条数。
 * 返回值：
 * - 无返回值；副作用是刷新通知列表。
 */
const handlePaginationChange = async (payload: { pageNum: number; pageSize: number }) => {
  pageState.value.pageNum = payload.pageNum
  pageState.value.pageSize = payload.pageSize
  await fetchPage()
}

/**
 * 方法效果：
 * 提交新增或编辑通知表单，并在发布范围为分组时携带关联分组。
 * 参数：
 * - 无，直接读取当前弹窗表单模型和弹窗模式。
 * 返回值：
 * - 无返回值；副作用是调用保存接口、关闭弹窗并刷新列表。
 */
const handleSubmitForm = async () => {
  submitLoading.value = true

  try {
    const payload: SysNoticeRecord = {
      ...formModel.value,
      title: formModel.value.title.trim(),
      content: formModel.value.content.trim(),
      remark: formModel.value.remark?.trim() || '',
      // 全员范围下不携带分组关联，避免脏数据
      groupIds: formModel.value.publishScope === 'GROUP' ? formModel.value.groupIds ?? [] : [],
      // 指定成员范围下携带本地已选成员的 userId 集合；其余范围清空，避免脏数据
      targetUserIds:
        formModel.value.publishScope === 'USER' ? targetMembers.value.map((item) => item.userId) : [],
    }

    if (dialogMode.value === 'create') {
      await createSysNoticeApi(payload)
      ElMessage.success('通知创建成功')
    } else {
      await updateSysNoticeApi(payload)
      ElMessage.success('通知更新成功')
    }

    dialogVisible.value = false
    await fetchPage()
  } finally {
    submitLoading.value = false
  }
}

/**
 * 方法效果：
 * 发布指定通知，使其对目标范围可见。
 * 参数：
 * - `noticeId`：待发布通知主键。
 * 返回值：
 * - 无返回值；副作用是调用发布接口并刷新列表。
 */
const handlePublishNotice = async (noticeId: number) => {
  await ElMessageBox.confirm('确认发布该通知吗？发布后对目标范围可见。', '发布通知', {
    type: 'warning',
  })

  await publishSysNoticeApi(noticeId)
  ElMessage.success('通知发布成功')
  await fetchPage()
}

/**
 * 方法效果：
 * 撤回已发布的通知，使其回到已撤回状态。
 * 参数：
 * - `noticeId`：待撤回通知主键。
 * 返回值：
 * - 无返回值；副作用是调用撤回接口并刷新列表。
 */
const handleRevokeNotice = async (noticeId: number) => {
  await ElMessageBox.confirm('确认撤回该通知吗？撤回后不再对目标范围可见。', '撤回通知', {
    type: 'warning',
  })

  await revokeSysNoticeApi(noticeId)
  ElMessage.success('通知撤回成功')
  await fetchPage()
}

/**
 * 方法效果：
 * 删除指定通知，并在删除成功后自动处理当前分页是否需要回退。
 * 参数：
 * - `noticeId`：待删除通知主键。
 * 返回值：
 * - 无返回值；副作用是调用删除接口并刷新列表。
 */
const handleDeleteNotice = async (noticeId: number) => {
  await ElMessageBox.confirm('删除后不可恢复，确认继续吗？', '删除通知', {
    type: 'warning',
  })

  await deleteSysNoticesApi([noticeId])
  ElMessage.success('通知删除成功')

  if (pageState.value.records.length === 1 && pageState.value.pageNum > 1) {
    pageState.value.pageNum -= 1
  }

  await fetchPage()
}

onMounted(async () => {
  await Promise.all([fetchGroupOptions(), fetchPage()])
})

/**
 * 已选指定成员的 userId 集合，传给 GroupMemberAddDialog 控制"已加入"禁用态，避免重复加入。
 */
const targetUserIdSet = computed(() => new Set(targetMembers.value.map((item) => item.userId)))

/**
 * 方法效果：
 * 打开"添加成员"子弹窗，复用通知分组模块的 GroupMemberAddDialog 交互。
 */
const handleOpenTargetUserAddDialog = () => {
  targetUserAddDialog.value?.open()
}

/**
 * 方法效果：
 * 接收 GroupMemberAddDialog 抛回的待加入用户，乐观追加到本地已选成员集合。
 * 参数：
 * - `user`：子弹窗选中的用户记录（含 userId/nickName/username/phoneNumber/status）。
 */
const handleAddTargetUser = (user: SysUserFormData) => {
  const userId = Number(user.userId)
  if (targetUserIdSet.value.has(userId)) {
    return
  }
  targetMembers.value.push({
    userId,
    username: user.username,
    nickName: user.nickName,
    phoneNumber: user.phoneNumber,
    status: user.status,
  })
}

/**
 * 方法效果：
 * 移除指定成员，乐观从本地已选集合删除。
 * 参数：
 * - `userId`：待移除用户主键。
 */
const handleRemoveTargetUser = (userId: number) => {
  targetMembers.value = targetMembers.value.filter((item) => item.userId !== userId)
}
</script>

<template>
  <section class="system-notice-view">
    <BaseCard>
      <SearchFilterPanel
        :schema="querySchema"
        :model-value="queryForm as unknown as Record<string, unknown>"
        :columns="3"
        label-width="72px"
        create-button-text="新增通知"
        :create-permission-key="SYSTEM_PERMISSION_KEYS.notice.create"
        @update:model-value="handleQueryFormUpdate"
        @search="handleSearch"
        @reset="resetQueryForm"
        @create="openCreateDialog"
      />
    </BaseCard>

    <BaseCard title="通知列表">
      <!-- 公共表格区域 -->
      <SharedTablePanel
        :rows="pageState.records as Record<string, unknown>[]"
        :schema="tableSchema"
        :actions="tableActions"
        :loading="listLoading"
        :form-loading="submitLoading"
        :show-selection="false"
        :pagination="tablePagination"
        :table-max-height="520"
        :form-visible="dialogVisible"
        :form-model-value="formModel as unknown as Record<string, unknown>"
        :form-title="dialogTitle"
        :form-submit-text="dialogSubmitText"
        :form-columns="3"
        :form-rules="noticeFormRules"
        row-key="noticeId"
        @pagination-change="handlePaginationChange"
        @update:form-visible="dialogVisible = $event"
        @update:form-model-value="handleFormModelUpdate"
        @form-submit="handleSubmitForm"
        @form-cancel="dialogVisible = false"
      >
        <template #field-groupIds="{ modelValue, updateFieldValue }">
          <ElSelect
            :model-value="Array.isArray(modelValue) ? (modelValue as number[]) : []"
            multiple
            filterable
            clearable
            placeholder="请选择关联分组"
            style="width: 100%"
            @update:model-value="updateFieldValue"
          >
            <ElOption
              v-for="item in groupOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
        </template>
        <template #field-targetUserIds>
          <!-- 指定成员控件：上方操作条 + 下方已选成员 tag 列表，复用分组模块的添加成员子弹窗 -->
          <div class="notice-target-user">
            <div class="notice-target-user__bar">
              <ElButton type="primary" size="small" @click="handleOpenTargetUserAddDialog">
                添加成员
              </ElButton>
              <span class="notice-target-user__hint">
                已选 {{ targetMembers.length }} 人{{ targetMembers.length === 0 ? '，请添加至少一名成员' : '' }}
              </span>
            </div>
            <div v-if="targetMembers.length > 0" class="notice-target-user__tags">
              <ElTag
                v-for="item in targetMembers"
                :key="item.userId"
                closable
                :type="Number(item.status) === 0 ? 'info' : 'success'"
                @close="handleRemoveTargetUser(item.userId)"
              >
                {{ item.nickName || item.username || `用户${item.userId}` }}
                <span v-if="item.username && item.nickName" class="notice-target-user__username">
                  ({{ item.username }})
                </span>
              </ElTag>
            </div>
            <ElEmpty v-else description="尚未添加指定成员" :image-size="48" />
          </div>
        </template>
      </SharedTablePanel>
    </BaseCard>

    <!-- 通知详情只读弹窗（复用博客式排版组件） -->
    <NoticeDetailDialog
      :visible="detailVisible"
      :notice="detailRecord"
      @update:visible="detailVisible = $event"
    />

    <!-- 指定成员添加子弹窗：复用通知分组模块的搜索+加入交互 -->
    <GroupMemberAddDialog
      ref="targetUserAddDialog"
      :exclude-user-ids="targetUserIdSet"
      @add="handleAddTargetUser"
    />
  </section>
</template>

<style scoped>
.system-notice-view {
  display: grid;
  gap: 18px;
}

.notice-target-user {
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 100%;
}

.notice-target-user__bar {
  display: flex;
  align-items: center;
  gap: 10px;
}

.notice-target-user__hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.notice-target-user__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.notice-target-user__username {
  color: var(--el-text-color-secondary);
  margin-left: 2px;
}
</style>
