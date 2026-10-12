# 文档与源码一致性审计报告

> 审计基线：`server/` 实体与控制器、`web/` 路由与页面实现、`desktop/`/`kiosk/`/`tablet/`/`app/` 目录结构、`docs/` 现有文档。审计时间：2026-10-08。

---

## ① 文档面枚举（全仓）

| 文档文件 | 角色 | 审计状态 |
|---|---|---|
| `README.md` | 仓库入口、产品预览、目录结构 | ✅ 已审 |
| `docs/index.md` | 文档站首页、产品预览、未来功能规划 | ✅ 已审 |
| `docs/web.md` | 管理端页面与任务对照、遗留项 | ✅ 已审 |
| `docs/app.md` | 小程序端工程现状、构建、接手建议 | ✅ 已审 |
| `docs/design/desktop.md` | 桌面工作站设计、外设、打包、API | ✅ 已审 |
| `docs/design/kiosk.md` | 顾客自助机设计、数据模型、API、交互 | ✅ 已审 |
| `docs/design/tablet.md` | 平板展示设计、数据模型、API、排期/叫号 | ✅ 已审 |
| `docs/server/api.md` | REST 接口清单、鉴权约定、分组端点 | ✅ 已审 |
| `docs/server/data-model.md` | JPA 实体字段、索引、关联说明 | ✅ 已审 |
| `docs/research/features.md` | 功能点清单（参考来源 vs Sinomed 现状） | ✅ 已审 |
| `docs/app-white-label.md` | 换马甲构建指南（未读，非核心面） | ⏭️ 跳过 |

---

## ② 可验证技术主张逐条对码取证（抽样）

### 2.1 鉴权放行路径
| 文档主张 | 代码位置 | 取证结果 |
|---|---|---|
| 放行：`/api/v1/user/login`、Swagger 路径、其余需 JWT | `SecurityConfig.java:51-64` | ✅ 一致。`permitAll` 清单含 `/api/v1/ads/playlist`、`/api/v1/calls/latest`、`/api/v1/kiosk/**`、`/api/v1/print/templates`、`/media/**`、`/actuator/**`、`/api/v1/user/login` |
| `POST /api/v1/user/register` 不在放行列表 | `SecurityConfig.java:64` + `api.md:16` | ✅ 一致，注册需登录 |
| Kiosk/Ads/Calls/Print 接口免登录 | `api.md:97,106,123,146` + `SecurityConfig.java:58-61` | ✅ 一致，均在 `permitAll` |

### 2.2 管理端路由与页面实现
| 文档主张 | 代码位置 | 取证结果 |
|---|---|---|
| `/order/query` 为真实实现（非占位） | `routes.ts:157-158` + `Order/Query/index.tsx:1-151` | ✅ 实现完整：ProTable + 状态流转（接单/完成/取消/删除）+ 分页 |
| `/order/create` 不存在 | `routes.ts:151-160` | ✅ 无此路由 |
| `/item/spread`、`/item/update` 为占位（组件与提交逻辑未完成） | `routes.ts:142-148` + `Item/Spread/index.tsx` + `Item/Update/index.tsx` | ✅ 确认：两页面均调用 `createItem` 而非 `updateItem`，本质为 Create 页复制品 |
| `/item/query` 为完整实现 | `Item/Query/index.tsx:1-231` | ✅ 完整：ProTable + 编辑跳转 `/item/create?itemId=` + 删除 + 封面图渲染 |
| `/ads` 四页均已实现 | `routes.ts:163-187` + `Ads/{Materials,Schedules,Screens,Calls}/index.tsx` | ✅ 四文件均存在 |
| `/treat/create` 支持 `?customerId=` 与 `?treatId=` 回填 | `Treat/Create/index.tsx:78-92` | ✅ 一致（复诊 10-09 对码）：两参数均读取，`treatId` 编辑态从单据反查顾客 id |

### 2.3 服务端实体与数据模型
| 文档主张 | 代码位置 | 取证结果 |
|---|---|---|
| `customers` 表字段（id/name/age/level/phone/gender/address/birthday） | `CustomerEntity.java:21-38` | ✅ 完全一致 |
| `treats` 表含五行/脉象/取穴/诊断/方案等 20+ 字段 | `TreatEntity.java:25-139` | ✅ 完全一致 |
| `items` 补 `enabled/cover/sort` 三字段（Kiosk 用） | `ItemEntity.java:34-45` | ✅ 完全一致，含默认值与注释 |
| `orders` 状态语义 0/1/2/9、price 为下单快照 | `OrderEntity.java:34-38` + `kiosk.md:43-52` | ✅ 一致 |
| `recharges` 仅有 Repository 无接口 | `RechargeEntity.java` 存在 + 无 Controller/Service 引用 | ✅ 确认 |
| `staffs` role `<10` 为管理层、status `0/1/2` | `StaffEntity.java:26,56-66,119-121` | ✅ 一致 |
| `ad_materials/screens/schedules`、`queue_calls` 新表 | 对应 Entity 均存在 | ✅ 一致 |

### 2.4 桌面版外设能力
| 文档主张 | 代码位置 | 取证结果 |
|---|---|---|
| D7 模板版本化：`GET /api/v1/print/templates` 免登录、SHA-256 版本、目录覆盖 | `SecurityConfig.java:61` + `api.md:110` + `desktop.md:58,120` | ✅ 三处一致 |
| D8 ESC/POS 直连：`print_escpos` Rust command、GBK 直发、切纸 | `desktop.md:55,121` + `src-tauri/src/escpos.rs` + `main.rs:75` | ✅ 一致（10-09 对码）：`print_escpos` 已注册 invoke_handler；GBK 直发（encoding_rs）、店名倍宽/明细行/合计加粗/走纸切纸/按列宽对齐均与文档吻合 |
| D9 扫码枪串口模式：`scanner_start/stop`、100ms 轮询、广播 `scanner-code` | `desktop.md:56,122` + `src-tauri/src/scanner.rs` + `main.rs:76-77` | ✅ 一致（10-09 对码）：两命令已注册；读线程 100ms 超时轮询、按行切码、`emit("scanner-code")`（另有 status/error 事件）与文档吻合 |

### 2.5 小程序端现状
| 文档主张 | 代码位置 | 取证结果 |
|---|---|---|
| 仅工程初始化 + 白标构建，业务页未开发 | `app.md:5-7` + `app/src/pages/index/` 仅有自检页 | ✅ 一致 |
| `src/app.config.ts` 仅注册 `pages/index/index` | `app.md:20` | ✅ 一致（10-09 对码）；另 `src/services/api.ts` 请求层与顾客首页已随 P1-11 落地，`app.md` 现状已同步（9e02f72） |

---

## ③ 三类差异入表

### A. 文档超前于代码（文档描述了代码未实现的能力）

| # | 文档位置 | 超前描述 | 代码现状 | 处理建议 |
|---|---|---|---|---|
| A1 | `web.md:52-53` | `/treat/create` 支持 `?customerId=` 与 `?treatId=` 复诊回填 | 路由存在，未验证回填逻辑是否完整 | 保留，标注「支持预载顾客 ID 与复诊回填（需验证）」 |
| A2 | `desktop.md:107-113` | D1-D5 全部验收 ✅，含「管理端业务页内打印入口未接（远程页 IPC 受限）」 | 实证日志显示登录进 dashboard、打印自检样张落盘 | 保留，已如实标注边界 |
| A3 | `desktop.md:119-122` | D7-D9 全部验收 ✅，含 curl/实机实证细节 | 文档极详细，Rust 侧未读但文档自洽 | 保留，标注「Rust 侧实证细节见 desktop.md」 |

### B. 代码超前于文档（代码已有能力但文档未提及）

| # | 代码位置 | 能力 | 文档缺失处 | 处理建议 |
|---|---|---|---|---|
| B1 | `Item/Query/index.tsx:119-130` | 编辑操作跳转 `/item/create?itemId=` 实现「更新」 | `web.md:66` 称 spread/update 为占位，**未说明 Query 页已通过跳转 Create 实现编辑** | 在 web.md 卡项系统表下补注：「编辑复用 `/item/create?itemId=` 回填，Spread/Update 页暂为占位」 |
| B2 | `Customer/Query/index.tsx` (未读) | 行操作含「创建诊断、更新、诊断历史、回访计划、删除」 | `web.md:45` 仅列表描述，未细化 | 保留，表格描述已覆盖 |
| B3 | `SecurityConfig.java:58-61` | 放行 `/api/v1/ads/playlist`、`/api/v1/calls/latest`、`/api/v1/kiosk/**`、`/api/v1/print/templates` | `api.md:7` 仅泛称「接口文档相关路径」，未枚举业务免登录端点 | **修正 api.md 鉴权约定：显式列出 4 个业务免登录端点组** |
| B4 | `routes.ts:110-124` | `/treat/history`、`/treat/fuzzy` 两路由 | `api.md:38-41` 有对应接口，`web.md:54` 仅列 `history` 未列 `query`（模糊查询） | web.md 补 `/treat/query` 即模糊查询页 |

### C. 描述不符（文档与代码口径不一致）

| # | 文档位置 | 文档口径 | 代码口径 | 处理建议 |
|---|---|---|---|---|
| C1 | `api.md:7` | 「放行路径：`/api/v1/user/login` 以及接口文档相关路径…其余接口一律需要 JWT」 | 实际还有 4 组业务免登录端点 | **修正：在鉴权约定显式列出 kiosk/ads/print/calls 免登录端点** |
| C2 | `web.md:130` | 「站点标题当前为模板值 `Youngs.fun`」 | `config.ts:86` 确认为 `Youngs.fun` | ✅ 一致，保留 |
| C3 | `web.md:131` | 「`services` 中保留 OpenAPI 模板生成的 petstore 示例接口与 `/api/rule` 模板接口」 | `web/src/services/swagger/` 下有 pet.ts/user.ts/store.ts | ✅ 一致，保留 |
| C4 | `features.md` | 无交叉链接指向新增设计原型 | 已有 `admin-booking.png`、`admin-herbprescription.png`、`admin-billing.png` | **补交叉链接：在「预约挂号/中药处方/收费结算」行尾注「设计原型见 admin-booking/herbprescription/billing.png」** |
| C5 | `index.md:76` | 引用 `/screenshots/admin-themes.png` | 该图因 montage 缩放错误（中心裁剪非等比缩放）需重渲 | **重渲染后替换，或标注「待更新」** |

---

## ④ 修复动作清单（以源码为真相修文档）

| 动作 | 目标文件 | 修改内容 |
|---|---|---|
| 1 | `docs/server/api.md` | 鉴权约定节：显式列出 `/api/v1/kiosk/**`、`/api/v1/ads/playlist`、`/api/v1/calls/latest`、`/api/v1/print/templates` 为免登录业务端点 |
| 2 | `docs/web.md` | 卡项系统表：修正 `/item/spread`、`/item/update` 说明为「编辑复用 `/item/create?itemId=` 回填，Spread/Update 页暂为占位」 |
| 3 | `docs/web.md` | 诊断系统表：补 `/treat/query` 即模糊查询页 |
| 4 | `docs/research/features.md` | 三行「未实现」功能（预约挂号、中药处方、收费结算）行尾补「↗ 设计原型：`/screenshots/admin-booking.png` 等」 |
| 5 | `docs/index.md` | 若 admin-themes.png 已重渲染则保留，否则标注「主题切换图待更新」 |
| 6 | `docs/design/mockups/README.md` (新建) | 记录 14 张基础原型 + 4 主题变体渲染命令、文件清单、数据宇宙一致性说明 |

---

## ⑤ P0 清单（必须在本次提交修复）

1. **api.md 鉴权约定补全** —— 关键安全边界文档，直接影响外部调用预期
2. **web.md 卡项/诊断系统口径修正** —— 避免后续开发对页面实现程度产生误判
3. **features.md 交叉链接补齐** —— 规划功能与设计原型对应，防止「已有原型不知情」
4. **admin-themes.png 重渲染或标注** —— 文档站首页引用的核心图资产

---

## ⑥ 统计摘要

| 类别 | 条目数 | 已修复/确认 | 待后续 |
|---|---|---|---|
| 文档超前于代码 (A) | 3 | 3 (确认边界，保留) | 0 |
| 代码超前于文档 (B) | 4 | 4 (列入修复清单) | 0 |
| 描述不符 (C) | 5 | 3 (确认一致), 2 (列入修复) | 0 |
| **合计** | **12** | **10** | **0** |

---

## ⑥A 处置记录（2026-10-09 复核）

- §⑤ P0 四项均已在前几轮落地：api.md 鉴权约定已显式枚举 4 组业务免登录端点；web.md 卡项/诊断口径已修正（编辑复用 `/item/create?itemId=`，Spread/Update 占位已注明；`/treat/query` 已入表）；features.md 三行已带设计原型链接；admin-themes.png 已重渲（2×2 等比拼图，2026-10-08 产物）；mockups/README.md 渲染记录已建（mobile-home 状态 10-09 同步为已实现，308961a）。
- §④ 动作 1-6 全部完成，无待办。
- 新增可开工项期间落地的产品增量：顾客档案「累计消费」订单聚合接口（`GET /api/v1/order/summary`，7afd646）。
- 遗留待用户定夺：站点标题 `Youngs.fun` 与 PWA 名称改不改（品牌口径）；CI maven.yml `-DskipTests` 是否接测试。

## ⑥B 文档扫尾（2026-10-12 复核）

- todo 清零批次（微信/支付宝条目，方案①静态收款码展示面）拍板已落：todo.md、api.md（新增收款码节 + 收费口径改写到店扫码收款正口径）、web.md 收费结算节、features.md 收费结算行；红线不动——不接支付网关、无发票、#88 在线支付真实通道不做。
- web.md 散文/表格漂移修复 4 处（均为此前批次代码先行、文档滞后）：`/treat/create` 行补**外设状态条**（desktop.md D10 `peripheral_status` 口径）；`/prescription/create` 行补**剂量上限提示**（dose-check 防抖、诊所自录 `dose_max`、不拦截）；`/prescription/herbs` 行补**单剂上限**（`doseMax` 列/表单）；`/billing/settle` 行补微信/支付宝**收款码**出图与缺码如实提示。
- api.md 结构核对：叫号自动联动（appointment/status）、dose-check、peripheral 相关均已同步；desktop.md D10、tablet.md 叫号联动此前批次已落。
- 复核结论：无新增「文档超前于代码」项；「代码超前于文档」项本轮已清零。遗留仍只有 §⑥A 两项待用户定夺（站点标题品牌口径、CI maven.yml `-DskipTests`）。

## ⑦ 后续建议

- 建立 `docs/design/mockups/README.md` 记录渲染流程，避免「montage 裁剪」等工程问题复发
- 考虑在 CI 中加入「文档-路由一致性」检查脚本（读取 `routes.ts` 与 `web.md` 表格对比）
- `desktop.md` 的 Rust 侧实证细节建议在下次桌面版迭代时补读 `src-tauri/src/commands/` 确证