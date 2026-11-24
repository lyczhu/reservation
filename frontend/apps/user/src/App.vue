<script setup lang="ts">
import { onLaunch } from '@dcloudio/uni-app'

// uni-app 要求 App.vue 含非空 script，全局初始化逻辑后续在此接入
onLaunch(() => {})
</script>

<style>
page {
  background-color: #f6f6f6;
}

/* #ifdef H5 */
/* 桌面浏览器宽度超过手机画布时限制应用宽度并居中，避免 rpx 布局被拉满整屏；rpx 基准由 pages.json 的 rpxCalc 配置对齐到 480 */
@media (min-width: 481px) {
  html,
  body {
    background-color: #ece7e3;
  }

  uni-app {
    max-width: 480px;
    margin: 0 auto;
    box-shadow: 0 0 40px rgba(59, 51, 48, 0.12);
    /* transform 后 uni-app 成为内部 fixed 元素的包含块，导航栏、固定底栏、全屏遮罩会一并收敛到画布宽度 */
    transform: translate(0, 0);
  }
}
/* #endif */
</style>