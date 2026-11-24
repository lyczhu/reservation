import { request } from '@reservation/shared'

export interface TimeInterval {
  start: string
  end: string
}

export interface BusinessDay {
  weekday: number
  label: string
  intervals: TimeInterval[]
}

export interface StoreInfo {
  name: string
  logo: string | null
  banners: string[]
  address: string | null
  lat: number | null
  lng: number | null
  phone: string | null
  announcement: string | null
  businessHours: BusinessDay[]
  restDays: number[]
}

export interface Category {
  id: number
  name: string
  icon: string | null
}

export interface ServiceCard {
  id: number
  categoryId: number
  name: string
  cover: string
  durationMin: number
  priceCents: number
  bookedCount: number
}

export interface TechnicianCard {
  id: number
  name: string
  avatar: string | null
  title: string | null
}

export interface ServiceDetail {
  id: number
  categoryId: number
  categoryName: string
  name: string
  cover: string
  images: string[]
  durationMin: number
  priceCents: number
  description: string | null
  notes: string | null
  bookedCount: number
  technicians: TechnicianCard[]
}

export interface StoreHome {
  store: StoreInfo
  categories: Category[]
  hotServices: ServiceCard[]
  bookableDays: number
  notice: string | null
}

export function getStoreHome(): Promise<StoreHome> {
  return request<StoreHome>({ url: '/c/store/home' })
}

export function getServices(params: { categoryId?: number; sort?: string } = {}): Promise<ServiceCard[]> {
  const query: string[] = []
  if (params.categoryId) query.push(`categoryId=${params.categoryId}`)
  if (params.sort) query.push(`sort=${params.sort}`)
  return request<ServiceCard[]>({ url: `/c/services${query.length ? `?${query.join('&')}` : ''}` })
}

export function getServiceDetail(id: number): Promise<ServiceDetail> {
  return request<ServiceDetail>({ url: `/c/services/${id}` })
}
