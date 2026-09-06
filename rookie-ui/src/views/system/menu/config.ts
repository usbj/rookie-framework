import type { FormRules } from 'element-plus'
import type { SharedFieldSchemaMap } from '@/types/components/data-display'
import type { SysMenuRecord } from '@/types/api/system/menu'

export interface MenuQueryFormState {
  menuName: string
  status: number | undefined
}

export const menuStatusOptions = [
  { label: '正常', value: 1 },
  { label: '停用', value: 0 },
]

export const menuBacklinksOptions = [
  { label: '否', value: 0 },
  { label: '是', value: 1 },
]

export const createDefaultMenuQuery = (): MenuQueryFormState => ({
  menuName: '',
  status: undefined,
})

export const createDefaultMenuForm = (): SysMenuRecord => ({
  menuId: 0,
  menuName: '',
  permKey: '',
  // 顶级菜单约定 parentId = -1，与后端 SysMenuServiceImpl.buildMenuTree
  // 及 sys_menu 表默认值、种子数据保持一致（0 会导致顶级菜单在列表中不展示）。
  parentId: -1,
  menuType: 2,
  route: '',
  backlinks: 0,
  path: '',
  icon: 'Menu',
  sort: 0,
  status: 1,
  sonMenus: [],
})

export const createMenuQuerySchema = (): SharedFieldSchemaMap<MenuQueryFormState> => ({
  menuName: {
    label: '菜单名称',
    inputType: 'text',
    placeholder: '请输入菜单名称',
    tableVisible: false,
    formVisible: true,
    formOrder: 1,
    props: { style: { width: '100%' } },
  },
  status: {
    label: '状态',
    inputType: 'select',
    placeholder: '请选择状态',
    tableVisible: false,
    formVisible: true,
    formOrder: 2,
    props: { style: { width: '100%' } },
    options: menuStatusOptions,
  },
})

export const buildMenuFormRules = (menuType: number): FormRules => {
  const baseRules: FormRules = {
    menuName: [
      { required: true, message: '请输入菜单名称', trigger: 'blur' },
      { min: 2, max: 30, message: '菜单名称长度需在 2 到 30 位之间', trigger: 'blur' },
    ],
    parentId: [{ required: true, message: '请选择上级菜单', trigger: 'change' }],
    menuType: [{ required: true, message: '请选择菜单类型', trigger: 'change' }],
    status: [{ required: true, message: '请选择状态', trigger: 'change' }],
  }

  if (menuType === 1) {
    baseRules.permKey = [{ required: true, message: '请输入目录权限字符', trigger: 'blur' }]
    baseRules.route = [{ required: true, message: '请输入目录路由片段', trigger: 'blur' }]
    baseRules.icon = [{ required: true, message: '请输入目录图标编码', trigger: 'blur' }]
  }

  if (menuType === 2) {
    baseRules.permKey = [{ required: true, message: '请输入菜单权限字符', trigger: 'blur' }]
    baseRules.route = [{ required: true, message: '请输入菜单路由片段', trigger: 'blur' }]
    baseRules.path = [{ required: true, message: '请输入组件路径', trigger: 'blur' }]
    baseRules.icon = [{ required: true, message: '请输入菜单图标编码', trigger: 'blur' }]
    baseRules.backlinks = [{ required: true, message: '请选择是否外链', trigger: 'change' }]
  }

  if (menuType === 3) {
    baseRules.permKey = [{ required: true, message: '请输入按钮权限字符', trigger: 'blur' }]
  }

  return baseRules
}

export const resolveMenuStatusLabel = (value: number) =>
  menuStatusOptions.find((item) => item.value === Number(value))?.label ?? '--'

export const getMenuStatusTagType = (value: number) => (Number(value) === 1 ? 'primary' : 'info')
