---
layout: home

hero:
  name: Sinomed
  text: 中医医馆管理系统
  tagline: 围绕中医医馆日常接诊设计：顾客建档、中医诊疗记录（四诊 / 脉象 / 五行 / 取穴 / 诊断与调理方案）、卡项、复盘与回访。服务端、管理端与小程序端一套仓库。
  actions:
    - theme: brand
      text: 快速上手
      link: /guide/getting-started
    - theme: alt
      text: 服务端接口
      link: /server/api

features:
  - icon:
      src: /icons/stethoscope.svg
      alt: 诊疗
    title: 顾客与诊疗记录
    details: 顾客建档、查询、更新（含过敏史/既往史逐条记录）；诊疗单覆盖主诉、望闻问切、脉象（左右手）、五行生克、取穴、诊断与调理方案，复诊可调阅历史。
  - icon:
      src: /icons/receipt-yuan.svg
      alt: 卡项
    title: 卡项与会员
    details: 门店卡项（服务项目）的创建、查询与维护；顾客档案带会员等级，为会员运营与订单打基础。
  - icon:
      src: /icons/report-analytics.svg
      alt: 复盘
    title: 复盘与回访
    details: 每日总结按天沉淀，员工复盘与顾客回访按日汇总；回访到期待办自动出现在每日报表；月度收费报表按结算时间聚合逐日单数/实收与支付方式构成。
  - icon:
      src: /icons/prescription.svg
      alt: 规划
    title: 医馆板块规划
    details: 预约排班、接诊单（含穴位字典选穴、针灸处方记录与外治处方模板套用）、中药处方（配伍审方/过敏提示/药材字典计价/代煎与膏方领取/药房发药工作台/病症模板/方剂库带方）、收费结算（储值/次卡核销/小票套打）、员工请假、周期员工班表与周班次对照、饮片出入库与效期预警已实装；采购单据与在线支付为规划；分层规划与参考来源见「调研 · 功能点清单」。
  - icon:
      src: /icons/device-desktop.svg
      alt: 桌面
    title: 桌面工作站
    details: 前台 / 医生工作站的 Windows 薄壳（Tauri 2）：直接加载管理端页面、处方笺 / 小票打印、扫码定位顾客、自动更新，业务仍在服务端。见「设计 · 桌面版」。
  - icon:
      src: /icons/device-tablet.svg
      alt: 平板
    title: 广告展示端
    details: 候诊区平板 Kiosk：广告与科普轮播、前台叫号 1 秒级上屏、断网续播；素材 / 排期 / 屏 / 叫号在管理端配置。见「设计 · 平板展示」。
  - icon:
      src: /icons/receipt-yuan.svg
      alt: 下单
    title: 顾客选服务
    details: 前台 iPad 自助点选卡项、留手机号下单，管理端订单管理接单流转。见「设计 · 顾客选服务」。
  - icon:
      src: /icons/api.svg
      alt: 接口
    title: 接口与文档
    details: REST 接口统一前缀 /api/v1，内置 Knife4j 中文接口文档（doc.html），JWT 登录鉴权。
  - icon:
      src: /icons/inventory.svg
      alt: 部署
    title: 容器化部署
    details: 提供 Dockerfile 与 docker-compose 编排（MySQL + 服务端 + Nginx），亦支持 systemd 裸机部署。
---

## 产品预览

一套界面语言贯穿五个设备端：石墨×古金主题（取自品牌 logo 的灰与金）、宣纸底、宋体标题，另有松烟绿 / 朱砂 / 黛蓝三套可切换主题色。以下为各端界面原型（设计稿，数据全为虚构演示），源稿在仓库 `docs/design/mockups/`。

**管理后台**（web · 顾客档案 / 接诊诊疗单）

![管理后台 · 顾客档案（界面原型）](/screenshots/admin-customer.png)

![管理后台 · 接诊诊疗单（界面原型）](/screenshots/admin-diagnosis.png)

**主题切换**（管理后台 · 外观设置，四套主题色即点即换）

![管理后台 · 四套主题色切换（界面原型）](/screenshots/admin-themes.png)

**预约排班**（管理后台 · 建约 / 待到店列表 / 到店接待转接诊 / 按日医师×时段排班网格；小程序自助约期已实装：免登录接口 + 小程序约期页）

![预约排班 · 建约、待到店列表、医师×时段排班网格与小程序自助约期均已实现](/screenshots/admin-booking.png)

**收费结算**（管理后台 · 结算台：待结算队列 + 储值/微信/支付宝/现金/次卡收款 + 小票套打；原型中的处方饮片行为示意，裁定不做）

![收费结算 · 待结算队列、储值充值/支付、次卡抵扣与小票打印已实现，处方饮片行为示意](/screenshots/admin-billing.png)

**中药处方**（管理后台 · 开方 / 处方查询 / 药房工作台：药味剂量与特殊煎法；配伍审方、计价、代煎与膏方领取、发药确认已实装）

![中药处方笺 · 开方、查询、配伍审方、过敏提示、实时计价与代煎、膏方领取已实现](/screenshots/admin-herbprescription.png)

**桌面工作站**（desktop · 接诊开单 + 外设状态）

![桌面工作站 · 接诊开单（界面原型）](/screenshots/desktop-workstation.png)

**小程序端**（app · 顾客首页，业务页面规划中）

![小程序端 · 首页（界面原型）](/screenshots/mobile-home.png)

**门店自助机**（kiosk · 选服务下单）

![门店自助机 · 选服务（界面原型）](/screenshots/kiosk-menu.png)

**候诊区展示屏**（tablet · 广告轮播 + 叫号）

![候诊区展示屏 · 广告轮播与叫号（界面原型）](/screenshots/tablet-screen.png)

管理后台还有每日报表、卡项、订单、广告屏、外观设置等页面原型，见仓库 `docs/design/mockups/`。
