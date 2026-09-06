/**
 * 文件作用：
 * 承接系统模块下的菜单管理页面，
 * 负责菜单树查询、树表格展示、新增、编辑、状态切换与删除操作。
 */
<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  ElButton,
  ElDialog,
  ElDropdown,
  ElDropdownItem,
  ElDropdownMenu,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
  ElTooltip,
} from 'element-plus'
import type { FormInstance } from 'element-plus'
import {
  changeSysMenuStatusApi,
  createSysMenuApi,
  deleteSysMenusApi,
  getSysMenuDetailApi,
  getSysMenuListApi,
  updateSysMenuApi,
} from '@/api/system/menu'
import BaseCard from '@/components/BaseCard.vue'
import DictTag from '@/components/DictTag.vue'
import SearchFilterPanel from '@/components/SearchFilterPanel.vue'
import { useDict } from '@/composables/useDict'
import { usePermission } from '@/composables/usePermission'
import { SYSTEM_PERMISSION_KEYS } from '@/constants/systemPermissions'
import type { SysMenuListQuery, SysMenuRecord } from '@/types/api/system/menu'
import type { SharedFieldSchemaMap } from '@/types/components/data-display'
import {
  buildMenuFormRules,
  createDefaultMenuForm,
  createDefaultMenuQuery,
  createMenuQuerySchema,
  getMenuStatusTagType,
  menuBacklinksOptions,
  menuStatusOptions,
  type MenuQueryFormState,
} from './config'
import MenuIconPicker from './MenuIconPicker.vue'
import { resolveCanonicalMenuIconCode } from '@/utils/menu-icons'

type MenuDialogMode = 'create' | 'edit'
type MenuRowActionKey = 'edit' | 'create-child' | 'enable' | 'disable' | 'delete'

interface MenuRowActionItem {
  key: MenuRowActionKey
  label: string
  buttonType?: 'primary' | 'danger'
  warning?: boolean
  onClick: () => Promise<void> | void
}

const queryForm = reactive<MenuQueryFormState>(createDefaultMenuQuery())
const listLoading = ref(false)
const submitLoading = ref(false)
const dialogVisible = ref(false)
const dialogMode = ref<MenuDialogMode>('create')
const menuTree = ref<SysMenuRecord[]>([])
const menuFormModel = ref<SysMenuRecord>(createDefaultMenuForm())
const formRef = ref<FormInstance>()
const expandAll = ref(false)
const tableRef = ref<InstanceType<typeof ElTable>>()
const { hasPermission } = usePermission()
const { getDictOptions, ensureDictLoaded } = useDict()

const menuTypeOptions = getDictOptions('sys_menu_type', 'number')

const querySchema = computed<SharedFieldSchemaMap<MenuQueryFormState>>(() => createMenuQuerySchema())
const dialogTitle = computed(() => (dialogMode.value === 'create' ? '新增菜单' : '编辑菜单'))
const dialogSubmitText = computed(() => (dialogMode.value === 'create' ? '创建菜单' : '保存修改'))
const menuFormRules = computed(() => buildMenuFormRules(Number(menuFormModel.value.menuType)))
const isDirectoryType = computed(() => Number(menuFormModel.value.menuType) === 1)
const isMenuType = computed(() => Number(menuFormModel.value.menuType) === 2)
const isButtonType = computed(() => Number(menuFormModel.value.menuType) === 3)

const parentMenuOptions = computed(() => {
  const flattenMenus = (menus: SysMenuRecord[], level = 0): Array<{ label: string; value: number }> =>
    menus.flatMap((menu) => {
      if (menu.menuId === menuFormModel.value.menuId) {
        return []
      }

      return [
        {
          label: `${'　'.repeat(level)}${menu.menuName}`,
          value: Number(menu.menuId),
        },
        ...flattenMenus(menu.sonMenus ?? [], level + 1),
      ]
    })

  // 顶级目录用 -1 与后端 buildMenuTree 约定对齐（0 会导致存盘后菜单从列表消失）。
  return [{ label: '顶级目录', value: -1 }, ...flattenMenus(menuTree.value)]
})

const canCreateMenu = computed(() => hasPermission(SYSTEM_PERMISSION_KEYS.menu.create))
const canEditMenu = computed(() => hasPermission(SYSTEM_PERMISSION_KEYS.menu.edit))
const canChangeStatus = computed(() => hasPermission(SYSTEM_PERMISSION_KEYS.menu.status))
const canDeleteMenu = computed(() => hasPermission(SYSTEM_PERMISSION_KEYS.menu.delete))

/**
 * 方法效果：
 * 接收筛选组件回传的新条件对象，并逐项同步到当前页面的查询表单。
 * 参数：
 * - `nextValue`：筛选组件回传的最新查询条件。
 * 返回值：
 * - 无返回值；副作用是更新当前页的查询表单状态。
 */
const handleQueryFormUpdate = (nextValue: Record<string, unknown>) => {
  queryForm.menuName = String(nextValue.menuName ?? '')
  queryForm.status =
    nextValue.status === undefined || nextValue.status === null || nextValue.status === ''
      ? undefined
      : Number(nextValue.status)
}

const buildMenuListParams = (): SysMenuListQuery => ({
  menuName: queryForm.menuName.trim() || undefined,
  status: queryForm.status,
})

/**
 * 方法效果：
 * 拉取菜单树列表，并更新当前树表格数据。
 * 拉取成功后自动展开全部节点（树表格默认折叠会让二级菜单看起来"消失"）。
 * 参数：
 * - 无，直接使用当前页的查询条件。
 * 返回值：
 * - 无返回值；副作用是刷新菜单树数据。
 */
const fetchMenuList = async () => {
  listLoading.value = true

  try {
    const result = await getSysMenuListApi(buildMenuListParams())
    menuTree.value = result.data
    // 数据刷新后展开全部节点：ElTable 树默认折叠，若保持收起会让人误以为数据丢失
    expandAllRows()
  } finally {
    listLoading.value = false
  }
}

/**
 * 方法效果：
 * 执行菜单查询。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是刷新菜单树列表。
 */
const handleSearch = async () => {
  await fetchMenuList()
}

/**
 * 方法效果：
 * 重置菜单查询条件后重新拉取列表。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是清空筛选条件并刷新列表。
 */
const resetQueryForm = async () => {
  Object.assign(queryForm, createDefaultMenuQuery())
  await fetchMenuList()
}

/**
 * 方法效果：
 * 根据菜单类型同步一些不需要继续保留的字段默认值。
 * 参数：
 * - `menuType`：当前菜单类型。
 * 返回值：
 * - 无返回值；副作用是调整表单模型中的字段值。
 */
const applyMenuTypeDefaults = (menuType: number) => {
  if (menuType === 1) {
    menuFormModel.value.backlinks = 0
    menuFormModel.value.path = ''
  }

  if (menuType === 2) {
    menuFormModel.value.icon = menuFormModel.value.icon || 'Menu'
  }

  if (menuType === 3) {
    menuFormModel.value.route = ''
    menuFormModel.value.path = ''
    menuFormModel.value.icon = ''
    menuFormModel.value.backlinks = 0
  }
}

/**
 * 方法效果：
 * 打开新增菜单弹窗，并准备一份干净的表单模型。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是切换弹窗状态并重置表单。
 */
const openCreateDialog = () => {
  dialogMode.value = 'create'
  menuFormModel.value = createDefaultMenuForm()
  applyMenuTypeDefaults(menuFormModel.value.menuType)
  dialogVisible.value = true
}

/**
 * 方法效果：
 * 以当前行作为父节点打开新增子菜单弹窗，并按父级类型推导默认子类型。
 * 参数：
 * - `parentMenu`：当前被操作的父级菜单。
 * 返回值：
 * - 无返回值；副作用是更新表单模型并展示弹窗。
 */
const openCreateChildDialog = (parentMenu: SysMenuRecord) => {
  dialogMode.value = 'create'
  menuFormModel.value = {
    ...createDefaultMenuForm(),
    parentId: Number(parentMenu.menuId),
    menuType: parentMenu.menuType === 2 ? 3 : 2,
  }
  applyMenuTypeDefaults(menuFormModel.value.menuType)
  dialogVisible.value = true
}

/**
 * 方法效果：
 * 打开编辑菜单弹窗，并先拉取菜单详情用于完整回显。
 * 参数：
 * - `menuId`：待编辑菜单主键。
 * 返回值：
 * - 无返回值；副作用是更新表单模型并展示弹窗。
 */
const openEditDialog = async (menuId: number) => {
  const result = await getSysMenuDetailApi(menuId)

  dialogMode.value = 'edit'
  menuFormModel.value = {
    ...createDefaultMenuForm(),
    ...result.data,
    icon: resolveCanonicalMenuIconCode(result.data.icon),
    sonMenus: result.data.sonMenus ?? [],
  }
  applyMenuTypeDefaults(menuFormModel.value.menuType)
  dialogVisible.value = true
}

/**
 * 方法效果：
 * 对提交前的菜单表单数据做清洗，确保不同菜单类型只提交需要的字段。
 * 参数：
 * - 无，直接读取当前弹窗表单模型。
 * 返回值：
 * - 清洗后的菜单提交对象。
 */
const sanitizePayload = (): SysMenuRecord => {
  const payload: SysMenuRecord = {
    ...menuFormModel.value,
    menuName: menuFormModel.value.menuName.trim(),
    permKey: menuFormModel.value.permKey.trim(),
    route: menuFormModel.value.route.trim(),
    path: menuFormModel.value.path.trim(),
    icon: menuFormModel.value.icon.trim(),
    sonMenus: menuFormModel.value.sonMenus ?? [],
  }

  if (payload.menuType === 1) {
    payload.path = ''
    payload.backlinks = 0
  }

  if (payload.menuType === 3) {
    payload.route = ''
    payload.path = ''
    payload.icon = ''
    payload.backlinks = 0
  }

  return payload
}

/**
 * 方法效果：
 * 提交新增或编辑菜单表单。
 * 参数：
 * - 无，直接读取当前弹窗表单模型和弹窗模式。
 * 返回值：
 * - 无返回值；副作用是调用保存接口、关闭弹窗并刷新列表。
 */
const handleSubmitMenuForm = async () => {
  if (!formRef.value) {
    return
  }

  const valid = await formRef.value.validate().catch(() => false)

  if (!valid) {
    return
  }

  submitLoading.value = true

  try {
    const payload = sanitizePayload()

    if (dialogMode.value === 'create') {
      await createSysMenuApi(payload)
      ElMessage.success('菜单创建成功')
    } else {
      await updateSysMenuApi(payload)
      ElMessage.success('菜单更新成功')
    }

    dialogVisible.value = false
    await fetchMenuList()
  } finally {
    submitLoading.value = false
  }
}

/**
 * 方法效果：
 * 更新指定菜单的启用状态，并在成功后刷新菜单树列表。
 * 参数：
 * - `menuId`：菜单主键。
 * - `status`：目标状态。
 * 返回值：
 * - 无返回值；副作用是调用状态切换接口并刷新列表。
 */
const handleChangeStatus = async (menuId: number, status: number) => {
  await changeSysMenuStatusApi(menuId, status)
  ElMessage.success(status === 1 ? '菜单已启用' : '菜单已停用')
  await fetchMenuList()
}

/**
 * 方法效果：
 * 删除指定菜单。
 * 参数：
 * - `menuId`：待删除菜单主键。
 * 返回值：
 * - 无返回值；副作用是调用删除接口并刷新列表。
 */
const handleDeleteMenu = async (menuId: number) => {
  await ElMessageBox.confirm('删除后不可恢复，确认继续吗？', '删除菜单', {
    type: 'warning',
  })

  await deleteSysMenusApi([menuId])
  ElMessage.success('菜单删除成功')
  await fetchMenuList()
}

/**
 * 方法效果：
 * 在菜单类型切换时同步调整表单默认值。
 * 参数：
 * - `value`：当前选中的菜单类型。
 * 返回值：
 * - 无返回值；副作用是更新表单模型。
 */
const handleMenuTypeChange = (value: number) => {
  menuFormModel.value.menuType = Number(value)
  applyMenuTypeDefaults(menuFormModel.value.menuType)
}

/**
 * 方法效果：
 * 更新当前表单选中的上级菜单。
 * 参数：
 * - `value`：当前选中的上级菜单主键。
 * 返回值：
 * - 无返回值；副作用是更新表单模型。
 */
const handleParentChange = (value: number) => {
  menuFormModel.value.parentId = Number(value)
}

/**
 * 方法效果：
 * 统一切换菜单树的展开和折叠状态。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是批量切换树表格的展开状态。
 */
const toggleExpandAll = () => {
  expandAll.value = !expandAll.value

  if (expandAll.value) {
    expandAllRows()
  } else {
    collapseAllRows()
  }
}

/**
 * 方法效果：
 * 展开树表格全部节点（遍历当前菜单树逐行展开）。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是展开所有树节点。
 */
const expandAllRows = () => {
  expandAll.value = true
  walkMenus(menuTree.value, true)
}

/**
 * 方法效果：
 * 折叠树表格全部节点。
 * 参数：
 * - 无。
 * 返回值：
 * - 无返回值；副作用是折叠所有树节点。
 */
const collapseAllRows = () => {
  expandAll.value = false
  walkMenus(menuTree.value, false)
}

/**
 * 方法效果：
 * 递归对树表格逐行设置展开/折叠状态。
 * 参数：
 * - `menus`：当前层菜单节点。
 * - `expanded`：true 展开 / false 折叠。
 * 返回值：
 * - 无返回值；副作用是更新表格行展开状态。
 */
const walkMenus = (menus: SysMenuRecord[], expanded: boolean) => {
  menus.forEach((menu) => {
    tableRef.value?.toggleRowExpansion(menu, expanded)
    if (menu.sonMenus?.length) {
      walkMenus(menu.sonMenus, expanded)
    }
  })
}

/**
 * 方法效果：
 * 生成当前行可展示的菜单操作按钮，避免模板里出现过长的条件判断。
 * 参数：
 * - `row`：当前菜单行数据。
 * 返回值：
 * - 当前行可执行的操作按钮数组。
 */
const getMenuActions = (row: SysMenuRecord): MenuRowActionItem[] => {
  const actions: MenuRowActionItem[] = []

  if (canEditMenu.value) {
    actions.push({
      key: 'edit',
      label: '编辑',
      buttonType: 'primary',
      onClick: () => openEditDialog(Number(row.menuId)),
    })
  }

  if (canCreateMenu.value && Number(row.menuType) !== 3) {
    actions.push({
      key: 'create-child',
      label: '新增',
      buttonType: 'primary',
      onClick: () => openCreateChildDialog(row),
    })
  }

  if (canChangeStatus.value) {
    if (Number(row.status) === 1) {
      actions.push({
        key: 'disable',
        label: '停用',
        warning: true,
        onClick: () => handleChangeStatus(Number(row.menuId), 0),
      })
    } else {
      actions.push({
        key: 'enable',
        label: '启用',
        buttonType: 'primary',
        onClick: () => handleChangeStatus(Number(row.menuId), 1),
      })
    }
  }

  if (canDeleteMenu.value) {
    actions.push({
      key: 'delete',
      label: '删除',
      buttonType: 'danger',
      onClick: () => handleDeleteMenu(Number(row.menuId)),
    })
  }

  return actions
}

/**
 * 方法效果：
 * 计算菜单表格操作列直接展示的按钮：仅「编辑」内联展示，
 * 「新增 / 启停 / 删除」全部放入“更多”下拉，避免操作列挤压排版。
 * 参数：
 * - `row`：当前菜单行数据。
 * 返回值：
 * - 当前行直接展示在表格中的操作按钮数组。
 */
const getInlineMenuActions = (row: SysMenuRecord) =>
  getMenuActions(row).filter((action) => action.key === 'edit')

/**
 * 方法效果：
 * 计算菜单表格中需要放入“更多”下拉里的操作按钮：新增 / 启停 / 删除。
 * 参数：
 * - `row`：当前菜单行数据。
 * 返回值：
 * - 需要折叠展示的操作按钮数组。
 */
const getOverflowMenuActions = (row: SysMenuRecord) =>
  getMenuActions(row).filter((action) => action.key !== 'edit')

/**
 * 方法效果：
 * 单元格展示值统一格式化：空值（null/undefined/空串）显示占位符，其余转字符串并去首尾空白。
 * 参数：
 * - `value`：单元格原始值（字符串或数字，如 sort 为数字 0/1）。
 * 返回值：
 * - 展示文本；空值返回 '--'。
 */
const formatCellValue = (value?: string | number | null): string => {
  if (value === null || value === undefined || value === '') {
    return '--'
  }
  const text = String(value).trim()
  return text ? text : '--'
}

onMounted(async () => {
  await Promise.all([ensureDictLoaded('sys_menu_type'), fetchMenuList()])
})
</script>

<template>
  <section class="system-menu-view">
    <BaseCard>
      <SearchFilterPanel
        :schema="querySchema"
        :model-value="queryForm as unknown as Record<string, unknown>"
        :columns="4"
        label-width="72px"
        create-button-text="新增菜单"
        :create-permission-key="SYSTEM_PERMISSION_KEYS.menu.create"
        @update:model-value="handleQueryFormUpdate"
        @search="handleSearch"
        @reset="resetQueryForm"
        @create="openCreateDialog"
      />
    </BaseCard>

    <BaseCard title="菜单列表">
      <div class="system-menu-view__toolbar">
        <ElButton plain @click="toggleExpandAll">{{ expandAll ? '折叠全部' : '展开全部' }}</ElButton>
      </div>

      <ElTable
        ref="tableRef"
        v-loading="listLoading"
        class="system-menu-table"
        :data="menuTree"
        row-key="menuId"
        :tree-props="{ children: 'sonMenus' }"
      >
        <ElTableColumn prop="menuName" label="菜单名称" min-width="260">
          <template #default="{ row }">
            <div class="system-menu-table__name-cell">
              <span class="system-menu-table__name-text">{{ row.menuName }}</span>
              <DictTag
                class="system-menu-table__type-tag"
                dict-key="sys_menu_type"
                :value="Number(row.menuType)"
              />
            </div>
          </template>
        </ElTableColumn>

        <ElTableColumn prop="permKey" label="权限标识" min-width="220" show-overflow-tooltip />
        <ElTableColumn prop="route" label="路由片段" min-width="150">
          <template #default="{ row }">
            {{ formatCellValue(row.route) }}
          </template>
        </ElTableColumn>
        <ElTableColumn prop="path" label="组件路径" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            {{ formatCellValue(row.path) }}
          </template>
        </ElTableColumn>
        <ElTableColumn prop="icon" label="图标编码" min-width="130">
          <template #default="{ row }">
            {{ formatCellValue(row.icon) }}
          </template>
        </ElTableColumn>
        <ElTableColumn prop="sort" label="排序" width="90" align="center">
          <template #default="{ row }">
            {{ formatCellValue(row.sort) }}
          </template>
        </ElTableColumn>
        <ElTableColumn prop="status" label="状态" width="110" align="center">
          <template #default="{ row }">
            <ElTag size="small" effect="plain" :type="getMenuStatusTagType(Number(row.status))">
              {{ Number(row.status) === 1 ? '正常' : '停用' }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" min-width="230" fixed="right">
          <template #default="{ row }">
            <div class="system-menu-table__actions">
              <ElButton
                v-for="action in getInlineMenuActions(row)"
                :key="action.key"
                text
                :type="action.buttonType || 'primary'"
                :class="{ 'is-warning': action.warning }"
                @click="action.onClick()"
              >
                {{ action.label }}
              </ElButton>

              <ElDropdown
                v-if="getOverflowMenuActions(row).length > 0"
                trigger="click"
                placement="bottom-end"
              >
                <ElButton text>更多</ElButton>

                <template #dropdown>
                  <ElDropdownMenu>
                    <ElDropdownItem
                      v-for="action in getOverflowMenuActions(row)"
                      :key="action.key"
                      @click="action.onClick()"
                    >
                      {{ action.label }}
                    </ElDropdownItem>
                  </ElDropdownMenu>
                </template>
              </ElDropdown>
            </div>
          </template>
        </ElTableColumn>
      </ElTable>
    </BaseCard>

    <ElDialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="760px"
      destroy-on-close
    >
      <ElForm
        ref="formRef"
        class="system-menu-form"
        :model="menuFormModel"
        :rules="menuFormRules"
        label-width="92px"
      >
        <div class="system-menu-form__grid">
          <ElFormItem label="菜单名称" prop="menuName">
            <ElInput v-model="menuFormModel.menuName" placeholder="请输入菜单名称" />
          </ElFormItem>

          <ElFormItem label="上级菜单" prop="parentId">
            <ElSelect v-model="menuFormModel.parentId" placeholder="请选择上级菜单" @change="handleParentChange">
              <ElOption
                v-for="item in parentMenuOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </ElSelect>
          </ElFormItem>

          <ElFormItem label="菜单类型" prop="menuType">
            <ElSelect v-model="menuFormModel.menuType" placeholder="请选择菜单类型" @change="handleMenuTypeChange">
              <ElOption
                v-for="item in menuTypeOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </ElSelect>
          </ElFormItem>

          <ElFormItem label="状态" prop="status">
            <ElSelect v-model="menuFormModel.status" placeholder="请选择状态">
              <ElOption
                v-for="item in menuStatusOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </ElSelect>
          </ElFormItem>

          <ElFormItem prop="sort">
            <template #label>
              <!-- label 容器为 inline-flex（右对齐），两个子项天然横排：
                   「排序」文本 + 问号说明图标 -->
              排序
              <ElTooltip
                content="同级内按排序值升序展示（越小越靠前，可填负数）"
                placement="top"
              >
                <span class="system-menu-form__sort-help">?</span>
              </ElTooltip>
            </template>
            <ElInputNumber
              v-model="menuFormModel.sort"
              :min="-9999"
              :max="9999"
              controls-position="right"
              placeholder="越小越靠前"
              style="width: 100%"
            />
          </ElFormItem>

          <ElFormItem label="权限字符" prop="permKey">
            <ElInput
              v-model="menuFormModel.permKey"
              :placeholder="isButtonType ? '请输入按钮权限字符' : '请输入权限字符'"
            />
          </ElFormItem>

          <ElFormItem v-if="!isButtonType" label="路由片段" prop="route">
            <ElInput
              v-model="menuFormModel.route"
              :placeholder="isDirectoryType ? '请输入目录路由片段' : '请输入菜单路由片段'"
            />
          </ElFormItem>

          <ElFormItem v-if="isMenuType" label="组件路径" prop="path">
            <ElInput v-model="menuFormModel.path" placeholder="请输入组件路径" />
          </ElFormItem>

          <ElFormItem v-if="!isButtonType" label="图标编码" prop="icon">
            <MenuIconPicker
              v-model="menuFormModel.icon"
              :placeholder="isDirectoryType ? '请输入目录图标名称' : '请输入菜单图标名称'"
            />
          </ElFormItem>

          <ElFormItem v-if="isMenuType" label="外链" prop="backlinks">
            <ElSelect v-model="menuFormModel.backlinks" placeholder="请选择外链标记">
              <ElOption
                v-for="item in menuBacklinksOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </ElSelect>
          </ElFormItem>
        </div>
      </ElForm>

      <template #footer>
        <div class="system-menu-form__footer">
          <ElButton @click="dialogVisible = false">取消</ElButton>
          <ElButton type="primary" :loading="submitLoading" @click="handleSubmitMenuForm">
            {{ dialogSubmitText }}
          </ElButton>
        </div>
      </template>
    </ElDialog>
  </section>
</template>

<style scoped>
.system-menu-view {
  display: grid;
  gap: 18px;
  /* 允许子项收缩：菜单树列宽总和较大时由表格内部横向滚动，
     避免内容把整个视图撑开产生页面级水平移动 */
  min-width: 0;
}

/* 卡片及其内容区同样允许收缩，保证 ElTable 在卡片宽度内滚动 */
.system-menu-view :deep(.base-card),
.system-menu-view :deep(.base-card__body) {
  min-width: 0;
}

/* 菜单树表格：固定表格宽度，列宽超出时在表格内部横向滚动 */
.system-menu-table {
  width: 100%;
}

.system-menu-view__toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}

.system-menu-table :deep(.el-table__cell) {
  vertical-align: middle;
}

.system-menu-table :deep(.el-table__cell:first-child .cell) {
  display: flex;
  align-items: center;
  gap: 8px;
}

.system-menu-table__name-cell {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.system-menu-table__name-text {
  min-width: 0;
}

.system-menu-table__type-tag {
  flex: none;
}

.system-menu-table__actions {
  display: flex;
  align-items: center;
  gap: 0 4px;
  flex-wrap: nowrap;
  white-space: nowrap;
}

.system-menu-table__actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.system-menu-table__actions :deep(.el-button.is-warning) {
  color: #d97706;
}

.system-menu-form__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 2px 16px;
}

.system-menu-form__grid :deep(.el-form-item) {
  margin-bottom: 18px;
}

.system-menu-form__grid :deep(.el-select),
.system-menu-form__grid :deep(.el-select__wrapper),
.system-menu-form__grid :deep(.el-input),
.system-menu-form__grid :deep(.el-input__wrapper) {
  width: 100%;
}

/* 问号说明图标：纯文本实现，宽高固定 18px（输入框 32px 的一半），
   尺寸完全由 CSS 控制；作为 label 插槽子项与「排序」文本天然横排（label 为 inline-flex），
   align-self: center 覆盖行内元素的基线对齐，保证与文字垂直居中；
   悬浮由 ElTooltip 显示说明 */
.system-menu-form__sort-help {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  align-self: center;
  width: 18px;
  height: 18px;
  margin-left: 4px;
  border-radius: 50%;
  background: var(--rookie-surface-weak);
  border: 1px solid var(--rookie-border);
  color: var(--rookie-text-secondary);
  font-size: 12px;
  line-height: 1;
  cursor: help;
  transition:
    border-color 0.2s ease,
    color 0.2s ease;
}

.system-menu-form__sort-help:hover {
  border-color: var(--rookie-primary-border);
  color: var(--rookie-primary-strong);
}

.system-menu-form__footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>
