<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getRules, updateRules } from '../../api/manage'
import { handleAuthError, requireStaffLogin } from '../../utils/session'

const GRANULARITY_OPTIONS = [30, 60]

const loading = ref(true)
const submitting = ref(false)
const error = ref('')

const form = reactive({
  slotGranularity: 30,
  bookableDays: 14,
  minLeadMinutes: 60,
  cancelDeadlineHours: 2,
  rescheduleDeadlineHours: 2,
  needConfirm: 1,
  bufferMinutes: 0,
  reminderHours: 2
})

const granularityIndex = computed(() => Math.max(0, GRANULARITY_OPTIONS.indexOf(form.slotGranularity)))

onLoad(async () => {
  if (!requireStaffLogin()) {
    return
  }
  try {
    const rule = await getRules()
    form.slotGranularity = rule.slotGranularity
    form.bookableDays = rule.bookableDays
    form.minLeadMinutes = rule.minLeadMinutes
    form.cancelDeadlineHours = rule.cancelDeadlineHours
    form.rescheduleDeadlineHours = rule.rescheduleDeadlineHours
    form.needConfirm = rule.needConfirm
    form.bufferMinutes = rule.bufferMinutes
    form.reminderHours = rule.reminderHours
  } catch (e) {
    if (handleAuthError(e)) {
      return
    }
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
})

function chooseGranularity(event: { detail: { value: number | string } }) {
  form.slotGranularity = GRANULARITY_OPTIONS[Number(event.detail.value)] || 30
}

function toggleNeedConfirm() {
  form.needConfirm = form.needConfirm === 1 ? 0 : 1
}

async function submit() {
  if (submitting.value) {
    return
  }
  const values = [
    form.bookableDays,
    form.minLeadMinutes,
    form.cancelDeadlineHours,
    form.rescheduleDeadlineHours,
    form.bufferMinutes,
    form.reminderHours
  ].map(Number)
  if (values.some((value) => !Number.isFinite(value) || value < 0)) {
    uni.showToast({ title: '请填写正确的规则数值', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    await updateRules({
      slotGranularity: form.slotGranularity,
      bookableDays: values[0],
      minLeadMinutes: values[1],
      cancelDeadlineHours: values[2],
      rescheduleDeadlineHours: values[3],
      needConfirm: form.needConfirm,
      maxConcurrent: 1,
      bufferMinutes: values[4],
      reminderHours: values[5]
    })
    uni.showToast({ title: '规则已保存', icon: 'none' })
  } catch (e) {
    uni.showToast({ title: e instanceof Error ? e.message : '保存失败', icon: 'none' })
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <view class="page">
    <view v-if="loading" class="state">加载中…</view>
    <view v-else-if="error" class="state error">{{ error }}</view>

    <template v-else>
      <view class="panel">
        <text class="panel-title">时段与预约</text>
        <view class="field">
          <text class="label">时段粒度</text>
          <picker mode="selector" :range="GRANULARITY_OPTIONS.map((v) => v + ' 分钟')" :value="granularityIndex" @change="chooseGranularity">
            <view class="picker-value">{{ form.slotGranularity }} 分钟 ›</view>
          </picker>
        </view>
        <view class="field">
          <text class="label">可约天数</text>
          <input v-model="form.bookableDays" class="input" type="number" placeholder="7-30" placeholder-class="ph" />
          <text class="unit">天</text>
        </view>
        <view class="field">
          <text class="label">最小提前量</text>
          <input v-model="form.minLeadMinutes" class="input" type="number" placeholder="0-1440" placeholder-class="ph" />
          <text class="unit">分钟</text>
        </view>
        <view class="field">
          <text class="label">服务缓冲</text>
          <input v-model="form.bufferMinutes" class="input" type="number" placeholder="0-30" placeholder-class="ph" />
          <text class="unit">分钟</text>
        </view>
      </view>

      <view class="panel">
        <text class="panel-title">取消与改期</text>
        <view class="field">
          <text class="label">取消截止</text>
          <input v-model="form.cancelDeadlineHours" class="input" type="number" placeholder="0-24" placeholder-class="ph" />
          <text class="unit">小时前</text>
        </view>
        <view class="field">
          <text class="label">改期截止</text>
          <input v-model="form.rescheduleDeadlineHours" class="input" type="number" placeholder="0-24" placeholder-class="ph" />
          <text class="unit">小时前</text>
        </view>
        <view class="field">
          <text class="label">是否需要确认</text>
          <view class="type" :class="{ rest: form.needConfirm === 0 }" @click="toggleNeedConfirm">
            {{ form.needConfirm === 1 ? '需店长确认' : '自动确认' }}
          </view>
        </view>
        <view class="field">
          <text class="label">到店提醒</text>
          <input v-model="form.reminderHours" class="input" type="number" placeholder="1-24" placeholder-class="ph" />
          <text class="unit">小时前</text>
        </view>
      </view>

      <view class="panel tip-panel">
        <text class="tip">说明：同一技师同一时段仅支持 1 单（并发防重）；修改规则仅影响新提交的预约，已有预约按提交时的规则快照执行。</text>
      </view>

      <view class="safe-gap" />
      <view class="bar">
        <view class="bar-button" :class="{ disabled: submitting }" @click="submit">
          {{ submitting ? '保存中…' : '保存规则' }}
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

.panel {
  margin-bottom: 22rpx;
  padding: 26rpx 28rpx 10rpx;
  border-radius: 22rpx;
  background: #ffffff;
  box-shadow: 0 8rpx 20rpx rgba(104, 74, 68, 0.04);
}

.panel-title {
  display: block;
  font-size: 28rpx;
  font-weight: 600;
  color: #3b3330;
}

.field {
  display: flex;
  align-items: center;
  padding: 22rpx 0;
  border-bottom: 1rpx solid #f5eee8;
}

.field:last-child {
  border-bottom: none;
}

.label {
  width: 190rpx;
  font-size: 26rpx;
  color: #8c827c;
}

.input {
  flex: 1;
  font-size: 28rpx;
  color: #3b3330;
}

.unit {
  margin-left: 8rpx;
  font-size: 24rpx;
  color: #b09a8e;
}

.picker-value {
  font-size: 28rpx;
  color: #3b3330;
}

.type {
  padding: 10rpx 28rpx;
  border-radius: 28rpx;
  font-size: 24rpx;
  color: #ffffff;
  background: linear-gradient(135deg, #d6978f, #c9707c);
}

.type.rest {
  color: #a89589;
  background: #f1eae3;
}

.tip-panel {
  padding-bottom: 26rpx;
}

.tip {
  font-size: 22rpx;
  line-height: 1.6;
  color: #b09a8e;
}

.safe-gap {
  height: 150rpx;
}

.bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 18rpx 32rpx calc(18rpx + env(safe-area-inset-bottom));
  background: #ffffff;
  box-shadow: 0 -8rpx 24rpx rgba(104, 74, 68, 0.06);
}

.bar-button {
  padding: 22rpx 0;
  border-radius: 44rpx;
  text-align: center;
  font-size: 30rpx;
  font-weight: 600;
  color: #ffffff;
  background: linear-gradient(135deg, #d6978f, #c9707c);
}

.bar-button.disabled {
  opacity: 0.55;
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

.ph {
  color: #cbbcb2;
}
</style>
