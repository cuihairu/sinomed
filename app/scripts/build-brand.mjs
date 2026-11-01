#!/usr/bin/env node
// 白标一键出包脚本：传商家参数 → 校验 → 注入环境变量 → taro 构建 → 恢复现场。
// 用法示例见 docs/app-white-label.md。
import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'
import { spawnSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'

const APP_ROOT = path.join(path.dirname(fileURLToPath(import.meta.url)), '..')
const HEX_COLOR = /^#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{6})$/
const APPID_RE = /^(?:wx[0-9a-f]{16}|touristappid)$/
const PROJECT_CONFIG = path.join(APP_ROOT, 'project.config.json')

function usage() {
  console.log(`白标一键出包：node scripts/build-brand.mjs [选项] [--target weapp|h5]

选项（全部可省略，省略时用仓库默认值出默认包）：
  --name    品牌名，如 和济堂
  --domain  后端域名，如 https://api.hejitang.example.com
  --theme   主题色，#RGB 或 #RRGGBB
  --appid   小程序 appid，wx 开头 16 位十六进制；留空用 touristappid
  --icon    商家图标文件（png/jpg 位图会同时注入页内展示；svg 仅 H5 favicon 用）
  --desc    H5 页面描述文案（仅 H5）
  --diag    白标自检面板开关（工程验收）：加此参数，「我的」页显示白标参数面板；不带则不面向顾客
  --target  构建目标，weapp（默认）或 h5`)
}

function parseArgs(argv) {
  const args = {}
  for (let i = 0; i < argv.length; i++) {
    const key = argv[i]
    if (key === '--help' || key === '-h') { usage(); process.exit(0) }
    if (!key.startsWith('--')) { console.error(`无法识别的参数：${key}`); process.exit(1) }
    const name = key.slice(2)
    if (name === 'target') { args.target = argv[++i]; continue }
    const value = argv[i + 1] && !argv[i + 1].startsWith('--') ? argv[++i] : ''
    args[name] = value
  }
  return args
}

function fail(message) {
  console.error(`✗ ${message}`)
  process.exit(1)
}

const args = parseArgs(process.argv.slice(2))
const target = args.target || 'weapp'
if (target !== 'weapp' && target !== 'h5') fail(`--target 只支持 weapp / h5，收到：${target}`)

// 参数校验：错的当场拦住，别等构建完才发现。
const name = args.name || ''
if (args.name !== undefined && !name.trim()) fail('--name 不能是空字符串')

const theme = args.theme || ''
if (theme && !HEX_COLOR.test(theme)) fail(`--theme 需为 #RGB 或 #RRGGBB，收到：${theme}`)

const domain = args.domain || ''
if (domain) {
  let parsed = null
  try { parsed = new URL(domain) } catch { /* 下面统一报错 */ }
  if (!parsed || (parsed.protocol !== 'https:' && parsed.host !== 'localhost' && parsed.host !== '127.0.0.1')) {
    fail('--domain 需为 https:// 开头的 URL（本地调试可用 http://localhost 或 http://127.0.0.1）')
  }
}

const appid = args.appid || ''
if (appid && !APPID_RE.test(appid)) fail(`--appid 需为 wx 开头 16 位十六进制或 touristappid，收到：${appid}`)

// icon 替换只用商家提供的文件，不做任何代餐：给 svg 时页内图标位为空，明确提示。
let iconFile = ''
let iconData = ''
if (args.icon) {
  const iconPath = path.resolve(args.icon)
  if (!fs.existsSync(iconPath)) fail(`--icon 文件不存在：${iconPath}`)
  const ext = path.extname(iconPath).toLowerCase()
  if (!['.png', '.jpg', '.jpeg', '.svg'].includes(ext)) fail(`--icon 支持 png/jpg/svg，收到：${ext}`)
  if (ext === '.svg') {
    iconFile = iconPath
    console.log('ℹ svg 图标仅注入 H5 favicon；小程序页内图标位需要 png/jpg，本次留空。')
  } else {
    iconFile = iconPath
    iconData = `data:image/${ext === '.png' ? 'png' : 'jpeg'};base64,${fs.readFileSync(iconPath).toString('base64')}`
  }
}

// appid 写进根 project.config.json，构建完恢复原样（仓库文件不落商家参数）。
let savedProjectConfig = null
function restoreProjectConfig() {
  if (savedProjectConfig === null) return
  fs.writeFileSync(PROJECT_CONFIG, savedProjectConfig)
  savedProjectConfig = null
  console.log('↩ 已恢复 project.config.json 原始 appid')
}
process.on('SIGINT', () => { restoreProjectConfig(); process.exit(130) })

if (target === 'weapp' && appid) {
  savedProjectConfig = fs.readFileSync(PROJECT_CONFIG, 'utf8')
  try {
    const configJson = JSON.parse(savedProjectConfig)
    configJson.appid = appid
    fs.writeFileSync(PROJECT_CONFIG, JSON.stringify(configJson, null, 2))
    console.log(`→ project.config.json appid 临时改为 ${appid}（构建后自动恢复）`)
  } catch (error) {
    restoreProjectConfig()
    fail(`project.config.json 解析失败：${error.message}`)
  }
}

const env = {
  ...process.env
}
if (name) env.BRAND_NAME = name
if (theme) env.BRAND_THEME = theme
if (domain) env.BRAND_API_BASE = domain
if (args.desc) env.BRAND_DESCRIPTION = args.desc
if (iconFile) env.BRAND_ICON_FILE = iconFile
if (iconData) env.BRAND_ICON_DATA = iconData
// 白标自检面板默认不打包（不面向顾客）；--diag 仅用于工程验收出包。
if (args.diag !== undefined) {
  env.TARO_APP_SHOW_DIAG = '1'
  console.log('ℹ 白标自检面板已开启（TARO_APP_SHOW_DIAG=1），「我的」页可见')
}

console.log(`→ 开始构建 ${target} 包：${name || '默认品牌'}`)
const runner = process.platform === 'win32' ? 'pnpm.cmd' : 'pnpm'
const result = spawnSync(runner, [`build:${target}`], {
  cwd: APP_ROOT,
  env,
  stdio: 'inherit'
})

restoreProjectConfig()

if (result.status !== 0) {
  fail(`构建失败（exit ${result.status ?? 'signal'}），环境变量与 appid 均已恢复。`)
}

console.log(`✓ ${target} 包构建完成：${path.join(APP_ROOT, 'dist')}`)
console.log('  参数摘要：')
console.log(`    品牌名   ${name || 'Sinomed（默认）'}`)
console.log(`    主题色   ${theme || '#C7A674（默认）'}`)
console.log(`    后端域名 ${domain || 'https://sinomed.cuihairu.site（默认）'}`)
console.log(`    appid    ${appid || 'touristappid（默认）'}`)
console.log(`    图标     ${iconFile || '未提供（默认包不带页内图标）'}`)
