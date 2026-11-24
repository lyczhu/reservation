import { USER_TOKEN_KEY, request } from '@reservation/shared'

export interface Slot {
  startTime: string
  endTime: string
  state: 'available' | 'full' | 'past' | 'rest'
}

export interface SlotResult {
  serviceId: number
  date: string
  durationMin: number
  granularity: number
  minLeadMinutes: number
  slots: Slot[]
}

export interface SubmitPayload {
  serviceId: number
  technicianId: number | null
  date: string
  startTime: string
  contactName: string
  contactPhone: string
  remark?: string
  idempotencyKey: string
}

export interface SubmitResult {
  id: number
  no: string
  verifyCode: string
  qrContent: string
  status: number
  statusText: string
  serviceId: number
  serviceName: string
  technicianId: number
  technicianName: string
  date: string
  startTime: string
  endTime: string
  priceCents: number
}

export function getSlots(serviceId: number, date: string, technicianId?: number): Promise<SlotResult> {
  const query = [`serviceId=${serviceId}`, `date=${date}`]
  if (technicianId) query.push(`technicianId=${technicianId}`)
  return request<SlotResult>({ url: `/c/slots?${query.join('&')}` })
}

export function submitReservation(payload: SubmitPayload): Promise<SubmitResult> {
  return request<SubmitResult>({
    url: '/c/reservations',
    method: 'POST',
    data: payload as unknown as Record<string, unknown>,
    tokenKey: USER_TOKEN_KEY
  })
}

/** 幂等键：小程序无 crypto.randomUUID，时间戳 + 随机串控制在 36 字符内。 */
export function createIdempotencyKey(): string {
  return `k${Date.now()}${Math.random().toString(16).slice(2, 10)}`
}
