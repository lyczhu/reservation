<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { listAudits, type AuditLogItem, type AuditSummary } from '../../api/audit'
import { formatDate } from '../../utils/format'
import { handleAuthError, isOwner, requireStaffLogin } from '../../utils/session'

const PAGE_SIZE = 20
/** 后端未返回时间时的兜底分组键，避免空值让整页渲染中断。 */
const UNKNOWN_DATE = 'unknown'

type RangeKey = 'all' | 'today' | '7d' | '30d'

interface AuditGroup {
  key: string
  label: string
  sub: string
  items: AuditLogItem[]
}

const quickRanges: { key: RangeKey; text: string }[] = [
  { key: 'all', text: '全部' },
  { key: 'today', text: '今天' },
  { key: '7d', text: '近 7 天' },
  { key: '30d', text: '近 30 天' }
]

const quickRange = ref<RangeKey>('all')
const expanded = ref<Record<number, boolean>>({})

const items = ref<AuditLogItem[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(true)
const loadingMore = ref(false)
const error = ref('')
const summary = ref<AuditSummary>({ total: 0, highRisk: 0, today: 0, last7Days: 0, operatorCount: 0 })

const hasMore = computed(() => items.value.length < total.value)

const groups = computed<AuditGroup[]>(() => {
  const byDate = new Map<string, AuditLogItem[]>()
  for (const item of items.value) {
    const key = item.createdAt ? item.createdAt.slice(0, 10) : UNKNOWN_DATE
    const bucket = byDate.get(key)
    if (bucket) {
      bucket.push(item)
    } else {
      byDate.set(key, [item])
    }
  }
  return Array.from(byDate.entries()).map(([key, bucket]) => ({
    key,
    label: dayLabel(key),
    sub: key === UNKNOWN_DATE ? '' : key,
    items: bucket
  }))
})

onShow(() => {
  if (!requireStaffLogin()) {
    return
  }
  if (!isOwner()) {
    uni.showToast({ title: '仅店长可查看操作审计', icon: 'none' })
    setTimeout(() => uni.navigateBack(), 800)
    return
  }
  load(true)
})

async function load(reset: boolean) {
  if (!reset && (loadingMore.value || !hasMore.value)) {
    return
  }
  if (reset) {
    loading.value = true
    page.value = 1
  } else {
    loadingMore.value = true
  }
  error.value = ''
  try {
    const result = await listAudits({
      ...rangeParams(),
      page: page.value,
      size: PAGE_SIZE
    })
    items.value = reset ? result.records : [...items.value, ...result.records]
    total.value = result.total
    summary.value = result.summary
  } catch (e) {
    if (handleAuthError(e)) {
      return
    }
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

/** 快捷时间范围换算成接口的 from/to（含当天）。 */
function rangeParams(): { from?: string; to?: string } {
  const today = new Date()
  if (quickRange.value === 'today') {
    return { from: formatDate(today), to: formatDate(today) }
  }
  if (quickRange.value === '7d' || quickRange.value === '30d') {
    const start = new Date()
    start.setDate(start.getDate() - (quickRange.value === '7d' ? 6 : 29))
    return { from: formatDate(start), to: formatDate(today) }
  }
  return {}
}

function chooseQuickRange(key: RangeKey) {
  if (quickRange.value === key) {
    return
  }
  quickRange.value = key
  load(true)
}

function resetRange() {
  quickRange.value = 'all'
  load(true)
}

function loadMore() {
  if (!hasMore.value || loadingMore.value) {
    return
  }
  page.value += 1
  load(false)
}

function retryLoad() {
  load(true)
}

function toggleDetail(id: number) {
  expanded.value = { ...expanded.value, [id]: !expanded.value[id] }
}

type DatasetEvent = { currentTarget: { dataset: Record<string, string | number | undefined> } }

function onQuickRangeTap(event: DatasetEvent) {
  const key = event.currentTarget.dataset.range as RangeKey | undefined
  if (key) {
    chooseQuickRange(key)
  }
}

function onCardTap(event: DatasetEvent) {
  const id = Number(event.currentTarget.dataset.id)
  if (id) {
    toggleDetail(id)
  }
}

function operatorLabel(item: AuditLogItem): string {
  const name = item.operatorName || `ID ${item.operatorId}`
  return `${item.operatorTypeText} · ${name}`
}

function timeOf(value: string): string {
  if (!value) {
    return '—'
  }
  return value.length >= 19 ? value.slice(11, 19) : value.replace('T', ' ')
}

function dayLabel(date: string): string {
  if (date === UNKNOWN_DATE) {
    return '时间未知'
  }
  const today = formatDate(new Date())
  const yesterday = new Date()
  yesterday.setDate(yesterday.getDate() - 1)
  if (date === today) {
    return '今天'
  }
  if (date === formatDate(yesterday)) {
    return '昨天'
  }
  const [, month, day] = date.split('-')
  return `${Number(month)}月${Number(day)}日`
}

function riskClass(level: string): string {
  return level === 'HIGH' ? 'high' : level === 'MEDIUM' ? 'medium' : 'low'
}
</script>

<template>
  <view class="page">
    <view class="intro">
      <view>
        <text class="intro-title">操作审计</text>
        <text class="intro-desc">记录店内的敏感操作与管理变更，仅店长可见</text>
      </view>
      <text class="intro-shield">只读</text>
    </view>

    <view class="overview">
      <view class="overview-head">
        <text class="overview-title">审计概览</text>
        <text class="overview-hint">按当前时间范围统计</text>
      </view>
      <view class="stats">
        <view class="stat">
          <text class="stat-num">{{ summary.total }}</text>
          <text class="stat-cap">记录</text>
        </view>
        <view class="stat">
          <text class="stat-num risk-high-text">{{ summary.highRisk }}</text>
          <text class="stat-cap">高风险</text>
        </view>
        <view class="stat">
          <text class="stat-num">{{ summary.today }}</text>
          <text class="stat-cap">今日</text>
        </view>
        <view class="stat">
          <text class="stat-num">{{ summary.last7Days }}</text>
          <text class="stat-cap">近 7 天</text>
        </view>
        <view class="stat">
          <text class="stat-num">{{ summary.operatorCount }}</text>
          <text class="stat-cap">操作人</text>
        </view>
      </view>
    </view>

    <view class="quick-row">
      <view
        v-for="range in quickRanges"
        :key="range.key"
        class="quick-chip"
        :class="{ active: quickRange === range.key }"
        :data-range="range.key"
        @tap="onQuickRangeTap"
      >
        {{ range.text }}
      </view>
    </view>

    <view v-if="loading" class="state">正在整理审计记录…</view>
    <view v-else-if="error" class="state error">
      <text>{{ error }}</text>
      <text class="retry" @tap="retryLoad">重新加载</text>
    </view>
    <view v-else-if="!items.length" class="state empty">
      <text class="empty-icon">记</text>
      <text class="empty-title">这个时间范围没有记录</text>
      <text class="empty-desc">切换到「全部」可以看到更早的操作记录</text>
      <text class="empty-action" @tap="resetRange">查看全部记录</text>
    </view>

    <view v-else class="list">
      <view v-for="group in groups" :key="group.key" class="group">
        <view class="group-head">
          <text class="group-dot" />
          <text class="group-label">{{ group.label }}</text>
          <text class="group-sub">{{ group.sub }}</text>
        </view>

        <view
          v-for="item in group.items"
          :key="item.id"
          class="card"
          :data-id="item.id"
          @tap="onCardTap"
        >
          <view class="card-top">
            <view class="tag-row">
              <text class="tag module">{{ item.category }}</text>
              <text class="tag" :class="riskClass(item.riskLevel)">{{ item.riskText }}</text>
            </view>
            <text class="time">{{ timeOf(item.createdAt) }}</text>
          </view>

          <view class="title-row">
            <text class="title">{{ item.actionText }}</text>
            <text class="expand">{{ expanded[item.id] ? '收起' : '详情' }} ›</text>
          </view>
          <view class="summary">{{ item.summary || '无变更详情' }}</view>

          <view class="meta">
            <view class="meta-row">
              <text class="meta-label">操作人</text>
              <text class="meta-value">{{ operatorLabel(item) }}</text>
            </view>
            <view class="meta-row">
              <text class="meta-label">操作对象</text>
              <text class="meta-value">{{ item.targetText }}</text>
            </view>
            <view class="meta-row">
              <text class="meta-label">来源</text>
              <text class="meta-value">{{ item.ipText }}</text>
            </view>
          </view>

          <view v-if="expanded[item.id]" class="detail">
            <view v-if="item.changes.length" class="change-list">
              <view v-for="(change, index) in item.changes" :key="index" class="change-row">
                <text class="change-label">{{ change.label }}</text>
                <view class="change-values">
                  <text class="before">{{ change.before }}</text>
                  <text class="arrow">→</text>
                  <text class="after">{{ change.after }}</text>
                </view>
              </view>
            </view>
            <view v-else class="raw-detail">{{ item.detail || '该操作未记录字段变更' }}</view>
            <view class="raw-row">
              <text class="raw-label">动作标识</text>
              <text class="raw-value">{{ item.action }}</text>
            </view>
            <view class="raw-row">
              <text class="raw-label">原始详情</text>
              <text class="raw-value">{{ item.detail || '—' }}</text>
            </view>
            <view class="raw-row">
              <text class="raw-label">记录编号</text>
              <text class="raw-value">#{{ item.id }}</text>
            </view>
          </view>
        </view>
      </view>

      <view v-if="hasMore" class="more" @tap="loadMore">
        {{ loadingMore ? '正在加载…' : `加载更多（已显示 ${items.length}/${total}）` }}
      </view>
      <view v-else class="end">已显示全部 {{ total }} 条记录</view>
    </view>
  </view>
</template>

<style scoped>
.page {
  min-height: 100vh;
  padding: 24rpx 32rpx 80rpx;
  background: #faf6f2;
}

.intro {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 22rpx;
  padding: 0 6rpx;
}

.intro-title {
  display: block;
  margin-bottom: 8rpx;
  font-size: 36rpx;
  font-weight: 700;
  color: #3b3330;
}

.intro-desc {
  display: block;
  font-size: 22rpx;
  color: #a08d83;
}

.intro-shield {
  padding: 6rpx 16rpx;
  border-radius: 20rpx;
  font-size: 20rpx;
  color: #8c827c;
  background: #f1ece8;
}

.overview {
  margin-bottom: 20rpx;
  padding: 26rpx 28rpx;
  border-radius: 22rpx;
  background: #ffffff;
}

.overview-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 20rpx;
}

.overview-title {
  font-size: 28rpx;
  font-weight: 600;
  color: #3b3330;
}

.overview-hint {
  font-size: 20rpx;
  color: #b09a8e;
}

.stats {
  display: flex;
  justify-content: space-between;
}

.stat {
  flex: 1;
  text-align: center;
}

.stat-num {
  display: block;
  margin-bottom: 6rpx;
  font-size: 36rpx;
  font-weight: 700;
  color: #3b3330;
}

.stat-num.risk-high-text {
  color: #c25454;
}

.stat-cap {
  font-size: 20rpx;
  color: #a08d83;
}

.quick-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  margin-bottom: 24rpx;
}

.quick-chip {
  padding: 12rpx 24rpx;
  border-radius: 30rpx;
  font-size: 23rpx;
  color: #8c827c;
  background: #f1ece8;
}

.quick-chip.active {
  color: #ffffff;
  background: #c9707c;
}

.state {
  padding: 90rpx 20rpx;
  text-align: center;
  font-size: 26rpx;
  color: #b09a8e;
}

.state.error {
  color: #c05a5a;
}

.retry {
  display: block;
  margin-top: 24rpx;
  color: #c9707c;
}

.empty-icon {
  display: block;
  width: 96rpx;
  height: 96rpx;
  margin: 0 auto 24rpx;
  border-radius: 50%;
  line-height: 96rpx;
  text-align: center;
  font-size: 36rpx;
  color: #c9707c;
  background: #f8e8e9;
}

.empty-title {
  display: block;
  margin-bottom: 10rpx;
  font-size: 28rpx;
  color: #6d605b;
}

.empty-desc {
  display: block;
  margin-bottom: 28rpx;
  font-size: 23rpx;
  color: #b09a8e;
}

.empty-action {
  display: inline-block;
  padding: 14rpx 36rpx;
  border-radius: 32rpx;
  font-size: 24rpx;
  color: #c9707c;
  background: #f8e8e9;
}

.group {
  margin-bottom: 28rpx;
}

.group-head {
  display: flex;
  align-items: center;
  padding: 0 6rpx 14rpx;
}

.group-dot {
  width: 12rpx;
  height: 12rpx;
  margin-right: 12rpx;
  border-radius: 50%;
  background: #c9707c;
}

.group-label {
  font-size: 26rpx;
  font-weight: 600;
  color: #6d605b;
}

.group-sub {
  margin-left: 14rpx;
  font-size: 20rpx;
  color: #b09a8e;
}

.card {
  margin-bottom: 18rpx;
  padding: 24rpx 26rpx;
  border-radius: 20rpx;
  background: #ffffff;
}

.card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12rpx;
}

.tag-row {
  display: flex;
  gap: 10rpx;
}

.tag {
  padding: 5rpx 14rpx;
  border-radius: 18rpx;
  font-size: 20rpx;
}

.tag.module {
  color: #8c6b74;
  background: #f7eff0;
}

.tag.high {
  color: #c25454;
  background: #fdf0ef;
}

.tag.medium {
  color: #b9843e;
  background: #fbf3e6;
}

.tag.low {
  color: #5b8c7b;
  background: #edf5f1;
}

.time {
  font-size: 21rpx;
  color: #b09a8e;
}

.title-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 10rpx;
}

.title {
  font-size: 30rpx;
  font-weight: 600;
  color: #3b3330;
}

.expand {
  font-size: 22rpx;
  color: #c9707c;
}

.summary {
  margin-bottom: 16rpx;
  font-size: 24rpx;
  line-height: 1.6;
  color: #6d605b;
}

.meta {
  padding: 12rpx 16rpx;
  border-radius: 12rpx;
  background: #faf6f2;
}

.meta-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: 5rpx 0;
}

.meta-label {
  flex-shrink: 0;
  font-size: 21rpx;
  color: #b09a8e;
}

.meta-value {
  margin-left: 20rpx;
  font-size: 22rpx;
  text-align: right;
  color: #6d605b;
  word-break: break-all;
}

.detail {
  margin-top: 18rpx;
  padding-top: 16rpx;
  border-top: 1rpx solid #f0e7e1;
}

.change-list {
  margin-bottom: 14rpx;
}

.change-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: 8rpx 0;
}

.change-label {
  flex-shrink: 0;
  font-size: 22rpx;
  color: #8c827c;
}

.change-values {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  justify-content: flex-end;
  margin-left: 20rpx;
}

.before {
  font-size: 22rpx;
  color: #b09a8e;
  text-decoration: line-through;
}

.arrow {
  margin: 0 10rpx;
  font-size: 22rpx;
  color: #c9707c;
}

.after {
  font-size: 22rpx;
  font-weight: 600;
  color: #3b3330;
}

.raw-detail {
  margin-bottom: 14rpx;
  padding: 14rpx 16rpx;
  border-radius: 10rpx;
  font-size: 22rpx;
  line-height: 1.6;
  color: #6d605b;
  background: #faf6f2;
}

.raw-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: 5rpx 0;
}

.raw-label {
  flex-shrink: 0;
  font-size: 20rpx;
  color: #cbbcb2;
}

.raw-value {
  margin-left: 20rpx;
  font-size: 20rpx;
  text-align: right;
  color: #a08d83;
  word-break: break-all;
}

.more {
  margin-top: 24rpx;
  padding: 20rpx 0;
  border-radius: 40rpx;
  text-align: center;
  font-size: 26rpx;
  color: #c9707c;
  background: #ffffff;
}

.end {
  padding: 30rpx 0;
  text-align: center;
  font-size: 22rpx;
  color: #cbbcb2;
}
</style>
