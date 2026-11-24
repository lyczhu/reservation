<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { verifyReservation, type VerifyResult } from '../../api/verify'
import { displayDate } from '../../utils/format'
import { requireStaffLogin } from '../../utils/session'

const code = ref('')
const loading = ref(false)
const result = ref<VerifyResult | null>(null)

onShow(() => {
  requireStaffLogin()
})

function onInput(value: string) {
  code.value = value.replace(/\D/g, '').slice(0, 6)
}

async function verify() {
  if (loading.value) {
    return
  }
  if (code.value.length !== 6) {
    uni.showToast({ title: '请输入 6 位核销码', icon: 'none' })
    return
  }
  loading.value = true
  result.value = null
  try {
    result.value = await verifyReservation({ code: code.value })
    code.value = ''
  } catch (e) {
    uni.showToast({ title: e instanceof Error ? e.message : '核销失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}

function scan() {
  // #ifdef MP-WEIXIN
  uni.scanCode({
    success: async (res) => {
      try {
        result.value = await verifyReservation({ qrContent: res.result })
        code.value = ''
      } catch (e) {
        uni.showToast({ title: e instanceof Error ? e.message : '核销失败', icon: 'none' })
      }
    },
    fail: () => uni.showToast({ title: '未完成扫码', icon: 'none' })
  })
  // #endif
  // #ifndef MP-WEIXIN
  uni.showToast({ title: '请在小程序端使用扫码核销', icon: 'none' })
  // #endif
}
</script>

<template>
  <view class="page">
    <view class="panel">
      <text class="title">输入顾客核销码</text>
      <text class="subtitle">核对顾客出示的 6 位数字码后输入</text>
      <input
        id="verify-code"
        class="code-input"
        :value="code"
        type="number"
        maxlength="6"
        placeholder="6 位核销码"
        placeholder-class="ph"
        @input="(e: any) => onInput(e.detail.value)"
      />
      <view class="btn" :class="{ disabled: loading }" @click="verify">
        {{ loading ? '核销中…' : '核销' }}
      </view>
      <!-- #ifdef MP-WEIXIN -->
      <view class="scan-btn" @click="scan">
        <text class="scan-icon">⎆</text>
        <text>扫描顾客二维码</text>
      </view>
      <!-- #endif -->
    </view>

    <view v-if="result" class="panel result" :class="{ done: result.alreadyCompleted }">
      <text class="result-title">{{ result.alreadyCompleted ? '该单已完成核销' : '核销成功' }}</text>
      <view class="line">
        <text class="label">预约号</text>
        <text class="value mono">{{ result.no }}</text>
      </view>
      <view class="line">
        <text class="label">服务</text>
        <text class="value">{{ result.serviceName }}</text>
      </view>
      <view class="line">
        <text class="label">技师</text>
        <text class="value">{{ result.technicianName }}</text>
      </view>
      <view class="line">
        <text class="label">时间</text>
        <text class="value">{{ displayDate(result.date) }} {{ result.startTime }}-{{ result.endTime }}</text>
      </view>
      <view class="line">
        <text class="label">顾客</text>
        <text class="value">{{ result.contactName }}</text>
      </view>
    </view>

    <view class="tips">
      <text class="tips-title">核销须知</text>
      <text class="tips-item">· 仅待服务状态的预约可核销；</text>
      <text class="tips-item">· 连续输错 3 次将锁定 5 分钟，请仔细核对；</text>
      <text class="tips-item">· 技师账号只能核销本人名下的预约。</text>
    </view>
  </view>
</template>

<style scoped>
.page {
  min-height: 100vh;
  padding: 24rpx 32rpx 120rpx;
  background: #faf6f2;
}

.panel {
  margin-bottom: 22rpx;
  padding: 32rpx 30rpx;
  border-radius: 22rpx;
  background: #ffffff;
  box-shadow: 0 8rpx 20rpx rgba(104, 74, 68, 0.04);
}

.title {
  display: block;
  font-size: 32rpx;
  font-weight: 700;
  color: #3b3330;
}

.subtitle {
  display: block;
  margin-top: 8rpx;
  font-size: 22rpx;
  color: #b09a8e;
}

.code-input {
  margin-top: 28rpx;
  padding: 22rpx 0;
  border-radius: 16rpx;
  text-align: center;
  font-size: 52rpx;
  font-weight: 700;
  letter-spacing: 16rpx;
  color: #3b3330;
  background: #faf6f2;
}

.ph {
  font-size: 28rpx;
  font-weight: 400;
  letter-spacing: 2rpx;
  color: #cbbcb2;
}

.btn {
  margin-top: 28rpx;
  padding: 22rpx 0;
  border-radius: 44rpx;
  text-align: center;
  font-size: 30rpx;
  font-weight: 600;
  color: #ffffff;
  background: linear-gradient(135deg, #d6978f, #c9707c);
}

.btn.disabled {
  opacity: 0.6;
}

.scan-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10rpx;
  margin-top: 20rpx;
  padding: 20rpx 0;
  border-radius: 44rpx;
  font-size: 27rpx;
  color: #c9707c;
  border: 2rpx solid #e8c9c4;
}

.scan-icon {
  font-size: 30rpx;
}

.result {
  border: 2rpx solid #cfe6d8;
  background: #f6fcf8;
}

.result.done {
  border-color: #f0dcc4;
  background: #fdf9f3;
}

.result-title {
  display: block;
  font-size: 30rpx;
  font-weight: 700;
  color: #4f8d6a;
}

.result.done .result-title {
  color: #c07a3c;
}

.line {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: 14rpx 0 0;
}

.label {
  font-size: 23rpx;
  color: #b09a8e;
}

.value {
  max-width: 70%;
  text-align: right;
  font-size: 25rpx;
  color: #3b3330;
}

.value.mono {
  font-weight: 600;
  color: #c9707c;
}

.tips {
  display: flex;
  flex-direction: column;
  padding: 4rpx 10rpx;
}

.tips-title {
  font-size: 24rpx;
  font-weight: 600;
  color: #8c827c;
}

.tips-item {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: #b09a8e;
}
</style>
