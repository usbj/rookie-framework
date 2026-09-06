/**
 * 文件作用：
 * 定义文件上传/下载模块的接口类型，与后端 FileUploadVo 字段保持一致。
 * 轻量文件模块不落库：上传返回存储名，凭存储名走下载接口取文件。
 */

/**
 * 文件上传结果，与后端 FileUploadVo 对齐。
 */
export interface FileUploadResult {
  /** 存储名（UUID + 原扩展名，如 a1b2c3.png），下载接口按此名取文件 */
  storedName: string
  /** 原始文件名（仅用于展示与下载命名，不参与存储定位） */
  originalName: string
  /** 文件大小（字节） */
  size: number
  /** 小写扩展名（无扩展名时为空字符串） */
  ext: string
}
