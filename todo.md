# TODO

> 生成于 2026-10-11；2026-10-14 整理。来源：docs/审计-文档一致性.md、docs/web.md 遗留、工作区在途改动。
> 2026-10-14 巡检 R3：按用户当日授权代决三项（维持非目标，用户事后可推翻）；在线支付需真实凭据仍挂起。
> 2026-10-16 巡检 PATROL1016：复核全部 5 条 open 项（2 卡点 + 3 已代决），解锁条件均未到——微信凭据仍占位（`app/project.config.json:5` touristappid、服务端 `WECHAT_APP_ID`/`WECHAT_APP_SECRET` 缺省空串），支付通道服务端无任何商户配置且付费红线未授权；三条已代决无推翻信号，维持非目标不重开。无合法开工项。
> 2026-10-16 巡检 P1016AM：卡点逐条再实证——微信凭据仍占位（`app/project.config.json:5` touristappid、`application.yml:51-53` 空缺省）、服务端支付商户配置仍零匹配，均未解除；三条已代决无推翻信号。无合法开工项。
> 2026-10-16 巡检 PATROL016：复核结论不变（两条外部凭据卡点、三条已代决均无解锁信号，未重开）；docs 根目录 8 份文档已补中英配对与切换器（用户 2026-10-16 执行单），子目录双语化按单登记为下一条（见「文档与站点补完」）。
> 2026-10-14 起排序依据：**手机端优先**（用户令，见下）。待办按 手机端补完 → 卡点 → 已代决 三档排列；新功能先落手机端，影响手机端的缺陷排在同类桌面问题之前，本阶段不新增桌面功能。

- [x] 手机端优先（来源：用户 2026-10-14 令，**优先级调整**）
  - 用户原话：**「sinomed 手机端优先」**
  - 含义：后续开发与缺陷处理**以手机端为第一优先**，桌面/后台次之
  - 要求：
    1. **先核现状**：本仓手机端（小程序/H5/移动端）已有哪些页面与功能、完成度如何，给 file:line 清单
    2. **排期按手机端优先**：新功能先落手机端；缺陷若影响手机端，排在同类桌面问题之前
    3. 手机端已有但不完整/不可用的部分，**优先补完**，不要新增桌面功能
  - 验收：给出手机端现状清单与按优先级排序的待办；已登记为本仓后续工作的排序依据｜证据：本文件「待办」已按手机端补完（各行带 file:line 锚点）/卡点/已代决三档重排；现状清单以 2026-10-14 盘点回报与 app.md 为准（weapp 与 h5 双端构建实测通过）

## 待办

### 手机端补完（第一优先）

- （当前无未完成项）

### 文档与站点补完

- [x] docs 子目录双语化——2026-10-16 用户执行单指定「本轮只做 docs/ 根目录，子目录登记 todo 下一轮再做」。范围：`docs/guide/`（getting-started、development）、`docs/server/`（architecture、api、data-model、deploy）、`docs/design/`、`docs/research/`、`docs/marketing/` 下全部实质 .md（排除 CHANGELOG/CONTRIBUTING/LICENSE/CODE_OF_CONDUCT/SECURITY 与 node_modules/dist 等）。口径同根目录：中文→配英文版 `.en.md`、英文→配中文 `.zh.md`，两份首屏（前 3 行）各带语言切换器；超长文档（1000+ 行）可只放切换器+「暂无译版」如实标注，不硬造劣质翻译；命令/路径/代码块/专有名词原样不译；门禁 = docs check + vitepress build（docs-only 不跑编译与测试）｜已完成：guide/server/design/research/marketing 全部 23 份中文文档（含 design/mockups/README）配对英文版，切换器双侧第 3 行、中文源仅 +2 行零删改（git numstat 逐份核验）；docs check 与 vitepress build（27.08s）双绿；证据：本轮 commit；根目录 8 份为 commit d243801

### 卡点

- [ ] 微信正式凭据（appid + AppSecret）——挂起：`app/project.config.json:5` 仍为 `touristappid` 占位，服务端 `WECHAT_APP_ID`/`WECHAT_APP_SECRET` 未配置。小程序真机运行与 openid 通道（openid-session 登录换取、openid-bind 自助绑定）的激活都压在这条上；实现已可测试、凭据到位即激活（「先建后激活」先例），无需改码
- [ ] 在线支付真实通道——挂起：需支付通道商户号与密钥等真实凭据 + 你授权接网关；红线不碰付费通道

### 已代决（维持非目标，用户事后可推翻）

- [ ] 西药与成药处方、药品目录——已代决（2026-10-14 巡检授权）：维持非目标。理由：单店中医馆定位，不做西药处方流转（docs/design/dispensing.md 裁定不变）；若要启用需用户推翻裁定并提供原型设计
- [ ] 多门店与连锁——已代决（2026-10-14 巡检授权）：维持非目标。理由：单店模型数据与权限均无门店维度，凭空分层属伪造设计（2026-10-12 裁定）；若后续连锁化再定门店字段与聚合口径
- [ ] 采购单据与盘点——已代决（2026-10-14 巡检授权）：维持非目标。理由：出入库台账口径已够用（供货方随入库流水留痕，FEFO 批次消耗与效期预警已实装），不做采购单据/盘点/报损/多库房（docs/design/dispensing.md 裁定不变）

## 已完成

- [x] 顾客 openid 自助绑定——已代决（2026-10-15 R1015F，用户可推翻）：绑定路径拍板为**小程序自助**（openid + 一次性短信验证码自证手机号后绑定，免桌面免前台；不选管理端登记——与本阶段「不新增桌面功能」冲突且需原 UX 取舍；不选双轨——凭据未到位先建两套面属超前）。落地：服务端新增 `POST /api/v1/kiosk/openid-bind`（openid_only 模式专用，复用查卡一次性验证码通道与审计口径；同档案重复绑定幂等、openid 已绑其他档案 400 拒绝不静默改绑；每次尝试含拒绝落 `card_lookup_logs`，结果新增 `bind_ok / bind_conflict`），`sms-code` 放宽到 openid_only 发码（仅服务绑定，查卡仍只走 openid），openid 查卡未绑定文案由「请到前台登记绑定」改为自助绑定引导；小程序「卡项」页加「绑定微信」按钮（openid_only 下可见，绑定成功直接以 openid 查一次卡）、「说明」卡补绑定行｜证据：commit 9b3190e，定向服务端测试 37 全绿（`CardLookup*Test`+`KioskOpenid*Test`：绑定新测试 10、off/sms_verified 模式拒绝 2、openid_only 发码断言更新 1），`tsc --noEmit --skipLibCheck` 源码零报错，weapp/h5 双端构建通过；**激活仍缺微信侧凭据**（见卡点）
- [x] desktop Rust 测试进 CI 门禁——desktop.yml 新增 `rust unit tests` job（ubuntu、`cargo test`，libudev/webkit2gtk 等前置同构建矩阵，rust-cache），与 NSIS/deb 构建并行拦截逻辑回归；此前 src-tauri 23 个 `#[test]`（ESC/POS 字节流、打印模板、配置解析、扫码行切分、崩溃预算）只存在于源码，desktop.yml 只跑 `pnpm tauri build` 不跑测试，改坏逻辑要到打包产物才暴露；frontendDist 为入库静态资源（desktop/ui），无需前端构建即可编译｜证据：commit 48edcc5，本地 `cargo test` 23 全绿（宏强制重展开实证编译不依赖 pnpm 产物）、CI 配置改动门禁全套（server 全量 256 测试 0 失败 + docs check 通过 + YAML 校验）、CI run 38669705114 全绿（rust unit tests / windows-nsis / linux-deb 均 success）
- [x] 前端各端进 CI 门禁——新增 `.github/workflows/frontend.yml`（push main 且 app/kiosk/tablet/web 变更时跑）：app 类型检查（`tsc --noEmit --skipLibCheck`，屏蔽 Taro/@types 库自身约 97 条类型噪音）+ 微信端与 H5 双端构建；kiosk / tablet 跑 `pnpm build`（内含 `tsc -b`）；web 跑 eslint + 类型检查 + jest（此前 17 个 jest 测试、tsc、eslint 都不在任何 workflow 里跑，web 构建由 docker.yml 出镜像覆盖）。app/kiosk/tablet 此前只靠本地手跑，改坏了要到部署才暴露｜证据：本地同款命令逐条实跑通过（app `pnpm exec tsc --noEmit --skipLibCheck` 0 报错 + `build:weapp` ✔ + `build:h5` compiled（2 条预存在 Taro 警告）、kiosk 与 tablet `pnpm build` ✔、web `pnpm lint:js` 0 报错 + `pnpm tsc` 0 报错 + `pnpm test` 4 套 17 测试全绿），服务端全量测试全绿，docs check 与 docs build 通过；工作流 YAML 已本地解析校验
- [x] 我的页跨设备身份——已代决（2026-10-15 巡检，用户可推翻）：跨设备查预约走「手机号+验证码一次性双因子」（与查卡 `sms_verified` 同一通道同一审计口径，不新开开关）；openid 绑定已另行落地自助绑定（见已完成）；登录态仍不做。落地：服务端新增 `POST /api/v1/kiosk/my-appointments`（回未过期待到店预约，验证码一次性、每次尝试落 `card_lookup_logs` 审计，off/其他模式拒绝），小程序「我的」页加「查我的预约」表单（无预约如实显示空，不造数据）｜证据：本轮 commit，6 个服务端测试类 29 测试全绿（含 my-appointments 4 新测试 + 三态拒绝 2 新测试），`npx tsc --noEmit` 源码零报错，weapp/h5 双端构建通过
- [x] 卡项页 openid 路径接入——服务端新增 `POST /api/v1/kiosk/openid-session`（`wx.login` code 经微信 jscode2session 换 openid，`WECHAT_APP_ID`/`WECHAT_APP_SECRET` 未配置时 400 明确拒绝不静默，`session_key` 不出服务端，每次尝试含拒绝都落 `card_lookup_logs` 审计）；小程序端 weapp 登录换取（openid 仅存内存，失败静默回退短信路径；H5 无 `wx.login` 只走短信），卡项页身份带齐即带上、服务端按 mode 取用｜证据：本轮 commit，5 个服务端测试类 23 测试全绿（查卡三态 + openid-session 新增 3 + 凭据缺失 1），`npx tsc --noEmit` 源码零报错，weapp/h5 双端构建通过；**激活仍缺微信侧凭据**（正式 appid 替换 `app/project.config.json:5` 的 `touristappid` 占位 + AppSecret，用户侧）
- [x] 首页「我的卡项」区块去占位——入口联动「卡项」页（`pages/card`）+ 查过卡的本机摘要回显（称呼/持卡数/查询日期，7 天有效）；明细与余次不落本机（隐私口径同「裸手机号查卡不做」，见 `app/src/services/cards.ts` 注释与 docs/design/app-booking.md 候选取舍表新增行）｜证据：本轮 commit，`npx tsc --noEmit` 源码零报错（余 97 条全为 node_modules 库类型），weapp 与 h5 双端构建通过，docs check 通过
- [x] 手机端类型门禁收口——移除 3 处未使用的 React 导入（`app/src/app.ts`、`pages/index/index.tsx`、`pages/me/index.tsx`，`jsx: react-jsx` 下默认导入无用途）｜证据：本轮 commit，`npx tsc --noEmit` 源码零报错（余 97 条全为 node_modules 内 Taro/@types 库类型），weapp 与 h5 双端构建通过
- [x] 名医排班——名医等级与挂号费溢价上限｜证据：commit a28f000，4 测试
- [x] 小程序手机号查卡——openid 与短信双因子，含审计｜证据：commit 28cfc0d，18 测试
- [x] 药典剂量审方｜证据：commit 8f893c7，5 测试
- [x] 疗程卡营销赠次与转卡｜证据：commit ec1c671，207 测试
- [x] 经营趋势图表｜证据：commit 68f2617，209 测试
- [x] UI 按原型核对 84 条差距｜证据：commit 3001379~9c912b6，后端 209 测试
- [x] CI 接测试与 maven 缓存｜证据：commit 66556ed
- [x] 站点标题与 PWA 品牌口径｜证据：commit 66556ed
- [x] 路线图口径收口｜证据：commit 9c912b6
- [x] 文档与代码一致性审计｜证据：commit 5ebb8db
