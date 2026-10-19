# REST 接口清单

> 🌐 [English](./api.en.md) · **本页为中文**

所有业务接口统一前缀 `/api/v1`，下表路径均以完整路径列出。管理端开发代理把 `/api` 转发到服务端（默认 `127.0.0.1:2347`），生产环境由 Nginx 把 `/api` 转发到服务端。

## 鉴权约定

- **放行路径**：
  - 登录与文档：`/api/v1/user/login`、`/doc.html`、`/swagger-ui.html`、`/swagger-ui/**`、`/v3/api-docs/**`、`/swagger-resources/**`、`/webjars/**`
  - **业务免登录端点（内网设备约定，见对应设计文档）**：
    - `/api/v1/kiosk/**` — 顾客自助机浏览与下单 + 小程序自助约期 + openid 自助绑定（只读 + 下单、自助约期、绑定三个写入口，见 [kiosk.md](/design/kiosk) 与 [app-booking.md](/design/app-booking)）
    - `/api/v1/ads/playlist` — 平板拉取排期（只读 + 屏 code 校验，见 [tablet.md](/design/tablet)）
    - `/api/v1/calls/latest` — 平板轮询叫号（只读 + 屏 code 校验，见 [tablet.md](/design/tablet)）
    - `/api/v1/print/templates` — 打印模板下发（空白版式无业务数据，见 [desktop.md](/design/desktop) D7）
    - `/media/**` — 广告媒体静态资源（Nginx 托管）
  - `/actuator/**` — 运维监控
- 其余接口一律需要 `Authorization: Bearer <JWT>`，未认证返回 401（含 `POST /error` 前端错误上报——未登录时上报会被 401 吞掉，登录后正常）。
- 注意 `POST /api/v1/user/register`（注册）不在放行列表内——首次使用需先有账号。
- 员工管理写接口（`POST`/`PUT`/`DELETE` `/api/v1/staff`）已启用方法级鉴权 `@PreAuthorize("hasRole('ADMIN')")`，非管理员越权返回 403。
- 登录响应头 `Authorization` 返回 access token、`Refresh` 返回 refresh token（均自带 `Bearer ` 前缀）；access 有效期 1 天，refresh 7 天。刷新接口尚未实现，续期需重新登录。

## 用户认证 `/api/v1/user`

| 方法 | 路径 | 功能 | 鉴权 |
| ---- | ---- | ---- | ---- |
| POST | `/api/v1/user/login` | 账号密码登录，返回 token 与权限标识（`admin` / `user`） | 公开 |
| POST | `/api/v1/user/register` | 注册员工账号（用户名 / 密码 2-20 位），直接返回 JWT | 需登录 |
| POST | `/api/v1/user/logout` | 登出（当前仅客户端丢弃 token） | 需登录 |
| GET | `/api/v1/user/current` | 获取当前登录员工信息 | 需登录 |

## 顾客 `/api/v1/customer`

| 方法 | 路径 | 功能 |
| ---- | ---- | ---- |
| GET | `/api/v1/customer/page` | 分页查询（`current` / `pageSize` 必填，可选 `name` / `age` / `phone` / `startTime` / `endTime`；`name` 按包含不区分大小写，`age` / `phone` 精确，可叠加） |
| GET | `/api/v1/customer/{id}` | 按 id 查询 |
| GET | `/api/v1/customer/phone/{phone}` | 按手机号查询 |
| GET | `/api/v1/customer/name/{name}` | 按姓名查询（可多条） |
| POST | `/api/v1/customer/` | 建档（性别必填；手机号可选、唯一、大陆号段格式） |
| PUT | `/api/v1/customer/` | 更新顾客信息 |
| DELETE | `/api/v1/customer/{id}` | 删除顾客 |
| GET | `/api/v1/customer/{id}/history` | 病史列表（过敏史/既往史/护理方案逐条记录，新记录在前；顾客不存在报 400） |
| POST | `/api/v1/customer/{id}/history` | 记病史：body `{ type, content }`，`type` 0 过敏 / 1 既往 / 2 护理方案（美容业态 d 档案扩展，不复用外治处方模板），`content` 非空且 ≤200 字（自动 trim）；顾客不存在/类型无效报 400；过敏审方仍只读 type=0 |
| DELETE | `/api/v1/customer/history/{historyId}` | 删除一条病史记录（不存在报 400；演示环境口径，无留痕） |
| GET | `/api/v1/customer/{id}/tag` | 顾客标签列表（美容业态 d）：肤质/偏好轻量标签，新加的在前；过敏史不在标签里（走病史 type 0） |
| POST | `/api/v1/customer/{id}/tag` | 加标签：body `{ tag }`，≤20 字（自动 trim）；同顾客同标签唯一（重复 400）；顾客不存在报 400 |
| DELETE | `/api/v1/customer/tag/{tagId}` | 删除标签 |
| PUT | `/api/v1/customer/{id}/consent` | **美容敏感信息单独同意**（PIPL 口径，docs/design/salon-extension.md §4，随 d 同批）：body `{ consent: 1/0 }`，记 `beauty_consent` 标志 + 时刻 + 经办员工（登录人）；普通档案更新不抹掉（`updateCustomerEntity` 不触这些列） |

## 诊疗单 `/api/v1/treat`

| 方法 | 路径 | 功能 |
| ---- | ---- | ---- |
| POST | `/api/v1/treat/` | 创建诊疗单（`customerId` 必须指向存在的顾客）；可带 `staffId` 署名接诊医师（选填，回显 `staffName`）；脉象支持左右手寸/关/尺分部字段（`pulseCunLeft`/`pulseGuanLeft`/`pulseChiLeft`/`pulseCunRight`/`pulseGuanRight`/`pulseChiRight`，另有总述 `pulse`/`pulseLeft`/`pulseRight` 与补充 `pulseInfo`） |
| GET | `/api/v1/treat/{id}` | 按 id 查询 |
| GET | `/api/v1/treat/customer/{id}` | 按顾客 id 查询全部诊疗单 |
| GET | `/api/v1/treat/customer/name/{name}` | 按顾客姓名查询（姓名需唯一） |
| GET | `/api/v1/treat/visited?ids=` | 按顾客批量查就诊次数（`ids` 逗号分隔必填，缺失 400）：treats 表按顾客聚合计数，回 `[{ customerId, visits }]`；无诊疗记录的顾客（含不存在的 id）不出现在结果里。排班页「首诊/复诊」角标依据——visits ≥ 1 = 复诊、无记录 = 首诊（treats 无项目维度，按顾客口径；疗程卡标记优先） |
| GET | `/api/v1/treat/history` | 分页查询某顾客的诊疗历史（`customerId` 必填，按创建时间倒序） |
| GET | `/api/v1/treat/fuzzy` | 模糊检索（传 `id` 单查；否则按 `name` / `age` / `phone` 圈定顾客后分页） |
| PUT | `/api/v1/treat/` | 按 id 全量更新接诊单：id/customerId 必填且须存在，字段以请求体为准（不带 `staffId` 即清空署名），回包含顾客姓名/年龄/性别与接诊医师署名（更新后驱逐 findById 缓存） |
| DELETE | `/api/v1/treat/{id}` | 删除诊疗单 |

字段含义见[数据模型 · treats 表](/server/data-model#treats-诊疗单)。针灸处方字段（`acuMethod` 针法 ≤30 字、`retentionMinutes` 留针分钟 1-240 可空、`manipulation` 手法 ≤30 字、`moxaMethod` 灸法 ≤60 字（如 艾条悬灸，每穴 15 分钟）、`acuCourse` 疗程/频次 ≤100 字）随接诊单创建/更新保存，越界 400；空白文本按空处理。见 TreatAcuHttpTest。

## 反馈与复盘 `/api/v1/review`

| 方法 | 路径 | 功能 |
| ---- | ---- | ---- |
| POST | `/api/v1/review/` | 保存每日总结（按当天 0 点 upsert，同日覆盖） |
| GET | `/api/v1/review/day?day=` | 按日期查询每日总结（不存在返回空模板） |
| POST | `/api/v1/review/staff` | 新建员工日复盘（只传 `name` 可自动回填员工 id） |
| POST | `/api/v1/review/customer` | 新建顾客回访记录（只传 `name` 可自动回填顾客 id，`day` 为回访日） |
| GET | `/api/v1/review/staff/list/day?day=` | 按日查询全部员工复盘 |
| GET | `/api/v1/review/customer/list/day?day=` | 按日查询全部顾客回访 |
| GET | `/api/v1/review/customer/list/customer?customerId=` | 按顾客查询全部回访计划（回访日倒序，首条即最近安排；`advice` 为回访原因，供档案页下次回访统计/回访提醒） |
| POST | `/api/v1/review/staff/bulk` | 批量保存员工复盘（逐条写入） |
| POST | `/api/v1/review/customer/bulk` | 批量保存顾客回访 |
| DELETE | `/api/v1/review/staff/{id}` | 删除员工复盘 |
| DELETE | `/api/v1/review/customer/{id}` | 删除顾客回访 |
| GET | `/api/v1/review/{id}` | 按 id 查询每日复盘总结（不存在报 400） |
| DELETE | `/api/v1/review/{id}` | 按 id 删除每日复盘总结（不存在报 400；演示环境口径，无留痕） |

## 卡项 `/api/v1/item`

| 方法 | 路径 | 功能 |
| ---- | ---- | ---- |
| POST | `/api/v1/item/` | 创建卡项（名称唯一、价格、描述；`durationMinutes` 单次时长分钟可空，展示用不参与结算；`category` 类目 0 医馆服务（缺省）/ 1 皮肤管理 / 2 身体护理 / 3 中医美容） |
| PUT | `/api/v1/item/` | 更新卡项 |
| GET | `/api/v1/item/page` | 分页查询（`name` 包含匹配不区分大小写、`price` 精确、`category` 精确，可叠加；不传即不过滤。2026-10-19 收口：`name`/`price` 此前静默忽略。排序 `sort` 升序、`id` 倒序。ItemFilterHttpTest + ItemCategoryHttpTest 锁死） |
| GET | `/api/v1/item/{id}` | 按 id 查询 |
| GET | `/api/v1/item/name/{name}` | 按名称查询（`{name}` 为路径变量；PathLookupHttpTest 锁死） |
| DELETE | `/api/v1/item/{id}` | 删除，返回删除前的卡项 |

## 员工与考勤（诊所运营） `/api/v1/staff`

| 方法 | 路径 | 功能 |
| ---- | ---- | ---- |
| POST | `/api/v1/staff/` | 创建员工（账号唯一、密码 2-20 位、手机号查重；`title` 职务可空，排班页医师名册=在职且职务含「医师」；`famousLevel` 名医等级 0-3，0 普通、1-3 名医，非法值 400） |
| PUT | `/api/v1/staff/` | 更新员工（携带 `password` 则同步改密；`title` 职务同步更新；`famousLevel` 同创建口径，传 null 不改） |
| GET | `/api/v1/staff/page` | 分页查询员工（`name` 可选、按包含不区分大小写；按 `famousLevel` 降序、同等级按创建时间升序） |
| GET | `/api/v1/staff/{id}` | 按 id 查询 |
| GET | `/api/v1/staff/name/{name}` | 按姓名查询（`{name}` 为路径变量；PathLookupHttpTest 锁死） |
| GET | `/api/v1/staff/phone/{phone}` | 按手机号查询（`{phone}` 为路径变量，与按姓名查询互不串；StaffLookupTest + PathLookupHttpTest 锁死） |
| DELETE | `/api/v1/staff/{id}` | 删除员工 |
| POST | `/api/v1/staff/sign` | 签到 / 签退（`signType`：1 上班、0 下班） |
| GET | `/api/v1/staff/timesheet/today` | 本人今日考勤（打卡明细与总时长） |
| GET | `/api/v1/staff/timesheet/month?month=` | 按月全员考勤统计（1-12，仅当年） |
| GET | `/api/v1/staff/timesheet/week?weekDate=` | 周班次对照：`weekDate` 所在周（周一至周日）员工 × 7 天，计划班次（`staff_shifts` 按星期）对照实际打卡（同日最早上班/最晚下班、整小时取整，历史日缺下班卡计 0）；`weekDate` 空取当周，非法格式 400 |
| POST | `/api/v1/staff/leave/` | 提交请假：`staffId` + `leaveType`（0 病假 / 1 事假）+ `reason` + `startTime`/`endTime` 必填，结束不早于起始；回包含联出员工名。记录口径：不改员工登录状态、无审批流 |
| GET | `/api/v1/staff/leave/page` | 请假分页（current、pageSize、staffId 可空），联出员工名，id 倒序 |
| DELETE | `/api/v1/staff/leave/{id}` | 删除请假记录（演示环境口径，无留痕） |

::: tip 考勤口径
时长按整小时统计，不足 1 小时不显示；同日多次上班卡取最早、下班卡取最晚；未打下班卡但已上班的按当前时间计算。详见[管理端](/web)页面说明。
:::

## 员工班表 `/api/v1/shift`（需登录）

周期班表（按周循环）：员工 × 星期（`weekday` 1 周一 … 7 周日）× `HH:mm` 时段，同一员工同一星期仅一条班次。

| 方法 | 路径 | 功能 |
| ---- | ---- | ---- |
| POST | `/api/v1/shift/` | 加排班：`staffId` + `weekday`（1-7）+ `start`/`end`（HH:mm，start < end）必填；员工须存在、同员工同星期唯一（重复 400「员工周X已有班次」），回包含联出员工姓名与星期文案 |
| PUT | `/api/v1/shift/` | 按 id 全量更新；换员工/星期撞已有班次报 400 |
| GET | `/api/v1/shift/page` | 班次分页（current、pageSize、staffId/weekday 可空过滤），按员工、星期升序 |
| GET | `/api/v1/shift/staff/{staffId}` | 某员工整周班次（weekday 升序，考勤/预约侧取数入口） |
| GET | `/api/v1/shift/{id}` | 班次详情（联员工姓名） |
| DELETE | `/api/v1/shift/{id}` | 删除班次（演示环境口径，无留痕） |

::: tip 班表口径
班表只做排班参考，考勤打卡仍以 signs 为准：班表删除/调整不回写任何打卡记录，与签到/请假/预约暂无联动。
:::

## 顾客选服务 Kiosk `/api/v1/kiosk`（kiosk.md K2）

免登录（前台大屏自助入口，见[顾客选服务设计](/design/kiosk)）；浏览只读 + 下单、自助约期、openid 绑定三个写入口。

| 方法 | 路径 | 功能 |
| ---- | ---- | ---- |
| GET | `/api/v1/kiosk/items` | 上架卡项列表（enabled=1，sort+name 升序），只透 id/name/price/cover/description/durationMinutes（时长可空，供小程序预约页展示服务时长，服务端不编造；2026-10-19 补入） |
| POST | `/api/v1/kiosk/orders` | 下单：body `{ phone, name?, itemIds[] }`。手机号须 `1\d{10}`；卡项须存在且上架（先全量校验再建档）；按手机号幂等建档（缺称呼默认「到店客人」），逐项落 `status=0` 订单并取卡项现价快照。返回 `{ customerId, customerName, orders[{id,itemId,itemName,price}], totalFee }` |
| POST | `/api/v1/kiosk/appointments` | 自助约期：body `{ phone, name?, itemId?, startTime }`。时段 `yyyy-MM-dd HH:mm` **整点**、`09:00–17:00` 且晚于当前时刻；卡项可空（到店再定），须存在且上架；频控同手机号当日 `status IN (0,1)` 限 1 条（超出 400「当日已有预约，请到店或致电改约」）；按手机号幂等建档，落 `duration=60`、`status=0`、`staffId=null`、remark「小程序自助」的预约。返回 `{ customerId, customerName, appointmentId, startTime, itemName?, status: 0 }`。口径见[小程序自助约期设计](/design/app-booking) |
| POST | `/api/v1/kiosk/sms-code` | 短信验证码（`sms_verified` 查卡与 `openid_only` 绑定共用，`off` 模式 400）：body `{ phone }`，发 6 位码落 `sms_codes`（5 分钟有效、一次性）。本系统无短信网关，发码为服务端桩——码只落库与日志（`SMS_CODE_DEMO phone=… code=…`），不对外泄露 |
| POST | `/api/v1/kiosk/openid-session` | 小程序登录换 openid（仅 `openid_only` 模式可用，其余模式 400）：body `{ code }`（`wx.login` 临时凭证），服务端经微信 jscode2session 换 openid 返回 `{ openid }`；需配置 `WECHAT_APP_ID`/`WECHAT_APP_SECRET`，未配置时 400 明确拒绝（不静默）；微信返回错误映射 400；每次尝试（含拒绝）落 `card_lookup_logs` 审计；`session_key` 不出服务端 |
| POST | `/api/v1/kiosk/card-lookup` | 手机查卡：body `{ phone?, openid?, smsCode? }`；按 `sinomed.phone-lookup.mode` 走 openid 或短信双因子，命中返回 `{ customerId, customerName, cards: [{ cardId, itemId, itemName, totalTimes, remainingTimes, status, validUntil }] }`；未命中/被拒不返回任何卡项，每次尝试（含拒绝）落 `card_lookup_logs` 审计。口径见[手机查卡设计](/design/kiosk#手机查卡小程序我的卡项2026-10-14-裁定) |
| POST | `/api/v1/kiosk/openid-bind` | openid 自助绑定（仅 `openid_only` 模式可用，其余模式 400；2026-10-15 代决）：body `{ openid, phone, smsCode }`，用一次性短信验证码自证手机号后把 openid 绑到该手机号对应的顾客档案（免前台免桌面）。同档案重复绑定幂等成功；openid 已绑其他档案 400 拒绝（不静默改绑，需前台处理）；缺 openid / 缺手机号或验证码 / 验证码错 / 该手机号无顾客档案均 400。返回 `{ message }`，每次尝试（含拒绝）落 `card_lookup_logs` 审计（result `bind_ok` / `bind_conflict`） |
| POST | `/api/v1/kiosk/my-appointments` | 手机自助查预约（跨设备）：body `{ phone, smsCode }`（仅 `sms_verified` 模式可用，与查卡同一套一次性验证码），返回 `{ appointments: [{ appointmentId, startTime, itemName, status }] }`——该手机号**未过期**（今天 0 点起）的待到店预约（status=0），新约在前，无预约返回空数组；每次尝试（含拒绝）落 `card_lookup_logs` 审计。口径见[手机查卡设计](/design/kiosk#手机查卡小程序我的卡项2026-10-14-裁定)（同一身份查询通道） |
| POST | `/api/v1/kiosk/appointment-cancel` | 手机自助取消预约（2026-10-16 代决，`app-booking.md` 拍板 2 的「取消不核验身份」前提已因双因子落地失效；仅 `sms_verified` 模式可用）：body `{ phone, smsCode, appointmentId }`，验证码一次性（查预约用过的码取消须重新取码，缺预约 id 不烧码）；只允许取消本人 status=0 且未过期的预约（0→9），非本人 / 已受理或已过号 / 已取消 / 已过期 / 未知预约均 400 不静默；取消后该时段按「每时段限约 1 位」自动释放、当日频控随之解除。返回 `{ message }`，每次尝试（含拒绝）落 `card_lookup_logs` 审计（result `cancel_ok` / `missing_appointment` / `not_owner` / `not_cancelable`） |

## 微信公众号 `/api/v1/wx`（第一期骨架，设计稿见 wechat-official-account.md）

| GET | `/api/v1/wx/callback/{accountId}` | 公众号后台服务器配置验证（免登录，`SecurityConfig` 放行；签名 = `SHA1(token,timestamp,nonce 字典序拼接)`）：通过原样返回 `echostr`；验签失败回空 body；模块关闭（`sinomed.official-account.enabled` 缺省 false）/ 账号不存在或停用一律回 `success` 拒处理并落 `wx_message_logs` 拒绝审计（result `disabled` / `account_missing` / `bad_signature`），不静默 |
| POST | `/api/v1/wx/callback/{accountId}` | 消息推送回调（免登录靠验签，仅明文模式；安全模式 AES 第二期）：验签 → 按 `MsgId`（消息）或 `FromUserName:CreateTime:Event`（事件）排重（微信 5s 超时重试 3 次）→ 全量落 `wx_message_logs`（`direction=in/out/reject`）→ 路由：`subscribe`/`unsubscribe` 维护 `wx_fans` 粉丝行（取关保留行不回复）、`text` 按启用规则 `priority` 升序做关键词匹配（exact 全等 / contains 包含），未命中走 `fallback` 兜底，`image` 先拉取 `PicUrl` 落 `wx_materials` 素材台账（`type=temp`，落盘 `sinomed.official-account.material-dir`，单文件上限 `material-max-bytes` 缺省 10MB；同 `(account, media_id)` 跳过重下，下载失败/超限只记 warn 不落台账、回调仍走兜底），`voice` 等其余仅落流水+兜底；被动回复 `text` XML（中英文内容 UTF-8，CDATA 转义），无回复回 `success`（微信 5s 约束）；请求体收原始字节按 UTF-8 解码（微信 Content-Type 常不带 charset） |
| POST | `/api/v1/wx/bind/{accountId}/code` | 粉丝绑定取码（免登录，粉丝 H5 侧；`SecurityConfig` 放行 `/api/v1/wx/bind/**`）：复用 Kiosk 短信通道桩 `sms_codes`（6 位、5 分钟、一次性，无真实短信网关，发码只落库与日志）；body `{ phone }`，手机号格式 `1` 开头 11 位；模块关闭/账号停用 400 且落 `wx_binding_logs` 审计（`disabled`/`account_missing`），不静默 |
| POST | `/api/v1/wx/bind/{accountId}` | 粉丝↔顾客绑定（免登录，双因子自证；设计稿第一期交付物 5）：body `{ openid, phone, smsCode }`——openid 来自 H5 网页授权（授权本身凭据门控，先建后激活），码走上端点；通过写 `wx_fans.customer_id`（粉丝行不存在时补建 `subscribe=1`），每次尝试（含拒绝）落 `wx_binding_logs`：`ok`/`missing_code`/`bad_code`/`used_code`/`expired_code`/`no_customer`/`bound_other`（该粉丝已绑他档，不静默改绑）/`conflict`（该顾客已被其他粉丝绑定，判公众号内部语义、与小程序 `customer.openid` 互不影响）；同档案重复绑定幂等回执；手机号格式错 400 不落审计（非尝试语义，同 Kiosk） |
| POST | `/api/v1/wx/fans/{accountId}/unbind` | 管理端解绑（需登录；设计稿 §4.7）：清 `wx_fans.customer_id`（保留粉丝行）并落审计 `unbind`；无粉丝行/未绑定分别 400 审计 `no_fan`/`not_bound`；顾客取关不自动解绑，换人关注属线下场景走管理端 |
| GET | `/api/v1/wx/message-logs` | 消息流水只读列表（管理端，需登录；第一期交付物 4，不做对话式 UI）：分页 `current`/`pageSize`（1 起），可选过滤 `accountId`/`fromUser`/`direction`（in 入站、out 出站回复、reject 拒绝审计）/`msgType`/`result`（ok/disabled/account_missing/bad_signature/duplicate）/`startTime`/`endTime`（`yyyy-MM-dd[ HH:mm:ss]`，转 Date 比较），AND 语义；默认 `createTime`+`id` 倒序最新在前；返回 `PageResp<WxMessageLogView>`（含 `customerName`：该粉丝已绑定顾客的姓名，设计稿第一期验收「已绑定顾客发消息可见对应顾客」；不含服务端排重字段 dedupeKey）；不依赖模块开关——disabled 期间的 reject 审计也可查 |
| GET | `/api/v1/wx/capabilities/{accountId}` | 账号能力矩阵（需登录）：按 `wx_accounts.account_type` + `verified` 返回 `{ accountId, accountName, accountType, verified, callback, materials, webAuth, menuManagement, customerService, massMessaging, templateMessage }`——模板消息仅服务号；菜单/客服/群发 = 服务号或已认证；管理端据此隐藏降级入口，不写死枚举分支；账号不存在 400 |

## 打印模板 `/api/v1/print`（desktop.md D7）

免登录（空白版式、不含业务数据；桌面工作站壳未登录也要拉模板，见[桌面版设计](/design/desktop)）。

| 方法 | 路径 | 功能 |
| ---- | ---- | ---- |
| GET | `/api/v1/print/templates` | 全部白名单模板（prescription / receipt）与内容摘要版本 `{ version, templates[{ name, content }] }`；版本为内容 SHA-256 前 16 位，内容不变则不变。模板解析：`data/printtemplates/{name}.html`（`PRINT_TEMPLATES_DIR` 可配）覆盖目录优先，缺失回落 jar 内置版式——模板更新只需改服务端文件，不发壳版本 |
| GET | `/api/v1/print/receipt/{settlementId}` | **结算小票套打（需登录）**：按结算单填充 80mm 小票模板，直接返回可打印 HTML（`text/html`）。单号 `S+结算单id`；次卡抵扣（实收 0）金额列显示「次卡抵扣」；抬头机构名可用 `sinomed.receipt.clinic-name` 配置覆盖；顾客/员工名做 HTML 转义。结算单不存在报 400 |

## 订单 `/api/v1/order`（kiosk.md K3，需登录）

> 多品项：订单明细落 `order_item` 表（name/price 为下单时刻快照，qty≥1，subtotal=单价×数量），订单接口统一联出 `items[]`；存量单无明细行时按 `itemId+price` 回退一行数量 1。`orders.price` 语义：单行=行小计，多行=各行小计合计。

| 方法 | 路径 | 功能 |
| ---- | ---- | ---- |
| POST | `/api/v1/order/` | 前台建单：`customerId` 必填（顾客须存在），`staffId` 可选记录建单人；单品传 `itemId`（卡项须存在且启用），多品项传 `items[{itemId,qty}]`（qty≥1，逐行校验，`price`=各行小计合计，`itemId` 记首行）；价格取卡项现价快照，落 `status=0` 待接待 |
| PUT | `/api/v1/order/remark` | 整单备注：body `{id, remark}`，≤200 字，空串/null 清空；超长报 400 |
| GET | `/api/v1/order/page` | 分页（current、pageSize、status 可空、pending 可空 `true`=只看待结算 0/1），按创建时间倒序（同秒按 id 倒序），联出顾客名/手机号、卡项名、明细行 `items` 与结算支付方式 `payType` |
| GET | `/api/v1/order/{id}` | 详情（联顾客名、卡项名与明细行 `items`、备注 `remark`） |
| PUT | `/api/v1/order/status` | 状态流转：`0→1`、`1→2`，`0/1→9` 取消；其余组合报错（body 带 id 与 status） |
| GET | `/api/v1/order/summary` | 按顾客汇总：`customerId` 必填，返回 `{ orders, amount }`（订单数与金额合计） |
| DELETE | `/api/v1/order/{id}` | 删除，仅 `status=9`（已取消）可删，连同明细行 |

## 预约 `/api/v1/appointment`（需登录）

| 方法 | 路径 | 功能 |
| ---- | ---- | ---- |
| POST | `/api/v1/appointment/` | 新建预约：`customerId` + `startTime` 必填，`itemId`/`staffId`/`roomId`/`duration`/`remark` 可选；新预约一律 `status=0` 待到店；**每时段限约 1 位**——同医师同日同小时（floor 到整点桶）已有 `status IN (0,1)` 预约报 400「该时段已约满（每时段限约 1 位），请换医师或时段」，`staffId` 为空（自助约期，到店分配）归同一空组互斥；`roomId` 非空时**工位维同样互斥**（同工位同小时跨技师也拒，报 400「该工位该时段已被占用」；美容业态 b 双维冲突，AppointmentRoomConflictHttpTest 锁死，工位须存在） |
| GET | `/api/v1/appointment/page` | 分页（current、pageSize、status 可空、date 可空 `yyyy-MM-dd` 按日过滤），联出顾客名/手机号、卡项名、员工名、工位名 |
| PUT | `/api/v1/appointment/status` | 状态流转：只允许 `0→1`（到店接待，随后从接诊页建单）、`0→8`（过号）与 `0→9`（取消）；过号/取消为终态，其余组合报错。`0→1` **自动叫号联动**（docs/tablet.md「到号自动叫号」）：接待即落一条 `queue_calls`（号码=当日接待序号补零两位、诊室=接诊医师名+「诊室」/未指派「待分配诊室」、姓名脱敏、广播全部屏），过号/取消不叫号 |
| DELETE | `/api/v1/appointment/{id}` | 删除，仅 `status=9`（已取消）可删 |

::: tip 预约口径
时段为开始时刻 + 时长（分钟）；状态：`0` 待到店、`1` 已接待、`8` 过号、`9` 已取消（8/9 为终态）。每时段限约 1 位（原型 admin-booking 口径）：同医师同日同小时已有待到店/已接待预约即拒，取消/过号后时段自动释放；自助约期（staffId 空）与已指派医师分属不同组互不影响。到店接待后请从接诊页发起接诊，预约状态不随接诊单自动回写。
:::

## 提成 `/api/v1/commission`（需登录，美容业态 c）

| 方法 | 路径 | 功能 |
| ---- | ---- | ---- |
| GET | `/api/v1/commission/rule` | 规则列表（全量按 id 倒序；`itemId` 可选按卡项过滤） |
| POST | `/api/v1/commission/rule` | 新建规则：`itemId` + `title`（岗位，`staffs.title` 精确匹配）+ `ratio`（整数百分比 0–100）必填；同项目同岗位唯一（一岗一比例） |
| PUT | `/api/v1/commission/rule` | 更新规则：`id` 必填，可改 `title`/`ratio`；itemId 建议删了重建 |
| DELETE | `/api/v1/commission/rule/{id}` | 删除规则（硬删；已生成记录不受影响，快照在记录里） |
| GET | `/api/v1/commission/record/page` | 记录分页（current、pageSize，`orderId`/`staffId` 可选过滤、两参同传按 AND 叠加——2026-10-19 收口：此前同传静默忽略 `staffId`），按 id 倒序，联出员工名 |
| GET | `/api/v1/commission/record/summary` | 按员工合计（工资基数）：`staffId`/`start`（含）/`end`（不含）可空；净额 = 正向 + 冲销 |

::: tip 提成口径
P0 固定比例「项目×岗位」（docs/design/salon-extension.md §2-c）：岗位取 `staffs.title`，与排班医师名册同一岗位口径；医师/技师共用本套规则（同表区分），不建并行实体。**记账锚点**：业绩归属 = `orders.staff_id`（建单人），结算完成时按 `order_items` 行小计 × 比例生成记录（行小计/比例/卡项名均为快照）；**按业绩不按实收**——次卡抵扣实收 0 也照记；整数元向下取整。员工无归属（前台单）、员工无岗位、岗位无规则，任一缺失不落记录（0 不建规则即得）。**退单**写等额负向冲销（remark「退单冲销 #结算id」），净额归零；重收后按新结算单重新记账。存量单无 `order_item` 明细行的不记账（明细行随多品项建单落库）。阶梯/多人分佣规则引擎为 P1。
:::

## 工位 `/api/v1/room`（需登录，美容业态 b）

| 方法 | 路径 | 功能 |
| ---- | ---- | ---- |
| GET | `/api/v1/room/` | 全量工位（含停用，按 id 升序）；历史预约仍引用停用工位，故不隐藏 |
| GET | `/api/v1/room/{id}` | 按 id 查询 |
| POST | `/api/v1/room/` | 新建工位：`name` 必填且唯一，`enabled` 缺省 1 在用 |
| PUT | `/api/v1/room/` | 更新工位：改名/停用（`enabled=0`）皆走此接口；停用不影响历史预约，新建预约表单只列在用工位 |
| DELETE | `/api/v1/room/{id}` | 删除，硬删；历史预约联不出工位名时显示为空 |

::: tip 工位口径
工位是预约的资源维度（美容业态 b，docs/design/salon-extension.md §2-b）：美容项目预约占工位，同工位同小时只排一位（技师+工位双维限约）；医馆预约不选工位（`roomId` 空），旧口径不变。与叫号展示文本 `queue_calls.room` 无关——那是叫号屏展示字段，不是可预约资源。
:::

## 储值 `/api/v1/recharge`（需登录）

| 方法 | 路径 | 功能 |
| ---- | ---- | ---- |
| POST | `/api/v1/recharge/` | 储值充值：`customerId` + `money`（>0），顾客须已建档；落一条正数流水 |
| GET | `/api/v1/recharge/balance` | 顾客储值余额：流水合计（`customerId` 必填），无流水为 0 |
| GET | `/api/v1/recharge/page` | 流水分页（current、pageSize、customerId 可空），联出顾客名；`money` 为负的行是储值支付扣减 |

## 收费 `/api/v1/settlement`（需登录）

| 方法 | 路径 | 功能 |
| ---- | ---- | ---- |
| POST | `/api/v1/settlement/` | 收款结算：`orderId` + `payType`（`1` 储值 / `2` 微信 / `3` 支付宝 / `4` 现金 / `5` 次卡抵扣）；订单状态 `0/1 → 2` 已完成；储值支付校验余额并落负数流水，不足报 400；次卡抵扣按订单卡项扣 1 次、实收记 0，无有效余次卡报 400；防重复收款按「最近一张结算单未退款」判断（退费重收后一单可多张结算单）；**结算完成自动落提成记录**（美容业态 c：订单明细行小计 × 规则比例，见下方提成组） |
| GET | `/api/v1/settlement/page` | 结算单分页（current、pageSize），联出顾客名，时间倒序 |
| GET | `/api/v1/settlement/report/month?month=` | 月度收费报表：`month=yyyy-MM` 必填（格式非法/月份不存在报 400）；按结算时间聚合当月结算单——`totalCount` 单数、`totalMoney` 实收合计（元）、`cardCount` 次卡核销单数、`days[]` 逐日单数/实收（只列有结算的日，升序）、`payTypes[]` 支付方式构成（`payType`/`payTypeText`/单数/实收，只列出现过的，升序）；储值充值不计入营收 |
| GET | `/api/v1/settlement/report/trend?months=` | 经营趋势：`months` 1-24 可空（缺省 6，越界报 400）；近 N 月（含当月）逐月聚合实收/结算单数/次卡核销单数与接诊量（`rows[]`：`month`/`money`/`count`/`cardCount`/`treatCount`），无数据月零填充、月份升序 |
| POST | `/api/v1/settlement/{id}/refund` | 退单：`reason` 必填 ≤100 字；一笔结算只能退一次（`refund_logs.settlement_id` 唯一兜底，已退款报 400「该结算已退款」）；退款 = 退整单、按原 `payType` 原路退回——储值补正数流水（note「退单 #结算id」）、次卡作废该订单核销流水并回补余次（金额记 0）、微信/支付宝/现金仅落退款记录；订单 `2 → 1` 已接单可重收；回退款单视图（联顾客/操作人名）；**退单自动冲销该结算单的提成记录**（等额负向，净额归零） |
| GET | `/api/v1/settlement/reconciliation?date=` | 日对账单：`date=yyyy-MM-dd` 必填（缺失/非法报 400）；按自然日聚合收款（单数/实收/`incomePayTypes[]`/次卡核销单列）、充值（笔数/金额单列不计营收，只含真实充值——储值支付扣减归收款侧、退款补回归退款侧）、退款（笔数/金额/`refundPayTypes[]` 按原支付方式 + 明细行）；`netMoney` = 实收 - 退款，`cashNetMoney` = 现金收款 - 现金退款（钱箱核对用） |

::: tip 收费口径
实收金额取订单价格快照（空价格按 0 收）；微信/支付宝为**到店扫码收款**（2026-10-12 拍板）——结算台出示商户收款码（`/api/v1/pay-qrcode/{method}` 出图，缺图 404 页面如实提示），顾客系统外扫码支付、系统只按所选方式记账，不拉起真实收银网关；小票走 `/api/v1/print/receipt/{settlementId}` 服务端套打（80mm HTML，浏览器打印）；次卡抵扣需顾客持本单卡项的有效余次卡（多张按早发的先扣）；**处方饮片行进结算：裁定不做**——收费以卡项订单结算为准，处方计价为试算/提示口径（原型 billing 饮片行为示意）。已结算订单的支付方式从订单分页 `payType` 联出。月度收费报表（`GET /report/month`）只统计结算单——储值充值本身不计入营收，次卡核销实收 0、单列 `cardCount`。退费与日对账已实装（[reconciliation.md](../design/reconciliation.md)）：退款整单原路退回、一单只退一次、理由必填；日对账按自然日三段聚合，充值单列不计营收，现金净额供钱箱核对；不做部分退款/跨日冲正/发票/在线支付真实退款。
:::

## 收款码 `/api/v1/pay-qrcode`（需登录）

> 到店扫码收款出图：读 `{sinomed.pay.qrcodes-dir:data/payqrcodes}/` 目录（同 printtemplates 的 data/ 目录约定，商户放图即生效不用重构建）；纯出图不接支付网关、无回调，钱货系统外完成。收款码是商户资产，未登录不出图。

| 方法 | 路径 | 功能 |
| ---- | ---- | ---- |
| GET | `/api/v1/pay-qrcode/{method}` | 取商户收款码图片：`method` 白名单 `wechat`/`alipay`（之外一律 404，封死路径拼接）；依次找 `{method}.png`/`.jpg`/`.jpeg`，按扩展名给 `image/png`/`image/jpeg`；文件缺失 404——页面据此如实提示未配置，不做假码。img 标签带不上 Bearer，页面用 fetch+blob 取图（同小票补打口径） |

## 次卡 `/api/v1/card`（需登录）

顾客持卡（次数卡/疗程卡——推拿、艾灸等按疗程计次的服务项目即疗程卡口径）：发卡即全量次数，结算按卡项抵扣并落核销流水；`validUntil` 有效期可空=长期有效，过期卡不参与抵扣（口径见 [data-model](/server/data-model)）。

| 方法 | 路径 | 功能 |
| ---- | ---- | ---- |
| POST | `/api/v1/card/` | 发卡：`customerId` + `itemId` + `totalTimes`（≥1）必填，`sourceOrderId` 可空（溯源购卡订单），`validUntil` 可空=长期有效（毫秒数，过期卡不参与抵扣）；余次=总次数、状态有效 |
| GET | `/api/v1/card/list` | 按顾客查持卡（`customerId` 必填），新卡在前，联出顾客名与卡项名 |
| GET | `/api/v1/card/customers?ids=` | 批量按顾客查持卡（`ids` 逗号分隔，可空列表返回 `[]`），联出顾客名与卡项名；排班格位疗程标记等聚合场景一次拉齐，避免逐顾客查询 |
| PUT | `/api/v1/card/{id}/status?status=` | 停用 / 恢复（`1` 有效 / `0` 停用）；停用卡不参与抵扣 |
| GET | `/api/v1/card/{id}/usages` | 按持卡查核销记录（新记录在前）：每次结算抵扣一条，含 `timesUsed`（第几次，1 起）、`orderId`、`staffId/staffName`（服务员工，取订单 staffId）、`createTime`；持卡不存在报 400 |
| POST | `/api/v1/card/{id}/adjust` | 赠次（疗程卡营销）：`times`（1-99）+ `reason`（必填 ≤100 字）必填；有效卡 total/remaining 各加 N，落调整流水；停用卡报 400（先恢复）；过期不拦 |
| POST | `/api/v1/card/{id}/transfer` | 转卡（疗程卡营销）：`customerId`（目标顾客）+ `reason`（必填 ≤100 字）必填；换归属顾客（余次/有效期照旧），落调整流水；停用卡/目标顾客不存在/已是该顾客持卡均报 400 |
| GET | `/api/v1/card/{id}/adjusts` | 按持卡查调整流水（新记录在前）：赠次/转卡留痕，含 `type`（1 赠次/2 转卡）、`times`、`customerId/customerName`（转卡目标）、`reason`、`staffId/staffName`（操作人，取当前登录账号）、`createTime`；持卡不存在报 400 |

抵扣本身不走本组：在结算接口选 `payType=5`，按订单卡项扣 1 次，同一事务落一条核销流水（无单不落——手工划扣不做，核销随结算发生）。

## 处方 `/api/v1/prescription`（需登录）

| 方法 | 路径 | 功能 |
| ---- | ---- | ---- |
| POST | `/api/v1/prescription/price` | 处方试算（不开方只算钱）：body 传 `herbs`（herb/weight）+ `doses`，按药材字典实时计价返回 `{ totalFen, perDoseFen, doses, herbCount, pricedHerbCount, unknownHerbs[] }`；未收录药名不计费 |
| POST | `/api/v1/prescription/compatibility` | 配伍审方：body `{ herbs: ["药名", …] }`，按经典十八反（禁忌）/十九畏（慎用）比对；自由文本药名按别名包含匹配（「法半夏」命中「半夏」）；返回 `{ checked, findings[] }`（`findings` 空即未发现配伍禁忌）；提示不拦截，是否照用由医师判断 |
| POST | `/api/v1/prescription/allergy-check` | 过敏审方：body `{ customerId, herbs: ["药名", …] }`，按顾客过敏史（`customer_histories` type=0）比对，记录原文包含药名（≥2 字，单字如「参」不参与防误报）即命中；返回 `{ customerId, checked, findings[] }`（finding 含 `herb`/`historyId`/`content` 史原文；同味命中多条史各报一条；`findings` 空即未命中）；既往史不参与；未知顾客/空白名单 400；提示不拦截，是否照用由医师判断 |
| POST | `/api/v1/prescription/dose-check` | 剂量审方：body `{ herbs: [{ herb, weight }] }`，按药材字典单剂克数上限（`herbs.dose_max`，可空=不预警）比对单剂克数：`weight` **严格大于**上限才命中（等值不提示）；返回 `{ checked, limited, findings[], unmatched[] }`（`checked` 去空白后参与比对数；`limited` 已设上限条目数，同名多味重复计；finding 含 `herb`/`weight`/`limit`；字典未收录药名进 `unmatched` 无参照；未设上限不参与）；空白名单 400；提示不拦截，是否照用由医师判断 |
| POST | `/api/v1/prescription/` | 开方：`customerId` + `herbs`（至少 1 味：`herb` 药名 + `weight` 剂量克，`special` 特殊煎法/`process` 炮制可选）必填；`treatId`/`staffId`/`reviewStaffId`（药师审核）/`checkStaffId`（调配复核，签名行存档、员工 id 校验）/`doses`（默认 7）/`usage`（煎法）/`takeMethod`（服法）/`remark` 可选；`prescriptionType=1` 为膏方（落「待制作」，`craft` 记收膏方式如 炼蜜/清膏/糖膏/阿胶收膏，不计袋数）；`prescriptionType=0`（默认）且 `decoction=true` 时代煎（袋数=剂数，落「待煎」，`deliveryType` 0 自取 / 1 同城配送 随方存档，不代煎忽略）；返回创建后的处方（含药味） |
| PUT | `/api/v1/prescription/{id}/decoction?status=` | 代煎流转（汤剂专用）：只允许 待煎(1)→可取(2)→已取(3) 顺序推进；未选代煎(0)/回退/跳跃报 400；回包为流转后的完整处方视图。代煎袋数=剂数，服务费=袋数×3 元（300 分/袋）实时算不落库、`decoctionFeeFen` 随视图返回（提示口径，收费仍以卡项订单结算为准） |
| PUT | `/api/v1/prescription/{id}/paste?status=` | 膏方领取流转：只允许 待制作(1)→可取(2)→已取(3) 顺序推进；非膏方（`prescriptionType=0`）报 400「不是膏方」；回退/跳跃报 400「流转无效」；回包为流转后的完整处方视图 |
| POST | `/api/v1/prescription/{id}/dispense` | 药房发药：待发药(0)→已发药(1) 单向，落发药流水（`dispense_logs`）；操作人取当前登录账号（自动带出路径留空）；body 可选 `{ note }`（≤100 字）；已发药再发报 400「处方已发药」；代煎/膏方走「已取」领取时自动置已发药（流水 note 写来源），无需重复点发药 |
| GET | `/api/v1/prescription/page` | 分页（current、pageSize、customerId 可空），联出顾客名、医师名与药味，id 倒序 |
| GET | `/api/v1/prescription/dispense/page` | 药房工作台分页：`dispenseStatus` 可空（0 待发药 / 1 已发药）、`customerKeyword` 按顾客姓名/手机模糊可空，id 倒序；联顾客姓名手机、类型/剂数/药味数与代煎/膏方状态文案 |
| GET | `/api/v1/prescription/{id}` | 处方详情（含按 sort 排序的药味） |
| DELETE | `/api/v1/prescription/{id}` | 删除处方（连同药味；演示环境口径，无留痕） |

## 病症处方模板 `/api/v1/prescription/template`（需登录）

| 方法 | 路径 | 功能 |
| ---- | ---- | ---- |
| POST | `/api/v1/prescription/template/` | 建模板：`name`（病症名，1–20 字，唯一）+ `herbs`（至少 1 味：herb + weight，special 可选）必填；`doses`（默认 7）/`decoction`（0 无需 / 1 代煎，默认 0）/`usage`/`remark`/`enabled`（默认 1）可选 |
| PUT | `/api/v1/prescription/template/` | 按 id 全量更新（药味全量替换）；换名撞其他模板报 400 |
| GET | `/api/v1/prescription/template/enabled` | 上架模板（enabled=1，id 升序，附药味）——开方页「套用模板」取数入口 |
| GET | `/api/v1/prescription/template/page` | 分页（id 倒序，含停用；`name` 模糊过滤可选），联出药味 |
| GET | `/api/v1/prescription/template/{id}` | 模板详情（含按 sort 排序的药味；不存在报 400） |
| DELETE | `/api/v1/prescription/template/{id}` | 删除模板（连同药味；不影响已开处方；演示环境口径，无留痕） |

## 外治处方模板 `/api/v1/therapy-template`（需登录）

耳穴压豆、穴位贴敷、艾灸、拔罐等外治法的常用组合（方法 + 取穴部位 + 疗程），口径同病症模板：只存建议值，接诊页「套用外治模板」回填后随诊疗单自由改（不写回模板），停用不在接诊页出现。

| 方法 | 路径 | 功能 |
| ---- | ---- | ---- |
| POST | `/api/v1/therapy-template/` | 建模板：`name`（模板名，1–20 字，唯一，如「失眠·耳穴压豆」）+ `method`（外治方法，≤10 字）必填；`target`（取穴/部位）/`course`（疗程/频次）/`note`（注意事项）各限 100 字可选；`enabled`（默认 1） |
| PUT | `/api/v1/therapy-template/` | 按 id 全量更新；换名撞其他模板报 400 |
| GET | `/api/v1/therapy-template/enabled` | 上架模板（enabled=1，名称升序）——接诊页「套用外治模板」取数入口 |
| GET | `/api/v1/therapy-template/page` | 分页（id 倒序，含停用；`name` 模糊 / `method` 精确过滤可选） |
| GET | `/api/v1/therapy-template/{id}` | 模板详情（不存在报 400） |
| DELETE | `/api/v1/therapy-template/{id}` | 删除模板（不影响已接诊记录；演示环境口径，无留痕） |

::: tip 处方口径
药材名为自由文本——配伍审方（十八反/十九畏，静态规则）、过敏审方（按顾客过敏史原文匹配）与计价（按药材字典实时试算）已实装；审方提示不拦截，计价无快照、随字典改价同步，未收录药名如实标「未比价」不计费；代煎领取已实装（袋数=剂数、待煎→可取→已取单向流转，无加急/回退）；膏方已实装（`prescriptionType=1`，开方落「待制作」，`craft` 记收膏方式，按料计不走代煎袋数，领取流转 待制作→可取→已取 单向推进，与代煎各自独立互不影响）；药房发药已实装（`dispense_status` 0 待发药/1 已发药 单向 + `dispense_logs` 流水，`POST /{id}/dispense` 与 `GET /dispense/page` 工作台，代煎/膏方「已取」自动置已发药）；病症处方模板已实装（模板名唯一、只存建议值，套用后随处方自由增减不写回模板，停用不出现在开方页）；外治处方模板已实装（`/api/v1/therapy-template`，接诊页套用带出方法/取穴/疗程建议，口径同病症模板）；方剂库已实装（经典方参考，开方页按方名/拼音检索一键带出全方，带出后随处方自由增减不写回方剂库）；库存为规划功能；剂量为单剂克数（可小数），`special` 记录先煎/后下/包煎等煎法。
:::

## 方剂库 `/api/v1/formula`（需登录）

经典方剂参考库（方名唯一，`pinyin` 为全拼小写字母检索码）。与病症模板的分工：方剂只存「方名 + 药味组成 + 出处/功效」文献参考值，不带剂数/代煎等开方建议；开方页「方剂库」按方名或拼音检索带出全方，带出后随处方自由增减、不写回方剂库。

| 方法 | 路径 | 功能 |
| ---- | ---- | ---- |
| POST | `/api/v1/formula/` | 收录方剂：`name`（1–20 字，唯一）+ `pinyin`（全拼小写字母，录入自动转小写）+ `herbs`（至少 1 味：herb + weight，special 可选）必填；`source`（出处）/`indication`（功效主治）可选 |
| PUT | `/api/v1/formula/` | 按 id 全量更新（药味全量替换）；换名撞其他方剂报 400 |
| GET | `/api/v1/formula/page` | 分页（id 倒序；`keyword` 模糊匹配方名**或拼音码**，如「逍遥」或「xiaoyao」），联出药味 |
| GET | `/api/v1/formula/{id}` | 方剂详情（含按 sort 排序的药味；不存在报 400） |
| DELETE | `/api/v1/formula/{id}` | 删除方剂（连同药味；不影响已开处方；演示环境口径，无留痕） |

## 药材字典 `/api/v1/herb`

需登录；处方计价的比价依据。药材名唯一、与处方药名**精确同名匹配**（「炙甘草」不匹配「甘草」）；价格为每克分价（int），计价 = Σ round(分价 × 单剂克数) × 剂数。

| 方法 | 路径 | 功能 |
| ---- | ---- | ---- |
| POST | `/api/v1/herb/` | 收录药材（name 唯一，price 每克分价 >0；重名报「药材已收录」；`stockMin`/`stockMax` 库存上下限可空=不预警） |
| PUT | `/api/v1/herb/` | 更新药材（改价/改名，名称查重不含自身；`stockMin`/`stockMax` 随更新整量覆盖，传 null 即清除） |
| DELETE | `/api/v1/herb/{id}` | 删除药材（演示口径无引用检查，删后相关药味转「未比价」） |
| GET | `/api/v1/herb/page` | 分页（current、pageSize、keyword 名称包含过滤），名称升序 |

## 饮片库存 `/api/v1/herb-stock`（需登录）

只记流水，当前库存 = 入库合计 - 出库合计（克）；出库按效期先进先出（FEFO）消耗批次，批次效期用于近期到期预警。台账口径，不涉采购单据与结算。

| 方法 | 路径 | 功能 |
| ---- | ---- | ---- |
| POST | `/api/v1/herb-stock/` | 登记出入库：`herbId` + `type`（1 入库 / 0 出库）+ `quantity`（克，正整数）必填；入库可带 `expiry`（yyyy-MM-dd，不得早于今天）/`supplier`（≤50 字）/`note`（≤100 字）；出库不得超过当前库存（400「出库数量超过当前库存：N 克」），出库流水不带效期 |
| GET | `/api/v1/herb-stock/page` | 流水分页（current、pageSize、herbId/type 可空过滤），联出药材名，id 倒序 |
| GET | `/api/v1/herb-stock/balance?expiryWithinDays=30` | 库存余额：各药材当前克数 + 最早未消耗批次效期（FEFO）+ 距效期天数 + `warnExpiry`（阈值内到期或已过期，默认 30 天，上限 365）+ `stockMin`/`stockMax`/`warnStock`（药材字典设了库存上下限时，低于下限或高于上限标 `warnStock`）；无流水的药材不成行，名称升序 |
| DELETE | `/api/v1/herb-stock/{id}` | 删除流水（演示口径无留痕，余额按剩余流水重算；流水不存在 400） |

## 穴位字典 `/api/v1/acupoint`（需登录）

经络穴位参考库（穴名唯一，`pinyin` 为全拼小写字母检索码，归经必填）。接诊单取穴字段仍为自由文本——字典只做选穴辅助，接诊页按穴名或拼音检索、选中即追加进取穴文本。

| 方法 | 路径 | 功能 |
| ---- | ---- | ---- |
| POST | `/api/v1/acupoint/` | 收录穴位：`name`（1–10 字，唯一）+ `pinyin`（全拼小写字母，录入自动转小写）+ `meridian`（归经，≤20 字）必填；`location`（体表定位）/`indication`（主治）可选 |
| PUT | `/api/v1/acupoint/` | 按 id 全量更新；换名撞其他穴位报 400 |
| GET | `/api/v1/acupoint/page` | 分页（id 倒序；`keyword` 模糊匹配穴名**或拼音码**，如「足三」或「zusanli」） |
| GET | `/api/v1/acupoint/{id}` | 穴位详情（不存在报 400） |
| DELETE | `/api/v1/acupoint/{id}` | 删除穴位（不影响已开接诊单；演示环境口径，无留痕） |

## 广告投屏 `/api/v1/ads`（tablet.md T1/T2）

管理接口需登录；下发与媒体静态资源免登录（内网屏设备约定，见[平板展示设计](/design/tablet)）。

| 方法 | 路径 | 功能 |
| ---- | ---- | ---- |
| POST/PUT | `/api/v1/ads/materials` | 新建 / 更新素材（name、type 1图2视频、url、durationMs、enabled、sort；`category` 分类（素材子标题，可空）；叠加层文案 kicker 眉题 / title 大标题 / copy 文案 / price 价格行 均可空，四项全空即纯素材播放，随 playlist 下发平板排版层） |
| DELETE | `/api/v1/ads/materials/{id}` | 删除素材 |
| GET | `/api/v1/ads/materials/page` | 素材分页（current、pageSize） |
| POST | `/api/v1/ads/materials/upload` | multipart 上传媒体，落盘 `data/ads/yyyyMM/`，返回 `{ url, originalName, size }` |
| POST/PUT | `/api/v1/ads/schedules` | 新建 / 更新排期（screenId、materialId、`name` 排期名可空（屏幕状态展示）、weekdays `1=周一…7=周日` 逗号分隔可空、start/end `HH:mm` 可空、enabled） |
| DELETE | `/api/v1/ads/schedules/{id}` | 删除排期 |
| GET | `/api/v1/ads/schedules/page` | 排期分页 |
| POST/PUT | `/api/v1/ads/screens` | 新建 / 更新屏（code 唯一、name、location、enabled） |
| DELETE | `/api/v1/ads/screens/{id}` | 删除屏 |
| GET | `/api/v1/ads/screens/page` | 屏分页 |
| GET | `/api/v1/ads/playlist?screen={code}` | 平板下发：心跳更新 `last_seen_at`，按星期/时段命中排期取素材（无排期兜底全部启用素材），返回 `{ version, items[] }`；`version` 为素材+排期的 `max(update_time)` 内容戳。屏 code 不存在报 400 |

媒体文件经 `/media/**` 静态映射到上传目录（`sinomed.ads.upload-dir`，默认 `data/ads`），免登录直读。

## 叫号 `/api/v1/calls`（tablet.md T8）

| 方法 | 路径 | 功能 |
| ---- | ---- | ---- |
| POST | `/api/v1/calls` | 前台叫号（需登录）：number、room 必填，patientMasked 可选，screenId 为空 = 全部屏广播；定向屏需存在且启用 |
| GET | `/api/v1/calls/latest?screen={code}&since={id}` | 平板游标拉取（免登录）：`since` 之后、广播 + 定向本屏的记录按 id 升序，返回 `{ since, calls[] }`；`since` 回传下次调用。屏 code 不存在报 400 |
| GET | `/api/v1/calls/recent` | 管理端最近 20 条（需登录），按 id 倒序 |

## 错误追踪 `/error`

| 方法 | 路径 | 功能 |
| ---- | ---- | ---- |
| POST | `/error` | 接收前端错误上报并写入日志 |

## 通用约定

- **分页请求**：`current`（1 起始）、`pageSize`；**分页响应**：`{ data, total, pages, success }`。
- **路径绑定**：个别接口（按姓名 / 名称查询等）的 `{xxx}` 占位符需出现在 URL 中，实际取值来自同名查询参数。
- **错误响应**：400 参数或业务校验失败、401 未认证、403 账号锁定、500 系统错误，响应体为 `{ message, ... }`。
- **在线调试**：服务端启动后打开 <http://127.0.0.1:2347/doc.html>（Knife4j 中文界面）可直接调试。
