import { STAFF_TOKEN_KEY, request } from '@reservation/shared'

export interface TemplateCount {
  key: string
  count: number
}

/** 商家端订阅授权上报：店长维度累计「新预约通知」配额。 */
export function reportStaffSubscribe(templates: TemplateCount[]): Promise<void> {
  return request<void>({
    url: '/subscribe/auth',
    method: 'POST',
    data: { templates },
    tokenKey: STAFF_TOKEN_KEY
  })
}
