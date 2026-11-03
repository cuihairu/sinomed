import { getBrand } from './brand'
import path from 'path'

// 白标品牌参数：BRAND_* 环境变量 > 仓库默认值（见 ./brand.ts）。
// 这里注入两处——
//  1. env → DefinePlugin：业务代码与 app.config.ts 里的 process.env.TARO_APP_* 编译期替换；
//  2. h5.htmlPluginOption：H5 <title> / favicon / meta。
const brand = getBrand()
// app.config.ts 由 CLI 在 webpack 之外提前求值，DefinePlugin 管不到；
// 这里把真实值写进 process.env，config 求值时才能读到品牌字段。
process.env.TARO_APP_BRAND_NAME = brand.name
process.env.TARO_APP_BRAND_THEME = brand.themeColor
process.env.TARO_APP_API_BASE = brand.apiBase
process.env.TARO_APP_BRAND_ICON = brand.iconData || ''
// 白标自检开关（工程验收用）：构建时 TARO_APP_SHOW_DIAG=1 才在「我的」页显示白标参数面板；
// 顾客出包不带此参数，面板不参与打包（改参数不改代码口径见 docs/app-white-label.md）
process.env.TARO_APP_SHOW_DIAG = process.env.TARO_APP_SHOW_DIAG === '1' ? '1' : ''
const defaultIcon = path.resolve(__dirname, '../brand-assets/logo.png')

const config = {
  projectName: 'sinomed-app',
  date: '2024-5-27',
  designWidth: 375,
  deviceRatio: {
    640: 2.34 / 2,
    750: 1,
    828: 1.81 / 2,
    375: 2 / 1
  },
  sourceRoot: 'src',
  outputRoot: 'dist',
  plugins: ['@tarojs/plugin-html'],
  env: {
    TARO_APP_BRAND_NAME: JSON.stringify(brand.name),
    TARO_APP_BRAND_THEME: JSON.stringify(brand.themeColor),
    TARO_APP_API_BASE: JSON.stringify(brand.apiBase),
    TARO_APP_BRAND_ICON: JSON.stringify(brand.iconData || ''),
    TARO_APP_SHOW_DIAG: JSON.stringify(process.env.TARO_APP_SHOW_DIAG === '1' ? '1' : '')
  },
  defineConstants: {
  },
  copy: {
    patterns: [
    ],
    options: {
    }
  },
  framework: 'react',
  compiler: {
    type: 'webpack5',
    prebundle: { enable: false }
  },
  mini: {
    postcss: {
      pxtransform: {
        enable: true,
        config: {
          selectorBlackList: ['nut-']
        }
      },
      url: {
        enable: true,
        config: {
          limit: 1024 // 设定转换尺寸上限
        }
      },
      cssModules: {
        enable: false, // 默认为 false，如需使用 css modules 功能，则设为 true
        config: {
          namingPattern: 'module', // 转换模式，取值为 global/module
          generateScopedName: '[name]__[local]___[hash:base64:5]'
        }
      }
    }
  },
  h5: {
    publicPath: '/',
    staticDirectory: 'static',
    webpackChain(chain) {
      chain.resolve.alias.set(
        'swiper/swiper-bundle.esm.js',
        path.resolve(__dirname, '../scripts/swiper-shim/index.mjs')
      )
    },
    htmlPluginOption: {
      title: brand.name,
      favicon: brand.iconFile || defaultIcon,
      meta: {
        description: brand.description,
        'theme-color': brand.themeColor
      }
    },
    // esnextModules: ['nutui-react'],
    postcss: {
      pxtransform: {
        enable: true,
        config: {
          selectorBlackList: ['nut-']
        }
      },
      autoprefixer: {
        enable: true,
        config: {
        }
      },
      cssModules: {
        enable: false, // 默认为 false，如需使用 css modules 功能，则设为 true
        config: {
          namingPattern: 'module', // 转换模式，取值为 global/module
          generateScopedName: '[name]__[local]___[hash:base64:5]'
        }
      }
    }
  }
}

module.exports = function (merge) {
  if (process.env.NODE_ENV === 'development') {
    return merge({}, config, require('./dev'))
  }
  return merge({}, config, require('./prod'))
}
