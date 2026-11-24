<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import {
  createService,
  listCategories,
  listManagedServices,
  listManagedTechnicians,
  updateService,
  type Category,
  type ManagedTechnician
} from '../../api/manage'
import { handleAuthError, requireStaffLogin } from '../../utils/session'

const editingId = ref(0)
const categories = ref<Category[]>([])
const technicians = ref<ManagedTechnician[]>([])
const loading = ref(true)
const submitting = ref(false)
const error = ref('')

const form = reactive({
  name: '',
  categoryId: 0,
  durationMin: 60,
  priceYuan: '',
  cover: '',
  description: '',
  notes: '',
  sort: 0,
  technicianIds: [] as number[]
})

const categoryNames = computed(() => categories.value.map((item) => item.name))
const categoryIndex = computed(() => categories.value.findIndex((item) => item.id === form.categoryId))

onLoad(async (options) => {
  if (!requireStaffLogin()) {
    return
  }
  editingId.value = Number(options?.id || 0)
  uni.setNavigationBarTitle({ title: editingId.value ? '编辑服务' : '新增服务' })
  try {
    const [categoryList, techList, serviceList] = await Promise.all([
      listCategories(),
      listManagedTechnicians(),
      listManagedServices()
    ])
    categories.value = categoryList
    technicians.value = techList.filter((tech) => tech.status === 1)
    if (editingId.value) {
      const target = serviceList.find((item) => item.id === editingId.value)
      if (target) {
        form.name = target.name
        form.categoryId = target.categoryId
        form.durationMin = target.durationMin
        form.priceYuan = (target.priceCents / 100).toFixed(2)
        form.cover = target.cover
        form.description = target.description || ''
        form.notes = target.notes || ''
        form.sort = target.sort || 0
        form.technicianIds = [...(target.technicianIds || [])]
      }
    } else {
      // 新建默认排到末尾
      form.sort = serviceList.reduce((max, item) => Math.max(max, item.sort || 0), 0) + 10
      if (categories.value.length) {
        form.categoryId = categories.value[0].id
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

function chooseCategory(event: { detail: { value: number | string } }) {
  const index = Number(event.detail.value)
  if (categories.value[index]) {
    form.categoryId = categories.value[index].id
  }
}

function toggleTechnician(id: number) {
  const index = form.technicianIds.indexOf(id)
  if (index >= 0) {
    form.technicianIds.splice(index, 1)
  } else {
    form.technicianIds.push(id)
  }
}

async function submit() {
  if (submitting.value) {
    return
  }
  if (!form.name.trim()) {
    uni.showToast({ title: '请填写服务名称', icon: 'none' })
    return
  }
  if (!form.categoryId) {
    uni.showToast({ title: '请选择分类', icon: 'none' })
    return
  }
  if (!form.cover.trim()) {
    uni.showToast({ title: '请填写封面图片地址', icon: 'none' })
    return
  }
  const price = Math.round(Number(form.priceYuan) * 100)
  if (!Number.isFinite(price) || price < 0) {
    uni.showToast({ title: '请填写正确的价格', icon: 'none' })
    return
  }
  if (!form.technicianIds.length) {
    uni.showToast({ title: '请至少选择一名可约技师', icon: 'none' })
    return
  }
  submitting.value = true
  const payload = {
    categoryId: form.categoryId,
    name: form.name.trim(),
    cover: form.cover.trim(),
    images: [] as string[],
    durationMin: Number(form.durationMin) || 60,
    priceCents: price,
    description: form.description.trim(),
    notes: form.notes.trim(),
    sort: Number(form.sort) || 0,
    technicianIds: form.technicianIds
  }
  try {
    if (editingId.value) {
      await updateService(editingId.value, payload)
    } else {
      await createService(payload)
    }
    uni.showToast({ title: '已保存', icon: 'none' })
    uni.navigateBack()
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
        <view class="field">
          <text class="label">名称</text>
          <input v-model="form.name" class="input" placeholder="如：法式美甲" placeholder-class="ph" />
        </view>
        <view class="field">
          <text class="label">分类</text>
          <picker class="picker" mode="selector" :range="categoryNames" :value="categoryIndex" @change="chooseCategory">
            <view class="picker-value">{{ categories[categoryIndex]?.name || '请选择' }}›</view>
          </picker>
        </view>
        <view class="field">
          <text class="label">时长</text>
          <input v-model="form.durationMin" class="input" type="number" placeholder="15-240 分钟" placeholder-class="ph" />
          <text class="unit">分钟</text>
        </view>
        <view class="field">
          <text class="label">价格</text>
          <input v-model="form.priceYuan" class="input" type="digit" placeholder="0.00" placeholder-class="ph" />
          <text class="unit">元</text>
        </view>
        <view class="field">
          <text class="label">排序</text>
          <input v-model="form.sort" class="input" type="number" placeholder="数字越小越靠前" placeholder-class="ph" />
        </view>
      </view>

      <view class="panel">
        <text class="panel-title">封面图</text>
        <input v-model="form.cover" class="input full" placeholder="https://… 图片地址" placeholder-class="ph" />
        <image v-if="form.cover" class="cover-preview" :src="form.cover" mode="aspectFill" />
        <text class="panel-title second">详情描述</text>
        <textarea v-model="form.description" class="textarea" placeholder="服务介绍（选填）" placeholder-class="ph" />
        <text class="panel-title second">备注</text>
        <input v-model="form.notes" class="input full" placeholder="注意事项（选填）" placeholder-class="ph" />
      </view>

      <view class="panel">
        <text class="panel-title">可约技师</text>
        <view class="chips">
          <view
            v-for="tech in technicians"
            :key="tech.id"
            class="chip"
            :class="{ active: form.technicianIds.includes(tech.id) }"
            @click="toggleTechnician(tech.id)"
          >
            {{ tech.name }}
          </view>
        </view>
        <text class="hint">至少选择一名技师提供服务</text>
      </view>

      <view class="safe-gap" />
      <view class="bar">
        <view class="bar-button" :class="{ disabled: submitting }" @click="submit">
          {{ submitting ? '保存中…' : '保存' }}
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
  padding: 10rpx 28rpx 24rpx;
  border-radius: 22rpx;
  background: #ffffff;
  box-shadow: 0 8rpx 20rpx rgba(104, 74, 68, 0.04);
}

.panel-title {
  display: block;
  padding-top: 20rpx;
  font-size: 28rpx;
  font-weight: 600;
  color: #3b3330;
}

.panel-title.second {
  margin-top: 22rpx;
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
  width: 130rpx;
  font-size: 26rpx;
  color: #8c827c;
}

.input {
  flex: 1;
  font-size: 28rpx;
  color: #3b3330;
}

.input.full {
  margin-top: 18rpx;
  padding: 18rpx 20rpx;
  border-radius: 14rpx;
  background: #faf6f2;
}

.unit {
  margin-left: 8rpx;
  font-size: 24rpx;
  color: #b09a8e;
}

.picker {
  flex: 1;
}

.picker-value {
  font-size: 28rpx;
  color: #3b3330;
}

.cover-preview {
  width: 100%;
  height: 280rpx;
  margin-top: 16rpx;
  border-radius: 16rpx;
  background: #f4ece6;
}

.textarea {
  width: 100%;
  height: 160rpx;
  margin-top: 16rpx;
  padding: 18rpx 20rpx;
  border-radius: 14rpx;
  font-size: 26rpx;
  color: #3b3330;
  background: #faf6f2;
  box-sizing: border-box;
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
  margin-top: 18rpx;
}

.chip {
  padding: 14rpx 30rpx;
  border-radius: 30rpx;
  border: 2rpx solid transparent;
  font-size: 26rpx;
  color: #3b3330;
  background: #faf6f2;
}

.chip.active {
  border-color: #c97b84;
  color: #c9707c;
  background: #fbf0ee;
}

.hint {
  display: block;
  margin-top: 14rpx;
  font-size: 22rpx;
  color: #b09a8e;
}

.ph {
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
