# TODO

> 生成于 2026-10-11；2026-10-13 整理。来源：docs/审计-文档一致性.md、docs/web.md 遗留、工作区在途改动。

## 待办

- [ ] 在线支付真实通道（红线，不碰）——用户拍板项，非工程面
- [ ] 西药/成药处方与药品目录——缺原型设计，待用户拍板（features.md 行 13 已记录裁定）
- [ ] 多门店/连锁——单店模型裁定不做，待连锁化再定（features.md 行 21）
- [ ] 采购单据/盘点/报损/多库房——出入库台账口径裁定不做（dispensing.md 非目标）

## 已完成（证据）

- [x] 名医排班——名医等级 staffs.famous_level（0 普通/1-3 名医，仅影响列表排序与标识徽章）+ 列表按等级降序 + 挂号费溢价上限配置 sinomed.famous-doctor-surge-cap-percent（缺省 20，后台可改）；不做复杂定价引擎、不做动态调价（2026-10-14 裁定）｜证据：commit a28f000，FamousDoctorTest 4 测试全绿
- [x] 小程序「我的卡项」手机号查卡——隐私红线：不做裸手机号查卡（无身份绑定即拒绝）；openid 绑定查卡（openid_only）+ 手机号/验证码双因子查卡（sms_verified），配置开关 sinomed.phone-lookup.mode 默认 off；两条路径都落 card_lookup_logs 审计（查了谁的卡/谁查的/时间），含 off 模式拒绝留痕｜证据：commit 28cfc0d，CardLookupOffTest 4 + CardLookupOpenidTest 5 + CardLookupSmsTest 9 = 18 测试全绿（全套 231 绿）
- [x] UI 按原型核对 15 组 84 条差距（P0 2 / P1 24 / P2 58）——全部清｜证据：commit 3001379~9c912b6（2026-10-11~12），后端 209 测试、web 17 测试全绿
- [x] 站点标题/PWA 品牌口径——「Youngs 医馆」非模板残留｜证据：commit 66556ed
- [x] CI maven.yml 接测试——去 -DskipTests + m2 缓存｜证据：commit 66556ed
- [x] 疗程卡营销赠次/转卡——card_adjusts 流水 + 3 接口 + 顾客档案操作｜证据：commit ec1c671，后端 207 测试
- [x] 经营趋势图表——GET /settlement/report/trend + 月度报表双柱图｜证据：commit 68f2617，后端 209 测试
- [x] 路线图口径收口——采购/盘点、名医排班、西药处方、多门店四项裁定记录｜证据：commit 9c912b6
- [x] 文档-代码一致性审计——docs/审计-文档一致性.md 落表｜证据：commit 5ebb8db
- [x] 药典剂量审方机制——herbs.dose_max 列 + 药材字典可录入（Herb/Query 页「单剂上限（克）」字段）+ `/api/v1/prescription/dose-check` 审方（提示不拦截）+ 开方页随写随查；诊所自录剂量上限属运营动作，无工程面｜证据：commit 8f893c7，PrescriptionDoseTest 3 + PrescriptionDoseHttpTest 2 全绿
