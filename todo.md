# TODO

> 生成于 2026-10-11（本仓无任何待办文件；来源：docs/audit-doc-consistency.md「文档超前于代码」项、docs/web.md 遗留、工作区在途改动）。

## 未实现功能（audit-doc-consistency.md 点名，有设计原型无代码）
- [ ] 预约挂号（设计原型：/screenshots/admin-booking.png）
- [ ] 中药处方（设计原型：/screenshots/…）
- [ ] 收费结算（设计原型：/screenshots/…）

## 安全与技术债
- [ ] 服务端接口层方法级权限强制（web.md:174：当前只做页面级隐藏）

## 在途工作（未提交，需先收口）
- [ ] 收尾当前 5 个未提交文件：PrescriptionController / PrescriptionEntity / PrescriptionRepository / PrescriptionItemRepository / DemoSeedRunner（处方相关改动）——完成即测试绿提交推送

验收：对应页面可实跑 + 后端接口有权限校验；测试绿才 push，commit 英文简短。
