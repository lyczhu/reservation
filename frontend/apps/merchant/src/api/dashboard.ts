import { STAFF_TOKEN_KEY, request } from '@reservation/shared'

export interface DashboardTechnician {
  id: number
  name: string
  avatar: string | null
}

export interface DashboardBlock {
  id: number
  no: string
  technicianId: number
  serviceName: string
  startTime: string
  endTime: string
  status: number
  statusText: string
  conflict: boolean
}

export interface DashboardStats {
  total: number
  waitConfirm: number
  waitService: number
  completed: number
  revenueCents: number
}

export interface DashboardData {
  date: string
  businessHours: Array<{ start: string; end: string }>
  slotStarts: string[]
  technicians: DashboardTechnician[]
  reservations: DashboardBlock[]
  stats: DashboardStats
}

export function getDashboard(date?: string): Promise<DashboardData> {
  return request<DashboardData>({
    url: `/b/dashboard${date ? `?date=${date}` : ''}`,
    tokenKey: STAFF_TOKEN_KEY
  })
}
