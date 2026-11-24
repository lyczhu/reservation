import { request } from '@reservation/shared'

export interface UserLoginResult {
  token: string
  userId: number
  nickname: string
  avatar: string | null
  phoneBound: boolean
}

export function userLogin(code: string): Promise<UserLoginResult> {
  return request<UserLoginResult>({ url: '/auth/user/login', method: 'POST', data: { code } })
}
