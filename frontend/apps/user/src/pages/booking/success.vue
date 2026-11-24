<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getStoreHome, type StoreHome } from '../../api/catalog'
import type { SubmitResult } from '../../api/booking'
import { displayDate, yuan } from '../../utils/format'
import { subscribeBookingMessages } from '../../utils/subscribe'

const result = ref<SubmitResult | null>(null)
const home = ref<StoreHome | null>(null)
const subscribed = ref(false)

onLoad(async () => {
  result.value = (uni.getStorageSync('reservation_last_reservation') as SubmitResult) || null
  uni.removeStorageSync('reservation_last_reservation')
  try {
    home.value = await getStoreHome()
  } catch {
    home.value = null
  }
})

const waitConfirm = computed(() => result.value?.status === 1)

function copyNo() {
  if (!result.value) {
    return
  }
  uni.setClipboardData({
    data: result.value.no,
    success: () => uni.showToast({ title: '预约号已复制', icon: 'none' })
  })
}

function openMap() {
  const store = home.value?.store
  if (!store) {
    return
  }
  if (!store.lat || !store.lng) {
    uni.setClipboardData({
      data: store.address || '',
      success: () => uni.showToast({ title: '地址已复制', icon: 'none' })
    })
    return
  }
  uni.openLocation({
    latitude: Number(store.lat),
    longitude: Number(store.lng),
    name: store.name,
    address: store.address || '',
    fail: () => {
      uni.setClipboardData({
        data: store.address || '',
        success: () => uni.showToast({ title: '地址已复制', icon: 'none' })
      })
    }
  })
}

function backHome() {
  uni.reLaunch({ url: '/pages/index/index' })
}

function goMyReservations() {
  uni.redirectTo({ url: '/pages/reservation/list' })
}

async function enableSubscribe() {
  if (subscribed.value) {
    return
  }
  try {
    const count = await subscribeBookingMessages()
    if (count > 0) {
      subscribed.value = true
      uni.showToast({ title: '通知已开启', icon: 'none' })
    } else {
      uni.showToast({ title: '当前版本未配置通知模板', icon: 'none' })
    }
  } catch {
    uni.showToast({ title: '开启失败，请稍后重试', icon: 'none' })
  }
}
</script>

<template>
  <view class="page">
    <view v-if="!result" class="state">未找到预约信息</view>

    <template v-else>
      <view class="hero">
        <view class="check">✓</view>
        <text class="hero-title">预约提交成功</text>
        <text class="hero-sub">
          {{ waitConfirm ? '已通知商家，请等待确认' : '预约已确认，请按时到店' }}
        </text>
      </view>

      <view class="card">
        <view class="row" @click="copyNo">
          <text class="label">预约号</text>
          <text class="value mono">{{ result.no }}</text>
        </view>
        <view class="row">
          <text class="label">服务</text>
          <text class="value">{{ result.serviceName }}</text>
        </view>
        <view class="row">
          <text class="label">技师</text>
          <text class="value">{{ result.technicianName }}</text>
        </view>
        <view class="row">
          <text class="label">时间</text>
          <text class="value">{{ displayDate(result.date) }} {{ result.startTime }}-{{ result.endTime }}</text>
        </view>
        <view class="row">
          <text class="label">金额</text>
          <text class="value price">¥{{ yuan(result.priceCents) }}（到店付）</text>
        </view>
        <view class="row">
          <text class="label">状态</text>
          <text class="value" :class="{ pending: waitConfirm }">{{ result.statusText }}</text>
        </view>
      </view>

      <view v-if="!waitConfirm" class="card verify">
        <text class="verify-label">到店核销码</text>
        <text class="verify-code">{{ result.verifyCode }}</text>
        <text class="verify-tip">请到店后向店员出示此码</text>
      </view>

      <view class="card subscribe">
        <view class="subscribe-main">
          <text class="subscribe-title">开启预约提醒</text>
          <text class="subscribe-desc">确认结果、改期变动与到店提醒将通过微信通知你</text>
        </view>
        <view class="subscribe-btn" :class="{ done: subscribed }" @click="enableSubscribe">
          {{ subscribed ? '已开启' : '开启通知' }}
        </view>
      </view>

      <view v-if="home" class="card store">
        <text class="store-name">{{ home.store.name }}</text>
        <text class="store-address">{{ home.store.address || '门店筹备中' }}</text>
        <view class="store-actions">
          <view class="ghost" @click="openMap">导航到店</view>
          <view class="primary" @click="goMyReservations">查看我的预约</view>
        </view>
        <view class="home-link" @click="backHome">返回首页</view>
      </view>

      <view v-else class="card store">
        <view class="store-actions">
          <view class="ghost" @click="goMyReservations">查看我的预约</view>
          <view class="ghost" @click="backHome">返回首页</view>
        </view>
      </view>

      <view class="footer">可在「我的预约」中自助改期或取消</view>
    </template>
  </view>
</template>

<style scoped>
.page {
  min-height: 100vh;
  padding: 48rpx 32rpx;
  background: #faf6f2;
}

.hero {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 32rpx 0 40rpx;
}

.check {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 120rpx;
  height: 120rpx;
  border-radius: 50%;
  font-size: 64rpx;
  color: #ffffff;
  background: linear-gradient(135deg, #d6978f, #c9707c);
  box-shadow: 0 16rpx 40rpx rgba(201, 112, 124, 0.32);
}

.hero-title {
  margin-top: 28rpx;
  font-size: 36rpx;
  font-weight: 700;
  color: #3b3330;
}

.hero-sub {
  margin-top: 12rpx;
  font-size: 24rpx;
  color: #b09a8e;
}

.card {
  margin-bottom: 24rpx;
  padding: 12rpx 28rpx;
  border-radius: 24rpx;
  background: #ffffff;
  box-shadow: 0 10rpx 28rpx rgba(104, 74, 68, 0.05);
}

.row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: 20rpx 0;
  border-bottom: 1rpx solid #f7f0ea;
}

.row:last-child {
  border-bottom: none;
}

.label {
  font-size: 24rpx;
  color: #b09a8e;
}

.value {
  max-width: 70%;
  text-align: right;
  font-size: 26rpx;
  color: #3b3330;
}

.value.mono {
  font-weight: 600;
  letter-spacing: 1rpx;
  color: #c9707c;
}

.value.price {
  color: #c97b84;
}

.value.pending {
  color: #d9a441;
}

.verify {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 36rpx 28rpx;
}

.verify-label {
  font-size: 24rpx;
  color: #b09a8e;
}

.verify-code {
  margin-top: 12rpx;
  font-size: 64rpx;
  font-weight: 700;
  letter-spacing: 12rpx;
  color: #3b3330;
}

.verify-tip {
  margin-top: 10rpx;
  font-size: 22rpx;
  color: #b09a8e;
}

.store {
  display: flex;
  flex-direction: column;
  padding: 28rpx;
}

.store-name {
  font-size: 28rpx;
  font-weight: 600;
  color: #3b3330;
}

.store-address {
  margin-top: 10rpx;
  font-size: 24rpx;
  color: #8c827c;
}

.store-actions {
  display: flex;
  gap: 20rpx;
  margin-top: 24rpx;
}

.ghost {
  flex: 1;
  padding: 18rpx 0;
  border-radius: 32rpx;
  text-align: center;
  font-size: 26rpx;
  color: #c9707c;
  border: 2rpx solid #e8c9c4;
}

.primary {
  flex: 1;
  padding: 18rpx 0;
  border-radius: 32rpx;
  text-align: center;
  font-size: 26rpx;
  font-weight: 600;
  color: #ffffff;
  background: linear-gradient(135deg, #d6978f, #c9707c);
}

.home-link {
  margin-top: 20rpx;
  text-align: center;
  font-size: 22rpx;
  color: #b09a8e;
}

.subscribe {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.subscribe-main {
  display: flex;
  flex: 1;
  flex-direction: column;
  margin-right: 20rpx;
}

.subscribe-title {
  font-size: 28rpx;
  font-weight: 600;
  color: #3b3330;
}

.subscribe-desc {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: #b09a8e;
}

.subscribe-btn {
  flex-shrink: 0;
  padding: 14rpx 32rpx;
  border-radius: 30rpx;
  font-size: 24rpx;
  color: #ffffff;
  background: linear-gradient(135deg, #d6978f, #c9707c);
}

.subscribe-btn.done {
  color: #b09a8e;
  background: #f1ece8;
}

.footer {
  padding: 24rpx 0;
  text-align: center;
  font-size: 20rpx;
  color: #cbbcb2;
}

.state {
  padding: 120rpx 0;
  text-align: center;
  font-size: 26rpx;
  color: #b09a8e;
}
</style>
