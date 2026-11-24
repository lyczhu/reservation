<script setup lang="ts">
import { onLaunch } from '@dcloudio/uni-app'
import { isStaffLogin } from './utils/session'

onLaunch(() => {
  if (!isStaffLogin()) {
    uni.reLaunch({ url: '/pages/login/index' })
  }
})
</script>

<style>
page {
  background-color: #faf6f2;
  color: #3b3330;
  font-size: 28rpx;
  font-family: -apple-system, 'PingFang SC', 'Helvetica Neue', sans-serif;
}
</style>
