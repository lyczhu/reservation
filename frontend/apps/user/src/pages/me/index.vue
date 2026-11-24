<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { BRAND, USER_TOKEN_KEY } from '@reservation/shared'
import { getStoreHome } from '../../api/catalog'
import { bindUserPhone, getUserProfile, type UserProfile } from '../../api/user'
import {
  clearUserLogin,
  doUserLogin,
  updateUserPhoneBound,
  userBrief
} from '../../utils/session'

const token = ref('')
const logged = computed(() => Boolean(token.value))

const profile = ref<UserProfile | null>(null)
const loading = ref(false)
const phoneVisible = ref(false)
const phoneInput = ref('')
const busy = ref(false)
const storePhone = ref('')

const nickInitial = computed(() => {
  const name = profile.value?.nickname || userBrief()?.nickname || BRAND.logoInitial
  return name.slice(0, 1)
})

onShow(() => {
  token.value = String(uni.getStorageSync(USER_TOKEN_KEY) || '')
  if (token.value) {
    refreshProfile()
  }
  loadStorePhone()
})

async function refreshProfile() {
  try {
    profile.value = await getUserProfile()
    updateUserPhoneBound(Boolean(profile.value.phone))
  } catch (e) {
    // token 失效时退回未登录态
    if (e instanceof Error && e.message.includes('未登录')) {
      clearUserLogin()
      token.value = ''
      profile.value = null
    }
  }
}

async function loadStorePhone() {
  try {
    const home = await getStoreHome()
    storePhone.value = home.store.phone || ''
  } catch {
    storePhone.value = ''
  }
}

async function login() {
  if (busy.value) {
    return
  }
  busy.value = true
  loading.value = true
  try {
    await doUserLogin()
    token.value = String(uni.getStorageSync(USER_TOKEN_KEY) || '')
    await refreshProfile()
    uni.showToast({ title: '登录成功', icon: 'none' })
  } catch (e) {
    uni.showToast({ title: e instanceof Error ? e.message : '登录失败', icon: 'none' })
  } finally {
    busy.value = false
    loading.value = false
  }
}

function onGetPhone(event: unknown) {
  const detail = (event as { detail?: { code?: string; errMsg?: string } })?.detail
  if (!detail?.code) {
    uni.showToast({ title: '未获取到手机号授权', icon: 'none' })
    return
  }
  submitPhone(detail.code)
}

function openPhoneInput() {
  phoneInput.value = ''
  phoneVisible.value = true
}

async function submitPhone(code: string) {
  if (busy.value) {
    return
  }
  busy.value = true
  try {
    await bindUserPhone(code)
    phoneVisible.value = false
    await refreshProfile()
    uni.showToast({ title: '手机号已绑定', icon: 'none' })
  } catch (e) {
    uni.showToast({ title: e instanceof Error ? e.message : '绑定失败', icon: 'none' })
  } finally {
    busy.value = false
  }
}

function submitPhoneInput() {
  const phone = phoneInput.value.trim()
  if (!/^1[3-9]\d{9}$/.test(phone)) {
    uni.showToast({ title: '请输入正确的手机号', icon: 'none' })
    return
  }
  submitPhone(phone)
}

function maskPhone(phone: string): string {
  return phone.length === 11 ? `${phone.slice(0, 3)}****${phone.slice(7)}` : phone
}

async function goReservations() {
  if (!logged.value) {
    await login()
    if (!logged.value) {
      return
    }
  }
  uni.navigateTo({ url: '/pages/reservation/list' })
}

function callStore() {
  if (!storePhone.value) {
    uni.showToast({ title: '门店电话未配置', icon: 'none' })
    return
  }
  // #ifdef H5
  // 桌面浏览器拉起 tel: 无回调，先给出可见反馈
  uni.setClipboardData({
    data: storePhone.value,
    success: () => uni.showToast({ title: '号码已复制', icon: 'none' })
  })
  window.location.href = `tel:${storePhone.value}`
  // #endif
  // #ifndef H5
  uni.makePhoneCall({
    phoneNumber: storePhone.value,
    fail: () => {
      uni.setClipboardData({
        data: storePhone.value,
        success: () => uni.showToast({ title: '电话已复制', icon: 'none' })
      })
    }
  })
  // #endif
}

function openLegal(page: 'privacy' | 'agreement') {
  uni.navigateTo({ url: `/pages/legal/${page}` })
}

function about() {
  uni.showModal({
    title: BRAND.aboutTitle,
    content: BRAND.aboutContent,
    showCancel: false,
    confirmText: '知道了',
    confirmColor: '#C9707C'
  })
}

function logout() {
  uni.showModal({
    title: '退出登录',
    content: '退出后需重新登录才能预约与查看订单。',
    confirmColor: '#C9707C',
    success: (res) => {
      if (!res.confirm) {
        return
      }
      clearUserLogin()
      token.value = ''
      profile.value = null
      uni.showToast({ title: '已退出', icon: 'none' })
    }
  })
}
</script>

<template>
  <view class="page">
    <view class="profile-card">
      <template v-if="logged">
        <image v-if="profile?.avatar" class="avatar" :src="profile.avatar" mode="aspectFill" />
        <view v-else class="avatar fallback">{{ nickInitial }}</view>
        <view class="info">
          <text class="nick">{{ profile?.nickname || userBrief()?.nickname || '顾客' }}</text>
          <text v-if="profile?.phone" class="sub">{{ maskPhone(profile.phone) }}</text>
          <text v-else class="sub warn">未绑定手机号</text>
        </view>
        <!-- #ifdef MP-WEIXIN -->
        <button
          v-if="!profile?.phone"
          class="bind-btn"
          open-type="getPhoneNumber"
          @getphonenumber="onGetPhone"
        >
          绑定手机号
        </button>
        <!-- #endif -->
        <!-- #ifndef MP-WEIXIN -->
        <view v-if="!profile?.phone" class="bind-btn" @click="openPhoneInput">绑定手机号</view>
        <!-- #endif -->
      </template>
      <template v-else>
        <view class="avatar fallback">{{ BRAND.logoInitial }}</view>
        <view class="info">
          <text class="nick">欢迎来到{{ BRAND.appName }}</text>
          <text class="sub">登录后可预约并查看订单</text>
        </view>
        <view class="login-btn" :class="{ disabled: busy }" @click="login">
          {{ loading ? '登录中…' : '微信登录' }}
        </view>
      </template>
    </view>

    <view class="panel">
      <view class="menu-row" @click="goReservations">
        <text class="menu-icon">📅</text>
        <text class="menu-name">我的预约</text>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-row" @click="callStore">
        <text class="menu-icon">☎️</text>
        <text class="menu-name">联系门店</text>
        <text class="menu-arrow">›</text>
      </view>
    </view>

    <view class="panel">
      <view class="menu-row" @click="openLegal('privacy')">
        <text class="menu-icon">🔐</text>
        <text class="menu-name">隐私政策</text>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-row" @click="openLegal('agreement')">
        <text class="menu-icon">📄</text>
        <text class="menu-name">用户协议</text>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-row" @click="about">
        <text class="menu-icon">💮</text>
        <text class="menu-name">关于我们</text>
        <text class="menu-arrow">›</text>
      </view>
    </view>

    <view v-if="logged" class="logout" @click="logout">退出登录</view>

    <text class="version">{{ BRAND.appName }} {{ BRAND.version }}</text>

    <view v-if="phoneVisible" class="mask" @click.self="phoneVisible = false">
      <view class="editor">
        <text class="editor-title">绑定手机号</text>
        <text class="editor-tip">当前环境暂不支持微信一键获取，请输入手机号完成绑定</text>
        <view class="field">
          <text class="label">手机号</text>
          <input v-model="phoneInput" class="input" type="number" maxlength="11" placeholder="请输入 11 位手机号" placeholder-class="ph" />
        </view>
        <view class="editor-actions">
          <view class="btn ghost" @click="phoneVisible = false">取消</view>
          <view class="btn primary" :class="{ disabled: busy }" @click="submitPhoneInput">
            {{ busy ? '绑定中…' : '确认绑定' }}
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<style scoped>
.page {
  min-height: 100vh;
  padding: 32rpx 32rpx 60rpx;
  background: #faf6f2;
}

.profile-card {
  display: flex;
  align-items: center;
  padding: 36rpx 30rpx;
  border-radius: 26rpx;
  background: linear-gradient(135deg, #fdf1ee, #ffffff);
  box-shadow: 0 10rpx 28rpx rgba(104, 74, 68, 0.05);
}

.avatar {
  flex: 0 0 108rpx;
  width: 108rpx;
  height: 108rpx;
  border-radius: 50%;
  background: #f4ece6;
}

.avatar.fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 42rpx;
  font-weight: 700;
  color: #ffffff;
  background: linear-gradient(135deg, #d6978f, #c9707c);
}

.info {
  flex: 1;
  margin-left: 24rpx;
  min-width: 0;
}

.nick {
  display: block;
  font-size: 34rpx;
  font-weight: 700;
  color: #3b3330;
}

.sub {
  display: block;
  margin-top: 8rpx;
  font-size: 23rpx;
  color: #b09a8e;
}

.sub.warn {
  color: #c98a6e;
}

.login-btn,
.bind-btn {
  flex-shrink: 0;
  margin: 0;
  padding: 14rpx 30rpx;
  border-radius: 32rpx;
  font-size: 24rpx;
  font-weight: 600;
  line-height: 1.4;
  color: #ffffff;
  background: linear-gradient(135deg, #d6978f, #c9707c);
  border: none;
}

.login-btn.disabled {
  opacity: 0.55;
}

button.bind-btn::after {
  border: none;
}

.panel {
  margin-top: 22rpx;
  padding: 6rpx 28rpx;
  border-radius: 22rpx;
  background: #ffffff;
  box-shadow: 0 10rpx 28rpx rgba(104, 74, 68, 0.05);
}

.menu-row {
  display: flex;
  align-items: center;
  padding: 28rpx 0;
  border-bottom: 1rpx solid #f5eee8;
}

.menu-row:last-child {
  border-bottom: none;
}

.menu-icon {
  width: 52rpx;
  font-size: 30rpx;
}

.menu-name {
  flex: 1;
  font-size: 28rpx;
  color: #3b3330;
}

.menu-arrow {
  font-size: 34rpx;
  color: #cbbcb2;
}

.logout {
  margin-top: 40rpx;
  padding: 22rpx 0;
  border-radius: 40rpx;
  text-align: center;
  font-size: 28rpx;
  color: #c05a5a;
  background: #ffffff;
  border: 2rpx solid #eccaca;
}

.version {
  display: block;
  margin-top: 36rpx;
  text-align: center;
  font-size: 22rpx;
  color: #cbbcb2;
}

.mask {
  position: fixed;
  inset: 0;
  z-index: 20;
  display: flex;
  align-items: flex-end;
  background: rgba(59, 51, 48, 0.45);
}

.editor {
  width: 100%;
  padding: 32rpx 32rpx calc(32rpx + env(safe-area-inset-bottom));
  border-radius: 28rpx 28rpx 0 0;
  background: #ffffff;
  box-sizing: border-box;
}

.editor-title {
  display: block;
  font-size: 30rpx;
  font-weight: 600;
  color: #3b3330;
}

.editor-tip {
  display: block;
  margin-top: 10rpx;
  font-size: 22rpx;
  color: #b09a8e;
}

.field {
  display: flex;
  align-items: center;
  margin-top: 16rpx;
  padding: 22rpx 0;
  border-bottom: 1rpx solid #f5eee8;
}

.label {
  width: 120rpx;
  font-size: 26rpx;
  color: #8c827c;
}

.input {
  flex: 1;
  font-size: 28rpx;
  color: #3b3330;
}

.editor-actions {
  display: flex;
  gap: 20rpx;
  margin-top: 30rpx;
}

.btn {
  flex: 1;
  padding: 20rpx 0;
  border-radius: 40rpx;
  text-align: center;
  font-size: 28rpx;
}

.btn.ghost {
  color: #8c827c;
  background: #f4ece6;
}

.btn.primary {
  color: #ffffff;
  font-weight: 600;
  background: linear-gradient(135deg, #d6978f, #c9707c);
}

.btn.primary.disabled {
  opacity: 0.55;
}

.ph {
  color: #cbbcb2;
}
</style>
