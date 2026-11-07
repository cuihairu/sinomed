# 术语表

[English](/glossary.md) | [中文](/zh/glossary.md)

术语按源码体现的职责归纳，不按目录名机械归类；证据指向本次分析所用 HEAD。

| 术语 | 英文 | 定义 | 证据 |
|---|---|---|---|
| 接入编排层 | Request Orchestration Layer | 在进入业务前完成 REST 暴露、身份认证、请求体限额与方法级授权。 | server/src/main/java/com/sinomed/config/SecurityConfig.java:27; server/src/main/java/com/sinomed/config/WebMvcConfig.java:16; server/src/main/resources/application.yml:3 |
| 资源控制器层 | Resource Controller Layer | 将 `/api/v1` 下 30 个业务资源族的 HTTP 请求校验并映射到应用服务。 | server/src/main/java/com/sinomed/controller/PrescriptionController.java:62; server/src/main/java/com/sinomed/controller/KioskController.java:66; server/src/main/java/com/sinomed/controller/WxOfficialAccountController.java:81 |
| 应用服务层 | Application Service Layer | 持有诊所用例、事务边界、缓存失效、仓储编排和外部集成调用。 | server/src/main/java/com/sinomed/service/impl/SettlementServiceImpl.java:49; server/src/main/java/com/sinomed/service/WxOfficialAccountService.java:90; server/src/main/java/com/sinomed/service/impl/HerbStockServiceImpl.java:37 |
| 持久化层 | Persistence Layer | 通过 JPA 实体与仓储，把关系型状态和审计字段保存到 MySQL 或 SQLite。 | server/src/main/java/com/sinomed/config/PersistenceConfig.java:6; server/src/main/java/com/sinomed/repository/PrescriptionTemplateRepository.java:11; server/src/main/resources/application.yml:18 |
| 业务字典层 | Clinical Reference Layer | 维护药材、方剂、穴位、治法与处方模板、禁忌/过敏规则，以及定价和剂量校验。 | server/src/main/java/com/sinomed/service/FormulaService.java:8; server/src/main/java/com/sinomed/service/AcupointService.java:8; server/src/main/java/com/sinomed/service/AllergyService.java:15 |
| 临床运营层 | Clinical Operations Layer | 协调顾客、预约、叫号、接诊、处方、发药、回访、员工考勤和订单流转。 | server/src/main/java/com/sinomed/service/AppointmentService.java:15; server/src/main/java/com/sinomed/service/TreatService.java:11; server/src/main/java/com/sinomed/service/ReviewService.java:11 |
| 财务结算层 | Financial Settlement Layer | 管理充值台账、卡项扣减/调整、订单、提成、结算、对账与退款。 | server/src/main/java/com/sinomed/service/CardService.java:16; server/src/main/java/com/sinomed/service/OrderService.java:15; server/src/main/java/com/sinomed/service/CommissionService.java:11 |
| 渠道集成层 | Channel Integration Layer | 承载小程序登录查卡、自助机流程、公众号回调、多账号 SDK 实例和微信凭据处理。 | server/src/main/java/com/sinomed/controller/KioskController.java:66; server/src/main/java/com/sinomed/service/WechatClient.java:18; server/src/main/java/com/sinomed/service/WxMpServiceProvider.java:22 |
| 内容与外设层 | Content And Peripheral Layer | 发布排期广告、媒体文件、打印模板、桌面命令、串口扫码枪和 ESC/POS 打印。 | server/src/main/java/com/sinomed/service/AdsService.java:16; desktop/src-tauri/src/main.rs:71; desktop/src-tauri/src/scanner.rs:29 |
| 交互执行层 | Interaction Runtime Layer | 将管理端、小程序、顾客自助机、候诊大屏和桌面 WebView 拆成独立构建前端，各持设备状态与 HTTP API。 | web/config/routes.ts:8; app/src/app.config.ts:1; tablet/src/App.tsx:15 |
| 平台生命周期层 | Platform Lifecycle Layer | 在启动、构建与部署阶段完成配置、迁移、种子、文档检查和多端打包。 | server/src/main/java/com/sinomed/seed/DemoSeedRunner.java:90; server/src/main/java/com/sinomed/config/DbUniqueIndexInitializer.java:22; compose.yml:6 |
