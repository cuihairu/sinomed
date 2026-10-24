# 小程序端（app）

`app/` 是 Taro 多端工程（React + TypeScript + NutUI React Taro），同一套代码编译到微信小程序、H5 等目标。

::: warning 当前状态
小程序端已完成**工程初始化 + 白标构建 + 顾客首页 + 自助约期 + 我的 + 卡项查卡**：首页按 mobile-home 原型实装（品牌头 / 预约横幅 / 今日宜养 / 我的卡项 / 养生贴士），「今日宜养」宫格实拉上架项目（复用自助机只读接口 `GET /api/v1/kiosk/items`），请求封装层见 `src/services/api.ts`；**自助约期已实装**——`pages/booking`（选日期/时段/项目/手机号提交）走免登录写入口 `POST /api/v1/kiosk/appointments`（口径见[小程序自助约期设计](/zh/design/app-booking)），首页预约横幅为其入口。底部四标签（首页 / 预约 / 卡项 / 我的）：`pages/me` 为本机预约摘要 + 跨设备查预约（手机号+验证码，走 `my-appointments`，`sms_verified` 模式可用，预约摘要不再换机即丢）+ 自助取消预约（`appointment-cancel`，重取验证码后可取消本人待到店且未过期的预约，2026-10-16 代决）+ 主题切换（仅 H5）+ 白标自检（工程验收用，已从首页挪到此处）；`pages/card` 手机查卡已实装（手机号 + 短信验证码表单；openid 路径已接小程序 `wx.login` → 服务端 `/api/v1/kiosk/openid-session` 换 openid，凭据未配置或换取失败时静默回退短信路径，H5 无 `wx.login` 只走短信路径；未绑定时填手机号+验证码走 `openid-bind` 自助绑定（2026-10-15 代决，openid 自证后绑到顾客档案，与查卡同一验证码通道与审计口径，免前台免桌面）——按后台 `sinomed.phone-lookup.mode` 三态开关（默认 `off` 关闭），两条路径都落 `card_lookup_logs` 审计，口径见[手机查卡设计](/zh/design/kiosk#手机查卡-小程序「我的卡项」-2026-10-14-裁定)。**顾客登录态（会话保持）仍不做**：免登录手机号直查会造成横向数据暴露（同[自助约期设计](/zh/design/app-booking) §4 既有边界），账户体系（登录后持续会话）超出演示 MVP 口径。**一次性验证查询已实装**——查卡 `sms_verified` 模式与跨设备查预约都用手机号+一次性验证码验证即查、查完不留会话，口径见[手机查卡设计](/zh/design/kiosk#手机查卡-小程序「我的卡项」-2026-10-14-裁定)。换商家出包见[换马甲构建指南](/zh/app-white-label)。业务功能以[管理端](/zh/web)为准。
:::

顾客首页已按设计稿实装（下方为原型稿，数据为虚构演示；实机宫格数据来自真实接口）：

![小程序端 · 首页（已实装界面截图）](/screenshots/mobile-home.png)

## 工程结构

| 位置 | 内容 |
| ---- | ---- |
| `config/index.ts` | Taro 编译配置（designWidth 375、webpack5、白标参数注入、H5 htmlPluginOption） |
| `config/brand.ts` | 白标品牌参数解析（`BRAND_*` 环境变量 → 带校验的品牌配置，默认值即仓库内置） |
| `src/services/brand.ts` | 业务代码侧品牌常量（编译期替换） |
| `src/services/theme.ts` | 四主题（石墨/松烟绿/朱砂/黛蓝）：H5 端「我的」页切换 + 本机记忆（`data-theme` 挂 documentElement，走 tokens.css 变量组）；小程序端 WXSS 无运行时变量能力，固定石墨 |
| `src/services/api.ts` | 请求封装层（`Taro.request`；已封装 `GET /api/v1/kiosk/items`，H5 开发态走 devServer 代理，其余端直连白标 apiBase） |
| `src/app.config.ts` | 页面注册（`pages/index/index`、`pages/booking/index`、`pages/card/index`、`pages/me/index`）、导航栏品牌名/主题色；底部 tabBar 四标签（首页 / 预约 / 卡项 / 我的） |
| `src/pages/index/` | 顾客首页：品牌头 / 预约横幅（入口 → 自助约期页）/ 今日宜养（宫格实拉上架项目）/ 我的卡项（入口联动「卡项」页；查过卡的本机回称呼+持卡数+查询日期，7 天有效，明细与余次不落本机；购卡与余次明细仍为规划）/ 养生贴士 |
| `src/pages/me/` | 我的 tab：本机预约摘要 + 跨设备查预约（手机号+验证码双因子，走 `POST /api/v1/kiosk/my-appointments`，`sms_verified` 模式可用）+ 待到店预约自助取消（`POST /api/v1/kiosk/appointment-cancel`，重取验证码后取消，仅本人未过期待到店）+ 快捷入口；主题区（仅 H5 端渲染）四主题色点切换；白标自检页底（品牌名/主题色/后端域名/页内图标） |
| `src/pages/booking/` | 自助约期页：日期条（今天起 7 天）→ 时段网格（9:00–17:00 整点，今日已过时段禁选）→ 项目选择（实拉上架项目，可「到店再定」）→ 手机号/称呼提交；成功回执展示预约号与时段，服务端 400 文案原样透出 |
| `scripts/build-brand.mjs` | 换马甲一键出包脚本（参数校验 → 注入 → 构建 → 恢复 appid） |
| `brand-assets/` | 仓库默认图标资产（商家图标不进这里，见[白标指南](/zh/app-white-label)） |
| `project.config.json` | 微信开发者工具配置（`miniprogramRoot: ./dist`；构建后 appid 自动恢复） |
| `project.tt.json` | 抖音小程序配置 |
| `babel.config.js` | babel-plugin-import 按需引入 NutUI |

## 构建命令

```bash
cd app
pnpm install
pnpm dev:h5        # H5 开发（浏览器预览）
pnpm dev:weapp     # 微信小程序开发（watch 模式）
pnpm build:h5      # H5 产物 → dist/
pnpm build:weapp   # 微信小程序产物 → dist/
```

其余端（swan / alipay / tt / qq / jd / rn / quickapp）的构建脚本同样可用，见 `package.json`。

换商家出包不走上面的裸构建命令，统一走[白标一键脚本](/zh/app-white-label)：

```bash
node scripts/build-brand.mjs --name 商家名 --domain https://... --theme '#RRGGBB' --appid wx...
```

## 关键配置

- **设计稿**：designWidth 375，`pxtransform` 把 px 转 rpx，NutUI 组件（`nut-` 前缀）豁免转换；
- **样式方案**：原生 CSS 文件（无 CSS Modules），`src/tokens.css` 与 `docs/design/mockups/tokens.css` 同源（品牌变量单一出处）；
- **UI 库**：`@nutui/nutui-react-taro` 2.x，经 babel-plugin-import 按需引入；
- **白标注入**：`BRAND_*` 环境变量 → 编译期替换（详见[换马甲构建指南](/zh/app-white-label)）；
- **webpack**：锁定 5.104.1（5.111 起 ProgressPlugin 加了 schema 校验，与 Taro 3.6.40 内置 webpackbar 冲突）；
- **微信 appid**：仓库存占位 `touristappid`；商家 appid 由构建脚本写进产物，仓库文件不改。

## 后续接手建议

- 请求封装层已建（`src/services/api.ts`），后续接口按业务域扩展；带鉴权的接口参照管理端 `src/requestErrorConfig.ts` 的 token 注入方式；
- 页面注册走 `src/app.config.ts`，tabBar 按业务域规划；
- 顾客侧身份口径：**不做账户登录态**——免登录，查卡/查预约走 openid 或手机号+一次性验证码验证即查、查完不留会话；openid 仅存内存不落存储。管理端鉴权才用服务端 `POST /api/v1/user/login` 的 token 注入方式（见 `web/src/requestErrorConfig.ts`）。
