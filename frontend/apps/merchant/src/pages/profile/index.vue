<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { BRAND } from '@reservation/shared'
import { staffLogout } from '../../api/auth'
import { reportStaffSubscribe } from '../../api/subscribe'
import { clearStaffLogin, isOwner, requireStaffLogin, staffInfo } from '../../utils/session'

// 微信公众平台申请「新预约通知」模板后通过构建期变量注入，未配置时小程序端跳过弹窗
const NEW_RESERVATION_TMPL =
  (import.meta as unknown as { env?: Record<string, string | undefined> }).env?.VITE_WX_TPL_NEW_RESERVATION || ''
const NOTIFY_KEY = 'reservation_staff_notify_on'

const info = computed(() => staffInfo())
const owner = computed(() => isOwner())
const username = ref('')
const subscribed = ref(false)
const busy = ref(false)

onShow(() => {
  requireStaffLogin()
  username.value = (uni.getStorageSync('reservation_staff_username') as string) || ''
  subscribed.value = Boolean(uni.getStorageSync(NOTIFY_KEY))
})

async function enableSubscribe() {
  if (busy.value || subscribed.value) {
    return
  }
  busy.value = true
  try {
    let accepted = 0
    // #ifdef MP-WEIXIN
    if (NEW_RESERVATION_TMPL) {
      const result = await new Promise<Record<string, string>>((resolve, reject) => {
        ;(uni as unknown as {
          requestSubscribeMessage: (options: {
            tmplIds: string[]
            success: (res: Record<string, string>) => void
            fail: (error: unknown) => void
          }) => void
        }).requestSubscribeMessage({
          tmplIds: [NEW_RESERVATION_TMPL],
          success: (res) => resolve(res),
          fail: (error) => reject(error)
        })
      })
      accepted = result[NEW_RESERVATION_TMPL] === 'accept' ? 1 : 0
    }
    // #endif
    // #ifndef MP-WEIXIN
    accepted = 1
    // #endif
    if (!accepted) {
      uni.showToast({ title: '未获得订阅授权', icon: 'none' })
      return
    }
    await reportStaffSubscribe([{ key: 'new_reservation', count: accepted }])
    uni.setStorageSync(NOTIFY_KEY, 1)
    subscribed.value = true
    uni.showToast({ title: '新预约通知已开启', icon: 'none' })
  } catch (e) {
    uni.showToast({ title: e instanceof Error ? e.message : '开启失败', icon: 'none' })
  } finally {
    busy.value = false
  }
}

async function logout() {
  uni.showModal({
    title: '退出登录',
    content: '确认退出当前账号吗？',
    confirmColor: '#C9707C',
    success: async (res) => {
      if (!res.confirm) {
        return
      }
      await staffLogout().catch(() => undefined)
      clearStaffLogin()
      uni.reLaunch({ url: '/pages/login/index' })
    }
  })
}

function openPage(url: string) {
  uni.navigateTo({ url })
}
</script>

<template>
  <view class="page">
    <view class="profile">
      <view class="avatar">{{ (info?.role === 'owner' ? '店' : '技') }}</view>
      <view class="profile-info">
        <text class="name">{{ info?.role === 'owner' ? '店长账号' : '技师账号' }}</text>
        <text class="sub">{{ username || `账号 ID ${info?.staffId ?? '-'}` }}</text>
      </view>
    </view>

    <view v-if="owner" class="panel">
      <view class="sub-row">
        <view class="sub-main">
          <text class="sub-title">新预约通知</text>
          <text class="sub-desc">顾客下单时通过微信通知你，及时确认与准备</text>
        </view>
        <view class="sub-btn" :class="{ done: subscribed }" @click="enableSubscribe">
          {{ subscribed ? '已开启' : '开启通知' }}
        </view>
      </view>
    </view>

    <view v-if="owner" class="panel">
      <text class="panel-title">门店管理</text>
      <view class="menu-row" @click="openPage('/pages/manage/services')">
        <text class="menu-icon">💅</text>
        <text class="menu-name">服务管理</text>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-row" @click="openPage('/pages/manage/categories')">
        <text class="menu-icon">🗂</text>
        <text class="menu-name">分类管理</text>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-row" @click="openPage('/pages/manage/technicians')">
        <text class="menu-icon">💇</text>
        <text class="menu-name">技师与排班</text>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-row" @click="openPage('/pages/manage/store')">
        <text class="menu-icon">🏪</text>
        <text class="menu-name">门店资料与营业时间</text>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-row" @click="openPage('/pages/manage/rules')">
        <text class="menu-icon">⚙️</text>
        <text class="menu-name">预约规则</text>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-row" @click="openPage('/pages/manage/accounts')">
        <text class="menu-icon">👤</text>
        <text class="menu-name">账号管理</text>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-row" @click="openPage('/pages/manage/audits')">
        <text class="menu-icon text-icon">记</text>
        <text class="menu-name">操作审计</text>
        <text class="menu-arrow">›</text>
      </view>
    </view>

    <view class="panel">
      <view class="row">
        <text class="label">角色</text>
        <text class="value">{{ info?.role === 'owner' ? '店长' : '技师' }}</text>
      </view>
      <view v-if="info?.technicianId" class="row">
        <text class="label">绑定技师</text>
        <text class="value">技师档案 #{{ info.technicianId }}</text>
      </view>
      <view class="row">
        <text class="label">预约管理</text>
        <text class="value">{{ owner ? '全店数据' : '仅本人数据' }}</text>
      </view>
    </view>

    <view class="logout" @click="logout">退出登录</view>

    <text class="version">{{ BRAND.merchantAppName }} {{ BRAND.version }}</text>
  </view>
</template>

<style scoped>
.page {
  min-height: 100vh;
  padding: 32rpx 32rpx 120rpx;
  background: #faf6f2;
}

.profile {
  display: flex;
  align-items: center;
  padding: 20rpx 8rpx 36rpx;
}

.avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 112rpx;
  height: 112rpx;
  border-radius: 32rpx;
  font-size: 44rpx;
  font-weight: 700;
  color: #ffffff;
  background: linear-gradient(135deg, #d6978f, #c9707c);
}

.profile-info {
  display: flex;
  flex-direction: column;
  margin-left: 24rpx;
}

.name {
  font-size: 34rpx;
  font-weight: 700;
  color: #3b3330;
}

.sub {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: #b09a8e;
}

.panel {
  margin-bottom: 22rpx;
  padding: 26rpx 28rpx;
  border-radius: 22rpx;
  background: #ffffff;
  box-shadow: 0 8rpx 20rpx rgba(104, 74, 68, 0.04);
}

.sub-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.sub-main {
  display: flex;
  flex: 1;
  flex-direction: column;
  margin-right: 20rpx;
}

.sub-title {
  font-size: 28rpx;
  font-weight: 600;
  color: #3b3330;
}

.sub-desc {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: #b09a8e;
}

.sub-btn {
  flex-shrink: 0;
  padding: 14rpx 30rpx;
  border-radius: 30rpx;
  font-size: 24rpx;
  color: #ffffff;
  background: linear-gradient(135deg, #d6978f, #c9707c);
}

.sub-btn.done {
  color: #b09a8e;
  background: #f1ece8;
}

.panel-title {
  display: block;
  margin-bottom: 8rpx;
  font-size: 28rpx;
  font-weight: 600;
  color: #3b3330;
}

.menu-row {
  display: flex;
  align-items: center;
  padding: 26rpx 0;
  border-bottom: 1rpx solid #f5eee8;
}

.menu-row:last-child {
  border-bottom: none;
}

.menu-icon {
  width: 48rpx;
  font-size: 30rpx;
}

.menu-icon.text-icon {
  font-size: 24rpx;
  color: #c9707c;
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

.row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: 14rpx 0;
}

.label {
  font-size: 24rpx;
  color: #b09a8e;
}

.value {
  font-size: 26rpx;
  color: #3b3330;
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
  margin-top: 40rpx;
  text-align: center;
  font-size: 22rpx;
  color: #cbbcb2;
}
</style>
