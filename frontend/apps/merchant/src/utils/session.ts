import { STAFF_TOKEN_KEY, registerAuthRefresher } from '@reservation/shared'

export interface StaffLoginInfo {
  token: string
  role: string
  staffId: number
  technicianId: number | null
}

const STAFF_INFO_KEY = 'reservation_staff_info'
const LOGIN_PAGE = '/pages/login/index'

export function isStaffLogin(): boolean {
  return Boolean(uni.getStorageSync(STAFF_TOKEN_KEY))
}

export function saveStaffLogin(info: StaffLoginInfo): void {
  uni.setStorageSync(STAFF_TOKEN_KEY, info.token)
  uni.setStorageSync(STAFF_INFO_KEY, info)
}

export function staffInfo(): StaffLoginInfo | null {
  return (uni.getStorageSync(STAFF_INFO_KEY) as StaffLoginInfo) || null
}

export function isOwner(): boolean {
  return staffInfo()?.role === 'owner'
}

export function clearStaffLogin(): void {
  uni.removeStorageSync(STAFF_TOKEN_KEY)
  uni.removeStorageSync(STAFF_INFO_KEY)
}

/** 未登录时跳转登录页，返回是否已登录。 */
export function requireStaffLogin(): boolean {
  if (isStaffLogin()) {
    return true
  }
  uni.reLaunch({ url: LOGIN_PAGE })
  return false
}

/** 接口返回登录失效时清理本地登录态并回登录页，返回是否已处理。 */
export function handleAuthError(e: unknown): boolean {
  if (e instanceof Error && (e.message.includes('未登录') || e.message.includes('登录已失效'))) {
    clearStaffLogin()
    uni.reLaunch({ url: LOGIN_PAGE })
    return true
  }
  return false
}

/** 任意接口登录失效时清登录态并回登录页，不依赖各页面逐个处理。 */
registerAuthRefresher(STAFF_TOKEN_KEY, async () => {
  clearStaffLogin()
  uni.reLaunch({ url: LOGIN_PAGE })
})
