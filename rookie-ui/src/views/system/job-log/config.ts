/**
 * 文件作用：
 * 承接定时任务执行日志页面的字段配置，
 * 统一定义执行日志列表筛选 / 表格列的字段元数据。
 * 关键约定：
 * - triggerType / status 取值固定（AUTO/MANUAL、0/1），不依赖字典，直接硬编码选项；
 * - status 表格用 tag 渲染（成功绿 / 失败红），exceptionMsg 超长时截断展示。
 */
import type { SharedFieldSchemaMap } from '@/types/components/data-display'
import type { SysJobLogRecord } from '@/types/api/system/job'
import { formatDateTime } from '@/utils/format'

export interface JobLogQueryFormState {
  jobName: string
  triggerType: string | undefined
  status: number | undefined
  dateRange: string[]
}

/** 触发方式选项（与后端 trigger_type 字段对齐） */
export const TRIGGER_TYPE_OPTIONS = [
  { label: '自动', value: 'AUTO' },
  { label: '手动', value: 'MANUAL' },
]

/** 执行状态选项（与后端 status 字段对齐：0成功 1失败） */
export const JOB_LOG_STATUS_OPTIONS = [
  { label: '成功', value: 0 },
  { label: '失败', value: 1 },
]

/** 异常信息表格展示最大长度（超长截断，完整信息可结合后端日志排查） */
const MAX_EXCEPTION_MSG_DISPLAY = 100

/**
 * 方法效果：
 * 生成执行日志查询表单的初始状态，保证筛选区每次重置后回到统一空值。
 * 参数：
 * - 无。
 * 返回值：
 * - 执行日志查询表单的默认对象。
 */
export const createDefaultJobLogQuery = (): JobLogQueryFormState => ({
  jobName: '',
  triggerType: undefined,
  status: undefined,
  dateRange: [],
})

/**
 * 方法效果：
 * 构建执行日志筛选区的字段配置，只在筛选区展示，不参与表格。
 * 参数：
 * - 无。
 * 返回值：
 * - 筛选区字段配置映射。
 */
export const createJobLogQuerySchema = (): SharedFieldSchemaMap<JobLogQueryFormState> => ({
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
  triggerType: {
    label: '触发方式',
    inputType: 'select',
    placeholder: '请选择触发方式',
    tableVisible: false,
    formVisible: true,
    formOrder: 2,
    span: 3,
    clearable: true,
    options: TRIGGER_TYPE_OPTIONS,
    props: { style: { width: '100%' } },
  },
  status: {
    label: '执行状态',
    inputType: 'select',
    placeholder: '请选择执行状态',
    tableVisible: false,
    formVisible: true,
    formOrder: 3,
    span: 3,
    clearable: true,
    options: JOB_LOG_STATUS_OPTIONS,
    props: { style: { width: '100%' } },
  },
  dateRange: {
    label: '执行时间',
    inputType: 'daterange',
    tableVisible: false,
    formVisible: true,
    formOrder: 4,
    span: 5,
    props: {
      unlinkPanels: true,
      style: { width: '100%' },
    },
  },
})

/**
 * 方法效果：
 * 构建执行日志表格列的字段配置（只读列表，无表单）。
 * 参数：
 * - 无。
 * 返回值：
 * - 执行日志字段配置映射，驱动表格列展示。
 */
export const createJobLogSchema = (): SharedFieldSchemaMap<SysJobLogRecord> => ({
  logId: {
    label: '日志编号',
    tableVisible: true,
    formVisible: false,
    tableOrder: 1,
    tableWidth: 100,
  },
  jobName: {
    label: '任务名称',
    tableVisible: true,
    formVisible: false,
    tableOrder: 2,
    tableMinWidth: 140,
  },
  triggerType: {
    label: '触发方式',
    tableVisible: true,
    formVisible: false,
    tableOrder: 3,
    tableWidth: 100,
    formatter: (value) => (String(value) === 'MANUAL' ? '手动' : '自动'),
  },
  invokeTarget: {
    label: '调用目标',
    tableVisible: true,
    formVisible: false,
    tableOrder: 4,
    tableMinWidth: 200,
  },
  status: {
    label: '状态',
    tableVisible: true,
    formVisible: false,
    tableOrder: 5,
    tableWidth: 90,
    options: JOB_LOG_STATUS_OPTIONS,
    renderType: 'tag',
    tagRender: {
      tagTypeMap: { 0: 'success', 1: 'danger' },
    },
  },
  costTime: {
    label: '耗时',
    tableVisible: true,
    formVisible: false,
    tableOrder: 6,
    tableWidth: 100,
    formatter: (value) => (value == null ? '--' : `${value} ms`),
  },
  exceptionMsg: {
    label: '异常信息',
    tableVisible: true,
    formVisible: false,
    tableOrder: 7,
    tableMinWidth: 200,
    formatter: (value) => {
      if (value == null || value === '') {
        return '--'
      }
      const text = String(value)
      return text.length > MAX_EXCEPTION_MSG_DISPLAY ? `${text.slice(0, MAX_EXCEPTION_MSG_DISPLAY)}…` : text
    },
  },
  executeTime: {
    label: '执行时间',
    tableVisible: true,
    formVisible: false,
    tableOrder: 8,
    tableMinWidth: 180,
    formatter: (value) => formatDateTime(value),
  },
})
