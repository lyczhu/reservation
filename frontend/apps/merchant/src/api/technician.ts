import { STAFF_TOKEN_KEY, request } from '@reservation/shared'

export interface TechnicianItem {
  id: number
  name: string
  avatar: string | null
  title: string | null
  status: number
  accountBound?: boolean
}

export function listTechnicians(): Promise<TechnicianItem[]> {
  return request<TechnicianItem[]>({ url: '/b/technicians', tokenKey: STAFF_TOKEN_KEY })
}
