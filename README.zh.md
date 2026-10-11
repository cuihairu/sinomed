[English](README.md) | [中文](README.zh.md)

<h1 align="center">Sinomed</h1>
<p align="center"><img src="docs/public/logo.svg" width="64" alt="Sinomed logo" /></p>
<p align="center"><b>中医医馆管理系统</b>：顾客建档 · 中医诊疗记录 · 卡项 · 复盘回访</p>
<p align="center">
  <img src="https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white" alt="Java 21" />
  <img src="https://img.shields.io/badge/Spring-Boot-6DB33F?logo=springboot&logoColor=white" alt="Spring Boot" />
  <img src="https://img.shields.io/badge/Vue-3-42b883?logo=vuedotjs&logoColor=white" alt="Vue 3" />
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

一套界面语言贯穿五个设备端：石墨×古金主题（取自品牌 logo 的灰与金）、宣纸底、宋体标题，另有松烟绿 / 朱砂 / 黛蓝三套可切换主题色。以下为各端界面原型（设计稿，数据全为虚构演示），源文件在 [`docs/design/mockups/`](docs/design/mockups/)。

**管理后台**（web · 顾客档案 / 接诊诊疗单）

<p align="center"><img src="docs/public/screenshots/admin-customer.png" width="880" alt="管理后台 · 顾客档案（界面原型）" /></p>

<p align="center"><img src="docs/public/screenshots/admin-diagnosis.png" width="880" alt="管理后台 · 接诊诊疗单：四诊、脉象、取穴、五行生克（界面原型）" /></p>

**主题切换**（管理后台 · 外观设置，四套主题色即点即换）

<p align="center"><img src="docs/public/screenshots/admin-themes.png" width="880" alt="管理后台 · 四套主题色切换：石墨×古金（默认，取自 logo）/ 松烟绿 / 朱砂 / 黛蓝" /></p>

**预约排班**（管理后台 · 建约 / 待到店列表 / 到店接待转接诊 / 按日医师×时段排班网格；小程序自助约期规划中）

<p align="center"><img src="docs/public/screenshots/admin-booking.png" width="880" alt="预约排班 · 建约、待到店列表与医师×时段排班网格已实现，小程序自助约期为设计原型" /></p>

**收费结算**（管理后台 · 结算台：待结算队列 + 储值/微信/支付宝/现金收款；原型中的处方饮片行、次卡抵扣与小票打印仍为设计稿）

<p align="center"><img src="docs/public/screenshots/admin-billing.png" width="880" alt="收费结算 · 待结算队列与储值充值/支付已实现，处方饮片行、次卡抵扣与小票打印为设计原型" /></p>

**中药处方**（管理后台 · 开方 / 处方查询：药味剂量与特殊煎法；配伍审方、计价与代煎领取为规划功能）

<p align="center"><img src="docs/public/screenshots/admin-herbprescription.png" width="880" alt="中药处方笺 · 开方与查询已实现，配伍审方与代煎领取为设计原型" /></p>

**桌面工作站**（desktop · 接诊开单 + 小票机 / 扫码枪外设状态）

<p align="center"><img src="docs/public/screenshots/desktop-workstation.png" width="880" alt="桌面工作站 · 接诊开单（界面原型）" /></p>

**小程序端**（app · 顾客首页，业务页面规划中）

<p align="center"><img src="docs/public/screenshots/mobile-home.png" width="300" alt="小程序端 · 首页（界面原型）" /></p>

**门店自助机**（kiosk · 选服务下单）

<p align="center"><img src="docs/public/screenshots/kiosk-menu.png" width="400" alt="门店自助机 · 选服务（界面原型）" /></p>

**候诊区展示屏**（tablet · 广告轮播 + 叫号）

<p align="center"><img src="docs/public/screenshots/tablet-screen.png" width="880" alt="候诊区展示屏 · 广告轮播与叫号（界面原型）" /></p>

管理后台还有每日报表、卡项、订单、广告屏、外观设置等页面原型，见 [`docs/design/mockups/`](docs/design/mockups/) 与[文档站](https://cuihairu.github.io/sinomed/)。

## 未来功能规划（设计原型）

预约排班的管理端列表/建约/到店接待/排班网格、收费结算的结算台/储值充值、中药处方的开方/查询均已实装，见上方产品预览；仍为设计原型的是：小程序自助约期、小票打印与次卡抵扣、配伍审方与代煎领取。

## 目录结构

```
sinomed/
├── server/    # 服务端（Java 21 / Maven，含 Dockerfile 与部署脚本）
├── app/       # 小程序端（Taro/多端工程，Node.js 24，pnpm）
├── web/       # 管理端前端（Ant Design Pro / Umi，Node.js 24，pnpm）
└── docs/      # 文档站（VitePress）与调研资料
```

- `server/`、`app/` 与 `web/` 的源码直接在本仓维护。
- 后端构建（需 Java 21）：`mvn -B package --file server/pom.xml`。
- 前端构建（需 Node.js 24）：进入 `web/`，`pnpm install && pnpm build`；小程序进入 `app/` 同法。

各组件的详细说明见各自目录内 README，以及[文档站](https://cuihairu.github.io/sinomed/)。
