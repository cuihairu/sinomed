import { defineConfig } from 'vitepress'

// Sinomed 文档站 —— 英文为主站点（根目录），中文挂 /zh/（VitePress 官方 locales 路由，无自制切换器）。
const nav = [
  { text: 'Guide', link: '/guide/getting-started', activeMatch: '/guide/' },
  { text: 'Server', link: '/server/architecture', activeMatch: '/server/' },
  { text: 'Admin console', link: '/web', activeMatch: '/web' },
  { text: 'Mini program', link: '/app', activeMatch: '/app' },
  { text: 'Waiting display', link: '/tablet', activeMatch: '/tablet' },
  { text: 'Design', link: '/design/desktop', activeMatch: '/design/' },
  { text: 'Marketing', link: '/marketing/', activeMatch: '/marketing/' },
  { text: 'Research', link: '/research/projects', activeMatch: '/research/' }
]

const guideSidebar = [
  { text: 'Getting started', items: [{ text: 'Quick start', link: '/guide/getting-started' }] },
  { text: 'Development', items: [{ text: 'Development guide', link: '/guide/development' }] }
]
const serverSidebar = [
  {
    text: 'Server',
    items: [
      { text: 'Architecture overview', link: '/server/architecture' },
      { text: 'REST API reference', link: '/server/api' },
      { text: 'Data model', link: '/server/data-model' },
      { text: 'Configuration & deployment', link: '/server/deploy' }
    ]
  }
]
const webSidebar = [
  { text: 'Admin console', items: [{ text: 'Structure & pages', link: '/web' }] }
]
const appSidebar = [
  {
    text: 'Mini program',
    items: [
      { text: 'Mini program structure', link: '/app' },
      { text: 'White-label build guide', link: '/app-white-label' }
    ]
  }
]
const marketingSidebar = [
  {
    text: 'Marketing materials',
    items: [
      { text: 'Usage guide', link: '/marketing/' },
      { text: 'Grand opening intro', link: '/marketing/article-opening' },
      { text: 'TCM soft-sell article', link: '/marketing/article-science' },
      { text: 'Promotion article', link: '/marketing/article-promo' },
      { text: 'Moments short posts', link: '/marketing/moments' }
    ]
  }
]
const tabletSidebar = [
  { text: 'Waiting display', items: [{ text: 'Waiting display (tablet kiosk)', link: '/tablet' }] }
]
const designSidebar = [
  {
    text: 'Design',
    items: [
      { text: 'Desktop design (Tauri 2)', link: '/design/desktop' },
      { text: 'Waiting display design (Kiosk)', link: '/design/tablet' },
      { text: 'Customer kiosk design', link: '/design/kiosk' },
      { text: 'Mini-program booking design', link: '/design/app-booking' },
      { text: 'Salon extension review', link: '/design/salon-extension' }
    ]
  }
]
const researchSidebar = [
  {
    text: 'Research',
    items: [
      { text: 'Open-source projects', link: '/research/projects' },
      { text: 'Feature checklist', link: '/research/features' },
      { text: 'Icon library & mapping', link: '/research/icons' }
    ]
  }
]

export default defineConfig({
  lang: 'en-US',
  base: '/sinomed/',
  title: 'Sinomed',
  description:
    'Sinomed TCM clinic management system docs: patient records, treatment records, service cards, retrospectives & follow-ups, and deployment guides.',
  head: [
    ['link', { rel: 'icon', type: 'image/svg+xml', href: '/sinomed/favicon.svg' }],
    [
      'meta',
      { name: 'theme-color', media: '(prefers-color-scheme: light)', content: '#C7A674' }
    ],
    [
      'meta',
      { name: 'theme-color', media: '(prefers-color-scheme: dark)', content: '#1e1d1b' }
    ],
    ['meta', { property: 'og:title', content: 'Sinomed · TCM Clinic Management System' }],
    [
      'meta',
      {
        property: 'og:description',
        content: 'Sinomed TCM clinic management system documentation site'
      }
    ],
    ['meta', { property: 'og:type', content: 'website' }]
  ],
  lastUpdated: true,
  locales: {
    root: { label: 'English', lang: 'en-US' },
    zh: { label: '简体中文', lang: 'zh-CN', link: '/zh/' }
  },
  themeConfig: {
    logo: '/logo.svg',
    siteTitle: 'Sinomed',
    socialLinks: [{ icon: 'github', link: 'https://github.com/cuihairu/sinomed' }],
    outline: { level: [2, 3] },
    locales: {
      root: {
        selectText: 'Languages',
        nav,
        sidebar: {
          '/guide/': guideSidebar,
          '/server/': serverSidebar,
          '/web': webSidebar,
          '/app': appSidebar,
          '/marketing/': marketingSidebar,
          '/tablet': tabletSidebar,
          '/design/': designSidebar,
          '/research/': researchSidebar
        },
        footer: {
          message:
            'Released under the <a href="https://opensource.org/licenses/Apache-2.0" target="_blank" rel="noopener">Apache-2.0</a> license (see the repository <a href="https://github.com/cuihairu/sinomed/blob/main/LICENSE" target="_blank" rel="noopener">LICENSE</a>)',
          copyright: 'Copyright © 2026 cuihairu'
        },
        outline: { level: [2, 3], label: 'On this page' },
        search: { provider: 'local' },
        docFooter: { prev: 'Previous', next: 'Next' },
        darkModeSwitchLabel: 'Theme',
        lightModeSwitchTitle: 'Switch to light theme',
        darkModeSwitchTitle: 'Switch to dark theme',
        sidebarMenuLabel: 'Menu',
        returnToTopLabel: 'Back to top',
        lastUpdated: { text: 'Last updated on' }
      },
      zh: {
        label: '简体中文',
        selectText: '语言',
        nav: [
          { text: '开始', link: '/zh/guide/getting-started', activeMatch: '/zh/guide/' },
          { text: '服务端', link: '/zh/server/architecture', activeMatch: '/zh/server/' },
          { text: '管理端', link: '/zh/web', activeMatch: '/zh/web' },
          { text: '小程序端', link: '/zh/app', activeMatch: '/zh/app' },
          { text: '广告展示端', link: '/zh/tablet', activeMatch: '/zh/tablet' },
          { text: '设计', link: '/zh/design/desktop', activeMatch: '/zh/design/' },
          { text: '推广材料', link: '/zh/marketing/', activeMatch: '/zh/marketing/' },
          { text: '调研', link: '/zh/research/projects', activeMatch: '/zh/research/' }
        ],
        sidebar: {
          '/zh/guide/': [
            {
              text: '开始',
              items: [{ text: '快速上手', link: '/zh/guide/getting-started' }]
            },
            {
              text: '开发',
              items: [{ text: '开发指南', link: '/zh/guide/development' }]
            }
          ],
          '/zh/server/': [
            {
              text: '服务端',
              items: [
                { text: '架构总览', link: '/zh/server/architecture' },
                { text: 'REST 接口清单', link: '/zh/server/api' },
                { text: '数据模型', link: '/zh/server/data-model' },
                { text: '配置与部署', link: '/zh/server/deploy' }
              ]
            }
          ],
          '/zh/web': [
            {
              text: '管理端',
              items: [{ text: '管理端结构与页面', link: '/zh/web' }]
            }
          ],
          '/zh/app': [
            {
              text: '小程序端',
              items: [
                { text: '小程序端结构', link: '/zh/app' },
                { text: '换马甲构建指南', link: '/zh/app-white-label' }
              ]
            }
          ],
          '/zh/marketing/': [
            {
              text: '推广材料',
              items: [
                { text: '使用说明', link: '/zh/marketing/' },
                { text: '开业介绍', link: '/zh/marketing/article-opening' },
                { text: '中医科普软广', link: '/zh/marketing/article-science' },
                { text: '活动促销', link: '/zh/marketing/article-promo' },
                { text: '朋友圈短文案', link: '/zh/marketing/moments' }
              ]
            }
          ],
          '/zh/tablet': [
            {
              text: '广告展示端',
              items: [{ text: '广告展示端（平板 Kiosk）', link: '/zh/tablet' }]
            }
          ],
          '/zh/design/': [
            {
              text: '设计',
              items: [
                { text: '桌面版设计（Tauri 2 薄壳）', link: '/zh/design/desktop' },
                { text: '平板展示设计（Kiosk）', link: '/zh/design/tablet' },
                { text: '顾客选服务设计（Kiosk）', link: '/zh/design/kiosk' },
                { text: '小程序自助约期设计', link: '/zh/design/app-booking' },
                { text: '美容业态扩展核对', link: '/zh/design/salon-extension' }
              ]
            }
          ],
          '/zh/research/': [
            {
              text: '调研',
              items: [
                { text: '开源项目盘点', link: '/zh/research/projects' },
                { text: '功能点清单', link: '/zh/research/features' },
                { text: '图标库与对照表', link: '/zh/research/icons' }
              ]
            }
          ]
        },
        footer: {
          message:
            '基于 <a href="https://opensource.org/licenses/Apache-2.0" target="_blank" rel="noopener">Apache-2.0</a> 许可发布（见仓库 <a href="https://github.com/cuihairu/sinomed/blob/main/LICENSE" target="_blank" rel="noopener">LICENSE</a>）',
          copyright: 'Copyright © 2026 cuihairu'
        },
        outline: { level: [2, 3], label: '本页目录' },
        search: {
          provider: 'local',
          options: {
            translations: {
              button: { buttonText: '搜索文档', buttonAriaLabel: '搜索文档' },
              modal: {
                noResultsText: '没有找到结果',
                resetButtonTitle: '清除查询条件',
                footer: { selectText: '选择', navigateText: '切换', closeText: '关闭' }
              }
            }
          }
        },
        docFooter: { prev: '上一篇', next: '下一篇' },
        darkModeSwitchLabel: '主题',
        lightModeSwitchTitle: '切换到亮色',
        darkModeSwitchTitle: '切换到暗色',
        sidebarMenuLabel: '菜单',
        returnToTopLabel: '回到顶部',
        lastUpdated: { text: '最后更新于' }
      }
    }
  }
})
