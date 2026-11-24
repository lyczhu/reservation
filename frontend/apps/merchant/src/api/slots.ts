import { request } from '@reservation/shared'

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

/** 时段可用性（匿名接口，商家端改期直接复用）。 */
export function getSlots(serviceId: number, date: string, technicianId?: number): Promise<SlotResult> {
  const query = technicianId ? `&technicianId=${technicianId}` : ''
  return request<SlotResult>({ url: `/c/slots?serviceId=${serviceId}&date=${date}${query}` })
}
