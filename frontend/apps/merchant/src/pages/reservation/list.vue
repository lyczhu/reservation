<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow, onUnload } from '@dcloudio/uni-app'
import {
  confirmReservation,
  listReservations,
  type StaffReservationItem
} from '../../api/reservation'
import { listTechnicians, type TechnicianItem } from '../../api/technician'
import { dateAfter, dateLabel, yuan } from '../../utils/format'
import { handleAuthError, isOwner, requireStaffLogin } from '../../utils/session'

const STATUS_TABS = [
  { key: 0, text: '全部' },
  { key: 1, text: '待确认' },
  { key: 2, text: '待服务' },
  { key: 3, text: '已完成' },
  { key: 4, text: '已取消' },
  { key: 5, text: '未到店' }
] as const

const dates = Array.from({ length: 7 }, (_, index) => dateAfter(index))
const activeDate = ref(dateAfter(0))
const activeStatus = ref(0)
const activeTechnician = ref(0)
const technicians = ref<TechnicianItem[]>([])
const items = ref<StaffReservationItem[]>([])
const loading = ref(true)
const error = ref('')
const owner = computed(() => isOwner())

onShow(() => {
  if (!requireStaffLogin()) {
    return
  }
  load()
})

// 代客预约成功返回：切到预约日期并刷新列表
function onProxyCreated(date: string) {
  if (dates.includes(date)) {
    activeDate.value = date
  }
  activeStatus.value = 0
  load()
}
uni.$on('proxy-created', onProxyCreated)
onUnload(() => uni.$off('proxy-created', onProxyCreated))

async function load() {
  loading.value = true
  error.value = ''
  try {
    if (owner.value && !technicians.value.length) {
      technicians.value = await listTechnicians().catch(() => [])
    }
    items.value = await listReservations({
      from: activeDate.value,
      to: activeDate.value,
      status: activeStatus.value || undefined,
      technicianId: activeTechnician.value || undefined
    })
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

function chooseStatus(status: number) {
  if (activeStatus.value === status) {
    return
  }
  activeStatus.value = status
  load()
}

function chooseTechnician(id: number) {
  if (activeTechnician.value === id) {
    return
  }
  activeTechnician.value = id
  load()
}

function openDetail(item: StaffReservationItem) {
  uni.navigateTo({ url: `/pages/reservation/detail?id=${item.id}` })
}

function goProxy() {
  uni.navigateTo({ url: '/pages/reservation/proxy' })
}

async function confirm(item: StaffReservationItem) {
  try {
    await confirmReservation(item.id)
    uni.showToast({ title: '已确认', icon: 'none' })
    await load()
  } catch (e) {
    uni.showToast({ title: e instanceof Error ? e.message : '操作失败', icon: 'none' })
  }
}
</script>

<template>
  <view class="page">
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

    <view class="tabs">
      <view
        v-for="tab in STATUS_TABS"
        :key="tab.key"
        class="tab"
        :class="{ active: activeStatus === tab.key }"
        @click="chooseStatus(tab.key)"
      >
        {{ tab.text }}
      </view>
    </view>

    <scroll-view v-if="owner && technicians.length" scroll-x class="techs" :show-scrollbar="false">
      <view class="techs-inner">
        <view class="tech-chip" :class="{ active: activeTechnician === 0 }" @click="chooseTechnician(0)">
          全部技师
        </view>
        <view
          v-for="tech in technicians"
          :key="tech.id"
          class="tech-chip"
          :class="{ active: activeTechnician === tech.id }"
          @click="chooseTechnician(tech.id)"
        >
          {{ tech.name }}
        </view>
      </view>
    </scroll-view>

    <view v-if="loading" class="state">加载中…</view>
    <view v-else-if="error" class="state error">{{ error }}</view>
    <view v-else-if="!items.length" class="state empty">
      <text class="empty-icon">🗓</text>
      <text>{{ activeStatus === 0 ? '当日暂无预约' : '当日暂无该状态的预约' }}</text>
    </view>

    <view v-else class="list">
      <view v-for="item in items" :key="item.id" class="card" @click="openDetail(item)">
        <view class="card-head">
          <view class="time">
            <text class="time-text">{{ item.startTime }}</text>
            <text class="time-sep">-</text>
            <text class="time-text small">{{ item.endTime }}</text>
          </view>
          <view class="head-right">
            <text v-if="item.source === 2" class="source">代客</text>
            <text class="status" :class="`s${item.status}`">{{ item.statusText }}</text>
          </view>
        </view>
        <view class="card-body">
          <view class="info">
            <text class="service">{{ item.serviceName }}</text>
            <text class="meta">{{ item.technicianName }} · {{ item.contactName }} {{ item.contactPhone }}</text>
            <text class="no">{{ item.no }}</text>
          </view>
          <text class="price">¥{{ yuan(item.priceCents) }}</text>
        </view>
        <view v-if="owner && item.status === 1" class="card-actions">
          <view class="primary small" @click.stop="confirm(item)">确认预约</view>
        </view>
      </view>
    </view>

    <view v-if="owner" class="fab" @click="goProxy">＋ 代客预约</view>
  </view>
</template>

<style scoped>
.page {
  min-height: 100vh;
  padding: 20rpx 32rpx 120rpx;
  background: #faf6f2;
}

.dates {
  margin-top: 4rpx;
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
  padding: 14rpx 0;
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

.tabs {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 10rpx;
  margin-top: 20rpx;
}

.tab {
  padding: 12rpx 0;
  border-radius: 12rpx;
  text-align: center;
  font-size: 23rpx;
  color: #8c827c;
  background: #ffffff;
}

.tab.active {
  color: #ffffff;
  background: #c97b84;
}

.techs {
  margin-top: 16rpx;
}

.techs-inner {
  display: flex;
}

.tech-chip {
  flex-shrink: 0;
  margin-right: 12rpx;
  padding: 10rpx 26rpx;
  border-radius: 28rpx;
  font-size: 23rpx;
  color: #8c827c;
  background: #ffffff;
}

.tech-chip.active {
  color: #c9707c;
  background: #fdf0ee;
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

.state.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.empty-icon {
  font-size: 64rpx;
  margin-bottom: 16rpx;
}

.fab {
  position: fixed;
  right: 32rpx;
  bottom: 140rpx;
  z-index: 20;
  padding: 18rpx 34rpx;
  border-radius: 40rpx;
  font-size: 26rpx;
  font-weight: 600;
  color: #ffffff;
  background: linear-gradient(135deg, #d6978f, #c9707c);
  box-shadow: 0 12rpx 28rpx rgba(201, 112, 124, 0.35);
}

.list {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
  margin-top: 20rpx;
}

.card {
  padding: 24rpx 26rpx;
  border-radius: 22rpx;
  background: #ffffff;
  box-shadow: 0 8rpx 20rpx rgba(104, 74, 68, 0.04);
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.time {
  display: flex;
  align-items: baseline;
}

.time-text {
  font-size: 34rpx;
  font-weight: 700;
  color: #3b3330;
}

.time-text.small {
  font-size: 24rpx;
  font-weight: 400;
  color: #b09a8e;
}

.time-sep {
  margin: 0 6rpx;
  font-size: 22rpx;
  color: #cbbcb2;
}

.head-right {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.source {
  padding: 4rpx 14rpx;
  border-radius: 8rpx;
  font-size: 20rpx;
  color: #8c6bab;
  background: #f1ebf8;
}

.status {
  padding: 4rpx 16rpx;
  border-radius: 8rpx;
  font-size: 21rpx;
}

.status.s1 {
  color: #b07d24;
  background: #fdf4e0;
}

.status.s2 {
  color: #b96a74;
  background: #fdf0ee;
}

.status.s3 {
  color: #4f8d6a;
  background: #e9f4ee;
}

.status.s4 {
  color: #8c827c;
  background: #f1ece8;
}

.status.s5 {
  color: #c07a3c;
  background: #fbf0e4;
}

.card-body {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16rpx;
}

.info {
  display: flex;
  flex: 1;
  flex-direction: column;
}

.service {
  font-size: 29rpx;
  font-weight: 600;
  color: #3b3330;
}

.meta {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: #8c827c;
}

.no {
  margin-top: 6rpx;
  font-size: 20rpx;
  color: #cbbcb2;
}

.price {
  font-size: 30rpx;
  font-weight: 600;
  color: #c97b84;
}

.card-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 18rpx;
  padding-top: 18rpx;
  border-top: 1rpx solid #f5eee8;
}

.primary.small {
  padding: 12rpx 40rpx;
  border-radius: 30rpx;
  font-size: 24rpx;
  color: #ffffff;
  background: linear-gradient(135deg, #d6978f, #c9707c);
}
</style>
