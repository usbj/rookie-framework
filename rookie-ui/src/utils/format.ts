/**
 * 文件作用：
 * 提供统一的数据格式化工具，覆盖表格/表单展示中最常见的时间、文本和特殊字符清洗场景。
 */

export interface FormatDisplayValueOptions {
  fallback?: string
  preserveLineBreaks?: boolean
}

const DEFAULT_FALLBACK = '--'

/**
 * 方法效果：
 * 判断当前值是否为空展示值。
 * 参数：
 * - `value`：待判断的原始值。
 * 返回值：
 * - `true` 表示应按空值处理。
 */
export const isEmptyDisplayValue = (value: unknown): boolean =>
  value === null || value === undefined || value === ''

/**
 * 方法效果：
 * 清洗文本中的控制字符和多余空白，避免特殊字符把表格排版撑坏。
 * 参数：
 * - `value`：原始文本。
 * - `preserveLineBreaks`：是否保留换行。
 * 返回值：
 * - 清洗后的安全展示文本。
 */
export const sanitizeDisplayText = (value: string, preserveLineBreaks = false): string => {
  const withoutControlChars = value.replace(
    preserveLineBreaks ? /[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]/g : /[\u0000-\u001F\u007F]/g,
    ' ',
  )

  return preserveLineBreaks
    ? withoutControlChars.replace(/[^\S\r\n]+/g, ' ').trim()
    : withoutControlChars.replace(/\s+/g, ' ').trim()
}

/**
 * 方法效果：
 * 将日期值格式化为本地时间字符串，并按数据完整度智能截断：
 * - 时分秒全为 0（即该字段只记录到日）时，只返回 `YYYY-MM-DD`；
 * - 否则返回完整的 `YYYY-MM-DD HH:mm:ss`。
 * 这样后端统一全量输出时间、前端按数据实际情况展示，既不丢失时间数据，
 * 也不会把「只记录到日」的字段显示成带 `00:00:00` 的长串。
 * 参数：
 * - `value`：可被 `Date` 识别的日期值。
 * - `fallback`：无效日期时的兜底展示文案。
 * 返回值：
 * - `YYYY-MM-DD` 或 `YYYY-MM-DD HH:mm:ss` 格式字符串；若无效则返回 fallback。
 */
export const formatDateTime = (value: unknown, fallback = DEFAULT_FALLBACK): string => {
  if (isEmptyDisplayValue(value)) {
    return fallback
  }

  const normalizedValue =
    typeof value === 'string'
      ? value.trim().replace('T', ' ').replace(/\.\d+(?=(Z|[+-]\d{2}:\d{2})?$)/, '')
      : value

  const date = normalizedValue instanceof Date ? normalizedValue : new Date(String(normalizedValue))

  if (Number.isNaN(date.getTime())) {
    return fallback
  }

  const pad = (part: number) => String(part).padStart(2, '0')
  const datePart = [
    date.getFullYear(),
    pad(date.getMonth() + 1),
    pad(date.getDate()),
  ].join('-')

  // 时分秒全为 0 视为「只记录到日」，不再拼接时间部分，避免显示 00:00:00
  if (date.getHours() === 0 && date.getMinutes() === 0 && date.getSeconds() === 0) {
    return datePart
  }

  return `${datePart} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

/**
 * 方法效果：
 * 将日期值强制格式化为只含年月日的 `YYYY-MM-DD` 字符串，
 * 适用于「只需要展示年月日」的字段（如生日、有效期等），无论时间部分是否有值都丢弃。
 * 参数：
 * - `value`：可被 `Date` 识别的日期值。
 * - `fallback`：无效日期时的兜底展示文案。
 * 返回值：
 * - `YYYY-MM-DD` 格式字符串；若无效则返回 fallback。
 */
export const formatDate = (value: unknown, fallback = DEFAULT_FALLBACK): string => {
  if (isEmptyDisplayValue(value)) {
    return fallback
  }

  const normalizedValue =
    typeof value === 'string'
      ? value.trim().replace('T', ' ').replace(/\.\d+(?=(Z|[+-]\d{2}:\d{2})?$)/, '')
      : value

  const date = normalizedValue instanceof Date ? normalizedValue : new Date(String(normalizedValue))

  if (Number.isNaN(date.getTime())) {
    return fallback
  }

  const pad = (part: number) => String(part).padStart(2, '0')

  return [
    date.getFullYear(),
    pad(date.getMonth() + 1),
    pad(date.getDate()),
  ].join('-')
}

/**
 * 方法效果：
 * 统一格式化展示值，自动处理空值、数组、日期和特殊字符。
 * 参数：
 * - `value`：原始值。
 * - `options`：格式化配置。
 * 返回值：
 * - 适合直接渲染到表格或描述文本中的字符串。
 */
export const formatDisplayValue = (
  value: unknown,
  options: FormatDisplayValueOptions = {},
): string => {
  const fallback = options.fallback ?? DEFAULT_FALLBACK

  if (isEmptyDisplayValue(value)) {
    return fallback
  }

  if (value instanceof Date) {
    return formatDateTime(value, fallback)
  }

  if (Array.isArray(value)) {
    const formattedItems: string[] = value
      .map((item): string => formatDisplayValue(item, options))
      .filter((item) => item !== fallback)

    return formattedItems.length > 0 ? formattedItems.join(', ') : fallback
  }

  if (typeof value === 'number' || typeof value === 'boolean') {
    return String(value)
  }

  if (typeof value === 'object') {
    return sanitizeDisplayText(JSON.stringify(value), options.preserveLineBreaks)
  }

  return sanitizeDisplayText(String(value), options.preserveLineBreaks) || fallback
}

/**
 * 方法效果：
 * 把字节数格式化为可读容量文本（B / KB / MB / GB / TB），保留两位小数。
 * 参数：
 * - `bytes`：字节数。
 * - `fallback`：非法值时的兜底文案。
 * 返回值：
 * - 如 `1.50 GB`；非法值返回 fallback。
 */
export const formatFileSize = (bytes: unknown, fallback = DEFAULT_FALLBACK): string => {
  const value = Number(bytes)
  if (!Number.isFinite(value) || value < 0) {
    return fallback
  }

  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let index = 0
  let size = value
  while (size >= 1024 && index < units.length - 1) {
    size /= 1024
    index += 1
  }

  // 字节整数展示，其余保留两位小数
  const formatted = index === 0 ? String(size) : size.toFixed(2)
  return `${formatted} ${units[index]}`
}

/**
 * 方法效果：
 * 把秒数格式化为「X天X小时X分X秒」的可读时长。
 * 参数：
 * - `seconds`：秒数。
 * - `fallback`：非法值时的兜底文案。
 * 返回值：
 * - 如 `3天5小时12分30秒`；不足一天只显示小时级以下；非法值返回 fallback。
 */
export const formatDuration = (seconds: unknown, fallback = DEFAULT_FALLBACK): string => {
  const total = Number(seconds)
  if (!Number.isFinite(total) || total < 0) {
    return fallback
  }

  const value = Math.floor(total)
  const days = Math.floor(value / 86400)
  const hours = Math.floor((value % 86400) / 3600)
  const minutes = Math.floor((value % 3600) / 60)
  const secs = value % 60

  const parts: string[] = []
  if (days > 0) {
    parts.push(`${days}天`)
  }
  if (hours > 0 || days > 0) {
    parts.push(`${hours}小时`)
  }
  if (minutes > 0 || hours > 0 || days > 0) {
    parts.push(`${minutes}分`)
  }
  parts.push(`${secs}秒`)
  return parts.join('')
}
