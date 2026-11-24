import { API_BASE_URL } from './constants'
import type { ApiResult } from './types'

/** 与后端 ErrorCode.UNAUTHORIZED 对应：未登录或登录已失效 */
export const UNAUTHORIZED_CODE = 40101

/** 与 uni.request / 微信小程序 wx.request 支持的 method 集合保持一致，不含 PATCH。 */
export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'DELETE'

export interface RequestOptions {
  url: string
  method?: HttpMethod
  data?: Record<string, unknown>
  /** 登录态存储键：用户端传 USER_TOKEN_KEY，商家端传 STAFF_TOKEN_KEY */
  tokenKey?: string
}

type AuthRefresher = () => Promise<void>

const authRefreshers: Record<string, AuthRefresher> = {}
const authRefreshing: Record<string, Promise<void> | undefined> = {}

/**
 * 注册登录态刷新逻辑：request 收到 40101 时清除该 tokenKey 并调用 refresher，
 * 随后自动重试一次原请求（用户端为静默重登，商家端为回登录页）。
 */
export function registerAuthRefresher(tokenKey: string, refresher: AuthRefresher): void {
  authRefreshers[tokenKey] = refresher
}

function send<T>(options: RequestOptions): Promise<ApiResult<T> | null> {
  return new Promise((resolve, reject) => {
    const token = options.tokenKey ? String(uni.getStorageSync(options.tokenKey) || '') : ''
    uni.request({
      url: `${API_BASE_URL}${options.url}`,
      method: options.method ?? 'GET',
      data: options.data,
      header: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {})
      },
      success: (res) => {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          resolve((res.data as ApiResult<T>) ?? null)
          return
        }
        reject(new Error(`请求失败(${res.statusCode})`))
      },
      fail: (err) => reject(new Error(err.errMsg || '网络异常'))
    })
  })
}

/**
 * uni.request 封装：拼接 baseURL、按 tokenKey 注入登录态、统一按 Result.code 解包；
 * 业务错误（code ≠ 0）以 Error(message) 抛出，由页面 catch 提示。
 */
export async function request<T>(options: RequestOptions): Promise<T> {
  let body = await send<T>(options)
  const tokenKey = options.tokenKey
  if (body && body.code === UNAUTHORIZED_CODE && tokenKey && authRefreshers[tokenKey]) {
    uni.removeStorageSync(tokenKey)
    // 并发请求同时 401 时共享同一次刷新，避免重复登录
    if (!authRefreshing[tokenKey]) {
      authRefreshing[tokenKey] = authRefreshers[tokenKey]().finally(() => {
        authRefreshing[tokenKey] = undefined
      })
    }
    await authRefreshing[tokenKey]
    body = await send<T>(options)
  }
  if (body && body.code === 0) {
    return body.data
  }
  throw new Error(body?.message || '请求失败')
}
