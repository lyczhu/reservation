import { STAFF_TOKEN_KEY, request } from '@reservation/shared'

export interface VerifyResult {
  id: number
  no: string
  status: number
  statusText: string
  serviceName: string
  technicianName: string
  date: string
  startTime: string
  endTime: string
  contactName: string
  alreadyCompleted: boolean
}

/** 核销：扫码传 qrContent，手输传 6 位 code。 */
export function verifyReservation(payload: { code?: string; qrContent?: string }): Promise<VerifyResult> {
  return request<VerifyResult>({
    url: '/b/verify',
    method: 'POST',
    data: { ...payload },
    tokenKey: STAFF_TOKEN_KEY
  })
}
