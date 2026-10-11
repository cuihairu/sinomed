import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App'
import './tokens.css'
import './app.css'

// 主题切换入口：?theme=pine|cinnabar|indigo|graphite（缺省保持 index.html 的 pine）
const params = new URLSearchParams(window.location.search)
const theme = params.get('theme')
if (theme === 'graphite') {
  delete document.documentElement.dataset.theme
} else if (theme === 'pine' || theme === 'cinnabar' || theme === 'indigo') {
  document.documentElement.dataset.theme = theme
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>
)
