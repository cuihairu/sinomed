# 美容业态扩展：模型核对与分期方向（salon 并入）

> 🌐 [English](./salon-extension.en.md) · **本页为中文**

> 状态：**方向记录（未排期）**。本页是 2026-10-18 用户令「salon 仓并入本仓」后的实施前模型核对——把美容四方向逐条映射到现有医馆模型，确认哪些复用、哪些新增，**红线是不产生两套并行的实体设计**。需求参考：[美容业态调研](/research/beauty-salon-feature-survey.md)（九类功能 + MVP 建议）。

---

## 1. 背景与定位

- 2026-10-18 用户令：salon 仓并入本仓，不再单独开发（todo.md「美容业态扩展」条）。
- 定位：**美容护肤 + 中医美容，不含美发**；与现有医馆业态共用**建档 / 卡项 / 回访**核心。
- 监管边界（调研 §8.5）：本系统按**生活美容**口径建项目库，不提供医美病历/诊疗功能；中医美容依托医馆已有中医诊疗语境。

## 2. 核对结论总表

| # | 账本方向 | 复用（现有模型） | 新增 | 依据（file:line） |
| --- | --- | --- | --- | --- |
| a | 项目类型扩展（皮肤管理/身体护理/中医美容项目库） | `items` 表整体复用（name/price/description/**duration_minutes**/enabled/cover/sort） | `items` 加**类目**维度（先轻量枚举字段，类目多后再拆字典表） | `ItemEntity.java` 无类目字段（全实体 grep category 零命中）；时长字段已具备，预约占位与收银无需改 |
| b | 预约资源模型（工位/技师排班） | 技师 = `staffs`（role/title/famous_level 已可表达岗位与等级）；排班 = `staff_shifts`（weekday+start/end 周模板）+ `signs` 打卡 | **工位/房间实体**（现全仓无资源级房间模型——`QueueCallEntity.java:38` 的 `room` 只是叫号展示文本）+ `appointments.room_id`；冲突检测从「技师单维」扩为「技师+工位双维」 | `AppointmentEntity.java` 现有 customer_id/item_id/**staff_id**/start_time/duration/status，无房间维度 |
| c | 技师提成规则（区别于医师） | 业绩归属已有挂点：`orders.staff_id` + `order_items` 多行明细（含名称/价格快照） | `commission_rules`（P0 固定比例：项目×岗位）+ `commission_records`（结账生成、可汇总）；医师/技师**同表**用岗位区分，不建两套 | 全服务端源码 grep 提成/commission **零命中**——纯新增，无并行冲突 |
| d | 美容客群标签（肤质/过敏史/护理方案） | **过敏史复用 `customer_histories`**（type 0 过敏 / 1 既往，自由文本，`CustomerHistoryEntity.java:20`） | 肤质/偏好 = 轻量客户标签；护理方案 = 客户档案扩展（**不复用** `TherapyTemplateEntity`——那是外治处方模板：方法/取穴/疗程，属医嘱语境） | `CustomerEntity.java` 现无标签与肤质字段 |

> 进度：**a、b、c 已实施**。a（2026-10-18 已代决开工）：`items.category` 列（integer default 0，ddl-auto 加列即迁移）+ 创建/更新/分页类目过滤 + 管理端类目选择与列表标注，`ItemCategoryHttpTest` 4 测试锁死。b（2026-10-18 巡检开工）：`rooms` 工位表 + `appointments.room_id`（可空）+ 技师/工位双维时段互斥（`ensureSlotFree` 加工位桶计数）+ 管理端工位管理页与建约工位选择，`RoomHttpTest` 3 + `AppointmentRoomConflictHttpTest` 5 测试锁死；叫号 `roomOf()` 仍为员工名+「诊室」展示文本，不动。c（2026-10-18 巡检开工）：`commission_rules`（P0 固定比例「项目×岗位」，一岗一比例）+ `commission_records`（结算落账、次卡抵扣实收 0 也记、退单负向冲销净额归零、按员工合计报表），`CommissionRuleHttpTest` 3 + `CommissionRecordHttpTest` 5 测试锁死。d 待排期。

## 3. 共用核心确认与调研偏差修正

账本句「与现有医馆业态共用建档/卡项/回访核心」逐项核实：

| 共用核心 | 现状 | 结论 |
| --- | --- | --- |
| 建档 | `customers`（name/phone/openid/level/birthday…） | 直接共用；美容客群差异走标签与档案扩展，不动主表 |
| 卡项 | `customer_cards`（totalTimes/remainingTimes/**validUntil**/sourceOrderId）= 次卡实例；`recharges` = 储值 | 次卡模型与美业疗程卡同构，直接共用；**本金/赠送分账**（调研 §1.2）实施储值时再核 `recharges` 口径，必要时加字段 |
| 回访 | `review` 系列（Review/ReviewCustomer/ReviewStaff） | 直接共用；生日/回访定时提醒属触发层，实施时加任务而非新实体 |
| 积分 | **服务端零实现**（`server/src/main/java` 全域 grep 积分零命中） | ⚠️ 修正调研 §10.1 的「已实现消费积分（1 元 1 分）」——该说法在医馆模型中**不成立**；若 P0 要积分需当新增排期，或降级 P1 |
| 诊室/房间 | `QueueCallEntity.room` 为叫号展示文本（`AdScreenEntity` 的「诊室」是广告定向位置） | ⚠️ 调研 §10.1 的「需确认房间维度」结论确认：**无资源级房间模型**，方向 b 需全新增 |

## 4. 合规底线（随分期实施，不后补）

- **敏感个人信息**（调研 §8.1）：肤质、过敏史、护理照片属 PIPL「医疗健康」敏感信息——采集需**单独同意**记录、可见权限收紧（仅服务技师与店长）；与档案/标签功能**同批上线**，不事后补。
- **预付卡**（调研 §8.4）：储值发售属单用途预付卡监管范围；系统侧至少能出**发卡与余额报表**支撑备案口径；正式对外售卡前需法务确认当地执行口径。
- **生活美容边界**（调研 §8.5）：项目库不录入医美性质项目（注射、光电诊疗类）；营销文案不出现医疗效果承诺（人工流程）。

## 5. 分期方向（照搬调研 §10，未排期）

- **P0（开业必需）**：a 项目库类目（**已实施**）+ b 工位实体与双维冲突检测（**已实施**）+ c 固定比例提成记账（**已实施**）+ d 标签/过敏（复用+新增）；组合支付与次卡核销、小票、日结交接班按调研清单逐项落。
- **P1**：提成规则引擎（阶梯/多人分佣）、耗材 BOM、效期预警、营销（券/拼团/老带新/沉睡召回）、触达体系。
- **P2**：多门店（已代决维持非目标，见 todo.md；届时再定门店字段与聚合口径）、收银一体机深度适配、门禁。
- 分期内每项**先落手机端**（todo.md 排序依据：手机端优先）。

## 6. 红线：不产生两套并行实体

所有扩展以「现有实体加字段 / 加新表」落地，**禁止**出现「美容版 customers / items / appointments / 排班」之类的并行实体或并行表。核对结论（§2）已给出每一方向的复用锚点，实施 PR 以本页为准；与本页冲突的实现按本页回改。
