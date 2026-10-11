# TODO

> 生成于 2026-10-11（本仓无任何待办文件；来源：docs/audit-doc-consistency.md「文档超前于代码」项、docs/web.md 遗留、工作区在途改动）。

## P0 — UI 按原型核对：差距清单（2026-10-11 全量核对完成）

> 15 组全部核对（原型 `docs/design/mockups/*.html` + `tokens.css` ↔ 实现 web/app/kiosk/tablet/desktop），共 **84 条差距（P0 2 / P1 24 / P2 58）**，逐条带 file:line 证据。
> 进度（2026-10-12 续批）：已清 22 条（P0 2/2 + P1 9 + P2 11：herbprescription 辨证条、mobile-home 全部 7 条、orders 全部 5 条、billing 明细/备注/说明行/队列单号、booking 待到店面板/过号/限约/stat4/联动面板），余 62 条（P1 15 / P2 47，其中 2 条卡点：booking 疗程角标、booking 医师名单——均缺模型字段）
> 两条裁定口径已执行：admin-billing 处方饮片行不列为差距；mobile-home 卡项余次「规划功能」占位按忠实占位处理（P2 信息项）。
> 严重度：P0=原型整块功能缺失；P1=功能在但结构/字段明显不同；P2=表层细节。修完按 mockups README §2 口径重出截图验收。

### admin-customer（11 条）
- [ ] 诊疗时间线字段结构：原型每条=项目名+疗程标签+摘要(医师·时长·证治取穴)，实现标题取 diagnose 证型、无医师/时长/疗程标签｜原型 admin-customer.html:119 / 实现 web/src/pages/Customer/Update/index.tsx:245｜P1
- [ ] 下次回访统计：原型显示真实回访日期，实现为「规划功能 · 无按顾客回访查询」占位｜原型 admin-customer.html:111 / 实现 web/src/pages/Customer/Update/index.tsx:221｜P2
- [ ] 持卡卡项面板缺「回访提醒」整块 note（含回访原因文案）｜原型 admin-customer.html:157 / 实现 web/src/pages/Customer/Update/index.tsx:254｜P2
- [ ] 卡项行缺「购入日期 · 有效期至」（Card 模型无有效期字段）｜原型 admin-customer.html:150 / 实现 web/src/pages/Customer/Update/index.tsx:265｜P2
- [ ] 头卡缺建档时间（API.Customer 无 createTime）｜原型 admin-customer.html:100 / 实现 web/src/pages/Customer/Update/index.tsx:180｜P2
- [ ] 新建顾客表单文案错误：「年纪」应为年龄、占位「输入员工名字」应为顾客｜原型 admin-customer.html:85 / 实现 web/src/pages/Customer/Create/index.tsx:31｜P2
- [ ] 会员等级为裸数字输入，原型为「金卡会员」档位徽标（无 普通/银卡/金卡 枚举）｜原型 admin-customer.html:99 / 实现 web/src/pages/Customer/Create/index.tsx:39｜P2
- [ ] 回访计划缺「回访原因」结构化字段（/review/plan 仅 姓名/最近访问/计划回访）｜原型 admin-customer.html:157 / 实现 web/src/pages/Review/Plan/index.tsx:58｜P2
- [ ] 诊疗历史全量列表仅 ID/主述/创建时间 三列，信息少于档案页时间线｜原型 admin-customer.html:115 / 实现 web/src/pages/Treat/History/index.tsx:39｜P2
- [ ] 缺面包屑导航（原型「顾客 / 顾客查询 / 王女士」三级）｜原型 admin-customer.html:95 / 实现 web/src/pages/Customer/Update/index.tsx:171｜P2
- [ ] 顾客查询操作列「跟访」与原型「复盘回访/回访」措辞不一致｜原型 admin-customer.html:92 / 实现 web/src/pages/Customer/Query/index.tsx:194｜P2

### admin-diagnosis（6 条）
- [ ] 脉象缺寸关尺分部：原型左右手各 寸/关/尺 三格共 6 格，实现仅左右手两个自由文本框（全库无寸/关/尺分字段）｜原型 admin-diagnosis.html:149 / 实现 web/src/pages/Treat/Create/index.tsx:291｜P1
- [ ] 五行生克可视化缺失：原型五行定位图（脏腑标注+生克提示）+ 本/克/难经/比率四指标，实现仅数值录入无图形化｜原型 admin-diagnosis.html:190 / 实现 web/src/pages/Treat/Create/index.tsx:298｜P1
- [ ] 诊断缺「第 N 疗程」标记（疗程只存在于 acuCourse）｜原型 admin-diagnosis.html:182 / 实现 web/src/pages/Treat/Create/index.tsx:447｜P2
- [ ] 取穴面板缺灸法行（「艾条悬灸，每穴 15 分钟」被并入针法/留针字段）｜原型 admin-diagnosis.html:176 / 实现 web/src/pages/Treat/Create/index.tsx:407｜P2
- [ ] 调理方案卡项余次为「规划功能」占位（与档案页已实现卡项实体相矛盾）｜原型 admin-diagnosis.html:206 / 实现 web/src/pages/Treat/Create/index.tsx:251｜P2
- [ ] 头卡缺「复诊，回填上次诊疗单」就诊语境说明｜原型 admin-diagnosis.html:127 / 实现 web/src/pages/Treat/Create/index.tsx:246｜P2

### admin-dash-day（4 条）
- [ ] 员工总结表列结构：原型 员工(带角色)/今日小结/状态/提醒操作，实现 员工/耗卡/卡项已做/成单建议/状态，无角色列无操作列｜原型 admin-dash-day.html:110 / 实现 web/src/pages/Dashboard/Day/index.tsx:164｜P1
- [ ] 未填行「提醒」操作缺失（提醒推送属规划域，面板 hint 已注明）｜原型 admin-dash-day.html:139 / 实现 web/src/pages/Dashboard/Day/index.tsx:159｜P2
- [ ] 员工在岗统计「5 人」为「规划功能」占位｜原型 admin-dash-day.html:101 / 实现 web/src/pages/Dashboard/Day/index.tsx:130｜P2
- [ ] 今日回访行缺脱敏手机号与「明日到期」置灰态（显示「最近到店」替代）｜原型 admin-dash-day.html:148 / 实现 web/src/pages/Dashboard/Day/index.tsx:222｜P2

### admin-items（2 条）
- [ ] 卡项列表未按「排序」字段排序（后端 PageRequest 无 Sort，顺序不确定）｜原型 admin-items.html:88 / 实现 web/src/pages/Item/Query/index.tsx:137（后端 server/src/main/java/com/sinomed/controller/ItemController.java:118）｜P2
- [ ] 卡项无「时长」结构化字段（仅 description 自由文本）｜原型 admin-items.html:95 / 实现 web/src/pages/Item/Query/index.tsx:50｜P2

### admin-orders（5 条，全部已清）
- [x] 已完成/已取消订单缺「查看」详情入口（实现渲染「-」）｜原型 admin-orders.html:120 / 实现 web/src/pages/Order/Query/index.tsx:196（done/cancelled 行「查看」开详情弹窗：明细表单价/数量/小计、合计、整单备注、支付方式、下单时间、来源）｜P1
- [x] 「项目」列仅单卡项无数量（数据模型无多品项/数量字段）｜原型 admin-orders.html:101 / 实现 server/src/main/java/com/sinomed/entity/OrderItemEntity.java:15（order_item 明细行 name/price/qty/subtotal 快照）+ web/src/pages/Order/Query/index.tsx:50（项目列「名 ×数量」顿号串）+ 前台建单多选卡项带数量 web/src/pages/Order/Create/index.tsx:134；存量单回退一行数量 1（OrderController.hydrateItems）｜P1
- [x] 单号格式：原型业务单号「20261008-006」，实现裸 id「#6」｜原型 admin-orders.html:99 / 实现 web/src/services/ant-design-pro/order.ts:47（orderBusinessNo=创建日期+流水号补零）｜P2
- [x] 订单列表未按时间倒序（page 查询无 ORDER BY）｜原型 admin-orders.html:99 / 实现 server/src/main/java/com/sinomed/service/impl/OrderServiceImpl.java:79（createTime 倒序，同秒 id 倒序）｜P2
- [x] 实现多出「下单时间」列（原型无，实现新增项）｜原型 admin-orders.html:95 / 实现 web/src/pages/Order/Query/index.tsx:144（列已删，下单时间挪进「查看」详情）｜P2

### admin-ads（4 条）
- [ ] 素材子标题缺「分类」（原型「图片 · 门店活动」，实现只有类型）｜原型 admin-ads.html:112 / 实现 web/src/pages/Ads/Index/index.tsx:254｜P2
- [ ] 屏幕状态在线文案缺排期名｜原型 admin-ads.html:145 / 实现 web/src/pages/Ads/Index/index.tsx:593｜P2
- [ ] 快捷叫号交互：原型已就绪号码一键上屏，实现为手填表单（手动叫号口径内）｜原型 admin-ads.html:158 / 实现 web/src/pages/Ads/Index/index.tsx:623｜P2
- [ ] 素材操作多出「删除」入口（实现新增项）｜原型 admin-ads.html:116 / 实现 web/src/pages/Ads/Index/index.tsx:290｜P2

### admin-appearance（2 条）
- [ ] 主题面板提示文案拼接错误（「石墨 × 古金（默认 · 取自 logo）」）｜原型 admin-appearance.html:108 / 实现 web/src/pages/Appearance/Index/index.tsx:51｜P2
- [ ] 预览网格未固定高 356px（随内容自适应）｜原型 admin-appearance.html:42 / 实现 web/src/pages/Appearance/Index/index.less:101｜P2

### admin-booking（7 条）
- [x] 「待到店」紧凑面板（到店一键转接诊）被拆成独立预约查询整页表格｜原型 admin-booking.html:130 / 实现 web/src/pages/Appointment/Schedule/index.tsx:231（右栏「待到店」面板：时段·顾客·项目·医师 + 一键「接待」，排班页就近接待，预约查询页保留整页表格）｜P1
- [x] 「过号」状态缺失（以「已取消」顶替，爽约与主动取消混同）｜原型 admin-booking.html:100 / 实现 web/src/services/ant-design-pro/appointment.ts:7（NO_SHOW=8）、web/src/pages/Appointment/Schedule/index.tsx:21、web/src/pages/Appointment/Query/index.tsx:35+183（0→8 Popconfirm）、server/src/main/java/com/sinomed/service/impl/AppointmentServiceImpl.java:104（0→1/8/9，8/9 终态）｜P1
- [x] 「每时段限约 1 位」规则未实现（同格多条 +N 并列，无冲突校验）｜原型 admin-booking.html:90 / 实现 server/src/main/java/com/sinomed/service/impl/AppointmentServiceImpl.java:57（floor 到整点桶，同医师 status IN(0,1) 互斥，staffId 空归空组）、server/src/main/java/com/sinomed/controller/KioskController.java:201（自助约期同规则）、回归 server/src/test/java/com/sinomed/AppointmentSlotTest.java + KioskAppointmentTest.sameSlotSecondBookingRejectedBySlotCap｜P1
- [x] 今日预约统计为工具栏内联文字（原型 2×2 统计卡面板，第四项应为「过号」）｜原型 admin-booking.html:122 / 实现 web/src/pages/Appointment/Schedule/index.tsx:208（右栏「今日预约」2×2 stat4：总数/已到店/待到店/过号）｜P2
- [ ] 格位次行缺「第 N 疗程/复诊/首诊」限定（仅项目名）【卡点：无数据源】预约/卡项模型没有疗程次数字段（orders 也不带卡项剩余次数），第 N 疗程无从推算；需要「顾客×卡项已完成次数」账本才能落地｜原型 admin-booking.html:94 / 实现 web/src/pages/Appointment/Schedule/index.tsx:179｜P2
- [x] 「小程序联动」面板未做（原型已标注规划，P2 信息项）｜原型 admin-booking.html:140 / 实现 web/src/pages/Appointment/Schedule/index.tsx:252（右栏面板含原型规划文案）｜P2
- [ ] 医师列仅按当日有约动态生成（原型当日医师名单全列占位）｜原型 admin-booking.html:92 / 实现 web/src/pages/Appointment/Schedule/index.tsx:62｜P2

### admin-herbprescription（11 条）
- [x] 「辨证摘要条」（接诊单联动：证型·治法）整段缺失｜原型 admin-herbprescription.html:108 / 实现 web/src/pages/Prescription/Create/index.tsx:344（接诊单下拉+辨证/治法/疗程，findCustomer 定位后自动带最近一张 index.tsx:210，开方随处方落 treatId index.tsx:259；口径：辨证=接诊单 diagnose、治法/方案=接诊单 plan 自由文本）｜P0
- [ ] 处方笺头部缺处方编号/诊号/性别年龄（仅姓名/会员/手机）｜原型 admin-herbprescription.html:106 / 实现 web/src/pages/Prescription/Create/index.tsx:288｜P1
- [ ] 加药无饮片目录拼音首字母检索（自由文本，字典仅精确同名比价）｜原型 admin-herbprescription.html:120 / 实现 web/src/pages/Prescription/Create/index.tsx:364｜P1
- [ ] 审方四查缺「剂量上限(药典)」「特殊煎法提醒」两项（仅配伍+过敏）｜原型 admin-herbprescription.html:136 / 实现 web/src/pages/Prescription/Create/index.tsx:369｜P1
- [ ] 签名行缺「药师审核/调配复核」（仅医师下拉）｜原型 admin-herbprescription.html:128 / 实现 web/src/pages/Prescription/Create/index.tsx:486｜P1
- [ ] 代煎缺取货时效与自取/同城配送二选一（仅代煎 N 袋勾选+¥3/袋提示）｜原型 admin-herbprescription.html:142 / 实现 web/src/pages/Prescription/Create/index.tsx:475｜P1
- [ ] 费用构成口径：原型代煎费入合计三行账，实现代煎费仅旁注提示（收费以卡项订单结算为准的既定口径，是否对齐原型待定夺）｜原型 admin-herbprescription.html:148 / 实现 web/src/pages/Prescription/Create/index.tsx:420｜P1
- [ ] 费用区缺「存方并收费/存入我的方剂库/打印处方笺」三键（仅「开方」一键；无打印代码）｜原型 admin-herbprescription.html:150 / 实现 web/src/pages/Prescription/Create/index.tsx:513｜P1
- [ ] 「炮制/煎法」列合并为单个特殊煎法自由文本｜原型 admin-herbprescription.html:110 / 实现 web/src/pages/Prescription/Create/index.tsx:348｜P2
- [ ] 「剂数/煎法/服法」三格合并为剂数框+用法文本框｜原型 admin-herbprescription.html:123 / 实现 web/src/pages/Prescription/Create/index.tsx:498｜P2
- [ ] 过敏审方无「档案无药物过敏记录」✓ 常显态（命中才渲染）｜原型 admin-herbprescription.html:138 / 实现 web/src/pages/Prescription/Create/index.tsx:399｜P2

### admin-billing（7 条；处方饮片行按裁定不计）
- [x] 结算明细多行表：原型 项目/数量/小计 三列多行（含卡项抵扣行 ¥0），实现单行 itemName+price（OrderEntity 无数量字段）｜原型 admin-billing.html:122 / 实现 web/src/pages/Billing/Settle/index.tsx:288（.lines 多行表联 order_item 明细，选次卡且有余次时加「卡项抵扣（次卡扣 1 次）¥0」行）｜P1
- [x] 「整单备注」琥珀备忘块整块缺失（页面与 Order 模型均无 remark）｜原型 admin-billing.html:140 / 实现 server/src/main/java/com/sinomed/service/impl/OrderServiceImpl.java:187（orders.remark ≤200 字）+ web/src/pages/Billing/Settle/index.tsx:319（琥珀 TextArea 失焦保存 PUT /api/v1/order/remark）｜P1
- [ ] 结算头卡缺会员档位标签与接诊单号（仅掩码手机/内部单号/下单时间）｜原型 admin-billing.html:114 / 实现 web/src/pages/Billing/Settle/index.tsx:277｜P2
- [x] 应收合计缺「卡项抵扣已减；支付后余额」说明行｜原型 admin-billing.html:152 / 实现 web/src/pages/Billing/Settle/index.tsx:330（.sub 说明行：次卡实收 0/储值支付后余额/到店收银）｜P2
- [x] 队列单号格式：原型业务编号「20261008-006」，实现内部 id「#6」｜原型 admin-billing.html:144 / 实现 web/src/pages/Billing/Settle/index.tsx:195（队列行与结算头卡均 orderBusinessNo）｜P2
- [ ] 微信/支付宝仅记录支付方式（原型扫码收款；在线支付官方口径为规划）｜原型 admin-billing.html:155 / 实现 web/src/pages/Billing/Settle/index.tsx:257｜P2
- [ ] 四种支付图标配色统一 brand 单色（原型绿微/蓝支/褐现分色）｜原型 admin-billing.html:154 / 实现 web/src/pages/Billing/Settle/index.less:313｜P2

### desktop-workstation（7 条）
- [ ] 外设状态条为写死静态文案（原型实时连接态；壳侧 print_html/print_escpos/scanner_start 无状态查询命令）｜原型 desktop-workstation.html:179 / 实现 web/src/pages/Treat/Create/index.tsx:532（壳命令 desktop/src-tauri/src/main.rs:70）｜P1
- [ ] 接诊单开单表项目行/从卡项添加为「规划功能」占位壳｜原型 desktop-workstation.html:133 / 实现 web/src/pages/Treat/Create/index.tsx:460｜P2
- [ ] 应收合计+「收款并打印小票」为「规划功能」占位（收款在 Billing/Settle 页）｜原型 desktop-workstation.html:168 / 实现 web/src/pages/Treat/Create/index.tsx:481｜P2
- [ ] 顾客条卡项余次「规划功能」占位｜原型 desktop-workstation.html:131 / 实现 web/src/pages/Treat/Create/index.tsx:249｜P2
- [ ] 今日工作站统计：打印/扫码计数「规划」占位（仅接诊数落地）｜原型 desktop-workstation.html:175 / 实现 web/src/pages/Treat/Create/index.tsx:526｜P2
- [ ] 近三次诊疗署名显示顾客姓名（原型为诊疗医师）｜原型 desktop-workstation.html:161 / 实现 web/src/pages/Treat/Create/index.tsx:510｜P2
- [ ] 工作站默认窗口 1280×800（原型 1360×832@1440×900）｜原型 desktop-workstation.html:8 / 实现 desktop/src-tauri/src/config.rs:28｜P2

### admin-themes 设计系统（6 条）
- [ ] 小程序 tokens 失效风险：变量挂 `:root`（WXSS 不支持）无 `page` 兜底，13 处 var(--brand) 在小程序构建不可解析（仅 H5 可靠）｜原型 tokens.css:5 / 实现 app/src/tokens.css:5、app/src/pages/index/index.css:60｜P1
- [ ] 小程序无四主题切换（白标单色 BRAND_THEME，与四主题体系脱节）｜原型 tokens.css:23 / 实现 app/src/app.ts:21、app/config/brand.ts:13｜P2
- [ ] Kiosk 写死 data-theme="pine" 无切换能力｜原型 tokens.css:3 / 实现 kiosk/index.html:2｜P2
- [ ] Tablet 未设 data-theme 固定石墨，无切换机制｜原型 tokens.css:23 / 实现 tablet/src/main.tsx:5｜P2
- [ ] Ads 素材卡渐变硬编码 #353a37 不随主题变｜原型 tokens.css:19 / 实现 web/src/pages/Ads/Index/index.less:141｜P2
- [ ] Settle 页硬编码警示色 #a3542e/#f7ede4（tokens 体系外）｜原型 admin-billing.html:62 / 实现 web/src/pages/Billing/Settle/index.less:124｜P2

### mobile-home（7 条，全部已清）
- [x] 底部 tabbar（首页/预约/卡项/我的）整条缺失，app.config 无 tabBar、无「卡项」「我的」页面｜原型 mobile-home.html:119 / 实现 app/src/app.config.ts:16、app/src/app.config.ts:24、app/src/pages/card/index.tsx:10、app/src/pages/me/index.tsx:14（无图标纯文字标签；卡项/我的为规划占位与身份页）｜P0
- [x] 预约横幅语义不同：原型「明日 10:00 · 艾灸调理」预约提醒，实现为通用「在线预约」入口（跳 booking 已实现）｜原型 mobile-home.html:77 / 实现 app/src/pages/index/index.tsx:55、app/src/pages/index/index.tsx:85（本机未过期待到店预约显提醒态，口径：本机自助约期写 Taro storage `sinomed_booking_session`，booking 提交后写 app/src/pages/booking/index.tsx:78，切换 tab 回首页 useDidShow 重读）｜P1
- [x] 实现多出「白标自检」区块直接暴露在顾客首页（原型无此节）｜原型 mobile-home.html:118 / 实现 app/src/pages/me/index.tsx:72（挪至「我的」页，工程验收用，共享样式上提 app.css）｜P1
- [x] 品牌头右侧铃铛/头像圆钮缺失｜原型 mobile-home.html:72 / 实现 app/src/pages/index/index.tsx:76（有本机称呼显首字，无则显「客」，点击进「我的」）｜P2
- [x] 问候语无顾客称呼（「下午好，王女士」→「下午好，欢迎光临」）｜原型 mobile-home.html:70 / 实现 app/src/pages/index/index.tsx:74（称呼取本机自助约期留的称呼，无接口查询顾客档案故不造假）｜P2
- [x] 区块标题右侧「全部项目」链接缺失｜原型 mobile-home.html:82 / 实现 app/src/pages/index/index.tsx:99（上架项目超 4 项时显示，点击展开全量/收起）｜P2
- [x] 我的卡项余次为「规划功能」占位（按裁定：忠实占位，信息项不计缺陷）｜原型 mobile-home.html:101 / 实现 app/src/pages/index/index.tsx:119、app/src/pages/card/index.tsx:15（卡项 tab 同为规划占位 + 到店/致电查询指引）｜P2

### kiosk-menu（3 条）
- [ ] 购物篮为条件渲染，空选态整条不出现（原型常驻底栏含空态指引）｜原型 kiosk-menu.html:81 / 实现 kiosk/src/App.tsx:159｜P2
- [ ] 页面左右留白 40px→32px（四处同步偏移）｜原型 kiosk-menu.html:10 / 实现 kiosk/src/app.css:13｜P2
- [ ] 卡片封面由 flex 撑满（min-height 150px）改为固定 16:9｜原型 kiosk-menu.html:28 / 实现 kiosk/src/menu/menu.css:37｜P2

### tablet-screen（2 条）
- [ ] 广告页排版层缺失：原型整版设计稿（眉题/110px 大标题/文案/价格/品牌渐变大字「灸」）未实现，仅 object-fit 播放素材文件（数据模型无文案字段）｜原型 tablet-screen.html:47 / 实现 tablet/src/player/Carousel.tsx:42、tablet/src/types.ts:2｜P1
- [ ] 叫号条文案「前往 X」与原型「到 3 号诊室」不一致｜原型 tablet-screen.html:66 / 实现 tablet/src/player/CallOverlay.tsx:35｜P2

### 逐页核对结论（15/15）
1. **admin-customer**：11 条（P0 0/P1 1/P2 10）——骨架高度对齐（头卡/四格统计/时间线/持卡卡项均超原型），缺口在时间线字段与回访域细节。
2. **admin-diagnosis**：6 条（P0 0/P1 2/P2 4）——差距中等，四诊/取穴/方案全对齐；核心缺口是寸关尺分部录入与五行生克可视化。
3. **admin-dash-day**：4 条（P0 0/P1 1/P2 3）——三组中对齐最好，员工总结列模型按自有复盘数据模型重建。
4. **admin-items**：2 条（P0 0/P1 0/P2 2）——五列表格/封面/开关/操作/分页全一致，仅排序显示与时长字段。
5. **admin-orders**：5 条（P0 0/P1 2/P2 3）——骨架对齐但缺「查看」详情、项目列单卡项无数量，排序无保证。✅ 2026-10-11 全部 5 条已清（order_item 明细模型 + 查看弹窗 + 业务单号 + 倒序 + 列清理）。
6. **admin-ads**：4 条（P0 0/P1 0/P2 4）——单页四 tab + 左表右栏完全对齐，仅文案/交互呈现细节。
7. **admin-appearance**：2 条（P0 0/P1 0/P2 2）——四主题卡+实时预览逐元素还原，仅两处皮肤级细节。
8. **admin-booking**：7 条（P0 0/P1 3/P2 4）——原型单页看板被拆三页，缺「过号」状态与每时段限约规则。✅ 2026-10-12 P1 全清 + P2 2 条（stat4 面板、小程序联动面板），余 2 条卡点（疗程角标、医师名单无字段）
9. **admin-herbprescription**：11 条（P0 1/P1 7/P2 3）——差距最大：辨证条缺失、签名链/饮片检索/打印存方收费按钮组缺位；膏方为实现超集非差距。✅ P0 辨证摘要条已清（2026-10-11，联接诊单 diagnose/plan/acuCourse）。
10. **admin-billing**：7 条（P0 0/P1 2/P2 5）——结算主流程对齐；缺口集中在多项目单据数据模型（明细表退化单行、无整单备注）。✅ 2026-10-11 已清 4 条（明细多行表、整单备注、应收说明行、队列业务单号）；余会员档位/接诊单号头卡与支付图标配色 3 条 P2。
11. **desktop-workstation**：7 条（P0 0/P1 1/P2 6）——薄壳+Web 页骨架完整，唯一实质差距是外设状态条静态文案。
12. **admin-themes**：6 条（P0 0/P1 1/P2 5）——web 端四主题落地完整（tokens 同源+data-theme+antd token 双通道）；差距在非 web 端（小程序 :root 失效风险、kiosk/tablet 无切换）。
13. **mobile-home**：7 条（P0 1/P1 2/P2 4）——主体四节结构对齐，唯一 P0 是底部 tabbar 及两个 tab 页整体缺失。✅ 2026-10-11 全部 7 条已清（tabbar+卡项/我的页、预约横幅提醒态、白标自检挪「我的」、铃铛、称呼、全部项目、卡项占位）。
14. **kiosk-menu**：3 条（P0 0/P1 0/P2 2）——差距最小，五区块逐项对齐；手机号下单/回执两步为实现超集。
15. **tablet-screen**：2 条（P0 0/P1 1/P2 1）——轮播/pager/叫号条/断网角标全落地，唯一实质差距是单页广告排版层。

**修复优先级建议**：~~P0 2 条（herbprescription 辨证条、mobile-home tabbar）~~（已清）→ P1 中影响数据模型的（billing 多项目明细/整单备注、orders 多品项+查看、booking 过号+限约、diagnosis 寸关尺+五行图）→ 其余 P1 → P2 批量扫尾。修复后按 mockups README §2 口径重出截图验收。

## 未实现功能（audit-doc-consistency.md 点名；mockups README 状态为「部分实现」，缺口以上表核对为准）
- [ ] 预约挂号（设计原型：/screenshots/admin-booking.png）
- [ ] 中药处方（设计原型：/screenshots/…）
- [ ] 收费结算（设计原型：/screenshots/…）

## 安全与技术债
- [ ] 服务端接口层方法级权限强制（web.md:174：当前只做页面级隐藏）

## 在途工作（未提交，需先收口）
- 无（web `pnpm test` 存量红已于 2026-10-11 修复：jest.config.ts 改用 createConfig 手工别名绕开 configUmiAlias 的 esbuild-register（Node 24 崩溃点），移除脚手架 login 快照测试，新增 billing 请求契约测试 6 例，测试全绿）

验收：对应页面可实跑 + 后端接口有权限校验；测试绿才 push，commit 英文简短。
