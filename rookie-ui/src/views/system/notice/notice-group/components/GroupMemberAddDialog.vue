/**
 * 文件作用：
 * 封装"添加分组成员"子弹窗，供主成员管理弹窗点击"添加成员"时打开。
 * 顶部按字段类型（昵称/用户名/手机号）搜索用户，下部展示用户分页表格，
 * 行内"加入"按钮把用户抛给父层维护，已在父层已选集合中的用户显示"已加入"禁用态。
 * 关键参数：
 * - `excludeUserIds`：父层已加入的 userId 集合，用于控制"已加入"按钮态，避免重复加入。
 * 关键事件：
 * - `add`：抛出待加入的用户记录，父层维护本地成员集合。
 */
<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import {
  ElButton,
  ElDialog,
  ElEmpty,
  ElInput,
  ElOption,
  ElPagination,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { getSysUserPageApi } from '@/api/system/user'
import { useDialogStack } from '@/composables/useDialogStack'
import type { SysUserFormData, SysUserPageResult } from '@/types/api/system/user'

const props = defineProps<{
  excludeUserIds: Set<number>
}>()

const emit = defineEmits<{
  add: [user: SysUserFormData]
}>()

/**
 * 搜索字段类型选项，与后端 SysUserListQuery 的 nickName/username/phoneNumber 三个字段对齐，
 * 复用现有用户分页接口，无需后端改动。
 */
const SEARCH_FIELD_OPTIONS = [
  { label: '昵称', value: 'nickName' as const },
  { label: '用户名', value: 'username' as const },
  { label: '手机号', value: 'phoneNumber' as const },
]

type SearchField = (typeof SEARCH_FIELD_OPTIONS)[number]['value']

const visible = ref(false)
const listLoading = ref(false)
const searchField = ref<SearchField>('nickName')
const keyword = ref('')
const userPageState = ref<SysUserPageResult>({
  records: [],
  pageNum: 1,
  pageSize: 10,
  pages: 0,
  total: 0,
})

// ==================== 弹窗栈（栈式互斥） ====================
// 语义：打开本子弹窗时，当前栈顶弹窗（主弹窗）被自动隐藏；
// 关闭本子弹窗时，自动恢复主弹窗（弹窗栈负责调用其 show）。
const dialogStack = useDialogStack()
const stackKey = Symbol('group-member-add')
/** 栈隐藏守卫：区分「被栈顶掉（hide）」与「用户关闭」，避免隐藏触发的 close 事件误出栈 */
let hidingByStack = false

/**
 * 方法效果：
 * 仅隐藏子弹窗（被更上层弹窗顶掉时调用）：置 visible=false，保留搜索条件与分页数据。
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
 * 仅恢复显示子弹窗（栈恢复上一个时调用）：置 visible=true，不重置搜索条件。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是重新显示弹窗。
 */
const show = () => {
  visible.value = true
}

// 关闭路径统一处理（v-model 变化必然触发，覆盖完成/X/Esc/遮罩）：
// - 栈隐藏（hide 触发）：只消费守卫标志，不出栈；
// - 用户关闭：出栈并自动恢复主弹窗。
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

/**
 * 方法效果：
 * 按当前搜索条件与分页参数拉取用户候选列表。
 * 参数：
 * - 无，直接读取当前搜索条件与分页状态。
 * 返回值：
 * - 无返回值；副作用是更新用户分页表格数据。
 */
const fetchUserPage = async () => {
  listLoading.value = true

  try {
    const trimmed = keyword.value.trim()
    const params: Record<string, unknown> = {
      pageNum: userPageState.value.pageNum,
      pageSize: userPageState.value.pageSize,
    }

    if (trimmed) {
      params[searchField.value] = trimmed
    }

    userPageState.value = await getSysUserPageApi(params as Parameters<typeof getSysUserPageApi>[0])
  } finally {
    listLoading.value = false
  }
}

/**
 * 方法效果：
 * 打开子弹窗：重置搜索条件，注册到弹窗栈（自动隐藏当前栈顶主弹窗）并展示。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是重置搜索条件并展示弹层。
 */
const open = () => {
  searchField.value = 'nickName'
  keyword.value = ''
  userPageState.value = {
    records: [],
    pageNum: 1,
    pageSize: 10,
    pages: 0,
    total: 0,
  }
  dialogStack.open({ key: stackKey, hide, show })
  visible.value = true
}

/**
 * 方法效果：
 * 执行用户搜索，关键词为空时清空表格不拉全量，有关键词时从第一页拉取候选列表。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是按关键词刷新或清空用户表格。
 */
const handleSearch = async () => {
  if (!keyword.value.trim()) {
    resetSearch()
    return
  }
  userPageState.value.pageNum = 1
  await fetchUserPage()
}

/**
 * 方法效果：
 * 重置搜索条件并清空表格结果，不向后端发请求。
 * 重置后表格回到空态，待管理员重新输入关键词搜索，避免无筛选拉全量用户。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是清空搜索条件与表格数据。
 */
const resetSearch = () => {
  keyword.value = ''
  searchField.value = 'nickName'
  userPageState.value = {
    records: [],
    pageNum: 1,
    pageSize: 10,
    pages: 0,
    total: 0,
  }
}

/**
 * 方法效果：
 * 把当前行用户抛给父层加入，乐观更新由父层统一维护。
 * 参数：
 * - `user`：当前行用户记录。
 * 返回值：
 * - 无返回值；副作用是向父层抛出 add 事件。
 */
const handleAdd = (user: SysUserFormData) => {
  emit('add', user)
}

/**
 * 方法效果：
 * 处理用户分页切换，按新的页码和每页条数重新拉取候选列表。
 * 参数：
 * - 无，直接读取分页组件回传的最新页码与每页条数。
 * 返回值：
 * - 无返回值；副作用是刷新用户表格。
 */
const handlePageChange = async () => {
  await fetchUserPage()
}

defineExpose({ open })
</script>

<template>
  <ElDialog
    v-model="visible"
    title="添加成员"
    width="720px"
    destroy-on-close
    append-to-body
    class="group-member-add-dialog"
  >
    <!-- 搜索区：字段类型 + 关键词 + 搜索/重置 -->
    <div class="group-member-add-search">
      <ElSelect v-model="searchField" class="group-member-add-search__field">
        <ElOption
          v-for="item in SEARCH_FIELD_OPTIONS"
          :key="item.value"
          :label="item.label"
          :value="item.value"
        />
      </ElSelect>
      <ElInput
        v-model="keyword"
        :placeholder="`请输入${SEARCH_FIELD_OPTIONS.find((item) => item.value === searchField)?.label ?? '关键词'}`"
        clearable
        class="group-member-add-search__keyword"
        @keyup.enter="handleSearch"
      >
        <template #prefix>
          <Search />
        </template>
      </ElInput>
      <ElButton type="primary" :loading="listLoading" @click="handleSearch">搜索</ElButton>
      <ElButton @click="resetSearch">重置</ElButton>
    </div>

    <!-- 用户候选分页表格 -->
    <ElTable
      :data="userPageState.records"
      v-loading="listLoading"
      class="group-member-add-table"
      max-height="360"
      row-key="userId"
    >
      <template #empty>
        <ElEmpty description="未找到匹配的用户，试试换个关键词或字段" :image-size="64" />
      </template>
      <ElTableColumn label="昵称" prop="nickName" min-width="120" show-overflow-tooltip />
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
            v-if="!excludeUserIds.has(Number(row.userId))"
            type="primary"
            size="small"
            @click="handleAdd(row as SysUserFormData)"
          >
            加入
          </ElButton>
          <ElButton v-else type="info" size="small" plain disabled>已加入</ElButton>
        </template>
      </ElTableColumn>
    </ElTable>

    <ElPagination
      class="group-member-add-pagination"
      :current-page="userPageState.pageNum"
      :page-size="userPageState.pageSize"
      :total="userPageState.total"
      :page-sizes="[10, 20, 50]"
      layout="total, prev, pager, next, sizes"
      background
      @update:current-page="userPageState.pageNum = $event; handlePageChange()"
      @update:page-size="userPageState.pageSize = $event; userPageState.pageNum = 1; handlePageChange()"
    />

    <template #footer>
      <ElButton type="primary" @click="visible = false">完成</ElButton>
    </template>
  </ElDialog>
</template>

<style scoped>
.group-member-add-search {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 14px;
}

.group-member-add-search__field {
  width: 110px;
  flex: none;
}

.group-member-add-search__keyword {
  flex: 1;
  min-width: 220px;
}

.group-member-add-table {
  margin-bottom: 12px;
}

.group-member-add-pagination {
  justify-content: flex-end;
}
</style>
