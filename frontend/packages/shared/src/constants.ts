// 后端地址由构建期环境变量注入，默认指向本地开发服务；真机调试需改为局域网 IP 或已配置的合法域名
export const API_BASE_URL =
  (import.meta as unknown as { env?: Record<string, string | undefined> }).env?.VITE_API_BASE_URL ||
  'http://localhost:8080/api/v1'
// 用户端与商家端为两个独立小程序，登录态分开存储
export const USER_TOKEN_KEY = 'reservation_user_token'
export const STAFF_TOKEN_KEY = 'reservation_staff_token'
// 用户端隐私政策同意标记，仅本地存储，未同意时不允许进入预约流程
export const USER_PRIVACY_AGREED_KEY = 'reservation_privacy_agreed'