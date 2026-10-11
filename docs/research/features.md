# 功能点清单

> 分层整理:通用门诊必备 + 中医特色单列。每个功能点标注参考来源项目(以各项目 README 实际描述为准)与 Sinomed 源码现状(以 `server/` 实际实现为准),可作为后续规划的对照清单。

图例——Sinomed 现状:**已实现**(接口与页面可用)/ **部分**(有相关字段或占位)/ **未实现**(源码中不存在)。

## 一、通用门诊必备

| 功能点 | 参考来源项目 | Sinomed 现状 | 说明 |
| ---- | ---- | ---- | ---- |
| 预约挂号 | openhis-clinic(预约登记)、ZainZhao/HIS、TANGKUO/HIS(挂号工作站)、hisystem(挂号收费) | 已实现 | `appointments` 表 + `/api/v1/appointment`（建约/待到店列表/到店接待转接诊/取消），管理端 `/appointment/*` 列表工作台 + `/appointment/schedule` 医师×时段排班网格；小程序自助约期已实装（`POST /api/v1/kiosk/appointments` 免登录 + 小程序 `pages/booking`，频控 1 条/日/手机号，不做同时段冲突校验）。**界面：[预约排班](/screenshots/admin-booking.png)** |
| 电子病历 | Yukang(主诉/现病史/四诊/辨证/医嘱)、openhis(EMR 模块)、his_mvp | 部分 | `treats` 诊疗单承载主诉、问诊、望诊、触诊、脉象、五行、取穴、诊断、方案、饮食、调理、回访;病史结构已实装（`customer_histories` 逐条记录过敏史/既往史，顾客档案页可增删，见 CustomerHistoryHttpTest）；无独立过敏史/既往史以外的既往史时间线 |
| 处方(西药/成药) | Yukang(中西成药处方)、openhis-clinic(收费发药) | 未实现 | 西药/成药处方与药品目录未做；中药处方已实装（`prescriptions`/`prescription_items`，配药材字典计价）。**界面：[中药处方笺](/screenshots/admin-herbprescription.png)** |
| 中药处方 | Yukang(中药/贴敷/外治)、herb-ms-ssm(药材-处方-计价)、his_mvp(中药方剂)、chinese_medicine_store_cos(病症处方) | 部分 | `prescriptions`/`prescription_items` 表 + `/api/v1/prescription`（开方/详情/分页/删除，药味含剂量克数与特殊煎法）+ `/prescription/*` 开方与查询页；药材字典实时计价、配伍审方（十八反/十九畏）、代煎与膏方领取流转、病症模板、方剂库检索带方已实装；饮片出入库流水与效期预警已实装（`/api/v1/herb-stock`，开方仍不扣库存）。**界面：[中药处方笺](/screenshots/admin-herbprescription.png)** |
| 药房与发药 | ZainZhao/HIS、TANGKUO/HIS(药房工作站)、openhis-clinic、hisystem(药房取药) | 未实现 | - |
| 中药饮片库存 | chinese_medicine_store_cos(药材档案/采购/库房预警)、his_mvp(出入库/盘点)、Yukang(批次/效期) | 部分 | 出入库流水与效期预警已实装（`herb_stock_logs` 入库/出库流水（克），库存=入合计-出合计按流水重算，出库按效期 FEFO 消耗批次不得超库存，`GET /api/v1/herb-stock/balance` 给最早未消耗批次效期与到期预警（默认 30 天），管理端 `/prescription/herb-stock` 余额看板 + 流水登记/删除，见 HerbStockHttpTest；种子 5 味常用药材入库 + 1 笔出库演示）；采购单据/盘点/库房上下限未做 |
| 收费结算 | Yukang(现金/微信/支付宝/银行卡/医保)、OpenHIS(划价收费)、openhis-clinic | 部分 | `settlements` 结算单 + `/api/v1/settlement`（订单 0/1→2 收款，一单一结算）+ `/api/v1/recharge` 储值流水（余额=合计），管理端 `/billing/settle` 结算台；微信/支付宝仅记录方式，无退费/日对账/发票，处方饮片行与卡项次卡抵扣未做。**界面：[收费结算台](/screenshots/admin-billing.png)** |
| 患者会员 | TANGKUO/HIS(患者管理)、Yukang(连锁分店)、chinese_medicine_store_cos(会员购买/订单) | 部分 | `customers.level` 会员等级字段存在；储值充值/扣减流水已接线（`recharges`），无折扣/套餐/跨店消费 |
| 排班 | 调研项目中未见明确实现的排班模块 | 部分 | `signs` 考勤仍是上下班打卡；预约排班已实装——`/appointment/schedule` 医师×时段日网格（占用/空档/格内接待）；周期性员工班表已实装（`/api/v1/shift` 员工×星期×HH:mm 时段、同员工同星期唯一，管理端 `/staff/shifts` 周矩阵加排班/调整/删除，见 StaffShiftHttpTest）；班表与考勤联动已实装（`/api/v1/staff/timesheet/week` 周班次对照：班表计划按星期对照当日打卡，同日最早上班/最晚下班、整小时取整，管理端 `/staff/timesheet/week`；与预约仍无联动）。**界面：[预约排班](/screenshots/admin-booking.png)** |
| 报表统计 | Yukang(经营统计/处方量趋势)、OpenHIS(图表统计)、openhis-clinic(报表查询) | 部分 | 已有每日报表(总结/复盘/回访)与按月考勤统计；月度收费报表已实装（`GET /api/v1/settlement/report/month` 逐日与支付方式构成、次卡核销单列；见 SettlementReportHttpTest）；无经营趋势分析与图表 |
| 多门店/连锁 | Yukang(总店聚合/分店独立库)、chinese_medicine_store_cos(多门店) | 未实现 | 单店模型 |

## 二、中医特色

| 功能点 | 参考来源项目 | Sinomed 现状 | 说明 |
| ---- | ---- | ---- | ---- |
| 辨证论治记录 | Yukang(四诊+辨证+医嘱病历结构)、his_mvp(辨证开方) | 部分 | 诊疗单含五行生克(本/克/难经/比率)、脉象(左右手)、诊断字段,可支撑辨证记录;无标准证型字典 |
| 中药饮片/方剂 | Yukang(中药饮片/中成药/颗粒分类)、his_mvp(方剂库+拼音输入)、herb-ms-ssm、chinese_medicine_store_cos | 部分 | 剂量模型已随中药处方上线（`prescription_items.weight` 克/剂 + `special` 煎法）；方剂库与拼音检索已实装（`/api/v1/formula` 方名唯一 + 全拼小写码检索，开方页按方名/拼音一键带出全方，见 FormulaHttpTest）；饮片目录=药材字典已实装并支撑实时计价；饮片出入库流水与效期预警已实装（`/api/v1/herb-stock` FEFO 批次口径，见 HerbStockHttpTest）；采购单据/盘点未做 |
| 针灸/取穴记录 | Yukang(外治/贴敷处方)、his_mvp(含经络穴位课程体系) | 部分 | 诊疗单含 `acupoint_left/right` 取穴字段（自由文本）；穴位字典已实装（`/api/v1/acupoint`，穴名唯一 + 全拼小写码检索，接诊页按穴名/拼音选穴追加，见 AcupointHttpTest）；针灸处方记录已实装（接诊单 `acu_method`/`retention_minutes`/`manipulation`/`acu_course` 针法/留针/手法/疗程，接诊页「针灸处方」区，守卫见 TreatAcuHttpTest）；耳穴/贴敷等外治处方模板已实装（`/api/v1/therapy-template`，接诊页「套用外治模板」带出方法/取穴/疗程建议，随单自由改不写回） |
| 推拿/艾灸疗程 | 仲正堂门店业态(推拿、关元灸;未见其 App 功能清单) | 部分 | 疗程卡口径已随次卡落地：推拿/艾灸疗程卡 = 卡项(次卡) + `customer_cards` 持卡，结算台次卡抵扣划次；核销流水已实装（`card_usages` 每次抵扣落「第几次/服务员工/订单」，顾客档案持卡栏可查，服务员工取订单 staffId）；无疗程卡营销(赠次/转卡/期限) |
| 病症处方模板 | chinese_medicine_store_cos(病症处方模板库) | 已实现 | 病症模板库已实装（`prescription_templates` + items 两表；`/api/v1/prescription/template/*` CRUD + 上架过滤；开方页「套用模板」一键带出药味/剂数/用法，模板只存建议值不写回；**界面：[处方 · 病症模板](/screenshots/admin-herbprescription.png)**） |
| 配伍禁忌审方 | Yukang(AI 审方:过敏/配伍禁忌/相互作用) | 部分 | 十八反/十九畏静态规则已实装（`POST /api/v1/prescription/compatibility`，别名包含匹配、提示不拦截；见 CompatibilityServiceImplTest）；过敏史静态审方已实装（`POST /api/v1/prescription/allergy-check`，按顾客过敏史原文包含匹配、提示不拦截；见 PrescriptionAllergyHttpTest）；相互作用比对与 AI 审方未做 |
| 膏方/代煎 | 调研项目中未见 | 已实现 | 代煎与膏方两条独立领取链均已实装：代煎（开方勾选落「待煎」，流转 待煎→可取→已取，袋数=剂数，服务费仅提示）；膏方（`prescription_type=1` 开方落「待制作」，`craft` 记收膏方式，按料计不走袋数，流转 待制作→可取→已取，非膏方/跳跃报 400；与代煎互不影响） |
| 名医排班 | 调研项目中未见 | 未实现 | - |

## 三、Sinomed 已落地的模块(源码)

以下为源码中可用的功能,详情见[服务端接口清单](/server/api)与[管理端页面](/web):

| 模块 | 能力 |
| ---- | ---- |
| 顾客系统 | 建档、分页/姓名/手机号查询、更新、删除 |
| 诊断系统 | 中医诊疗单创建(四诊/脉象/五行/取穴/诊断/方案)、按顾客查历史、模糊检索 |
| 反馈系统 | 每日总结(按天 upsert)、员工日复盘、顾客回访(到期待办进每日报表) |
| 员工系统 | 员工 CRUD、上/下班签到、今日与按月考勤统计 |
| 卡项系统 | 卡项创建、查询、维护 |
| 认证 | JWT 登录、管理员/员工两级角色、离职禁用 |

## 四、规划时的取材建议

- **业务模型**优先参照 Yukang(与中医医馆重合度最高)与 openhis-clinic(诊所 5 模块闭环);两者见[开源项目盘点](/research/projects)。
- 涉及**复用代码**前先看许可证:MIT/Apache 可放宽参考;GPL-3.0(openhis 系)有传染性;无许可证项目(chinese_medicine_store_cos、his_mvp 等)只能借鉴思路,不可复制代码。
- 功能命名与文案以本仓源码为准(顾客/诊断/反馈/员工/卡项系统),引入新模块时避免与现有命名冲突。
