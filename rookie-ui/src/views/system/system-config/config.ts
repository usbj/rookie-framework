/**
 * 文件作用：
 * 集中定义「系统设置」页面的字段 schema、默认值、校验规则与固定选项，
 * 让 index.vue 专注状态与交互。schema 部分按表单模式与当前值类型动态生成，
 * 以支持「按 valueType 切换值输入控件」与「内置项编辑时禁用键与类型」两个动态行为。
 */
import type { FormRules } from 'element-plus'
import type { SharedFieldInputType, SharedFieldSchemaMap } from '@/types/components/data-display'
import type { SysConfigRecord } from '@/types/api/system/system-config'
import { formatDateTime } from '@/utils/format'

/**
 * 表单模型类型：在 SysConfigRecord 基础上把 configValue 放宽为 string | number | boolean，
 * 以适配不同值类型的表单控件（BOOLEAN 用开关需 boolean、NUMBER 用数字控件需 number、其余 string）。
 * 提交前由 index.vue 归一化回字符串再发给后端。
 */
export type SysConfigFormModel = Omit<SysConfigRecord, 'configValue'> & {
  configValue: string | number | boolean
}

/** 值类型字典键，前端 valueType 标签与下拉统一走该字典 */
export const CONFIG_VALUE_TYPE_DICT_KEY = 'sys_config_value_type'

/** 状态选项：1启用 0停用，与 sys_config.status 对齐 */
export const configStatusOptions = [
  { label: '启用', value: 1 },
  { label: '停用', value: 0 },
]

/** 值类型 → 值字段输入控件类型映射，控制 configValue 在表单中按类型切换控件 */
const VALUE_TYPE_TO_INPUT: Record<string, SharedFieldInputType> = {
  STRING: 'text',
  BOOLEAN: 'switch',
  NUMBER: 'number',
  JSON: 'textarea',
}

export interface SysConfigQueryFormState {
  configKey: string
  configName: string
  status: number | undefined
  dateRange: string[]
}

export const createDefaultSysConfigQuery = (): SysConfigQueryFormState => ({
  configKey: '',
  configName: '',
  status: undefined,
  dateRange: [],
})

/**
 * 新增表单默认值。
 * valueType 默认 STRING，isSystem 固定 0（内置项只能由系统初始化脚本写入，业务接口新增强制置 0），
 * status 默认 1 启用。
 */
export const createDefaultSysConfigForm = (): SysConfigFormModel => ({
  configId: undefined,
  configKey: '',
  configName: '',
  configValue: '',
  valueType: 'STRING',
  isSystem: 0,
  remark: '',
  status: 1,
})

export const createSysConfigQuerySchema = (): SharedFieldSchemaMap<SysConfigQueryFormState> => ({
  configKey: {
    label: '设置键',
    inputType: 'text',
    placeholder: '请输入设置键',
    tableVisible: false,
    formVisible: true,
    formOrder: 1,
    span: 6,
    props: { style: { width: '100%' } },
  },
  configName: {
    label: '设置名称',
    inputType: 'text',
    placeholder: '请输入设置名称',
    tableVisible: false,
    formVisible: true,
    formOrder: 2,
    span: 6,
    props: { style: { width: '100%' } },
  },
  status: {
    label: '状态',
    inputType: 'select',
    placeholder: '请选择状态',
    tableVisible: false,
    formVisible: true,
    formOrder: 3,
    span: 5,
    props: { style: { width: '100%' } },
    options: configStatusOptions,
  },
  dateRange: {
    label: '创建时间',
    inputType: 'daterange',
    tableVisible: false,
    formVisible: true,
    formOrder: 4,
    span: 7,
    props: {
      unlinkPanels: true,
      style: { width: '100%' },
    },
  },
})

/**
 * 生成系统设置表格 + 表单 schema。
 * 动态行为：
 * - configValue 的 inputType 随当前 valueType 切换（STRING→文本、NUMBER→数字、BOOLEAN→开关、JSON→多行文本）。
 * - 编辑内置项（isSystem=1）时，configKey 与 valueType 禁用，仅可改值/名称/备注/状态。
 * 参数：
 * - `mode`：表单模式 'create' | 'edit'。
 * - `isSystem`：当前编辑项是否内置项（仅 edit 模式有意义）。
 * - `valueType`：当前值类型，决定 configValue 控件类型。
 * 返回值：
 * - 表格与表单共用的字段 schema 映射。
 */
export const createSysConfigSchema = (
  mode: 'create' | 'edit',
  isSystem: number,
  valueType: string,
): SharedFieldSchemaMap<SysConfigFormModel> => ({
  configId: {
    label: '编号',
    tableVisible: true,
    formVisible: false,
    tableOrder: 1,
    tableWidth: 80,
  },
  configKey: {
    label: '设置键',
    inputType: 'text',
    placeholder: '请输入设置键（如 sys.user.initPassword）',
    tableVisible: true,
    formVisible: true,
    tableOrder: 2,
    formOrder: 1,
    span: 12,
    tableMinWidth: 200,
    // 内置项编辑时禁用，防止改键导致代码硬依赖失效；新增模式可输入
    disabled: mode === 'edit' && isSystem === 1,
  },
  configName: {
    label: '设置名称',
    inputType: 'text',
    placeholder: '请输入设置名称',
    tableVisible: true,
    formVisible: true,
    tableOrder: 3,
    formOrder: 2,
    span: 12,
    tableMinWidth: 160,
  },
  configValue: {
    label: '设置值',
    // 按 valueType 切换控件：BOOLEAN 开关、NUMBER 数字、JSON 多行文本、其余普通文本
    inputType: VALUE_TYPE_TO_INPUT[valueType] ?? 'text',
    placeholder: valueType === 'JSON' ? '请输入 JSON 对象或数组' : '请输入设置值',
    tableVisible: true,
    formVisible: true,
    tableOrder: 4,
    formOrder: 4,
    span: 24,
    tableMinWidth: 180,
    // JSON 类型多行编辑，其余单行；开关不需要 rows
    props: valueType === 'JSON' ? { rows: 4 } : {},
    // 表格中 BOOLEAN 显示是/否，其余原值（JSON 等长文本由 show-overflow-tooltip 截断）
    formatter: (value, row) => {
      if (String(row.valueType) === 'BOOLEAN') {
        return String(value) === 'true' || String(value) === '1' ? '是' : '否'
      }
      return value === null || value === undefined || value === '' ? '--' : String(value)
    },
  },
  valueType: {
    label: '值类型',
    inputType: 'select',
    dictKey: CONFIG_VALUE_TYPE_DICT_KEY,
    placeholder: '请选择值类型',
    tableVisible: true,
    formVisible: true,
    tableOrder: 5,
    formOrder: 3,
    span: 12,
    tableWidth: 110,
    // 内置项编辑时禁用，防止改类型导致缓存解释错乱；新增模式可选
    disabled: mode === 'edit' && isSystem === 1,
  },
  isSystem: {
    label: '内置',
    // 仅表格展示（表单不编辑此字段，新增强制 0，内置项由初始化脚本写入）
    tableVisible: true,
    formVisible: false,
    tableOrder: 6,
    tableWidth: 90,
    formatter: (value) => (Number(value) === 1 ? '内置' : '自定义'),
  },
  status: {
    label: '状态',
    inputType: 'select',
    placeholder: '请选择状态',
    tableVisible: true,
    formVisible: true,
    tableOrder: 7,
    formOrder: 5,
    span: 12,
    tableWidth: 90,
    options: configStatusOptions,
    formatter: (value) => (Number(value) === 1 ? '启用' : '停用'),
    // 内置项编辑时禁用停用，与后端「内置项不可停用」校验对齐
    disabled: mode === 'edit' && isSystem === 1,
  },
  remark: {
    label: '备注',
    inputType: 'textarea',
    placeholder: '请输入备注说明',
    tableVisible: true,
    formVisible: true,
    tableOrder: 8,
    formOrder: 6,
    span: 24,
    tableMinWidth: 180,
    props: {
      rows: 3,
    },
  },
  createTime: {
    label: '创建时间',
    tableVisible: true,
    formVisible: false,
    tableOrder: 9,
    tableMinWidth: 170,
    formatter: (value) => formatDateTime(value),
  },
})

export const sysConfigFormRules: FormRules = {
  configKey: [
    { required: true, message: '请输入设置键', trigger: 'blur' },
    { min: 2, max: 100, message: '设置键长度需在 2 到 100 位之间', trigger: 'blur' },
  ],
  configName: [
    { required: true, message: '请输入设置名称', trigger: 'blur' },
    { min: 2, max: 50, message: '设置名称长度需在 2 到 50 位之间', trigger: 'blur' },
  ],
  // 设置值为必填；BOOLEAN 的 false、NUMBER 的 0 为合法值，不会被 required 判空
  configValue: [{ required: true, message: '请输入设置值', trigger: 'blur' }],
  valueType: [{ required: true, message: '请选择值类型', trigger: 'change' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }],
}
