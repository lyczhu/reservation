import { STAFF_TOKEN_KEY, request } from '@reservation/shared'

export interface StaffReservationItem {
  id: number
  no: string
  status: number
  statusText: string
  source: number
  sourceText: string
  serviceId: number
  serviceName: string
  technicianId: number
  technicianName: string
  date: string
  startTime: string
  endTime: string
  priceCents: number
  contactName: string
  contactPhone: string
}

export interface StaffTimelineItem {
  action: string
  actionText: string
  operatorRole: number
  detail: string | null
  createdAt: string
}

export interface StaffReservationDetail extends StaffReservationItem {
  durationMin: number
  remark: string | null
  verifyCode: string | null
  cancelReason: string | null
  createdAt: string
  confirmedAt: string | null
  completedAt: string | null
  cancelledAt: string | null
  timeline: StaffTimelineItem[]
}

export interface ReservationQuery {
  from?: string
  to?: string
  technicianId?: number
  status?: number
  phone?: string
}

export function listReservations(query: ReservationQuery): Promise<StaffReservationItem[]> {
  const params = Object.entries(query)
    .filter(([, value]) => value !== undefined && value !== null && value !== '')
    .map(([key, value]) => `${key}=${encodeURIComponent(String(value))}`)
    .join('&')
  return request<StaffReservationItem[]>({
    url: `/b/reservations${params ? `?${params}` : ''}`,
    tokenKey: STAFF_TOKEN_KEY
  })
}

export function getReservation(id: number): Promise<StaffReservationDetail> {
  return request<StaffReservationDetail>({ url: `/b/reservations/${id}`, tokenKey: STAFF_TOKEN_KEY })
}

export function confirmReservation(id: number): Promise<StaffReservationDetail> {
  return request<StaffReservationDetail>({
    url: `/b/reservations/${id}/confirm`,
    method: 'POST',
    tokenKey: STAFF_TOKEN_KEY
  })
}

export function rejectReservation(id: number, reason: string): Promise<StaffReservationDetail> {
  return request<StaffReservationDetail>({
    url: `/b/reservations/${id}/reject`,
    method: 'POST',
    data: { reason },
    tokenKey: STAFF_TOKEN_KEY
  })
}

export function cancelReservation(id: number, reason: string): Promise<StaffReservationDetail> {
  return request<StaffReservationDetail>({
    url: `/b/reservations/${id}/cancel`,
    method: 'POST',
    data: { reason },
    tokenKey: STAFF_TOKEN_KEY
  })
}

export function noShowReservation(id: number, reason: string): Promise<StaffReservationDetail> {
  return request<StaffReservationDetail>({
    url: `/b/reservations/${id}/no-show`,
    method: 'POST',
    data: { reason },
    tokenKey: STAFF_TOKEN_KEY
  })
}

export function rescheduleReservation(
  id: number,
  date: string,
  startTime: string,
  technicianId?: number
): Promise<StaffReservationDetail> {
  return request<StaffReservationDetail>({
    url: `/b/reservations/${id}/reschedule`,
    method: 'POST',
    data: { date, startTime, technicianId },
    tokenKey: STAFF_TOKEN_KEY
  })
}

/** 查看完整手机号（店长，写审计）。 */
export function fullPhone(id: number): Promise<string> {
  return request<string>({ url: `/b/reservations/${id}/phone`, tokenKey: STAFF_TOKEN_KEY })
}

export interface ProxyBookingPayload {
  serviceId: number
  technicianId?: number | null
  date: string
  startTime: string
  contactName: string
  contactPhone: string
  remark?: string
  idempotencyKey: string
}

export interface ProxyBookingResult {
  id: number
  no: string
  verifyCode: string
  status: number
  statusText: string
  serviceName: string
  technicianName: string
  date: string
  startTime: string
  endTime: string
  priceCents: number
}

/** 代客预约（店长）。 */
export function createProxyReservation(payload: ProxyBookingPayload): Promise<ProxyBookingResult> {
  return request<ProxyBookingResult>({
    url: '/b/reservations',
    method: 'POST',
    data: { ...payload },
    tokenKey: STAFF_TOKEN_KEY
  })
}
