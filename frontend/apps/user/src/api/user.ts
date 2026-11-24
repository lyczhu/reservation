import { USER_TOKEN_KEY, request } from '@reservation/shared'

export interface UserProfile {
  userId: number
  nickname: string
  avatar: string | null
  phone: string | null
}

/** 当前登录用户资料（昵称/头像/手机号）。 */
export function getUserProfile(): Promise<UserProfile> {
  return request<UserProfile>({ url: '/c/user/profile', tokenKey: USER_TOKEN_KEY })
}

/** 绑定手机号：小程序传 getPhoneNumber 的 code，dev 环境直接传 11 位手机号。 */
export function bindUserPhone(code: string): Promise<string> {
  return request<string>({
    url: '/auth/user/phone',
    method: 'POST',
    data: { code },
    tokenKey: USER_TOKEN_KEY
  })
}
