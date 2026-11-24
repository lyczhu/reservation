<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { BRAND, USER_PRIVACY_AGREED_KEY } from '@reservation/shared'
import { getStoreHome, type Category, type ServiceCard, type StoreHome } from '../../api/catalog'
import { durationText, yuan } from '../../utils/format'

const loading = ref(true)
const error = ref('')
const home = ref<StoreHome | null>(null)
const privacyVisible = ref(false)

const store = computed(() => home.value?.store ?? null)
const banners = computed(() => home.value?.store.banners ?? [])
const categories = computed(() => home.value?.categories ?? [])
const hotServices = computed(() => home.value?.hotServices ?? [])
const noticeText = computed(() => home.value?.notice || home.value?.store.announcement || '')

const todayWeekday = () => {
  const day = new Date().getDay()
  return day === 0 ? 7 : day
}

const todayRest = computed(() => {
  if (!home.value) {
    return false
  }
  return home.value.store.restDays.includes(todayWeekday())
})

const todayHoursText = computed(() => {
  if (!home.value) {
    return ''
  }
  const day = home.value.store.businessHours.find((item) => item.weekday === todayWeekday())
  if (!day || !day.intervals.length) {
    return '今日休息'
  }
  return `今日营业 ${day.intervals.map((item) => `${item.start}-${item.end}`).join(' / ')}`
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    home.value = await getStoreHome()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

onLoad(load)

// 首次进入强制展示隐私保护提示，同意后方可预约（微信审核合规要求）
onShow(() => {
  privacyVisible.value = !uni.getStorageSync(USER_PRIVACY_AGREED_KEY)
})

function agreePrivacy() {
  uni.setStorageSync(USER_PRIVACY_AGREED_KEY, 1)
  privacyVisible.value = false
}

function declinePrivacy() {
  privacyVisible.value = false
  uni.showToast({ title: '同意隐私政策后才能预约哦', icon: 'none' })
}

function openPrivacyDoc() {
  uni.navigateTo({ url: '/pages/legal/privacy' })
}

function openAgreementDoc() {
  uni.navigateTo({ url: '/pages/legal/agreement' })
}

function goMe() {
  uni.navigateTo({ url: '/pages/me/index' })
}

function openMap() {
  const current = store.value
  if (!current) {
    return
  }
  if (!current.lat || !current.lng) {
    copyAddress()
    return
  }
  uni.openLocation({
    latitude: Number(current.lat),
    longitude: Number(current.lng),
    name: current.name,
    address: current.address || '',
    fail: () => copyAddress()
  })
}

function copyAddress() {
  const address = store.value?.address
  if (!address) {
    uni.showToast({ title: '门店筹备中', icon: 'none' })
    return
  }
  uni.setClipboardData({
    data: address,
    success: () => uni.showToast({ title: '地址已复制', icon: 'none' })
  })
}

function callStore() {
  const phone = store.value?.phone
  if (!phone) {
    uni.showToast({ title: '门店电话未配置', icon: 'none' })
    return
  }
  uni.makePhoneCall({
    phoneNumber: phone,
    fail: () => {
      uni.setClipboardData({
        data: phone,
        success: () => uni.showToast({ title: '电话已复制', icon: 'none' })
      })
    }
  })
}

function goCategory(category: Category) {
  uni.navigateTo({
    url: `/pages/service/list?categoryId=${category.id}&name=${encodeURIComponent(category.name)}`
  })
}

function goAllServices() {
  uni.navigateTo({ url: '/pages/service/list' })
}

function goMyReservations() {
  uni.navigateTo({ url: '/pages/reservation/list' })
}

function goDetail(service: ServiceCard) {
  uni.navigateTo({ url: `/pages/service/detail?id=${service.id}` })
}
</script>

<template>
  <view class="home">
    <view class="hero">
      <view class="hero-row">
        <view class="hero-left">
          <text class="hero-brand">{{ BRAND.appName }}</text>
          <text class="hero-slogan">{{ BRAND.slogan }}</text>
        </view>
        <view class="hero-actions">
          <view class="hero-link" @click="goMyReservations">我的预约 ›</view>
          <view class="hero-link" @click="goMe">我的 ›</view>
        </view>
      </view>
    </view>

    <view v-if="noticeText" class="notice">
      <text class="notice-dot" />
      <text class="notice-text">{{ noticeText }}</text>
    </view>

    <swiper
      v-if="banners.length"
      class="banner"
      circular
      autoplay
      :interval="4000"
      indicator-dots
      indicator-active-color="#ffffff"
    >
      <swiper-item v-for="(url, index) in banners" :key="index">
        <image class="banner-img" :src="url" mode="aspectFill" />
      </swiper-item>
    </swiper>
    <view v-else class="banner banner-fallback">
      <text class="fallback-title">让美好如约而至</text>
      <text class="fallback-sub">预约属于你的片刻宁静</text>
    </view>

    <view v-if="store" class="store-card">
      <view class="store-head">
        <image v-if="store.logo" class="store-logo" :src="store.logo" mode="aspectFill" />
        <view v-else class="store-logo store-logo-fallback">{{ store.name.slice(0, 1) }}</view>
        <view class="store-info">
          <text class="store-name">{{ store.name }}</text>
          <text class="store-hours" :class="{ closed: todayRest }">{{ todayHoursText }}</text>
        </view>
        <view class="store-actions">
          <view class="action" @click="openMap">
            <text class="action-icon">📍</text>
            <text class="action-text">导航</text>
          </view>
          <view class="action" @click="callStore">
            <text class="action-icon">☎</text>
            <text class="action-text">电话</text>
          </view>
        </view>
      </view>
      <view class="store-address" @click="openMap">{{ store.address || '门店筹备中' }}</view>
    </view>

    <view v-if="loading" class="state">正在准备你的美丽时光…</view>
    <view v-else-if="error" class="state error">
      <text>{{ error }}</text>
      <view class="retry" @click="load">重新加载</view>
    </view>

    <view v-if="categories.length" class="section">
      <view class="section-head">
        <text class="section-title">服务分类</text>
      </view>
      <view class="cat-grid">
        <view v-for="category in categories" :key="category.id" class="cat-item" @click="goCategory(category)">
          <image v-if="category.icon" class="cat-icon" :src="category.icon" mode="aspectFill" />
          <view v-else class="cat-icon cat-icon-fallback">{{ category.name.slice(0, 1) }}</view>
          <text class="cat-name">{{ category.name }}</text>
        </view>
      </view>
    </view>

    <view v-if="hotServices.length" class="section">
      <view class="section-head">
        <text class="section-title">热门服务</text>
        <text class="section-more" @click="goAllServices">查看全部 ›</text>
      </view>
      <view class="svc-grid">
        <view v-for="service in hotServices" :key="service.id" class="svc-card" @click="goDetail(service)">
          <image v-if="service.cover" class="svc-cover" :src="service.cover" mode="aspectFill" />
          <view v-else class="svc-cover svc-cover-fallback">{{ service.name.slice(0, 1) }}</view>
          <view class="svc-body">
            <text class="svc-name">{{ service.name }}</text>
            <view class="svc-meta">
              <text class="svc-price">¥{{ yuan(service.priceCents) }}</text>
              <text class="svc-duration">{{ durationText(service.durationMin) }}</text>
            </view>
            <text class="svc-booked">已约 {{ service.bookedCount }} 次</text>
          </view>
        </view>
      </view>
    </view>

    <view class="footer">{{ BRAND.homeFooter }}</view>

    <view v-if="privacyVisible" class="privacy-mask">
      <view class="privacy-card">
        <text class="privacy-title">隐私保护提示</text>
        <text class="privacy-body">
          在你使用预约服务前，请阅读并同意
          <text class="privacy-link" @click="openPrivacyDoc">《隐私政策》</text>
          与
          <text class="privacy-link" @click="openAgreementDoc">《用户协议》</text>
          。我们仅在提供预约服务所必需的范围内收集与使用你的信息。
        </text>
        <view class="privacy-actions">
          <view class="privacy-decline" @click="declinePrivacy">暂不</view>
          <view class="privacy-agree" @click="agreePrivacy">同意并继续</view>
        </view>
      </view>
    </view>
  </view>
</template>

<style scoped>
.home {
  min-height: 100vh;
  padding: 0 32rpx 48rpx;
  background: #faf6f2;
}

.hero {
  padding: 36rpx 8rpx 20rpx;
}

.hero-row {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
}

.hero-left {
  display: flex;
  flex-direction: column;
}

.hero-actions {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.hero-link {
  padding: 10rpx 20rpx;
  border-radius: 28rpx;
  font-size: 24rpx;
  color: #c9707c;
  background: #fdf0ee;
}

.hero-brand {
  font-size: 48rpx;
  font-weight: 700;
  letter-spacing: 8rpx;
  color: #3b3330;
}

.hero-slogan {
  margin-top: 10rpx;
  font-size: 24rpx;
  letter-spacing: 4rpx;
  color: #b09a8e;
}

.notice {
  display: flex;
  align-items: center;
  margin-bottom: 20rpx;
  padding: 16rpx 24rpx;
  border-radius: 16rpx;
  background: #fdf0ee;
}

.notice-dot {
  width: 12rpx;
  height: 12rpx;
  margin-right: 14rpx;
  border-radius: 50%;
  background: #c97b84;
}

.notice-text {
  flex: 1;
  font-size: 24rpx;
  color: #a4585f;
}

.banner {
  height: 300rpx;
  margin-bottom: 24rpx;
  border-radius: 28rpx;
  overflow: hidden;
}

.banner-img {
  width: 100%;
  height: 100%;
}

.banner-fallback {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #e8c9c4 0%, #d8a8a4 55%, #b97f80 100%);
}

.fallback-title {
  font-size: 40rpx;
  font-weight: 600;
  letter-spacing: 6rpx;
  color: #fffdfb;
}

.fallback-sub {
  margin-top: 14rpx;
  font-size: 24rpx;
  letter-spacing: 3rpx;
  color: rgba(255, 253, 251, 0.82);
}

.store-card {
  padding: 28rpx;
  margin-bottom: 32rpx;
  border-radius: 28rpx;
  background: #ffffff;
  box-shadow: 0 12rpx 32rpx rgba(104, 74, 68, 0.06);
}

.store-head {
  display: flex;
  align-items: center;
}

.store-logo {
  width: 92rpx;
  height: 92rpx;
  border-radius: 24rpx;
}

.store-logo-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 40rpx;
  font-weight: 600;
  color: #ffffff;
  background: linear-gradient(135deg, #d8a8a4, #b97f80);
}

.store-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  margin-left: 20rpx;
}

.store-name {
  font-size: 32rpx;
  font-weight: 600;
  color: #3b3330;
}

.store-hours {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: #8c827c;
}

.store-hours.closed {
  color: #c97b84;
}

.store-actions {
  display: flex;
  gap: 16rpx;
}

.action {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 88rpx;
  height: 88rpx;
  border-radius: 20rpx;
  background: #faf4ef;
}

.action-icon {
  font-size: 28rpx;
}

.action-text {
  margin-top: 4rpx;
  font-size: 20rpx;
  color: #8c7a6e;
}

.store-address {
  margin-top: 20rpx;
  padding-top: 20rpx;
  font-size: 24rpx;
  color: #8c827c;
  border-top: 1rpx solid #f3ece6;
}

.section {
  margin-bottom: 36rpx;
}

.section-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 20rpx;
  padding: 0 8rpx;
}

.section-title {
  font-size: 32rpx;
  font-weight: 600;
  color: #3b3330;
}

.section-more {
  font-size: 24rpx;
  color: #b09a8e;
}

.cat-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20rpx;
}

.cat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 24rpx 0 20rpx;
  border-radius: 24rpx;
  background: #ffffff;
}

.cat-icon {
  width: 76rpx;
  height: 76rpx;
  border-radius: 50%;
}

.cat-icon-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 30rpx;
  font-weight: 600;
  color: #b97f80;
  background: #f7e9e6;
}

.cat-name {
  margin-top: 12rpx;
  font-size: 24rpx;
  color: #5a4f49;
}

.svc-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 24rpx;
}

.svc-card {
  border-radius: 24rpx;
  overflow: hidden;
  background: #ffffff;
  box-shadow: 0 10rpx 28rpx rgba(104, 74, 68, 0.05);
}

.svc-cover {
  width: 100%;
  height: 220rpx;
}

.svc-cover-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 56rpx;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.9);
  background: linear-gradient(135deg, #e3c2bc, #c9968f);
}

.svc-body {
  padding: 18rpx 20rpx 22rpx;
}

.svc-name {
  font-size: 28rpx;
  font-weight: 600;
  color: #3b3330;
}

.svc-meta {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-top: 12rpx;
}

.svc-price {
  font-size: 30rpx;
  font-weight: 600;
  color: #c97b84;
}

.svc-duration {
  font-size: 22rpx;
  color: #b09a8e;
}

.svc-booked {
  display: block;
  margin-top: 10rpx;
  font-size: 20rpx;
  color: #b09a8e;
}

.state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 80rpx 0;
  font-size: 26rpx;
  color: #b09a8e;
}

.state.error {
  color: #c97b84;
}

.retry {
  margin-top: 24rpx;
  padding: 12rpx 48rpx;
  border-radius: 32rpx;
  font-size: 24rpx;
  color: #ffffff;
  background: #c97b84;
}

.footer {
  padding: 32rpx 0 16rpx;
  text-align: center;
  font-size: 20rpx;
  letter-spacing: 2rpx;
  color: #cbbcb2;
}
.privacy-mask {
  position: fixed;
  inset: 0;
  z-index: 30;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 60rpx;
  background: rgba(59, 51, 48, 0.5);
}

.privacy-card {
  width: 100%;
  padding: 40rpx 36rpx 32rpx;
  border-radius: 28rpx;
  background: #ffffff;
  box-sizing: border-box;
}

.privacy-title {
  display: block;
  font-size: 32rpx;
  font-weight: 700;
  color: #3b3330;
  text-align: center;
}

.privacy-body {
  display: block;
  margin-top: 22rpx;
  font-size: 26rpx;
  line-height: 1.8;
  color: #5c524c;
}

.privacy-link {
  color: #c9707c;
}

.privacy-actions {
  display: flex;
  gap: 20rpx;
  margin-top: 32rpx;
}

.privacy-decline {
  flex: 1;
  padding: 20rpx 0;
  border-radius: 40rpx;
  text-align: center;
  font-size: 28rpx;
  color: #8c827c;
  background: #f4ece6;
}

.privacy-agree {
  flex: 1.6;
  padding: 20rpx 0;
  border-radius: 40rpx;
  text-align: center;
  font-size: 28rpx;
  font-weight: 600;
  color: #ffffff;
  background: linear-gradient(135deg, #d6978f, #c9707c);
}
</style>
