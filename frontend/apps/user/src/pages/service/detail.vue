<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getServiceDetail, type ServiceDetail } from '../../api/catalog'
import { durationText, yuan } from '../../utils/format'

const serviceId = ref(0)
const detail = ref<ServiceDetail | null>(null)
const loading = ref(true)
const error = ref('')

const gallery = computed(() => {
  if (!detail.value) {
    return []
  }
  const urls = [detail.value.cover, ...detail.value.images].filter(Boolean)
  return Array.from(new Set(urls))
})

onLoad(async (options) => {
  serviceId.value = Number(options?.id || 0)
  if (!serviceId.value) {
    error.value = '服务不存在'
    loading.value = false
    return
  }
  await load()
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    detail.value = await getServiceDetail(serviceId.value)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '服务加载失败'
  } finally {
    loading.value = false
  }
}

function goBooking() {
  if (!detail.value) {
    return
  }
  uni.navigateTo({ url: `/pages/booking/index?serviceId=${detail.value.id}` })
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
      <view class="back" @click="goBack">返回列表</view>
    </view>

    <template v-else-if="detail">
      <swiper v-if="gallery.length" class="gallery" circular indicator-dots indicator-active-color="#ffffff">
        <swiper-item v-for="(url, index) in gallery" :key="index">
          <image class="gallery-img" :src="url" mode="aspectFill" />
        </swiper-item>
      </swiper>
      <view v-else class="gallery gallery-fallback">{{ detail.name.slice(0, 1) }}</view>

      <view class="panel">
        <view class="title-row">
          <text class="name">{{ detail.name }}</text>
          <text class="price">¥{{ yuan(detail.priceCents) }}</text>
        </view>
        <view class="meta-row">
          <text class="tag">{{ detail.categoryName }}</text>
          <text class="meta">{{ durationText(detail.durationMin) }}</text>
          <text class="meta">已约 {{ detail.bookedCount }} 次</text>
          <text class="pay">到店付</text>
        </view>
      </view>

      <view v-if="detail.technicians.length" class="panel">
        <text class="panel-title">可约技师</text>
        <scroll-view scroll-x class="tech-row" :show-scrollbar="false">
          <view v-for="technician in detail.technicians" :key="technician.id" class="tech">
            <image v-if="technician.avatar" class="tech-avatar" :src="technician.avatar" mode="aspectFill" />
            <view v-else class="tech-avatar tech-avatar-fallback">{{ technician.name.slice(0, 1) }}</view>
            <text class="tech-name">{{ technician.name }}</text>
            <text class="tech-title">{{ technician.title || '技师' }}</text>
          </view>
        </scroll-view>
      </view>

      <view v-if="detail.description" class="panel">
        <text class="panel-title">服务说明</text>
        <text class="panel-text">{{ detail.description }}</text>
      </view>

      <view v-if="detail.notes" class="panel">
        <text class="panel-title">注意事项</text>
        <text class="panel-text">{{ detail.notes }}</text>
      </view>

      <view class="safe-gap" />

      <view class="bar">
        <view class="bar-price">
          <text class="bar-label">服务价</text>
          <text class="bar-value">¥{{ yuan(detail.priceCents) }}</text>
        </view>
        <view class="bar-button" @click="goBooking">立即预约</view>
      </view>
    </template>
  </view>
</template>

<style scoped>
.page {
  min-height: 100vh;
  padding-bottom: 160rpx;
  background: #faf6f2;
}

.gallery {
  height: 480rpx;
}

.gallery-img {
  width: 100%;
  height: 100%;
}

.gallery-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 120rpx;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.9);
  background: linear-gradient(135deg, #e3c2bc, #c9968f);
}

.panel {
  margin: 24rpx 32rpx 0;
  padding: 28rpx;
  border-radius: 24rpx;
  background: #ffffff;
  box-shadow: 0 10rpx 28rpx rgba(104, 74, 68, 0.05);
}

.title-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}

.name {
  flex: 1;
  font-size: 36rpx;
  font-weight: 700;
  color: #3b3330;
}

.price {
  margin-left: 24rpx;
  font-size: 38rpx;
  font-weight: 700;
  color: #c97b84;
}

.meta-row {
  display: flex;
  align-items: center;
  gap: 20rpx;
  margin-top: 18rpx;
}

.tag {
  padding: 4rpx 16rpx;
  border-radius: 8rpx;
  font-size: 20rpx;
  color: #b97f80;
  background: #f7e9e6;
}

.meta {
  font-size: 24rpx;
  color: #8c827c;
}

.pay {
  margin-left: auto;
  font-size: 22rpx;
  color: #b09a8e;
}

.panel-title {
  display: block;
  font-size: 28rpx;
  font-weight: 600;
  color: #3b3330;
  margin-bottom: 18rpx;
}

.panel-text {
  font-size: 26rpx;
  line-height: 1.8;
  color: #6f635c;
}

.tech-row {
  white-space: nowrap;
}

.tech {
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  width: 140rpx;
  margin-right: 24rpx;
}

.tech-avatar {
  width: 96rpx;
  height: 96rpx;
  border-radius: 50%;
}

.tech-avatar-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 36rpx;
  color: #ffffff;
  background: linear-gradient(135deg, #d8a8a4, #b97f80);
}

.tech-name {
  margin-top: 10rpx;
  font-size: 24rpx;
  color: #3b3330;
}

.tech-title {
  font-size: 20rpx;
  color: #b09a8e;
}

.safe-gap {
  height: 40rpx;
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
  padding: 22rpx 88rpx;
  border-radius: 44rpx;
  font-size: 30rpx;
  font-weight: 600;
  color: #ffffff;
  background: linear-gradient(135deg, #d6978f, #c9707c);
  box-shadow: 0 10rpx 24rpx rgba(201, 112, 124, 0.3);
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
