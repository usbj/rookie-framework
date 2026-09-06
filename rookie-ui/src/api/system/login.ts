/**
 * 文件作用：
 * 集中管理系统登录与注册相关的后端接口请求方法，
 * 当前先承接 /login 与 /register 的调用。
 */
import { get, post } from '@/utils/http'
import type {
  ApiResult,
  LoginRequestData,
  LoginResponseData,
  RegisterRequestData,
} from '@/types/api/system/login'

/**
 * 调用后端登录接口。
 * 当前后端返回 Result<String>，其中 data 为登录 token。
 */
export const loginApi = (data: LoginRequestData) =>
  post<ApiResult<LoginResponseData>, LoginRequestData>('/login', data)

/**
 * 调用后端注册接口。
 * 注册开关由后端系统设置 sys.user.registerEnabled 控制，关闭时后端直接拒绝。
 * 当前后端返回 Result<Boolean>，data 为是否注册成功。
 */
export const registerApi = (data: RegisterRequestData) =>
  post<ApiResult<boolean>, RegisterRequestData>('/register', data)

/**
 * 调用后端注册开关查询接口（公开接口，无需登录）。
 * 后端读取系统设置 sys.user.registerEnabled（BOOLEAN，默认 false）返回。
 * 前端注册入口/注册页据此显隐与提示；不直接读取系统设置接口。
 */
export const getRegisterEnabledApi = () => get<ApiResult<boolean>>('/register/enabled')

/**
 * 方法效果：
 * 调用后端退出登录接口（需要登录）。
 * 后端从在线集合移除当前用户并删除登录态缓存，旧 token 立即失效；
 * 前端调用后清空本地登录态并跳转登录页。
 */
export const logoutApi = () => post<ApiResult<boolean>>('/logout')
