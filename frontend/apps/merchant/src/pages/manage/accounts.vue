<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import {
  createAccount,
  listAccounts,
  listManagedTechnicians,
  resetAccountPassword,
  unbindAccountWx,
  updateAccountStatus,
  type ManagedTechnician,
  type StaffAccount
} from '../../api/manage'
import { timeText } from '../../utils/format'
import { handleAuthError, requireStaffLogin } from '../../utils/session'

const items = ref<StaffAccount[]>([])
const technicians = ref<ManagedTechnician[]>([])
const loading = ref(true)
const error = ref('')
const submitting = ref(false)

const editorVisible = ref(false)
const form = reactive({ username: '', password: '', technicianId: 0 })
const passwordVisible = ref(false)
const passwordTarget = ref<StaffAccount | null>(null)
const newPassword = ref('')

const bindableTechnicians = computed(() => {
  const bound = new Set(items.value.map((item) => item.technicianId).filter((id): id is number => id !== null))
  return technicians.value.filter((tech) => tech.status === 1 && !bound.has(tech.id))
})
const technicianNames = computed(() => bindableTechnicians.value.map((tech) => tech.name))
const technicianIndex = computed(() => bindableTechnicians.value.findIndex((tech) => tech.id === form.technicianId))

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
    const [accounts, techs] = await Promise.all([listAccounts(), listManagedTechnicians()])
    items.value = accounts
    technicians.value = techs
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
  form.username = ''
  form.password = ''
  form.technicianId = bindableTechnicians.value[0]?.id || 0
  editorVisible.value = true
}

function chooseTechnician(event: { detail: { value: number | string } }) {
  const tech = bindableTechnicians.value[Number(event.detail.value)]
  if (tech) {
    form.technicianId = tech.id
  }
}

async function save() {
  if (submitting.value) {
    return
  }
  if (!/^1[3-9]\d{9}$/.test(form.username.trim())) {
    uni.showToast({ title: '登录名需为手机号格式', icon: 'none' })
    return
  }
  if (form.password.length < 8) {
    uni.showToast({ title: '密码至少 8 位', icon: 'none' })
    return
  }
  if (!form.technicianId) {
    uni.showToast({ title: '请选择绑定技师', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    await createAccount({ username: form.username.trim(), password: form.password, technicianId: form.technicianId })
    editorVisible.value = false
    uni.showToast({ title: '账号已创建', icon: 'none' })
    load()
  } catch (e) {
    uni.showToast({ title: e instanceof Error ? e.message : '创建失败', icon: 'none' })
  } finally {
    submitting.value = false
  }
}

function openReset(item: StaffAccount) {
  passwordTarget.value = item
  newPassword.value = ''
  passwordVisible.value = true
}

async function resetPassword() {
  if (!passwordTarget.value || submitting.value) {
    return
  }
  if (newPassword.value.length < 8) {
    uni.showToast({ title: '密码至少 8 位', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    await resetAccountPassword(passwordTarget.value.id, newPassword.value)
    passwordVisible.value = false
    uni.showToast({ title: '密码已重置', icon: 'none' })
  } catch (e) {
    uni.showToast({ title: e instanceof Error ? e.message : '重置失败', icon: 'none' })
  } finally {
    submitting.value = false
  }
}

function toggle(item: StaffAccount) {
  uni.showModal({
    title: item.status === 1 ? '停用账号' : '启用账号',
    content: item.status === 1 ? `停用后「${item.username}」将无法登录商家端。` : `启用后「${item.username}」可重新登录。`,
    confirmColor: '#c9707c',
    success: async (res) => {
      if (!res.confirm) {
        return
      }
      try {
        await updateAccountStatus(item.id, item.status === 1 ? 2 : 1)
        item.status = item.status === 1 ? 2 : 1
        item.statusText = item.status === 1 ? '启用' : '停用'
      } catch (e) {
        uni.showToast({ title: e instanceof Error ? e.message : '操作失败', icon: 'none' })
      }
    }
  })
}

function unbind(item: StaffAccount) {
  uni.showModal({
    title: '解绑微信',
    content: `解绑后「${item.username}」需重新绑定才能微信快捷登录。`,
    confirmColor: '#c9707c',
    success: async (res) => {
      if (!res.confirm) {
        return
      }
      try {
        await unbindAccountWx(item.id)
        item.wxBound = false
        uni.showToast({ title: '已解绑', icon: 'none' })
      } catch (e) {
        uni.showToast({ title: e instanceof Error ? e.message : '解绑失败', icon: 'none' })
      }
    }
  })
}
</script>

<template>
  <view class="page">
    <view v-if="loading" class="state">加载中…</view>
    <view v-else-if="error" class="state error">{{ error }}</view>

    <view v-else class="list">
      <view v-for="item in items" :key="item.id" class="card" :class="{ off: item.status !== 1 }">
        <view class="head">
          <view class="head-left">
            <text class="username">{{ item.username }}</text>
            <text class="role">{{ item.roleText }}</text>
            <text v-if="item.status !== 1" class="badge">已停用</text>
          </view>
          <text class="wx">{{ item.wxBound ? '已绑微信' : '未绑微信' }}</text>
        </view>
        <text class="meta">
          {{ item.technicianName ? `绑定技师：${item.technicianName}` : '店长账号' }}
          <text v-if="item.lastLoginAt"> · 最近登录 {{ timeText(item.lastLoginAt) }}</text>
        </text>
        <view class="ops">
          <view class="op" @click="openReset(item)">重置密码</view>
          <view v-if="item.wxBound" class="op" @click="unbind(item)">解绑微信</view>
          <view v-if="item.role !== 1" class="op" :class="{ danger: item.status === 1 }" @click="toggle(item)">
            {{ item.status === 1 ? '停用' : '启用' }}
          </view>
        </view>
      </view>
    </view>

    <view class="fab" @click="openCreate">＋ 新增技师账号</view>

    <view v-if="editorVisible" class="mask" @click.self="editorVisible = false">
      <view class="editor">
        <text class="editor-title">新增技师账号</text>
        <view class="field">
          <text class="label">登录名</text>
          <input v-model="form.username" class="input" type="number" maxlength="11" placeholder="手机号" placeholder-class="ph" />
        </view>
        <view class="field">
          <text class="label">初始密码</text>
          <input v-model="form.password" class="input" password placeholder="至少 8 位" placeholder-class="ph" />
        </view>
        <view class="field">
          <text class="label">绑定技师</text>
          <picker mode="selector" :range="technicianNames" :value="technicianIndex" @change="chooseTechnician">
            <view class="picker-value">{{ bindableTechnicians[technicianIndex]?.name || '暂无可绑定技师' }}›</view>
          </picker>
        </view>
        <text v-if="!bindableTechnicians.length" class="hint">所有启用技师均已绑定账号，请先在技师管理新增</text>
        <view class="editor-actions">
          <view class="btn ghost" @click="editorVisible = false">取消</view>
          <view class="btn primary" :class="{ disabled: submitting || !bindableTechnicians.length }" @click="save">
            {{ submitting ? '创建中…' : '创建' }}
          </view>
        </view>
      </view>
    </view>

    <view v-if="passwordVisible" class="mask" @click.self="passwordVisible = false">
      <view class="editor">
        <text class="editor-title">重置密码 · {{ passwordTarget?.username }}</text>
        <view class="field">
          <text class="label">新密码</text>
          <input v-model="newPassword" class="input" password placeholder="至少 8 位" placeholder-class="ph" />
        </view>
        <view class="editor-actions">
          <view class="btn ghost" @click="passwordVisible = false">取消</view>
          <view class="btn primary" :class="{ disabled: submitting }" @click="resetPassword">
            {{ submitting ? '重置中…' : '确认重置' }}
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
  margin-bottom: 20rpx;
  padding: 24rpx;
  border-radius: 22rpx;
  background: #ffffff;
  box-shadow: 0 8rpx 20rpx rgba(104, 74, 68, 0.04);
}

.card.off .username {
  opacity: 0.5;
}

.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.head-left {
  display: flex;
  align-items: center;
}

.username {
  font-size: 30rpx;
  font-weight: 600;
  color: #3b3330;
}

.role {
  margin-left: 12rpx;
  padding: 2rpx 12rpx;
  border-radius: 8rpx;
  font-size: 20rpx;
  color: #c9707c;
  background: #fbf0ee;
}

.badge {
  margin-left: 10rpx;
  padding: 2rpx 12rpx;
  border-radius: 8rpx;
  font-size: 20rpx;
  color: #a89589;
  background: #f1eae3;
}

.wx {
  font-size: 22rpx;
  color: #b09a8e;
}

.meta {
  display: block;
  margin-top: 10rpx;
  font-size: 22rpx;
  color: #b09a8e;
}

.ops {
  display: flex;
  margin-top: 16rpx;
  padding-top: 14rpx;
  border-top: 1rpx solid #f5eee8;
}

.op {
  margin-right: 36rpx;
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
  width: 140rpx;
  font-size: 26rpx;
  color: #8c827c;
}

.input {
  flex: 1;
  font-size: 28rpx;
  color: #3b3330;
}

.picker-value {
  font-size: 28rpx;
  color: #3b3330;
}

.hint {
  display: block;
  margin-top: 16rpx;
  font-size: 22rpx;
  color: #c9707c;
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
