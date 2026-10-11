# TODO

> 生成于 2026-10-11（本仓无任何待办文件；来源：docs/audit-doc-consistency.md「文档超前于代码」项、docs/web.md 遗留、工作区在途改动）。

## P0 — UI 按原型核对（用户 2026-10-11 令：现在 UI 都是错的）
原型 = `docs/design/mockups/*.html`（15 页 + `tokens.css` 设计系统内核），实际截图 = `docs/public/screenshots/*.png`（15 张同名 1:1 对应）。逐页比对实现与原型，产出差异证据表后修。

- [ ] **建立对照表**：15 页逐页列 `原型 HTML | 实际路由/组件 | 主要差异 | 严重度`，差异必须带 file:line 证据（原型侧写 section/元素，实现侧写组件文件）
- [ ] `admin-customer` 顾客档案 / `admin-diagnosis` 接诊诊疗单 / `admin-dash-day` 每日报表
- [ ] `admin-items` 卡项管理 / `admin-orders` 订单管理 / `admin-ads` 广告屏管理
- [ ] `admin-appearance` 外观设置：四主题卡即时切换 + 实时预览网格
- [ ] `admin-booking` 预约排班（◐部分实现：待到店/建约/排班网格已装，对照补缺口）
- [ ] `admin-herbprescription` 中药处方（◐部分实现：开方/审方/计价/代煎已装，对照补缺口）
- [ ] `admin-billing` 收费结算（◐部分实现：结算台/次卡抵扣/小票已装；**处方饮片行已裁定不做，核对时不得当缺陷**）
- [ ] `desktop-workstation` 接诊开单+外设（1920×1080）/ `mobile-home` 小程序首页（卡项余次占位是如实标注，非 bug）
- [ ] `kiosk-menu` 自助下单（900×1200 竖屏）/ `tablet-screen` 广告轮播+叫号上屏
- [ ] **设计系统一致性**：实现侧是否消费 `tokens.css` 变量与四主题（石墨/松烟绿/朱砂/黛蓝）、色板 `#666666`+`#C7A674`、宋体标题、宣纸底——不一致处逐条列出
- [ ] 修复后按 mockups README §2 口径重出截图（1440×900@2x 等），对照通过才勾选

## 未实现功能（audit-doc-consistency.md 点名；mockups README 状态为「部分实现」，缺口以上表核对为准）
- [ ] 预约挂号（设计原型：/screenshots/admin-booking.png）
- [ ] 中药处方（设计原型：/screenshots/…）
- [ ] 收费结算（设计原型：/screenshots/…）

## 安全与技术债
- [ ] 服务端接口层方法级权限强制（web.md:174：当前只做页面级隐藏）

## 在途工作（未提交，需先收口）
- [ ] 收尾当前 5 个未提交文件：PrescriptionController / PrescriptionEntity / PrescriptionRepository / PrescriptionItemRepository / DemoSeedRunner（处方相关改动）——完成即测试绿提交推送

验收：对应页面可实跑 + 后端接口有权限校验；测试绿才 push，commit 英文简短。
