<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { ensureUserLogin } from '../../utils/session'
import { cancelReservation, listReservations, type ReservationItem } from '../../api/reservation'
import { displayDate, yuan } from '../../utils/format'

const tabs = [
  { key: 0, text: '全部' },
  { key: 1, text: '待确认' },
  { key: 2, text: '待服务' },
  { key: 3, text: '已完成' },
  { key: 4, text: '已取消' },
  { key: 5, text: '未到店' }
] as const

const activeTab = ref(0)
const items = ref<ReservationItem[]>([])
const loading = ref(true)
const error = ref('')

onShow(load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    await ensureUserLogin()
    items.value = await listReservations(activeTab.value || undefined)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

function selectTab(key: number) {
  if (activeTab.value === key) {
    return
  }
  activeTab.value = key
  load()
}

function goDetail(item: ReservationItem) {
  uni.navigateTo({ url: `/pages/reservation/detail?id=${item.id}` })
}

function goBooking() {
  uni.reLaunch({ url: '/pages/index/index' })
}

function cancel(item: ReservationItem) {
  uni.showModal({
    title: '取消预约',
    content: `确认取消「${item.serviceName}」${displayDate(item.date)} ${item.startTime} 的预约吗？`,
    confirmText: '确认取消',
    cancelText: '再想想',
    confirmColor: '#C9707C',
    success: async (res) => {
      if (!res.confirm) {
        return
      }
      try {
        await cancelReservation(item.id, '顾客取消')
        uni.showToast({ title: '已取消预约', icon: 'none' })
        await load()
      } catch (e) {
        uni.showToast({ title: e instanceof Error ? e.message : '取消失败', icon: 'none' })
      }
    }
  })
}

function reschedule(item: ReservationItem) {
  uni.navigateTo({ url: `/pages/reservation/reschedule?id=${item.id}` })
}
</script>

<template>
  <view class="page">
    <view class="tabs">
      <view
        v-for="tab in tabs"
        :key="tab.key"
        class="tab"
        :class="{ active: activeTab === tab.key }"
        @click="selectTab(tab.key)"
      >
        {{ tab.text }}
      </view>
    </view>

    <view v-if="loading" class="state">加载中…</view>
    <view v-else-if="error" class="state error">{{ error }}</view>
    <view v-else-if="!items.length" class="state empty">
      <text class="empty-icon">🗓</text>
      <text class="empty-text">{{ activeTab === 0 ? '还没有预约，去挑一个心仪的服务吧' : '暂无该状态的预约' }}</text>
      <view class="empty-button" @click="goBooking">去预约</view>
    </view>

    <view v-else class="list">
      <view v-for="item in items" :key="item.id" class="card" @click="goDetail(item)">
        <view class="card-head">
          <text class="status" :class="`s${item.status}`">{{ item.statusText }}</text>
          <text class="no">{{ item.no }}</text>
        </view>
        <view class="card-body">
          <image v-if="item.serviceCover" class="cover" :src="item.serviceCover" mode="aspectFill" />
          <view v-else class="cover cover-fallback">{{ item.serviceName.slice(0, 1) }}</view>
          <view class="info">
            <text class="name">{{ item.serviceName }}</text>
            <text class="meta">{{ item.technicianName }} · {{ displayDate(item.date) }}</text>
            <text class="meta">{{ item.startTime }}-{{ item.endTime }}</text>
          </view>
          <text class="price">¥{{ yuan(item.priceCents) }}</text>
        </view>
        <view v-if="item.canCancel || item.canReschedule" class="card-actions">
          <view v-if="item.canCancel" class="ghost" @click.stop="cancel(item)">取消预约</view>
          <view v-if="item.canReschedule" class="primary" @click.stop="reschedule(item)">改期</view>
        </view>
      </view>
    </view>
  </view>
</template>

<style scoped>
.page {
  min-height: 100vh;
  padding: 20rpx 32rpx 48rpx;
  background: #faf6f2;
}

.tabs {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14rpx;
  margin-bottom: 24rpx;
}

.tab {
  padding: 12rpx 0;
  border-radius: 12rpx;
  text-align: center;
  font-size: 25rpx;
  color: #8c827c;
  background: #ffffff;
}

.tab.active {
  color: #ffffff;
  background: #c97b84;
}

.list {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.card {
  padding: 24rpx 28rpx;
  border-radius: 24rpx;
  background: #ffffff;
  box-shadow: 0 10rpx 28rpx rgba(104, 74, 68, 0.05);
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20rpx;
}

.status {
  padding: 4rpx 18rpx;
  border-radius: 8rpx;
  font-size: 22rpx;
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

.no {
  font-size: 22rpx;
  color: #cbbcb2;
}

.card-body {
  display: flex;
  align-items: center;
}

.cover {
  width: 120rpx;
  height: 120rpx;
  border-radius: 18rpx;
  flex-shrink: 0;
}

.cover-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 44rpx;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.9);
  background: linear-gradient(135deg, #e3c2bc, #c9968f);
}

.info {
  flex: 1;
  display: flex;
  flex-direction: column;
  margin-left: 22rpx;
}

.name {
  font-size: 30rpx;
  font-weight: 600;
  color: #3b3330;
}

.meta {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: #b09a8e;
}

.price {
  font-size: 30rpx;
  font-weight: 600;
  color: #c97b84;
}

.card-actions {
  display: flex;
  justify-content: flex-end;
  gap: 20rpx;
  margin-top: 20rpx;
  padding-top: 20rpx;
  border-top: 1rpx solid #f5eee8;
}

.ghost {
  padding: 12rpx 36rpx;
  border-radius: 30rpx;
  font-size: 24rpx;
  color: #8c827c;
  border: 2rpx solid #e8ddd4;
}

.primary {
  padding: 12rpx 44rpx;
  border-radius: 30rpx;
  font-size: 24rpx;
  color: #ffffff;
  background: linear-gradient(135deg, #d6978f, #c9707c);
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
  font-size: 72rpx;
}

.empty-text {
  margin-top: 20rpx;
  font-size: 26rpx;
}

.empty-button {
  margin-top: 28rpx;
  padding: 16rpx 64rpx;
  border-radius: 36rpx;
  font-size: 26rpx;
  color: #ffffff;
  background: #c97b84;
}
</style>
