import { defineConfig } from 'vitepress'

// Sinomed 文档站 —— 中医医馆管理系统
export default defineConfig({
  lang: 'zh-CN',
  base: '/sinomed/',
  title: 'Sinomed',
  description:
    'Sinomed 中医医馆管理系统文档：顾客建档、中医诊疗记录、卡项、复盘回访、考勤管理与部署指南。',
  head: [
    ['link', { rel: 'icon', type: 'image/svg+xml', href: '/sinomed/favicon.svg' }],
    [
      'meta',
      { name: 'theme-color', media: '(prefers-color-scheme: light)', content: '#C7A674' },
    ],
    [
      'meta',
      { name: 'theme-color', media: '(prefers-color-scheme: dark)', content: '#1e1d1b' },
    ],
    ['meta', { property: 'og:title', content: 'Sinomed · 中医医馆管理系统' }],
    ['meta', { property: 'og:description', content: 'Sinomed 中医医馆管理系统文档站' }],
    ['meta', { property: 'og:type', content: 'website' }]
  ],
  lastUpdated: true,
  themeConfig: {
    logo: '/logo.svg',
    siteTitle: 'Sinomed',
    nav: [
      { text: '开始', link: '/guide/getting-started', activeMatch: '/guide/' },
      { text: '服务端', link: '/server/architecture', activeMatch: '/server/' },
      { text: '管理端', link: '/web', activeMatch: '/web' },
      { text: '小程序端', link: '/app', activeMatch: '/app' },
      { text: '广告展示端', link: '/tablet', activeMatch: '/tablet' },
      { text: '设计', link: '/design/desktop', activeMatch: '/design/' },
      { text: '推广材料', link: '/marketing/', activeMatch: '/marketing/' },
      { text: '调研', link: '/research/projects', activeMatch: '/research/' }
    ],
    sidebar: {
      '/guide/': [
        {
          text: '开始',
          items: [{ text: '快速上手', link: '/guide/getting-started' }]
        },
        {
          text: '开发',
          items: [{ text: '开发指南', link: '/guide/development' }]
        }
      ],
      '/server/': [
        {
          text: '服务端',
          items: [
            { text: '架构总览', link: '/server/architecture' },
            { text: 'REST 接口清单', link: '/server/api' },
            { text: '数据模型', link: '/server/data-model' },
            { text: '配置与部署', link: '/server/deploy' }
          ]
        }
      ],
      '/web': [
        {
          text: '管理端',
          items: [{ text: '管理端结构与页面', link: '/web' }]
        }
      ],
      '/app': [
        {
          text: '小程序端',
          items: [
            { text: '小程序端结构', link: '/app' },
            { text: '换马甲构建指南', link: '/app-white-label' }
          ]
        }
      ],
      '/marketing/': [
        {
          text: '推广材料',
          items: [
            { text: '使用说明', link: '/marketing/' },
            { text: '开业介绍', link: '/marketing/article-opening' },
            { text: '中医科普软广', link: '/marketing/article-science' },
            { text: '活动促销', link: '/marketing/article-promo' },
            { text: '朋友圈短文案', link: '/marketing/moments' }
          ]
        }
      ],
      '/tablet': [
        {
          text: '广告展示端',
          items: [{ text: '广告展示端（平板 Kiosk）', link: '/tablet' }]
        }
      ],
      '/design/': [
        {
          text: '设计',
          items: [
            { text: '桌面版设计（Tauri 2 薄壳）', link: '/design/desktop' },
            { text: '平板展示设计（Kiosk）', link: '/design/tablet' },
            { text: '顾客选服务设计（Kiosk）', link: '/design/kiosk' },
            { text: '小程序自助约期设计', link: '/design/app-booking' },
            { text: '美容业态扩展核对', link: '/design/salon-extension' }
          ]
        }
      ],
      '/research/': [
        {
          text: '调研',
          items: [
            { text: '开源项目盘点', link: '/research/projects' },
            { text: '功能点清单', link: '/research/features' },
            { text: '图标库与对照表', link: '/research/icons' }
          ]
        }
      ]
    },
    socialLinks: [{ icon: 'github', link: 'https://github.com/cuihairu/sinomed' }],
    footer: {
      message: '基于 <a href="https://opensource.org/licenses/Apache-2.0" target="_blank" rel="noopener">Apache-2.0</a> 许可发布（见仓库 <a href="https://github.com/cuihairu/sinomed/blob/main/LICENSE" target="_blank" rel="noopener">LICENSE</a>）',
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
})
