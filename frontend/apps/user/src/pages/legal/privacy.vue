<script setup lang="ts">
import { onLoad } from '@dcloudio/uni-app'
import { BRAND } from '@reservation/shared'

const doc = BRAND.legal.privacy
const sections = doc.sections

onLoad(() => {
  uni.setNavigationBarTitle({ title: '隐私政策' })
})
</script>

<template>
  <view class="page">
    <text class="doc-title">{{ doc.title }}</text>
    <text class="doc-date">更新日期：{{ doc.updatedAt }}　生效日期：{{ doc.effectiveAt }}</text>
    <text class="intro">
      {{ doc.intro }}
    </text>

    <view v-for="(section, index) in sections" :key="index" class="section">
      <text class="section-title">{{ section.title }}</text>
      <text v-for="(item, itemIndex) in section.items" :key="itemIndex" class="item">{{ item }}</text>
    </view>

    <text class="footer">{{ doc.footer }}</text>
  </view>
</template>

<style scoped>
.page {
  min-height: 100vh;
  padding: 40rpx 40rpx 80rpx;
  background: #faf6f2;
}

.doc-title {
  display: block;
  font-size: 40rpx;
  font-weight: 700;
  color: #3b3330;
}

.doc-date {
  display: block;
  margin-top: 14rpx;
  font-size: 22rpx;
  color: #b09a8e;
}

.intro {
  display: block;
  margin-top: 26rpx;
  font-size: 26rpx;
  line-height: 1.8;
  color: #5c524c;
}

.section {
  margin-top: 36rpx;
}

.section-title {
  display: block;
  font-size: 30rpx;
  font-weight: 600;
  color: #3b3330;
}

.item {
  display: block;
  margin-top: 14rpx;
  font-size: 26rpx;
  line-height: 1.8;
  color: #5c524c;
}

.footer {
  display: block;
  margin-top: 60rpx;
  text-align: center;
  font-size: 22rpx;
  color: #cbbcb2;
}
</style>
