import { STAFF_TOKEN_KEY, request } from '@reservation/shared'

export interface ServiceItem {
  id: number
  categoryId: number
  name: string
  cover: string | null
  durationMin: number
  priceCents: number
  status: number
  technicianIds: number[]
}

export function listServices(): Promise<ServiceItem[]> {
  return request<ServiceItem[]>({ url: '/b/services', tokenKey: STAFF_TOKEN_KEY })
}
