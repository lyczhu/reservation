import { STAFF_TOKEN_KEY, request, type PageResult } from '@reservation/shared'

/** 审计详情中的单个字段变更。 */
export interface AuditChange {
  field: string
  label: string
  before: string
  after: string
}

/** 当前筛选条件下的概览统计。 */
export interface AuditSummary {
  total: number
  highRisk: number
  today: number
  last7Days: number
  operatorCount: number
}

/** 审计日志列表项，对应后端 AuditLogVO。 */
export interface AuditLogItem {
  id: number
  operatorType: number
  operatorTypeText: string
  operatorId: number
  operatorName: string | null
  action: string
  actionText: string
  target: string | null
  targetText: string
  detail: string | null
  ip: string | null
  ipText: string
  createdAt: string
  category: string
  riskLevel: 'HIGH' | 'MEDIUM' | 'LOW'
  riskText: string
  summary: string
  changes: AuditChange[]
}

/** 审计分页结果 + 概览。 */
export interface AuditPage extends PageResult<AuditLogItem> {
  summary: AuditSummary
}

export interface AuditQuery {
  action?: string
  category?: string
  operatorType?: number
  operatorId?: number
  from?: string
  to?: string
  keyword?: string
  page?: number
  size?: number
}

/** 接口字段兜底：后端版本不一致或历史数据缺字段时，页面也不能因空值中断渲染。 */
function text(value: unknown, fallback: string): string {
  return typeof value === 'string' && value.trim() ? value : fallback
}

function normalizeChange(raw: Partial<AuditChange> | null | undefined): AuditChange {
  const field = text(raw?.field, '')
  return {
    field,
    label: text(raw?.label, field || '字段'),
    before: text(raw?.before, '—'),
    after: text(raw?.after, '—')
  }
}

function normalizeItem(raw: Partial<AuditLogItem> | null | undefined, index: number): AuditLogItem {
  const operatorType = Number(raw?.operatorType ?? 0)
  const action = text(raw?.action, '')
  const riskLevel = raw?.riskLevel === 'HIGH' || raw?.riskLevel === 'MEDIUM' ? raw.riskLevel : 'LOW'
  return {
    id: Number(raw?.id ?? index),
    operatorType,
    operatorTypeText: text(raw?.operatorTypeText, operatorType === 1 ? '顾客' : '商家账号'),
    operatorId: Number(raw?.operatorId ?? 0),
    operatorName: raw?.operatorName ?? null,
    action,
    actionText: text(raw?.actionText, action || '未知操作'),
    target: raw?.target ?? null,
    targetText: text(raw?.targetText, text(raw?.target, '—')),
    detail: raw?.detail ?? null,
    ip: raw?.ip ?? null,
    ipText: text(raw?.ipText, text(raw?.ip, '—')),
    createdAt: text(raw?.createdAt, ''),
    category: text(raw?.category, '其他'),
    riskLevel,
    riskText: text(raw?.riskText, riskLevel === 'HIGH' ? '高风险' : riskLevel === 'MEDIUM' ? '中风险' : '低风险'),
    summary: text(raw?.summary, text(raw?.detail, '')),
    changes: Array.isArray(raw?.changes) ? raw?.changes.map(normalizeChange) : []
  }
}

/** 统一把响应规整成可直接渲染的结构，缺 summary / changes 时给安全默认值。 */
function normalizePage(raw: Partial<AuditPage> | null | undefined): AuditPage {
  const records = Array.isArray(raw?.records) ? raw.records.map(normalizeItem) : []
  const total = Number(raw?.total ?? records.length) || 0
  const summary = raw?.summary
  return {
    records,
    total,
    current: Number(raw?.current ?? 1) || 1,
    size: Number(raw?.size ?? records.length) || records.length,
    summary: {
      total: Number(summary?.total ?? total) || 0,
      highRisk: Number(summary?.highRisk ?? 0) || 0,
      today: Number(summary?.today ?? 0) || 0,
      last7Days: Number(summary?.last7Days ?? 0) || 0,
      operatorCount: Number(summary?.operatorCount ?? 0) || 0
    }
  }
}

function queryString(query: Record<string, string | number | undefined>): string {
  const params = Object.entries(query)
    .filter(([, value]) => value !== undefined && value !== null && value !== '')
    .map(([key, value]) => `${key}=${encodeURIComponent(String(value))}`)
    .join('&')
  return params ? `?${params}` : ''
}

/** 审计日志分页查询（店长专属，参数中 page 从 1 开始）。 */
export async function listAudits(query: AuditQuery): Promise<AuditPage> {
  const page = await request<AuditPage>({
    url: `/b/audits${queryString(query)}`,
    tokenKey: STAFF_TOKEN_KEY
  })
  return normalizePage(page)
}
