# 数据模型

数据表由 JPA 按实体自动创建（`ddl-auto: update`），主键均为自增 `id`。所有实体带审计字段 `create_time` / `update_time`（JPA Auditing 自动维护）；实体之间不使用外键约束，通过业务 id 字段在服务层关联。

## customers — 顾客

| 字段 | 类型 | 含义 |
| ---- | ---- | ---- |
| id | Long | 主键 |
| name | String | 姓名（非空） |
| age | Integer | 年龄 |
| level | Integer | 会员等级 |
| phone | String | 手机号（唯一，大陆号段） |
| gender | Integer | 性别（0 女、1 男） |
| address | String | 地址 |
| birthday | Date | 生日 |

索引：姓名、手机号、等级、生日。

## customer_histories — 顾客病史（过敏史/既往史）

| 字段 | 类型 | 含义 |
| ---- | ---- | ---- |
| id | Long | 主键 |
| customer_id | Long | 顾客 id（非空，业务外键，索引） |
| type | Integer | 0 过敏史 / 1 既往史（非空） |
| content | String | 病史内容（非空，自由文本，≤200 字） |

逐条 append 记录（新增/删除，不做编辑），查询按 id 倒序。演示种子给两位演示顾客各补过敏/既往记录，见 [api](/server/api) 顾客组。

## treats — 诊疗单（核心业务表）

中医诊疗记录，一个顾客可有多条：

| 字段 | 类型 | 含义 |
| ---- | ---- | ---- |
| id | Long | 主键 |
| cid | Long | 顾客 id（非空，业务外键） |
| cdesc | TEXT | 主诉 |
| inquiry | TEXT | 问诊 |
| observation | TEXT | 望诊 |
| palpation | TEXT | 触诊 |
| pulse | String | 脉象 |
| pulse_left / pulse_right | TEXT | 左 / 右手脉象 |
| pulse_info | TEXT | 脉象解读 |
| five_ben_zi_* / five_zi_ben_* | String | 五行·子母关系（左/右手） |
| five_ben_ke_* / five_ke_ben_* | String | 五行·相克关系（左/右手） |
| five_nan_jin_*（大/小，左/右） | String | 难经取象 |
| five_rate_left / five_rate_right | TEXT | 五行比率 |
| five_aux_left / five_aux_right | TEXT | 五行辅助 |
| acupoint_left / acupoint_right | TEXT | 取穴（左/右手） |
| acu_method / manipulation | String | 针灸处方：针法（毫针/电针/温针…）、手法（补法/泻法/平补平泻…），≤30 字，空白按空处理 |
| retention_minutes | Integer | 针灸处方：留针分钟（1-240，可空） |
| acu_course | TEXT | 针灸处方：疗程/频次（自由文本，≤100 字） |
| diagnose | TEXT | 诊断 |
| plan | TEXT | 调理方案 |
| diet | TEXT | 饮食建议 |
| conditioning | TEXT | 调理过程 |
| review | TEXT | 回访记录 |

索引：顾客 id、创建时间。

`acupoints`（穴位字典）：`name`（穴名唯一 1–10 字）、`pinyin`（全拼小写检索码，非空）、`meridian`（归经，非空）、`location`（体表定位）、`indication`（主治）、审计时间。经络穴位参考数据——接诊单取穴字段仍为自由文本，字典只做选穴辅助（按穴名/拼音检索、选中即追加），不参与其他业务校验。

## items — 卡项

| 字段 | 类型 | 含义 |
| ---- | ---- | ---- |
| id | Long | 主键 |
| name | String | 名称（非空、唯一） |
| price | Integer | 价格 |
| description | TEXT | 描述 |
| enabled | Integer | 1 上架、0 下架（Kiosk 只展示上架项；存量行默认 1） |
| cover | String | 封面图相对 url（可空，`/media` 托管） |
| sort | Integer | 展示顺序，小者在前 |

## reviews / review_staffs / review_customers — 反馈与复盘

| 表 | 字段要点 | 说明 |
| ---- | ---- | ---- |
| `reviews`（每日总结） | `day`（唯一，归一为当日 0 点）、`good`、`improvement` | 全店每日总结，同日保存即覆盖 |
| `review_staffs`（员工日复盘） | `staff_id`、`name`、`cost`（耗费）、`done`、`advice`、`day` | 按员工按日记录 |
| `review_customers`（顾客回访） | `customer_id`、`name`、`last`（上次时间）、`day`（回访日）、`advice` | `day` 到期后出现在每日报表提醒 |

## orders / recharges

| 表 | 字段要点 | 现状 |
| ---- | ---- | ---- |
| `orders`（订单） | `user_id`（顾客）、`item_id`（卡项）、`staff_id`、`status`、`price` | 已接线：Kiosk 下单与前台建单均落 `status=0` 并快照卡项现价（前台建单记录 `staff_id`）；`status` 语义 `0` 已下单 → `1` 已确认 → `2` 已完成，`9` 已取消（`0/1→9`，仅 `9` 可删），管理端分页与流转见 [api](/server/api) 订单组；结算信息在 `settlements` 表（一单一结算），订单分页联出支付方式 |
| `recharges`（储值流水） | `user_id`、`money` | 已接线：充值为正、储值支付扣减为负，余额 = 流水合计；接口见 [api](/server/api) 储值组 |

## settlements — 结算单（收费结算）

| 字段 | 类型 | 含义 |
| ---- | ---- | ---- |
| id | Long | 主键（结算单号，时间倒序展示） |
| order_id | Long | 订单 id（非空，唯一索引——一单一结算，防重复收款） |
| user_id | Long | 顾客 id（非空，冗余自订单便于按顾客对账） |
| pay_type | Integer | 支付方式：`1` 储值 / `2` 微信 / `3` 支付宝 / `4` 现金 / `5` 次卡抵扣 |
| money | Integer | 实收金额（元，取订单价格快照；次卡抵扣记 0） |
| create_time / update_time | Date | 审计时间 |

索引：`order_id`（唯一）、`user_id`。结算动作把订单 `0/1 → 2` 已完成；储值支付同时在 `recharges` 落一条负数流水；次卡抵扣扣 `customer_cards` 余 1 次。微信/支付宝为演示口径（仅记录方式，无真实收银通道），见 [api](/server/api) 收费组。

## customer_cards — 顾客持卡（次卡）

| 字段 | 类型 | 含义 |
| ---- | ---- | ---- |
| id | Long | 主键 |
| customer_id | Long | 顾客 id（非空，索引，customers.id） |
| item_id | Long | 卡项 id（非空，items.id） |
| total_times / remaining_times | Integer | 总次数 / 剩余次数（发卡=全量，抵扣减余次） |
| status | Integer | `1` 有效 / `0` 停用（列默认 1；停用卡不参与抵扣） |
| source_order_id | Long | 发卡来源订单（可空，前台手工发卡为空） |
| create_time / update_time | Date | 审计时间 |

索引：顾客。抵扣按「同顾客 + 同卡项、有效且有余次」取**最早一张**扣 1 次；暂无有效期（规划）。接口见 [api](/server/api) 次卡组。

## card_usages — 次卡核销流水

| 字段 | 类型 | 含义 |
| ---- | ---- | ---- |
| id | Long | 主键 |
| card_id | Long | 持卡 id（非空，索引，customer_cards.id） |
| order_id | Long | 触发抵扣的订单（可空——老卡补录/历史迁移无单据） |
| staff_id | Long | 服务/操作员工（可空，取订单 staffId） |
| times_used | Integer | 本次是第几次消费（1 起 = 总次数 − 扣后余次） |
| create_time / update_time | Date | 审计时间 |

append-only：每次结算抵扣（`payType=5`）在同一事务落一条，随结算回滚；不做手工划扣。种子数据为演示卡片补了历史核销（无订单号）。接口见 [api](/server/api) 次卡组。

## appointments — 预约（前台/馆长建约、小程序自助约期 → 到店接待 → 转接诊）

| 字段 | 类型 | 含义 |
| ---- | ---- | ---- |
| id | Long | 主键 |
| customer_id | Long | 顾客 id（非空，customers.id） |
| item_id | Long | 预约卡项 id（可空=到店再定） |
| staff_id | Long | 接待员工 id（可空=到店分配） |
| start_time | Date | 预约时段开始时刻（非空） |
| duration | Integer | 时长（分钟；自助约期固定 60） |
| status | Integer | `0` 待到店 → `1` 已接待（转接诊），`0→9` 取消；仅 `9` 可删 |
| remark | String | 备注（症状/需求）；自助约期记「小程序自助」软标记，无 source 列 |
| create_time / update_time | Date | 审计时间 |

索引：顾客、卡项、时段。接口见 [api](/server/api) 预约组；不做同时段冲突校验（演示边界）。

## prescriptions / prescription_items — 中药处方

| 字段 | 类型 | 含义 |
| ---- | ---- | ---- |
| id | Long | 主键（处方号） |
| treat_id | Long | 接诊单 id（可空，treats.id） |
| customer_id | Long | 顾客 id（非空，customers.id） |
| staff_id | Long | 开方医师 id（可空，staffs.id） |
| doses | Integer | 剂数（几付，非空，默认 7） |
| decoction_status | Integer | 代煎状态：0 无需代煎 / 1 待煎 / 2 可取 / 3 已取（勾代煎开方落 1，流转 待煎→可取→已取 单向推进） |
| decoction_bags | Integer | 代煎袋数（=剂数；无需代煎为空） |
| prescription_type | Integer | 处方类型：0 汤剂（默认）/ 1 膏方 |
| paste_status | Integer | 膏方领取状态：0 非膏方 / 1 待制作 / 2 可取 / 3 已取（开膏方落 1，流转 待制作→可取→已取 单向推进） |
| craft | String | 收膏方式（仅膏方：炼蜜/清膏/糖膏/阿胶收膏等，可空；汤剂恒空） |
| dispense_status | Integer | 发药状态：0 待发药（默认）/ 1 已发药（开方落 0，单向不回退；代煎/膏方推进「已取」自动置 1） |
| dispense_time | Date | 发药时间（待发药为空） |
| dispense_by | Long | 发药操作人 staff_id（自动带出路径留空） |
| usage | String | 用法：煎服法/频次/代煎说明 |
| remark | String | 备注 |
| create_time / update_time | Date | 审计时间 |

`prescription_items`（药味）：`prescription_id`（非空，索引）、`herb`（药名，非空，自由文本）、`weight`（单剂克数，Double，非空）、`special`（特殊煎法，可空）、`sort`（顺序，非空）。

`herbs`（药材字典）：`name`（唯一，与处方药名精确同名比价）、`price`（每克分价，int，>0）、审计时间。处方计价按字典**实时试算**（不落库、无快照，未收录药名不计费）。

`dispense_logs`（发药流水，单向追加）：`prescription_id`（非空，索引）+ `action`（1 发药，本链路只有一种动作）+ `staff_id`（操作人，自动带出为空）+ `note`（≤100 字，自动带出写来源如「代煎领取自动发药」）+ 审计时间。重复发药 400、不可撤销；发药不扣库存、不落金额快照，见 [药房与发药流转](../design/dispensing.md)。

`herb_stock_logs`（饮片出入库流水）：`herb_id`（非空，索引）+ `type`（1 入库 / 0 出库）+ `quantity`（克，正整数）+ `expiry`（批次效期，入库可带，出库恒空）+ `supplier`（≤50 字）+ `note`（≤100 字）+ 审计时间。**库存 = 入库合计 - 出库合计（不落余额列，按流水重算）**；出库按效期先进先出（FEFO）消耗批次，余额接口据此给「最早未消耗批次效期 + 到期预警（默认 30 天）」；台账口径，不涉采购单据与结算，见 [api](/server/api) 饮片库存组。

`prescription_templates` / `prescription_template_items`（病症模板，结构同下）：模板侧多 `doses`/`decoction`/`usage`/`remark` 建议值与 `enabled` 上架位。`formulas` / `formula_items`（方剂库）：`formulas` 为 `name`（方名唯一 1–20 字）、`pinyin`（全拼小写检索码，非空）、`source`（出处）、`indication`（功效主治）；`formula_items` 为 `formula_id`（非空，索引）+ 药味四件套 `herb`/`weight`/`special`/`sort`。两者均为开方参考数据——模板按病症套用、方剂按方名/拼音检索带出全方，带出后随处方自由增减、不写回；方剂为文献常用量口径，不参与计价与审方。

`therapy_templates`（外治处方模板）：`name`（模板名唯一，1–20 字，索引）+ `method`（外治方法，非空，≤10 字，索引）+ `target`（建议取穴/部位，≤100 字，可空）+ `course`（建议疗程/频次，≤100 字，可空）+ `note`（注意事项，≤100 字，可空）+ `enabled`（0 停用 / 1 启用）+ 审计时间。单表无明细（外治组合是「方法 + 部位 + 疗程」的自由文本，不像药方有逐味剂量）。接诊页「套用外治模板」只取 `method`/`target`/`course` 三个建议值回填针灸处方区与取穴文本，回填后随诊疗单自由改，不写回模板。

索引：`prescriptions` 顾客、接诊单；`prescription_items` 处方；`herb_stock_logs` 药材、类型。代煎领取与膏方领取流转已实装（两条独立单向链：待煎→可取→已取；待制作→可取→已取）；药房发药已实装（`dispense_status` 0→1 单向 + `dispense_logs` 流水，代煎/膏方「已取」自动带出）；饮片出入库流水已实装（余额按流水重算）；配伍审方（十八反/十九畏）为静态规则比对（不落表），见 [api](/server/api) 处方组。

## staffs — 员工（登录主体，诊所运营）

| 字段 | 类型 | 含义 |
| ---- | ---- | ---- |
| id | Long | 主键 |
| name | String | 姓名（非空） |
| account | String | 登录账号（唯一） |
| password | String | 密码（BCrypt） |
| phone | String | 手机号（唯一） |
| avatar | String | 头像 URL |
| age | Integer | 年龄 |
| gender | Integer | 性别（0 女、1 男） |
| address | String | 地址 |
| birthday | Date | 生日 |
| role | Integer | 角色：`< 10` 管理层（`admin`），`>= 10` 普通员工（默认 99） |
| status | Integer | 状态：0 离职、1 在职、2 休假（`<= 0` 无法登录） |

索引：姓名、账号、手机号。

## signs — 考勤打卡（诊所运营）

| 字段 | 类型 | 含义 |
| ---- | ---- | ---- |
| id | Long | 主键 |
| staff_id | Long | 员工 id |
| type | Integer | 1 上班打卡、0 下班打卡 |
| create_time | Date | 打卡时间 |

考勤统计在查询时实时计算（无定时任务）：同日多次上班卡取最早、下班卡取最晚，时长按整小时计。

## staff_leaves — 员工请假（诊所运营）

| 字段 | 类型 | 含义 |
| ---- | ---- | ---- |
| id | Long | 主键 |
| staff_id | Long | 员工 id（staffs.id） |
| leave_type | Integer | 类型：0 病假、1 事假 |
| reason | String | 事由（非空） |
| start_time / end_time | Date | 起止时间（结束不早于起始） |
| create_time / update_time | Date | 审计时间 |

索引：员工 id。口径：请假为记录性质，不改员工登录状态、无审批流（演示口径）。

## staff_shifts — 员工周期班表（诊所运营）

| 字段 | 类型 | 含义 |
| ---- | ---- | ---- |
| id | Long | 主键 |
| staff_id | Long | 员工 id（staffs.id） |
| weekday | Integer | 星期（1 周一 … 7 周日） |
| start_time / end_time | String | HH:mm 时段（start < end） |
| create_time / update_time | Date | 审计时间 |

索引：员工 id、星期。口径：同一员工同一星期仅一条班次，按周循环；班表只做排班参考，考勤打卡仍以 signs 为准（不回写打卡记录）。

## ad_materials / ad_screens / ad_schedules — 广告投屏（平板展示）

配套 [tablet/](/design/tablet) Kiosk 端，设计见[平板展示设计](/design/tablet)。文件本体不上库：素材上传落盘 `data/ads/yyyyMM/`（`sinomed.ads.upload-dir` 可改），表里存相对 url。

**ad_materials — 素材**：`name`、`type`（1 图片、2 视频）、`url`、`duration_ms`（轮播停留毫秒）、`enabled`、`sort`（顺序，小者在前）。

**ad_screens — 屏**：`code`（唯一，平板以 `?screen=code` 打开）、`name`、`location`、`enabled`、`last_seen_at`（平板拉 playlist 时顺带心跳，管理端按 2 分钟阈值显示在线角标）。

**ad_schedules — 排期**：`screen_id` / `material_id`（业务外键）、`weekdays`（`1=周一…7=周日` 逗号分隔，空 = 每天）、`start_time` / `end_time`（`HH:mm`，空 = 全天）、`enabled`。

## queue_calls — 叫号记录（平板展示）

| 字段 | 类型 | 含义 |
| ---- | ---- | ---- |
| id | Long | 主键（同时作平板拉取游标 `since`） |
| screen_id | Long | 定向屏（业务外键；为空 = 全部屏广播） |
| number | String | 号码（如 `08`） |
| room | String | 诊室名 |
| patient_masked | String | 脱敏姓名（如 `张*`，隐私默认） |
| status | Integer | 0 待叫、1 已叫（当前写入即 1） |
| called_at | Date | 叫号时间 |
