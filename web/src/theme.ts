/**
 * 运行时主题 —— 与 docs/design/mockups/tokens.css 四套主色同源。
 * 切换两件事：① documentElement[data-theme] 让全部走 var(--brand) 系的自定义样式换色；
 * ② antd token（colorPrimary 等）经 ThemeProvider 调 useAntdConfigSetter 深合并进
 *    umi antd 插件自带的 ConfigProvider（初始值由 app.tsx 的 antd 运行时 modify 回放）；
 * ③ ProLayout 侧栏选中块等经 initialState.settings.token.sider 覆盖（外观设置页切换时 bump）。
 * 选择持久化 localStorage('sinomed-theme')，getInitialState 启动时回放。
 */
import type { ProLayoutProps, Settings as ProSettings } from '@ant-design/pro-components';

export type ThemeKey = 'graphite' | 'pine' | 'cinnabar' | 'indigo';

/**
 * ProSettings（PureSettings）没声明 token 字段，ProLayoutProps 才有；但运行时 settings
 * 确实携带 token（defaultSettings / themedSettings / 外观设置页都写入），ProLayout 也消费它。
 */
export type SettingsWithToken = Partial<ProSettings> & { token?: ProLayoutProps['token'] };

export const STORAGE_KEY = 'sinomed-theme';

/** tokens.css --faint 的回退值（仅当浏览器读不到 CSS 变量时兜底，与 tokens.css 同源）。 */
const FAINT_FALLBACK = '#696c68';

/**
 * 运行时读取 tokens.css 的 --faint，让 antd colorTextPlaceholder 与 CSS token 同源；
 * 若 getComputedStyle 取不到（如 SSR/未挂载），回退到与 tokens.css 一致的常量。
 * tokens.css 仍是色彩唯一真相源：改 --faint 后占位符自动跟随，无硬编码漂移。
 */
export function faintColor(): string {
  if (typeof document !== 'undefined') {
    const v = getComputedStyle(document.documentElement).getPropertyValue('--faint').trim();
    if (v) return v;
  }
  return FAINT_FALLBACK;
}

export interface ThemeDef {
  key: ThemeKey;
  /** 侧栏/卡片名 */
  label: string;
  desc: string;
  /** data-theme 属性值；默认主题为空（:root 即石墨×古金） */
  attr: '' | 'pine' | 'cinnabar' | 'indigo';
  brand: string;
  brandDeep: string;
  brandTint: string;
  /** 主题卡色板 */
  swatchBg: string;
}

export const THEMES: ThemeDef[] = [
  {
    key: 'graphite',
    label: '石墨 × 古金',
    desc: '默认 · 取自 logo',
    attr: '',
    brand: '#353a37',
    brandDeep: '#262a28',
    brandTint: '#eceeee',
    swatchBg: '#eceeee',
  },
  {
    key: 'pine',
    label: '松烟绿',
    desc: '清雅 · 草木调',
    attr: 'pine',
    brand: '#2f6d4f',
    brandDeep: '#234f39',
    brandTint: '#e7efe9',
    swatchBg: '#e7efe9',
  },
  {
    key: 'cinnabar',
    label: '朱砂',
    desc: '传统 · 印泥调',
    attr: 'cinnabar',
    brand: '#9e4a3d',
    brandDeep: '#7c382e',
    brandTint: '#f6eae7',
    swatchBg: '#f6eae7',
  },
  {
    key: 'indigo',
    label: '黛蓝',
    desc: '沉稳 · 靛青调',
    attr: 'indigo',
    brand: '#3f5b76',
    brandDeep: '#2f465c',
    brandTint: '#e8edf2',
    swatchBg: '#e8edf2',
  },
];

/** 品牌点缀全主题一致：金棕取自 logo #C7A674 */
export const AMBER = '#c7a674';

export function isThemeKey(v: string | null): v is ThemeKey {
  return !!v && THEMES.some((t) => t.key === v);
}

export function readTheme(): ThemeKey {
  const saved = localStorage.getItem(STORAGE_KEY);
  return isThemeKey(saved) ? saved : 'graphite';
}

export function themeDef(key: ThemeKey): ThemeDef {
  return THEMES.find((t) => t.key === key) || THEMES[0];
}

// ---------- 简易外置 store：applyTheme 后通知订阅者（ThemeProvider 据此重渲染） ----------

const listeners = new Set<() => void>();

export function subscribeTheme(fn: () => void) {
  listeners.add(fn);
  return () => listeners.delete(fn);
}

/** 应用主题到 DOM 与 localStorage，并广播变更（antd token 经 ThemeProvider 生效） */
export function applyTheme(key: ThemeKey) {
  const def = themeDef(key);
  if (def.attr) {
    document.documentElement.dataset.theme = def.attr;
  } else {
    delete document.documentElement.dataset.theme;
  }
  localStorage.setItem(STORAGE_KEY, key);
  listeners.forEach((fn) => fn());
}

/** 启动回放：只落数据集，不广播（getInitialState 里调用） */
export function restoreTheme() {
  const def = themeDef(readTheme());
  if (def.attr) {
    document.documentElement.dataset.theme = def.attr;
  }
}

/** antd 运行时 token 覆盖（内嵌 ConfigProvider） */
export function antdTokenOverrides(key: ThemeKey) {
  const def = themeDef(key);
  return {
    colorPrimary: def.brand,
    colorInfo: def.brand,
    colorLink: def.brand,
    // 占位符跟随 --faint:antd 默认 #bfbfbf 在白/宣纸底上不足 WCAG AA
    colorTextPlaceholder: faintColor(),
  };
}

/** ProLayout 侧栏 token 覆盖（经 initialState.settings.token.sider 下发） */
export function siderTokenOverrides(key: ThemeKey) {
  const def = themeDef(key);
  return {
    colorTextMenu: '#4b4f4d',
    colorBgMenuItemHover: '#f0f1ef',
    colorTextMenuHover: def.brandDeep,
    colorBgMenuItemSelected: def.brandTint,
    colorTextMenuSelected: def.brandDeep,
    colorTextMenuActive: def.brandDeep,
  };
}
