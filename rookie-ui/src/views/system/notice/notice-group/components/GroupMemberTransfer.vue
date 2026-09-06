/**
 * 文件作用：
 * 封装通知分组的成员管理弹窗（主弹窗），
 * 展示当前分组已选成员表格，提供"添加成员"按钮打开搜索子弹窗，
 * 移除按钮先标记待移除（不立即从表格删除，可撤销），保存时统一提交。
 * 关键参数：
 * - `groupId`：当前分组主键。
 * - `members`：当前分组已有成员列表（含成员记录 id、userId 及后端关联补全的展示字段）。
 * 关键状态：
 * - `localMembers`：本地成员集合（打开时拷贝 props.members，增删/标记先在此乐观更新）。
 * - `originalUserIds`：打开时的原始 userId 集合，保存时用于识别新增成员。
 * 关键行为：
 * - 添加成员：打开子弹窗，搜索用户后点"加入"加入本地集合（不立即调接口）；
 * - 移除成员：点表格行"移除"打 pendingRemove 标记（行变灰，可点"撤销"恢复），不从表格删除；
 * - 保存：标记待移除的原始成员还原成成员记录 id 调 removeMembers，未标记的新增成员调 addMembers。
 */
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  ElButton,
  ElDialog,
  ElEmpty,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { addNoticeGroupMembersApi, removeNoticeGroupMembersApi } from '@/api/system/notice'
import { useDialogStack } from '@/composables/useDialogStack'
import type { SysNoticeGroupMemberRecord } from '@/types/api/system/notice'
import type { SysUserFormData } from '@/types/api/system/user'
import GroupMemberAddDialog from './GroupMemberAddDialog.vue'

/**
 * 本地成员记录，在原始成员基础上扩展 pendingRemove 标记，
 * 标记为 true 表示该行待移除（保存时才真正删除，期间可撤销）。
 */
type LocalMember = SysNoticeGroupMemberRecord & { pendingRemove: boolean }

const props = defineProps<{
  groupId: number
  members: SysNoticeGroupMemberRecord[]
}>()

const emit = defineEmits<{
  success: []
}>()

const visible = ref(false)
const saving = ref(false)

// ==================== 弹窗栈（栈式互斥） ====================
// 语义：打开"添加成员"子弹窗时本主弹窗被栈隐藏（本地成员改动保留），
// 子弹窗关闭后自动恢复本主弹窗。
const dialogStack = useDialogStack()
const stackKey = Symbol('group-member-transfer')
/** 栈隐藏守卫：区分「被栈顶掉（hide）」与「用户关闭」，避免隐藏触发的 close 事件误出栈 */
let hidingByStack = false

/**
 * 方法效果：
 * 仅隐藏主弹窗（被子弹窗顶掉时调用）：置 visible=false，不重置本地成员集合。
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
 * 仅恢复显示主弹窗（栈恢复上一个时调用）：置 visible=true，不重新初始化本地成员。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是重新显示弹窗。
 */
const show = () => {
  visible.value = true
}

// 关闭路径统一处理（v-model 变化必然触发，覆盖取消/保存完成/X/Esc/遮罩）：
// - 栈隐藏（hide 触发）：只消费守卫标志，不出栈；
// - 用户关闭：出栈，若有上一个弹窗则自动恢复。
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

// 本地成员集合，打开时拷贝 props.members，增删/标记先在此乐观更新
const localMembers = ref<LocalMember[]>([])
// 打开时记录原始 userId 集合，保存时用于识别新增成员
const originalUserIds = ref<Set<number>>(new Set())

// 添加成员子弹窗引用
const addDialogRef = ref<InstanceType<typeof GroupMemberAddDialog>>()

/**
 * 未标记移除的 userId 集合，供子弹窗 excludeUserIds 控制重复加入态
 * （已标记移除的成员允许重新"加入"恢复，故不计入排除集合）。
 */
const activeUserIds = computed(
  () =>
    new Set(
      localMembers.value
        .filter((member) => !member.pendingRemove)
        .map((member) => Number(member.userId)),
    ),
)

/**
 * 待移除条数，工具栏展示待保存的移除计数。
 */
const pendingRemoveCount = computed(
  () => localMembers.value.filter((member) => member.pendingRemove).length,
)

/**
 * userId 到成员记录 id 的映射，用于保存时把待移除的 userId 还原成后端需要的成员记录主键。
 * 后端 removeMembers 接收的是成员记录 id（SysNoticeGroupMember.id），而非 userId。
 * 仅对原始成员有效（新增的成员尚未落库，无记录 id）。
 */
const memberIdByUserId = computed(() => {
  const map = new Map<number, number>()

  localMembers.value.forEach((member) => {
    if (member.id) {
      map.set(Number(member.userId), Number(member.id))
    }
  })

  return map
})

/**
 * 方法效果：
 * 打开主弹窗，以 props.members 拷贝初始化本地成员集合（pendingRemove 置 false）与原始 userId 集合，
 * 并注册到弹窗栈（自动隐藏当前栈顶弹窗）。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是初始化本地状态并展示弹层。
 */
const open = () => {
  localMembers.value = props.members.map((member) => ({ ...member, pendingRemove: false }))
  originalUserIds.value = new Set(props.members.map((member) => Number(member.userId)))
  dialogStack.open({ key: stackKey, hide, show })
  visible.value = true
}

/**
 * 方法效果：
 * 打开添加成员子弹窗。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是展示子弹窗。
 */
const openAddDialog = () => {
  addDialogRef.value?.open()
}

/**
 * 方法效果：
 * 接收子弹窗抛出的待加入用户，追加到本地成员集合（乐观更新，不立即调接口）。
 * 若该用户已在本地集合中被标记待移除，则撤销标记恢复，避免重复行。
 * 参数：
 * - `user`：子弹窗选中的用户记录，转成本地成员结构。
 * 返回值：
 * - 无返回值；副作用是更新本地成员集合。
 */
const handleAddUser = (user: SysUserFormData) => {
  const uid = Number(user.userId)

  // 已存在（含已标记待移除）则撤销标记恢复，不重复追加
  const existing = localMembers.value.find((member) => Number(member.userId) === uid)
  if (existing) {
    if (existing.pendingRemove) {
      existing.pendingRemove = false
    }
    return
  }

  localMembers.value = [
    ...localMembers.value,
    {
      id: 0,
      groupId: props.groupId,
      userId: uid,
      nickName: user.nickName,
      username: user.username,
      phoneNumber: user.phoneNumber,
      status: user.status,
      pendingRemove: false,
    },
  ]
}

/**
 * 方法效果：
 * 切换指定成员的待移除标记——未标记则标记为待移除，已标记则撤销恢复。
 * 不从表格删除行，保存时才真正提交移除。
 * 参数：
 * - `userId`：待切换移除标记的用户主键。
 * 返回值：
 * - 无返回值；副作用是更新本地成员的 pendingRemove 标记。
 */
const toggleRemoveMark = (userId: number) => {
  const target = localMembers.value.find((member) => Number(member.userId) === Number(userId))

  if (target) {
    target.pendingRemove = !target.pendingRemove
  }
}

/**
 * 方法效果：
 * 提交成员变更：
 * - 新增 = 未标记移除且非原始的 userId，调用 addMembers 传 userIds；
 * - 移除 = 标记 pendingRemove 的原始成员，按 userId 还原成成员记录 id 调 removeMembers。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是调用接口、提示并关闭弹层、抛出 success 事件。
 */
const handleSave = async () => {
  // 待新增：未标记移除且不在原始集合中的
  const toAdd = localMembers.value
    .filter((member) => !member.pendingRemove && !originalUserIds.value.has(Number(member.userId)))
    .map((member) => Number(member.userId))

  // 待移除：标记 pendingRemove 且在原始集合中（有成员记录 id）的
  const toRemoveMemberIds = localMembers.value
    .filter((member) => member.pendingRemove && originalUserIds.value.has(Number(member.userId)))
    .map((member) => memberIdByUserId.value.get(Number(member.userId)))
    .filter((id): id is number => typeof id === 'number')

  // 无任何变更时直接关闭，避免无意义请求
  if (toAdd.length === 0 && toRemoveMemberIds.length === 0) {
    visible.value = false
    return
  }

  saving.value = true

  try {
    if (toAdd.length > 0) {
      await addNoticeGroupMembersApi(props.groupId, toAdd)
    }

    if (toRemoveMemberIds.length > 0) {
      await removeNoticeGroupMembersApi(props.groupId, toRemoveMemberIds)
    }

    ElMessage.success('分组成员更新成功')
    visible.value = false
    emit('success')
  } finally {
    saving.value = false
  }
}

defineExpose({ open })
</script>

<template>
  <ElDialog
    v-model="visible"
    title="管理分组成员"
    width="720px"
    class="group-member-dialog"
  >
    <!--
      不使用 destroy-on-close：本弹窗内含「添加成员」子弹窗（GroupMemberAddDialog）组件，
      若销毁内容，主弹窗被弹窗栈隐藏（visible=false）时子弹窗组件会随之卸载，
      导致子弹窗打开后瞬间消失（且栈条目被 onBeforeUnmount 移除）。
      状态重置由 open() 显式完成，不依赖内容销毁。
    -->
    <!-- 成员表格操作区 -->
    <div class="group-member-toolbar">
      <span class="group-member-toolbar__count">
        共 {{ activeUserIds.size }} 人
        <span v-if="pendingRemoveCount > 0" class="group-member-toolbar__pending">
          （待移除 {{ pendingRemoveCount }}）
        </span>
      </span>
      <ElButton type="primary" :icon="Plus" @click="openAddDialog">添加成员</ElButton>
    </div>

    <!-- 已选成员表格 -->
    <ElTable
      :data="localMembers"
      class="group-member-table"
      max-height="360"
      row-key="userId"
      :row-class-name="({ row }) => (row.pendingRemove ? 'is-pending-remove' : '')"
    >
      <template #empty>
        <ElEmpty description="暂无成员，点击右上角「添加成员」" :image-size="64" />
      </template>
      <ElTableColumn label="昵称" prop="nickName" min-width="120" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.nickName ?? `用户#${row.userId}` }}
        </template>
      </ElTableColumn>
      <ElTableColumn label="用户名" prop="username" min-width="120" show-overflow-tooltip />
      <ElTableColumn label="手机号" prop="phoneNumber" min-width="130" show-overflow-tooltip />
      <ElTableColumn label="状态" width="90">
        <template #default="{ row }">
          <ElTag :type="Number(row.status) === 1 ? 'success' : 'info'" size="small" effect="plain">
            {{ Number(row.status) === 1 ? '启用' : '停用' }}
          </ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn label="操作" width="110" fixed="right">
        <template #default="{ row }">
          <ElButton
            v-if="!row.pendingRemove"
            type="danger"
            size="small"
            plain
            @click="toggleRemoveMark(Number(row.userId))"
          >
            移除
          </ElButton>
          <ElButton
            v-else
            type="warning"
            size="small"
            plain
            @click="toggleRemoveMark(Number(row.userId))"
          >
            撤销
          </ElButton>
        </template>
      </ElTableColumn>
    </ElTable>

    <template #footer>
      <ElButton @click="visible = false">取消</ElButton>
      <ElButton type="primary" :loading="saving" @click="handleSave">保存</ElButton>
    </template>

    <!-- 添加成员子弹窗 -->
    <GroupMemberAddDialog
      ref="addDialogRef"
      :exclude-user-ids="activeUserIds"
      @add="handleAddUser"
    />
  </ElDialog>
</template>

<style scoped>
.group-member-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.group-member-toolbar__count {
  color: var(--rookie-text-secondary);
  font-size: var(--rookie-font-size-sm);
}

.group-member-toolbar__pending {
  color: var(--rookie-warning-strong, var(--rookie-primary-strong));
}

.group-member-table {
  margin-bottom: 12px;
}

/* 待移除行：整行变灰 + 删除线，直观区分标记态 */
.group-member-table :deep(.is-pending-remove) {
  opacity: 0.5;
}

.group-member-table :deep(.is-pending-remove td) {
  text-decoration: line-through;
}
</style>
