<script setup lang="ts">
import { reactive, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import {
  createCategory,
  deleteCategory,
  listCategories,
  sortCategories,
  updateCategory,
  updateCategoryStatus,
  type Category
} from '../../api/manage'
import { handleAuthError, requireStaffLogin } from '../../utils/session'

const items = ref<Category[]>([])
const loading = ref(true)
const error = ref('')
const submitting = ref(false)

const editorVisible = ref(false)
const editingId = ref(0)
const form = reactive({ name: '', icon: '', sort: 0 })

onShow(() => {
  if (!requireStaffLogin()) {
    return
  }
  load()
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    items.value = await listCategories()
  } catch (e) {
    if (handleAuthError(e)) {
      return
    }
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = 0
  form.name = ''
  form.icon = ''
  // 新建默认排到末尾
  form.sort = items.value.reduce((max, item) => Math.max(max, item.sort || 0), 0) + 10
  editorVisible.value = true
}

function openEdit(item: Category) {
  editingId.value = item.id
  form.name = item.name
  form.icon = item.icon
  form.sort = item.sort || 0
  editorVisible.value = true
}

async function save() {
  if (submitting.value) {
    return
  }
  if (!form.name.trim()) {
    uni.showToast({ title: '请填写分类名称', icon: 'none' })
    return
  }
  if (!form.icon.trim()) {
    uni.showToast({ title: '请填写图标地址', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    const payload = { name: form.name.trim(), icon: form.icon.trim(), sort: Number(form.sort) || 0 }
    if (editingId.value) {
      await updateCategory(editingId.value, payload)
    } else {
      await createCategory(payload)
    }
    editorVisible.value = false
    uni.showToast({ title: '已保存', icon: 'none' })
    load()
  } catch (e) {
    uni.showToast({ title: e instanceof Error ? e.message : '保存失败', icon: 'none' })
  } finally {
    submitting.value = false
  }
}

async function toggle(item: Category) {
  try {
    await updateCategoryStatus(item.id, item.status === 1 ? 2 : 1)
    item.status = item.status === 1 ? 2 : 1
  } catch (e) {
    uni.showToast({ title: e instanceof Error ? e.message : '操作失败', icon: 'none' })
  }
}

function remove(item: Category) {
  uni.showModal({
    title: '删除分类',
    content: `确认删除「${item.name}」？分类下若有服务将无法删除。`,
    confirmText: '删除',
    confirmColor: '#c9707c',
    success: async (res) => {
      if (!res.confirm) {
        return
      }
      try {
        await deleteCategory(item.id)
        items.value = items.value.filter((row) => row.id !== item.id)
        uni.showToast({ title: '已删除', icon: 'none' })
      } catch (e) {
        uni.showToast({ title: e instanceof Error ? e.message : '删除失败', icon: 'none' })
      }
    }
  })
}

async function move(item: Category, direction: -1 | 1) {
  const index = items.value.findIndex((row) => row.id === item.id)
  const target = index + direction
  if (index < 0 || target < 0 || target >= items.value.length) {
    return
  }
  const reordered = [...items.value]
  ;[reordered[index], reordered[target]] = [reordered[target], reordered[index]]
  items.value = reordered
  try {
    await sortCategories(reordered.map((row) => row.id))
  } catch (e) {
    uni.showToast({ title: e instanceof Error ? e.message : '排序保存失败', icon: 'none' })
    load()
  }
}
</script>

<template>
  <view class="page">
    <view v-if="loading" class="state">加载中…</view>
    <view v-else-if="error" class="state error">{{ error }}</view>
    <view v-else-if="!items.length" class="state empty">
      <text class="empty-icon">🗂</text>
      <text>暂无分类，点击右下角新增</text>
    </view>

    <view v-else class="list">
      <view v-for="(item, index) in items" :key="item.id" class="card" :class="{ off: item.status !== 1 }">
        <image v-if="item.icon" class="icon" :src="item.icon" mode="aspectFill" />
        <view v-else class="icon placeholder">无图</view>
        <view class="info">
          <view class="name-row">
            <text class="name">{{ item.name }}</text>
            <text v-if="item.status !== 1" class="badge">已停用</text>
          </view>
          <text class="meta">排序 {{ item.sort }}</text>
        </view>
        <view class="op" :class="{ disabled: index === 0 }" @click="move(item, -1)">↑</view>
        <view class="op" :class="{ disabled: index === items.length - 1 }" @click="move(item, 1)">↓</view>
        <view class="op" @click="openEdit(item)">编辑</view>
        <view class="op" @click="toggle(item)">{{ item.status === 1 ? '停用' : '启用' }}</view>
        <view class="op danger" @click="remove(item)">删除</view>
      </view>
    </view>

    <view class="fab" @click="openCreate">＋ 新增分类</view>

    <view v-if="editorVisible" class="mask" @click.self="editorVisible = false">
      <view class="editor">
        <text class="editor-title">{{ editingId ? '编辑分类' : '新增分类' }}</text>
        <view class="field">
          <text class="label">名称</text>
          <input v-model="form.name" class="input" placeholder="如：美甲（≤10字）" placeholder-class="ph" />
        </view>
        <view class="field">
          <text class="label">图标</text>
          <input v-model="form.icon" class="input" placeholder="https://… 图片地址" placeholder-class="ph" />
        </view>
        <view class="field">
          <text class="label">排序</text>
          <input v-model="form.sort" class="input" type="number" placeholder="数字越小越靠前" placeholder-class="ph" />
        </view>
        <image v-if="form.icon" class="icon-preview" :src="form.icon" mode="aspectFill" />
        <view class="editor-actions">
          <view class="btn ghost" @click="editorVisible = false">取消</view>
          <view class="btn primary" :class="{ disabled: submitting }" @click="save">
            {{ submitting ? '保存中…' : '保存' }}
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<style scoped>
.page {
  min-height: 100vh;
  padding: 24rpx 32rpx 140rpx;
  background: #faf6f2;
}

.card {
  display: flex;
  align-items: center;
  margin-bottom: 20rpx;
  padding: 24rpx;
  border-radius: 22rpx;
  background: #ffffff;
  box-shadow: 0 8rpx 20rpx rgba(104, 74, 68, 0.04);
}

.card.off .icon,
.card.off .name {
  opacity: 0.5;
}

.icon {
  flex: 0 0 88rpx;
  width: 88rpx;
  height: 88rpx;
  border-radius: 18rpx;
  background: #f4ece6;
}

.icon.placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20rpx;
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
  margin-top: 6rpx;
  font-size: 22rpx;
  color: #b09a8e;
}

.op {
  margin-left: 18rpx;
  font-size: 24rpx;
  color: #8c827c;
}

.op.disabled {
  opacity: 0.3;
}

.op.danger {
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

.mask {
  position: fixed;
  inset: 0;
  z-index: 20;
  display: flex;
  align-items: flex-end;
  background: rgba(59, 51, 48, 0.45);
}

.editor {
  width: 100%;
  padding: 32rpx 32rpx calc(32rpx + env(safe-area-inset-bottom));
  border-radius: 28rpx 28rpx 0 0;
  background: #ffffff;
  box-sizing: border-box;
}

.editor-title {
  display: block;
  margin-bottom: 8rpx;
  font-size: 30rpx;
  font-weight: 600;
  color: #3b3330;
}

.field {
  display: flex;
  align-items: center;
  padding: 22rpx 0;
  border-bottom: 1rpx solid #f5eee8;
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

.icon-preview {
  width: 120rpx;
  height: 120rpx;
  margin-top: 18rpx;
  border-radius: 16rpx;
  background: #f4ece6;
}

.editor-actions {
  display: flex;
  gap: 20rpx;
  margin-top: 30rpx;
}

.btn {
  flex: 1;
  padding: 20rpx 0;
  border-radius: 40rpx;
  text-align: center;
  font-size: 28rpx;
}

.btn.ghost {
  color: #8c827c;
  background: #f4ece6;
}

.btn.primary {
  color: #ffffff;
  font-weight: 600;
  background: linear-gradient(135deg, #d6978f, #c9707c);
}

.btn.primary.disabled {
  opacity: 0.55;
}

.ph {
  color: #cbbcb2;
}
</style>
