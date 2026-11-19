import { createApp } from 'vue'
import App from './App.vue'
import './style.css'
import { i18n } from './i18n'
import './theme'

export function createDashboardApp() {
  return createApp(App).use(i18n)
}

const mountTarget = document.getElementById('app')
if (mountTarget) {
  createDashboardApp().mount(mountTarget)
}
