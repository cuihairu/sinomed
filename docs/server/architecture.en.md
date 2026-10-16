# Architecture Overview

> 🌐 [中文](./architecture.md) · **This page is in English**

The server (`server/`) is the business hub of the whole system: both the admin console and the mini program access it through REST endpoints under the `/api/v1` prefix.

## Tech Stack

| Component | Choice | Version |
| ---- | ---- | ---- |
| Language / runtime | Java | 21 |
| Application framework | Spring Boot | 4.1.x |
| Data access | Spring Data JPA (Hibernate) | bundled with Boot |
| Database | MySQL | 8.0 |
| Cache | Caffeine (local cache) | 3.3.x |
| Authentication | Spring Security + JWT (jjwt) | 0.13.x |
| API docs | Knife4j (OpenAPI 3, Chinese UI) | 4.5.0 |
| Build | Maven Wrapper | - |

## Layered Structure

The code under the `com.sinomed` package follows a classic layered organization:

| Package | Responsibility |
| ---- | ---- |
| `controller` | REST endpoint layer: parameter reception, validation, and the permission entry point; no business rules |
| `service` / `service.impl` | Business layer: business rules, data assembly, cache annotations |
| `repository` | Data access layer: Spring Data JPA interfaces |
| `entity` | Data model: one-to-one with database tables (ddl-auto creates tables automatically) |
| `vo` | Output view objects (View) |
| `params` | Input parameter objects |
| `config` | Configuration classes: security, cache, JPA, API docs, encryption |
| `security` | JWT filter, issuance / verification, role and permission definitions |
| `exception` | Mapping of global exceptions to HTTP status codes |
| `util` | Utilities: date ranges, attendance calculation, phone number validation |

## Modules and Business Chains

Judging from the endpoints and pages, the system's current main chains are:

```text
顾客建档 → 创建诊疗单（四诊/脉象/取穴/诊断与调理方案） → 复诊调阅历史
卡项维护 → 订单（自助机下单 / 前台建单 → 待接待 → 接单 / 完成 / 取消）
每日报表 ← 每日总结 / 员工复盘 / 顾客回访（到期提醒）
收费结算 ← 订单收款（储值 / 微信 / 支付宝 / 现金 / 次卡抵扣） → 小票套打 / 月度收费报表
员工签到 → 打卡记录 → 今日/按月考勤统计（运营辅助）
```

| Module | Controller | Status |
| ---- | ---------- | ---- |
| Patient (create a patient record / query / update / delete / medical history) | `CustomerController` | Available |
| Treatment record (TCM four examinations record) | `TreatController` | Available |
| Acupoint dictionary (point selection by name/pinyin search) | `AcupointController` | Available |
| Appointments (booking / scheduling grid / check-in) | `AppointmentController` | Available |
| Feedback (daily summary / staff retrospective / patient follow-up) | `ReviewController` | Available |
| Service card | `ItemController` | Available |
| User authentication (login / register / current user) | `UserController` | Available |
| Staff and attendance (clinic operations) | `StaffController`, `StaffLeaveController` | Available (leave implemented) |
| Staff recurring shift table (staff × weekday shift reference) | `StaffShiftController` | Available |
| Orders (Kiosk ordering / front-desk creation / status flow) | `OrderController` | Available |
| Chinese medicine prescriptions (writing / decoction service and paste flows / real-time pricing / compatibility and allergy review) | `PrescriptionController` | Available |
| Syndrome prescription templates | `PrescriptionTemplateController` | Available |
| External therapy prescription templates (common combinations for ear points/application/moxibustion/cupping) | `TherapyTemplateController` | Available |
| Formula library (bring in the whole formula by name/pinyin search) | `FormulaController` | Available |
| Herb dictionary (pricing data for prescription writing) | `HerbController` | Available |
| Processed herb stock-in/stock-out (ledger registration / balance and expiry warnings) | `HerbStockController` | Available |
| Charging/settlement and stored value (payment / top-up ledger) | `SettlementController`, `RechargeController` | Available |
| Payment QR code images (WeChat/Alipay merchant code images) | `PayQrCodeController` | Available (reads `data/payqrcodes/` to serve images, no payment gateway) |
| Visit cards (issue a card / deactivate / write-off ledger) | `CardController` | Available |
| Settlement receipt overprint (80mm HTML, overridable templates) | `ReceiptPrintController`, `PrintTemplateController` | Available |
| Kiosk (unauthenticated ordering / self-service scheduling) | `KioskController` | Available |
| Advertising screen and queue calling | `AdsController`, `CallController` | Available |
| Frontend error reporting | `TraceController` | Simple log recording |

::: warning Note when reading the API documentation
Staff leave is implemented (`/api/v1/staff/leave/`; recording convention: it does not change login status and there is no approval flow). Stock-in/stock-out inventory and online payment are planned features. Treat the "Status" column on this page as authoritative and avoid inferring available features from endpoint names.
:::

## Authentication and Permissions

- Login goes through `POST /api/v1/user/login`; on success both the response body and headers return the access token (1 day) and refresh token (7 days), all `Bearer` JWTs.
- Except for login and API documentation paths, all endpoints require `Authorization: Bearer <token>`.
- Roles are divided by the staff table's `role` field: `role == 0` yields the `admin` permission set, `1-9` is management (the `MANAGER` permission set), `≥10` is ordinary staff (`USER`); staff with `status <= 0` (departed) cannot log in.
- Permission strings (`admin:*` / `management:*`) are defined in code; method-level authorization is partially enabled — the staff management write endpoints (`POST`/`PUT`/`DELETE` `/api/v1/staff`) carry `@PreAuthorize("hasRole('ADMIN')")` and unauthorized attempts return 403; the other endpoints work with just a login.
- The `admin / 123` administrator account is created automatically **the first time someone logs in as admin** (built-in bcrypt password); the demo seed creates only the three staff accounts `gu`/`shen`/`su`.

## Cross-Cutting Mechanisms

- **Audit fields**: all entities carry `create_time` / `update_time` (card_adjusts only `create_time`), maintained automatically by JPA Auditing.
- **Cache**: by-id queries go through the Caffeine cache (about 50 minutes expiry by default); updates/deletes of service cards and treatment records already carry `@CacheEvict` for active invalidation; invalidation is still single-key granular, leaving a short window of stale reads — a known item pending optimization.
- **Pagination**: `current` starts at 1; the uniform response structure is `{ data, total, pages, success }`.
- **Exceptions**: a global advice maps parameter errors to 400, authentication failures to 401, account lockout to 403, and everything else to 500.
- **API docs**: the Knife4j Chinese UI, scanning `com.sinomed.controller`, entry point `/doc.html`.
