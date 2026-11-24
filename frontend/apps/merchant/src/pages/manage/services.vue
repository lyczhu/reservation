<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import {
  deleteService,
  listCategories,
  listManagedServices,
  sortServices,
  updateServiceStatus,
  type Category,
  type ManagedService
} from '../../api/manage'
import { yuan } from '../../utils/format'
import { handleAuthError, isOwner, requireStaffLogin } from '../../utils/session'

const items = ref<ManagedService[]>([])
const categories = ref<Category[]>([])
const activeCategory = ref(0)
const loading = ref(true)
const error = ref('')
const busyId = ref(0)

const filtered = computed(() =>
  activeCategory.value === 0 ? items.value : items.value.filter((item) => item.categoryId === activeCategory.value)
)

function categoryName(id: number): string {
  return categories.value.find((item) => item.id === id)?.name || '未分类'
}

onShow(() => {
  if (!requireStaffLogin()) {
    return
  }
  if (!isOwner()) {
    error.value = '仅店长可管理服务'
    loading.value = false
    return
  }
  load()
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [list, categoryList] = await Promise.all([listManagedServices(), listCategories().catch(() => [])])
    items.value = list
    categories.value = categoryList
  } catch (e) {
    if (handleAuthError(e)) {
      return
    }
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

function chooseCategory(id: number) {
  activeCategory.value = id
}

function edit(item: ManagedService) {
  uni.navigateTo({ url: `/pages/manage/service-edit?id=${item.id}` })
}

function create() {
  uni.navigateTo({ url: '/pages/manage/service-edit' })
}

async function toggle(item: ManagedService) {
  if (busyId.value) {
    return
  }
  busyId.value = item.id
  try {
    await updateServiceStatus(item.id, item.status === 1 ? 2 : 1)
    item.status = item.status === 1 ? 2 : 1
    uni.showToast({ title: item.status === 1 ? '已上架' : '已下架', icon: 'none' })
  } catch (e) {
    uni.showToast({ title: e instanceof Error ? e.message : '操作失败', icon: 'none' })
  } finally {
    busyId.value = 0
  }
}

function remove(item: ManagedService) {
  uni.showModal({
    title: '删除服务',
    content: `确认删除「${item.name}」？已产生的历史预约不受影响。`,
    confirmText: '删除',
    confirmColor: '#c9707c',
    success: async (res) => {
      if (!res.confirm) {
        return
      }
      try {
        await deleteService(item.id)
        items.value = items.value.filter((row) => row.id !== item.id)
        uni.showToast({ title: '已删除', icon: 'none' })
      } catch (e) {
        uni.showToast({ title: e instanceof Error ? e.message : '删除失败', icon: 'none' })
      }
    }
  })
}

async function move(item: ManagedService, direction: -1 | 1) {
  const list = activeCategory.value === 0 ? items.value : filtered.value
  const index = list.findIndex((row) => row.id === item.id)
  const target = index + direction
  if (index < 0 || target < 0 || target >= list.length) {
    return
  }
  const reordered = [...list]
  ;[reordered[index], reordered[target]] = [reordered[target], reordered[index]]
  if (activeCategory.value === 0) {
    items.value = reordered
  } else {
    items.value = items.value.map((row) => reordered.find((r) => r.id === row.id) || row)
  }
  try {
    await sortServices(reordered.map((row) => row.id))
  } catch (e) {
    uni.showToast({ title: e instanceof Error ? e.message : '排序保存失败', icon: 'none' })
    load()
  }
}
</script>

<template>
  <view class="page">
    <scroll-view v-if="categories.length" scroll-x class="cats" :show-scrollbar="false">
      <view class="cats-inner">
        <view class="cat-chip" :class="{ active: activeCategory === 0 }" @click="chooseCategory(0)">全部</view>
        <view
          v-for="category in categories"
          :key="category.id"
          class="cat-chip"
          :class="{ active: activeCategory === category.id }"
          @click="chooseCategory(category.id)"
        >
          {{ category.name }}
        </view>
      </view>
    </scroll-view>

    <view v-if="loading" class="state">加载中…</view>
    <view v-else-if="error" class="state error">{{ error }}</view>
    <view v-else-if="!filtered.length" class="state empty">
      <text class="empty-icon">💅</text>
      <text>暂无服务，点击右下角新增</text>
    </view>

    <view v-else class="list">
      <view v-for="(item, index) in filtered" :key="item.id" class="card" :class="{ off: item.status !== 1 }">
        <view class="card-main">
          <image v-if="item.cover" class="cover" :src="item.cover" mode="aspectFill" />
          <view v-else class="cover placeholder">无图</view>
          <view class="info">
            <view class="name-row">
              <text class="name">{{ item.name }}</text>
              <text v-if="item.status !== 1" class="badge">已下架</text>
            </view>
            <text class="meta">{{ categoryName(item.categoryId) }} · {{ item.durationMin }}分钟 · 已约{{ item.bookedCount }}次</text>
            <text class="price">¥{{ yuan(item.priceCents) }}</text>
          </view>
        </view>
        <view class="actions">
          <view class="act" :class="{ disabled: index === 0 }" @click="move(item, -1)">上移</view>
          <view class="act" :class="{ disabled: index === filtered.length - 1 }" @click="move(item, 1)">下移</view>
          <view class="act" @click="edit(item)">编辑</view>
          <view class="act" @click="toggle(item)">{{ item.status === 1 ? '下架' : '上架' }}</view>
          <view class="act danger" @click="remove(item)">删除</view>
        </view>
      </view>
    </view>

    <view class="fab" @click="create">＋ 新增服务</view>
  </view>
</template>

<style scoped>
.page {
  min-height: 100vh;
  padding: 20rpx 32rpx 140rpx;
  background: #faf6f2;
}

.cats {
  margin-top: 4rpx;
}

.cats-inner {
  display: flex;
}

.cat-chip {
  flex: 0 0 auto;
  margin-right: 14rpx;
  padding: 12rpx 28rpx;
  border-radius: 30rpx;
  font-size: 25rpx;
  color: #6b615b;
  background: #ffffff;
}

.cat-chip.active {
  color: #ffffff;
  background: linear-gradient(135deg, #d6978f, #c9707c);
}

.list {
  margin-top: 20rpx;
}

.card {
  margin-bottom: 20rpx;
  padding: 24rpx;
  border-radius: 22rpx;
  background: #ffffff;
  box-shadow: 0 8rpx 20rpx rgba(104, 74, 68, 0.04);
}

.card.off .cover,
.card.off .price {
  opacity: 0.45;
}

.card-main {
  display: flex;
}

.cover {
  flex: 0 0 132rpx;
  width: 132rpx;
  height: 132rpx;
  border-radius: 16rpx;
  background: #f4ece6;
}

.cover.placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22rpx;
  color: #b09a8e;
}

.info {
  flex: 1;
  margin-left: 20rpx;
  min-width: 0;
}

.name-row {
  display: flex;
  align-items: center;
}

.name {
  font-size: 30rpx;
  font-weight: 600;
  color: #3b3330;
}

.badge {
  margin-left: 12rpx;
  padding: 2rpx 12rpx;
  border-radius: 8rpx;
  font-size: 20rpx;
  color: #a89589;
  background: #f1eae3;
}

.meta {
  display: block;
  margin-top: 8rpx;
  font-size: 22rpx;
  color: #b09a8e;
}

.price {
  display: block;
  margin-top: 8rpx;
  font-size: 28rpx;
  font-weight: 600;
  color: #c9707c;
}

.actions {
  display: flex;
  margin-top: 18rpx;
  padding-top: 16rpx;
  border-top: 1rpx solid #f5eee8;
}

.act {
  flex: 1;
  text-align: center;
  font-size: 24rpx;
  color: #8c827c;
}

.act.disabled {
  opacity: 0.35;
}

.act.danger {
  color: #c9707c;
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

.empty-icon {
  display: block;
  margin-bottom: 12rpx;
  font-size: 60rpx;
}

.fab {
  position: fixed;
  right: 32rpx;
  bottom: calc(40rpx + env(safe-area-inset-bottom));
  padding: 20rpx 40rpx;
  border-radius: 44rpx;
  font-size: 28rpx;
  font-weight: 600;
  color: #ffffff;
  background: linear-gradient(135deg, #d6978f, #c9707c);
  box-shadow: 0 12rpx 28rpx rgba(201, 112, 124, 0.35);
}
</style>
