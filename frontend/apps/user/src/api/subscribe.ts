import { USER_TOKEN_KEY, request } from '@reservation/shared'

export interface TemplateCount {
  key: string
  count: number
}

/** 授权上报：requestSubscribeMessage 接受后累计配额。 */
export function reportSubscribeAuth(templates: TemplateCount[]): Promise<void> {
  return request<void>({
    url: '/subscribe/auth',
    method: 'POST',
    data: { templates },
    tokenKey: USER_TOKEN_KEY
  })
}
