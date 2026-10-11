import Taro from '@tarojs/taro'

/**
 * 四主题（与 docs/design/mockups/tokens.css 同源）：H5 端运行时可切换，
 * data-theme 挂 documentElement 走 tokens.css 的 [data-theme] 变量组；
 * 小程序端 WXSS 无运行时变量切换能力，固定石墨兜底（见 tokens.css page 选择器）。
 */
export const THEMES = [
  { key: 'graphite', label: '石墨 × 古金', color: '#353a37' },
  { key: 'pine', label: '松烟绿', color: '#2f6d4f' },
  { key: 'cinnabar', label: '朱砂', color: '#9e4a3d' },
  { key: 'indigo', label: '黛蓝', color: '#3f5b76' },
] as const

export type ThemeKey = (typeof THEMES)[number]['key']

const STORAGE_KEY = 'sinomed_theme'

export function isThemeKey(v: unknown): v is ThemeKey {
  return typeof v === 'string' && THEMES.some((t) => t.key === v)
}

export function readTheme(): ThemeKey {
  try {
    const saved = Taro.getStorageSync(STORAGE_KEY)
    return isThemeKey(saved) ? saved : 'graphite'
  } catch {
    return 'graphite'
  }
}

/** 应用主题：先存本机；H5 端同步挂 documentElement data-theme（整页变量换色） */
export function applyTheme(key: ThemeKey) {
  try {
    Taro.setStorageSync(STORAGE_KEY, key)
  } catch {
    // 存储失败不阻断切换（H5 当次仍生效）
  }
  if (process.env.TARO_ENV === 'h5') {
    const root = document.documentElement
    if (key === 'graphite') {
      delete root.dataset.theme
    } else {
      root.dataset.theme = key
    }
  }
}

/** 启动回放：把本机选择落回 documentElement（app.ts 引入时执行一次） */
export function restoreTheme() {
  if (process.env.TARO_ENV === 'h5') {
    applyTheme(readTheme())
  }
}
