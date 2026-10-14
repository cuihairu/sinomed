# 顾客选服务设计：到店 Kiosk（浏览 / 选卡项 / 下单）

> 状态：**已实施（K1–K6）**。`kiosk/` 端（浏览网格 / 下单 / 成功回执 / 空闲清篮）、服务端 kiosk 接口与订单管理均已落地并逐项实证；与候诊区展示屏（[平板展示](/design/tablet)）是两台设备两个工程——展示屏只播不录，本机是顾客自助录入入口。

界面原型（选服务下单页，设计稿 · 数据为虚构演示）：

![门店自助机 · 选服务（界面原型）](/screenshots/kiosk-menu.png)

## 定位与场景

`kiosk/` 是摆在**前台/大堂的顾客自助 iPad 网页**：到店顾客在大屏上浏览服务项目，自己勾选想做的卡项，留手机号提交，前台在管理端接单。三步：

1. **浏览**：大字网格展示启用中的卡项（封面 + 名称 + 价格），触摸友好；
2. **选服务**：点选加入购物篮，可多选、可增删；
3. **下单**：填手机号（可填称呼）提交，屏幕回执单号与合计；前台在管理端「订单管理」看到新单并接待。

不做支付（无任何收款代码，下单即到店待接待）；不做会员价/优惠计算（订单金额按下单时刻卡项价格快照）。

```text
顾客（kiosk/ 大屏点选） ──► server kiosk 接口（浏览只读 + 下单）
                                │
                  前台（web dash 订单管理）◄── 新单状态流转（接单 → 完成 / 取消）
```

## 数据模型（复用既有表，新增字段）

### items — 卡项补三个字段

现有卡项表只有 `name / price / description`，Kiosk 浏览需要展示与上架控制，补：

| 字段 | 类型 | 含义 |
| ---- | ---- | ---- |
| enabled | Integer | 1 上架、0 下架（Kiosk 只展示上架项；存量行默认 1） |
| cover | String | 封面图相对 url（可空；复用现有媒体上传与 `/media` 托管） |
| sort | Integer | 展示顺序，小者在前 |

`ddl-auto: update` 自动加列，不影响存量数据。管理端卡项页（已有）补这三个字段的编辑。

### orders — 状态语义定档

表已存在（`user_id / item_id / staff_id / status / price`），此前接口全是空壳。现在把状态定档并写死：

| status | 含义 |
| ------ | ---- |
| 0 | 已下单（Kiosk 提交后的初始态） |
| 1 | 已确认（前台接待中） |
| 2 | 已完成 |
| 9 | 已取消 |

- `user_id` 存顾客 id（`customers` 表，业务外键）；`item_id` 存卡项 id；`staff_id` 接单时可填（Kiosk 单初始为空）；
- `price` 是**下单时刻的卡项价格快照**（Integer，分为单位与卡项表一致——卡项 price 当前即按元存整数，如实沿用）；
- 一个卡项一条订单（多选 = 多条订单，同一次提交共享同一顾客），不加购物车表。

### 顾客关联

`customers.phone` 唯一。Kiosk 下单按手机号查顾客，查无则建档：`name` 取称呼输入（不填默认「到店客人」），`phone` 必填且要求 11 位手机号格式。

## API 面（服务端新增 `/api/v1/kiosk`，免登录）

| 方法 | 路径 | 说明 |
| ---- | ---- | ---- |
| GET | `/api/v1/kiosk/items` | Kiosk 浏览：`enabled=1` 的卡项按 `sort,name` 升序，返回 `id / name / price / cover / description`（不含审计字段） |
| POST | `/api/v1/kiosk/orders` | Kiosk 下单：`{ phone, name?, itemIds: [卡项id…] }`；校验手机号格式与卡项存在且上架；按手机号找/建顾客；逐项写订单（status=0、price=卡项现价）；返回 `{ customerId, customerName, orders: [{ id, itemId, itemName, price }], totalFee }` |

免登录边界（如实）：Kiosk 是内网设备，浏览接口纯只读；下单接口是唯一写入口，写范围限定「一条顾客档案（按手机号幂等）+ N 条 status=0 订单」，不暴露任何查询/删除能力。如暴露公网需另加验证码或屏 token。

管理端订单接口：复用 `OrderService`（save/findById 已有），新增列表查询与状态流转，走**需登录**的 `/api/v1/order` 组：

| 方法 | 路径 | 说明 |
| ---- | ---- | ---- |
| GET | `/api/v1/order/page` | 分页（current、pageSize、status 可选），联出顾客姓名与卡项名 |
| PUT | `/api/v1/order/status` | `{ id, status }`，只允许 0→1→2 正向流转与 0/1→9 取消 |
| DELETE | `/api/v1/order/{id}` | 沿用既有删除（仅 9 已取消可删） |

现有 `POST/PUT /api/v1/order/`、`GET /api/v1/order/{id}` 等空壳接口一并替换为真实实现（OrderView 字段与实体对齐：customerId/itemId/staffId/status/price，去掉实体里不存在的 extOrderId/payType/payment）。

### 手机查卡（小程序「我的卡项」，2026-10-14 裁定）

隐私红线：**不做裸手机号查卡**——无身份绑定即拒绝。身份绑定两条路径，按后台配置 `sinomed.phone-lookup.mode` 三态开关（env `PHONE_LOOKUP_MODE`）选择，**默认 `off`（关闭）**：

| mode | 行为 |
| ---- | ---- |
| `off` | 默认。`card-lookup` 与 `sms-code` 一律 400 拒绝，小程序页面透出「在线查卡已关闭，请到店前台或致电门店查询」 |
| `openid_only` | 登录态天然带 openid，按 `customers.openid` 查绑定顾客；缺 openid / 未绑定均拒绝，**不回退手机号** |
| `sms_verified` | 手机号 + 短信验证码双因子：先 `sms-code` 发码，再带 `smsCode` 查卡；验证码 5 分钟有效、一次性，错/过期/已用均拒绝 |

| 方法 | 路径 | 说明 |
| ---- | ---- | ---- |
| POST | `/api/v1/kiosk/sms-code` | `{ phone }`；仅 `sms_verified` 模式可用，发 6 位码落 `sms_codes`（5 分钟有效、一次性）。本系统无短信网关，发码为服务端桩：码只落库与日志（`SMS_CODE_DEMO phone=… code=…`），不对外泄露 |
| POST | `/api/v1/kiosk/card-lookup` | `{ phone?, openid?, smsCode? }`；按 mode 走 openid 或双因子，命中顾客返回 `{ customerId, customerName, cards: [{ cardId, itemId, itemName, totalTimes, remainingTimes, status, validUntil }] }`（新卡在前，含停用与过期卡由前端标态）；未命中/被拒不返回任何卡项 |

**审计**：每次尝试（成功与拒绝）都落 `card_lookup_logs` 一条——查了谁的卡（`phone` / `customer_id`）、谁查的（`operator`：openid 路径=openid，短信路径=手机号）、什么模式（`mode`）、结果（`result`：`ok / disabled / missing_openid / no_binding / missing_code / bad_code / expired_code / used_code / no_customer`）、时间（`create_time`）。两条路径同口径，off 模式的拒绝也留痕。

小程序侧（`app/src/pages/card`）：手机号 + 验证码表单，服务端 400 的 `errorMessage` 原样透出；命中后列持卡与余次。openid 路径服务端已就绪，小程序当前无登录态故只走短信路径；接入登录态后由页面带上 openid 即可。

## 交互链路

```text
Kiosk 首页（卡项网格，30s 无操作自动清篮回首页）
  → 点选卡项 → 底部购物篮（数量合并、可删）
  → 「去下单」→ 手机号 + 称呼（可选）→ 提交
  → 成功页（单号列表 + 合计金额）→ 10s 自动回首页

前台 dash「订单管理」：新单列表（状态角标）
  → 接单（0→1）→ 服务完成（1→2）；未接待可取消（→9）
```

## 目录落位

```text
kiosk/                      # monorepo 新增目录（与 tablet/ 平级，同一套 Vite + React + TS 约定）
├── src/
│   ├── net/api.ts          # items 拉取与下单提交
│   ├── menu/               # 卡项网格与购物篮
│   └── App.tsx             # 首页 / 下单 / 成功三态切换、空闲超时回首页
├── index.html              # 触摸大字布局基准（768–1080 宽竖屏为主）
├── package.json            # name: sinomed-kiosk（Node.js 24 / pnpm）
└── .nvmrc

web/src/pages/Order/        # dash：订单管理（替换现存的顾客列表复制品页面）
server/ …/controller/KioskController.java、OrderController.java（真实实现）
```

部署落位：Nginx 增加 `/kiosk/`（`kiosk/dist`）；卡项封面沿用 `/media/` 托管，无需新增静态段。

## todo 原子项

| # | 事项 | 验收 | 状态 |
| ---- | ---- | ---- | ---- |
| K1 | items 补 `enabled / cover / sort` 三字段并透出到 VO；dash 卡项页补编辑 | dash 可上架/下架、传封面、排序 | ✅ 存量行默认上架、表单三字段浏览器实证 |
| K2 | server KioskController：`GET /kiosk/items` + `POST /kiosk/orders`（免登录、手机号建档幂等） | curl 浏览只读、下单落库、重复手机号不重复建档 | ✅ 下架项排除、双单合计与同号归同顾客 curl 实证 |
| K3 | dash 订单管理页：分页列表（联顾客/卡项名）+ 状态流转，替换空壳 OrderController | kiosk 下的单在 dash 可见、可接单/完成/取消 | ✅ 7 项 curl + 浏览器接单流转实证 |
| K4 | `kiosk/` 脚手架 + 卡项网格浏览页 | 浏览器打开即见上架卡项大字网格 | ✅ 竖屏实证：上架 2 项、下架排除、点选汇总 |
| K5 | kiosk 购物篮 + 下单 + 成功回执 + 空闲回首页 | 全流程触摸走通，重复手机号二单归同一顾客 | ✅ 手机号校验/回执/10s 自动返回/30s 清篮，二单 customerId 相同 |
| K6 | 端到端验收 + 文档同步（data-model / api / deploy / index） | 设计稿改已实施，docs build 过 | ✅ 文档四件套同步，docs build 过 |
