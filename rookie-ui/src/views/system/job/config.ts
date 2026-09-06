/**
 * 文件作用：
 * 承接定时任务管理页面的字段配置与表单默认值，
 * 统一定义任务列表筛选 / 表格列 / 表单字段的元数据。
 * 关键约定：
 * - 状态（status）不依赖字典，直接硬编码选项（启用/停用），表格用 tag 渲染；
 * - cron 表达式为 Spring 6 段式，表单做必填与 6 段格式的轻校验，完整合法性由后端校验；
 * - 调用目标（beanName + methodName）限定 task.bean-package-prefixes 配置项所列包（默认 com.rookie.system.task），
 *   表单 placeholder 明确提示。
 */
import type { FormRules } from 'element-plus'
import type { SharedFieldSchemaMap } from '@/types/components/data-display'
import type { SysJobRecord } from '@/types/api/system/job'
import { formatDateTime } from '@/utils/format'

export interface JobQueryFormState {
  jobName: string
  status: number | undefined
  dateRange: string[]
}

/** 任务状态选项（与后端 status 字段对齐：1启用 0停用） */
export const JOB_STATUS_OPTIONS = [
  { label: '启用', value: 1 },
  { label: '停用', value: 0 },
]

/**
 * 方法效果：
 * 生成任务查询表单的初始状态，保证筛选区每次重置后回到统一空值。
 * 参数：
 * - 无。
 * 返回值：
 * - 任务查询表单的默认对象。
 */
export const createDefaultJobQuery = (): JobQueryFormState => ({
  jobName: '',
  status: undefined,
  dateRange: [],
})

/**
 * 方法效果：
 * 生成任务新增/编辑表单的初始状态（新增默认启用）。
 * 参数：
 * - 无。
 * 返回值：
 * - 任务表单的默认对象。
 */
export const createDefaultJobForm = (): SysJobRecord => ({
  jobName: '',
  beanName: '',
  methodName: '',
  cronExpression: '',
  params: '',
  status: 1,
  remark: '',
})

/**
 * 方法效果：
 * 构建任务列表筛选区的字段配置，只在筛选区展示，不参与表格。
 * 参数：
 * - 无。
 * 返回值：
 * - 筛选区字段配置映射。
 */
export const createJobQuerySchema = (): SharedFieldSchemaMap<JobQueryFormState> => ({
  jobName: {
    label: '任务名称',
    inputType: 'text',
    placeholder: '请输入任务名称',
    tableVisible: false,
    formVisible: true,
    formOrder: 1,
    span: 4,
    props: { style: { width: '100%' } },
  },
  status: {
    label: '状态',
    inputType: 'select',
    placeholder: '请选择状态',
    tableVisible: false,
    formVisible: true,
    formOrder: 2,
    span: 3,
    clearable: true,
    options: JOB_STATUS_OPTIONS,
    props: { style: { width: '100%' } },
  },
  dateRange: {
    label: '创建时间',
    inputType: 'daterange',
    tableVisible: false,
    formVisible: true,
    formOrder: 3,
    span: 5,
    props: {
      unlinkPanels: true,
      style: { width: '100%' },
    },
  },
})

/**
 * 方法效果：
 * 构建任务列表表格列与表单字段的配置。
 * 参数：
 * - `dialogMode`：弹窗模式（create/edit，仅影响部分字段展示）。
 * 返回值：
 * - 任务字段配置映射，驱动表格列展示与弹窗表单。
 */
export const createJobSchema = (
  dialogMode: 'create' | 'edit',
): SharedFieldSchemaMap<SysJobRecord> => ({
  jobId: {
    label: '任务编号',
    tableVisible: true,
    formVisible: false,
    tableOrder: 1,
    tableWidth: 100,
  },
  jobName: {
    label: '任务名称',
    inputType: 'text',
    placeholder: '请输入任务名称',
    tableVisible: true,
    formVisible: true,
    tableOrder: 2,
    formOrder: 1,
    span: 12,
    tableMinWidth: 140,
  },
  beanName: {
    label: 'Bean 名称',
    inputType: 'text',
    placeholder: '如 demoTask（限配置项所列任务包）',
    tableVisible: true,
    formVisible: true,
    tableOrder: 3,
    formOrder: 2,
    span: 12,
    tableMinWidth: 140,
  },
  methodName: {
    label: '方法名',
    inputType: 'text',
    placeholder: '如 reconcile / gc（public，无参或单个 String 参数）',
    tableVisible: true,
    formVisible: true,
    tableOrder: 4,
    formOrder: 3,
    span: 12,
    tableMinWidth: 120,
  },
  cronExpression: {
    label: 'cron 表达式',
    inputType: 'text',
    placeholder: 'Spring 6 段式，如 0 0 2 * * ?（每天 02:00）',
    tableVisible: true,
    formVisible: true,
    tableOrder: 5,
    formOrder: 4,
    span: 12,
    tableMinWidth: 160,
  },
  params: {
    label: '执行参数',
    inputType: 'text',
    placeholder: '可选；方法为 String 单参时传入',
    tableVisible: false,
    formVisible: true,
    formOrder: 5,
    span: 12,
  },
  status: {
    label: '状态',
    inputType: 'select',
    placeholder: '请选择状态',
    tableVisible: true,
    formVisible: true,
    tableOrder: 6,
    formOrder: 6,
    span: 12,
    tableWidth: 90,
    options: JOB_STATUS_OPTIONS,
    // 非字典字段的标签渲染：启用绿 / 停用灰
    renderType: 'tag',
    tagRender: {
      tagTypeMap: { 1: 'success', 0: 'info' },
    },
  },
  remark: {
    label: '备注',
    inputType: 'textarea',
    placeholder: '选填',
    tableVisible: true,
    formVisible: true,
    tableOrder: 7,
    formOrder: 7,
    span: 24,
    tableMinWidth: 140,
    props: { rows: 2 },
  },
  createTime: {
    label: '创建时间',
    tableVisible: true,
    formVisible: false,
    tableOrder: 8,
    tableMinWidth: 180,
    formatter: (value) => formatDateTime(value),
  },
})

/**
 * 任务表单校验规则：cron 必填 + 6 段式格式轻校验（完整合法性由后端 CronExpression 把关）。
 */
export const createJobFormRules = (): FormRules => ({
  jobName: [{ required: true, message: '请输入任务名称', trigger: 'blur' }],
  beanName: [{ required: true, message: '请输入 Bean 名称', trigger: 'blur' }],
  methodName: [{ required: true, message: '请输入方法名', trigger: 'blur' }],
  cronExpression: [
    { required: true, message: '请输入 cron 表达式', trigger: 'blur' },
    {
      pattern: /^(\S+\s+){5}\S+$/,
      message: 'cron 需为 6 段式表达式，如 0 0 2 * * ?',
      trigger: 'blur',
    },
  ],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }],
})
