export default defineAppConfig({
  pages: [
    'pages/index/index',
    'pages/booking/index',
    'pages/card/index',
    'pages/me/index'
  ],
  window: {
    backgroundTextStyle: 'light',
    navigationBarBackgroundColor: process.env.TARO_APP_BRAND_THEME,
    navigationBarTitleText: process.env.TARO_APP_BRAND_NAME,
    navigationBarTextStyle: 'black'
  },
  // 底部四标签：首页/预约/卡项/我的（对齐原型 mobile-home tabbar；
  // 我的承载身份与白标自检，四者均无图标走纯文字标签）
  tabBar: {
    color: '#696c68',
    selectedColor: process.env.TARO_APP_BRAND_THEME,
    backgroundColor: '#ffffff',
    borderStyle: 'black',
    list: [
      { pagePath: 'pages/index/index', text: '首页' },
      { pagePath: 'pages/booking/index', text: '预约' },
      { pagePath: 'pages/card/index', text: '卡项' },
      { pagePath: 'pages/me/index', text: '我的' }
    ]
  }
})
