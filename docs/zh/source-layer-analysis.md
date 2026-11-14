# 源码分层分析

[English](/source-layer-analysis.md) | [中文](/zh/source-layer-analysis.md)

本分析记录 HEAD `1f10eef0234f37cd81bd5b11b35d8c2c93b4bc99` 的运行时边界。服务端是一个 Spring Boot 进程；仓库另含四个独立构建的 Web 前端和一个 Tauri 桌面壳。

## 模块与状态

| 模块 | 职责 | 状态存储 |
|---|---|---|
| Spring Boot API（`server/`） | 承载 REST 资源、诊所用例、集成与生命周期任务。 | 默认 MySQL，演示态 SQLite；Caffeine 缓存与进程内 SDK/票据状态；本地广告、微信素材、打印模板文件。 |
| 管理端（`web/`） | Ant Design Pro 管理临床、收费、运营、内容和公众号后台。 | JWT、主题与表格偏好存浏览器存储；页面态存 React 内存。 |
| 小程序端（`app/`） | Taro 小程序提供预约和验证后查卡。 | 预约会话、卡项摘要和 OpenID 相关状态存平台存储或组件内存。 |
| 顾客自助机（`kiosk/`） | 提供目录浏览和下单。 | 目录/订单态仅存 React 内存；持久化交还 API。 |
| 候诊大屏（`tablet/`） | 轮播媒体并播报叫号。 | 素材清单/目录与叫号态存内存；IndexedDB 预缓存支持断网播放。 |
| 桌面壳（`desktop/`） | 加载管理端地址并暴露扫码/打印桥接命令。 | OS 配置目录下 JSON；扫码/ESC-POS 活动连接态存内存；打印临时文件落盘。 |
| 文档站（`docs/`） | 构建双语文档与一致性检查。 | 构建期静态资产；无应用状态。 |

## 调用面

- 管理端页面调用 `web/src/services/ant-design-pro` 生成客户端，经 Umi request 发 REST；登录后 JWT 存浏览器。
- 小程序和两个公共端以平台 request/browser fetch 调 REST；桌面壳不代理业务请求，内嵌管理页直调 API。
- Tauri IPC 只连接本地 WebView 与扫码枪、ESC/POS、打印、配置和自启命令。
- 服务端经 RestTemplate/Java HttpClient/WxJava 出网调微信，并以 HTTP 接收回调；媒体与打印模板读写配置的本地文件根。
- 未见 Redis、消息队列或 RPC 总线。进程内缓存是 Caffeine；持久态为关系库加选定文件。

## 抽象层

### 1. 接入编排

Spring Security 与 multipart 限额先准入口；JWT 认证、方法安全和静态/媒体路由在此配置。实现位于 `server/src/main/java/com/sinomed/config`；配置 schema 是 `server/src/main/resources/application.yml`；外部契约由 Springdoc/Knife4j 生成，而非手工协议文件。

### 2. 资源控制器

30 个控制器把资源请求转成服务调用。API 范围由 Springdoc 从 `com.sinomed.controller` 装配；无控制器专属协议文件。用户文案在管理端 locale 或页面代码，不在控制器注解里。

### 3. 应用服务

`server/src/main/java/com/sinomed/service` 接口定义用例；`service/impl` 提供事务与编排。公众号渠道以 186 行薄门面 `WxOfficialAccountService` 落在本层（2026-11-10 拆分落地），委托 `service/` 下 `WxCallbackService` / `WxFanBindService` / `WxOauthService`，以及 `service/impl` 下 `WxMessageLogService` 与四个 `Wx*AdminService`（自动回复/菜单/素材/账号），回调/OAuth token 由 `WxAccountTokenResolver` 解析。

### 4. 持久化

JPA 审计与仓储构成关系库边界。`server/init/init.sql` 是运维引导，运行 schema 主要来自 JPA `ddl-auto=update`；测试/引导 SQL 不是运行协议契约。

### 5. 临床参考

药材/方剂/穴位/模板字典与安全校验是参考数据和规则计算。schema 随 JPA 实体；管理端文案在 web locale 与页面。

### 6. 临床运营

顾客、预约、叫号、接诊、处方、回访、考勤和订单工作流归此。控制器/服务族是具体单元；无独立工作流 DSL 或专属协议文件。

### 7. 财务结算

卡项、充值、订单、提成、结算、对账和退款流程维护审计日志与资金状态迁移。

### 8. 渠道集成

自助机端点、小程序登录、公众号回调、WxJava 实例和凭据加密归此。`application.yml` 是渠道配置 schema；XML 回调解析和 token 封装是实现内协议行为，不是独立线上格式文件。

### 9. 内容与外设

广告目录/排期/媒体服务在服务端；大屏播放消费排期 API。桌面本地配置、打印模板、扫码枪和 ESC/POS 是设备能力；模板是协议产物，按钮文案在 `desktop/ui/index.html`。

### 10. 交互运行时

web/app/kiosk/tablet/desktop 是独立部署面。路由、平台存储键、本地主题和展示文案各自定义 schema 与文案挂点。文档站是构建期文档运行时，有自己的链接/主题检查。

### 11. 平台生命周期

演示种子、启动迁移、文档校验和 Compose/CI 打包初始化或打包运行模块。配置来自 Compose/environment 和 VitePress；生命周期任务在应用启动执行一次，CI 按推送/发布执行。

## 拆分理由

1. 接入编排与控制器分离：认证/方法安全是准入关注点，控制器是 30 个资源翻译器。
2. 服务是事务/应用边界：`SettlementServiceImpl` 组合多仓储形成事务资金流，`WxOfficialAccountService` 编排回调、账号、文件和出网。
3. 临床参考与临床运营分离：字典/规则是处方复用安全输入，运营记录个体就诊和工作流状态。
4. 财务结算独立：充值、扣减、提成、结算与对账共享资金台账事务不变量，不同于临床流。
5. 渠道集成独立：自助机/小程序/公众号流量各有凭据、验签、查卡限流和回调协议行为。
6. 内容外设与交互运行时分离：广告排期与设备能力有服务/文件/IPC 边界，不同于前端状态和渲染。
7. 交互运行时彼此独立：每个目标有独立包管理、构建、通道和设备约束，即使共享 REST 契约。
8. 平台生命周期独立：种子与启动迁移是条件一次性任务，不同于常驻请求服务。

## 相关页

- [术语表](/zh/glossary.md)
- [架构总览](/zh/server/architecture.md)
