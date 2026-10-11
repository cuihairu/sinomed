# 架构总览

服务端（`server/`）是整个系统的业务中枢：管理端与小程序端都通过 `/api/v1` 前缀的 REST 接口访问它。

## 技术栈

| 组件 | 选型 | 版本 |
| ---- | ---- | ---- |
| 语言 / 运行时 | Java | 21 |
| 应用框架 | Spring Boot | 3.5.x |
| 数据访问 | Spring Data JPA（Hibernate） | 随 Boot |
| 数据库 | MySQL | 8.0 |
| 缓存 | Caffeine（本地缓存） | 3.3.x |
| 认证 | Spring Security + JWT（jjwt） | 0.13.x |
| 接口文档 | Knife4j（OpenAPI 3，中文） | 4.5.0 |
| 构建 | Maven Wrapper | - |

## 分层结构

代码在 `com.sinomed` 包下按经典分层组织：

| 包 | 职责 |
| ---- | ---- |
| `controller` | REST 接口层：参数接收、校验、权限入口，不含业务规则 |
| `service` / `service.impl` | 业务层：业务规则、数据组装、缓存注解 |
| `repository` | 数据访问层：Spring Data JPA 接口 |
| `entity` | 数据模型：与数据库表一一对应（ddl-auto 自动建表） |
| `vo` | 出参视图对象（View） |
| `params` | 入参对象 |
| `config` | 配置类：安全、缓存、JPA、接口文档、加密 |
| `security` | JWT 过滤器、签发 / 校验、角色与权限定义 |
| `exception` | 全局异常到 HTTP 状态码的映射 |
| `util` | 工具类：日期区间、考勤计算、手机号校验 |

## 模块与业务链路

从接口与页面出发，系统当前的主链路是：

```text
顾客建档 → 创建诊疗单（四诊/脉象/取穴/诊断与调理方案） → 复诊调阅历史
卡项维护 → 订单（自助机下单 / 前台建单 → 待接待 → 接单 / 完成 / 取消）
每日报表 ← 每日总结 / 员工复盘 / 顾客回访（到期提醒）
收费结算 ← 订单收款（储值 / 微信 / 支付宝 / 现金 / 次卡抵扣） → 小票套打 / 月度收费报表
员工签到 → 打卡记录 → 今日/按月考勤统计（运营辅助）
```

| 模块 | Controller | 现状 |
| ---- | ---------- | ---- |
| 顾客（建档 / 查询 / 更新 / 删除 / 病史） | `CustomerController` | 可用 |
| 诊疗单（中医四诊记录） | `TreatController` | 可用 |
| 穴位字典（穴名/拼音检索选穴） | `AcupointController` | 可用 |
| 预约（建约 / 排班网格 / 到店接待） | `AppointmentController` | 可用 |
| 反馈（每日总结 / 员工复盘 / 顾客回访） | `ReviewController` | 可用 |
| 卡项 | `ItemController` | 可用 |
| 用户认证（登录 / 注册 / 当前用户） | `UserController` | 可用 |
| 员工与考勤（诊所运营） | `StaffController`、`StaffLeaveController` | 可用（请假已实装） |
| 员工周期班表（员工×星期班次参考） | `StaffShiftController` | 可用 |
| 订单（自助机下单 / 前台建单 / 状态流转） | `OrderController` | 可用 |
| 中药处方（开方 / 代煎与膏方流转 / 实时计价 / 配伍与过敏审方） | `PrescriptionController` | 可用 |
| 病症处方模板 | `PrescriptionTemplateController` | 可用 |
| 外治处方模板（耳穴/贴敷/艾灸/拔罐常用组合） | `TherapyTemplateController` | 可用 |
| 方剂库（方名/拼音检索带出全方） | `FormulaController` | 可用 |
| 药材字典（开方比价取数） | `HerbController` | 可用 |
| 饮片出入库（流水登记 / 余额与效期预警） | `HerbStockController` | 可用 |
| 收费结算与储值（收款 / 充值流水） | `SettlementController`、`RechargeController` | 可用 |
| 次卡（发卡 / 停用 / 核销流水） | `CardController` | 可用 |
| 结算小票套打（80mm HTML，模板可覆盖） | `ReceiptPrintController`、`PrintTemplateController` | 可用 |
| 自助机（免登录下单 / 自助约期） | `KioskController` | 可用 |
| 广告屏与叫号 | `AdsController`、`CallController` | 可用 |
| 前端错误上报 | `TraceController` | 简单日志记录 |

::: warning 阅读接口文档时注意
员工请假已实装（`/api/v1/staff/leave/`，记录口径：不改登录状态、无审批流）。库存出入库、在线支付为规划功能。以本页「现状」列为准，避免按接口名推断可用功能。
:::

## 认证与权限

- 登录走 `POST /api/v1/user/login`，成功后响应体与响应头同时返回 access token（1 天）与 refresh token（7 天），均为 `Bearer` JWT。
- 除登录与接口文档路径外，所有接口都要求携带 `Authorization: Bearer <token>`。
- 角色按员工表的 `role` 字段划分：`role < 10` 视为管理层（返回 `admin` 权限集），其余为普通员工；员工 `status <= 0`（离职）无法登录。
- 权限字符串（`admin:*` / `management:*`）已在代码中定义，但接口层面尚未做方法级鉴权，当前所有登录用户可访问全部接口。
- 首次启动自动创建 `admin / 123` 管理员账号。

## 横切机制

- **审计字段**：所有实体带 `create_time` / `update_time`，由 JPA Auditing 自动维护。
- **缓存**：按 id 的查询走 Caffeine 缓存（默认约 50 分钟过期），更新 / 删除当前不主动失效，存在短窗口脏读，属已知待优化点。
- **分页**：`current` 从 1 起始，统一返回 `{ data, total, pages, success }` 结构。
- **异常**：全局 advice 把参数错误映射 400、认证失败 401、账号锁定 403、其余 500。
- **接口文档**：Knife4j 中文界面，扫描 `com.sinomed.controller`，入口 `/doc.html`。
