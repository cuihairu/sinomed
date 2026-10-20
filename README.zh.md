[English](README.md) | [中文](README.zh.md)

<h1 align="center">Sinomed</h1>
<p align="center"><img src="docs/public/logo.svg" width="64" alt="Sinomed logo" /></p>
<p align="center"><b>中医医馆管理系统</b>：顾客建档 · 中医诊疗记录 · 卡项 · 复盘回访</p>
<p align="center">
  <img src="https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white" alt="Java 21" />
  <img src="https://img.shields.io/badge/Spring-Boot-6DB33F?logo=springboot&logoColor=white" alt="Spring Boot" />
  <img src="https://img.shields.io/badge/React-18-20232A?logo=react&logoColor=61DAFB" alt="React 18" />
  <img src="https://img.shields.io/badge/小程序-Taro-3AA3F0?logo=taro&logoColor=white" alt="Taro" />
  <img src="https://img.shields.io/badge/SQLite-Docker-2496ED?logo=docker&logoColor=white" alt="SQLite / Docker" />
  <img src="https://img.shields.io/badge/docs-VitePress-646CFF?logo=vitepress&logoColor=white" alt="VitePress" />
  <a href="https://opensource.org/licenses/Apache-2.0"><img src="https://img.shields.io/badge/License-Apache--2.0-blue.svg" alt="Apache 2.0" /></a>
</p>
<p align="center">文档站：<a href="https://cuihairu.github.io/sinomed/">cuihairu.github.io/sinomed</a></p>

## 演示站点

**演示站点 https://sinomed.cuihairu.site/ ｜ 演示账号 `admin` / `123`**（沙箱体验用，数据定期重置）

> 演示环境由根目录 `compose.yml` 一键起：服务端 + 管理端 + SQLite 持久卷，`DEMO_SEED=true` 幂等播种一套全虚构脱敏的演示数据（顾客、中医诊疗记录、卡项、复盘回访、预约、储值与待结算单）。员工演示账号 `gu`（馆长）、`shen`（中医师）、`su`（前台），密码均为 `123`。镜像由 CI 推送 ghcr.io（`latest` + `sha-<短提交>`）。

## 产品预览

一套界面语言贯穿五个设备端：石墨×古金主题（取自品牌 logo 的灰与金）、宣纸底、宋体标题，另有松烟绿 / 朱砂 / 黛蓝三套可切换主题色。以下为各端已实装页面的界面截图（数据全为虚构演示）。

**管理后台**（web · 顾客档案 / 接诊诊疗单）

<p align="center"><img src="docs/public/screenshots/admin-customer.png" width="880" alt="管理后台 · 顾客档案（已实装）" /></p>

<p align="center"><img src="docs/public/screenshots/admin-diagnosis.png" width="880" alt="管理后台 · 接诊诊疗单：四诊、脉象、取穴、五行生克（已实装）" /></p>

**主题切换**（管理后台 · 外观设置，四套主题色即点即换）

<p align="center"><img src="docs/public/screenshots/admin-themes.png" width="880" alt="管理后台 · 四套主题色切换：石墨×古金（默认，取自 logo）/ 松烟绿 / 朱砂 / 黛蓝" /></p>

**预约排班**（管理后台 · 建约 / 待到店列表 / 到店接待转接诊 / 按日医师×时段排班网格；小程序自助约期已实装）

<p align="center"><img src="docs/public/screenshots/admin-booking.png" width="880" alt="预约排班 · 建约、待到店列表、排班网格与小程序自助约期均已实装" /></p>

**收费结算**（管理后台 · 结算台：待结算队列 + 储值/微信/支付宝/现金/次卡收款 + 小票套打 + 退单原路退回与按日对账）

<p align="center"><img src="docs/public/screenshots/admin-billing.png" width="880" alt="收费结算 · 待结算队列、储值充值/支付、次卡抵扣与小票打印均已实装" /></p>

**中药处方**（管理后台 · 开方 / 处方查询：药味剂量与特殊煎法；配伍审方、剂量审方、计价、代煎与膏方领取、药房发药工作台）

<p align="center"><img src="docs/public/screenshots/admin-herbprescription.png" width="880" alt="中药处方笺 · 开方、查询、配伍审方、计价与代煎、膏方领取均已实装" /></p>

**桌面工作站**（desktop · 接诊开单 + 小票机 / 扫码枪外设状态）

<p align="center"><img src="docs/public/screenshots/desktop-workstation.png" width="880" alt="桌面工作站 · 接诊开单（已实装）" /></p>

**小程序端**（app · 底部四标签：首页 / 预约 / 卡项 / 我的；手机查卡已实装——openid 与短信验证码双因子，按后台 `sinomed.phone-lookup.mode` 三态开关（默认 `off` 关闭）并落审计，口径见[手机查卡设计](docs/design/kiosk.md)；顾客登录态与购卡仍属规划功能）

<p align="center"><img src="docs/public/screenshots/mobile-home.png" width="300" alt="小程序端 · 首页（已实装）" /></p>

**门店自助机**（kiosk · 选服务下单）

<p align="center"><img src="docs/public/screenshots/kiosk-menu.png" width="400" alt="门店自助机 · 选服务（已实装）" /></p>

**候诊区展示屏**（tablet · 广告轮播 + 叫号）

<p align="center"><img src="docs/public/screenshots/tablet-screen.png" width="880" alt="候诊区展示屏 · 广告轮播与叫号（已实装）" /></p>

管理后台还有每日报表、卡项、订单、广告屏、外观设置等页面设计稿，见 [`docs/design/mockups/`](docs/design/mockups/) 与[文档站](https://cuihairu.github.io/sinomed/)。

## 未来功能规划

已实装：预约排班（管理端列表/建约/到店接待/排班网格 + 小程序自助约期）、收费结算（结算台：储值/微信/支付宝/现金/次卡收款、小票套打、退费与日对账）、中药处方（开方/查询：配伍审方、剂量审方、计价、代煎与膏方领取、药房发药工作台）、小程序手机查卡（openid 与短信双因子，`sinomed.phone-lookup.mode` 三态开关默认 off，落审计）、名医分层（等级 0-3、名册排序、溢价上限）、桌面工作站、门店自助机、候诊区展示屏。

在线支付真实通道仍挂起（需商户号与密钥等真实凭据，在此之前红线不碰）。已代决维持非目标（2026-10-14 裁定）：西药/成药处方、多门店/连锁、采购单据与盘点——单店中医馆定位。口径见 `docs/research/features.md`，审计轨迹见 `docs/审计-文档一致性.md`。

## 目录结构

```
sinomed/
├── server/    # 服务端（Java 21 / Maven，含 Dockerfile 与部署脚本）
├── web/       # 管理端前端（Ant Design Pro / Umi，Node.js 24，pnpm）
├── app/       # 小程序端（Taro/多端工程，Node.js 24，pnpm）
├── desktop/   # 桌面工作站壳（Tauri 2，Windows）
├── kiosk/     # 门店自助机（选服务下单）
├── tablet/    # 候诊区展示屏（广告轮播 + 叫号）
└── docs/      # 文档站（VitePress）与调研资料
```

- `server/`、`app/` 与 `web/` 的源码直接在本仓维护。
- 后端构建（需 Java 21；Maven 版本由 `server/.mvn/wrapper` 钉在 3.10.0，无需本机装 Maven）：`cd server && ./mvnw -B package`。
- 前端构建（需 Node.js 24）：进入 `web/`，`pnpm install && pnpm build`；小程序进入 `app/` 同法。

各组件的详细说明见各自目录内 README，以及[文档站](https://cuihairu.github.io/sinomed/)。

## 本地测试口径

CI 与本地开发按改动面三路分流——改一行文档不该全量编译。

1. **文档改动**（`*.md`、`docs/**`、图片）：不编译。maven.yml 的 `paths-ignore`
   已跳过这些路径（docs.yml 本就只认 `docs/**`；docker.yml 只认 `server/**`、`web/**`）。
2. **代码改动**：按语言各跑一侧，不交叉全量。本仓后端单模块（`server/pom.xml`，
   无聚合父 pom，reactor 为空），`-pl` 不可用（报 "Could not find the selected
   project in the reactor"）：
   - 后端：`cd server && ./mvnw -B test`
   - 前端：`pnpm -C web lint && pnpm -C web test`（CI 已跑 lint:js + tsc + jest，见 `.github/workflows/frontend.yml`；本地多一层 prettier 检查）
3. **全量**（`cd server && ./mvnw -B package`）：仅以下情形触发——pom.xml、
   `.github/**`、Docker/Compose 配置、发布前。
