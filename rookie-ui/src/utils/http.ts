/**
 * 文件作用：
 * 统一封装前端 HTTP 请求实例、基础地址、请求头注入和通用错误处理，
 * 供所有与后端对接的接口方法复用。
 */
import axios, { AxiosError } from 'axios'
import type { AxiosInstance, AxiosRequestConfig, AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'
import { USER_INFO_STORAGE_KEY, USER_TOKEN_STORAGE_KEY } from '@/stores/user'
import type {
  ApiResult,
  NormalizedPageResult,
  RawPageInfoResult,
} from '@/types/api/system/common'

/**
 * 自定义请求配置扩展：`silent: true` 时业务错误不弹 ElMessage（心跳等高频静默请求用），
 * 401 登录失效跳转逻辑不受影响。通过模块扩展挂到 axios 配置类型上，请求方法可直接传入。
 */
declare module 'axios' {
  export interface AxiosRequestConfig {
    silent?: boolean
  }
}

const SUCCESS_CODE = 200
const AUTH_EXPIRED_CODE = 401
const DICT_CACHE_STORAGE_KEY = 'rookie-dict-cache'
let isRedirectingToLogin = false

const clearLocalAuthState = () => {
  localStorage.removeItem(USER_TOKEN_STORAGE_KEY)
  localStorage.removeItem(USER_INFO_STORAGE_KEY)
  localStorage.removeItem(DICT_CACHE_STORAGE_KEY)
}

const getCurrentRedirectPath = () => {
  const baseUrl = import.meta.env.BASE_URL || '/'
  const currentLocation = `${window.location.pathname}${window.location.search}${window.location.hash}`

  if (baseUrl !== '/' && currentLocation.startsWith(baseUrl)) {
    return currentLocation.slice(baseUrl.length - 1) || '/'
  }

  return currentLocation || '/'
}

const redirectToLogin = () => {
  if (typeof window === 'undefined' || isRedirectingToLogin) {
    return
  }

  isRedirectingToLogin = true
  clearLocalAuthState()

  const baseUrl = import.meta.env.BASE_URL || '/'
  const loginUrl = new URL(`${baseUrl}login`, window.location.origin)
  const redirectPath = getCurrentRedirectPath()

  if (redirectPath && redirectPath !== '/login') {
    loginUrl.searchParams.set('redirect', redirectPath)
  }

  window.location.replace(loginUrl.toString())
}

/**
 * 当前开发环境默认通过 /api 代理转发到后端服务，
 * 生产环境可通过 VITE_API_BASE_URL 指定真实接口地址。
 */
const http: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
})

/**
 * 请求发出前自动补充 token。
 * 当前后端过滤器从 Token 请求头里读取登录态，因此统一在这里注入。
 */
http.interceptors.request.use((config) => {
  const token = localStorage.getItem(USER_TOKEN_STORAGE_KEY)

  if (token) {
    config.headers.Token = token
  }

  return config
})

/**
 * 方法效果：
 * 统一处理后端 Result 结构中的业务错误和网络错误。
 * 成功时仍返回完整响应体，交给各 API 方法按自身类型消费。
 * 说明：
 * - `silent` 请求配置（自定义扩展字段）为 true 时，业务错误不弹 ElMessage，
 *   供心跳等高频静默请求使用；401 登录失效跳转逻辑不受影响。
 */
http.interceptors.response.use(
  (response: AxiosResponse<ApiResult<unknown>>) => {
    const payload = response.data

    if (typeof payload?.code === 'number' && payload.code !== SUCCESS_CODE) {
      const silent = Boolean(response.config.silent)

      if (payload.code === AUTH_EXPIRED_CODE) {
        if (!silent) {
          ElMessage.error(payload.msg || '登录状态已失效，请重新登录')
        }
        redirectToLogin()
        return Promise.reject(new Error(payload.msg || '登录状态已失效'))
      }

      if (!silent) {
        ElMessage.error(payload.msg || '请求失败')
      }
      return Promise.reject(new Error(payload.msg || '请求失败'))
    }

    return response
  },
  (error: AxiosError) => {
    const silent = Boolean(error.config?.silent)

    if (error.response?.status === AUTH_EXPIRED_CODE) {
      if (!silent) {
        ElMessage.error('登录状态已失效，请重新登录')
      }
      redirectToLogin()
      return Promise.reject(error)
    }

    const message =
      error.response?.data && typeof error.response.data === 'object' && 'msg' in error.response.data
        ? String(error.response.data.msg)
        : error.message || '网络请求异常'

    if (!silent) {
      ElMessage.error(message)
    }
    return Promise.reject(error)
  },
)

/**
 * 统一暴露简洁的请求方法。
 * 这里直接返回后端响应体 data，避免业务层重复取 response.data。
 */
export const request = async <T>(config: AxiosRequestConfig) => {
  const response = await http.request<T>(config)
  return response.data
}

/**
 * 方法效果：
 * 统一把后端 PageInfo 结构转换为前端更易消费的分页结果。
 * 参数：
 * - `config`：Axios 请求配置。
 * 返回值：
 * - 归一化后的分页数据，字段统一为 `records / total / pageNum / pageSize / pages`。
 */
export const requestPage = async <T>(config: AxiosRequestConfig) => {
  const response = await request<ApiResult<RawPageInfoResult<T>>>(config)
  const pageInfo = response.data

  return {
    records: pageInfo.list,
    pageNum: pageInfo.pageNum,
    pageSize: pageInfo.pageSize,
    pages: pageInfo.pages,
    total: pageInfo.total,
  } satisfies NormalizedPageResult<T>
}

export const get = <T>(url: string, config?: AxiosRequestConfig) =>
  request<T>({
    ...config,
    method: 'get',
    url,
  })

/**
 * 方法效果：
 * 以二进制流（blob）方式发起 GET 请求，用于文件下载与图片读取。
 * 参数：
 * - `url`：接口地址。
 * - `config`：可选 Axios 配置。
 * 返回值：
 * - 二进制数据（Blob）。
 * 说明：
 * - `<img>` 标签无法携带 Token 请求头，头像等图片需先用本方法取 blob 再转 objectURL 展示；
 * - 响应拦截器对 blob 不生效（Blob 无 code 字段），业务错误码由错误分支统一提示。
 */
export const getBlob = async <T = Blob>(url: string, config?: AxiosRequestConfig) => {
  const response = await http.request<T>({
    ...config,
    method: 'get',
    url,
    responseType: 'blob',
  })
  return response.data
}

export const post = <T, D = unknown>(url: string, data?: D, config?: AxiosRequestConfig<D>) =>
  request<T>({
    ...config,
    method: 'post',
    url,
    data,
  })

export const put = <T, D = unknown>(url: string, data?: D, config?: AxiosRequestConfig<D>) =>
  request<T>({
    ...config,
    method: 'put',
    url,
    data,
  })

/**
 * 方法效果：
 * 发起删除请求，供批量删除、按主键删除等接口统一复用。
 * 参数：
 * - `url`：接口地址。
 * - `config`：可选 Axios 配置。
 * 返回值：
 * - 删除接口返回的完整业务结果。
 */
export const del = <T>(url: string, config?: AxiosRequestConfig) =>
  request<T>({
    ...config,
    method: 'delete',
    url,
  })

/**
 * 方法效果：
 * 发起分页查询请求，并直接返回归一化后的分页结果。
 * 参数：
 * - `url`：接口地址。
 * - `config`：可选 Axios 配置，通常用于传入查询参数。
 * 返回值：
 * - 归一化后的分页结果。
 */
export const getPage = <T>(url: string, config?: AxiosRequestConfig) =>
  requestPage<T>({
    ...config,
    method: 'get',
    url,
  })

export default http
