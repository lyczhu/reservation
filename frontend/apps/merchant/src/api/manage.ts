import { STAFF_TOKEN_KEY, request } from '@reservation/shared'

export interface TimeRange {
  start: string
  end: string
}

/** 管理接口通用：店长专属，token 均走 staff 登录态。 */
const KEY = STAFF_TOKEN_KEY

// ===== 服务管理 =====
export interface ManagedService {
  id: number
  categoryId: number
  name: string
  cover: string
  images: string[]
  durationMin: number
  priceCents: number
  description: string
  notes: string
  sort: number
  status: number
  bookedCount: number
  technicianIds: number[]
}

export interface ServicePayload {
  categoryId: number
  name: string
  cover: string
  images: string[]
  durationMin: number
  priceCents: number
  description: string
  notes: string
  sort: number
  technicianIds: number[]
}

export function listManagedServices(): Promise<ManagedService[]> {
  return request<ManagedService[]>({ url: '/b/services', tokenKey: KEY })
}

export function createService(payload: ServicePayload): Promise<ManagedService> {
  return request<ManagedService>({ url: '/b/services', method: 'POST', data: { ...payload }, tokenKey: KEY })
}

export function updateService(id: number, payload: ServicePayload): Promise<void> {
  return request<void>({ url: `/b/services/${id}`, method: 'PUT', data: { ...payload }, tokenKey: KEY })
}

export function updateServiceStatus(id: number, status: number): Promise<void> {
  return request<void>({ url: `/b/services/${id}/status`, method: 'PUT', data: { status }, tokenKey: KEY })
}

export function deleteService(id: number): Promise<void> {
  return request<void>({ url: `/b/services/${id}`, method: 'DELETE', tokenKey: KEY })
}

export function sortServices(ids: number[]): Promise<void> {
  return request<void>({ url: '/b/services/sort', method: 'PUT', data: { ids }, tokenKey: KEY })
}

// ===== 分类管理 =====
export interface Category {
  id: number
  name: string
  icon: string
  sort: number
  status: number
}

export function listCategories(): Promise<Category[]> {
  return request<Category[]>({ url: '/b/categories', tokenKey: KEY })
}

export function createCategory(payload: { name: string; icon: string; sort?: number }): Promise<Category> {
  return request<Category>({ url: '/b/categories', method: 'POST', data: { ...payload }, tokenKey: KEY })
}

export function updateCategory(id: number, payload: { name: string; icon: string; sort?: number }): Promise<void> {
  return request<void>({ url: `/b/categories/${id}`, method: 'PUT', data: { ...payload }, tokenKey: KEY })
}

export function updateCategoryStatus(id: number, status: number): Promise<void> {
  return request<void>({ url: `/b/categories/${id}/status`, method: 'PUT', data: { status }, tokenKey: KEY })
}

export function deleteCategory(id: number): Promise<void> {
  return request<void>({ url: `/b/categories/${id}`, method: 'DELETE', tokenKey: KEY })
}

export function sortCategories(ids: number[]): Promise<void> {
  return request<void>({ url: '/b/categories/sort', method: 'PUT', data: { ids }, tokenKey: KEY })
}

// ===== 技师管理 =====
export interface ManagedTechnician {
  id: number
  name: string
  avatar: string
  title: string
  intro: string
  status: number
  services: string[]
  accountUsername: string | null
}

export interface TechnicianPayload {
  name: string
  avatar: string
  title: string
  intro: string
  accountUsername?: string
  accountPassword?: string
}

export function listManagedTechnicians(): Promise<ManagedTechnician[]> {
  return request<ManagedTechnician[]>({ url: '/b/technicians', tokenKey: KEY })
}

export function createTechnician(payload: TechnicianPayload): Promise<ManagedTechnician> {
  return request<ManagedTechnician>({ url: '/b/technicians', method: 'POST', data: { ...payload }, tokenKey: KEY })
}

export function updateTechnician(id: number, payload: TechnicianPayload): Promise<void> {
  return request<void>({ url: `/b/technicians/${id}`, method: 'PUT', data: { ...payload }, tokenKey: KEY })
}

export function updateTechnicianStatus(id: number, status: number): Promise<void> {
  return request<void>({ url: `/b/technicians/${id}/status`, method: 'PUT', data: { status }, tokenKey: KEY })
}

// ===== 排班 =====
export interface ShiftEntry {
  scope: 'week' | 'date'
  weekday?: number
  date?: string
  type: 'work' | 'rest'
  intervals: TimeRange[]
}

export interface TechnicianShifts {
  technicianId: number
  weekTemplate: ShiftEntry[]
  overrides: ShiftEntry[]
}

export function getTechnicianShifts(id: number): Promise<TechnicianShifts> {
  return request<TechnicianShifts>({ url: `/b/technicians/${id}/shifts`, tokenKey: KEY })
}

export function updateTechnicianShifts(id: number, entries: ShiftEntry[]): Promise<void> {
  return request<void>({ url: `/b/technicians/${id}/shifts`, method: 'PUT', data: { entries }, tokenKey: KEY })
}

// ===== 门店与规则 =====
export interface StoreInfo {
  id: number
  name: string
  logo: string
  banner: string[]
  address: string
  lat: number | null
  lng: number | null
  phone: string
  businessHours: Record<string, TimeRange[]>
  announcement: string
  status: number
}

export interface StorePayload {
  name: string
  logo: string
  banner: string[]
  address: string
  lat: number | null
  lng: number | null
  phone: string
  businessHours: Record<string, TimeRange[]>
  announcement: string
}

export function getStore(): Promise<StoreInfo> {
  return request<StoreInfo>({ url: '/b/store', tokenKey: KEY })
}

export function updateStore(payload: StorePayload): Promise<void> {
  return request<void>({ url: '/b/store', method: 'PUT', data: { ...payload }, tokenKey: KEY })
}

export interface RuleConfig {
  slotGranularity: number
  bookableDays: number
  minLeadMinutes: number
  cancelDeadlineHours: number
  rescheduleDeadlineHours: number
  needConfirm: number
  maxConcurrent: number
  bufferMinutes: number
  reminderHours: number
}

export function getRules(): Promise<RuleConfig> {
  return request<RuleConfig>({ url: '/b/rules', tokenKey: KEY })
}

export function updateRules(payload: RuleConfig): Promise<void> {
  return request<void>({ url: '/b/rules', method: 'PUT', data: { ...payload }, tokenKey: KEY })
}

// ===== 账号管理 =====
export interface StaffAccount {
  id: number
  username: string
  role: number
  roleText: string
  technicianId: number | null
  technicianName: string | null
  wxBound: boolean
  status: number
  statusText: string
  lastLoginAt: string | null
  createdAt: string
}

export function listAccounts(): Promise<StaffAccount[]> {
  return request<StaffAccount[]>({ url: '/b/accounts', tokenKey: KEY })
}

export function createAccount(payload: { username: string; password: string; technicianId: number }): Promise<StaffAccount> {
  return request<StaffAccount>({ url: '/b/accounts', method: 'POST', data: { ...payload }, tokenKey: KEY })
}

export function updateAccountStatus(id: number, status: number): Promise<void> {
  return request<void>({ url: `/b/accounts/${id}/status`, method: 'PUT', data: { status }, tokenKey: KEY })
}

export function resetAccountPassword(id: number, password: string): Promise<void> {
  return request<void>({ url: `/b/accounts/${id}/reset-password`, method: 'POST', data: { password }, tokenKey: KEY })
}

export function unbindAccountWx(id: number): Promise<void> {
  return request<void>({ url: `/b/accounts/${id}/unbind-wx`, method: 'POST', tokenKey: KEY })
}
