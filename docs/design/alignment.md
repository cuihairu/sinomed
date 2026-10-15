# 界面原型 → 现状差距清单与对齐计划

对照基准：[docs/design/mockups](./mockups/README.md)（tokens.css 为五端唯一样式源：默认「石墨×古金」主题取自 logo #666666/#C7A674，`data-theme="pine|cinnabar|indigo"` 三套可切换主色；宣纸底 `--paper`、金棕点缀 `--amber`、宋体标题 `--serif`）。

口径约定：

- **P0** = 高频主路径屏（顾客档案、接诊开单、全局壳主题），先行像素级对齐；**P1** = 其余已实现屏逐屏对齐；**仅原型** = 规划功能，不排实现。
- 差距按 **布局 / 配色 / 组件 / 文案** 四类归纳。
- **数据支撑** 列标明原型里的信息块有无真实接口：`✅` 直接可用；`⚠️ 需前端聚合`；`❌ 无接口`。`❌` 的块实现时一律渲染「规划功能」占位或暂不渲染，**不造假数据**（同 app.md 既有 warning 口径）。
- 每屏完成后独立提交（`feat(web): 顾客档案对齐原型` 式标题），本地起 dev server 截图与 `docs/public/screenshots/` 原型图对照后再提交。

## tokens 同源方案

| 端 | 现状 | 做法 |
|---|---|---|
| web 管理后台 | antd 默认蓝 `#1890ff`（config/defaultSettings.ts:12），系统无衬线 | 拷贝 tokens.css → `web/src/tokens.css` 并在 global.less 引入；defaultSettings `colorPrimary` 与 ProLayout token 对齐 `--brand`；标题/数字类切 `--serif` |
| desktop 壳 | 引导页 `desktop/ui/index.html` 自带样式 | 同步 tokens 变量与宋体标题 |
| app 小程序 | 单页 index.css，已有散变量 `--brand-color #c7a674` | 引 tokens.css，删除散变量改引用 |
| kiosk 自助机 | `#2f6d4f` 墨绿 / `#f6f5f1` 底 | 数值恰等于 pine 主题 → 整页挂 `data-theme="pine"` 引 tokens.css，替换散落 hex |
| tablet 展示屏 | 深色 `#101613` + `#ffce54` | 引 tokens.css 沿用石墨基调 + `--amber` 点缀，替换散落 hex |

## 一、管理后台 web（逐屏）

| # | 屏（原型） | 现状 | 主要差距 | 数据支撑 | 级别 |
|---|---|---|---|---|---|
| 1 | 全局壳 + 主题（appearance 原型口径） | ProLayout `mix` 布局、antd 蓝、白底 | **配色**：主色/选中态/链接全换 `--brand` 系；**布局**：`mix`→纯侧栏（`layout: 'side'`），侧栏白底圆角选中块；**组件**：logo 位换「养」字方标+宋体馆名；**文案**：`Youngs.fun`→馆名副标题 | ✅ | **P0** |
| 2 | 顾客档案（admin-customer） | `Customer/Update` 裸 ProForm 7 字段纵排 | **布局**：整页重排 = 头卡(头像/姓名/等级chip/元信息+操作) + 4 统计卡 + 左「诊疗记录」时间线 + 右「持卡卡项」栏；**组件**：宋体姓名/衬线数字、等级金棕 chip、时间线日期块、余次进度条、回访提醒注记块；**文案**：「年纪」→「年龄」等 | 姓名/性别/年龄/手机/等级/生日 ✅；诊疗记录 ✅ `queryTreatByPage({customerId})`；累计消费 ✅ `GET /api/v1/order/summary`（fetchOrderSummary，已完成订单价格合计）；持卡卡项/余次 ✅ 已实装（`customer_cards` + `/api/v1/card`，顾客页渲染余次进度条）；下次回访 ❌ 无按顾客查询→「规划」占位 | **P0** |
| 3 | 接诊开单 = 桌面工作站（desktop-workstation） | `Treat/Create` 单列长 ProForm（四诊全宽 textarea、五行 ProCard 嵌套） | **布局**：患者头卡 + 四诊 2×2 + 脉象左右手寸关尺格 + 取穴左右 + 诊断(证型+疗程) + 五行图示 + 调理方案条目 + 右栏（历史调阅/打印处方笺入口）；**配色/组件**：panel 卡片化、标签 chip、宋体节标题；**文案**：标签对齐原型（主诉/问诊/望诊/切诊/脉象/取穴/诊断/调理方案） | 全部 Treat 字段 ✅；顾客头卡 ✅ `fetchCustomerById`；历史调阅 ✅ `queryTreatByPage`；打印处方笺 ⚠️ 有 print 模板链路（D7）可挂；开单区/卡项抵扣 ❌ 无订单创建接口于接诊流→「规划」占位；扫码进单 ✅ 既有 ScannerInput/scan 流程 | **P0** |
| 4 | 每日报表（admin-dash-day） | `Dashboard/Day` 三张 ProCard 表格 | **布局**：顶部 4 统计卡 + 左员工总结表 + 右近 7 日柱状 + 今日回访/今日总结；**配色/组件**：状态 pill（已提交/未填写）、提醒按钮、宋体数字；**文案**：标题带日期 + ‹前一天› 导航 | 员工总结/今日回访/今日总结 ✅ 三接口已在用；在岗/接诊/新客统计 ❌ 无聚合接口→暂缓或「规划」；近 7 日 ⚠️ 按 treat 按日查询聚合（接口参数支持 startTime/endTime） | P1 |
| 5 | 卡项管理（admin-items） | `Item/Query` ProTable（名称/价格/描述/时间） | **布局**：提示条 + 封面列 + 上架开关列 + 排序列 + 分页；**组件**：首字封面 chip；**文案**：tip 同原型 | 名称/价格/描述 ✅；封面/上架/排序 ✅ 实体已有（ItemEntity cover/enabled/sort，ItemView 落库默认 enabled=1/sort=0；仅 web typings.d.ts 未同步）→ P1 可真实做开关/排序/封面，无需占位 | P1 |
| 6 | 订单管理（admin-orders） | `Order/Query` ProTable，状态 Tag 蓝/橙/绿/灰 | **布局**：tab 状态过滤（现有 valueType select→改 tab 或保留）；**配色**：Tag 换主题色 pill；**文案**：「已下单」→「待接待」对齐原型与自助机口径 | 单号/顾客/项目/金额/状态/接单-完成-取消 ✅ 全真实 | P1 |
| 7 | 广告屏（admin-ads） | `Ads/Materials|Schedules|Screens|Calls` 四个 ProTable 页 | **布局**：原型为单页 4 tab + 右栏屏幕状态/快捷叫号；逐页对齐卡片化与 pill；**文案**：脱敏 `王*` 已在展示屏口径 | 素材/排期/屏幕/叫号四组接口 ✅ | P1 |
| 8 | 复盘回访 / 员工 / 登录 | 无专属原型 | 仅随全局壳主题（#1）被动对齐，不单独排屏 | ✅ | P1（被动） |
| 9 | 外观设置（admin-appearance） | 无此页 | 新建「系统/外观设置」页：4 主题卡选择 → 写 localStorage → `data-theme` + antd token 运行时切换 + 实时预览区 | 纯前端 ✅ | P1 |
| 10 | 预约排班 / 中药处方 / 收费结算 | 无→预约/结算已建域 | 预约排班已实装（5910aee/f04cf62：`appointments` 表 + `/api/v1/appointment` + `/appointment/*` 列表工作台 + `/appointment/schedule` 医师×时段排班网格）；收费结算已实装（26d6419/9d1e018：`settlements` 表 + 储值流水扣减 + `/api/v1/settlement`、`/api/v1/recharge` + `/billing/settle` 结算台；次卡抵扣 81aaf5e、小票打印 f57f10d 已实装，处方饮片行仍为设计稿）；中药处方已实装（f91add1/0ee1d1d：`prescriptions`/`prescription_items` 表 + `/api/v1/prescription` + `/prescription/*` 开方与查询，药材自由文本；配伍审方 7100355、计价 60e818c、代煎领取 141f295 已实装） | 预约 ✅ / 处方 ✅ / 收费 ✅ | 三者均已实现；处方库存（饮片出入库）为规划 |

## 二、桌面工作站（desktop 壳）

| # | 屏 | 现状 | 主要差距 | 级别 |
|---|---|---|---|---|
| 1 | 接诊开单主屏 | 加载 web `/treat/create` | 即 web 屏 3，随 P0 落地；`ScannerInput` 扫码定位条随页面对齐 | **P0**（同 web） |
| 2 | 壳引导页 `ui/index.html` | 自带浅色样式、外设自检按钮 | 配色/宋体标题对齐 tokens；外设状态（小票机/扫码枪）展示样式统一 | P1 |

## 三、小程序 app

四标签结构（首页 / 预约 / 卡项 / 我的），页面注册与 tabBar 见 `app/src/app.config.ts`；白标自检已从首页挪到「我的」页底。下表按当前实现记「现状」，未实装部分照旧列差距、不造假数据。

| # | 屏 | 现状 | 主要差距 | 级别 |
|---|---|---|---|---|
| 1 | 首页（mobile-home） | 已按原型实装：品牌头 / 预约横幅（入口 → `pages/booking`）/ 今日宜养宫格（实拉 `GET /api/v1/kiosk/items`）/ 我的卡项（入口联动 `pages/card`，查过卡的本机回显称呼+持卡数+查询日期、7 天有效）/ 养生贴士；配色已收编 tokens（`src/tokens.css` 与 mockups 同源） | 布局/配色无差距；**购卡与余次明细仍为规划**——余次只在验证后的卡项页展示，明文不落本机 | P1（已对齐） |
| 2 | 自助约期（mobile-booking） | 已实装：日期条（今天起 7 天）→ 时段网格（9:00–17:00 整点，今日已过时段禁选）→ 项目选择（实拉上架项目，可「到店再定」）→ 手机号/称呼提交 → 回执展示预约号与时段；走免登录写入口 `POST /api/v1/kiosk/appointments`，口径见 [app-booking](./app-booking.md) | 无布局差距；**时段占用查询不做**——服务端不做占用查询，冲突到店人工调和（同页注记） | P1（已实装） |
| 3 | 手机查卡（mobile-card） | 已实装：手机号 + 短信验证码双因子（`POST /api/v1/kiosk/sms-code` → `card-lookup`）；weapp 另接 `wx.login` → `openid-session` 换 openid 走 `openid_only` 路径（openid 仅存内存、换取失败静默回退短信路径，H5 只走短信）；按 `sinomed.phone-lookup.mode` 三态开关，**默认 `off` 关闭**；每次尝试含拒绝都落 `card_lookup_logs` 审计 | openid 自助绑定已实装（`POST /api/v1/kiosk/openid-bind`，openid_only 下填手机号+验证码自证后绑档案，同档案幂等、已绑其他档案拒绝；激活仍缺微信凭据，见仓库根 `todo.md` 卡点档）；登录态不做 | P1（已实装） |
| 4 | 我的（mobile-me） | 已实装：本机预约摘要 + 跨设备查预约（手机号+验证码一次性双因子 `POST /api/v1/kiosk/my-appointments`，回未过期待到店预约、无预约如实回空）+ 主题切换（仅 H5 端渲染）+ 白标自检页底 | **登录态（会话保持）不做**——免登录手机号直查会造成横向数据暴露，超演示 MVP 口径 | P1（已实装） |

## 四、自助机 kiosk

| # | 屏 | 现状 | 主要差距 | 级别 |
|---|---|---|---|---|
| 1 | 选服务（kiosk-menu） | menu 页已有服务列表+购物篮结构 | 配色已是墨绿（=pine）→ 换 tokens 变量收编散 hex；宋体标题、宣纸底、金棕价签/点缀；购物篮/结算按钮细节对齐原型 | P1 |
| 2 | 下单回执（order） | 已有 | 同上随全局对齐 | P1 |

## 五、展示屏 tablet

| # | 屏 | 现状 | 主要差距 | 级别 |
|---|---|---|---|---|
| 1 | 轮播+叫号（tablet-screen） | player 轮播 + call 叫号条已实现 | 深色底换 tokens 石墨基调、`h1 em` 金棕、宋体标题、叫号条 `王*` 脱敏（已实现，核对）；页码/进度细节 | P1 |

## 执行顺序

1. **P0** web 全局壳主题（tokens.css 落 web + colorPrimary/ProLayout 对齐）→ 2. **P0** 顾客档案 → 3. **P0** 接诊开单（含桌面工作站主屏）→ 4. P1 订单 → 5. P1 卡项 → 6. P1 每日报表 → 7. P1 广告屏 → 8. P1 外观设置（主题切换落地）→ 9. P1 kiosk → 10. P1 tablet → 11. P1 app → 12. P1 desktop 引导页。

每屏：改实现 → `pnpm build`/lint 过 → 本地截图对照原型 PNG → 独立 commit（标题 `feat(web): 顾客档案对齐原型` 式，正文列布局/配色/组件/文案四类改动与实证）→ fetch+rebase → push。

## 进度

- [x] P0-1 web 全局壳主题对齐（bd72bae）
- [x] P0-2 顾客档案对齐（fc84cfa）
- [x] P0-3 接诊开单（桌面工作站主屏）对齐（42e567b，desktop 壳随页面同享）
- [x] P1-4 订单管理对齐（f42e0ee）
- [x] P1-5 卡项管理对齐（8d4b77f）
- [x] P1-6 每日报表对齐（1c72d5b，随批 fix(server)：分页日期过滤改 Date 比较 b8dc2a8）
- [x] P1-7 广告屏四页对齐（bcb6b1c，四页合并单页 4 tab + 右栏，旧路由 redirect）
- [x] P1-8 外观设置（8591447；运行时主题切换：data-theme CSS 变量 + useAntdConfigSetter 注入插件 ConfigProvider + settings.colorPrimary/侧栏 token 三层下发）
- [x] P1-9 kiosk 自助机对齐（eccf091；tokens.css 同源拷入 + index.html 挂 data-theme="pine"；hero/三步胶囊/渐变首字卡/两行购物篮对齐 kiosk-menu 原型，散 hex 全收编变量）
- [x] P1-10 tablet 展示屏对齐（2caa81f；tokens.css 同源拷入；石墨底 --ink、纸色圆角 slide 卡 + 真实素材内嵌、pager 圆点真实进度、叫号全屏覆盖改原型底部横条=金棕 label+号码脱敏+宋体诊室，15s 自动收回）
- [x] P1-11 app 小程序首页对齐（9566fdb；mobile-home 版式：品牌头/预约横幅/今日宜养/我的卡项/养生贴士/白标自检页底；宫格实拉上架项目，预约与卡项余次如实「规划功能」pill+note；tokens.css 同源引入，--brand-color 白标注入保留）
- [x] P1-12 desktop 壳引导页对齐（5e656b7；五端散色残留 grep 复核零命中，--brand-color 白标注入机制保留）
- [x] P1-13 处方配伍审方 + 药材字典/计价（8e3402f/0144ba0；十八反/十九畏静态规则提示不拦截；herbs 字典 + 处方实时试算，未收录药名不计费）
- [x] P1-14 卡实体（次卡）与结算抵扣（81aaf5e；customer_cards + /api/v1/card 发卡/停用，payType 5 扣 1 次实收 0；顾客页持卡栏与结算台次卡选项实装）
- [x] P1-15 结算小票打印（f57f10d/1b33703；`GET /api/v1/print/receipt/{settlementId}` 服务端套打 80mm HTML：单号 S+结算id、次卡抵扣金额列显示「次卡抵扣」、名字 HTML 转义；结算台收款成功出打印入口、最近结算可补打；模板仍可 data/printtemplates 覆盖）
- [x] P1-16 处方代煎领取（141f295/47e4828；prescriptions +decoction_status/decoction_bags，开方 decoction=true 袋数=剂数落「待煎」；`PUT /prescription/{id}/decoction?status=` 待煎→可取→已取单向流转，回退/跳跃/未选代煎 400；服务费 ¥3/袋 实时算仅提示；开方页勾选、查询页状态列+抽屉流转按钮；加急与取药窗口不做——演示口径）
- [x] P1-17 处方饮片行进结算 —— **裁定不做**（依据仓内既有裁定「收费以卡项订单结算为准」：处方计价为试算/提示口径、不是收费来源，见 api.md 结算段、开方页 note、PrescriptionController javadoc，60e818c 起、141f295 重申）。原型 billing 的饮片行（「8 味 × 7 剂 ¥280」与推拿并列）为示意、非接口契约，与裁定冲突时以裁定为准。技术佐证：orders.price 整数元 vs 处方分单位计价，转元必舍入、违背「钱的事要确定」；多行结算（settlement_items）属域级改动。代煎费 ¥3/袋 同口径（仅提示）。
- [x] P1-18 员工请假补全（c33f1cf/7e8a7a0；web.md 遗留项「/staff/leave 提交调用创建员工接口，功能未完成」：原 server `POST /staff/leave` 为返回空对象的占位、web 页误用 createStaff 会把请假提交成建员工——已重建：staff_leaves 表 + `/api/v1/staff/leave/{,page,id}` 提交/分页/删除（类型 0 病假 1 事假、事由、起止时间校验，联出员工名），web 登记表单 + 近期请假列表；口径：记录性质不改登录状态、无审批流（演示口径））
- [x] P1-19 病症处方模板（6b5d992/a6b0ab7；对齐 chinese_medicine_store_cos 的病症处方库思路：prescription_templates + items 两表，`/api/v1/prescription/template/{,page,enabled,id}` CRUD + 上架过滤（停用不出现在开方页），病症名唯一 1–20 字、模板只存建议值（剂数/代煎/用法/备注），更新整单替换药味行（sort=行序）；web `/prescription/templates` 管理页（药味行内增删、上架 Switch、删除 Popconfirm）+ 开方页「套用模板」下拉一键带出，带出后随处方自由增减不写回模板；种子 风寒感冒/脾胃虚弱 两例，HTTP 测试覆盖 CRUD/重名/校验/未登录 401）
- [x] P1-20 推拿/艾灸疗程卡核销流水（9e5a379/2205c6c；复用 P1-14 次卡链路的口径——推拿/艾灸疗程卡即按疗程计次的卡项 + `customer_cards`，本次补齐疗程管理缺的核销维度：`card_usages` append-only 流水，结算 `payType=5` 扣次时同一事务落「第几次/服务员工(取订单 staffId)/订单号」，随结算回滚、不做手工划扣；`GET /api/v1/card/{id}/usages` 新记录在前联员工名；web 顾客档案持卡栏「已用 X/N 次」+ 核销记录弹层；种子演示卡补 3 条历史核销（回写时间线、老卡补录无订单号口径）；HTTP 测试覆盖 发卡→建单→次卡结算→查流水 与无卡 400/未知持卡 400/未登录 401）
- [x] P1-21 膏方处方与领取流转（2134975/280c435；prescriptions 增 `prescription_type`（0 汤剂默认/1 膏方）、`paste_status`（开膏方落 1 待制作）、`craft`（收膏方式，仅膏方），列定义带默认 0 使老插入点免改；`POST /prescription/` 开膏方按料计不落袋数（与代煎互斥，各走各的领取链）；`PUT /api/v1/prescription/{id}/paste?status=` 待制作→可取→已取 单向推进，非膏方「不是膏方」400、回退/跳跃「流转无效」400，与代煎流转同形不混用；web 开方页类型 Radio（汤剂/膏方，切膏方清代煎、切回清收膏方式）+ 收膏方式 Select（炼蜜/清膏/糖膏/阿胶收膏），查询页列表 类型/膏方 状态列 + 详情抽屉「制成 · 转可取 / 顾客已领取」按钮；种子补 膏方示例（九味药、30 剂按料计、炼蜜收膏、落「可取」留「已取」给演示）；单测 5 例 + HTTP 测试覆盖 开膏方→流转→汤剂 400/跳跃 400/未登录 401）
- [x] P1-22 顾客病史（过敏史/既往史）（7bb2679/bfcf3e8；features.md 电子病历行的缺口「无独立病史结构」：`customer_histories` 逐条 append 表（type 0 过敏/1 既往，content ≤200 字），`GET/POST /api/v1/customer/{id}/history` + `DELETE /api/v1/customer/history/{id}`，守卫 顾客不存在/类型无效/内容空白/内容过长 均 400（内容 trim 后入库，校验不过不落库）；不做编辑与留痕——演示口径，删除即删；web 顾客档案病史栏（过敏/既往 Radio.Button + 输入 + 记录，行内类型 pill/内容/日期/删除确认）；种子给两位演示顾客各补过敏/既往（回写 28/28/9 天前时间线）；HTTP 测试覆盖 记两条→新记录在前→删一条 与四道守卫/未登录 401；走查顺带修：膏方种子漏置 `decoction_status`（列 NOT NULL 无默认值）导致演示环境膏方种子静默失败）
- [x] P1-23 处方过敏审方（6c354cc/7237b2f；features.md 配伍禁忌审方行的过敏维度：`POST /api/v1/prescription/allergy-check` 按 `customer_histories` type=0 过敏史比对，记录原文包含药名即命中——≥2 字参与、单字如「参」防长文本误报；一味命中多条史各报一条，同史提多味各报一条；既往史不参与；未知顾客/空白名单 400；提示不拦截，与配伍审方同口径；web 开方页定位顾客后防抖 500ms 随写随查，命中块列出 药味+史原文、无命中不占位；种子 王慕清「阿胶、蜂蜜过敏」× 膏方含阿胶 演示自然命中；单测 4 例 + HTTP 测试覆盖 建档→记史→命中 与守卫/未登录 401）
- [x] P1-24 月度收费报表（a2f695a/e5e4eea；features.md 报表统计行的缺口「无经营/收费类报表」：`GET /api/v1/settlement/report/month?month=yyyy-MM` 按结算时间取当月结算单、Java 内聚合（SQLite 方言不做日期分组）——`days[]` 逐日单数/实收只列有结算的日、`payTypes[]` 支付方式构成按 payType 升序并附文案、次卡核销（payType 5，实收 0）单列 `cardCount`，储值充值不计入营收；月份格式非法/月份不存在 400、未登录 401；web `/dash/month`（月份选择 + 当月实收/结算单数/次卡核销三卡 + 支付方式构成 + 逐日明细 + 口径 note）；种子补当月 6 笔 微信/支付宝/现金/储值结算（按日回写时间线，储值同口径落负数流水）+ 1 笔次卡核销（第 4 次带订单号、扣余次，与 P1-20 核销流水口径一致）；单测 2 例 + HTTP 测试 2 例；实机走查 2026-10 报 7 单 ¥3516、次卡核销 1 次、推拿卡余 6/10 与 4 条核销流水一致，同库重启复核实无重复种子）
- [x] P1-25 方剂库（07832e5/cfb32fa；features.md 中药饮片/方剂行的缺口「方剂库、拼音检索未做」，his_mvp 方剂库+拼音输入的对齐：`formulas` + `formula_items` 两表（方名唯一 1–20 字、`pinyin` 全拼小写检索码、出处/功效主治；药味 同处方 herb/weight/special/sort），`/api/v1/formula/{,page,id}` CRUD + `keyword` 方名**或拼音码**模糊（`like concat('%',:kw,'%')`，service 传空串全匹配）；守卫 方名空/超 20 字/重名（更新豁免自身）/拼音空/非小写字母（录入自动转小写）/至少 1 味/药名空/剂量>0/方剂不存在 均 400；更新整单替换药味行；与病症模板分工——方剂只存文献参考值不带剂数/代煎建议；web `/prescription/formulas` 管理页（方名+拼音双行、药味 chip、方名/拼音检索）+ 开方页「方剂库」下拉检索一键带出全方（药味带出、剂数不变，带出后随处方自由增减不写回）；种子 9 首经典方（四君子汤/四物汤/逍遥散/补中益气汤/玉屏风散/六味地黄丸/平胃散/二陈汤/八珍汤，出处与功效随方）+ 药材字典补 10 味（去早退守卫，老库按 existsByName 增量补齐）；单测 5 例 + HTTP 测试覆盖 建方→检索→改→删 与守卫/未登录 401）
- [x] P1-26 穴位字典（9ecc30d/b8247ca；features.md 针灸/取穴记录行的缺口「无穴位字典」：`acupoints` 单表（穴名唯一 1–10 字、`pinyin` 全拼小写检索码、`meridian` 归经必填、定位/主治可空），`/api/v1/acupoint/{,page,id}` CRUD + `keyword` 穴名**或拼音码**模糊；守卫 穴名空/超 10 字/重名（更新豁免自身）/拼音空/非小写字母/归经空/超 20 字/穴位不存在 均 400；接诊单取穴字段保持自由文本——字典只做选穴辅助；web `/treat/acupoints` 管理页（穴名+拼音双行、归经 pill、穴名/拼音检索）+ 接诊页左右取穴行各加「字典选穴」下拉（按穴名/拼音检索、选中以「、」追加进取穴文本，仍可自由写）；种子 27 常用穴（足三里/三阴交/合谷/太冲/内关/外关/曲池/阳陵泉/委中/百会/风池/大椎/关元/气海/中脘/神阙/涌泉/太溪/血海/阴陵泉/肩井/命门/肾俞/脾俞/胃俞/印堂/太阳，归经/定位/主治随穴）；单测 5 例 + HTTP 测试覆盖 收录→检索→改→删 与守卫/未登录 401）
- [x] P1-27 周期性员工班表（b684f78/0be5ae0；features.md 排班行的缺口「周期性员工班表未做」：`staff_shifts` 单表（staff_id + `weekday` 1 周一…7 周日 + `start`/`end` HH:mm，同员工同星期唯一、按周循环），`/api/v1/shift/{,page,staff/{staffId},id}` CRUD + 分页 `staffId`/`weekday` 可空过滤（service 按空值分派派生查询避开 SQLite JPQL 空参坑，员工+星期+时段升序）；守卫 员工空/不存在/星期越界/时段非 HH:mm（小时≤23 分钟≤59）/start ≥ end/同员工同星期重复（更新豁免自身）/班次不存在 均 400，回包含联出员工姓名与星期文案；**班表口径：只做排班参考，考勤打卡仍以 signs 为准，不回写打卡记录、与请假/预约暂无联动**；web `/staff/shifts` 周矩阵页（行=员工、列=周一~周日，格内时段可点击调整、`✕` Popconfirm 删除，「加排班」弹层 员工/星期/TimePicker 30 分钟步进 + 下班晚于上班前置校验，仅 `canAdmin`）；种子 沈知远 周一~五 09:00-18:00、苏文若 周一~六 09:30-18:30、顾景明 周三~日 10:00-19:00 共 16 条；单测 5 例 + HTTP 测试覆盖 加排班→分页过滤→整周列表→改→删 与守卫/未登录 401）
- [x] P1-28 针灸处方记录（2099b39/9d282af；features.md 针灸/取穴记录行的缺口「针灸处方（针法/留针/手法记录）未做」：接诊单 `treats` 加 4 列（`acu_method` 针法 ≤30 字、`retention_minutes` 留针分钟 1-240 可空、`manipulation` 手法 ≤30 字、`acu_course` 疗程/频次 ≤100 字，空白文本按空处理），随接诊单创建/更新全量保存（TreatView 双向映射，service save/update 统一校验，越界 400）；web 接诊页「针灸处方」区（针法/留针 InputNumber 1-240/手法/疗程 TextArea，复诊回填）；种子 不寐单补 毫针+耳穴压豆/25 分钟/平补平泻/每周 2 次×2 周、腰痛单补 温针/20 分钟/补法/一周两次×3 次（针法为空才写，幂等）；TreatUpdateTest 扩 2 例（字段往返/守卫）+ 新增 TreatAcuHttpTest 2 例（创建→回读→更新→清空、守卫、未登录 401））
- [x] P1-29 周班次对照（e4af672/afd2a7d；features.md 排班行的缺口「班表与考勤联动未做」：`GET /api/v1/staff/timesheet/week?weekDate=` 把入参锚定到所在周周一（`(dow-2+7)%7` 天回退），拉整周 signs 走与月度考勤同一套 `TimesheetUtil` 口径（同日最早上班/最晚下班、整小时取整，历史日缺下班卡计 0），员工按 id 升序全量成行 × 7 天，班表计划按星期对照（无班次/无打卡留空）；`weekDate` 空取当周，非法格式报 400；顺带把 `DateUtil.parseParam` 改严格模式（`2026-13-40` 这类越界日期不再被滚动成合法日期）；web `/staff/timesheet/week` 员工 × 7 天对照板（应/实两行，选日期看所在周 + 本周回当前，仅 `canAdmin`）；StaffWeekTimesheetTest 3 例 + StaffWeekTimesheetHttpTest 2 例（结构读取/400/401））
- [x] P1-30 饮片出入库与效期预警（features.md 中药饮片库存行的缺口「库存未做」：`herb_stock_logs` 单表流水（herb_id + `type` 1 入/0 出 + `quantity` 克 + 批次 `expiry` + `supplier` ≤50 字 + `note` ≤100 字），**不落余额列、库存按流水重算 = 入合计-出合计**，出库按效期先进先出（FEFO）消耗批次，`GET /api/v1/herb-stock/balance?expiryWithinDays=` 给各药材当前克数 + 最早未消耗批次效期 + 距效期天数 + `warnExpiry`（默认 30 天、上限 365，已过期同样预警）；`/api/v1/herb-stock/{,/page,/balance,id}` 登记/流水分页（herbId/type 可空过滤、id 倒序、联药材名）/余额/删除（演示口径无留痕）；守卫 药材必填/药材不存在/类型非法/数量非正/效期非 yyyy-MM-dd 或早于今天/供货方与备注超长/出库超当前库存 均 400；web `/prescription/herb-stock` 余额看板（阈值 30/60/90 切换）+ 流水登记弹层（出库不填效期）+ 流水分页删除；种子 5 味常用药材入库（黄芪 5000/当归 3000/茯苓 4000/白术 2500/甘草 6000 克，均带批次效期与供货方）+ 1 笔甘草出库 800 克（门诊领用），白术效期 25 天用作 30 天阈值预警示例；HerbStockTest 3 例 + HerbStockHttpTest 2 例。台账口径，不涉采购单据、盘点与结算，开方仍不扣库存）
- [x] P1-31 外治处方模板（features.md 针灸/取穴记录行的缺口「耳穴/贴敷等外治处方模板未做」：`therapy_templates` 单表（模板名唯一 1–20 字、`method` 外治方法非空 ≤10 字、`target` 建议取穴/部位 + `course` 建议疗程频次 + `note` 注意事项 各 ≤100 字可空、`enabled` 上架位），`/api/v1/therapy-template/{,enabled,page,id}` CRUD + 分页 `name` 模糊 / `method` 精确可空过滤（Specification 合取，避开 SQLite 派生查询空参坑）；守卫 名空/超 20 字/重名（更新豁免自身）/方法空/超 10 字/三段可空超长 均 400；口径同病症模板——只存建议值，接诊页套用后随诊疗单自由改不写回模板，停用不出现在接诊页；web `/prescription/therapy-templates` 管理页（方法 chip、上架开关）+ 接诊页针灸处方区「套用外治模板」下拉（方法/疗程回填、取穴建议以「、」追加进取穴-左）；种子 4 组常用外治组合（失眠·耳穴压豆/咳喘·穴位贴敷/肾阳虚·温灸关元/腰痛·留罐）；TherapyTemplateTest 3 例 + TherapyTemplateHttpTest 2 例（建→列表→分页过滤→改→删 与守卫/未登录 401））
- [x] P1-32 药房与发药流转（features.md 药房与发药行的缺口「未实现」，按 [dispensing.md](./dispensing.md) 定稿口径：`prescriptions` 加 `dispense_status`（0 待发药默认/1 已发药单向；列 NOT NULL，服务 save 与种子两处创建点均显式置 0）/`dispense_time`/`dispense_by` 三列 + `dispense_logs` 单向流水（prescription_id 索引、action=1、staff_id、note ≤100 字）；`POST /api/v1/prescription/{id}/dispense` 待发药→已发药（重复 400「处方已发药」、不存在 400、操作人取当前登录账号、备注 ≤100 字）；`GET /api/v1/prescription/dispense/page` 工作台分页（dispenseStatus/customerKeyword 可空过滤、id 倒序、联顾客姓名手机与类型/剂数/药味数/代煎膏方文案）；代煎/膏方推进「已取」自动置已发药并落流水（操作人留空、note 写来源——与定稿「取做操作用户」的偏差已记录在稿）；web `/prescription/dispensary` 药房工作台（三态切换带计数、顾客检索、发药弹窗带可选备注单向确认、已发药行只读）+ 处方查询抽屉发药状态与快捷确认；种子发药演示（首个处方置已发药 + 流水回写时间线）；DispenseTest 5 例 + DispenseHttpTest 1 例（发药→分页过滤→重复 400→未登录 401））
- [x] P1-33 退费与日对账（按 [reconciliation.md](./reconciliation.md) 定稿口径：`refund_logs` 单表（settlement_id **唯一索引**一单只退一次、order_id 索引、money=原结算实收（次卡 0）、pay_type 冗余、staff_id 可空、reason ≤100 字必填）；`POST /api/v1/settlement/{id}/refund` 退整单原路退回——储值补正数流水（`recharges` +`note` 列写「退单 #结算id」）、次卡作废该订单 `card_usages` 并回补余次、微信/支付宝/现金仅落退款记录；订单 `2→1` 可重收；`GET /reconciliation?date=` 按自然日三段聚合（收款/充值/退款 + `netMoney`、`cashNetMoney`），充值段只算真实充值（正数非退单 note——储值支付扣减归收款侧、退款补回归退款侧）；**settlements.order_id 唯一索引随「退款后重收」口径移除**（SettlementIndexMigration 启动 DROP 老库遗留索引，settle 防重改判「最近一张未退款」）；web `/billing/reconciliation` 日对账页（统计卡 + 收款构成 + 充值/退款明细表）+ 结算台最近结算行级「退款」（理由弹窗单向确认、已退款 tag）；种子退一笔历史现金结算单演示（订单回退队列可重收）；RefundReconciliationTest 6 例 + RefundHttpTest 2 例（退款→对账→重复 400→date 缺失/非法 400→未登录 401），实机验证储值退款补余额、重收落新结算单、三收拦截）
- [x] P1-34 到店自动叫号（docs/tablet.md「到号自动叫号」缺口，2026-10-12 拍板：预约接待 0→1 服务端自动落 `queue_calls` 与前台手动叫号两条并行——号码=当日接待序号补零两位（接待时刻自然日、按 id 计序）、诊室=接诊医师名+「诊室」/未指派「待分配诊室」、姓名脱敏（张*）、广播全部屏；过号/取消不叫号；散客无预约仍走手动叫号。落 AppointmentServiceImpl.fireQueueCall + QueueCallEntity 注释刷新，回归 AppointmentSlotTest.receiveFiresQueueCallAndNoShowDoesNot（过号不叫/接待叫一条/号码两位/诊室取医师名/姓名脱敏/广播全屏/未指派诊室））
- [x] P1-35 小程序四标签现状补记（§三 表格按当前实现重写：首页「我的卡项」由「规划」占位改为真实入口 + 本机摘要，预约 / 卡项 / 我的三屏从原型清单补进现状表；剩余规划项如实留白 = 购卡与余次明细、顾客 openid 绑定登记、登录态。对应实现 `9cfaa92`/`be5da27`/`af0af7f`）
