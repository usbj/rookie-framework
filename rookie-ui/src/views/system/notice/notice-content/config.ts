/**
 * 文件作用：
 * 承接通知内容管理页面的字段配置与表单默认值，
 * 统一定义通知列表筛选、表格列、弹窗表单的字段元数据和校验规则。
 * 关键约定：
 * - noticeType / level / publishScope / status 统一走字典系统（dictKey），
 *   表格自动渲染为 DictTag、表单自动渲染为字典驱动的 select，
 *   下拉选项与标签样式由字典数据项的 tagType / tagEffect / cssClass 统一控制，
 *   不再在页面 config 中硬编码枚举值。
 * - routePath、expireTime 字段已删除，publishTime 由发布接口确定、status 默认草稿，均不参与表单。
 */
import type { FormRules } from 'element-plus'
import type { SharedFieldSchemaMap } from '@/types/components/data-display'
import type { SysNoticeRecord } from '@/types/api/system/notice'
import { formatDateTime } from '@/utils/format'

export interface NoticeQueryFormState {
  title: string
  noticeType: string | undefined
  level: string | undefined
  status: string | undefined
  dateRange: string[]
}

/**
 * 方法效果：
 * 生成通知查询表单的初始状态，保证筛选区每次重置后回到统一空值。
 * 参数：
 * - 无。
 * 返回值：
 * - 通知查询表单的默认对象。
 */
export const createDefaultNoticeQuery = (): NoticeQueryFormState => ({
  title: '',
  noticeType: undefined,
  level: undefined,
  status: undefined,
  dateRange: [],
})

/**
 * 方法效果：
 * 生成通知弹窗表单的初始状态，枚举字段给后端默认值，避免新增时漏传。
 * routePath、expireTime 字段已删除，publishTime 由发布接口确定不参与表单，
 * status 固定 DRAFT 不在表单展示。
 * 参数：
 * - 无。
 * 返回值：
 * - 通知表单的默认对象。
 */
export const createDefaultNoticeForm = (): SysNoticeRecord => ({
  title: '',
  content: '',
  noticeType: 'NOTICE',
  level: 'NORMAL',
  publishScope: 'ALL',
  status: 'DRAFT',
  isTop: 0,
  needConfirm: 0,
  remark: '',
  groupIds: [],
  targetUserIds: [],
})

/**
 * 方法效果：
 * 构建通知列表筛选区的字段配置，只在筛选区展示，不参与表格与弹窗。
 * 参数：
 * - 无。
 * 返回值：
 * - 筛选区字段配置映射。
 */
export const createNoticeQuerySchema = (): SharedFieldSchemaMap<NoticeQueryFormState> => ({
  title: {
    label: '标题',
    inputType: 'text',
    placeholder: '请输入通知标题',
    tableVisible: false,
    formVisible: true,
    formOrder: 1,
    span: 6,
    props: { style: { width: '100%' } },
  },
  noticeType: {
    label: '类型',
    inputType: 'select',
    placeholder: '请选择类型',
    tableVisible: false,
    formVisible: true,
    formOrder: 2,
    span: 4,
    clearable: true,
    dictKey: 'sys_notice_type',
    props: { style: { width: '100%' } },
  },
  level: {
    label: '级别',
    inputType: 'select',
    placeholder: '请选择级别',
    tableVisible: false,
    formVisible: true,
    formOrder: 3,
    span: 4,
    clearable: true,
    dictKey: 'sys_notice_level',
    props: { style: { width: '100%' } },
  },
  status: {
    label: '状态',
    inputType: 'select',
    placeholder: '请选择状态',
    tableVisible: false,
    formVisible: true,
    formOrder: 4,
    span: 4,
    clearable: true,
    dictKey: 'sys_notice_status',
    props: { style: { width: '100%' } },
  },
  dateRange: {
    label: '创建时间',
    inputType: 'daterange',
    tableVisible: false,
    formVisible: true,
    formOrder: 5,
    span: 6,
    props: {
      unlinkPanels: true,
      style: { width: '100%' },
    },
  },
})

/**
 * 方法效果：
 * 构建通知表格列与弹窗表单共用的字段配置。
 * 参数：
 * - `groupOptions`：可关联的分组下拉选项，供弹窗内 groupIds 多选字段复用。
 * 返回值：
 * - 通知字段配置映射，同时驱动表格列展示和弹窗表单编辑。
 */
export const createNoticeSchema = (
  groupOptions: Array<{ label: string; value: number }>,
): SharedFieldSchemaMap<SysNoticeRecord> => ({
  noticeId: {
    label: '通知编号',
    tableVisible: true,
    formVisible: false,
    tableOrder: 1,
    tableWidth: 110,
  },
  title: {
    label: '标题',
    inputType: 'text',
    placeholder: '请输入通知标题',
    tableVisible: true,
    formVisible: true,
    tableOrder: 2,
    formOrder: 1,
    tableMinWidth: 180,
    span: 24,
  },
  noticeType: {
    label: '类型',
    inputType: 'select',
    placeholder: '请选择类型',
    tableVisible: true,
    formVisible: true,
    tableOrder: 3,
    formOrder: 2,
    tableWidth: 110,
    span: 8,
    dictKey: 'sys_notice_type',
  },
  level: {
    label: '级别',
    inputType: 'select',
    placeholder: '请选择级别',
    tableVisible: true,
    formVisible: true,
    tableOrder: 4,
    formOrder: 3,
    tableWidth: 110,
    span: 8,
    dictKey: 'sys_notice_level',
  },
  publishScope: {
    label: '发布范围',
    inputType: 'select',
    placeholder: '请选择发布范围',
    tableVisible: true,
    formVisible: true,
    tableOrder: 5,
    formOrder: 4,
    tableWidth: 120,
    span: 8,
    dictKey: 'sys_notice_scope',
  },
  status: {
    label: '状态',
    tableVisible: true,
    formVisible: false,
    tableOrder: 6,
    tableWidth: 120,
    dictKey: 'sys_notice_status',
  },
  isTop: {
    label: '置顶',
    inputType: 'switch',
    tableVisible: true,
    formVisible: true,
    tableOrder: 7,
    formOrder: 5,
    tableWidth: 90,
    span: 8,
    formatter: (value) => (Number(value) === 1 ? '是' : '否'),
  },
  needConfirm: {
    label: '需确认',
    inputType: 'switch',
    tableVisible: true,
    formVisible: true,
    tableOrder: 8,
    formOrder: 6,
    tableWidth: 100,
    span: 8,
    formatter: (value) => (Number(value) === 1 ? '是' : '否'),
  },
  publishTime: {
    label: '发布时间',
    tableVisible: true,
    formVisible: false,
    tableOrder: 9,
    tableMinWidth: 170,
    formatter: (value) => formatDateTime(value),
  },
  groupIds: {
    label: '关联分组',
    inputType: 'custom',
    tableVisible: false,
    formVisible: true,
    formOrder: 7,
    span: 24,
    // 仅在发布范围为分组时展示，全员范围下隐藏整个表单项
    visibleWhen: (model) => String(model.publishScope ?? '') === 'GROUP',
    options: groupOptions,
  },
  targetUserIds: {
    label: '指定成员',
    inputType: 'custom',
    tableVisible: false,
    formVisible: true,
    formOrder: 8,
    span: 24,
    // 仅在发布范围为"指定成员"时展示，其余范围隐藏
    visibleWhen: (model) => String(model.publishScope ?? '') === 'USER',
  },
  content: {
    label: '正文内容',
    inputType: 'markdown',
    placeholder: '请输入通知正文（支持 Markdown）',
    tableVisible: false,
    formVisible: true,
    formOrder: 9,
    span: 24,
  },
  remark: {
    label: '备注',
    inputType: 'textarea',
    placeholder: '请输入备注',
    tableVisible: false,
    formVisible: true,
    formOrder: 10,
    span: 24,
    props: {
      rows: 2,
    },
  },
  createTime: {
    label: '创建时间',
    tableVisible: true,
    formVisible: false,
    tableOrder: 10,
    tableMinWidth: 170,
    formatter: (value) => formatDateTime(value),
  },
})

export const noticeFormRules: FormRules = {
  title: [
    { required: true, message: '请输入通知标题', trigger: 'blur' },
    { min: 2, max: 200, message: '通知标题长度需在 2 到 200 位之间', trigger: 'blur' },
  ],
  content: [{ required: true, message: '请输入通知正文', trigger: 'blur' }],
  noticeType: [{ required: true, message: '请选择通知类型', trigger: 'change' }],
  level: [{ required: true, message: '请选择通知级别', trigger: 'change' }],
  publishScope: [{ required: true, message: '请选择发布范围', trigger: 'change' }],
}
