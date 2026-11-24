<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getStoreHome } from '../../api/catalog'
import { getSlots, type Slot } from '../../api/booking'
import { getReservation, rescheduleReservation, type ReservationDetail } from '../../api/reservation'
import { ensureUserLogin } from '../../utils/session'
import { dateAfter, dateLabel, displayDate } from '../../utils/format'

const reservationId = ref(0)
const detail = ref<ReservationDetail | null>(null)
const dates = ref<string[]>([])
const activeDate = ref('')
const slots = ref<Slot[]>([])
const selectedStart = ref('')
const minLead = ref(60)
const loading = ref(true)
const slotLoading = ref(false)
const submitting = ref(false)
const error = ref('')

const STATE_TEXT: Record<Slot['state'], string> = {
  available: '可约',
  full: '已满',
  past: '已过',
  rest: '休息'
}

onLoad(async (options) => {
  reservationId.value = Number(options?.id || 0)
  if (!reservationId.value) {
    error.value = '预约不存在'
    loading.value = false
    return
  }
  try {
    await ensureUserLogin()
    const [current, home] = await Promise.all([
      getReservation(reservationId.value),
      getStoreHome().catch(() => null)
    ])
    detail.value = current
    if (!current.canReschedule) {
      error.value = '当前预约不可改期'
      loading.value = false
      return
    }
    const days = home?.bookableDays ?? 14
    dates.value = Array.from({ length: days }, (_, index) => dateAfter(index))
    activeDate.value = dates.value[0]
    loading.value = false
    await loadSlots()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
    loading.value = false
  }
})

async function loadSlots() {
  if (!detail.value) {
    return
  }
  slotLoading.value = true
  selectedStart.value = ''
  try {
    const result = await getSlots(detail.value.serviceId, activeDate.value, detail.value.technicianId)
    slots.value = result.slots
    minLead.value = result.minLeadMinutes ?? 60
  } catch (e) {
    slots.value = []
    uni.showToast({ title: e instanceof Error ? e.message : '时段加载失败', icon: 'none' })
  } finally {
    slotLoading.value = false
  }
}

function chooseDate(date: string) {
  if (activeDate.value === date) {
    return
  }
  activeDate.value = date
  loadSlots()
}

function chooseSlot(slot: Slot) {
  if (slot.state !== 'available') {
    return
  }
  selectedStart.value = slot.startTime
}

function slotText(slot: Slot): string {
  const current = detail.value
  if (current && activeDate.value === current.date && slot.startTime === current.startTime) {
    return '当前预约'
  }
  if (slot.state !== 'past') {
    return STATE_TEXT[slot.state]
  }
  const startAt = new Date(`${activeDate.value}T${slot.startTime}:00`).getTime()
  if (startAt <= Date.now()) {
    return '已过'
  }
  const lead = minLead.value
  if (lead >= 60 && lead % 60 === 0) {
    return `需提前${lead / 60}小时`
  }
  return `需提前${lead}分钟`
}

const newEnd = computed(() => {
  if (!selectedStart.value || !detail.value) {
    return ''
  }
  const [hour, minute] = selectedStart.value.split(':').map(Number)
  const total = hour * 60 + minute + detail.value.durationMin
  return `${`${Math.floor(total / 60) % 24}`.padStart(2, '0')}:${`${total % 60}`.padStart(2, '0')}`
})

async function submit() {
  if (!detail.value || submitting.value) {
    return
  }
  if (!selectedStart.value) {
    uni.showToast({ title: '请先选择新时段', icon: 'none' })
    return
  }
  if (activeDate.value === detail.value.date && selectedStart.value === detail.value.startTime) {
    uni.showToast({ title: '与原预约时间相同', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    await rescheduleReservation(detail.value.id, activeDate.value, selectedStart.value)
    uni.showToast({ title: '改期成功', icon: 'none' })
    setTimeout(() => uni.navigateBack(), 600)
  } catch (e) {
    const message = e instanceof Error ? e.message : '改期失败'
    uni.showToast({ title: message, icon: 'none' })
    if (message.includes('时段') || message.includes('冲突')) {
      loadSlots()
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <view class="page">
    <view v-if="loading" class="state">加载中…</view>
    <view v-else-if="error" class="state error">{{ error }}</view>

    <template v-else-if="detail">
      <view class="panel origin">
        <text class="origin-label">当前预约</text>
        <text class="origin-value">{{ detail.serviceName }} · {{ detail.technicianName }}</text>
        <text class="origin-value">{{ displayDate(detail.date) }} {{ detail.startTime }}-{{ detail.endTime }}</text>
      </view>

      <view class="panel">
        <text class="panel-title">选择新日期</text>
        <scroll-view scroll-x class="date-row" :show-scrollbar="false">
          <view class="date-row-inner">
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
      </view>

      <view class="panel">
        <view class="panel-head">
          <text class="panel-title">选择新时段</text>
          <view class="legend">
            <text class="legend-dot available" />
            <text class="legend-text">可约</text>
            <text class="legend-dot full" />
            <text class="legend-text">已满</text>
            <text class="legend-dot past" />
            <text class="legend-text">已过</text>
            <text class="legend-dot rest" />
            <text class="legend-text">休息</text>
          </view>
        </view>
        <view v-if="slotLoading" class="slot-empty">时段加载中…</view>
        <view v-else-if="slots.length" class="slot-grid">
          <view
            v-for="slot in slots"
            :key="slot.startTime"
            class="slot"
            :class="[slot.state, { selected: selectedStart === slot.startTime }]"
            @click="chooseSlot(slot)"
          >
            <text class="slot-time">{{ slot.startTime }}</text>
            <text class="slot-state">{{ slotText(slot) }}</text>
          </view>
        </view>
        <view v-else class="slot-empty">当日无可约时段，换个日期看看吧</view>
      </view>

      <view class="panel summary">
        <view class="summary-row">
          <text class="summary-label">新时间</text>
          <text class="summary-value">
            {{ selectedStart ? `${displayDate(activeDate)} ${selectedStart}-${newEnd}` : '请选择时段' }}
          </text>
        </view>
        <view class="summary-row">
          <text class="summary-label">技师</text>
          <text class="summary-value">{{ detail.technicianName }}（不变）</text>
        </view>
      </view>

      <view class="safe-gap" />

      <view class="bar">
        <view class="bar-button" :class="{ disabled: submitting || !selectedStart }" @click="submit">
          {{ submitting ? '提交中…' : '确认改期' }}
        </view>
      </view>
    </template>
  </view>
</template>

<style scoped>
.page {
  min-height: 100vh;
  padding: 24rpx 32rpx 0;
  background: #faf6f2;
}

.panel {
  margin-bottom: 24rpx;
  padding: 28rpx;
  border-radius: 24rpx;
  background: #ffffff;
  box-shadow: 0 10rpx 28rpx rgba(104, 74, 68, 0.05);
}

.origin {
  display: flex;
  flex-direction: column;
  background: #fdf4f2;
}

.origin-label {
  font-size: 22rpx;
  color: #b09a8e;
}

.origin-value {
  margin-top: 8rpx;
  font-size: 26rpx;
  color: #3b3330;
}

.panel-title {
  display: block;
  font-size: 28rpx;
  font-weight: 600;
  color: #3b3330;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.panel-head .panel-title {
  margin-bottom: 0;
}

.legend {
  display: flex;
  align-items: center;
  gap: 8rpx;
}

.legend-dot {
  width: 14rpx;
  height: 14rpx;
  border-radius: 4rpx;
  margin-left: 12rpx;
}

.legend-dot.available {
  background: #ffffff;
  border: 1rpx solid #d8b9b5;
}

.legend-dot.full {
  background: #efe8e2;
}

.legend-dot.past {
  background: #f4efea;
}

.legend-dot.rest {
  background: #f6f1ec;
}

.legend-text {
  font-size: 20rpx;
  color: #b09a8e;
}

.date-row {
  margin-top: 20rpx;
}

.date-row-inner {
  display: flex;
}

.date {
  display: flex;
  flex: 0 0 120rpx;
  box-sizing: border-box;
  flex-direction: column;
  align-items: center;
  margin-right: 16rpx;
  padding: 18rpx 0;
  border-radius: 20rpx;
  border: 2rpx solid transparent;
  background: #faf6f2;
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
  margin-top: 6rpx;
  font-size: 20rpx;
  color: #b09a8e;
}

.slot-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16rpx;
  margin-top: 20rpx;
}

.slot {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 16rpx 0;
  border-radius: 16rpx;
  border: 2rpx solid #eee3dc;
  background: #ffffff;
}

.slot-time {
  font-size: 26rpx;
  font-weight: 600;
  color: #3b3330;
}

.slot-state {
  margin-top: 4rpx;
  font-size: 18rpx;
  color: #b09a8e;
}

.slot.full {
  border-color: transparent;
  background: #efe8e2;
}

.slot.full .slot-time {
  color: #b9aea6;
}

.slot.past {
  border-color: transparent;
  background: #f4efea;
}

.slot.past .slot-time {
  color: #c9bfb7;
  text-decoration: line-through;
}

.slot.rest {
  border-style: dashed;
  border-color: #e6dcd4;
  background: #faf6f2;
}

.slot.rest .slot-time {
  color: #c9bfb7;
}

.slot.selected {
  border-color: #c97b84;
  background: linear-gradient(135deg, #d6978f, #c9707c);
  box-shadow: 0 8rpx 20rpx rgba(201, 112, 124, 0.28);
}

.slot.selected .slot-time,
.slot.selected .slot-state {
  color: #ffffff;
}

.slot-empty {
  padding: 40rpx 0;
  text-align: center;
  font-size: 24rpx;
  color: #b09a8e;
}

.summary-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: 10rpx 0;
}

.summary-label {
  font-size: 24rpx;
  color: #b09a8e;
}

.summary-value {
  max-width: 70%;
  text-align: right;
  font-size: 26rpx;
  color: #3b3330;
}

.safe-gap {
  height: 160rpx;
}

.bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 20rpx 32rpx calc(20rpx + env(safe-area-inset-bottom));
  background: #ffffff;
  box-shadow: 0 -8rpx 24rpx rgba(104, 74, 68, 0.06);
}

.bar-button {
  padding: 22rpx 0;
  border-radius: 44rpx;
  text-align: center;
  font-size: 30rpx;
  font-weight: 600;
  color: #ffffff;
  background: linear-gradient(135deg, #d6978f, #c9707c);
  box-shadow: 0 10rpx 24rpx rgba(201, 112, 124, 0.3);
}

.bar-button.disabled {
  opacity: 0.55;
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
</style>
