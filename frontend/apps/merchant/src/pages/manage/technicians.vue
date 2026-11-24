<script setup lang="ts">
import { reactive, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import {
  createTechnician,
  listManagedTechnicians,
  updateTechnician,
  updateTechnicianStatus,
  type ManagedTechnician,
  type TechnicianPayload
} from '../../api/manage'
import { handleAuthError, requireStaffLogin } from '../../utils/session'

const items = ref<ManagedTechnician[]>([])
const loading = ref(true)
const error = ref('')
const submitting = ref(false)

const editorVisible = ref(false)
const editingId = ref(0)
const hasAccount = ref(false)
const form = reactive({
  name: '',
  avatar: '',
  title: '',
  intro: '',
  accountUsername: '',
  accountPassword: ''
})

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
    items.value = await listManagedTechnicians()
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
  hasAccount.value = false
  form.name = ''
  form.avatar = ''
  form.title = ''
  form.intro = ''
  form.accountUsername = ''
  form.accountPassword = ''
  editorVisible.value = true
}

function openEdit(item: ManagedTechnician) {
  editingId.value = item.id
  hasAccount.value = Boolean(item.accountUsername)
  form.name = item.name
  form.avatar = item.avatar
  form.title = item.title || ''
  form.intro = item.intro || ''
  form.accountUsername = ''
  form.accountPassword = ''
  editorVisible.value = true
}

async function save() {
  if (submitting.value) {
    return
  }
  if (!form.name.trim()) {
    uni.showToast({ title: '请填写技师姓名', icon: 'none' })
    return
  }
  if (!form.avatar.trim()) {
    uni.showToast({ title: '请填写头像地址', icon: 'none' })
    return
  }
  if (!hasAccount.value && (form.accountUsername.trim() || form.accountPassword.trim())) {
    if (!/^1[3-9]\d{9}$/.test(form.accountUsername.trim())) {
      uni.showToast({ title: '账号需为手机号格式', icon: 'none' })
      return
    }
    if (form.accountPassword.length < 6) {
      uni.showToast({ title: '密码至少 6 位', icon: 'none' })
      return
    }
  }
  submitting.value = true
  try {
    const payload: TechnicianPayload = {
      name: form.name.trim(),
      avatar: form.avatar.trim(),
      title: form.title.trim(),
      intro: form.intro.trim()
    }
    if (!hasAccount.value && form.accountUsername.trim() && form.accountPassword.trim()) {
      payload.accountUsername = form.accountUsername.trim()
      payload.accountPassword = form.accountPassword
    }
    if (editingId.value) {
      await updateTechnician(editingId.value, payload)
    } else {
      await createTechnician(payload)
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

async function toggle(item: ManagedTechnician) {
  uni.showModal({
    title: item.status === 1 ? '停用技师' : '启用技师',
    content:
      item.status === 1
        ? '停用后该技师不可被新预约；其未来活跃预约需先处理。'
        : '启用后技师将重新参与排班与分配。',
    confirmColor: '#c9707c',
    success: async (res) => {
      if (!res.confirm) {
        return
      }
      try {
        await updateTechnicianStatus(item.id, item.status === 1 ? 2 : 1)
        item.status = item.status === 1 ? 2 : 1
      } catch (e) {
        uni.showToast({ title: e instanceof Error ? e.message : '操作失败', icon: 'none' })
      }
    }
  })
}

function openShifts(item: ManagedTechnician) {
  uni.navigateTo({ url: `/pages/manage/technician-shifts?id=${item.id}&name=${encodeURIComponent(item.name)}` })
}
</script>

<template>
  <view class="page">
    <view v-if="loading" class="state">加载中…</view>
    <view v-else-if="error" class="state error">{{ error }}</view>
    <view v-else-if="!items.length" class="state empty">
      <text class="empty-icon">💇</text>
      <text>暂无技师，点击右下角新增</text>
    </view>

    <view v-else class="list">
      <view v-for="item in items" :key="item.id" class="card" :class="{ off: item.status !== 1 }">
        <image v-if="item.avatar" class="avatar" :src="item.avatar" mode="aspectFill" />
        <view v-else class="avatar placeholder">{{ item.name.slice(0, 1) }}</view>
        <view class="info">
          <view class="name-row">
            <text class="name">{{ item.name }}</text>
            <text class="title">{{ item.title || '技师' }}</text>
            <text v-if="item.status !== 1" class="badge">已停用</text>
          </view>
          <text class="meta">服务：{{ item.services.length ? item.services.join('、') : '未关联' }}</text>
          <text class="meta">账号：{{ item.accountUsername || '未开通' }}</text>
        </view>
        <view class="ops">
          <view class="op" @click="openShifts(item)">排班</view>
          <view class="op" @click="openEdit(item)">编辑</view>
          <view class="op" :class="{ danger: item.status === 1 }" @click="toggle(item)">
            {{ item.status === 1 ? '停用' : '启用' }}
          </view>
        </view>
      </view>
    </view>

    <view class="fab" @click="openCreate">＋ 新增技师</view>

    <view v-if="editorVisible" class="mask" @click.self="editorVisible = false">
      <view class="editor">
        <text class="editor-title">{{ editingId ? '编辑技师' : '新增技师' }}</text>
        <view class="field">
          <text class="label">姓名</text>
          <input v-model="form.name" class="input" placeholder="如：林晓" placeholder-class="ph" />
        </view>
        <view class="field">
          <text class="label">头像</text>
          <input v-model="form.avatar" class="input" placeholder="https://… 图片地址" placeholder-class="ph" />
        </view>
        <view class="field">
          <text class="label">职称</text>
          <input v-model="form.title" class="input" placeholder="如：高级美甲师" placeholder-class="ph" />
        </view>
        <view class="field">
          <text class="label">简介</text>
          <input v-model="form.intro" class="input" placeholder="擅长方向（选填）" placeholder-class="ph" />
        </view>
        <template v-if="!hasAccount">
          <text class="section">开通登录账号（选填）</text>
          <view class="field">
            <text class="label">账号</text>
            <input v-model="form.accountUsername" class="input" type="number" maxlength="11" placeholder="手机号" placeholder-class="ph" />
          </view>
          <view class="field">
            <text class="label">密码</text>
            <input v-model="form.accountPassword" class="input" password placeholder="至少 6 位" placeholder-class="ph" />
          </view>
        </template>
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

.card.off .avatar,
.card.off .name {
  opacity: 0.5;
}

.avatar {
  flex: 0 0 96rpx;
  width: 96rpx;
  height: 96rpx;
  border-radius: 50%;
  background: #f4ece6;
}

.avatar.placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 34rpx;
  font-weight: 600;
  color: #c9707c;
  background: #fbf0ee;
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

.title {
  margin-left: 10rpx;
  font-size: 22rpx;
  color: #b09a8e;
}

.badge {
  margin-left: 10rpx;
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

.ops {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 12rpx;
}

.op {
  font-size: 24rpx;
  color: #8c827c;
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
  max-height: 85vh;
  overflow-y: auto;
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

.section {
  display: block;
  margin-top: 24rpx;
  font-size: 24rpx;
  color: #b09a8e;
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
