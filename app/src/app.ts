import { useEffect } from 'react'
import { useDidShow, useDidHide } from '@tarojs/taro'
// 全局样式
import './app.css'
import { restoreTheme } from './services/theme'

function App(props) {
  // 可以使用所有的 React Hooks
  useEffect(() => {})

  // 对应 onShow
  useDidShow(() => {})

  // 对应 onHide
  useDidHide(() => {})

  return props.children
}

// 白标主题色：H5 侧注入 CSS 变量供全局样式取用；
// 小程序端组件直接读 BRAND.themeColor 内联样式（无 document 概念）。
if (process.env.TARO_ENV === 'h5') {
  document.documentElement.style.setProperty(
    '--brand-color',
    process.env.TARO_APP_BRAND_THEME
  )
}

// 四主题回放：H5 端把本机选择落回 documentElement（tokens.css [data-theme] 变量组）
restoreTheme()

export default App
