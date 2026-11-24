<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { listServices, type ServiceItem } from '../../api/service'
import { listTechnicians, type TechnicianItem } from '../../api/technician'
import { getSlots, type Slot } from '../../api/slots'
import { createProxyReservation } from '../../api/reservation'
import { dateAfter, dateLabel, displayDate, yuan } from '../../utils/format'
import { isOwner, requireStaffLogin } from '../../utils/session'

const services = ref<ServiceItem[]>([])
const technicians = ref<TechnicianItem[]>([])
const dates = Array.from({ length: 14 }, (_, index) => dateAfter(index))
const selectedService = ref<ServiceItem | null>(null)
const selectedTechnician = ref(0)
const activeDate = ref(dates[0])
const slots = ref<Slot[]>([])
const selectedStart = ref('')
const minLead = ref(60)
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

const availableTechnicians = computed(() => {
  if (!selectedService.value) {
    return []
  }
  const allowed = new Set(selectedService.value.technicianIds || [])
  return technicians.value.filter((tech) => allowed.has(tech.id))
})

const selectedEnd = computed(() => {
  if (!selectedStart.value || !selectedService.value) {
    return ''
  }
  const [hour, minute] = selectedStart.value.split(':').map(Number)
  const total = hour * 60 + minute + selectedService.value.durationMin
  const hh = `${Math.floor(total / 60)}`.padStart(2, '0')
  const mm = `${total % 60}`.padStart(2, '0')
  return `${hh}:${mm}`
})

onLoad(async () => {
  if (!requireStaffLogin()) {
    return
  }
  if (!isOwner()) {
    error.value = '仅店长可代客预约'
    loading.value = false
    return
  }
  try {
    const [serviceList, techList] = await Promise.all([listServices(), listTechnicians()])
    services.value = serviceList.filter((service) => service.status === 1)
    technicians.value = techList.filter((tech) => tech.status === 1)
    loading.value = false
    if (services.value.length === 1) {
      chooseService(services.value[0])
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
    loading.value = false
  }
})

function chooseService(service: ServiceItem) {
  if (selectedService.value?.id === service.id) {
    return
  }
  selectedService.value = service
  selectedTechnician.value = 0
  loadSlots()
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

async function loadSlots() {
  if (!selectedService.value) {
    slots.value = []
    return
  }
  slotLoading.value = true
  selectedStart.value = ''
  try {
    const result = await getSlots(
      selectedService.value.id,
      activeDate.value,
      selectedTechnician.value || undefined
    )
    slots.value = result.slots
    minLead.value = result.minLeadMinutes ?? 60
  } catch (e) {
    slots.value = []
    uni.showToast({ title: e instanceof Error ? e.message : '时段加载失败', icon: 'none' })
  } finally {
    slotLoading.value = false
  }
}

function chooseSlot(slot: Slot) {
  if (slot.state !== 'available') {
    return
  }
  selectedStart.value = slot.startTime
}

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

function idempotencyKey(): string {
  return `p${Date.now()}${Math.random().toString(36).slice(2, 10)}`.slice(0, 36)
}

async function submit() {
  if (submitting.value) {
    return
  }
  if (!selectedService.value) {
    uni.showToast({ title: '请选择服务', icon: 'none' })
    return
  }
  if (!selectedStart.value) {
    uni.showToast({ title: '请选择时段', icon: 'none' })
    return
  }
  const name = contactName.value.trim()
  const phone = contactPhone.value.trim()
  if (!name) {
    uni.showToast({ title: '请填写顾客姓名', icon: 'none' })
    return
  }
  if (!/^1[3-9]\d{9}$/.test(phone)) {
    uni.showToast({ title: '请输入正确的手机号', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    const result = await createProxyReservation({
      serviceId: selectedService.value.id,
      technicianId: selectedTechnician.value || null,
      date: activeDate.value,
      startTime: selectedStart.value,
      contactName: name,
      contactPhone: phone,
      remark: remark.value.trim() || undefined,
      idempotencyKey: idempotencyKey()
    })
    uni.showModal({
      title: '代客预约成功',
      content: `预约号 ${result.no}\n${result.serviceName} · ${result.technicianName}\n${displayDate(result.date)} ${result.startTime}-${result.endTime}`,
      showCancel: false,
      confirmText: '返回列表',
      confirmColor: '#C9707C',
      success: () => {
        // 通知列表页切到预约日期并刷新
        uni.$emit('proxy-created', result.date)
        uni.navigateBack()
      }
    })
  } catch (e) {
    uni.showToast({ title: e instanceof Error ? e.message : '提交失败', icon: 'none' })
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <view class="page">
    <view v-if="loading" class="state">加载中…</view>
    <view v-else-if="error" class="state error">{{ error }}</view>

    <template v-else>
      <view class="panel">
        <text class="panel-title">选择服务</text>
        <view class="chips">
          <view
            v-for="service in services"
            :key="service.id"
            class="chip"
            :class="{ active: selectedService?.id === service.id }"
            @click="chooseService(service)"
          >
            {{ service.name }}
            <text class="chip-sub">¥{{ yuan(service.priceCents) }}</text>
          </view>
        </view>
      </view>

      <view v-if="selectedService" class="panel">
        <text class="panel-title">选择技师</text>
        <view class="chips">
          <view
            class="chip"
            :class="{ active: selectedTechnician === 0 }"
            @click="chooseTechnician(0)"
          >
            不指定<text class="chip-sub">系统分配</text>
          </view>
          <view
            v-for="tech in availableTechnicians"
            :key="tech.id"
            class="chip"
            :class="{ active: selectedTechnician === tech.id }"
            @click="chooseTechnician(tech.id)"
          >
            {{ tech.name }}<text class="chip-sub">{{ tech.title || '技师' }}</text>
          </view>
        </view>
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
          <view v-if="selectedService" class="legend">
            <text class="legend-dot available" /><text class="legend-text">可约</text>
            <text class="legend-dot full" /><text class="legend-text">已满</text>
            <text class="legend-dot past" /><text class="legend-text">已过</text>
            <text class="legend-dot rest" /><text class="legend-text">休息</text>
          </view>
        </view>
        <view v-if="!selectedService" class="slot-empty">请先选择服务</view>
        <view v-else-if="slotLoading" class="slot-empty">时段加载中…</view>
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
        <text class="panel-title">顾客信息</text>
        <view class="field">
          <text class="field-label">姓名</text>
          <input
            id="proxy-name"
            v-model="contactName"
            class="field-input"
            placeholder="顾客称呼"
            placeholder-class="ph"
          />
        </view>
        <view class="field">
          <text class="field-label">手机号</text>
          <input
            id="proxy-phone"
            v-model="contactPhone"
            class="field-input"
            type="tel"
            maxlength="11"
            placeholder="用于到店联系"
            placeholder-class="ph"
          />
        </view>
        <view class="field">
          <text class="field-label">备注</text>
          <input
            id="proxy-remark"
            v-model="remark"
            class="field-input"
            placeholder="选填，如偏好、需求"
            placeholder-class="ph"
          />
        </view>
      </view>

      <view class="panel summary">
        <view class="summary-row">
          <text class="summary-label">服务</text>
          <text class="summary-value">
            {{ selectedService ? `${selectedService.name} · ¥${yuan(selectedService.priceCents)}` : '未选择' }}
          </text>
        </view>
        <view class="summary-row">
          <text class="summary-label">时间</text>
          <text class="summary-value">
            {{ selectedStart ? `${displayDate(activeDate)} ${selectedStart}-${selectedEnd}` : '未选择' }}
          </text>
        </view>
        <view class="summary-row">
          <text class="summary-label">顾客</text>
          <text class="summary-value">{{ contactName || '未填写' }} {{ contactPhone }}</text>
        </view>
      </view>

      <view class="safe-gap" />

      <view class="bar">
        <view class="bar-button" :class="{ disabled: submitting }" @click="submit">
          {{ submitting ? '提交中…' : '确认代客预约' }}
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
  margin-bottom: 22rpx;
  padding: 26rpx 28rpx;
  border-radius: 22rpx;
  background: #ffffff;
  box-shadow: 0 8rpx 20rpx rgba(104, 74, 68, 0.04);
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

.legend {
  display: flex;
  align-items: center;
  gap: 8rpx;
}

.legend-dot {
  width: 14rpx;
  height: 14rpx;
  border-radius: 50%;
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

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
  margin-top: 18rpx;
}

.chip {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 14rpx 28rpx;
  border-radius: 16rpx;
  border: 2rpx solid transparent;
  font-size: 26rpx;
  color: #3b3330;
  background: #faf6f2;
}

.chip.active {
  border-color: #c97b84;
  background: #fbf0ee;
  color: #c9707c;
}

.chip-sub {
  margin-top: 4rpx;
  font-size: 19rpx;
  color: #b09a8e;
}

.chip.active .chip-sub {
  color: #c99a94;
}

.date-row {
  margin-top: 18rpx;
}

.date-row-inner {
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
  margin-top: 4rpx;
  font-size: 20rpx;
  color: #b09a8e;
}

.slot-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14rpx;
  margin-top: 18rpx;
}

.slot {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 14rpx 0;
  border-radius: 14rpx;
  border: 2rpx solid #eee3dc;
  background: #ffffff;
}

.slot-time {
  font-size: 25rpx;
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
  padding: 20rpx 0;
  border-bottom: 1rpx solid #f5eee8;
}

.field-label {
  width: 120rpx;
  font-size: 26rpx;
  color: #8c827c;
}

.field-input {
  flex: 1;
  font-size: 28rpx;
  color: #3b3330;
}

.ph {
  color: #cbbcb2;
}

.summary-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: 8rpx 0;
}

.summary-label {
  font-size: 24rpx;
  color: #b09a8e;
}

.summary-value {
  max-width: 72%;
  text-align: right;
  font-size: 26rpx;
  color: #3b3330;
}

.safe-gap {
  height: 150rpx;
}

.bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 18rpx 32rpx calc(18rpx + env(safe-area-inset-bottom));
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
