<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import {
  cancelReservation,
  confirmReservation,
  fullPhone,
  getReservation,
  noShowReservation,
  rejectReservation,
  type StaffReservationDetail
} from '../../api/reservation'
import { verifyReservation } from '../../api/verify'
import { displayDate, timeText, yuan } from '../../utils/format'
import { handleAuthError, isOwner, requireStaffLogin } from '../../utils/session'

const reservationId = ref(0)
const detail = ref<StaffReservationDetail | null>(null)
const loading = ref(true)
const error = ref('')
const owner = computed(() => isOwner())
const fullPhoneValue = ref('')
const phoneVisible = ref(false)

type ReasonAction = 'reject' | 'cancel' | 'no-show'
const reasonVisible = ref(false)
const reasonAction = ref<ReasonAction>('cancel')
const reasonText = ref('')
const submitting = ref(false)

const ACTION_TITLE: Record<ReasonAction, string> = {
  reject: '拒绝预约',
  cancel: '取消预约',
  'no-show': '标记未到店'
}

const STATUS_COLOR: Record<number, string> = {
  1: 'linear-gradient(135deg,#e5c07b,#d9a441)',
  2: 'linear-gradient(135deg,#7fa8d0,#5e8fbf)',
  3: 'linear-gradient(135deg,#7fbf9a,#4f8d6a)',
  4: 'linear-gradient(135deg,#b8aca4,#8c827c)',
  5: 'linear-gradient(135deg,#e0a368,#c07a3c)'
}

const bannerStyle = computed(() => {
  const status = detail.value?.status ?? 1
  return `background: ${STATUS_COLOR[status] || STATUS_COLOR[1]};`
})

const timelineDesc = computed(() => (detail.value ? [...detail.value.timeline].reverse() : []))

const OPERATOR_ROLE_TEXT: Record<number, string> = {
  1: '顾客',
  2: '技师',
  3: '店长',
  4: '系统'
}

function operatorRoleText(role: number): string {
  return OPERATOR_ROLE_TEXT[role] || '系统'
}

onLoad((options) => {
  reservationId.value = Number(options?.id || 0)
})

onShow(() => {
  if (!requireStaffLogin()) {
    return
  }
  if (!reservationId.value) {
    error.value = '预约不存在'
    loading.value = false
    return
  }
  load()
})

async function load() {
  loading.value = true
  error.value = ''
  phoneVisible.value = false
  fullPhoneValue.value = ''
  try {
    detail.value = await getReservation(reservationId.value)
  } catch (e) {
    if (handleAuthError(e)) {
      return
    }
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

function copyNo() {
  if (detail.value) {
    uni.setClipboardData({ data: detail.value.no })
  }
}

async function showFullPhone() {
  if (!detail.value || phoneVisible.value) {
    return
  }
  try {
    fullPhoneValue.value = await fullPhone(detail.value.id)
    phoneVisible.value = true
  } catch (e) {
    uni.showToast({ title: e instanceof Error ? e.message : '查看失败', icon: 'none' })
  }
}

async function doConfirm() {
  if (!detail.value) {
    return
  }
  try {
    detail.value = await confirmReservation(detail.value.id)
    uni.showToast({ title: '已确认', icon: 'none' })
  } catch (e) {
    uni.showToast({ title: e instanceof Error ? e.message : '操作失败', icon: 'none' })
  }
}

function openReason(action: ReasonAction) {
  reasonAction.value = action
  reasonText.value = ''
  reasonVisible.value = true
}

async function submitReason() {
  if (!detail.value || submitting.value) {
    return
  }
  const reason = reasonText.value.trim()
  if (!reason) {
    uni.showToast({ title: '请填写原因', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    if (reasonAction.value === 'reject') {
      detail.value = await rejectReservation(detail.value.id, reason)
    } else if (reasonAction.value === 'cancel') {
      detail.value = await cancelReservation(detail.value.id, reason)
    } else {
      detail.value = await noShowReservation(detail.value.id, reason)
    }
    reasonVisible.value = false
    uni.showToast({ title: '操作成功', icon: 'none' })
  } catch (e) {
    uni.showToast({ title: e instanceof Error ? e.message : '操作失败', icon: 'none' })
  } finally {
    submitting.value = false
  }
}

function doVerify() {
  if (!detail.value || submitting.value) {
    return
  }
  uni.showModal({
    title: '核销确认',
    content: '确认顾客已到店并完成服务？核销后不可撤销。',
    confirmText: '确认核销',
    cancelText: '再想想',
    confirmColor: '#C9707C',
    success: (res) => {
      if (res.confirm) {
        verifyNow()
      }
    }
  })
}

async function verifyNow() {
  if (!detail.value || submitting.value) {
    return
  }
  submitting.value = true
  try {
    const result = await verifyReservation({ code: detail.value.verifyCode ?? undefined })
    uni.showToast({ title: result.alreadyCompleted ? '该单已完成核销' : '核销成功', icon: 'none' })
    await load()
  } catch (e) {
    uni.showToast({ title: e instanceof Error ? e.message : '核销失败', icon: 'none' })
  } finally {
    submitting.value = false
  }
}

function reschedule() {
  if (detail.value) {
    uni.navigateTo({ url: `/pages/reservation/reschedule?id=${detail.value.id}` })
  }
}
</script>

<template>
  <view class="page">
    <view v-if="loading" class="state">加载中…</view>
    <view v-else-if="error" class="state error">{{ error }}</view>

    <template v-else-if="detail">
      <view class="banner" :style="bannerStyle">
        <text class="banner-status">{{ detail.statusText }}</text>
        <text class="banner-hint">{{ detail.sourceText }} · {{ detail.no }}</text>
      </view>

      <view class="panel">
        <view class="line">
          <text class="label">服务</text>
          <text class="value strong">{{ detail.serviceName }}</text>
        </view>
        <view class="line">
          <text class="label">技师</text>
          <text class="value">{{ detail.technicianName }}</text>
        </view>
        <view class="line">
          <text class="label">时间</text>
          <text class="value">{{ displayDate(detail.date) }} {{ detail.startTime }}-{{ detail.endTime }}</text>
        </view>
        <view class="line">
          <text class="label">金额</text>
          <text class="value price">¥{{ yuan(detail.priceCents) }}</text>
        </view>
        <view class="line" @click="copyNo">
          <text class="label">预约号</text>
          <text class="value mono">{{ detail.no }}</text>
        </view>
      </view>

      <view v-if="detail.status === 2 && detail.verifyCode" class="panel verify">
        <text class="verify-label">到店核销码</text>
        <text class="verify-code">{{ detail.verifyCode }}</text>
        <text class="verify-tip">顾客出示此码或二维码即可核销</text>
      </view>

      <view class="panel">
        <text class="panel-title">顾客信息</text>
        <view class="line">
          <text class="label">姓名</text>
          <text class="value">{{ detail.contactName }}</text>
        </view>
        <view class="line">
          <text class="label">手机号</text>
          <view class="phone">
            <text class="value">{{ phoneVisible ? fullPhoneValue : detail.contactPhone }}</text>
            <text v-if="owner && !phoneVisible" class="phone-link" @click="showFullPhone">查看完整</text>
          </view>
        </view>
        <view v-if="detail.remark" class="line">
          <text class="label">备注</text>
          <text class="value">{{ detail.remark }}</text>
        </view>
        <view v-if="detail.cancelReason" class="line">
          <text class="label">取消原因</text>
          <text class="value">{{ detail.cancelReason }}</text>
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
                <text class="timeline-operator">{{ operatorRoleText(item.operatorRole) }}</text>
                <text class="timeline-time">{{ timeText(item.createdAt) }}</text>
              </view>
              <text v-if="item.detail" class="timeline-detail">{{ item.detail }}</text>
            </view>
          </view>
        </view>
      </view>

      <view v-if="!owner" class="tech-tip">技师账号仅可查看本人预约，如需操作请联系店长</view>

      <view v-if="owner && (detail.status === 1 || detail.status === 2)" class="safe-gap" />

      <view v-if="owner && (detail.status === 1 || detail.status === 2)" class="bar">
        <view class="bar-row secondary">
          <view class="ghost" @click="reschedule">改期</view>
          <view class="ghost" @click="openReason('cancel')">取消预约</view>
        </view>
        <view class="bar-row">
          <template v-if="detail.status === 1">
            <view class="ghost danger" @click="openReason('reject')">拒绝</view>
            <view class="primary" @click="doConfirm">确认预约</view>
          </template>
          <template v-else>
            <view class="ghost warn" @click="openReason('no-show')">未到店</view>
            <view class="primary" :class="{ disabled: submitting }" @click="doVerify">核销完成</view>
          </template>
        </view>
      </view>

      <view v-if="reasonVisible" class="mask" @click.self="reasonVisible = false">
        <view class="dialog">
          <text class="dialog-title">{{ ACTION_TITLE[reasonAction] }}</text>
          <textarea
            v-model="reasonText"
            class="dialog-input"
            :placeholder="`请填写${ACTION_TITLE[reasonAction]}的原因（必填）`"
            placeholder-class="ph"
            maxlength="50"
          />
          <view class="dialog-actions">
            <view class="dialog-btn" @click="reasonVisible = false">取消</view>
            <view class="dialog-btn confirm" :class="{ disabled: submitting }" @click="submitReason">
              {{ submitting ? '提交中…' : '确认' }}
            </view>
          </view>
        </view>
      </view>
    </template>
  </view>
</template>

<style scoped>
.page {
  min-height: 100vh;
  padding: 24rpx 32rpx 40rpx;
  background: #faf6f2;
}

.banner {
  display: flex;
  flex-direction: column;
  margin-bottom: 20rpx;
  padding: 30rpx 32rpx;
  border-radius: 22rpx;
}

.banner-status {
  font-size: 36rpx;
  font-weight: 700;
  color: #ffffff;
}

.banner-hint {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: rgba(255, 255, 255, 0.85);
}

.panel {
  margin-bottom: 20rpx;
  padding: 26rpx 28rpx;
  border-radius: 22rpx;
  background: #ffffff;
  box-shadow: 0 8rpx 20rpx rgba(104, 74, 68, 0.04);
}

.panel-title {
  display: block;
  margin-bottom: 8rpx;
  font-size: 28rpx;
  font-weight: 600;
  color: #3b3330;
}

.line {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: 16rpx 0 0;
}

.label {
  font-size: 24rpx;
  color: #b09a8e;
}

.value {
  max-width: 72%;
  text-align: right;
  font-size: 26rpx;
  color: #3b3330;
}

.value.strong {
  font-size: 30rpx;
  font-weight: 600;
}

.value.price {
  font-size: 30rpx;
  font-weight: 600;
  color: #c97b84;
}

.value.mono {
  font-weight: 600;
  letter-spacing: 1rpx;
  color: #c9707c;
}

.phone {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.phone-link {
  padding: 4rpx 16rpx;
  border-radius: 8rpx;
  font-size: 21rpx;
  color: #c9707c;
  background: #fdf0ee;
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
  margin-top: 10rpx;
  font-size: 60rpx;
  font-weight: 700;
  letter-spacing: 10rpx;
  color: #3b3330;
}

.verify-tip {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: #b09a8e;
}

.timeline {
  margin-top: 12rpx;
}

.timeline-item {
  display: flex;
}

.timeline-axis {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 32rpx;
  margin-right: 16rpx;
}

.timeline-dot {
  width: 14rpx;
  height: 14rpx;
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
  padding-bottom: 24rpx;
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

.timeline-operator {
  margin-left: 12rpx;
  padding: 2rpx 10rpx;
  border-radius: 20rpx;
  font-size: 20rpx;
  color: #c9707c;
  background: #fbf0ee;
}

.timeline-time {
  margin-left: auto;
  font-size: 20rpx;
  color: #cbbcb2;
}

.timeline-detail {
  margin-top: 6rpx;
  font-size: 21rpx;
  color: #8c827c;
  word-break: break-word;
}

.tech-tip {
  margin-top: 10rpx;
  padding: 20rpx;
  border-radius: 16rpx;
  text-align: center;
  font-size: 22rpx;
  color: #b09a8e;
  background: #f6f1ec;
}

.safe-gap {
  height: 210rpx;
}

.bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 16rpx 32rpx calc(16rpx + env(safe-area-inset-bottom));
  background: #ffffff;
  box-shadow: 0 -8rpx 24rpx rgba(104, 74, 68, 0.06);
}

.bar-row {
  display: flex;
  gap: 16rpx;
}

.bar-row.secondary {
  margin-bottom: 14rpx;
}

.ghost {
  flex: 1;
  padding: 18rpx 0;
  border-radius: 38rpx;
  text-align: center;
  font-size: 27rpx;
  color: #8c827c;
  border: 2rpx solid #e8ddd4;
}

.ghost.danger {
  color: #c05a5a;
  border-color: #eccaca;
}

.ghost.warn {
  color: #c07a3c;
  border-color: #f0dcc4;
}

.primary {
  flex: 1;
  padding: 18rpx 0;
  border-radius: 38rpx;
  text-align: center;
  font-size: 27rpx;
  font-weight: 600;
  color: #ffffff;
  background: linear-gradient(135deg, #d6978f, #c9707c);
}

.primary.disabled {
  opacity: 0.6;
}

.mask {
  position: fixed;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(59, 51, 48, 0.45);
  z-index: 50;
}

.dialog {
  width: 600rpx;
  padding: 36rpx;
  border-radius: 24rpx;
  background: #ffffff;
}

.dialog-title {
  display: block;
  text-align: center;
  font-size: 30rpx;
  font-weight: 600;
  color: #3b3330;
}

.dialog-input {
  box-sizing: border-box;
  width: 100%;
  height: 160rpx;
  margin-top: 24rpx;
  padding: 18rpx;
  border-radius: 14rpx;
  font-size: 26rpx;
  background: #faf6f2;
}

.ph {
  color: #cbbcb2;
}

.dialog-actions {
  display: flex;
  gap: 20rpx;
  margin-top: 28rpx;
}

.dialog-btn {
  flex: 1;
  padding: 16rpx 0;
  border-radius: 34rpx;
  text-align: center;
  font-size: 27rpx;
  color: #8c827c;
  background: #f1ece8;
}

.dialog-btn.confirm {
  color: #ffffff;
  background: linear-gradient(135deg, #d6978f, #c9707c);
}

.dialog-btn.disabled {
  opacity: 0.6;
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
</style>
