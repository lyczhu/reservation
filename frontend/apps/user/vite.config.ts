import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

export default defineConfig({
  plugins: [uni()],
  // workspace 源码包不预构建，避免修改 @reservation/shared 后 dev 产物缓存陈旧
  optimizeDeps: {
    exclude: ['@reservation/shared']
  }
})