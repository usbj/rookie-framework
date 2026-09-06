/**
 * 文件作用：
 * 集中管理文件上传/下载相关的后端接口，
 * 包括 multipart 上传与按存储名流式下载（blob）。
 * 轻量文件模块不落库：上传拿到存储名后，下载凭存储名取文件。
 */
import { getBlob, post } from '@/utils/http'
import type { ApiResult } from '@/types/api/system/common'
import type { FileUploadResult } from '@/types/api/system/file'

/**
 * 方法效果：
 * 以 multipart/form-data 上传文件，返回存储名等元信息。
 * 参数：
 * - `file`：待上传的文件对象（浏览器 File）。
 * 返回值：
 * - 后端 Result 包裹的上传结果（storedName / originalName / size / ext）。
 * 说明：
 * - 实例默认 `Content-Type: application/json` 会导致 axios 把 FormData JSON 序列化
 *   （transformRequest 检测到 application/json 即走 formDataToJSON），
 *   因此必须显式置 undefined 让浏览器自动设置带 boundary 的 multipart 头。
 */
export const uploadFileApi = (file: File) => {
  const formData = new FormData()
  formData.append('file', file)
  return post<ApiResult<FileUploadResult>>('/sys/file/upload', formData, {
    headers: { 'Content-Type': undefined },
  })
}

/**
 * 方法效果：
 * 触发浏览器保存已上传的文件（blob 流式下载，携带 Token 请求头）。
 * 参数：
 * - `storedName`：上传接口返回的存储名。
 * - `originalName`：可选，保存时的展示文件名（缺省用存储名）。
 * 返回值：
 * - 无返回值；副作用是触发浏览器下载。
 */
export const downloadFileApi = async (storedName: string, originalName?: string) => {
  const blob = await getBlob<Blob>(`/sys/file/download/${encodeURIComponent(storedName)}`, {
    params: originalName ? { originalName } : undefined,
  })
  saveBlobAsFile(blob, originalName || storedName)
}

/**
 * 方法效果：
 * 把二进制数据保存为本地文件（创建临时 objectURL 并模拟点击下载）。
 * 参数：
 * - `blob`：文件二进制数据。
 * - `fileName`：保存文件名。
 * 返回值：
 * - 无返回值；副作用是触发浏览器下载并释放临时 URL。
 */
export const saveBlobAsFile = (blob: Blob, fileName: string) => {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  link.click()
  // 延迟释放：部分浏览器（如 Firefox）在 click 后立即 revoke 会导致下载失败
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
