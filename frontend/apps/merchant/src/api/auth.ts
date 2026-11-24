import { STAFF_TOKEN_KEY, request } from '@reservation/shared'
import type { StaffLoginInfo } from '../utils/session'

export function staffLogin(username: string, password: string): Promise<StaffLoginInfo> {
  return request<StaffLoginInfo>({
    url: '/auth/staff/login',
    method: 'POST',
    data: { username, password }
  })
}

export function staffWxLogin(code: string): Promise<StaffLoginInfo> {
  return request<StaffLoginInfo>({
    url: '/auth/staff/wx-login',
    method: 'POST',
    data: { code }
  })
}

export function staffLogout(): Promise<void> {
  return request<void>({
    url: '/auth/staff/logout',
    method: 'POST',
    tokenKey: STAFF_TOKEN_KEY
  })
}
