import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App'
import { registerServiceWorker } from './net/precache'
import './tokens.css'
import './app.css'

// 主题切换入口：?theme=pine|cinnabar|indigo|graphite（缺省石墨，即不设 data-theme）
const params = new URLSearchParams(window.location.search)
const theme = params.get('theme')
if (theme === 'pine' || theme === 'cinnabar' || theme === 'indigo') {
  document.documentElement.dataset.theme = theme
} else if (theme === 'graphite') {
  delete document.documentElement.dataset.theme
}

registerServiceWorker()

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>
)
