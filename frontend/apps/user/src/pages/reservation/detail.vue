<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { getStoreHome } from '../../api/catalog'
import { cancelReservation, getReservation, type ReservationDetail } from '../../api/reservation'
import { ensureUserLogin } from '../../utils/session'
import { displayDate, yuan } from '../../utils/format'

const reservationId = ref(0)
const detail = ref<ReservationDetail | null>(null)
const storePhone = ref('')
const loading = ref(true)
const error = ref('')

const STATUS_HINT: Record<number, string> = {
  1: '已提交，等待商家确认',
  2: '请按时到店，向店员出示核销码',
  3: '服务已完成，期待再次光临',
  4: '预约已取消',
  5: '预约未履约，如有疑问请联系门店'
}

const hint = computed(() => {
  if (!detail.value) {
    return ''
  }
  if (detail.value.status === 4 && detail.value.cancelReason) {
    return detail.value.cancelReason
  }
  return STATUS_HINT[detail.value.status] || ''
})

/** 时间线最新记录置顶展示；detail 为内部审计串，不对顾客展示。 */
const timelineDesc = computed(() => (detail.value ? [...detail.value.timeline].reverse() : []))

onLoad((options) => {
  reservationId.value = Number(options?.id || 0)
})

onShow(async () => {
  if (!reservationId.value) {
    error.value = '预约不存在'
    loading.value = false
    return
  }
  await load()
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    await ensureUserLogin()
    detail.value = await getReservation(reservationId.value)
    if (!storePhone.value) {
      const home = await getStoreHome().catch(() => null)
      storePhone.value = home?.store.phone || ''
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

function timeText(value: string): string {
  if (!value) {
    return ''
  }
  return value.replace('T', ' ').slice(5, 16)
}

function cancel() {
  const current = detail.value
  if (!current) {
    return
  }
  uni.showModal({
    title: '取消预约',
    content: `确认取消 ${displayDate(current.date)} ${current.startTime} 的预约吗？`,
    confirmText: '确认取消',
    cancelText: '再想想',
    confirmColor: '#C9707C',
    success: async (res) => {
      if (!res.confirm) {
        return
      }
      try {
        detail.value = await cancelReservation(current.id, '顾客取消')
        uni.showToast({ title: '已取消预约', icon: 'none' })
      } catch (e) {
        uni.showToast({ title: e instanceof Error ? e.message : '取消失败', icon: 'none' })
      }
    }
  })
}

function reschedule() {
  if (!detail.value) {
    return
  }
  uni.navigateTo({ url: `/pages/reservation/reschedule?id=${detail.value.id}` })
}

function callStore() {
  if (!storePhone.value) {
    uni.showToast({ title: '门店电话未配置', icon: 'none' })
    return
  }
  uni.makePhoneCall({
    phoneNumber: storePhone.value,
    fail: () => {
      uni.setClipboardData({
        data: storePhone.value,
        success: () => uni.showToast({ title: '电话已复制', icon: 'none' })
      })
    }
  })
}

function goBack() {
  uni.navigateBack()
}
</script>

<template>
  <view class="page">
    <view v-if="loading" class="state">加载中…</view>
    <view v-else-if="error" class="state error">
      <text>{{ error }}</text>
      <view class="back" @click="goBack">返回</view>
    </view>

    <template v-else-if="detail">
      <view class="banner" :class="`s${detail.status}`">
        <text class="banner-status">{{ detail.statusText }}</text>
        <text class="banner-hint">{{ hint }}</text>
      </view>

      <view class="panel">
        <view class="service-row">
          <image v-if="detail.serviceCover" class="cover" :src="detail.serviceCover" mode="aspectFill" />
          <view v-else class="cover cover-fallback">{{ detail.serviceName.slice(0, 1) }}</view>
          <view class="service-info">
            <text class="service-name">{{ detail.serviceName }}</text>
            <text class="service-meta">{{ detail.technicianName }} · {{ detail.durationMin }}分钟</text>
            <text class="service-meta">{{ displayDate(detail.date) }} {{ detail.startTime }}-{{ detail.endTime }}</text>
          </view>
          <text class="price">¥{{ yuan(detail.priceCents) }}</text>
        </view>
        <view class="row">
          <text class="label">预约号</text>
          <text class="value mono">{{ detail.no }}</text>
        </view>
      </view>

      <view v-if="detail.status === 2 && detail.verifyCode" class="panel verify">
        <text class="verify-label">到店核销码</text>
        <text class="verify-code">{{ detail.verifyCode }}</text>
        <text class="verify-tip">请到店后向店员出示此码</text>
      </view>

      <view class="panel">
        <text class="panel-title">联系信息</text>
        <view class="row">
          <text class="label">联系人</text>
          <text class="value">{{ detail.contactName }}</text>
        </view>
        <view class="row">
          <text class="label">手机号</text>
          <text class="value">{{ detail.contactPhone }}</text>
        </view>
        <view v-if="detail.remark" class="row">
          <text class="label">备注</text>
          <text class="value">{{ detail.remark }}</text>
        </view>
      </view>

      <view class="panel">
        <text class="panel-title">预约动态</text>
        <view class="timeline">
          <view v-for="(item, index) in timelineDesc" :key="index" class="timeline-item">
            <view class="timeline-axis">
              <view class="timeline-dot" :class="{ first: index === 0 }" />
              <view v-if="index < timelineDesc.length - 1" class="timeline-line" />
            </view>
            <view class="timeline-content">
              <view class="timeline-head">
                <text class="timeline-action">{{ item.actionText }}</text>
                <text class="timeline-time">{{ timeText(item.createdAt) }}</text>
              </view>
            </view>
          </view>
        </view>
      </view>

      <view class="safe-gap" />

      <view class="bar">
        <view v-if="detail.canCancel || detail.canReschedule" class="bar-actions">
          <view v-if="detail.canCancel" class="ghost" @click="cancel">取消预约</view>
          <view v-if="detail.canReschedule" class="primary" @click="reschedule">改期</view>
        </view>
        <view v-else class="bar-actions">
          <view class="ghost full" @click="callStore">联系门店</view>
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

.banner {
  display: flex;
  flex-direction: column;
  margin-bottom: 24rpx;
  padding: 32rpx;
  border-radius: 24rpx;
  background: linear-gradient(135deg, #d6978f, #c9707c);
}

.banner.s1 {
  background: linear-gradient(135deg, #e5c07b, #d9a441);
}

.banner.s3 {
  background: linear-gradient(135deg, #7fbf9a, #4f8d6a);
}

.banner.s4 {
  background: linear-gradient(135deg, #b8aca4, #8c827c);
}

.banner.s5 {
  background: linear-gradient(135deg, #e0a368, #c07a3c);
}

.banner-status {
  font-size: 36rpx;
  font-weight: 700;
  color: #ffffff;
}

.banner-hint {
  margin-top: 10rpx;
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.86);
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
  padding-bottom: 20rpx;
  border-bottom: 1rpx solid #f7f0ea;
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

.service-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  margin-left: 22rpx;
}

.service-name {
  font-size: 30rpx;
  font-weight: 600;
  color: #3b3330;
}

.service-meta {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: #b09a8e;
}

.price {
  font-size: 30rpx;
  font-weight: 600;
  color: #c97b84;
}

.row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: 18rpx 0 0;
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

.panel-title {
  display: block;
  margin-bottom: 8rpx;
  font-size: 28rpx;
  font-weight: 600;
  color: #3b3330;
}

.verify {
  display: flex;
  flex-direction: column;
  align-items: center;
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

.timeline {
  margin-top: 16rpx;
}

.timeline-item {
  display: flex;
}

.timeline-axis {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 32rpx;
  margin-right: 18rpx;
}

.timeline-dot {
  width: 16rpx;
  height: 16rpx;
  margin-top: 10rpx;
  border-radius: 50%;
  background: #ddcfc6;
}

.timeline-dot.first {
  background: #c9707c;
  box-shadow: 0 0 0 6rpx rgba(201, 112, 124, 0.15);
}

.timeline-line {
  flex: 1;
  width: 2rpx;
  margin: 6rpx 0;
  background: #f0e7e0;
}

.timeline-content {
  flex: 1;
  padding-bottom: 28rpx;
}

.timeline-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}

.timeline-action {
  font-size: 26rpx;
  font-weight: 600;
  color: #3b3330;
}

.timeline-time {
  font-size: 20rpx;
  color: #cbbcb2;
}

.safe-gap {
  height: 140rpx;
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

.bar-actions {
  display: flex;
  gap: 20rpx;
}

.ghost {
  flex: 1;
  padding: 20rpx 0;
  border-radius: 40rpx;
  text-align: center;
  font-size: 28rpx;
  color: #c9707c;
  border: 2rpx solid #e8c9c4;
}

.ghost.full {
  flex: 1;
}

.primary {
  flex: 1;
  padding: 20rpx 0;
  border-radius: 40rpx;
  text-align: center;
  font-size: 28rpx;
  font-weight: 600;
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

.back {
  display: inline-block;
  margin-top: 24rpx;
  padding: 12rpx 48rpx;
  border-radius: 32rpx;
  font-size: 24rpx;
  color: #ffffff;
  background: #c97b84;
}
</style>
