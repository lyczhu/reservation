<script setup lang="ts">
import { reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getTechnicianShifts, updateTechnicianShifts } from '../../api/manage'
import { handleAuthError, requireStaffLogin } from '../../utils/session'

const WEEK_TEXT = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']

const technicianId = ref(0)
const technicianName = ref('')
const loading = ref(true)
const submitting = ref(false)
const error = ref('')

interface DayShift {
  type: 'work' | 'rest'
  start: string
  end: string
}

const days = reactive<DayShift[]>(
  WEEK_TEXT.map(() => ({ type: 'work' as const, start: '10:00', end: '20:00' }))
)

onLoad(async (options) => {
  if (!requireStaffLogin()) {
    return
  }
  technicianId.value = Number(options?.id || 0)
  technicianName.value = decodeURIComponent(String(options?.name || ''))
  try {
    const data = await getTechnicianShifts(technicianId.value)
    for (const shift of data.weekTemplate || []) {
      const index = (shift.weekday || 1) - 1
      if (index < 0 || index > 6) {
        continue
      }
      const range = shift.intervals?.[0]
      days[index].type = shift.type === 'work' ? 'work' : 'rest'
      if (range) {
        days[index].start = range.start
        days[index].end = range.end
      }
    }
  } catch (e) {
    if (handleAuthError(e)) {
      return
    }
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
})

function toggleType(index: number) {
  days[index].type = days[index].type === 'work' ? 'rest' : 'work'
}

function pickStart(index: number, event: { detail: { value: string } }) {
  days[index].start = event.detail.value
}

function pickEnd(index: number, event: { detail: { value: string } }) {
  days[index].end = event.detail.value
}

async function submit() {
  if (submitting.value) {
    return
  }
  for (let index = 0; index < 7; index += 1) {
    if (days[index].type === 'work' && days[index].start >= days[index].end) {
      uni.showToast({ title: `${WEEK_TEXT[index]}结束时间需晚于开始`, icon: 'none' })
      return
    }
  }
  submitting.value = true
  const entries = days.map((day, index) => ({
    scope: 'week' as const,
    weekday: index + 1,
    type: day.type,
    intervals: day.type === 'work' ? [{ start: day.start, end: day.end }] : []
  }))
  try {
    await updateTechnicianShifts(technicianId.value, entries)
    uni.showToast({ title: '排班已保存', icon: 'none' })
    setTimeout(() => uni.navigateBack(), 400)
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
        <text class="panel-title">{{ technicianName || '技师' }} · 每周排班</text>
        <text class="hint">设置固定每周的出勤时间，用户可预约的时段将按此生成</text>

        <view v-for="(day, index) in days" :key="index" class="day-row">
          <text class="day-label">{{ WEEK_TEXT[index] }}</text>
          <view class="type" :class="{ rest: day.type === 'rest' }" @click="toggleType(index)">
            {{ day.type === 'work' ? '上班' : '休息' }}
          </view>
          <template v-if="day.type === 'work'">
            <picker mode="time" :value="day.start" @change="pickStart(index, $event)">
              <view class="time">{{ day.start }}</view>
            </picker>
            <text class="sep">-</text>
            <picker mode="time" :value="day.end" @change="pickEnd(index, $event)">
              <view class="time">{{ day.end }}</view>
            </picker>
          </template>
          <text v-else class="rest-tip">当天不排班</text>
        </view>
      </view>

      <view class="safe-gap" />
      <view class="bar">
        <view class="bar-button" :class="{ disabled: submitting }" @click="submit">
          {{ submitting ? '保存中…' : '保存排班' }}
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

.hint {
  display: block;
  margin-top: 8rpx;
  font-size: 22rpx;
  color: #b09a8e;
}

.day-row {
  display: flex;
  align-items: center;
  padding: 24rpx 0;
  border-bottom: 1rpx solid #f5eee8;
}

.day-row:last-child {
  border-bottom: none;
}

.day-label {
  width: 96rpx;
  font-size: 26rpx;
  font-weight: 600;
  color: #3b3330;
}

.type {
  flex: 0 0 auto;
  padding: 8rpx 26rpx;
  border-radius: 26rpx;
  font-size: 24rpx;
  color: #ffffff;
  background: linear-gradient(135deg, #d6978f, #c9707c);
}

.type.rest {
  color: #a89589;
  background: #f1eae3;
}

.time {
  margin-left: 16rpx;
  padding: 8rpx 20rpx;
  border-radius: 10rpx;
  font-size: 26rpx;
  color: #3b3330;
  background: #faf6f2;
}

.sep {
  margin: 0 8rpx;
  font-size: 24rpx;
  color: #b09a8e;
}

.rest-tip {
  margin-left: 20rpx;
  font-size: 22rpx;
  color: #cbbcb2;
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
</style>
