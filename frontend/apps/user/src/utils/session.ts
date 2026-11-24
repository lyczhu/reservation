import { USER_TOKEN_KEY, registerAuthRefresher } from '@reservation/shared'
import { userLogin, type UserLoginResult } from '../api/auth'

const USER_INFO_KEY = 'reservation_user_info'

export interface UserBriefInfo {
  userId: number
  nickname: string
  avatar: string | null
  phoneBound: boolean
}

export function saveUserLogin(result: UserLoginResult): void {
  uni.setStorageSync(USER_TOKEN_KEY, result.token)
  uni.setStorageSync(USER_INFO_KEY, {
    userId: result.userId,
    nickname: result.nickname,
    avatar: result.avatar,
    phoneBound: result.phoneBound
  })
}

export function isUserLogin(): boolean {
  return Boolean(uni.getStorageSync(USER_TOKEN_KEY))
}

export function userBrief(): UserBriefInfo | null {
  return (uni.getStorageSync(USER_INFO_KEY) as UserBriefInfo) || null
}

export function updateUserPhoneBound(bound: boolean): void {
  const brief = userBrief()
  if (brief) {
    brief.phoneBound = bound
    uni.setStorageSync(USER_INFO_KEY, brief)
  }
}

export function clearUserLogin(): void {
  uni.removeStorageSync(USER_TOKEN_KEY)
  uni.removeStorageSync(USER_INFO_KEY)
}

/** 微信一键登录（小程序取 code，H5 预览用时间戳配合后端 dev 降级）。 */
export async function doUserLogin(): Promise<UserLoginResult> {
  const code = await loginCode()
  const result = await userLogin(code)
  saveUserLogin(result)
  return result
}

/**
 * 预约提交前确保已登录：无 token 时静默登录并缓存用户信息。
 */
export async function ensureUserLogin(): Promise<void> {
  if (isUserLogin()) {
    return
  }
  await doUserLogin()
}

/** 登录失效时静默重登；request 层负责清除旧 token 并自动重试原请求。 */
registerAuthRefresher(USER_TOKEN_KEY, async () => {
  await doUserLogin()
})

function loginCode(): Promise<string> {
  // #ifdef MP-WEIXIN
  return new Promise((resolve, reject) => {
    uni.login({
      provider: 'weixin',
      success: (res) => {
        if (res.code) {
          resolve(res.code)
        } else {
          reject(new Error('微信登录失败'))
        }
      },
      fail: (err) => reject(new Error(err.errMsg || '微信登录失败'))
    })
  })
  // #endif
  // #ifndef MP-WEIXIN
  return Promise.resolve(`dev_${Date.now()}`)
  // #endif
}
