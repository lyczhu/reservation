// uni-app 应用入口：注册 Pinia，由框架调用 createApp 创建实例
import { createSSRApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
// 提前注册登录失效刷新逻辑，保证任意页面首次请求即可回到登录页
import './utils/session'

export function createApp() {
  const app = createSSRApp(App)
  app.use(createPinia())
  return { app }
}