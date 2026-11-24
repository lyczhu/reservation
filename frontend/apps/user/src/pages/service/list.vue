<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getServices, getStoreHome, type Category, type ServiceCard } from '../../api/catalog'
import { durationText, yuan } from '../../utils/format'

const categories = ref<Category[]>([])
const services = ref<ServiceCard[]>([])
const activeCategory = ref(0)
const activeSort = ref<'default' | 'price' | 'hot'>('default')
const loading = ref(true)

const sorts = [
  { key: 'default', text: '默认' },
  { key: 'price', text: '价格' },
  { key: 'hot', text: '热度' }
] as const

onLoad(async (options) => {
  if (options?.categoryId) {
    activeCategory.value = Number(options.categoryId)
  }
  if (options?.name) {
    uni.setNavigationBarTitle({ title: decodeURIComponent(options.name) })
  }
  try {
    const home = await getStoreHome()
    categories.value = home.categories
  } catch {
    categories.value = []
  }
  await loadServices()
})

async function loadServices() {
  loading.value = true
  try {
    services.value = await getServices({
      categoryId: activeCategory.value || undefined,
      sort: activeSort.value
    })
  } catch (e) {
    uni.showToast({ title: e instanceof Error ? e.message : '加载失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}

function selectCategory(id: number) {
  if (activeCategory.value === id) {
    return
  }
  activeCategory.value = id
  loadServices()
}

function selectSort(key: 'default' | 'price' | 'hot') {
  if (activeSort.value === key) {
    return
  }
  activeSort.value = key
  loadServices()
}

function goDetail(service: ServiceCard) {
  uni.navigateTo({ url: `/pages/service/detail?id=${service.id}` })
}
</script>

<template>
  <view class="page">
    <scroll-view scroll-x class="chips" :show-scrollbar="false">
      <view class="chip" :class="{ active: activeCategory === 0 }" @click="selectCategory(0)">全部</view>
      <view
        v-for="category in categories"
        :key="category.id"
        class="chip"
        :class="{ active: activeCategory === category.id }"
        @click="selectCategory(category.id)"
      >
        {{ category.name }}
      </view>
    </scroll-view>

    <view class="sort-row">
      <view
        v-for="item in sorts"
        :key="item.key"
        class="sort"
        :class="{ active: activeSort === item.key }"
        @click="selectSort(item.key)"
      >
        {{ item.text }}
      </view>
    </view>

    <view class="list">
      <view v-for="service in services" :key="service.id" class="card" @click="goDetail(service)">
        <image v-if="service.cover" class="cover" :src="service.cover" mode="aspectFill" />
        <view v-else class="cover cover-fallback">{{ service.name.slice(0, 1) }}</view>
        <view class="info">
          <text class="name">{{ service.name }}</text>
          <text class="meta">{{ durationText(service.durationMin) }} · 已约 {{ service.bookedCount }} 次</text>
          <view class="bottom">
            <text class="price">¥{{ yuan(service.priceCents) }}</text>
            <view class="book">预约</view>
          </view>
        </view>
      </view>
    </view>

    <view v-if="loading" class="state">加载中…</view>
    <view v-else-if="!services.length" class="state">该分类暂无服务</view>
  </view>
</template>

<style scoped>
.page {
  min-height: 100vh;
  padding: 24rpx 32rpx 48rpx;
  background: #faf6f2;
}

.chips {
  white-space: nowrap;
  margin-bottom: 20rpx;
}

.chip {
  display: inline-block;
  margin-right: 16rpx;
  padding: 12rpx 32rpx;
  border-radius: 32rpx;
  font-size: 26rpx;
  color: #8c827c;
  background: #ffffff;
}

.chip.active {
  color: #ffffff;
  background: #c97b84;
}

.sort-row {
  display: flex;
  gap: 32rpx;
  margin-bottom: 24rpx;
  padding: 0 8rpx;
}

.sort {
  font-size: 26rpx;
  color: #8c827c;
}

.sort.active {
  font-weight: 600;
  color: #3b3330;
}

.sort.active::after {
  content: '';
  display: block;
  width: 32rpx;
  height: 6rpx;
  margin: 6rpx auto 0;
  border-radius: 4rpx;
  background: #c97b84;
}

.card {
  display: flex;
  margin-bottom: 24rpx;
  padding: 20rpx;
  border-radius: 24rpx;
  background: #ffffff;
  box-shadow: 0 10rpx 28rpx rgba(104, 74, 68, 0.05);
}

.cover {
  width: 200rpx;
  height: 200rpx;
  border-radius: 20rpx;
  flex-shrink: 0;
}

.cover-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 56rpx;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.9);
  background: linear-gradient(135deg, #e3c2bc, #c9968f);
}

.info {
  flex: 1;
  display: flex;
  flex-direction: column;
  margin-left: 24rpx;
}

.name {
  font-size: 30rpx;
  font-weight: 600;
  color: #3b3330;
}

.meta {
  margin-top: 10rpx;
  font-size: 22rpx;
  color: #b09a8e;
}

.bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: auto;
}

.price {
  font-size: 32rpx;
  font-weight: 600;
  color: #c97b84;
}

.book {
  padding: 10rpx 36rpx;
  border-radius: 28rpx;
  font-size: 24rpx;
  color: #ffffff;
  background: #c97b84;
}

.state {
  padding: 80rpx 0;
  text-align: center;
  font-size: 26rpx;
  color: #b09a8e;
}
</style>
