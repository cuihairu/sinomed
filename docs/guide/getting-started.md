# 快速上手

> 🌐 [English](./getting-started.en.md) · **本页为中文**

本指南按平台分三步：先起**服务端**，再跑**管理端**，最后按需运行**小程序端**。三端均在本仓库内：`server/`（服务端）、`web/`（管理端）、`app/`（小程序端）。

## 你会用到的环境

| 组件 | 版本要求 | 说明 |
| ---- | -------- | ---- |
| JDK | 21 | 服务端编译与运行 |
| Maven | 3.10.0（`server/.mvn/wrapper` 钉版，仓库自带 `./mvnw` 可免安装） | 服务端构建 |
| Node.js | 24 | 管理端与小程序端 |
| pnpm | 9/10 | 前端包管理 |
| MySQL | 8.0 | 数据库 |
| Docker（可选） | - | 用 compose 一键起数据库与整套服务 |

## 第一步：启动服务端

### 方式 A：Docker Compose（推荐，一键起库）

仓库提供开发编排 `server/dev.yml`，会启动 MySQL（自动执行 `server/init/init.sql` 建库）与服务端容器（开启 5008 远程调试）：

```bash
cd server
docker compose -f dev.yml up -d
```

启动完成后：

- 服务端地址：<http://127.0.0.1:2347>
- 接口文档（Knife4j 中文）：<http://127.0.0.1:2347/doc.html>

### 方式 B：本机运行

先准备一个 MySQL 8 实例，并执行 `server/init/init.sql` 完成建库（数据表由 JPA 自动创建，无需手工建表）。然后：

```bash
cd server
DB_HOST=127.0.0.1 DB_PORT=3306 DB_NAME=sinomed \
DB_USERNAME=sinomed DB_PASSWORD='你的密码' SERVER_PORT=2347 \
./mvnw spring-boot:run
```

服务端通过环境变量读取数据库与端口配置，全部变量见[配置与部署](/server/deploy)。

::: tip 首次启动会自动创建管理员
首次启动时，若库中不存在 `admin` 账号，系统会自动创建管理员账号 **admin**（默认密码 **123**，源码内置默认值）。接入正式环境前请先登录修改密码，并更换 `application.yml` 中的 JWT 密钥。
:::

## 第二步：启动管理端

管理端是 Ant Design Pro / Umi 工程。开发模式默认关闭 mock，通过代理直连本机服务端：

```bash
cd web
pnpm install
pnpm dev          # 等价于 REACT_APP_ENV=dev MOCK=none UMI_ENV=dev max dev
```

启动后访问 `http://localhost:8000`，开发代理会把 `/api` 转发到 `http://127.0.0.1:2347`（见 `web/config/proxy.ts`）。

用上一步的 `admin / 123` 登录即可进入工作台。建议沿医馆主流程走一遍（建档 → 接诊 → 调理方案 → 回访）：

1. **顾客建档**：顾客系统 → 顾客建案，录入姓名 / 年龄 / 性别 / 手机 / 会员等级。
2. **接诊**：顾客查询 → 行操作「创建诊断」，填写主诉、四诊（问 / 望 / 切）、脉象（左右手）、五行生克、取穴、诊断与调理方案；复诊时从「诊断历史」调阅上次记录回填。
3. **卡项**：卡项系统 → 创建卡项，登记门店服务项目（为订单与耗卡打基础）。
4. **复盘与回访**：反馈系统填写每日总结、为顾客设定回访计划；到期回访自动出现在每日报表（`/dash/day`）。

::: tip 关于「开方」的边界
调理方案（`plan`）与饮食建议以文本记录；中药处方（药材 / 剂量 / 剂数）已有结构化模型（`prescriptions` 表 + `/api/v1/prescription`），挂号（预约）与收费也已实装，其余板块的规划对照见[功能点清单](/research/features)。
:::

此外：员工上 / 下班签到与按月考勤统计在员工系统（诊所运营辅助），管理员可在其中维护员工账号。

## 第三步：运行小程序端

小程序端为 Taro 工程，支持编译到微信 / H5 等多端：

```bash
cd app
pnpm install
pnpm dev:h5       # 浏览器 H5 预览
pnpm build:weapp  # 编译微信小程序产物到 dist/
```

微信端产物在 `app/dist`，用微信开发者工具导入该目录即可预览。注意 `app/project.config.json` 当前是模板占位 `appid`（`touristappid`），正式开发请替换为你自己的小程序 appid。

::: warning 小程序端现状
小程序端已完成工程初始化 + 白标构建 + 顾客首页 + 自助约期 + 我的 + 卡项查卡（底部四标签，网络请求层见 `src/services/api.ts`，宫格拉真实上架项目；自助约期走免登录 `POST /api/v1/kiosk/appointments`，手机查卡按后台 `sinomed.phone-lookup.mode` 三态开关、默认 `off` 关闭）。当前业务功能以管理端（web）为准。
:::

## 下一步

- 了解服务端全貌：[架构总览](/server/architecture)
- 查接口与字段：[REST 接口清单](/server/api)、[数据模型](/server/data-model)
- 生产环境部署：[配置与部署](/server/deploy)
