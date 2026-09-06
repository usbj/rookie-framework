/**
 * 文件作用：
 * 承接在线用户管理页面的字段配置，
 * 统一定义在线列表表格列的字段元数据（只读列表，无筛选/表单）。
 * 关键约定：
 * - loginTime / lastActive 为毫秒时间戳（number），统一转 Date 后走 formatDateTime 展示；
 * - 在线用户列表只读，无新增/编辑表单，所有字段 formVisible 均为 false。
 */
import type { SharedFieldSchemaMap } from '@/types/components/data-display'
import type { OnlineUserRecord } from '@/types/api/system/online'
import { formatDateTime } from '@/utils/format'

/**
 * 方法效果：
 * 把毫秒时间戳格式化为可读时间（在线列表专用）。
 * formatDateTime 内部用 new Date(String(value)) 解析，对纯数字字符串会得到 Invalid Date，
 * 因此先显式 new Date(number) 再交给统一格式化。
 * 参数：
 * - `value`：毫秒时间戳。
 * 返回值：
 * - `YYYY-MM-DD HH:mm:ss`；无效值返回兜底文案。
 */
const formatEpochTime = (value: unknown) => formatDateTime(new Date(Number(value)))

/**
 * 方法效果：
 * 构建在线用户表格列的字段配置。
 * 参数：
 * - 无。
 * 返回值：
 * - 在线用户字段配置映射，驱动表格列展示。
 */
export const createOnlineSchema = (): SharedFieldSchemaMap<OnlineUserRecord> => ({
  username: {
    label: '登录账号',
    tableVisible: true,
    formVisible: false,
    tableOrder: 1,
    tableMinWidth: 120,
  },
  nickName: {
    label: '昵称',
    tableVisible: true,
    formVisible: false,
    tableOrder: 2,
    tableMinWidth: 120,
  },
  loginIp: {
    label: '登录 IP',
    tableVisible: true,
    formVisible: false,
    tableOrder: 3,
    tableWidth: 140,
  },
  loginTime: {
    label: '登录时间',
    tableVisible: true,
    formVisible: false,
    tableOrder: 4,
    tableMinWidth: 170,
    formatter: formatEpochTime,
  },
  lastActive: {
    label: '最后活跃',
    tableVisible: true,
    formVisible: false,
    tableOrder: 5,
    tableMinWidth: 170,
    formatter: formatEpochTime,
  },
})
