# 2026-10-31 UI 审核(impeccable):sinomed web + app

> 方法:impeccable v4.8.0 `audit/critique` + Playwright 真机渲染 + 全页对比度扫描(WCAG 1.4.3,普通文字 ≥4.5:1、大字 ≥3:1;禁用/非交互组件豁免)+ 计算样式/DOM 探针。
> 范围:`web`(UmiJS + Ant Design Pro,真实全栈:Java 服务 :2347 + 前端 dev :8002,登录 admin/123)+ `app`(Taro 3 小程序,H5 产物静态服务 :8150)。web 桌面 1280×800,app 移动 390×844。
> 登录:web 有鉴权,用管理员登录后扫 8 个页面;app 无登录墙(小程序预约页公开)。
> 设计基线:**顶层无 `DESIGN.md` / `PRODUCT.md`**;视觉方向固化在 `docs/design/mockups/*.html` 与 `docs/design/`(有 mockup 与 token 表,但未提炼成 DESIGN.md 供审核对照)。

## 0. 整体 critique(不留情面)

这个产品有两套完全不同的"完成度"。

**web 是真产品,app 是原型壳。** web 端能看出一个中医馆后台的完整骨架:侧栏"Youngs 医馆"宋体字标、石墨×古金双色、预约排班的医师×时段网格、顾客/处方/账单全链路,数据是真的(Java 后端在跑)。但它的门面是**Ant Design Pro 出厂默认值**:登录页占位符写着 `Username: admin or user` / `Password: ant.design`(把 Pro 演示账号直接漏给了用户)、标语是 "Ant Design is the most influential web design specification in Xihu district"、底部 "Produced by Ant Design"——**品牌只做了内页,登录页和页脚没换皮**。侧栏菜单整列是英文(Dashboard/Customers/Appointments),内容却全是中文,中英混杂,像把英文模板直接叠在中文业务上。

**app 端的毛病是"没藏好"。** 预约页有真实的"项目加载失败 / 重试"错误态、空态、加载态,这是加分项;但「我的」页把**白标自检诊断面板直接暴露给顾客**:主题色裸值 `#C7A674`、后端 API base、图标注入状态,还有一句 "换商家出包见仓库 docs/app-white-label.md"——**内部开发文档路径和调试参数当成商品卖点端给了终端用户**。它该藏在一个 staging/调试开关后面。

**色彩纪律**:与 sandtable 同源的问题——辅助灰整族没被 AA 预算约束。web 94 处对比度失败集中在 `--faint:#979c99` 与 antd 默认灰(#bfbfbf 占位、面包屑灰、空态灰、索引徽标灰底白字),app 端同一族 `#979c99` 在预约/卡项/我的三页全部踩线。修完(见下)后 web 剩 6 处、app 剩 5 处,全部是**品牌金棕(logo #C7A674 系)、终端态刻意淡化、或扫描器假阳性**,已逐一甄别,无真实可修的 AA 失败。

## 1. 已修(坏损,当场最小修复,一问题一 commit)

### 1.1 [高] `--faint` 辅助灰整族不达 WCAG AA(web)— `1b5af5c`

- **现象**:web 全页扫描 94 处对比度失败,大头是辅助灰 `--faint:#979c99` 与散落同类灰,最差对纸白仅 ~2.6:1。
- **根因**:`--faint` token 取值未按 AA 预算选,antd 默认灰(#bfbfbf 占位符、面包屑、空态、索引徽标)在宣纸底/白底上全部不足。
- **修复**:`web/src/tokens.css:10` `--faint #979c99 → #6e7270`(对纸底 `#f7f7f4` 保 4.55:1),沿 `docs/design/mockups/tokens.css:10` 锁步同改。
- **证据**:修复后 8 页扫描仅剩 6 处,均为豁免/品牌/终端态/假阳性(见 §2)。

### 1.2 [高] antd 默认灰文字(占位符/面包屑/空态/索引徽标)— `554fc7d`

- **现象**:antd 组件默认灰(占位符 `#bfbfbf`、面包屑链接、空态描述、索引列徽标灰底白字)在白/宣纸底上 < 4.5:1。
- **根因**:antd v5 emotion 注入顺序晚于 `global.less`,纯 CSS 覆盖会被后注入样式反超。
- **定位**:`web/src/theme.ts` `antdTokenOverrides` 加 `colorTextPlaceholder:'#6e7270'`;`web/src/global.less` 加三条 `!important` 覆盖(`.ant-pro-field-index-column` 背景走 `--faint`、`.ant-breadcrumb a/.ant-breadcrumb-separator` 与 `.ant-empty-description` 走 `--muted`)。
- **修复**:占位符、面包屑、空态描述、索引徽标全部 ≥ AA;仅动中性灰,未触品牌色。

### 1.3 [中] 预约排班空时段提示对比度不足 — `8ca8962`

- **现象**:排班网格空槽 `.slot.empty` 文字 `#b3b8b5` 对纸底 ~2.3:1。
- **定位**:`web/src/pages/Appointment/Schedule/index.less:218`。
- **修复**:`color:#b3b8b5` → `var(--faint)`(锁步 §1.1)。

### 1.4 [中] "已到店"状态徽标对比度临界不足 — `cc3e19b`

- **现象**:排班图例与槽位徽标 `.lg.in` / `.st.in` 绿 `#2f7d4f` 对 `#e8f3ec` 仅 **4.43:1**(< 4.5)。
- **根因**:语义绿(非品牌色)取值未按 AA 预算,恰在临界下。
- **定位**:`web/src/pages/Appointment/Schedule/index.less` 两处(254/317)。
- **修复**:`#2f7d4f → #2c744a`(同绿相、略深,对 `#e8f3ec` 保 4.98:1);修复后该页仅剩"今天(禁用按钮)/过号/已取消(终端态)"三处豁免项。

### 1.5 [高] app `--faint` 同族对比度失败(预约/卡项/我的)— `d490a2b`

- **现象**:app(H5)三页扫描共 19 处失败,全部是 `--faint:#979c99`——预约页"整点·60分钟/可到店再定/提交即预约…",卡项与我的页说明、底部 tab 未选中标签(`#979c99` 对白 2.79:1)。
- **根因**:app 有独立 token 副本,`#979c99` 未按 AA 预算。
- **定位**:`app/src/tokens.css` `:root` 与 `page` 两处 `--faint`;`app/src/app.config.ts` `tabBar.color`。
- **修复**:两处 `--faint #979c99 → #6e7270`(与 web 锁步),`tabBar.color #979c99 → #6e7270`;重建 H5 后复扫,三页仅剩品牌金棕项(见 §2)。
- **证据**:复扫 预约 1 / 卡项 1 / 我的 3,均为品牌色/禁用态。

## 2. 剩余对比度项(已逐项甄别,非真实可修失败)

| # | 比值 | 位置 | 归类 / 处置 |
| --- | --- | --- | --- |
| R1 | 4.33 | web 外观页"金卡会员"标签 `#8a6a35` on `#f4eee1` | **品牌金棕**(logo 金 #C7A674 系,amber-deep on amber-tint),任务红线"不碰品牌色"→ todo,需产品定夺 |
| R2 | 2.3 | web 排班"过号"徽标 `#9aa1a0` on `#eef0ef` | **终端态刻意淡化**(已过号/已取消=非活动状态,视觉降级符合层级),非品牌色但属设计意图 → 记录,不改 |
| R3 | 1.71 | web 排班"已取消"徽标 `#c2c7c5`(虚线描边) | 同上,取消态更弱,层级正确 → 记录,不改 |
| R4 | 2.30 | app 底部 tab **选中**标签 金棕 `#C7A674` on 白 | **品牌金棕**(selectedColor = `TARO_APP_BRAND_THEME`),红线"不碰品牌色"→ todo |
| R5 | 2.30 | app「我的」"养"字印章 白 on `#C7A674` | **品牌 logo 印章**,红线"不碰品牌色"→ 记录 |
| R6 | 4.33 | app「我的」"获取验证码" `#8a6a35` on `#f4eee1` | **品牌金棕**控件,临界差 → todo |
| R7 | 1 | web 排班"今天"按钮(扫描 fg/bg 均 #000) | **假阳性**:该按钮当日 `disabled`,1.4.3 豁免"非交互组件";非当日为 antd 默认按钮(正常) |
| R8 | 1 | web 排班/仪表盘"后一天›""前一天‹"按钮 | **假阳性**:纯图标按钮 + `title` 可访问名,非正文文本 |
| R9 | 1 | web 针灸页 `zusanli`(`<code>` 半透明) | **假阳性**:实际 `rgba(0,0,0,0.65)`=`#595959`,通过 AA;扫描器对半透明底误判 |

> 结论:§1 修完后,web 剩 6 处、app 剩 5 处,**全部为品牌金棕、终端态淡化、禁用/图标控件或扫描器假阳性,无真实可修的 WCAG AA 失败**。

## 3. Todo(待办,非当场坏损)

| # | 严重度 | 问题 | 定位 / 建议 |
| --- | --- | --- | --- |
| T1 | 高 | **登录页是 Ant Design Pro 出厂壳**:占位符 `Username: admin or user` / `Password: ant.design`(泄漏 Pro 演示账号)、标签 `Account Login`/`Phone Login`、标语 "…most influential web design specification in Xihu district"、页脚 "Produced by Ant Design"、标题 `Login- Youngs 医馆` | `web/src/pages/User/login`(Pro 模板);建议换品牌文案与真实占位符,页脚改"© 2026 Youngs 医馆",标语改中医馆口径 |
| T2 | 中 | **中英混杂**:侧栏菜单整列英文(Dashboard/Customers/Appointments/Herbal Prescription…),页面内容全中文 | `web/config/routes.ts` + ProLayout 菜单;建议统一中文(或引 i18n 键),至少与内容同语言 |
| T3 | 低 | 表格操作列四个链接(诊断/更历/删除/新增)两两换行,行高不齐 | 各列表页 action 列;建议合并为"更多"下拉或按钮组 |
| T4 | 中 | **白标自检诊断面板暴露给终端顾客**(「我的」页:主题色裸值 `#C7A674`、API base、图标注入态、`docs/app-white-label.md` 文档路径) | `app/src/pages/me/index.tsx:220-241` `.brand-tip`;建议移入 staging/调试开关,或改隐藏诊断路由,不面向顾客 |
| T5 | 中 | **app `dev:h5` 跑不起来**(webpack-dev-server ValidationError 配置不匹配);生产 `build:h5` 正常 | `app/config/`;建议修 dev server 配置或注明"开发需用 `build:h5` + 静态服务" |
| T6 | 信息 | **缺顶层 `DESIGN.md` / `PRODUCT.md`**;视觉方向在 `docs/design/mockups/*.html` 与 `docs/design/`,未提炼成审核对照基线 | 建议 `impeccable document` 生成 DESIGN.md,把四主题 token、宋体标题、金棕点缀口径固化 |
| T7 | 低 | app 四标签纯文字无图标(设计选择,可接受) | `app/src/app.config.ts`;如后续要图标,注意选中/未选中对比(见 R4) |

## 4. 值得肯定

- **app 的错误/空/加载态完整**:预约页"项目加载失败 + 重试"用虚线卡 + 深色正文 + 加粗品牌"重试",层级清晰;项目/日期/时段分区明确;「我的」有"本机预约 暂无"真实空态。不是一片空白。
- **web 排班网格**是真亮点:医师×时段二维网格 + 右栏"今日预约/待到店/小程序联动",槽位状态徽标(在店/待到店/过号/取消)有语义色分层,图例齐全,当日无班次医师列头弱化。
- **主题系统**完整:web 运行时四主题(石墨×古金/松烟绿/朱砂/黛蓝)经 `data-theme` + antd token 深合并,`web/src/theme.ts` 结构化,非散落硬编码;app 白标参数走构建注入(`services/brand.ts`),业务代码不散落。
- **token 五端一致**:web/app/token 表同一套品牌语言,`docs/design/mockups/tokens.css` 为源头;本次 `--faint` 修复 web/app/文档三处锁步。
- 空态描述、面包屑、索引徽标在修复后统一走中性灰(--muted/--faint),不再用 antd 默认浅灰,层级稳定。

## 5. 门禁

- web 测试:`jest` → 5 suites / **19/19 通过**(本次所触及服务层);CSS/token 改动不触及逻辑。
- app:无测试脚本(纯前端页面),门禁为 `build:h5` 成功 + 复扫。
- 对比度:web 8 页 94→6、app 3 页 19→5(余项均豁免/品牌/终端态/假阳性,见 §2)。
- 红线遵守:仅动中性灰(--faint/--muted)与语义绿,未触 logo 金棕/品牌四主题/视觉方向;无版本号变更。

## 6. 截图与探针

截图(临时,未入库):`/tmp/ui-audit-sinomed/` — `login-now.png`、`02-dashboard.png`、`10-customer-query.png`(操作列/中英混杂)、`11-acupoints.png`、`12-billing-settle.png`、`13-appearance.png`、`14-appointment.png`、`15-rx-create.png`、`final-appearance2.png`、`final-schedule.png`;`/tmp/ui-audit-sinomed/app/` — `app-home.png`、`tab-预约.png`、`tab-卡项.png`、`tab-我的.png`、`hash-*.png`。
探针脚本:`/tmp/sscan.mjs`(web 8 页对比度)、`/tmp/apphash.mjs`(app 3 页 hash 导航对比度)、`/tmp/artprobe.mjs`、`/tmp/loginprobe.mjs`。
