<script setup lang="ts">
import { reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getStore, updateStore, type TimeRange } from '../../api/manage'
import { handleAuthError, requireStaffLogin } from '../../utils/session'

const WEEK_TEXT = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']

const loading = ref(true)
const submitting = ref(false)
const error = ref('')
const lat = ref<number | null>(null)
const lng = ref<number | null>(null)

const form = reactive({
  name: '',
  logo: '',
  phone: '',
  address: '',
  announcement: ''
})

interface DayHours {
  open: boolean
  start: string
  end: string
}

const days = reactive<DayHours[]>(WEEK_TEXT.map(() => ({ open: true, start: '10:00', end: '20:00' })))

onLoad(async () => {
  if (!requireStaffLogin()) {
    return
  }
  try {
    const store = await getStore()
    form.name = store.name
    form.logo = store.logo || ''
    form.phone = store.phone || ''
    form.address = store.address || ''
    form.announcement = store.announcement || ''
    lat.value = store.lat
    lng.value = store.lng
    for (let weekday = 1; weekday <= 7; weekday += 1) {
      const ranges = store.businessHours?.[String(weekday)] || []
      const range = ranges[0]
      days[weekday - 1].open = Boolean(range)
      if (range) {
        days[weekday - 1].start = range.start
        days[weekday - 1].end = range.end
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

function toggleOpen(index: number) {
  days[index].open = !days[index].open
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
  if (!form.name.trim()) {
    uni.showToast({ title: '请填写门店名称', icon: 'none' })
    return
  }
  for (let index = 0; index < 7; index += 1) {
    if (days[index].open && days[index].start >= days[index].end) {
      uni.showToast({ title: `${WEEK_TEXT[index]}结束时间需晚于开始`, icon: 'none' })
      return
    }
  }
  const businessHours: Record<string, TimeRange[]> = {}
  days.forEach((day, index) => {
    businessHours[String(index + 1)] = day.open ? [{ start: day.start, end: day.end }] : []
  })
  submitting.value = true
  try {
    await updateStore({
      name: form.name.trim(),
      logo: form.logo.trim(),
      banner: [],
      address: form.address.trim(),
      lat: lat.value,
      lng: lng.value,
      phone: form.phone.trim(),
      businessHours,
      announcement: form.announcement.trim()
    })
    uni.showToast({ title: '已保存', icon: 'none' })
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
        <text class="panel-title">门店资料</text>
        <view class="field">
          <text class="label">名称</text>
          <input v-model="form.name" class="input" placeholder="门店名称" placeholder-class="ph" />
        </view>
        <view class="field">
          <text class="label">电话</text>
          <input v-model="form.phone" class="input" type="tel" placeholder="联系电话" placeholder-class="ph" />
        </view>
        <view class="field">
          <text class="label">地址</text>
          <input v-model="form.address" class="input" placeholder="门店地址" placeholder-class="ph" />
        </view>
        <view class="field">
          <text class="label">Logo</text>
          <input v-model="form.logo" class="input" placeholder="https://… 图片地址" placeholder-class="ph" />
        </view>
        <view class="field">
          <text class="label">公告</text>
          <input v-model="form.announcement" class="input" placeholder="展示在首页的公告（选填）" placeholder-class="ph" />
        </view>
      </view>

      <view class="panel">
        <text class="panel-title">营业时间</text>
        <text class="hint">关闭某天表示门店休息，用户端当天不可预约</text>
        <view v-for="(day, index) in days" :key="index" class="day-row">
          <text class="day-label">{{ WEEK_TEXT[index] }}</text>
          <view class="type" :class="{ rest: !day.open }" @click="toggleOpen(index)">
            {{ day.open ? '营业' : '休息' }}
          </view>
          <template v-if="day.open">
            <picker mode="time" :value="day.start" @change="pickStart(index, $event)">
              <view class="time">{{ day.start }}</view>
            </picker>
            <text class="sep">-</text>
            <picker mode="time" :value="day.end" @change="pickEnd(index, $event)">
              <view class="time">{{ day.end }}</view>
            </picker>
          </template>
          <text v-else class="rest-tip">当天不营业</text>
        </view>
      </view>

      <view class="panel">
        <text class="panel-title">预约规则</text>
        <text class="hint">时段粒度、可约天数、提前量等规则在独立页面设置</text>
        <view class="link" @click="uni.navigateTo({ url: '/pages/manage/rules' })">前往设置预约规则 ›</view>
      </view>

      <view class="safe-gap" />
      <view class="bar">
        <view class="bar-button" :class="{ disabled: submitting }" @click="submit">
          {{ submitting ? '保存中…' : '保存门店资料' }}
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

.hint {
  display: block;
  margin-top: 8rpx;
  font-size: 22rpx;
  color: #b09a8e;
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
  width: 120rpx;
  font-size: 26rpx;
  color: #8c827c;
}

.input {
  flex: 1;
  font-size: 28rpx;
  color: #3b3330;
}

.day-row {
  display: flex;
  align-items: center;
  padding: 24rpx 0;
  border-bottom: 1rpx solid #f5eee8;
}

.day-row:last-of-type {
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

.link {
  padding: 24rpx 0;
  font-size: 26rpx;
  color: #c9707c;
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
