import { USER_TOKEN_KEY, request } from '@reservation/shared'

export interface ReservationItem {
  id: number
  no: string
  status: number
  statusText: string
  serviceName: string
  serviceCover: string | null
  technicianName: string
  date: string
  startTime: string
  endTime: string
  priceCents: number
  canCancel: boolean
  canReschedule: boolean
}

export interface TimelineItem {
  action: string
  actionText: string
  operatorRole: number
  detail: string | null
  createdAt: string
}

export interface ReservationDetail {
  id: number
  no: string
  status: number
  statusText: string
  serviceId: number
  serviceName: string
  serviceCover: string | null
  technicianId: number
  technicianName: string
  technicianAvatar: string | null
  date: string
  startTime: string
  endTime: string
  durationMin: number
  priceCents: number
  contactName: string
  contactPhone: string
  remark: string | null
  verifyCode: string | null
  qrContent: string | null
  cancelReason: string | null
  createdAt: string
  canCancel: boolean
  canReschedule: boolean
  timeline: TimelineItem[]
}

export function listReservations(status?: number): Promise<ReservationItem[]> {
  return request<ReservationItem[]>({
    url: `/c/reservations${status ? `?status=${status}` : ''}`,
    tokenKey: USER_TOKEN_KEY
  })
}

export function getReservation(id: number): Promise<ReservationDetail> {
  return request<ReservationDetail>({ url: `/c/reservations/${id}`, tokenKey: USER_TOKEN_KEY })
}

/** 取消原因走查询参数：后端接口无请求体，避免 Content-Type 不匹配。 */
export function cancelReservation(id: number, reason?: string): Promise<ReservationDetail> {
  const query = reason ? `?reason=${encodeURIComponent(reason)}` : ''
  return request<ReservationDetail>({
    url: `/c/reservations/${id}/cancel${query}`,
    method: 'POST',
    tokenKey: USER_TOKEN_KEY
  })
}

export function rescheduleReservation(id: number, date: string, startTime: string): Promise<ReservationDetail> {
  return request<ReservationDetail>({
    url: `/c/reservations/${id}/reschedule`,
    method: 'POST',
    data: { date, startTime },
    tokenKey: USER_TOKEN_KEY
  })
}
