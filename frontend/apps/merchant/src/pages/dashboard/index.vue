<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getDashboard, type DashboardBlock, type DashboardData } from '../../api/dashboard'
import { dateAfter, dateLabel, yuan } from '../../utils/format'
import { handleAuthError, requireStaffLogin, staffInfo } from '../../utils/session'

const dash = ref<DashboardData | null>(null)
const activeDate = ref(dateAfter(0))
const loading = ref(true)
const error = ref('')
const dates = Array.from({ length: 7 }, (_, index) => dateAfter(index))

const CELL_WIDTH = 88
const TECH_WIDTH = 100

const roleText = computed(() => (staffInfo()?.role === 'owner' ? '店长' : '技师'))
const slotStarts = computed(() => dash.value?.slotStarts ?? [])
const technicians = computed(() => dash.value?.technicians ?? [])
const restDay = computed(() => Boolean(dash.value) && (dash.value?.businessHours?.length ?? 0) === 0)

const gridStyle = computed(
  () =>
    `grid-template-columns: ${TECH_WIDTH}rpx repeat(${slotStarts.value.length}, ${CELL_WIDTH}rpx);`
)

onShow(() => {
  if (!requireStaffLogin()) {
    return
  }
  load()
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    dash.value = await getDashboard(activeDate.value)
  } catch (e) {
    if (handleAuthError(e)) {
      return
    }
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

function chooseDate(date: string) {
  if (activeDate.value === date) {
    return
  }
  activeDate.value = date
  load()
}

function blockStyle(block: DashboardBlock): string {
  const startIndex = slotStarts.value.indexOf(block.startTime)
  const techIndex = technicians.value.findIndex((item) => item.id === block.technicianId)
  if (startIndex < 0 || techIndex < 0) {
    return 'display: none;'
  }
  const endIndex = slotStarts.value.indexOf(block.endTime)
  const span =
    endIndex > startIndex
      ? endIndex - startIndex
      : Math.max(1, Math.ceil((minutesOf(block.endTime) - minutesOf(block.startTime)) / stepMinutes()))
  return `grid-column: ${startIndex + 2} / span ${span}; grid-row: ${techIndex + 2};`
}

/** 时段列步进即粒度（营业时间轴按 slotGranularity 生成）。 */
function stepMinutes(): number {
  const starts = slotStarts.value
  return starts.length >= 2 ? minutesOf(starts[1]) - minutesOf(starts[0]) : 30
}

function minutesOf(time: string): number {
  const [hour, minute] = time.split(':').map(Number)
  return hour * 60 + minute
}

function openDetail(block: DashboardBlock) {
  uni.navigateTo({ url: `/pages/reservation/detail?id=${block.id}` })
}
</script>

<template>
  <view class="page">
    <view class="head">
      <view class="head-left">
        <text class="title">今日看板</text>
        <text class="subtitle">{{ roleText }}视图 · 色块点开可处理预约</text>
      </view>
      <view class="refresh" @click="load">刷新</view>
    </view>

    <scroll-view scroll-x class="dates" :show-scrollbar="false">
      <view class="dates-inner">
        <view
          v-for="(date, index) in dates"
          :key="date"
          class="date"
          :class="{ active: activeDate === date }"
          @click="chooseDate(date)"
        >
          <text class="date-top">{{ dateLabel(date, index).top }}</text>
          <text class="date-week">{{ dateLabel(date, index).week }}</text>
        </view>
      </view>
    </scroll-view>

    <view v-if="loading" class="state">看板加载中…</view>
    <view v-else-if="error" class="state error">
      <text>{{ error }}</text>
      <view class="retry" @click="load">重新加载</view>
    </view>

    <template v-else-if="dash">
      <view class="stats">
        <view class="stat">
          <text class="stat-value">{{ dash.stats.total }}</text>
          <text class="stat-label">预约数</text>
        </view>
        <view class="stat">
          <text class="stat-value warn">{{ dash.stats.waitConfirm }}</text>
          <text class="stat-label">待确认</text>
        </view>
        <view class="stat">
          <text class="stat-value primary">{{ dash.stats.waitService }}</text>
          <text class="stat-label">待服务</text>
        </view>
        <view class="stat">
          <text class="stat-value ok">{{ dash.stats.completed }}</text>
          <text class="stat-label">已完成</text>
        </view>
      </view>
      <view class="revenue">
        <text class="revenue-label">预计营收（待服务 + 已完成）</text>
        <text class="revenue-value">¥{{ yuan(dash.stats.revenueCents) }}</text>
      </view>

      <view v-if="restDay" class="rest">当日门店休息，暂无营业时段</view>
      <view v-else-if="!technicians.length" class="rest">当日无排班技师</view>
      <scroll-view v-else scroll-x class="grid-scroll" :show-scrollbar="false">
        <view class="grid" :style="gridStyle">
          <view class="corner" />
          <view v-for="slot in slotStarts" :key="slot" class="col-head">{{ slot }}</view>

          <template v-for="(tech, ti) in technicians" :key="tech.id">
            <view class="tech-head" :style="`grid-row: ${ti + 2};`">{{ tech.name }}</view>
            <view
              v-for="(slot, si) in slotStarts"
              :key="`${tech.id}-${slot}`"
              class="cell"
              :style="`grid-column: ${si + 2}; grid-row: ${ti + 2};`"
            />
          </template>

          <view
            v-for="block in dash.reservations"
            :key="block.id"
            class="block"
            :class="[`s${block.status}`, { conflict: block.conflict }]"
            :style="blockStyle(block)"
            @click="openDetail(block)"
          >
            <text class="block-text">{{ block.serviceName }}</text>
            <view v-if="block.conflict" class="conflict-badge">!</view>
          </view>
        </view>
      </scroll-view>

      <view class="legend">
        <text class="legend-dot s1" /><text class="legend-text">待确认</text>
        <text class="legend-dot s2" /><text class="legend-text">待服务</text>
        <text class="legend-dot s3" /><text class="legend-text">已完成</text>
        <text class="legend-dot s4" /><text class="legend-text">已取消</text>
        <text class="legend-dot s5" /><text class="legend-text">未到店</text>
        <text class="legend-dot conflict-dot" /><text class="legend-text">排班冲突</text>
      </view>
    </template>
  </view>
</template>

<style scoped>
.page {
  min-height: 100vh;
  padding: 24rpx 32rpx 120rpx;
  background: #faf6f2;
}

.head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
}

.head-left {
  display: flex;
  flex-direction: column;
}

.title {
  font-size: 40rpx;
  font-weight: 700;
  color: #3b3330;
}

.subtitle {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: #b09a8e;
}

.refresh {
  padding: 10rpx 24rpx;
  border-radius: 26rpx;
  font-size: 24rpx;
  color: #c9707c;
  background: #fdf0ee;
}

.dates {
  margin-top: 24rpx;
}

.dates-inner {
  display: flex;
}

.date {
  display: flex;
  flex: 0 0 116rpx;
  box-sizing: border-box;
  flex-direction: column;
  align-items: center;
  margin-right: 14rpx;
  padding: 16rpx 0;
  border-radius: 18rpx;
  border: 2rpx solid transparent;
  background: #ffffff;
}

.date.active {
  border-color: #c97b84;
  background: #fbf0ee;
}

.date-top {
  font-size: 26rpx;
  font-weight: 600;
  color: #3b3330;
}

.date.active .date-top {
  color: #c9707c;
}

.date-week {
  margin-top: 4rpx;
  font-size: 20rpx;
  color: #b09a8e;
}

.state {
  padding: 120rpx 0;
  text-align: center;
  font-size: 26rpx;
  color: #b09a8e;
}

.state.error {
  color: #c97b84;
}

.retry {
  display: inline-block;
  margin-top: 20rpx;
  padding: 10rpx 40rpx;
  border-radius: 28rpx;
  font-size: 24rpx;
  color: #ffffff;
  background: #c97b84;
}

.stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16rpx;
  margin-top: 24rpx;
}

.stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 22rpx 0;
  border-radius: 20rpx;
  background: #ffffff;
  box-shadow: 0 8rpx 20rpx rgba(104, 74, 68, 0.04);
}

.stat-value {
  font-size: 40rpx;
  font-weight: 700;
  color: #3b3330;
}

.stat-value.warn {
  color: #b07d24;
}

.stat-value.primary {
  color: #b96a74;
}

.stat-value.ok {
  color: #4f8d6a;
}

.stat-label {
  margin-top: 6rpx;
  font-size: 20rpx;
  color: #b09a8e;
}

.revenue {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-top: 16rpx;
  padding: 22rpx 26rpx;
  border-radius: 20rpx;
  background: linear-gradient(135deg, #d6978f, #c9707c);
}

.revenue-label {
  font-size: 22rpx;
  color: rgba(255, 255, 255, 0.85);
}

.revenue-value {
  font-size: 36rpx;
  font-weight: 700;
  color: #ffffff;
}

.rest {
  margin-top: 24rpx;
  padding: 80rpx 0;
  text-align: center;
  border-radius: 20rpx;
  font-size: 26rpx;
  color: #b09a8e;
  background: #ffffff;
}

.grid-scroll {
  margin-top: 24rpx;
  border-radius: 20rpx;
  background: #ffffff;
  box-shadow: 0 8rpx 20rpx rgba(104, 74, 68, 0.04);
}

.grid {
  display: grid;
  padding: 16rpx 0;
  position: relative;
}

.corner {
  grid-row: 1;
  grid-column: 1;
}

.col-head {
  grid-row: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 44rpx;
  font-size: 18rpx;
  color: #b09a8e;
}

.tech-head {
  grid-column: 1;
  display: flex;
  align-items: center;
  padding-left: 20rpx;
  height: 88rpx;
  font-size: 24rpx;
  font-weight: 600;
  color: #3b3330;
}

.cell {
  height: 88rpx;
  box-sizing: border-box;
  border-left: 1rpx solid #f4ece6;
  border-top: 1rpx solid #f4ece6;
}

.block {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 64rpx;
  margin-top: 12rpx;
  border-radius: 10rpx;
  z-index: 2;
  position: relative;
  overflow: hidden;
}

.block-text {
  max-width: 100%;
  padding: 0 8rpx;
  font-size: 19rpx;
  color: #ffffff;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.block.s1 {
  background: #d9a441;
}

.block.s2 {
  background: #5e8fbf;
}

.block.s3 {
  background: #5c9c74;
}

.block.s4 {
  background: #b9afa7;
}

.block.s5 {
  background: #c98a4b;
}

.block.conflict {
  border: 3rpx solid #d64545;
}

.conflict-badge {
  position: absolute;
  top: 0;
  right: 0;
  width: 26rpx;
  height: 26rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 0 8rpx 0 10rpx;
  font-size: 18rpx;
  font-weight: 700;
  color: #ffffff;
  background: #d64545;
}

.legend {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8rpx;
  margin-top: 20rpx;
}

.legend-dot {
  width: 16rpx;
  height: 16rpx;
  border-radius: 4rpx;
  margin-left: 16rpx;
}

.legend-dot.s1 {
  background: #d9a441;
}

.legend-dot.s2 {
  background: #5e8fbf;
}

.legend-dot.s3 {
  background: #5c9c74;
}

.legend-dot.s4 {
  background: #b9afa7;
}

.legend-dot.s5 {
  background: #c98a4b;
}

.legend-dot.conflict-dot {
  background: #ffffff;
  border: 2rpx solid #d64545;
}

.legend-text {
  font-size: 20rpx;
  color: #b09a8e;
}
</style>
