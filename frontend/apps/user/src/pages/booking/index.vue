<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { USER_PRIVACY_AGREED_KEY } from '@reservation/shared'
import { getServiceDetail, getStoreHome, type ServiceDetail } from '../../api/catalog'
import { createIdempotencyKey, getSlots, submitReservation, type Slot } from '../../api/booking'
import { ensureUserLogin } from '../../utils/session'
import { dateAfter, dateLabel, durationText, yuan } from '../../utils/format'

const service = ref<ServiceDetail | null>(null)
const dates = ref<string[]>([])
const activeDate = ref('')
const slots = ref<Slot[]>([])
const selectedTechnician = ref(0)
const selectedStart = ref('')
const contactName = ref('')
const contactPhone = ref('')
const remark = ref('')
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

const minLead = ref(60)

onLoad(async (options) => {
  const serviceId = Number(options?.serviceId || 0)
  if (!serviceId) {
    error.value = '请先选择服务'
    loading.value = false
    return
  }
  try {
    const [detail, home] = await Promise.all([
      getServiceDetail(serviceId),
      getStoreHome().catch(() => null)
    ])
    service.value = detail
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

// 未同意隐私政策不允许进入预约流程，拦回首页统一弹窗
onShow(() => {
  if (!uni.getStorageSync(USER_PRIVACY_AGREED_KEY)) {
    uni.reLaunch({ url: '/pages/index/index' })
  }
})

async function loadSlots() {
  if (!service.value) {
    return
  }
  slotLoading.value = true
  selectedStart.value = ''
  try {
    const result = await getSlots(service.value.id, activeDate.value, selectedTechnician.value || undefined)
    slots.value = result.slots
    minLead.value = result.minLeadMinutes ?? 60
  } catch (e) {
    slots.value = []
    uni.showToast({ title: e instanceof Error ? e.message : '时段加载失败', icon: 'none' })
  } finally {
    slotLoading.value = false
  }
}

function chooseTechnician(id: number) {
  if (selectedTechnician.value === id) {
    return
  }
  selectedTechnician.value = id
  loadSlots()
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

/** 区分真正已过与「未到但不足最小提前量」：后者提示需提前多久。 */
function slotText(slot: Slot): string {
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

const technicianText = computed(() => {
  if (!selectedTechnician.value) {
    return '不指定技师（系统分配）'
  }
  const hit = service.value?.technicians.find((item) => item.id === selectedTechnician.value)
  return hit ? `${hit.name}${hit.title ? ` · ${hit.title}` : ''}` : '指定技师'
})

async function submit() {
  if (!service.value || submitting.value) {
    return
  }
  if (!selectedStart.value) {
    uni.showToast({ title: '请先选择时段', icon: 'none' })
    return
  }
  if (!contactName.value.trim()) {
    uni.showToast({ title: '请填写联系人姓名', icon: 'none' })
    return
  }
  if (!/^1[3-9]\d{9}$/.test(contactPhone.value.trim())) {
    uni.showToast({ title: '手机号格式不正确', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    await ensureUserLogin()
    const result = await submitReservation({
      serviceId: service.value.id,
      technicianId: selectedTechnician.value || null,
      date: activeDate.value,
      startTime: selectedStart.value,
      contactName: contactName.value.trim(),
      contactPhone: contactPhone.value.trim(),
      remark: remark.value.trim() || undefined,
      idempotencyKey: createIdempotencyKey()
    })
    uni.setStorageSync('reservation_last_reservation', result)
    uni.redirectTo({ url: '/pages/booking/success' })
  } catch (e) {
    const message = e instanceof Error ? e.message : '提交失败'
    uni.showToast({ title: message, icon: 'none' })
    if (message.includes('时段') || message.includes('不可约') || message.includes('冲突')) {
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

    <template v-else-if="service">
      <view class="panel service-row">
        <image v-if="service.cover" class="service-cover" :src="service.cover" mode="aspectFill" />
        <view v-else class="service-cover service-cover-fallback">{{ service.name.slice(0, 1) }}</view>
        <view class="service-info">
          <text class="service-name">{{ service.name }}</text>
          <text class="service-meta">{{ durationText(service.durationMin) }} · 到店付</text>
          <text class="service-price">¥{{ yuan(service.priceCents) }}</text>
        </view>
      </view>

      <view class="panel">
        <text class="panel-title">选择技师</text>
        <scroll-view scroll-x class="tech-row" :show-scrollbar="false">
          <view class="tech-row-inner">
            <view
              class="tech"
              :class="{ active: selectedTechnician === 0 }"
              @click="chooseTechnician(0)"
            >
              <view class="tech-avatar tech-auto">随</view>
              <text class="tech-name">不指定</text>
              <text class="tech-title">系统分配</text>
            </view>
            <view
              v-for="technician in service.technicians"
              :key="technician.id"
              class="tech"
              :class="{ active: selectedTechnician === technician.id }"
              @click="chooseTechnician(technician.id)"
            >
              <image v-if="technician.avatar" class="tech-avatar" :src="technician.avatar" mode="aspectFill" />
              <view v-else class="tech-avatar tech-avatar-fallback">{{ technician.name.slice(0, 1) }}</view>
              <text class="tech-name">{{ technician.name }}</text>
              <text class="tech-title">{{ technician.title || '技师' }}</text>
            </view>
          </view>
        </scroll-view>
      </view>

      <view class="panel">
        <text class="panel-title">选择日期</text>
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
          <text class="panel-title">选择时段</text>
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

      <view class="panel">
        <text class="panel-title">联系信息</text>
        <view class="field">
          <text class="field-label">姓名</text>
          <input
            id="contact-name"
            v-model="contactName"
            class="field-input"
            placeholder="您的称呼"
            placeholder-class="ph"
            maxlength="20"
          />
        </view>
        <view class="field">
          <text class="field-label">手机号</text>
          <input
            id="contact-phone"
            v-model="contactPhone"
            class="field-input"
            type="tel"
            placeholder="用于到店联系"
            placeholder-class="ph"
            maxlength="11"
          />
        </view>
        <view class="field">
          <text class="field-label">备注</text>
          <input
            id="contact-remark"
            v-model="remark"
            class="field-input"
            placeholder="选填，如偏好、需求"
            placeholder-class="ph"
            maxlength="100"
          />
        </view>
      </view>

      <view class="panel summary">
        <view class="summary-row">
          <text class="summary-label">预约服务</text>
          <text class="summary-value">{{ service.name }}</text>
        </view>
        <view class="summary-row">
          <text class="summary-label">技师</text>
          <text class="summary-value">{{ technicianText }}</text>
        </view>
        <view class="summary-row">
          <text class="summary-label">时间</text>
          <text class="summary-value">
            {{ selectedStart ? `${activeDate} ${selectedStart}` : '请选择时段' }}
          </text>
        </view>
      </view>

      <view class="safe-gap" />

      <view class="bar">
        <view class="bar-price">
          <text class="bar-label">合计</text>
          <text class="bar-value">¥{{ yuan(service.priceCents) }}</text>
        </view>
        <view class="bar-button" :class="{ disabled: submitting }" @click="submit">
          {{ submitting ? '提交中…' : '提交预约' }}
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

.service-row {
  display: flex;
  align-items: center;
}

.service-cover {
  width: 132rpx;
  height: 132rpx;
  border-radius: 20rpx;
  flex-shrink: 0;
}

.service-cover-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 48rpx;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.9);
  background: linear-gradient(135deg, #e3c2bc, #c9968f);
}

.service-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  margin-left: 24rpx;
}

.service-name {
  font-size: 32rpx;
  font-weight: 600;
  color: #3b3330;
}

.service-meta {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: #b09a8e;
}

.service-price {
  margin-top: 8rpx;
  font-size: 30rpx;
  font-weight: 600;
  color: #c97b84;
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
  margin-bottom: 20rpx;
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

.tech-row {
  margin-top: 20rpx;
}

.tech-row-inner {
  display: flex;
  align-items: stretch;
}

.tech {
  display: flex;
  flex: 0 0 140rpx;
  box-sizing: border-box;
  flex-direction: column;
  align-items: center;
  margin-right: 20rpx;
  padding: 18rpx 0 14rpx;
  border-radius: 20rpx;
  border: 2rpx solid transparent;
  background: #faf6f2;
}

.tech.active {
  border-color: #c97b84;
  background: #fbf0ee;
}

.tech-avatar {
  width: 88rpx;
  height: 88rpx;
  border-radius: 50%;
}

.tech-avatar-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32rpx;
  color: #ffffff;
  background: linear-gradient(135deg, #d8a8a4, #b97f80);
}

.tech-auto {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 30rpx;
  font-weight: 600;
  color: #b97f80;
  background: #f7e9e6;
}

.tech-name {
  margin-top: 10rpx;
  line-height: 34rpx;
  font-size: 24rpx;
  color: #3b3330;
}

.tech-title {
  line-height: 28rpx;
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

.field {
  display: flex;
  align-items: center;
  padding: 24rpx 0;
  border-bottom: 1rpx solid #f5eee8;
}

.field:last-child {
  border-bottom: none;
  padding-bottom: 4rpx;
}

.field-label {
  width: 120rpx;
  font-size: 26rpx;
  color: #6f635c;
}

.field-input {
  flex: 1;
  font-size: 26rpx;
  color: #3b3330;
}

.ph {
  color: #cbbcb2;
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
  height: 180rpx;
}

.bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  padding: 20rpx 32rpx calc(20rpx + env(safe-area-inset-bottom));
  background: #ffffff;
  box-shadow: 0 -8rpx 24rpx rgba(104, 74, 68, 0.06);
}

.bar-price {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.bar-label {
  font-size: 20rpx;
  color: #b09a8e;
}

.bar-value {
  font-size: 36rpx;
  font-weight: 700;
  color: #c97b84;
}

.bar-button {
  padding: 22rpx 72rpx;
  border-radius: 44rpx;
  font-size: 30rpx;
  font-weight: 600;
  color: #ffffff;
  background: linear-gradient(135deg, #d6978f, #c9707c);
  box-shadow: 0 10rpx 24rpx rgba(201, 112, 124, 0.3);
}

.bar-button.disabled {
  opacity: 0.6;
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
