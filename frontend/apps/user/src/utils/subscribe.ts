import { reportSubscribeAuth, type TemplateCount } from '../api/subscribe'

// 微信公众平台申请的一次性订阅模板 ID；未配置时小程序端跳过弹窗
const env = (import.meta as unknown as { env?: Record<string, string | undefined> }).env ?? {}
const TEMPLATE_IDS: Record<string, string> = {
  reservation_success: env.VITE_WX_TPL_SUCCESS || '',
  reservation_change: env.VITE_WX_TPL_CHANGE || '',
  reservation_reminder: env.VITE_WX_TPL_REMINDER || ''
}

/**
 * 成功页订阅引导：请求订阅三类通知并上报授权。
 * 小程序端对「同意」的模板各上报 1 次；非小程序端无 requestSubscribeMessage，按全部接受上报。
 */
export async function subscribeBookingMessages(): Promise<number> {
  let accepted: TemplateCount[] = []

  // #ifdef MP-WEIXIN
  const requested = Object.keys(TEMPLATE_IDS).filter((key) => TEMPLATE_IDS[key])
  if (requested.length) {
    const result = await new Promise<Record<string, string>>((resolve, reject) => {
      ;(uni as unknown as {
        requestSubscribeMessage: (options: {
          tmplIds: string[]
          success: (res: Record<string, string>) => void
          fail: (error: unknown) => void
        }) => void
      }).requestSubscribeMessage({
        tmplIds: requested.map((key) => TEMPLATE_IDS[key]),
        success: (res) => resolve(res),
        fail: (error) => reject(error)
      })
    })
    accepted = requested
      .filter((key) => result[key] === 'accept')
      .map((key) => ({ key, count: 1 }))
  }
  // #endif

  // #ifndef MP-WEIXIN
  accepted = Object.keys(TEMPLATE_IDS).map((key) => ({ key, count: 1 }))
  // #endif

  if (accepted.length) {
    await reportSubscribeAuth(accepted)
  }
  return accepted.length
}
