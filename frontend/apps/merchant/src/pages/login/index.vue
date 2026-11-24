<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { BRAND } from '@reservation/shared'
import { staffLogin, staffWxLogin } from '../../api/auth'
import { isStaffLogin, saveStaffLogin } from '../../utils/session'

const username = ref('')
const password = ref('')
const loading = ref(false)
const wxLoading = ref(false)

onLoad(() => {
  if (isStaffLogin()) {
    uni.reLaunch({ url: '/pages/dashboard/index' })
  }
})

async function login() {
  if (loading.value) {
    return
  }
  if (!username.value.trim() || !password.value) {
    uni.showToast({ title: '请输入账号与密码', icon: 'none' })
    return
  }
  loading.value = true
  try {
    const info = await staffLogin(username.value.trim(), password.value)
    saveStaffLogin(info)
    uni.setStorageSync('reservation_staff_username', username.value.trim())
    uni.reLaunch({ url: '/pages/dashboard/index' })
  } catch (e) {
    uni.showToast({ title: e instanceof Error ? e.message : '登录失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}

function wxLogin() {
  if (wxLoading.value) {
    return
  }
  wxLoading.value = true
  // #ifdef MP-WEIXIN
  uni.login({
    success: async (res) => {
      try {
        const info = await staffWxLogin(res.code)
        saveStaffLogin(info)
        uni.reLaunch({ url: '/pages/dashboard/index' })
      } catch (e) {
        uni.showToast({ title: e instanceof Error ? e.message : '登录失败', icon: 'none' })
      } finally {
        wxLoading.value = false
      }
    },
    fail: () => {
      wxLoading.value = false
      uni.showToast({ title: '微信登录失败', icon: 'none' })
    }
  })
  // #endif
  // #ifndef MP-WEIXIN
  wxLoading.value = false
  uni.showToast({ title: '请在小程序端使用微信登录', icon: 'none' })
  // #endif
}
</script>

<template>
  <view class="page">
    <view class="hero">
      <view class="logo">{{ BRAND.logoInitial }}</view>
      <text class="brand">{{ BRAND.merchantBrand }}</text>
      <text class="slogan">{{ BRAND.merchantSlogan }}</text>
    </view>

    <view class="panel">
      <view class="field">
        <text class="label">账号</text>
        <input
          id="login-username"
          v-model="username"
          class="input"
          placeholder="登录账号（手机号）"
          placeholder-class="ph"
        />
      </view>
      <view class="field">
        <text class="label">密码</text>
        <input
          id="login-password"
          v-model="password"
          class="input"
          password
          placeholder="登录密码"
          placeholder-class="ph"
        />
      </view>

      <view class="btn" :class="{ disabled: loading }" @click="login">
        {{ loading ? '登录中…' : '登 录' }}
      </view>

      <!-- #ifdef MP-WEIXIN -->
      <view class="wx-btn" :class="{ disabled: wxLoading }" @click="wxLogin">
        <text class="wx-icon">微</text>
        <text>{{ wxLoading ? '登录中…' : '微信一键登录' }}</text>
      </view>
      <!-- #endif -->

      <text class="hint">账号由店长在「账号与权限」中创建；忘记密码请联系店长重置</text>
    </view>

    <text class="footer">{{ BRAND.legalFooter }}</text>
  </view>
</template>

<style scoped>
.page {
  min-height: 100vh;
  padding: 0 56rpx;
  background: linear-gradient(180deg, #fdf4f2 0%, #faf6f2 42%);
}

.hero {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 140rpx 0 64rpx;
}

.logo {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 132rpx;
  height: 132rpx;
  border-radius: 40rpx;
  font-size: 60rpx;
  font-weight: 700;
  color: #ffffff;
  background: linear-gradient(135deg, #d6978f, #c9707c);
  box-shadow: 0 16rpx 36rpx rgba(201, 112, 124, 0.3);
}

.brand {
  margin-top: 32rpx;
  font-size: 40rpx;
  font-weight: 700;
  color: #3b3330;
}

.slogan {
  margin-top: 10rpx;
  font-size: 24rpx;
  color: #b09a8e;
}

.panel {
  padding: 40rpx 36rpx;
  border-radius: 28rpx;
  background: #ffffff;
  box-shadow: 0 12rpx 32rpx rgba(104, 74, 68, 0.06);
}

.field {
  display: flex;
  align-items: center;
  padding: 24rpx 0;
  border-bottom: 1rpx solid #f3ebe5;
}

.label {
  width: 96rpx;
  font-size: 26rpx;
  color: #8c827c;
}

.input {
  flex: 1;
  font-size: 28rpx;
  color: #3b3330;
}

.ph {
  color: #cbbcb2;
}

.btn {
  margin-top: 48rpx;
  padding: 24rpx 0;
  border-radius: 44rpx;
  text-align: center;
  font-size: 30rpx;
  font-weight: 600;
  letter-spacing: 6rpx;
  color: #ffffff;
  background: linear-gradient(135deg, #d6978f, #c9707c);
  box-shadow: 0 12rpx 26rpx rgba(201, 112, 124, 0.3);
}

.btn.disabled {
  opacity: 0.6;
}

.wx-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12rpx;
  margin-top: 24rpx;
  padding: 22rpx 0;
  border-radius: 44rpx;
  font-size: 28rpx;
  color: #4f8d6a;
  border: 2rpx solid #cfe6d8;
  background: #f2faf5;
}

.wx-btn.disabled {
  opacity: 0.6;
}

.wx-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40rpx;
  height: 40rpx;
  border-radius: 50%;
  font-size: 22rpx;
  color: #ffffff;
  background: #4f8d6a;
}

.hint {
  display: block;
  margin-top: 32rpx;
  text-align: center;
  font-size: 22rpx;
  color: #b09a8e;
  line-height: 34rpx;
}

.footer {
  display: block;
  padding: 60rpx 0 40rpx;
  text-align: center;
  font-size: 22rpx;
  color: #cbbcb2;
}
</style>
