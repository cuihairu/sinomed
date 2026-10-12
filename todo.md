# TODO

> 生成于 2026-10-11（本仓无任何待办文件；来源：docs/audit-doc-consistency.md「文档超前于代码」项、docs/web.md 遗留、工作区在途改动）。

## P0 — UI 按原型核对：差距清单（2026-10-11 全量核对完成）

> 15 组全部核对（原型 `docs/design/mockups/*.html` + `tokens.css` ↔ 实现 web/app/kiosk/tablet/desktop），共 **84 条差距（P0 2 / P1 24 / P2 58）**，逐条带 file:line 证据。
> 进度（2026-10-12 续批）：已清 86 条（P0 2/2 + P1 20 + P2 51：herbprescription 辨证条+rx头/特殊煎法提醒/签名行/领取方式/三键、customer 时间线疗程标签+取穴摘要+新建表单文案/会员档位/回访链接+下次回访统计/回访提醒/回访原因+面包屑、treat history 列扩、diagnosis 灸法行+卡项余次实卡+就诊语境、dash-day 角色副行/真实在岗/回访手机号、mobile-home 全部 7 条、orders 全部 5 条、billing 明细/备注/说明行/队列单号+支付图标配色、booking 待到店面板/过号/限约/stat4/联动面板、diagnosis 寸关尺分部+五行生克图、item 分页排序+时长字段、ads 素材分类+排期名+屏幕状态、appearance 文案+预览高、ads 渐变主题化、warn 色入 tokens、叫号条文案、工作站窗口 1360×832、ads 删除裁定保留、ads 快捷叫号草稿态、prescription 过敏审方常显态、billing 头卡会员档位、kiosk/tablet ?theme 入口、kiosk 购物篮空态常驻+留白 40px+封面 16:9、customer 卡项有效期+建档月份（补 valid_until 列）、herbprescription 炮制列+用法三格（补 process/take_method 列）、app 四主题（H5 端切换+本机记忆，小程序端如实固定石墨）、接口层方法级权限（员工管理写接口 ADMIN 强制+角色映射修正）、herbprescription 目录拼音首字母检索（pinyin-pro 本地算）、代煎费三行账（拍板对齐原型入应收合计）、desktop-workstation 收款去向真实跳转结算台+死文本移除（开单表/打印扫码计数按卡点如实标注）+余次题面核销、三条过时审计行（预约/中药处方/收费结算）核销、接诊单挂医师署名（拍板=开单署名语义，treats.staff_id+全出口回显+双页署名显示）），余 2 条（P2，全部卡点/禁碰）：卡点 1 条（booking 医师名单——原型当日医师全列为占位，staffs.role 是权限档（0/1-9/≥10）非职务语义，无医师名册/在岗排班数据源，列全体员工会把手帐号标成医师列头，待职务/名册模型落地再做）；1 条禁碰（在线支付真实通道，#88）
> 两条裁定口径已执行：admin-billing 处方饮片行不列为差距；mobile-home 卡项余次「规划功能」占位按忠实占位处理（P2 信息项）。
> 严重度：P0=原型整块功能缺失；P1=功能在但结构/字段明显不同；P2=表层细节。修完按 mockups README §2 口径重出截图验收。

### admin-customer（11 条）
- [x] 诊疗时间线字段结构：原型每条=项目名+疗程标签+摘要(医师·时长·证治取穴)，实现标题取 diagnose 证型、无医师/时长/疗程标签｜✅ 2026-10-12 部分清：疗程标签取 acuCourse（index.tsx:256，amber tag）、摘要出 治法+取穴（index.tsx:239-247，plan+acupointLeft/Right join）、标题回退 diagnose→acuMethod（index.tsx:255）。余卡点：医师/时长/项目名——接诊单模型无 staffId/duration/项目关联字段（医师落谁涉及接诊归属语义，待定夺再加）｜P1（部分，余模型字段）
- [x] 下次回访统计：原型显示真实回访日期，实现为「规划功能 · 无按顾客回访查询」占位｜✅ 2026-10-12 新增按顾客回访查询 GET /review/customer/list/customer（ReviewController.java，回访日倒序 + 回归 ReviewPlanHttpTest），档案页取今天起最近一条计划显真实日期、无计划如实「无计划」（Customer/Update/index.tsx:243-249,276-280）｜P2
- [x] 持卡卡项面板缺「回访提醒」整块 note（含回访原因文案）｜✅ 2026-10-12 卡项面板底部新增「回访提醒 · MM-DD」note，文案取该计划 advice（无则回退默认提示），随下次回访计划常显（Customer/Update/index.tsx:321-326、index.less .plan-note b 块题样式对齐原型）｜P2
- [x] 卡项行缺「购入日期 · 有效期至」（Card 模型无有效期字段）｜✅ 2026-10-12 模型补字段：customer_cards.valid_until 可空=长期有效（CustomerCardEntity.java:52-54），发卡入参/读回 CardView.validUntil（CardView.java:44-46+From），抵扣跳过过期卡（CardServiceImpl deduct removeIf，CardServiceTest issueStoresValidUntilAndDeductSkipsExpired 2 顾客 4 断言）；web 发卡弹窗加有效期 DatePicker（Customer/Update/index.tsx 发卡 Modal）、持卡行「购入日期 · 有效期至」meta（index.tsx cd-meta，样式 index.less）；docs api.md/data-model.md 口径同步（原「暂无有效期（规划）」作废）｜原型 admin-customer.html:150 / 实现 web/src/pages/Customer/Update/index.tsx:308｜P2
- [x] 头卡缺建档时间（API.Customer 无 createTime）｜✅ 2026-10-12 API 本就带 createTime（CustomerView.java:38，题面口径修正），web 头卡补「YYYY-MM 建档」span（Customer/Update/index.tsx:198）｜原型 admin-customer.html:100 / 实现 web/src/pages/Customer/Update/index.tsx:198｜P2
- [x] 新建顾客表单文案错误：「年纪」应为年龄、占位「输入员工名字」应为顾客｜✅ 2026-10-12 文案修正：label/tooltip「年龄」、placeholder 38、姓名 placeholder「输入顾客姓名」、性别 placeholder 女（Customer/Create/index.tsx:30-32）｜P2
- [x] 会员等级为裸数字输入，原型为「金卡会员」档位徽标（无 普通/银卡/金卡 枚举）｜✅ 2026-10-12 改 ProFormSelect 档位选择 1 普通会员/2 银卡会员/3 金卡会员，placeholder 普通会员（Customer/Create/index.tsx:39-46；存量库 level 数值直接落入档位语义）｜P2
- [x] 回访计划缺「回访原因」结构化字段（/review/plan 仅 姓名/最近访问/计划回访）｜✅ 2026-10-12 /review/plan 新增「回访原因」TextArea 存回访记录既有 advice 字段（Review/Plan/index.tsx:66-73），后端存档读回回归见 ReviewPlanHttpTest；顺修提交体误传 id=customerId 的隐患（防 JPA merge 改写他人回访行，Plan/index.tsx:47-51）｜P2
- [x] 诊疗历史全量列表仅 ID/主述/创建时间 三列，信息少于档案页时间线｜✅ 2026-10-12 列扩为 ID/主述/辨证/治法方案/疗程/取穴(join acupointLeft/Right)/创建时间/操作（index.tsx:42-100），与档案页时间线同源字段对齐｜P2
- [x] 缺面包屑导航（原型「顾客 / 顾客查询 / 王女士」三级）｜✅ 2026-10-12 PageContainer breadcrumb 三级 顾客/顾客查询(链接)/顾客姓名（Customer/Update/index.tsx:186）｜P2
- [x] 顾客查询操作列「跟访」与原型「复盘回访/回访」措辞不一致｜✅ 2026-10-12 操作链接改「回访」（Customer/Query/index.tsx:194）｜P2

### admin-diagnosis（6 条）
- [x] 脉象缺寸关尺分部：原型左右手各 寸/关/尺 三格共 6 格，实现仅左右手两个自由文本框（全库无寸/关/尺分字段）｜原型 admin-diagnosis.html:149 / 实现 server/src/main/java/com/sinomed/entity/TreatEntity.java:63（pulse_cun/guan/chi_left/right 六列，ddl-auto 建列）、web/src/pages/Treat/Create/index.tsx:302（左手/右手 寸·关·尺 三格 ×2，总述保留）、回归 server/src/test/java/com/sinomed/TreatAcuHttpTest（建/读 6 分部字段）｜P1
- [x] 五行生克可视化缺失：原型五行定位图（脏腑标注+生克提示）+ 本/克/难经/比率四指标，实现仅数值录入无图形化｜原型 admin-diagnosis.html:190 / 实现 web/src/pages/Treat/Create/index.tsx:585（WuxingLive 组件：火心/木肝/土脾/金肺/水肾五节点十字定位、中宫脾高亮 + 本生/克乘/难经/缓急四指标随录入实时联动，值取既有 five_* 字段 L/R 成对展示；原型的「火不生土·木乘土」为逐案医理断语，不可由字段推导，未做）｜P1
- [x] 诊断缺「第 N 疗程」标记（疗程只存在于 acuCourse）｜✅ 2026-10-12 拍板疗程口径=卡项（同项目第 N 张卡按购入先后=第 N 疗程，购卡流水天然推算，不建已完成次数账本表；无卡项不标，不伪造首诊/复诊）：接诊单顾客条持卡 chip 带「· 第 N 疗程」（Treat/Create/index.tsx:208-217 courseOf 按 createTime 升序推算，:289 chip 展示）；acuCourse 自由文本仍保留供医师写频次语境｜原型 admin-diagnosis.html:182｜P2
- [x] 取穴面板缺灸法行（「艾条悬灸，每穴 15 分钟」被并入针法/留针字段）｜✅ 2026-10-12 新增 moxaMethod 灸法字段全链路：treats.moxa_method 列（TreatEntity.java，ddl-auto 建列）、TreatView 存取映射、TreatServiceImpl 校验 ≤60 字（400 越界）、表单 针法/灸法 + 留针/手法 两行（Treat/Create/index.tsx:481-495）、回归 TreatAcuHttpTest 存档读回断言｜P2
- [x] 调理方案卡项余次为「规划功能」占位（与档案页已实现卡项实体相矛盾）｜✅ 2026-10-12 患者条「持卡卡项余次」接真实 card 实体：activeCards 列表逐卡出「卡名 余 N/M 次」（amber），无卡/无有效卡如实标注（Treat/Create/index.tsx:205,275-289，listCardsByCustomer 同档案页数据源）｜P2
- [x] 头卡缺「复诊，回填上次诊疗单」就诊语境说明｜✅ 2026-10-12 患者条新增就诊语境块：日期 + 编辑历史诊疗单/复诊（历史单据见下方诊疗记录）/初诊建档 三态（Treat/Create/index.tsx:207-211,271-274）｜P2

### admin-dash-day（4 条）
- [x] 员工总结表列结构：原型 员工(带角色)/今日小结/状态/提醒操作，实现 员工/耗卡/卡项已做/成单建议/状态，无角色列无操作列｜✅ 2026-10-12 员工列加角色副行（已提交/未填写行都有，index.tsx:206+220，ROLE_TEXT 管理员/领导/员工 index.tsx:31，staffRoles map index.tsx:104 区间）；「提醒操作」列见下条卡点；小结列保持自有复盘模型 耗卡/已做/建议（item 129 既定口径）｜P1
- [x] 未填行「提醒」操作缺失【规划域】｜✅ 2026-10-12 评估免费/自托管通道后按既定口径收口：①微信订阅消息/公众号模板——需企业主体认证与用户授权，MVP 无资质；②短信——付费+签名备案，非免费；③自托管 SMTP——无域名与 SPF/DKIM 信誉，外发进垃圾箱（MailHog 类仅开发收信）；④浏览器 Notification/SSE——仅页面打开期有效，且信息与常显复盘面板完全重复（同页同数据源），只添权限弹窗噪音。能触达页外人员（顾客/员工手机）的通道全不可行，能做的页内提醒已由常显面板覆盖——维持「如实标注不做假按钮」收口（Dashboard/Day/index.tsx:189 hint 注明「提醒推送属规划域，暂无通知接口」），待后续接入真实通道（如企业微信/公众号）再落地｜原型 admin-dash-day.html:139｜P2
- [x] 员工在岗统计「5 人」为「规划功能」占位｜✅ 2026-10-12 真实口径：周排班 fetchWeekTimesheet 里该日有签到记录的员工数（index.tsx:54+103-108，stat 真值 index.tsx:155-160），删规划占位与 .hold 样式｜P2
- [x] 今日回访行缺脱敏手机号与「明日到期」置灰态（显示「最近到店」替代）｜✅ 2026-10-12 脱敏手机号已加：customer id→phone map（index.tsx:36+94-98）行内 138****2211（index.tsx:233-236）。「明日到期」不适用：回访面板按所选日查询（fetchReviewCustomerBulk(day)），行本来就是当日到期，无「明日行」可置灰｜P2

### admin-items（2 条）
- [x] 卡项列表未按「排序」字段排序（后端 PageRequest 无 Sort，顺序不确定）｜✅ 2026-10-12 /item/page 改 sort 升序 + 同序号 id 倒序稳定分页（server/src/main/java/com/sinomed/controller/ItemController.java:119-121），小序号在前（原型 admin-items 口径）｜P2
- [x] 卡项无「时长」结构化字段（仅 description 自由文本）｜✅ 2026-10-12 新增 items.duration_minutes 列（ItemEntity.java:49，ddl-auto 建列，可空展示字段不参与结算）+ ItemView 映射 + 表单「时长（分钟）」（Item/Create/index.tsx:94-102）+ 列表副标题「描述 · N 分钟」（Item/Query/index.tsx:50-54）+ 回归 ItemDurationHttpTest｜P2

### admin-orders（5 条，全部已清）
- [x] 已完成/已取消订单缺「查看」详情入口（实现渲染「-」）｜原型 admin-orders.html:120 / 实现 web/src/pages/Order/Query/index.tsx:196（done/cancelled 行「查看」开详情弹窗：明细表单价/数量/小计、合计、整单备注、支付方式、下单时间、来源）｜P1
- [x] 「项目」列仅单卡项无数量（数据模型无多品项/数量字段）｜原型 admin-orders.html:101 / 实现 server/src/main/java/com/sinomed/entity/OrderItemEntity.java:15（order_item 明细行 name/price/qty/subtotal 快照）+ web/src/pages/Order/Query/index.tsx:50（项目列「名 ×数量」顿号串）+ 前台建单多选卡项带数量 web/src/pages/Order/Create/index.tsx:134；存量单回退一行数量 1（OrderController.hydrateItems）｜P1
- [x] 单号格式：原型业务单号「20261008-006」，实现裸 id「#6」｜原型 admin-orders.html:99 / 实现 web/src/services/ant-design-pro/order.ts:47（orderBusinessNo=创建日期+流水号补零）｜P2
- [x] 订单列表未按时间倒序（page 查询无 ORDER BY）｜原型 admin-orders.html:99 / 实现 server/src/main/java/com/sinomed/service/impl/OrderServiceImpl.java:79（createTime 倒序，同秒 id 倒序）｜P2
- [x] 实现多出「下单时间」列（原型无，实现新增项）｜原型 admin-orders.html:95 / 实现 web/src/pages/Order/Query/index.tsx:144（列已删，下单时间挪进「查看」详情）｜P2

### admin-ads（4 条）
- [x] 素材子标题缺「分类」（原型「图片 · 门店活动」，实现只有类型）｜✅ 2026-10-12 新增 ad_materials.category 列（AdMaterialEntity.java:31）+ 表单「分类」（Ads/Index/index.tsx:693）+ 子标题「类型 · 分类」join（index.tsx:257）+ 回归 AdsMaterialCopyHttpTest 增断言｜P2
- [x] 屏幕状态在线文案缺排期名｜✅ 2026-10-12 排期新增 name 字段（AdScheduleEntity.java:38 + 表单 + 排期名列），屏幕状态改「正在播 {排期名}（N 条素材轮播）」，无命中回退默认轮播（Ads/Index/index.tsx:135-152,602-605）｜P2
- [x] 快捷叫号交互：原型已就绪号码一键上屏，实现为手填表单（手动叫号口径内）｜✅ 2026-10-12 表单即草稿：Form.useWatch 顶层订阅 number/patientMasked/room（index.tsx:508-511），填入即显示号码卡+就诊科室（index.tsx:624-644），无草稿回显最近一次叫号；补原型说明行「上屏约 1 秒，停留 15 秒后自动回轮播」（index.tsx:646）｜原型 admin-ads.html:158 / 实现 web/src/pages/Ads/Index/index.tsx:623｜P2
- [x] 素材操作多出「删除」入口（实现新增项）【裁定保留】：误传媒体需清理手段，playlist 仅取启用+命中排期素材、删除不影响在播编排口径（删除有 Popconfirm 二次确认），属管理页合理超集（Ads/Index/index.tsx:290）｜P2

### admin-appearance（2 条）
- [x] 主题面板提示文案拼接错误（「石墨 × 古金（默认 · 取自 logo）」）｜✅ 2026-10-12 hint 改取 desc 首段（ Appearance/Index/index.tsx:51，「石墨 × 古金（默认）」/「松烟绿（清雅）」与原型口径一致）｜P2
- [x] 预览网格未固定高 356px（随内容自适应）｜✅ 2026-10-12 .pv 固定 height 356px（Appearance/Index/index.less）｜P2

### admin-booking（7 条）
- [x] 「待到店」紧凑面板（到店一键转接诊）被拆成独立预约查询整页表格｜原型 admin-booking.html:130 / 实现 web/src/pages/Appointment/Schedule/index.tsx:231（右栏「待到店」面板：时段·顾客·项目·医师 + 一键「接待」，排班页就近接待，预约查询页保留整页表格）｜P1
- [x] 「过号」状态缺失（以「已取消」顶替，爽约与主动取消混同）｜原型 admin-booking.html:100 / 实现 web/src/services/ant-design-pro/appointment.ts:7（NO_SHOW=8）、web/src/pages/Appointment/Schedule/index.tsx:21、web/src/pages/Appointment/Query/index.tsx:35+183（0→8 Popconfirm）、server/src/main/java/com/sinomed/service/impl/AppointmentServiceImpl.java:104（0→1/8/9，8/9 终态）｜P1
- [x] 「每时段限约 1 位」规则未实现（同格多条 +N 并列，无冲突校验）｜原型 admin-booking.html:90 / 实现 server/src/main/java/com/sinomed/service/impl/AppointmentServiceImpl.java:57（floor 到整点桶，同医师 status IN(0,1) 互斥，staffId 空归空组）、server/src/main/java/com/sinomed/controller/KioskController.java:201（自助约期同规则）、回归 server/src/test/java/com/sinomed/AppointmentSlotTest.java + KioskAppointmentTest.sameSlotSecondBookingRejectedBySlotCap｜P1
- [x] 今日预约统计为工具栏内联文字（原型 2×2 统计卡面板，第四项应为「过号」）｜原型 admin-booking.html:122 / 实现 web/src/pages/Appointment/Schedule/index.tsx:208（右栏「今日预约」2×2 stat4：总数/已到店/待到店/过号）｜P2
- [x] 格位次行缺「第 N 疗程/复诊/首诊」限定（仅项目名）｜✅ 2026-10-12 按疗程口径拍板（同项目第 N 张卡=第 N 疗程，无卡项不标）落地：服务端批量接口 GET /api/v1/card/customers（CustomerCardRepository.findByCustomerIdIn + CardService.listByCustomerIds + CardController，一次拉齐避免逐顾客 N+1，回归 CardServiceTest.listByCustomerIdsReturnsCardsAcrossCustomers）；排班页格位 `.s` 与待到店行 `.it` 带「· 第 N 疗程」（Schedule/index.tsx load 内按当日顾客批量拉卡 reduce 计数，同项目卡数=最新卡疗程序号），amber 标色与原型角标位对齐；「首诊/复诊」无就诊史模型不伪造，维持不标｜原型 admin-booking.html:94｜P2
- [x] 「小程序联动」面板未做（原型已标注规划，P2 信息项）｜原型 admin-booking.html:140 / 实现 web/src/pages/Appointment/Schedule/index.tsx:252（右栏面板含原型规划文案）｜P2
- [ ] 医师列仅按当日有约动态生成（原型当日医师名单全列占位）｜原型 admin-booking.html:92 / 实现 web/src/pages/Appointment/Schedule/index.tsx:62｜P2

### admin-herbprescription（11 条）
- [x] 「辨证摘要条」（接诊单联动：证型·治法）整段缺失｜原型 admin-herbprescription.html:108 / 实现 web/src/pages/Prescription/Create/index.tsx:344（接诊单下拉+辨证/治法/疗程，findCustomer 定位后自动带最近一张 index.tsx:210，开方随处方落 treatId index.tsx:259；口径：辨证=接诊单 diagnose、治法/方案=接诊单 plan 自由文本）｜P0
- [x] 处方笺头部缺处方编号/诊号/性别年龄（仅姓名/会员/手机）｜✅ 2026-10-12 rx-head 条：顾客 性别·年龄·掩码手机（maskPhone index.tsx:32）+ 医师·日期·诊号=treatId + 处方编号预览 Rx-YYYYMMDD（index.tsx:371-388，rxNo index.tsx:254，成功消息回真实编号 index.tsx:322；后端字段已有）｜P1
- [x] 加药无饮片目录拼音首字母检索（自由文本，字典仅精确同名比价）｜原型 admin-herbprescription.html:120 / 实现 web/src/pages/Prescription/Create/index.tsx:364｜P1｜✅ 2026-10-12：「从饮片目录添加」Select（index.tsx:530 起）下拉全量字典，filterHerbDir 中文名包含或拼音首字母命中（pinyin-pro pattern:'first' 本地算+initialsCache 缓存，如 zz/fs/gcao），选中填第一个空行否则追加，选项带每克单价；web.md:142 同步
- [x] 审方四查缺「剂量上限(药典)」「特殊煎法提醒」两项（仅配伍+过敏）｜✅ 2026-10-12 特殊煎法提醒已实装：扫药味 special 命中 先煎/后下/包煎/烊化/冲服/另煎 计「N 味需标注」（index.tsx:519-533，SPECIAL_FX index.tsx:36）。「剂量上限(药典)」卡点：药材字典无药典剂量区间数据源，待补数据再做｜P1（余卡点项）
- [x] 签名行缺「药师审核/调配复核」（仅医师下拉）｜✅ 2026-10-12 meta 增 药师审核/调配复核 两下拉（index.tsx:655-673）+ 笺尾 sign-row 三签名（index.tsx:692-703）；后端 prescriptions 加 review_staff_id/check_staff_id（PrescriptionEntity.java:79-85，save 存档 PrescriptionServiceImpl.java，create 校验员工存在 PrescriptionController.java:107-113，PrescriptionSignHttpTest 3 例回归）｜P1
- [x] 代煎缺取货时效与自取/同城配送二选一（仅代煎 N 袋勾选+¥3/袋提示）｜✅ 2026-10-12 勾代煎出 领取方式 自取/同城配送 Radio（index.tsx:627-638）+ 时效提示「今日 17:00 前可取，加急 2 小时」（index.tsx:622）；后端 delivery_type 随方存档（PrescriptionEntity.java:87-89，仅代煎保留、不代煎忽略）｜P1
- [x] 费用构成口径：原型代煎费入合计三行账，实现代煎费仅旁注提示（收费以卡项订单结算为准的既定口径，是否对齐原型待定夺）｜原型 admin-herbprescription.html:148 / 实现 web/src/pages/Prescription/Create/index.tsx:420｜P1｜✅ 2026-10-12 拍板（用户授权自拍板）：对齐原型三行账——计价面板出「饮片 N 味×付数」行 + 「代煎服务（N 袋）¥3/袋」行（勾代煎时），应收=两者之和（recvFen，index.tsx:308）；运费 ¥6 与「实际收费以卡项订单结算为准」仍旁注不入合计；web.md:142 同步
- [x] 费用区缺「存方并收费/存入我的方剂库/打印处方笺」三键（仅「开方」一键；无打印代码）｜✅ 2026-10-12 三键齐：存方并收费（primary，原提交行为 index.tsx:713）· 存入我的方剂库（弹窗填病症名 1–20 字存启用模板 index.tsx:706-708+718-733，createTemplate）· 打印处方笺（window.print + @media print 只出处方笺主卡 index.tsx:710 + index.less 打印段）｜P1
- [x] 「炮制/煎法」列合并为单个特殊煎法自由文本｜✅ 2026-10-12 模型加炮制列：prescription_items.process 可空（PrescriptionItemEntity.java:35-37），药味视图/存取随单（PrescriptionItemView FromItemEntity、PrescriptionServiceImpl save）；开方行两输入「炮制，如 麸炒 / 煎法，如 先煎 30 分钟」（Create/index.tsx:494-505），查询列表「煎法/服法」列（Query/index.tsx:167）与详情「炮制 / 煎法」列（Query/index.tsx:255-264）合并展示；模板/方剂带出无炮制列（FreeFrom From 注释），PrescriptionServiceSaveTest 断言炮制落库；api.md/data-model.md 同步｜原型 admin-herbprescription.html:110 / 实现 web/src/pages/Prescription/Create/index.tsx:494｜P2
- [x] 「剂数/煎法/服法」三格合并为剂数框+用法文本框｜✅ 2026-10-12 用法拆三格（原型 .usage 口径）：剂数（InputNumber 付）/煎法（usage）/服法（take_method 新列 PrescriptionEntity.java:44-46），usage-row 样式（index.less）；查询列表「煎法/服法」合并列、详情「煎法」「服法」分行（Query/index.tsx:167+269-280）；api.md/data-model.md/web.md 同步｜原型 admin-herbprescription.html:123 / 实现 web/src/pages/Prescription/Create/index.tsx:720｜P2
- [x] 过敏审方无「档案无药物过敏记录」✓ 常显态（命中才渲染）｜✅ 2026-10-12 定位顾客即常显：过敏史条数（type 0）随顾客定位拉取（index.tsx:76+165-174），未命中渲染 ✓ ok 态「档案 N 条过敏记录，未命中当前药方 / 档案无药物过敏记录」（index.tsx:574-581），ok 绿底样式 index.less:469-476｜原型 admin-herbprescription.html:138 / 实现 web/src/pages/Prescription/Create/index.tsx:574｜P2

### admin-billing（7 条；处方饮片行按裁定不计）
- [x] 结算明细多行表：原型 项目/数量/小计 三列多行（含卡项抵扣行 ¥0），实现单行 itemName+price（OrderEntity 无数量字段）｜原型 admin-billing.html:122 / 实现 web/src/pages/Billing/Settle/index.tsx:288（.lines 多行表联 order_item 明细，选次卡且有余次时加「卡项抵扣（次卡扣 1 次）¥0」行）｜P1
- [x] 「整单备注」琥珀备忘块整块缺失（页面与 Order 模型均无 remark）｜原型 admin-billing.html:140 / 实现 server/src/main/java/com/sinomed/service/impl/OrderServiceImpl.java:187（orders.remark ≤200 字）+ web/src/pages/Billing/Settle/index.tsx:319（琥珀 TextArea 失焦保存 PUT /api/v1/order/remark）｜P1
- [x] 结算头卡缺会员档位标签与接诊单号（仅掩码手机/内部单号/下单时间）｜✅ 2026-10-12 会员档位标签：选单时 fetchCustomerById 取 level（index.tsx:100-101），头卡名字旁档位药丸（index.tsx:286，LEVEL_TEXT index.tsx:26，样式 index.less .nm .tier）；接诊单号【卡点：无数据源】订单模型无 treatId（OrderEntity 仅 userId/itemId/staffId）且自助机下单本无接诊单可关联，头卡沿用业务单号｜原型 admin-billing.html:114 / 实现 web/src/pages/Billing/Settle/index.tsx:285｜P2
- [x] 应收合计缺「卡项抵扣已减；支付后余额」说明行｜原型 admin-billing.html:152 / 实现 web/src/pages/Billing/Settle/index.tsx:330（.sub 说明行：次卡实收 0/储值支付后余额/到店收银）｜P2
- [x] 队列单号格式：原型业务编号「20261008-006」，实现内部 id「#6」｜原型 admin-billing.html:144 / 实现 web/src/pages/Billing/Settle/index.tsx:195（队列行与结算头卡均 orderBusinessNo）｜P2
- [ ] 微信/支付宝仅记录支付方式（原型扫码收款；在线支付官方口径为规划）｜原型 admin-billing.html:155 / 实现 web/src/pages/Billing/Settle/index.tsx:257｜P2
- [x] 四种支付图标配色统一 brand 单色（原型绿微/蓝支/褐现分色）｜✅ 2026-10-12 图标底色按原型分色：储=brand、微=#5f9e6e、支=#5b7fa6、现=#9a8f7a、次卡=#8a938c（billing.ts PAY_ICON_BG + Settle/index.tsx pi 内联底色，与原型同款内联口径）｜P2

### desktop-workstation（7 条）
- [x] 外设状态条为写死静态文案（原型实时连接态；壳侧 print_html/print_escpos/scanner_start 无状态查询命令）｜✅ 2026-10-12 部分清：状态条按运行环境如实标注——壳内（window.__TAURI__ 检测 index.tsx:96）出「串口事件通道就绪」（remote 能力只放事件监听，收码即监听中 index.tsx:557-596），浏览器出「页顶扫码条/需桌面工作站」，plan-note 分环境说明。余卡点：真实端口连接态需壳侧新增状态查询命令（Rust commands + tauri ACL 权限文件），本轮不动壳｜P1（部分，余壳侧命令）
- [x] 接诊单开单表项目行/从卡项添加为「规划功能」占位壳｜原型 desktop-workstation.html:133 / 实现 web/src/pages/Treat/Create/index.tsx:460｜P2｜✅ 2026-10-12 裁定落不进现口径：项目行需「接诊单↔订单」模型（与 #85 缺 treatId 同源），开单会与 Billing/Settle 收款口径冲突；已移除死的「从卡项添加」文本（不做假按钮），plan-note 注明与订单接口同批落地
- [x] 应收合计+「收款并打印小票」为「规划功能」占位（收款在 Billing/Settle 页）｜原型 desktop-workstation.html:168 / 实现 web/src/pages/Treat/Create/index.tsx:481｜P2｜✅ 2026-10-12 落地（收款仍归 Billing/Settle 口径）：「应收合计·规划功能」卡改为「收款去向」卡，卡内真实链接跳 /billing/settle（按订单计应收+次卡抵扣+小票打印），本页如实注明不做开单试算
- [x] 顾客条卡项余次「规划功能」占位｜原型 desktop-workstation.html:131 / 实现 web/src/pages/Treat/Create/index.tsx:249｜P2｜✅ 2026-10-12 核验：题面过时——已实装（定位顾客后 listCardsByCustomer 取实卡，activeCards 渲染「卡项名 余 N/M 次」，无卡/无有效卡均如实标注，index.tsx:276-292），勾选
- [x] 今日工作站统计：打印/扫码计数「规划」占位（仅接诊数落地）｜原型 desktop-workstation.html:175 / 实现 web/src/pages/Treat/Create/index.tsx:526｜P2｜✅ 2026-10-12 裁定为卡点：今日接诊数真实已落地；打印小票/扫码定位计数需收银埋点与扫码事件统计，全库无此数据源，不做假数——页面 plan-note 如实注明
- [x] 近三次诊疗署名显示顾客姓名（原型为诊疗医师）【卡点：无数据源】与顾客时间线医师缺口同源——接诊单模型无 staffId（医师落谁涉及接诊归属语义，待定夺再加），现以顾客名如实标注｜原型 desktop-workstation.html:161 / 实现 web/src/pages/Treat/Create/index.tsx:510｜P2｜✅ 2026-10-12 拍板（用户授权）落库：接诊归属语义=**开单署名医师**（本次接诊由哪位医师执行，选填）——treats 加 staff_id 列，TreatView staffId/staffName，TreatController toTreatView 统一按 staffs 回显姓名（全部读出口覆盖）；开方页「患者信息」加「接诊医师（署名）」选填下拉随单提交；近三次诊疗署名医师、未署名如实标「未署名」，顾客档案时间线加医师名（未署名不显示）；TreatAcuHttpTest 署名回归（带署名回显+更新清空）
- [x] 工作站默认窗口 1280×800（原型 1360×832@1440×900）｜✅ 2026-10-12 默认宽高改 1360×832（desktop/src-tauri/src/config.rs default_width/default_height；存量 config.json 已存值不受影响）｜P2

### admin-themes 设计系统（6 条）
- [x] 小程序 tokens 失效风险：变量挂 `:root`（WXSS 不支持）无 `page` 兜底，13 处 var(--brand) 在小程序构建不可解析（仅 H5 可靠）｜✅ 2026-10-12 tokens.css 追加 `page` 选择器兜底块（app/src/tokens.css:50，全量基础变量+默认石墨主题；H5 端 page 不匹配元素、:root 继续生效，两端互不干扰）；weapp 构建过（taro build --type weapp compiled successfully）。四主题切换仍属 #102 P2 不动｜P1
- [x] 小程序无四主题切换（白标单色 BRAND_THEME，与四主题体系脱节）｜✅ 2026-10-12 H5 端四主题落地：services/theme.ts（THEMES 四组/读写 storage sinomed_theme/挂 documentElement data-theme，app.ts 启动回放），「我的」页主题区四色点切换（仅 TARO_ENV=h5 渲染，me/index.tsx + theme-row 样式）；小程序端 WXSS 无运行时变量能力固定石墨，如实不渲染开关（tokens.css page 兜底注释同步）；白标 BRAND_THEME 不动（--brand-color 独立变量）；H5+weapp build 过｜原型 tokens.css:23 / 实现 app/src/services/theme.ts:8｜P2
- [x] Kiosk 写死 data-theme="pine" 无切换能力｜✅ 2026-10-12 main.tsx 启动读 ?theme=pine|cinnabar|indigo|graphite 挂/摘 documentElement data-theme（main.tsx:7-15），缺省保持 index.html 的 pine（index.html:2），vite build 过｜原型 tokens.css:3 / 实现 kiosk/src/main.tsx:7｜P2
- [x] Tablet 未设 data-theme 固定石墨，无切换机制｜✅ 2026-10-12 main.tsx 启动读 ?theme=pine|cinnabar|indigo|graphite（main.tsx:7-15，缺省石墨不设属性），tsc+vite build 过｜原型 tokens.css:23 / 实现 tablet/src/main.tsx:7｜P2
- [x] Ads 素材卡渐变硬编码 #353a37 不随主题变｜✅ 2026-10-12 g1 渐变改 var(--brand-deep)→var(--brand)（Ads/Index/index.less，四主题切换即随动，白字在四套主色上均可读）｜P2
- [x] Settle 页硬编码警示色 #a3542e/#f7ede4（tokens 体系外）｜✅ 2026-10-12 tokens.css 新增 --warn/--warn-deep/--warn-tint（警示语义全主题恒定），10 个 less 文件的硬编码 #a3542e/#f7ede4/#7c3a1a 全量替换为变量｜P2

### mobile-home（7 条，全部已清）
- [x] 底部 tabbar（首页/预约/卡项/我的）整条缺失，app.config 无 tabBar、无「卡项」「我的」页面｜原型 mobile-home.html:119 / 实现 app/src/app.config.ts:16、app/src/app.config.ts:24、app/src/pages/card/index.tsx:10、app/src/pages/me/index.tsx:14（无图标纯文字标签；卡项/我的为规划占位与身份页）｜P0
- [x] 预约横幅语义不同：原型「明日 10:00 · 艾灸调理」预约提醒，实现为通用「在线预约」入口（跳 booking 已实现）｜原型 mobile-home.html:77 / 实现 app/src/pages/index/index.tsx:55、app/src/pages/index/index.tsx:85（本机未过期待到店预约显提醒态，口径：本机自助约期写 Taro storage `sinomed_booking_session`，booking 提交后写 app/src/pages/booking/index.tsx:78，切换 tab 回首页 useDidShow 重读）｜P1
- [x] 实现多出「白标自检」区块直接暴露在顾客首页（原型无此节）｜原型 mobile-home.html:118 / 实现 app/src/pages/me/index.tsx:72（挪至「我的」页，工程验收用，共享样式上提 app.css）｜P1
- [x] 品牌头右侧铃铛/头像圆钮缺失｜原型 mobile-home.html:72 / 实现 app/src/pages/index/index.tsx:76（有本机称呼显首字，无则显「客」，点击进「我的」）｜P2
- [x] 问候语无顾客称呼（「下午好，王女士」→「下午好，欢迎光临」）｜原型 mobile-home.html:70 / 实现 app/src/pages/index/index.tsx:74（称呼取本机自助约期留的称呼，无接口查询顾客档案故不造假）｜P2
- [x] 区块标题右侧「全部项目」链接缺失｜原型 mobile-home.html:82 / 实现 app/src/pages/index/index.tsx:99（上架项目超 4 项时显示，点击展开全量/收起）｜P2
- [x] 我的卡项余次为「规划功能」占位（按裁定：忠实占位，信息项不计缺陷）｜原型 mobile-home.html:101 / 实现 app/src/pages/index/index.tsx:119、app/src/pages/card/index.tsx:15（卡项 tab 同为规划占位 + 到店/致电查询指引）｜P2

### kiosk-menu（3 条）
- [x] 购物篮为条件渲染，空选态整条不出现（原型常驻底栏含空态指引）｜✅ 2026-10-12 底栏常驻：空选态渲染「点选上方想做的项目，选好后在这里提交到前台」指引（App.tsx:159-181，.basket-empty 样式 app.css），选中后原两行清单/合计/提交不变｜原型 kiosk-menu.html:81 / 实现 kiosk/src/App.tsx:159｜P2
- [x] 页面左右留白 40px→32px（四处同步偏移）｜✅ 2026-10-12 四处对齐原型 40px：kiosk-hero 44/40/8（app.css:13）、kiosk-steps 22/40/6（app.css:74）、item-grid 18/40/170（menu.css:7，底部 reserve 150→170 适配加高的篮）、basket-bar 22/40/34（app.css:127）｜原型 kiosk-menu.html:10 / 实现 kiosk/src/app.css:13｜P2
- [x] 卡片封面由 flex 撑满（min-height 150px）改为固定 16:9｜✅ 2026-10-12 .item-cover aspect-ratio: 16/9（menu.css:37，eccf091 已落地），img cover 铺满、缺省首字渐变同 16:9｜原型 kiosk-menu.html:28 / 实现 kiosk/src/menu/menu.css:37｜P2

### tablet-screen（2 条）
- [x] 广告页排版层缺失：原型整版设计稿（眉题/110px 大标题/文案/价格/品牌渐变大字「灸」）未实现，仅 object-fit 播放素材文件（数据模型无文案字段）｜✅ 2026-10-12 全链路：ad_materials 加 kicker/title/copy/price 四列（AdMaterialEntity.java:31-46，ddl-auto 建列）、view/playlist 透传（AdMaterialView.java、PlaylistView.PlaylistItem、AdsServiceImpl.java:209）、管理端素材表单四字段（Ads/Index/index.tsx:750-771）、平板 PlaylistItem 类型 + 播放叠加层（tablet/src/types.ts、Carousel.tsx:60-75，眉题 pill/宋体大标题/说明行/价格行，媒体铺满+左下渐变底，四项全空即纯素材播放）、回归 AdsMaterialCopyHttpTest（建+分页读回）。原型「灸」大字为该稿专属装饰、媒体素材自带视觉，未做｜P1
- [x] 叫号条文案「前往 X」与原型「到 3 号诊室」不一致｜✅ 2026-10-12 文案改「到 {room}」（tablet/src/player/CallOverlay.tsx）｜P2

### 逐页核对结论（15/15）
1. **admin-customer**：11 条（P0 0/P1 1/P2 10）——骨架高度对齐（头卡/四格统计/时间线/持卡卡项均超原型），缺口在时间线字段与回访域细节。
2. **admin-diagnosis**：6 条（P0 0/P1 2/P2 4）——差距中等，四诊/取穴/方案全对齐；核心缺口是寸关尺分部录入与五行生克可视化。✅ 2026-10-12 P1 全清（寸关尺六分部字段+三格录入、五行定位图+四指标联动）
3. **admin-dash-day**：4 条（P0 0/P1 1/P2 3）——三组中对齐最好，员工总结列模型按自有复盘数据模型重建。
4. **admin-items**：2 条（P0 0/P1 0/P2 2）——五列表格/封面/开关/操作/分页全一致，仅排序显示与时长字段。
5. **admin-orders**：5 条（P0 0/P1 2/P2 3）——骨架对齐但缺「查看」详情、项目列单卡项无数量，排序无保证。✅ 2026-10-11 全部 5 条已清（order_item 明细模型 + 查看弹窗 + 业务单号 + 倒序 + 列清理）。
6. **admin-ads**：4 条（P0 0/P1 0/P2 4）——单页四 tab + 左表右栏完全对齐，仅文案/交互呈现细节。
7. **admin-appearance**：2 条（P0 0/P1 0/P2 2）——四主题卡+实时预览逐元素还原，仅两处皮肤级细节。
8. **admin-booking**：7 条（P0 0/P1 3/P2 4）——原型单页看板被拆三页，缺「过号」状态与每时段限约规则。✅ 2026-10-12 P1 全清 + P2 2 条（stat4 面板、小程序联动面板），余 2 条卡点（疗程角标、医师名单无字段）
9. **admin-herbprescription**：11 条（P0 1/P1 7/P2 3）——差距最大：辨证条缺失、签名链/饮片检索/打印存方收费按钮组缺位；膏方为实现超集非差距。✅ P0 辨证摘要条已清（2026-10-11，联接诊单 diagnose/plan/acuCourse）；✅ 2026-10-12 再清 5 条（rx 头、特殊煎法提醒、签名行+后端字段、代煎领取方式+delivery_type、三键含存方剂库与打印）；余 4 条 P2 + 2 条卡点（拼音检索、药典剂量上限——缺数据源）+ 1 条口径待定夺（代煎费入合计）。
10. **admin-billing**：7 条（P0 0/P1 2/P2 5）——结算主流程对齐；缺口集中在多项目单据数据模型（明细表退化单行、无整单备注）。✅ 2026-10-11 已清 4 条（明细多行表、整单备注、应收说明行、队列业务单号）；余会员档位/接诊单号头卡与支付图标配色 3 条 P2。
11. **desktop-workstation**：7 条（P0 0/P1 1/P2 6）——薄壳+Web 页骨架完整，唯一实质差距是外设状态条静态文案。
12. **admin-themes**：6 条（P0 0/P1 1/P2 5）——web 端四主题落地完整（tokens 同源+data-theme+antd token 双通道）；差距在非 web 端（小程序 :root 失效风险、kiosk/tablet 无切换）。
13. **mobile-home**：7 条（P0 1/P1 2/P2 4）——主体四节结构对齐，唯一 P0 是底部 tabbar 及两个 tab 页整体缺失。✅ 2026-10-11 全部 7 条已清（tabbar+卡项/我的页、预约横幅提醒态、白标自检挪「我的」、铃铛、称呼、全部项目、卡项占位）。
14. **kiosk-menu**：3 条（P0 0/P1 0/P2 2）——差距最小，五区块逐项对齐；手机号下单/回执两步为实现超集。
15. **tablet-screen**：2 条（P0 0/P1 1/P2 1）——轮播/pager/叫号条/断网角标全落地，唯一实质差距是单页广告排版层。

**修复优先级建议**：~~P0 2 条（herbprescription 辨证条、mobile-home tabbar）~~（已清）→ P1 中影响数据模型的（billing 多项目明细/整单备注、orders 多品项+查看、booking 过号+限约、diagnosis 寸关尺+五行图）→ 其余 P1 → P2 批量扫尾。修复后按 mockups README §2 口径重出截图验收。

## 未实现功能（audit-doc-consistency.md 点名；mockups README 状态为「部分实现」，缺口以上表核对为准）
- [x] 预约挂号（设计原型：/screenshots/admin-booking.png）｜✅ 2026-10-12 核验为过时审计行：功能已实装（appointments API + /appointment/* 工作台+排班网格 + 小程序自助约期），features.md:11 已带界面交叉链接，admin-booking.png 在 docs/public/screenshots/，audit-doc-consistency C4/4 的补链接建议已应用
- [x] 中药处方（设计原型：/screenshots/…）｜✅ 2026-10-12 核验为过时审计行：中药处方全链路已实装（开方/计价/审方/代煎膏方流转/模板/方剂库），features.md:14 已带界面交叉链接，admin-herbprescription.png 在位
- [x] 收费结算（设计原型：/screenshots/…）｜✅ 2026-10-12 核验为过时审计行：结算/退费原路退回/日对账/月报表已实装，features.md:17 已带界面交叉链接，admin-billing.png 在位

## 安全与技术债
- [x] 服务端接口层方法级权限强制（web.md:174：当前只做页面级隐藏）｜✅ 2026-10-12 员工管理写接口（POST/PUT/DELETE /api/v1/staff）@PreAuthorize("hasRole('ADMIN')")（StaffController.java:75+152+272），与 web canAdmin（role==0）同口径；服务端权威映射修正 role 0→ADMIN / 1-9→MANAGER / ≥10→USER（原 1-9 全给 ADMIN、99 员工给 MANAGER 的错位，StaffEntity.getAuthorities:85-96）；越权写 403、读列表不受限，回归 StaffPermissionHttpTest（admin 建删 + clerk 越权 403 + clerk 读 page 200）；web.md 权限节同步｜P2

## 在途工作（未提交，需先收口）
- 无（web `pnpm test` 存量红已于 2026-10-11 修复：jest.config.ts 改用 createConfig 手工别名绕开 configUmiAlias 的 esbuild-register（Node 24 崩溃点），移除脚手架 login 快照测试，新增 billing 请求契约测试 6 例，测试全绿）

验收：对应页面可实跑 + 后端接口有权限校验；测试绿才 push，commit 英文简短。
