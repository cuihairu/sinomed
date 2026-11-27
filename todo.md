# TODO

> 账本生成于 2026-10-11。排序依据（2026-10-14 用户令）：手机端优先——新功能先落手机端，影响手机端的缺陷排在同类桌面问题之前，本阶段不新增桌面功能。
> 历史巡检与审核轨迹见 docs/审计-文档一致性.md 与 docs/review/；本文件只留需求与证据。

## 待办

- [ ] 公众号模块第一期余项：素材上传/菜单推送/网页授权真实出网，待公众号正式凭据（appid+AppSecret）与公众号后台「网页授权域名」白名单登记（均用户侧，凭据到位即激活无需改码）；群发/模板/客服待对应工作台立项；第三期带参二维码未排期。 **[BLOCKED 用户侧凭据与域名白名单，见 docs/review/2026-10-30-decisions.md 第 2 项]** 复核 2026-11-05 仍缺 用户侧凭据与域名白名单。
  - 证据（2026-11-01 复核）：application.yml:57 enabled 仍 false、:58 WX_OA_TOKEN 空；已代决按 docs/review/2026-10-30-decisions.md 执行。
  - 复核（2026-11-04）：application.yml:57-58 仍为 false/空，正式凭据与域名白名单仍未到位。
  - 复核（2026-11-06）：application.yml:57-58 仍为 false/空，compose.yml/server/docker-compose.yml 均无 WX_OA_* 注入，全仓无真实 appid/AppSecret 或「网页授权域名」白名单登记；卡 B 远端上传（uploadRemoteMaterial→cgi-bin/material/add_material）已实现但真实出网压凭据；公众号定向测试绿（WxOfficialAccountHttpTest+WxMaterialCaptureHttpTest+WxMaterialLocalUploadHttpTest+WxMaterialRemoteUploadHttpTest+WxMaterialAdminHttpTest+WxAccountAdminHttpTest+WxMenusAndMaterialsSchemaTest = 47 tests, 0 failures, 0 errors, 0 skipped），仍缺 用户侧凭据与域名白名单。
  - 复核（2026-11-12）：server/src/main/resources/application.yml:57-58 仍 `enabled: ${WX_OA_ENABLED:false}` / `token: ${WX_OA_TOKEN:}` 空缺省，无真实 appid/AppSecret，无「网页授权域名」白名单登记 → 仍缺 用户侧凭据与域名白名单。
  - 复核（2026-11-21）：application.yml:57-58 仍 `enabled: ${WX_OA_ENABLED:false}` / `token: ${WX_OA_TOKEN:}` 空缺省，无真实 appid/AppSecret，无「网页授权域名」白名单登记 → 仍缺 用户侧凭据与域名白名单。
  - 复核（2026-11-27）：application.yml:57-58 仍 false/空缺省，compose.yml/server/docker-compose.yml 无 WX_OA_* 注入，无真实 appid/AppSecret 与「网页授权域名」白名单登记 → 仍 BLOCKED 用户侧凭据与域名白名单。
- [ ] 微信小程序正式凭据（appid+AppSecret）：真机运行与 openid 通道（openid-session/openid-bind）激活压在此（用户侧；公众号另用独立 appid 存 wx_accounts，非本项 WECHAT_APP_ID/WECHAT_APP_SECRET）。 **[BLOCKED 用户侧账号凭据与真机联调，见 docs/review/2026-10-30-decisions.md 第 1 项]**
  - 证据（2026-11-01 复核）：app/project.config.json:5 仍 touristappid；application.yml:52-53 空缺省；已代决按 docs/review/2026-10-30-decisions.md 执行。
  - 复核（2026-11-04）：app/project.config.json:5 仍 touristappid，application.yml:52-53 仍空缺省。
  - 复核（2026-11-06）：app/project.config.json:5 仍 touristappid，application.yml:52-53 仍空缺省；小程序构建/tsc 通过不解除凭据阻塞。
  - 复核（2026-11-12）：app/project.config.json:5 仍 `"appid": "touristappid"`；server/src/main/resources/application.yml:52-53 仍 `${WECHAT_APP_ID:}` / `${WECHAT_APP_SECRET:}` 空缺省 → 仍缺 用户侧账号凭据与真机联调。
  - 复核（2026-11-21）：app/project.config.json:5 仍 `"appid": "touristappid"`；application.yml:52-53 仍 `${WECHAT_APP_ID:}` / `${WECHAT_APP_SECRET:}` 空缺省 → 仍缺 用户侧账号凭据与真机联调。
  - 复核（2026-11-27）：app/project.config.json:5 仍 `"appid": "touristappid"`；application.yml:52-53 仍 `${WECHAT_APP_ID:}` / `${WECHAT_APP_SECRET:}` 空缺省 → 仍 BLOCKED 用户侧账号凭据与真机联调。
- [ ] 在线支付真实通道：缺商户号与密钥等真实凭据 + 用户授权接网关，付费红线不碰（用户侧）。 **[BLOCKED 用户侧商户资料与网关授权，见 docs/review/2026-10-30-decisions.md 第 3 项]**
  - 证据（2026-11-01 复核）：PayQrCodeController 仍仅按本地目录出收款码图，无网关/商户号/密钥配置；已代决按 docs/review/2026-10-30-decisions.md 执行。
  - 复核（2026-11-04）：PayQrCodeController.java:22/43 仍仅配置本地收款码目录，无商户号、密钥或网关接线。
  - 复核（2026-11-06）：PayQrCodeController.java 仍仅按 {sinomed.pay.qrcodes-dir} 本地目录出收款码图，application.yml 无商户号/密钥/网关配置，无在线支付通道。
  - 复核（2026-11-12）：application.yml 全仓 grep appid/AppSecret 仅 wechat 段命中，无商户号/密钥/网关配置 → 仍缺 用户侧商户资料与网关授权（付费红线不碰）。
  - 复核（2026-11-21）：PayQrCodeController.java:43 仍仅 `@Value("${sinomed.pay.qrcodes-dir:data/payqrcodes}")` 本地目录出图，全仓 grep gateway/merchantId/apiV3Key 零命中，无商户号/密钥/网关接线 → 仍缺 用户侧商户资料与网关授权（付费红线不碰）。
  - 复核（2026-11-27）：PayQrCodeController.java:43 仍仅本地目录出图；application.yml 无商户号/密钥/网关配置，付费红线不碰 → 仍 BLOCKED 用户侧商户资料与网关授权。
- [x] 配置开关效果动态文档页（双语）：逐开关 off/on 图 + 切换 GIF，流水线可重跑、兼作配置效果回归（kode todo C-24 同款，2026-11-01 用户令）——证据：已建成 docs/guide/config-switches.md 与 docs/zh/guide/config-switches.md（逐开关标注对应回归测试类，纳入 VitePress 双语侧栏）；**两态图与切换 GIF 由流水线真实产出**（commit 771244c）——`docs/scripts/gen-config-switch-visuals.sh` 起真 server（每态独立 SQLite 库）→ curl 关键端点 → ImageMagick 渲帧 → 拼 GIF，产物固定写 `docs/public/config-switches/`（9 PNG + 4 GIF，重跑覆盖）：
  - phone-lookup.mode 三态（off / openid_only / sms_verified）：逐态断言响应文案（「在线查卡已关闭」/「需要 openid」/「需要手机号与短信验证码」），3 帧 GIF。
  - official-account.enabled 两态：**false** 态用**故意错误签名**验证门控先于验签（模块开关拦住，不是验签）；**true** 态关键词命中回被动回复「营业时间 9:00-18:00」并断言 `in`/`out` 规则创建成功。
  - demo.seed 两态：探针改用免登录 `/api/v1/kiosk/items`（顾客分页需 JWT，两态都 401、看不出差异），false=空列表 vs true=已播种卡项。
  - sinomed.wechat.app-id 两态：必须 `PHONE_LOOKUP_MODE=openid_only`，否则 phone-lookup.off 守卫先触发、两态都回「在线查卡已关闭」、凭据开关一个字节都没被测到；未配置=「微信凭据未配置」，已配置=**真实出网** jscode2session（微信侧 `errcode=40013 invalid appid`）。
  - 幽灵开关：`famous-doctor-surge-cap-percent` 在 server 生产代码无消费点（全仓唯一引用是 `FamousDoctorTest.surgeCapConfigDefault` 的缺省装配断言），文档先前称的「溢价不得超过该百分比」尚未接线 → 该节改为预留态、不出两态图（不给未生效行为摆拍）。
  - 门禁：`pnpm -C docs check` 绿（图片引用无断链）、`pnpm -C docs build` 绿（build complete）。
  - 【2026-11-15 回退未完成】Codex 深审第 6 条：验收要求「逐开关 off/on 图 + 切换 GIF、流水线可重跑」，现产物仅文字描述 + 测试类映射，缺 off/on 截图、切换 GIF 与可重跑生成流水线——恢复未完成状态，待视觉产物与流水线落地并验收后再勾选。——**已闭环（commit 771244c）**：视觉流水线与 9 PNG + 4 GIF 落地，`pnpm -C docs check` / `pnpm -C docs build` 绿。
- [x] 公众号回调排重 check-then-insert 竞态：并发重复消息可触发 dedupe_key 唯一索引冲突未捕获 → 500 → 微信重试放大（C3，docs/review/2026-11-01-code-review.md；证据 WxOfficialAccountService.onMessage:270-275）——证据：已修复（logMessage 改 saveAndFlush+DataIntegrityViolationException 捕获→落 duplicate 审计回 success，并发双投递不再 500），WxOfficialAccountHttpTest.concurrentDuplicateMsgIdBothSucceedWithoutRace 并发双线程定向绿。
- [x] 预约时段互斥 check-then-act 竞态：两个自助约期并发过 count==0 后双双落库，时段双占（C4，docs/review/2026-11-01-code-review.md；证据 AppointmentServiceImpl.ensureSlotFree:69-91 + KioskController:249）——证据：已修复（AppointmentEntity 增 slot_key/room_slot_key @Column(unique=true)，SQLite 方言内联为唯一列，MySQL 建唯一约束；status 0/1 落 "<staff|room>|<hourStart>"、终态置 NULL 释放；save 改 saveAndFlush+唯一违例捕获转 400；Kiosk 自助路径同样接线），AppointmentSlotTest.concurrentSameSlotOnlyOneSucceeds 并发双线程定向绿（连续 3 次过，schema 实查 slot_key/room_slot_key 均带 unique）。
- [x] 账号启用判定一致化：onMessage 用 `!=0`、其余 `==1`，saveAccount 补 enabled∈{0,1} 校验，防脏值 2 出现「收消息按启用、验 URL 按停用」（C5，docs/review/2026-11-01-code-review.md；证据 WxOfficialAccountService:211 vs :196、:1057）——证据：已修复（onMessage 改 `==1` 与 verifyUrl/其余一致；saveAccount 增 enabled∈{0,1} 校验，脏值 2 直接 400），WxOfficialAccountHttpTest.dirtyEnabledRejectedConsistentlyOnCallbackAndVerify + WxAccountAdminHttpTest.rejectsInvalidFields（enabled=2 断言）定向绿。
- [x] 短信验证码错试无限爆破：6 位码 4 个免登录端点无尝试计数，有效期内可枚举 10^6（C1，P1 安全，docs/review/2026-11-01-code-review.md；证据 KioskController.consumeSmsCode + WxOfficialAccountService.consumeBindSmsCode）——证据：已修复（SmsCodeEntity 增 attempts 列；SmsCodeRepository.incrementAttempts 单条 UPDATE 原子递增并达 5 次上限烧码；consume 两路径均改用；后续 718e6b5 再补正确码单用原子化 markUsed，并发正确码消费仅一次；发码路径补 setAttempts(0) 初始化），CardLookupSmsTest.atomicIncrementBurnsExactlyAtMax + concurrentCorrectCodeConsumedOnlyOnce 与 WxBindHttpTest.bindCodeAttemptsInitializedToZeroOnIssue 定向绿；修复 commit b8a0e7d / 19ec512 / 718e6b5。
- [x] JWT 过期后管理端假死：401 只弹 toast 不跳登录，员工每天触发一次全界面假死（C2，P1 功能，docs/review/2026-11-01-code-review.md；证据 web/src/requestErrorConfig.ts:70-71/89-97）——证据：已修复（errorHandler 401 分支清 localStorage jwt 并 history.push('/user/login')；登录请求自身 401 不重定向防回环），requestErrorConfig.test.ts 3 例定向绿（含登录请求自 401 不跳、非 401 维持 toast）；修复 commit 99db876。
- [x] 回调验签改常量时间比较（C6，docs/review/2026-11-01-code-review.md；证据 WxSignatureUtil.java:22）——证据：已修复（WxSignatureUtil.check 改 MessageDigest.isEqual 常量时间逐字节比较，去 equalsIgnoreCase 大小写折叠），WxSignatureUtilTest.rejectsWrongSignature 补等长大写拒绝断言定向绿。
- [x] WxOauthTicketStore 未兑换票据清理：签发未兑换票据永久滞留（C7，docs/review/2026-11-01-code-review.md；证据 WxOauthTicketStore.java:29）——证据：已修复（issue 顺带 purgeExpired 清过期未兑换票据，内存有界于 TTL 窗口），WxOauthTicketStoreTest.redeemIsSingleUseAndWrongAccountRejected 定向绿。
- [x] 素材拉取 mimeType 规范化对齐上传路径白名单（C8，docs/review/2026-11-01-code-review.md；证据 WxOfficialAccountService.java:1448）——证据：已修复（captureMaterial 经 canonicalizeMimeType 剥参+小写+白名单规范化入库，白名单外回 null 兜底 octet-stream），WxMaterialCaptureHttpTest.fetchMimeNormalizedToWhitelist（含参数/大小写与 text/html 两断言）定向绿。
- [x] uploadLocalMaterial 落盘后 save 失败清孤儿文件（C9，docs/review/2026-11-01-code-review.md；证据 WxOfficialAccountService.java:1243-1256）——证据：已修复（uploadLocalMaterial 改 saveAndFlush，catch RuntimeException 时 Files.deleteIfExists(target) 清刚写孤儿文件再上抛，与远端上传路径同口径），全量 458 测试绿。
- [x] web JWT 存储纵深防御评估（localStorage 可被 XSS 窃取，当前无注入点，评估 httpOnly cookie 迁移）（C10，docs/review/2026-11-01-code-review.md；证据 web/src/pages/User/Login/index.tsx:91）——证据：评估完成，维持 Bearer+localStorage（非目标、可推翻）：全 web 仅小票窗口 document.write，服务端 ReceiptPrintServiceImpl 对 clinic/customer/staff/item/pay 全部 esc；无 dangerouslySetInnerHTML/eval/new Function；httpOnly 迁移需改登录/请求/登出全链路并补 CSRF/SameSite，当前无注入点下收益不足以引入该破坏面；重评触发=新增富文本/第三方脚本/未转义 HTML 注入面。
- [x] docs 构建配置分脑：package.json 弃用 `pnpm.overrides`（vite 6.4.4）与 workspace yaml（6.4.3）、lockfile（6.4.4）三处不一致，pnpm ≥11 拒跑 docs 脚本、CI `--frozen-lockfile` 越过 pnpm 10 即断（C12，P1 构建，docs/review/2026-11-01-code-review.md；证据 docs/package.json:18-22 + docs/pnpm-workspace.yaml:1-3）——证据：已修复（commit 07f5709：删死 package.json 的 `pnpm` 字段、workspace yaml 对齐 6.4.4、补 onlyBuiltDependencies esbuild、重生成 lockfile），`pnpm -C docs check` + `pnpm -C docs build` 双绿。
- [x] swagger/knife4j/actuator permitAll 按 prod profile 收紧（C11，docs/review/2026-11-01-code-review.md；证据 SecurityConfig.java:51-58）——证据：已修复（SecurityConfig 把 API 文档面抽出 DOCS_PUBLIC，仅非 prod profile permitAll；/actuator/** 无依赖端点已移除特例，未来端点落入 anyRequest 默认需认证），DocsAccessHttpTest.docsEndpointsNotBlockedBySecurityWithoutProdProfile + DocsAccessProdHttpTest.docsEndpointsRequireAuthUnderProd 定向绿。

## 已代决（非目标/拍板，用户可推翻）

- [x] 西药与成药处方、药品目录维持非目标（2026-10-14；单店中医馆定位，docs/design/dispensing.md）。
- [x] 多门店与连锁维持非目标（2026-10-14；单店模型无门店维度）。
- [x] 采购单据与盘点维持非目标（2026-10-14；出入库台账口径已够用）。
- [x] react-router dependabot #147 接受风险关告警（CVE-2026-53666；umi 4 纯 SPA 无 SSR，漏洞路径不可达；重评触发=umi/react-router 大版本升级）。
- [x] 小程序自助取消预约恢复顾客侧取消（原「取消不核验身份」前提因 2026-10-14/15 双因子失效）——证据：KioskAppointmentCancelTest 13 例 + 模式门禁 2 例，全量 283 测试绿，12 份文档双语同步。
- [x] 顾客 openid 自助绑定走小程序自助（openid+一次性短信码），不做管理端登记/双轨——证据：commit 9b3190e，定向 37 测试绿，weapp/h5 双端构建过。
- [x] 我的页跨设备身份走手机号+验证码一次性双因子，登录态不做——证据：my-appointments 4 新测试 + 三态拒绝 2，tsc 0 错，双端构建过。

## 已完成

### 公众号模块（2026-10-19 → 11-02）

- [x] 第一期骨架 + 能力矩阵（四表/验签/路由/惰性开关）——证据：17 新测试，全量 342 绿。
- [x] 消息流水只读端点 `GET /api/v1/wx/message-logs`——证据：WxMessageLogsHttpTest 5 例，全量 347 绿。
- [x] 粉丝↔顾客绑定服务端（code/bind/unbind + 审计）——证据：WxBindHttpTest 11 例，全量 359 绿。
- [x] wx_menus/wx_materials 建表补齐 + Hibernate/SQLite 多列唯一静默丢失修复（启动期 CREATE UNIQUE INDEX）——证据：WxMenusAndMaterialsSchemaTest 5 例，全量 364 绿。
- [x] 图片消息素材拉取落盘（同 media_id 防重下、3s 双上限、超限不落行）——证据：WxMaterialCaptureHttpTest 4 例。
- [x] 网页授权服务端半边（start/callback/ticket，HMAC state）——证据：WxOauthHttpTest 4 例，全量 377 绿。
- [x] H5 绑定静态单页 + callback 回跳补 `?account=`——证据：WxOauthHttpTest 第 5 例，全量 378 绿。
- [x] 自动回复规则 CRUD 服务端——证据：WxAutoReplyAdminHttpTest 6 例，全量 384 绿。
- [x] 规则编辑 web 页 + 管理端账号列表端点（密钥不下发）——证据：第 7 例 listsAccountsForAdminWithoutSecrets，全量 385 绿。
- [x] 安全/兼容模式消息加解密（WxMsgCryptor，OpenSSL KAT 锚定）——证据：WxMsgCryptorTest 6 + WxEncryptModeCallbackHttpTest 5，全量 396 绿。
- [x] 管理端消息流水 + 素材台账查看 web 页（blob 预览、路径越界拒）——证据：WxMaterialAdminHttpTest 5 例，全量 401 绿。
- [x] 自定义菜单编辑/草稿 CRUD（两级校验/容量/加权名长 + 能力降级）——证据：WxMenuAdminHttpTest 4 例，全量 405 绿。
- [x] WxJava SDK 薄封装 + 消息/事件路由（加解密与 OAuth 仍手写）——证据：commit 79d387d，+10 测试，全量 420 绿。
- [x] 素材上传与治理本地半边（永久台账 + 本仓存储，MANAGER+）——证据：WxMaterialLocalUploadHttpTest（后扩 6 例），全量 426-428 绿。
- [x] 公众号账号配置入口（AES 落库、掩码回显、缓存 evict）——证据：commit e448b3d，WxAccountAdminHttpTest 4 例，全量 432 绿。
- [x] 素材远端上传（`POST /materials/remote/{accountId}`，错误清孤儿透传）——证据：WxMaterialRemoteUploadHttpTest 7 例，全量 439 绿。
- [x] token 取用优先级 resolveToken（env 优先/账号行兜底）+ 账号 400 契约锁定——证据：commit b6a1dbf，WxAccountTokenPrecedenceHttpTest/WxAccountDbTokenHttpTest，全量 443 绿。
- [x] 管理端整面角色门禁（18× MANAGER+ / 2× ADMIN，回调与粉丝侧免登录不动）——证据：commit 839342d，WxAdminRoleGuardHttpTest 5 例，全量 448 绿。
- [x] web /wx 路由加 canAdmin 门禁（MANAGER+ 与后端同口径）——证据：commit b096e94，web lint/tsc/jest 24 绿。
- [x] WxOfficialAccountController 三个分区注释「需登录」改「需 MANAGER+」——证据：commit b096e94，WxOfficialAccountController.java:181/:266/:316。
- [x] WxAdminRoleGuardHttpTest 补 MANAGER 写端点放行断言（capabilities/规则/菜单 CRUD、push、unbind）——证据：commit b096e94，全量 450 绿。
- [x] wx_accounts.app_secret/aes_key AES-GCM 加密落库——证据：WxCredentialCryptoTest 3 + NoKeyTest 2。

### 查询契约与过滤修复

- [x] item/page name/price 过滤收口（QBE 静默忽略改 Specification）——证据：ItemFilterHttpTest 5 例，全量 312 绿。
- [x] commission/record/page 双参 AND 收口——证据：CommissionRecordHttpTest 新例，全量 313 绿。
- [x] customer/staff 分页 name 改包含匹配 + treat/fuzzy 空命中响应形——证据：11 新测试，全量 325 绿。
- [x] 小程序预约页展示服务时长（KioskItemView 透 durationMinutes）——证据：KioskItemHttpTest 新例，全量 314 绿。

### 小程序手机端

- [x] 手机号查卡（openid 与短信双因子，含审计）——证据：commit 28cfc0d，18 测试。
- [x] 卡项页 openid 路径接入（openid-session，session_key 不出服务端）——证据：23 测试绿，双端构建过。
- [x] 首页「我的卡项」区块去占位（本机摘要 7 天有效，明细不落本机）——证据：tsc 0 错，双端构建过。
- [x] 手机端类型门禁收口（移除 3 处未用 React 导入）——证据：tsc 0 错，双端构建过。

### 美容业态扩展（2026-10-18，salon 仓并入）

- [x] 项目类型扩展 items.category——证据：ItemCategoryHttpTest 4 例。
- [x] 预约资源模型扩展（rooms 工位 + appointments.room_id + 双维时段互斥）——证据：RoomHttpTest 3 + AppointmentRoomConflictHttpTest 5。
- [x] 技师提成规则与落账（规则/快照/负向冲销/合计报表）——证据：CommissionRuleHttpTest 3 + CommissionRecordHttpTest 5。
- [x] 美容客群标签 + PIPL 单独同意——证据：CustomerTagHttpTest 4 例。
- [x] 模型核对（复用医馆实体，修正调研两处偏差）——证据：docs/design/salon-extension.md（中英双份）。

### 基建与 CI

- [x] CI 接测试与 maven 缓存；站点标题与 PWA 品牌口径——证据：commit 66556ed。
- [x] 前端各端进 CI 门禁（app 双端构建 + kiosk/tablet/web 检查）——证据：本地同款命令逐条实跑过 + 工作流 YAML 校验。
- [x] desktop Rust 测试进 CI 门禁——证据：commit 48edcc5，本地 23 绿，CI 全绿。
- [x] dependabot #366 清零（pin follow-redirects@1.16.1）——证据：app CI 同款门禁全绿。
- [x] dependabot #383 清零（serialize-javascript 7.1.3）——证据：app CI 同款门禁全绿。

### 早期功能

- [x] 名医排班（等级与溢价上限）——证据：commit a28f000，4 测试。
- [x] 药典剂量审方——证据：commit 8f893c7，5 测试。
- [x] 疗程卡营销赠次与转卡——证据：commit ec1c671，207 测试。
- [x] 经营趋势图表——证据：commit 68f2617，209 测试。
- [x] UI 按原型核对 84 条差距——证据：commit 3001379~9c912b6，209 测试。
- [x] 路线图口径收口——证据：commit 9c912b6。
- [x] 文档与代码一致性审计（首轮）——证据：commit 5ebb8db。

### 文档与站点

- [x] docs 根目录 8 份双语配对——证据：commit d243801。
- [x] docs 子目录 23 份双语配对（guide/server/design/research/marketing）——证据：docs check + vitepress build 双绿。
- [x] 文档一致性审计第八轮（README 徽章/链接、wx 模块文档缺口、api 标题、zh 双语漂移 B1-B9）——证据：docs/审计-文档一致性.md §VIII，docs check + vitepress build 绿。

## 审核发现（已收口）

- [x] OAuth 证据行号 691→645 修正——证据：WxOauthHttpTest 绿。
- [x] start 302 证据行号 104→106-107 修正。
- [x] 素材 page/file/upload/delete 四端点补 MANAGER+（USER 403 断言）。
- [x] 本地上传补 MIME↔扩展名一致校验（evil.png+text/html 400）。
- [x] materialType 落标准枚举（png→image、mp4→video）。
- [x] 删除本地素材容忍文件已缺失（照常清台账行）。
- [x] 字节往返断言改逐字节 assertArrayEquals。
- [x] 400 契约逐项锁定（txt/空/超限/MIME 不符/未知账号）+ api.md:161 同步。
- [x] MIME 校验剥 `;` 参数后精确比较，回放规范 MIME——证据：commit f851e1d，全量 427 绿。
- [x] api.md 素材契约改「需 MANAGER+」（双语）——证据：commit f851e1d。
- [x] 设计稿 §4.5 尾注本地上传已交付（双语）——证据：commit f851e1d。
- [x] USER 403 断言补 page/file 两端点——证据：commit e73c626。
- [x] mp4 video 类型落库与 MIME 校验测试——证据：commit 2c54fe1，全量 428 绿。
- [x] 待办改名「微信小程序正式凭据」澄清两套 appid 体系——证据：commit 37485e2。
- [x] 回执三处行号改引实测——证据：commit e09fc3d。
- [x] 回执自述行号同步实测 1239/1259-1264。
- [x] 1667c7b..a88dc28 无发现。
- [x] 无发现回执范围钉死 1667c7b..a88dc28（弃移动 HEAD 引用）。
- [x] token 取用优先级 resolveToken 全接线（验签/OAuth state/SDK 配置）——证据：commit b6a1dbf，全量 443 绿。
- [x] WxAccountView token 注释与明文列实况一致——证据：commit b6a1dbf。
- [x] 账号 400 契约锁定（更新不存在 id/appId 唯一冲突）——证据：commit b6a1dbf。
- [x] mp4 描述字段透传断言（title/introduction）——证据：commit fd99509，全量 443 绿。
- [x] 回执旧 hash 改 HEAD 祖先 b6a1dbf/fd99509。
- [x] WxJava 版本引用改实际依赖 4.8.6-20261024.175727——证据：commit fd99509。
- [x] 回执定位引用 todo.md:58→60 修正。
- [x] --muted 下调 #5e635f 恢复 ink>muted>faint 亮度层级——证据：web jest 19 绿。
- [x] global.less 选择器收窄 .ant-pro-field-index-column-border——证据：web jest 19 绿。
- [x] colorTextPlaceholder 复用 FAINT 常量——证据：web jest 19 绿。
- [x] pnpm overrides 迁 pnpm-workspace.yaml（pnpm 12 兼容），冻结 install + 双端构建过——证据：commit 0fed355。
- [x] --faint 根因修复 #696c68 四主题浅底全过 AA，tokens.test 2 例锁定——证据：web jest 21 绿，server 443 绿。
- [x] colorTextPlaceholder 改运行时读 --faint（tokens.css 唯一真相源）——证据：commit 33e0db6。
- [x] index 徽标恢复 top-3/4+ 两档视觉分层——证据：commit 34e890e。
- [x] global.less 注释对比度值同步 #696c68 白字 5.32:1。
- [x] tokens.test 改运行时解析 tokens.css（删 TS 字面量硬编码）——证据：web jest 21 绿。
- [x] brandTints 补默认 :root 主题入 AA 锁（四主题全覆盖）。
- [x] todo 注记行号 58-60→56-58 修正。
- [x] dbaa279a..HEAD 逐 commit 复核无发现——证据：web 21 / server 443 绿。
- [x] README 文档站链接双斜杠改单斜杠（en/zh）——证据：commit 本轮，curl 200。
- [x] README docs 徽章 href 由演示站改指 github.io 文档站（en/zh 各指对语言）——证据：commit 本轮，curl 200。
- [x] docs/zh/app-white-label.md --diag 口径与英文镜像同步——证据：commit 本轮，build-brand.mjs:25/120。
- [x] docs/zh/design/wechat-official-account.md §6.3 补 2026-11-01 角色门禁交付注记——证据：commit 本轮，839342d。

## 审核发现

- [x] 审核发现：docs/server/data-model.md 与 docs/zh/server/data-model.md 的 sms_codes 表缺 attempts 列行——证据：已清（commit b723156 已在 used 行后补 attempts 列行；HEAD docs/server/data-model.md:368 / docs/zh/server/data-model.md:368 存在），原记录基于 b723156 前的旧视图。
- [x] 审核发现：发码路径未显式初始化 attempts——kiosk sms-code 与 wx bind code 发码时均未 setAttempts(0)，实体无 @DynamicInsert/@ColumnDefault，新码 attempts 列插入 NULL 而非列定义的 integer default 0，与"计数器从 0 起"语义略有出入（读取端 ==null?1 已容错，无实际功能 bug，属数据规范性小瑕疵）（server/src/main/java/com/sinomed/controller/KioskController.java:281、server/src/main/java/com/sinomed/service/WxOfficialAccountService.java:500）——证据：已修复（KioskController.smsCode 与 WxOfficialAccountService.issueBindCode 均补 setAttempts(0)），CardLookupSmsTest.attemptsInitializedToZeroOnIssue + WxBindHttpTest.bindCodeAttemptsInitializedToZeroOnIssue 定向绿。
- [x] 审核发现：docs/server/data-model.md:375 与 docs/zh/server/data-model.md:375 声称「除 wx_binding_logs 外均有 update_time」，但 wx_message_logs 实体（server/src/main/java/com/sinomed/entity/WxMessageLogEntity.java:73-74）同样只有 create_time、无 update_time，与「列名逐字取自实体」宣称不符（docs/server/data-model.md:375）——证据：已修复（docs/server/data-model.md:375 / docs/zh/server/data-model.md:375 改述「wx_message_logs 与 wx_binding_logs 为追加型、无 update_time，其余 5 表另有 update_time」，与实体实况逐字相符）。
- [x] 审核发现：短信验证码错试计数在 consumeSmsCode（server/src/main/java/com/sinomed/controller/KioskController.java:628-637）与 consumeBindSmsCode（server/src/main/java/com/sinomed/service/WxOfficialAccountService.java:760-769）为无事务/无锁的读-改-写，并发错试可丢增量、绕过 5 次烧码上限（server/src/main/java/com/sinomed/controller/KioskController.java:630）——证据：已修复（SmsCodeRepository.incrementAttempts 单条 UPDATE 原子递增+达上限烧码，consume 两路径均改用），CardLookupSmsTest.atomicIncrementBurnsExactlyAtMax 定向绿。

- [x] 审核发现：时段互斥键/消息排重的「唯一违例」判定靠异常消息子串匹配 "constraint"/"unique"（AppointmentServiceImpl.isSlotKeyConflict、KioskController.isSlotKeyConflict、WxOfficialAccountService.isUniqueConstraint 逐层 getCause+getMessage 扫描），而默认方言 MySQL（application.yml:10/28）的重复键 SQLException 消息为 "Duplicate entry '...' for key '...'"（不含该两词），MySQL 下唯一冲突会被误判为普通 DataAccessException 上抛——建约并发双落回 500 而非 400「该时段已约满」（AppointmentServiceImpl.java:77、KioskController.java:279），且 WxOfficialAccountService 的 dedupe 防「微信重试放大」目标落空（WxOfficialAccountService.java:492）；全部测试仅跑 SQLite（其消息含 "constraint"），掩盖了该方言差异（server/src/main/java/com/sinomed/service/impl/AppointmentServiceImpl.java:74）——证据：已修复（新增 DbUniqueUtil.isUniqueViolation 按 DataIntegrityViolationException 类型判定为主、消息扫描兜底并补 "duplicate entry"/"for key" 匹配，三处判定均改走该工具），DbUniqueUtilTest.mysqlDuplicateEntryMessageDetected + sqliteConstraintMessageDetected + nonConstraintViolationNotMisclassified 定向绿。

- [x] 审核发现：验证码「一次性」成功消费路径仍是无锁读-改-写（issued.getUsed()==1 快检后 issued.setUsed(1); save），并发两个正确码请求都能读到 used=0 并双双置已用，同码被消费两次；本轮原子递增仅加固错试路径，未覆盖此正确码单次消费竞态，且无并发正确码消费测试（server/src/main/java/com/sinomed/controller/KioskController.java:658）——证据：已修复（SmsCodeRepository.markUsed 单条 UPDATE 置 used=1 仅当原值 0，返回受影响行数；Kiosk consumeSmsCode 与 Wx consumeBindSmsCode 均改用，返回 0 时拒绝+审计），CardLookupSmsTest.concurrentCorrectCodeConsumedOnlyOnce 并发双投递定向绿。

- [x] 审核发现：远端素材拉取 captureMaterial 先 Files.write(target, body) 再 materialRepository.save(material)，save 抛错被 catch(Exception) 吞掉仅记 warn，刚写盘文件成孤儿；本轮「清理孤儿素材文件」（commit 66fa20e）只补了 uploadLocalMaterial 上传路径，下载路径同型缺口未处理（server/src/main/java/com/sinomed/service/WxOfficialAccountService.java:1498）——证据：已修复（captureMaterial 改 saveAndFlush，catch RuntimeException 时 Files.deleteIfExists(target) 清刚写孤儿文件再上抛，与上传路径同口径），WxMaterialCaptureHttpTest.saveFailureAfterRemoteCaptureDeletesOrphanFile 定向绿。

- [x] 审核发现：测试缺口——并发唯一索引兜底（AppointmentSlotTest.concurrentSameSlotOnlyOneSucceeds）只覆盖管理端 /api/v1/appointment/ 建约路径，Kiosk 自助约期自带 saveAndFlush + isSlotKeyConflict 的并发路径未做并发验证（server/src/main/java/com/sinomed/controller/KioskController.java:256）——证据：已补齐（KioskAppointmentTest.concurrentSameSlotOnlyOneSucceeds 并发双线程自助约同一整点，断言恰一成功一 400「该时段已约满」），定向绿。

- [x] 审核发现：本轮 diff 未见问题（3cdffe71..HEAD，仅 1 个提交 1cc2ca5，改动 README.md/README.zh.md 徽章排版文档，已全量逐行审查，无安全/正确性/测试缺口）——证据：复核 1cc2ca5 实为修 README 徽章行嵌套 <p> 标签与去重复 Apache-2.0 徽章（仅文档展示，无代码/安全/测试影响），git show --stat 确认仅 README.md/README.zh.md 两文件，无安全/正确性/测试缺口。

- [x] 审核发现：DbUniqueUtil.isUniqueViolation 以 `instanceof DataIntegrityViolationException` 为主判定，但该类型并非「仅唯一键违例」——Spring 会把 NOT NULL 违例（MySQL 1048/SQLSTATE 23000）、外键违例、data-too-long 等同样包装成 DataIntegrityViolationException；约期/消息排重路径下这类非唯一完整性错误会被误判为「该时段已约满」400（AppointmentServiceImpl.java:67、KioskController.java:262）或误判为「重复消息」静默回 success 造成消息不落库（WxOfficialAccountService.java:481），掩盖真实 500。旧消息扫描（仅 "constraint"/"unique"）在 MySQL 的 NOT NULL 消息 "Column cannot be null" 下反而不会命中、会如实上抛；类型判定把它提前归为冲突是回退。且 DbUniqueUtilTest 只测了死锁（CannotAcquireLockException，非 DIVE）负例，未测「DIVE 但非唯一（NOT NULL/FK）应判 false」这一关键负例（server/src/main/java/com/sinomed/util/DbUniqueUtil.java:23、server/src/test/java/com/sinomed/DbUniqueUtilTest.java:38-44）——证据：已修复（isUniqueViolation 不再按 DIVE 类型一刀切，改沿链判 MySQL 1062/SQLite 19/PG 23505/SQL Server 2601/2627 等唯一错误码与 "duplicate entry"/"unique constraint" 消息，NOT NULL/FK/超长如实上抛不再误判），DbUniqueUtilTest 补 notNullViolationWrappedAsDataIntegrityViolationNotMisclassified + foreignKeyViolationWrappedAsDataIntegrityViolationNotMisclassified + dataTooLongWrappedAsDataIntegrityViolationNotMisclassified 三项定向绿，全量 470 绿。
- [x] 审核发现：SecurityConfig 把 `/actuator/**` 列入 DOCS_PUBLIC 并按 prod 收紧（C11），但 pom.xml 无 spring-boot-starter-actuator 依赖（grep 0 命中），/actuator/** 实际无任何端点（恒 404）——该子项收紧无对象可验证，DocsAccessHttpTest 与 DocsAccessProdHttpTest 也仅覆盖 swagger/doc.html/v3-api-docs、未覆盖 actuator；若后续加 actuator 依赖则 C11 收紧才真正生效，当前属未验证的空转配置（server/src/main/java/com/sinomed/config/SecurityConfig.java:46、server/pom.xml）——证据：已修复（复核属实后删除无依赖的 /actuator/** 空转特例；未来引入 spring-boot-starter-actuator 时端点落入 anyRequest 默认需认证，届时应补端点定向回归）。
- [x] 审核发现：本轮（2aef5f0）把 swagger/knife4j 与 actuator 收紧为「prod 下需认证」，但 docs/server/api.md:8,15 与 docs/zh/server/api.md:8,15 仍把这些路径列作无条件「放行路径 / 免登录」，未标注 prod 例外，与 SecurityConfig 现况相矛盾（docs/server/api.md:8、docs/zh/server/api.md:8）——证据：已修复（docs/server/api.md:8 与 docs/zh/server/api.md:8 改「登录与文档」拆分，文档标注「prod 下需认证」例外；actuator 无依赖端点，不再作为文档面特例列出），docs check + build 绿。
- [x] 审核发现：docs/guide/config-switches.md:17 与 docs/zh/guide/config-switches.md:17 称 `CardLookupOpenidTest`（8 项），但该类实际只有 7 个 @Test（括号内亦只列 7 项：openid 查卡命中/缺 openid 拒/未绑定拒/取码供绑定/my-appointments 拒/appointment-cancel 拒/成功与拒绝落审计），计数虚标 8 与列项不符（docs/guide/config-switches.md:17）——证据：已修复（docs/guide/config-switches.md:17 与 docs/zh/guide/config-switches.md:17 计数 8→7，与 CardLookupOpenidTest 实际 7 个 @Test 相符），docs check + build 绿。
- [x] 审核发现：todo.md:23 证据引用 `DocsAccessHttpTest.docsEndpointsPublicWithoutProdProfile`，但该类实际方法名为 `docsEndpointsNotBlockedBySecurityWithoutProdProfile`（DocsAccessHttpTest.java:30），证据行引用的方法名不存在，属回执引用错误（todo.md:23）——证据：已修复（todo.md:23 引用方法名改 docsEndpointsNotBlockedBySecurityWithoutProdProfile），与 DocsAccessHttpTest.java:30 实际方法名相符。
- [x] 审核发现：SecurityConfig 用 `environment.getActiveProfiles()` 判 prod，仅返回显式激活的 profile；若 prod 经 `spring.profiles.default=prod`（默认 profile，非 active）激活则 getActiveProfiles() 为空、判定 false，C11 收紧静默失效。建议改 `environment.acceptsProfiles("prod")`（覆盖 active+default）或同时检查 getDefaultProfiles()（server/src/main/java/com/sinomed/config/SecurityConfig.java:63）——证据：已修复（SecurityConfig.java:63 改 environment.acceptsProfiles("prod") 覆盖 active+default），新增 DocsAccessDefaultProfileHttpTest 用 spring.profiles.default=prod 验证 docs 端点未登录 401，定向绿。
- [x] 审核发现：DocsAccessHttpTest.status() 的 `catch (Exception e) -> 500` 把连接失败/传输异常等非 HTTP 错误也当作「已过安全层」返回 500（≠401），断言仍绿——若服务未起或请求根本没到安全层，测试误报通过；建议只 catch HttpStatusCodeException（5xx），其余异常应上抛（server/src/test/java/com/sinomed/DocsAccessHttpTest.java:45-48）——证据：已修复（DocsAccessHttpTest.status 只 catch HttpStatusCodeException，其余异常上抛不吞），docs 端点测试仍绿。

- [x] 审核发现：SQLite 方言下 `errorCode == 19` 并非唯一约束专属码——实测本项目 sqlite-jdbc 3.53.4.0 对 NOT NULL/FK/CHECK/UNIQUE 违例一律返回基码 19（仅靠消息区分 SQLITE_CONSTRAINT_NOTNULL/FOREIGNKEY/CHECK/UNIQUE），SQLite 的 NOT NULL/FK/CHECK 消息不含 "unique" 落空消息扫描后被 `errorCode==19` 判为唯一冲突，恰是本提交声称修复的误判类；且新增负例只测 MySQL 码 1048/1452/1406，未测 SQLite 码 19 + 非唯一消息（全部测试与 demo 实际跑 SQLite，compose.yml:22-24）——SQLite 下约期 NOT NULL/FK 仍会被误报「该时段已约满」400、消息落库误判排重被吞（server/src/main/java/com/sinomed/util/DbUniqueUtil.java:52、server/src/test/java/com/sinomed/DbUniqueUtilTest.java:47-70）——证据：已修复（isUniqueSqlCode 对 errorCode==19 增加消息含 "unique" 判定，DbUniqueUtil.java:48-52），新增 sqliteNotNullCode19NotMisclassifiedAsUnique / sqliteCheckCode19NotMisclassifiedAsUnique / sqliteUniqueCode19StillDetected 三例，DbUniqueUtilTest 定向绿 + 全量 473 绿；commit e9eba1e（DbUniqueUtil.java:48-52、DbUniqueUtilTest.java:71-87）。
- [x] 审核发现：isUniqueViolation 消息匹配含冗余死条件——`"unique constraint"` 出现两次（:29 与 :33）且被 :28 的 `(unique && (constraint||failed))` 完全包含，`"duplicate key value"`（:31）被 :30 的 `"duplicate key"` 包含，均为死条件（server/src/main/java/com/sinomed/util/DbUniqueUtil.java:29-33）——证据：已修复（删除 `unique constraint` 重复条件与 `duplicate key value` 死条件，DbUniqueUtil.java:27-30），DbUniqueUtilTest 定向绿 + 全量 473 绿；commit e9eba1e（DbUniqueUtil.java:27-30）。
- [x] 审核发现：`environment.acceptsProfiles("prod")` 走的是 String... 重载，该重载在 Spring Framework（本仓解析 spring-core 7.0.10）已 `@Deprecated`（javap 实测标注），建议改 `acceptsProfiles(Profiles.of("prod"))`（server/src/main/java/com/sinomed/config/SecurityConfig.java:62）——证据：已修复（SecurityConfig.java:62 改 environment.acceptsProfiles(Profiles.of("prod"))，import Profiles），DocsAccessDefaultProfileHttpTest 定向绿 + 全量 473 绿；commit e9eba1e（SecurityConfig.java:8、:62）。
- [x] 审核发现：SecurityConfig.java:78 行内注释仍写「API 文档面 / Actuator：prod 收紧为需认证（C11）」，但本提交已把 `/actuator/**` 从 DOCS_PUBLIC 移除，注释与现况不符；DocsAccessHttpTest.java:15 类注释「与 Actuator 的 permitAll」同属过时（server/src/main/java/com/sinomed/config/SecurityConfig.java:78、server/src/test/java/com/sinomed/DocsAccessHttpTest.java:15）——证据：已修复（SecurityConfig.java:78 注释改「API 文档面：prod 收紧为需认证（C11）」并注明 /actuator/** 无依赖端点已由 anyRequest 需认证；DocsAccessHttpTest.java:15 类注释同步），DocsAccessHttpTest 定向绿 + 全量 473 绿；commit e9eba1e（SecurityConfig.java:78、DocsAccessHttpTest.java:15）。

- [x] 审核发现：上轮登记后出现一批有补丁的新公告未清——app：fast-uri 3.1.8（moderate，补丁 3.1.9）、qs ≤6.16.0（moderate，补丁 6.16.1，原两精确钉 6.16.0 已入公告范围）、glob 10.5.0（moderate，补丁 14.0.2）、minimatch 7.4.9/9.0.x（moderate，补丁 ≥11.0.1）；web：hono 4.13.13 + @hono/node-server 2.1.3（moderate ×5，补丁 4.13.16/2.1.6）——证据：已修复（commit 16947ae）：app 范围 override `fast-uri@<3.1.9: 3.1.9`、`qs@<6.16.1: 6.16.1`（取代 qs@6.5.5/qs@6.14.2 两精确钉）、`glob@10.2.6: 14.0.2`（原生配对 minimatch ^11，plugin-doctor 仅 taro doctor 路径加载）、`minimatch@>=7.0.0 <11.0.1: 11.0.1`（消费方逐一核验：typescript-estree 命名导入兼容 v11、npm-check>depcheck 为 @tarojs/cli dist 从不 require 的死依赖）；web override `hono@<4.13.16: 4.13.16` + `@hono/node-server@<2.1.6: 2.1.6`。audit：app 8→1（仅余 NO-PATCH request）、web 11→3（仅余 react-router ×2 维持已代决「仅 7.18.0 修复为 major 跳版」+ elliptic NO-PATCH）。门禁：app test 2/2、tsc 0 错、weapp+h5 build 绿；web jest 10 套件 38 例、tsc/lint 0 错、build 绿；docs check+build 绿；双语 deps-audit 登记表同步 7 行。

## 漏洞扫描遗留（2026-11-03 全舰队扫描，源 scratch/vuln-scan-*.md）

- [x] VULN-SINOMED-001 [高危·命令注入·已解除] git-clone ≤0.2.0（app，@tarojs/cli>download-git-repo 链）：链路已替换——证据：与 VULN-003 同一个 download-git-repo shim 方案，app/pnpm-workspace.yaml override `download-git-repo: link:./scripts/noop-download-git-repo`（零依赖 shim，唯一消费者 `@tarojs/cli/dist/create/fetchTemplate.js` 仅在未使用的 `taro init/create` 远程模板路径加载，调用即显式报错不静默），重锁后 `app/pnpm-lock.yaml` 零 git-clone / 零 decompress，`pnpm audit --audit-level=high` 13→8（仅余 2 low/6 moderate，high/critical 全清）；`pnpm test` 2 例绿、tsc 0 错、`pnpm build:weapp` 与 `build:h5` exit 0；双语 deps-audit 登记表同步标记 Cleared，commit 903cfe6。
  - 复核（2026-11-04）：pnpm why 仍为 @tarojs/cli@3.6.40>download-git-repo@2.0.0>git-clone@0.1.0；npm view git-clone 最新 0.2.0、2022-06 修改。
  - 复核（2026-11-06）：pnpm why 仍为 @tarojs/cli@3.6.40>download-git-repo@2.0.0>git-clone@0.1.0；npm view git-clone 最新仍 0.2.0（2021-09 发布），无更高 patched；app pnpm audit --audit-level=high 仍命中 git-clone。
  - 复核（2026-11-12）：npm view git-clone versions 最高仍 0.2.0（time.modified 2022-06-18），无更高 patched；npm view download-git-repo 全版本（2.0.0/3.0.0/3.0.1/3.0.2）均声明 `git-clone: ^0.1.0` + `download: ^7.1.0` → 漏洞叶点结构性内嵌于替换线，无法靠升级 download-git-repo 消除，只能链路替换。可达性核实：唯一加载点 @tarojs/cli dist/create/fetchTemplate.js:15（经 dist/index.js:4-6 → create/project.js:23 → fetchTemplate.js），即 `taro init`/`taro create` 脚手架路径；本仓 app/package.json scripts 仅 `taro build --type X` 与 `node --test`，CI（frontend.yml:82/89/92/118）只跑 pnpm test/build:weapp/build:h5，全仓（excl node_modules/lock）无 `taro init`/`download-git-repo`/`git-clone` 引用 → 当前构建/测试/CI 路径不加载该链。
  - 复核（2026-11-21）：npm view git-clone versions 最高仍 0.2.0（time.modified 2022-06-18），无更高 patched。链锁复核：rebase 后（HEAD dc8abfb，本轮合入 #24/#25/#26/#27 四个 dependabot）app/pnpm-lock 仍 @tarojs/cli@3.6.40>download-git-repo@2.0.0>git-clone@0.1.0，PR#27（app-minor-patch）只动 @babel/runtime/postcss 两版本，未触该链；app pnpm audit --audit-level=high 仍命中 git-clone（high，Command injection）。可达性无变化：scripts/CI 仍无 `taro init`/`download-git-repo`/`git-clone` 引用。
- [x] VULN-SINOMED-002 [高危 REDoS] html-minifier ≤4.0.0（app 终扫残留）：换 html-minifier-terser；验收：替换后 pnpm audit 中 html-minifier 不再命中；audit-only。证据：app/pnpm-workspace.yaml:35 `html-minifier: npm:html-minifier-terser@^6.1.0` override + app/pnpm-lock.yaml:39/9140 html-minifier→html-minifier-terser@6.1.0；pnpm audit 无 html-minifier（余 17 项全为既有其它漏洞）；build:h5 绿 50.05s + build:weapp 绿 36.18s + tsc --noEmit --skipLibCheck 0 错。commit 见下。
- [x] VULN-SINOMED-003 [严重·路径穿越·已解除] decompress ≤4.2.1 ×2（app，@tarojs/cli>download-git-repo>download 链）：上游无补丁但整链已替换——证据：同 VULN-001 的 download-git-repo 本地零依赖 shim override，`taro init/create` 远程模板路径不常用且调用即显式报错；`app/pnpm-lock.yaml` 零 decompress，`pnpm audit --audit-level=high` 13→8（critical 2 全清）；门禁同 VULN-001（test 2/2、tsc 0 错、weapp+h5 build 绿）；双语 deps-audit 登记表同步标记 Cleared，commit 903cfe6。
  - 证据（2026-11-04 复核）：pnpm audit 仍命中 decompress ×2（@tarojs/cli>download-git-repo>download>decompress）；npm view decompress 最新仍 4.2.1、@xhmikosr/decompress 最新 11.1.4（type:module）；npm view @xhmikosr/download 404 无同级替换线。
  - 复核（2026-11-04 二次）：同日重查结论不变，pnpm audit 仍命中 decompress；@xhmikosr/download 仍 404。
  - 复核（2026-11-06）：app pnpm audit --audit-level=high 仍命中 decompress（.>@tarojs/cli>download-git-repo>download>decompress）；npm view decompress 最新仍 4.2.1、@xhmikosr/download 仍 404，替换线仍不可用。
  - 复核（2026-11-12）：npm view decompress 最新仍 4.2.1（2023-03-04）；**download 最新已升 8.0.0，但其 dependencies 仍声明 `decompress: ^4.2.1`** → 升级 download 同样无法消除漏洞，替换 line-of-work 只能整链替换；@xhmikosr/decompress 仍 11.1.4（ESM-only）、@xhmikosr/download 仍 404。可达性：与 VULN-SINOMED-001 同链（download-git-repo>download>decompress），经同一 `taro init` 脚手架路径加载，当前构建/测试/CI 路径不加载（见 VULN-SINOMED-001 复核 2026-11-12）。
  - 复核（2026-11-21）：npm view decompress 最新仍 4.2.1（2023-03-04），@xhmikosr/decompress 仍 11.1.4 ESM-only（替换线 CJS 不兼容），@xhmikosr/download 仍 404（无同级替换线）；npm view download@8.0.0 dependencies 仍声明 `decompress: ^4.2.1` → 升级 download 无法消除；app pnpm audit --audit-level=high 仍命中 decompress（critical，symlink path traversal）。可达性同 VULN-SINOMED-001 复核（同一 taro 脚手架链，构建/测试/CI 不加载）；PR#27 未触该链。
- [x] VULN-SINOMED-004 [高危·原型污染] swiper 6.8.0→12.1.2【跨大版本需改代码（跨 6 个 major）】：@tarojs/components 依赖（app 终扫残留）；验收：pnpm audit --audit-level=high 中 swiper 不再命中；该仓无 test 脚本，audit-only。证据：app/pnpm-workspace.yaml:32 `swiper: 12.1.2` override + app/package.json swiper 显式依赖 12.1.2 + app/config/index.ts:79-84 h5.webpackChain alias 指 scripts/swiper-shim/index.mjs 补 $wrapperEl/$el 兼容；app/pnpm-lock.yaml swiper@12.1.2；pnpm audit --audit-level=high 无 swiper（22 项全为既有其它漏洞）；build:weapp 绿 48.59s + build:h5 绿 143s + tsc --noEmit --skipLibCheck 0 错。commit 见下。
- [x] VULN-SINOMED-005 [高危 symlink 路径穿越·已解除] extract-zip ≤2.0.1 ×2（web，@ant-design/pro-cli>carlo>puppeteer-core 链）：上游无补丁，但唯一链路已被移除——证据：删除 web `@ant-design/pro-cli` devDependency 与已崩溃的 `i18n-remove` script（实测 `Failed to load config "prettier"`，umi max 4 无原生 i18n-remove，无可用能力损失），重锁后 `web/pnpm-lock.yaml` 零 extract-zip、`pnpm audit --audit-level=high` 14→11（仅余 2 low/9 moderate，无 high）、`pnpm test` 10 套件 38 例全绿、`pnpm tsc` 0 错、`pnpm lint:js` 0 错、`pnpm build` exit 0；双语 deps-audit 登记表同步标记 Cleared，commit 6dae008。
  - 复核（2026-11-04）：pnpm why 仍为 @ant-design/pro-cli@2.1.5>carlo@0.9.46>puppeteer-core@1.12.2>extract-zip@1.7.0；npm view extract-zip 最新 2.0.1、2023-03 修改。
  - 复核（2026-11-06）：web pnpm audit --audit-level=high 仍命中 extract-zip（.>@ant-design/pro-cli>carlo>puppeteer-core>extract-zip）；npm view extract-zip 最新仍 2.0.1（2023-03 修改），无更高 patched。
  - 复核（2026-11-12）：npm view extract-zip 最新仍 2.0.1（2023-03-04），无更高 patched。可达性核实：web/package.json 中唯一 pro-cli 入口为 scripts.i18n-remove=`pro i18n-remove --locale=zh-CN --write`（bin `pro`→./cli.js，其余 scripts 均走 max/jest/umi-serve 等），grep @ant-design/pro-cli/lib 对 carlo/puppeteer **零引用** → `pro i18n-remove` 不加载 carlo→puppeteer-core→extract-zip 链；CI（frontend.yml）不运行 pro-cli。该链被装填但未被本仓任何命令/CI 步骤加载。
  - 复核（2026-11-21）：npm view extract-zip 最新仍 2.0.1（2020-06 发布），无更高 patched。链锁复核：web/pnpm-lock 仍 @ant-design/pro-cli@2.1.5>carlo@0.9.46>puppeteer-core@1.12.2>extract-zip@1.7.0 ×2，PR#25（web-minor-patch）只动 @umijs/lint/max 4.7.32→4.7.34，未触该链；web pnpm audit --audit-level=high 仍命中 extract-zip（high，unvalidated symlink path traversal ×2）。可达性无变化：pro-cli 唯一入口 `pro i18n-remove` 不加载 carlo 链，CI 不运行 pro-cli。
  - 复核（2026-11-21 二次）：**阻塞性质收窄为「能力移除决策」，非「上游无补丁」**。唯一无补丁的出路是删 `@ant-design/pro-cli` devDependency（链随之消失、audit 干净），但：① `web/src/locales/zh-CN` 仍在（连同 bn-BD/en-US/fa-IR/id-ID/ja-JP/pt-BR/zh-TW 共 8 语言），`i18n-remove --locale=zh-CN --write` 的活儿**未完成** → pro-cli 非死重，删它=移除一个有 pending 任务的 locale 维护能力；② pro-cli@2.1.5 为 umi-3 时代工具（`npm view` 声明 `umi-utils: ^1.5.1`、`@babel/core: ^7.5.4`、`eslint: ^7.1.0`、`typescript: ^4.0.3`），而 web 用 `@umijs/max@4.7.34` → pro-cli 2.x 对 max 4 大概率已不兼容，该 script 或已名存实亡。结论：需用户拍板 ——（a）保留 pro-cli、接受 audit 命中（链不被本仓加载，风险仅"装填未加载"）；或（b）删 pro-cli 并明确 zh-CN 移除改走 max 4 自身方案（`max setup`/`max i18n-remove` 是否提供，另行验证），此时 i18n-remove script 一并删。不宜由 agent 单方移除 bilingual 应用的 locale 能力。
  - 复核（2026-11-21 三次，**实证 pro-cli 能力已失效**）：在 web 内直接空跑 `node node_modules/@ant-design/pro-cli/cli.js i18n-remove --locale=zh-CN`（**不带 `--write`** —— 已读 `src/i18n/index.js:62-64`，`if (write) { fs.writeFileSync(...) }`，即未传 `--write` 时纯读不写、本空跑零改动），结果**崩溃**：`Error: Failed to load config "prettier" to extend from.`，栈在 `@eslint/eslintrc@0.4.3`（pro-cli 内部 eslint@7.1.0 链，config-array-factory 加载 BaseConfig 的 extends 时找不到 prettier 配置）→ 未到扫描/写 zh-CN 阶段即终止。即：**选项 (a)「保留 pro-cli」所保的 i18n-remove 能力在本仓已不可用**（崩溃，非仅"装填未加载"），保留只继续背 extract-zip 的 audit 命中、且换不来任何可用能力 —— 实证把 (a) 的价值压到零，倾向 (b)（删 pro-cli，zh-CN 移除改 max 4 原生方案），但删与保留仍属能力移除决策，**待用户拍板**，agent 不单方删。附：**max 4 亦无原生 i18n-remove** —— `node node_modules/@umijs/max/bin/max.js --help` 命令清单（build/config/dev/setup/lint/deadcode/plugin/preview/mfsu/run/dva/generate 等）**无 i18n/locale 相关命令**，即 (b) 删除后该能力无同款替代、zh-CN 移除需另走手工/脚本（删除 `web/src/locales/zh-CN` + 清 `config` 引用），能力本身本就已崩溃、无可失 —— (a) 零价值与 (b) 可行性同时被实证。

## 审核发现（2026-11-04，git diff 0b2edc1..12be529 只审不改）

- [x] 审核发现：swiper 6→12 升级后 v12 容器修饰类前缀由 swiper-container- 改为 swiper-（app/node_modules/swiper/shared/swiper-core.mjs:3251 containerModifierClass:'swiper-'），而 @tarojs/components 内联的是 Swiper 6.8.0 CSS（dist/collection/components/swiper/style/index.css，基于 .swiper-container-* 修饰类），.swiper-container-vertical>.swiper-wrapper{flex-direction:column} 与 .swiper-container-horizontal>.swiper-pagination-bullets{bottom:10px;left:0;width:100%} 等规则因类名不匹配而失效，纵向 swiper 布局与分页圆点定位将回归；shim 兼容层只桥接了 $wrapperEl/$el 两个 JS 属性、未覆盖 CSS 类名变更，属不完整兼容（app/scripts/swiper-shim/index.mjs:1 与 app/config/index.ts:79-84）。——证据：已修复（app/scripts/swiper-shim/index.mjs 构造 super 前把 containerModifierClass 回退为 v6 的 'swiper-container-'，使 v12 实际加到容器的修饰类与 Taro 内联的 Swiper 6.8.0 CSS 选择器一致），app/test/swiper-shim.test.mjs 新增 shim emits swiper-container-* modifier classes + swiper-container-vertical 两例定向绿（jsdom 运行时断言 swiper-container-horizontal/vertical/initialized 类与分页圆点渲染）；build:weapp 绿 + build:h5 绿 + tsc --noEmit --skipLibCheck 0 错；commit 1d43487。
- [x] 审核发现：swiper 6.8.0→12.1.2 跨 6 个 major 的升级（含容器类名变更、$wrapperEl/$el 由 jQuery 风格改为原生 el/wrapperEl、.esm.js 改 .mjs）在 VULN-SINOMED-004 证据中仅验证 build 绿+audit 绿+tsc 0 错，无任何运行时 DOM/视觉回归测试（app 仓无 test 脚本），纵向布局、分页圆点、circular 循环、autoplay、zoom 等运行时行为均未覆盖，兼容缺口无法被 CI 拦截（app/pnpm-workspace.yaml:36）。——证据：已补（app/package.json 增 test 脚本 `node --test test/*.test.mjs`，新增 app/test/swiper-shim.test.mjs 以 jsdom 做运行时 DOM 断言：容器修饰类 swiper-container-* 与分页圆点渲染；.github/workflows/frontend.yml app job 增 Runtime test 步骤，兼容缺口自此可被 CI 拦截），pnpm test 2 例绿；commit 1d43487。
- [x] 审核发现：todo.md 本轮新增的两条「已修复」记录引用 commit f9d59e9，但当前 HEAD 实为 1d43487（两者同父 12be529a、同名提交，f9d59e9 已被 amend 成孤儿、`git branch --contains f9d59e9` 为空），提交引用指向已废弃哈希、与实际历史不符，后续追踪会被误导（todo.md:199-200）。——证据：已修正（todo.md:199-200 引用 f9d59e9→1d43487，git show 确认 1d43487 为当前 HEAD、同父 12be529a 的同名重写提交，f9d59e9 仅剩孤儿对象；`git branch -a --contains f9d59e9` 空），`git log --oneline -1` = 1d43487。
- [x] 审核发现：本 diff 为 app 新增 test 脚本（`node --test test/*.test.mjs`）并在 CI 增 Runtime test 步骤，但 .github/workflows/frontend.yml:4 头部注释仍写「app 无测试脚本，以多端构建通过为准」、docs/guide/development.md:44 仍写「no test script yet」，口径未随本次改动同步（改动未更新既有注释/文档）。——证据：已同步（frontend.yml:4 改「app 跑 node --test 运行时测试（swiper shim 兼容），再以多端构建通过为准，另加类型检查」；docs/guide/development.md Mini program 行改 `pnpm test` runs `node --test test/*.test.mjs`），口径与新增 Runtime test 步骤一致。
- [x] 审核发现：app/package.json:6-8 engines 声明 node >=24.0.0，而本轮新增的 jsdom@30.1.3 引擎要求为 `^22.22.2 || ^24.15.0 || >=26.0.0`（app/pnpm-lock.yaml:4723），node 24.0–24.14 区间存在引擎不匹配空隙，engines 下限未随新依赖收紧；当前 CI/本地用 24.21 恰好满足，实际未触发，属潜在边界（app/package.json:6-8）。——证据：已收紧（app/package.json engines.node 改 >=24.15.0，与 jsdom@30.1.3 的 ^24.15.0 下界对齐，消除 24.0–24.14 空隙），app/pnpm test 2 例仍绿（swiper shim 运行时断言）。
- [x] 审核发现：Node 引擎范围 `>=24.15.0` 仍包含 Node 25，但 jsdom 30.1.3 的引擎范围排除了整个 Node 25，声明支持的环境与测试依赖要求不一致（app/package.json:7）。——证据：已修复（app/package.json:7 engines.node 改 `>=24.15.0 <25.0.0 || >=26.0.0`，与 jsdom@30.1.3 的 `^22.22.2 || ^24.15.0 || >=26.0.0` 在 app 承诺区间对齐，排除整个 Node 25），app/pnpm test 2 例仍绿（swiper shim 运行时断言）。
- [x] 审核发现：英文开发指南已补充小程序 `pnpm test`，中文对应页仍称暂无测试脚本，中文读者会遗漏运行时测试（docs/guide/development.md:44）。——证据：已同步（docs/zh/guide/development.md:44 改「`pnpm test` 跑 `node --test test/*.test.mjs`（swiper shim 运行时 DOM 断言）；多端构建通过为准；类型检查另跑 `pnpm exec tsc --noEmit --skipLibCheck`」），与英文页 Mini program 行口径一致。

- [x] 审核发现：本轮 diff 未见问题（31e2aff3..HEAD）（属实：独立审阅 31e2aff3..b58a90f 完整 diff 仅含 app/package.json engines 收紧、docs/.vitepress/theme/custom.css 排版新增、docs/zh/guide/development.md 测试口径同步与 todo.md 勾选；与 5c360af 的已验结论一致，无引入问题；证据：docs pnpm check + build 绿）。
- [x] 审核发现：代码块的 `code` 保留 VitePress 默认 `width: fit-content`，长 URL 仍横向溢出（docs/.vitepress/theme/custom.css:230）。 属实：`word-break: break-word`（= overflow-wrap: break-word）不影响 min-content，fit-content 钳制取 min-content 时单 token 仍超容器；已修：custom.css 对 `pre code` 追加 `overflow-wrap: anywhere`（影响 min-content，token 换行），vitepress vp-doc.css:312-337 为默认值出处；证据：docs pnpm build 绿。

- [x] 审核发现：本轮文档一致性审计将描述不一致计为 0，但中英文开发指南仍将 Spring Boot 当前基线写为 4.1.1，而 server/pom.xml 已是 4.1.2（docs/审计-文档一致性.md:174）。——证据：属实（server/pom.xml:8 = 4.1.2，HEAD 时 docs/guide/development.md:68 与 docs/zh/guide/development.md:68 均写 4.1.1）；已修（dev 指南两处改 4.1.2；复扫再清同源基线漂移：server/README.md:11 与 docs/审计-文档一致性.md:74（zh:74）版本链均改 4.1.2）；验证：docs pnpm check + build 绿，git grep 当前态无残余 4.1.1 基线。

- [x] 审核发现：本轮 diff 未见问题（c45b7773..HEAD）。审核通过，无需改动

- [x] 审核发现：本轮 diff 未见问题（efe7cb5..HEAD）（属实：独立审阅 efe7cb5..HEAD 完整 diff 仅含 commit e51fcb1 的 todo.md 公众号凭据复核行（2026-11-06），全部事实断言复核属实——application.yml:57-58 仍 false/空、compose.yml/server/docker-compose.yml 均无 WX_OA_* 注入、uploadRemoteMaterial→cgi-bin/material/add_material 已实现、公众号 7 个定向测试类合计 47 tests 0 failures；无引入问题；证据：server ./mvnw -B test 473 绿（0 failures/0 errors/0 skipped）、docs pnpm check + build 绿）。
- [x] 审核发现：本轮 diff 未见问题（e51fcb1f..HEAD）（属实：独立审阅 e51fcb1f..HEAD 完整 diff 仅含 commit 6b89de1 的 todo.md 纯文书追加（2 行审核记录），无任何代码/配置/文档内容改动，无引入问题；证据：git diff e51fcb1f..HEAD --stat 仅 todo.md | 2 ++，当日 HEAD sha=6b89de10e82aa5db5cf59df505e5203e8cb34915）。

- [x] 审核发现：swiper 声明升级至 12.2.0，但 pnpm 覆盖规则仍锁定 12.1.2，实际安装及构建未使用新版本（app/package.json:60）——证据：已修复（app/pnpm-workspace.yaml:36 `swiper: 12.1.2` → `12.2.0` 与声明对齐；lockfile 重解析 `swiper@12.2.0`、旧版残留 0；node_modules 实装 12.2.0；`pnpm build:weapp` 编译成功 2.62m exit 0）。
- [x] 审核发现：webpack 声明升级至 5.112.2，但 pnpm 覆盖规则仍锁定 5.104.1，实际安装及构建未使用新版本（app/package.json:81）——证据：已修复（5.112.2 与 Taro 3.6.40 不兼容——`@tarojs/webpack5-runner` 引用私有路径 `webpack/lib/dependencies/EntryDependency`，实测 5.112.2 该模块被移除、`pnpm build:weapp` 报 `Cannot find module`，而 5.104.1 存在该模块；故声明回退 `app/package.json:81` `5.112.2` → `5.104.1` 与 override 对齐，declared=override=installed=构建可用；`pnpm build:weapp` 编译成功 2.62m exit 0）。

## 源码分层分析 2026-11-07

基于 HEAD `1f10eef0234f37cd81bd5b11b35d8c2c93b4bc99`。按职责归纳 11 层：接入编排、资源控制器、应用服务、持久化、业务字典、临床运营、财务结算、渠道集成、内容与外设、交互运行时、平台生命周期。完整地图与互链见 `docs/source-layer-analysis.md` 与 `docs/zh/source-layer-analysis.md`，术语见 `docs/glossary.md` 与 `docs/zh/glossary.md`。

- [x] LAY-001 [P1] 拆分 server/src/main/java/com/sinomed/service/WxOfficialAccountService.java（1587 行）——回调验签/加密、账号与菜单、消息日志、OAuth、素材文件和出网上传混在一个服务（file:line server/src/main/java/com/sinomed/service/WxOfficialAccountService.java:215）——证据：已拆分（commit dbcb8f2）。1587 行 → 门面 WxOfficialAccountService 186 行（仅留 Controller 公开签名委托）+ 8 个按关注域子服务：WxCallbackService 344（回调/验签/加密/路由/粉丝维护/自动回复匹配）、WxFanBindService 212（取码/绑定/解绑/审计）、WxOauthService 216（网页授权/ticket）、WxMessageLogService 127（流水落库/分页）、WxAutoReplyAdminService 129（规则 CRUD）、WxMenuAdminService 295（菜单本地+推送微信）、WxMaterialAdminService 394（素材台账/本地/远端上传）、WxAccountAdminService 128（能力矩阵/账号保存）+ WxAccountTokenResolver 26（env 优先 token 解析，拆独立组件避免回调/OAuth 各持一份）；依赖图无环（Materials←Callback；Bind←OAuth；Callback←OAuth+AccountAdmin）；行为不变——Wx 26 测试类 130 条定向绿 + 全量 473 条（0 失败 0 错）绿，`./mvnw test` BUILD SUCCESS 26:59。
- [x] LAY-002 [P1] 拆分 server/src/main/java/com/sinomed/seed/DemoSeedRunner.java（1230 行）——一个 ApplicationRunner 直接播种 20+ 张业务表，领域数据构造与生命周期开关纠缠（file:line server/src/main/java/com/sinomed/seed/DemoSeedRunner.java:91）。——证据：已拆分（commit e8b1bba）。1230 行 → 编排器 DemoSeedRunner 72 行（唯一保留 @ConditionalOnProperty(demo.seed=true)+ApplicationRunner，run() 内 23 步播种调用顺序与原版逐行一致，失败仅告警不阻断启动）+ 6 个领域种子器（无条件 @Component，未启用时不被编排器调用即不播种）+ SeedSupport 85（backdateRow JDBC UPDATE 解决 @CreatedDate 覆盖显式时间、staffByAccount/itemByName/at/daysAgo 公共构造）：Catalog 209（药材/模板/方剂/穴位）、Clinic 202（员工/项目/班次/药材库存/治疗模板）、Customer 243（顾客/治疗/病史）、DailyOps 163（评价/签到/预约）、Billing 270（订单/卡/结算/退款）、Pharmacy 154（处方/发药）；行为不变——全量 473 条（0 失败 0 错）绿，./mvnw test BUILD SUCCESS。
- [x] LAY-003 [P1] 拆分 web/src/pages/Wx/Index/index.tsx（1352 行）——账号、菜单、自动回复、素材、消息日志和授权管理都是内聚页面组件/状态机，却耦合在一文件（file:line web/src/pages/Wx/Index/index.tsx:130）。——证据：已拆分（commit 00f97da）。1352 行 → 组合页 index.tsx 32 行（PageContainer+Tabs，页头说明原文保留，tab 顺序 accounts/rules/menus/logs/materials 与原版一致）+ 5 个默认导出 tab：AccountsTab 241（账号配置，useModel isAdmin 门控，密钥留空=保持）、RulesTab 300（自动回复规则，单启用类型禁用拦截）、MenusTab 446（菜单树/能力矩阵门控/推送微信，menuNameWeight 与服务端同式校验）、MessageLogsTab 144（消息流水 ProTable+行内管理端解绑）、MaterialsTab 145（素材台账+blob 预览 Modal）+ constants 100（标签/枚举/timeRangeColumn 统一口径）+ useAccountOptions 19（账号下拉/行内账号名）；原注释逐字保留；config 路由（config/routes.ts:309 './Wx/Index'）下同目录新文件不生成新路由；lint（eslint+prettier+tsc）全绿。
- [x] LAY-004 [P1] 拆分 web/src/pages/Prescription/Create/index.tsx（1020 行）——顾客定位、药味表、安全审方、定价、模板/方剂带入和提交状态共用一个组件作用域（file:line web/src/pages/Prescription/Create/index.tsx:69）。——证据：已拆分（commit 933845e）。1020 行 → index.tsx 410 行（状态装配/处方笺头/辨证摘要/模板方剂两个 Select/提交与存模板逻辑/签名行/页脚按钮）+ 按关注域拆出的模块：usePrescriptionChecks 167（配伍/过敏/剂量/计价四组 500ms 防抖+序号防竞态 effect，提示不拦截）、usePrescriptionImports 104（模板/方剂带入，只填当前开方单不写回）、useCustomerLookup 45（手机号定位+接诊单联动）、RxMetaForm 202（类型/代煎/领取/医师三签/用法三格 meta 状态机）、CheckPanels 138（审方展示区）、HerbRows 104（药味表+饮片目录检索）、PricingPanel 53（三行账）、SaveTemplateModal 36、CustomerPanel 40、helpers 43（LEVEL_TEXT/fenToYuan/maskPhone/SPECIAL_FX/拼音首字母缓存）；原注释与文案逐字保留；lint（eslint+prettier+tsc）全绿。
- [x] 目录：`server/src/main/java/com/sinomed/service` 与 `service/impl` 并存时，独立领域服务多在 `impl`，而 `WxOfficialAccountService` 这类跨渠道复合服务在根目录，复合边界与普通 CRUD 边界未区分（file:line server/src/main/java/com/sinomed/service/WxOfficialAccountService.java:90）。——证据：已区分（commit dbcb8f2，LAY-001 拆分落地后边界自然成立）。根目录 `service/` 现为「接口（配 impl 实现）+ 公众号渠道运行时具体 bean」两层：WxOfficialAccountService 是 186 行薄门面（javadoc 明示 8 个子服务与扩展去处「新增公众号能力直接挂对应子服务」），WxCallbackService/WxFanBindService/WxOauthService/WxAccountTokenResolver 为渠道运行时 @Component；`impl/` 为普通 CRUD（34 个 *Impl）+ 公众号管理端 5 个 *Admin*/流水服务。复合=门面委托、CRUD=接口+Impl，两类边界可从包位置与命名直接辨认。
- [x] 目录：`web/src/pages/Wx/Index/index.tsx` 把至少五个公众号管理面板合并到同一页面文件，与其余每任务一目录的 web 分类口径不一致（file:line web/src/pages/Wx/Index/index.tsx:418）。——证据：已拆分（commit 00f97da，LAY-003 落地）。web/src/pages/Wx/Index/ 现为每面板一文件：AccountsTab/RulesTab/MenusTab/MessageLogsTab/MaterialsTab 五个默认导出 tab + constants + useAccountOptions，index.tsx 32 行组合页（PageContainer+Tabs），与其余页面一任务一目录口径一致。

## 审核发现

- [x] 审核发现：`minimatch@<11.0.1: '>=11.0.1'` 强制把声明 `^3` 的 `recursive-readdir` 和 `test-exclude` 的传递依赖提升到 11.0.5，而 v11 主版本要求 CJS 命名导入 `const { minimatch } = require(...)` 且引擎要求 Node `20 || >=22`；这两个消费方仍执行 CJS 默认导入并把结果当构造器/命名空间使用（`new minimatch.Minimatch(...)`、`minimatch(...)`、`minimatch.filter(...)`），在支持范围内会因 default export 不存在而运行时失败，同时把 `react-dev-inspector` 生产依赖经 `recursive-readdir` 的 `^3.0.5` 依赖越权升级（file:line web/pnpm-workspace.yaml:5）。——证据：属实（两处细节修正），已修复（commit b983316）。核实：原 `minimatch@<11.0.1: '>=11.0.1'` 确把 recursive-readdir@2.2.3 / test-exclude@6.0.0（声明 ^3.0.5 / ^3.0.4）越权提到 11.0.5；v11 CJS 仅命名导出（无 default、模块不可调用，node -e 实测）。运行时实测：test-exclude 在 11.0.5 下 `TypeError: minimatch is not a function`（index.js:99 / is-outside-dir-win32.js:9 把模块当函数调用）——属实；recursive-readdir 在 11.0.5 下实测正常（`new minimatch.Minimatch` 命中 v11 顶层命名导出），「该用法会失败」不属实；两包均无 `minimatch.filter` 用法，该细节不属实；engines `20||>=22` 在本地 node 24.21 与 CI node 24（frontend.yml 等）均满足。修复：override 收窄为 `minimatch@^9.0.4: '>=11.0.1'`（唯一命中 glob@10.5.0 的 ^9.0.4，其源码为命名导入 `import { Minimatch } from 'minimatch'`，该导入形态兼容 v11，但配对后 glob 的文件匹配实测已坏，见 G2 更正）；`*` 的 @types/minimatch 类型桩自然解析 11.0.5；声明 ^3 的消费方全部回落声明范围（recursive-readdir/test-exclude=3.1.5，@humanwhocodes/config-array、glob@7.2.3、readdirp@3.6.0 同），react-dev-inspector 生产链不再被越权。验证：`pnpm install --frozen-lockfile` 一致；两消费方 node 运行时实测 OK；`pnpm build` 绿（Compiled successfully 2.59m）；jest 7 套件 24 条全绿；`pnpm audit` 9 条、minimatch 0 命中。
- [x] 审核发现：本轮 diff 未见问题（7b8518a6..HEAD）。——证据：属实（2026-11-10）。git diff 7b8518a6..HEAD 仅含 minimatch override 收窄（b983316）+ 其证据勾选（63d6a7b），无其他代码改动；`pnpm install --frozen-lockfile` 一致（Done 577ms）；minimatch 解析：glob@10.5.0（声明 ^9.0.4、命名导入兼容 v11）与 @types/minimatch@6.0.0（`*` 类型桩、无运行时代码）→ 11.0.5，glob@7.2.3 / recursive-readdir@2.2.3 / test-exclude@6.0.0 等声明 ^3 → 全回落 3.1.5；无越权残留、无新问题。
  - 【2026-11-14 G2 更正】上面「命名导入兼容 v11」「无新问题」结论有误，已证伪：收窄后 web 的 glob@10.5.0 仍配对 minimatch 11.0.5，实跑 `globSync('src/**/*.test.{ts,tsx,js,jsx}')` 返回 **0**（盘上实有 7 个测试文件：src/requestErrorConfig.test.ts、src/services/ant-design-pro/{wx,billing,appointment,tokens,order,prescription}.test.ts），对照 app 的 glob@10.5.0＋minimatch@9 配对同法命中真实文件（test/*.test.mjs 1/1）——即 glob@10 的匹配在 minimatch 11 下静默漏项（glob 内以 `instanceof RegExp` 判匹配片段，v11 返回 Matcher 实例）；影响面为 rimraf 等经 glob 的通配清理（现行 build/jest 门禁不走该路径，故仍绿）。修复未做，按 Codex 深审第 1 条挂起待修（恢复 glob 支持的 minimatch 主版本或整链升级＋真实文件匹配断言）。
  - 【2026-11-15 已修复】挂起解除，见下方「Codex 深审发现」第 1 条的修复证据（commit 46075e7）。

## Codex 深审发现（2026-11-13，luna profile 近5 commit 审核，已收口：6 条均带修复证据 commit 46075e7 / 34f1611 / f1f90c7 / 9106a3a / 771244c，均已核验在 git 历史中）

- [Major] minimatch 修复仍遗留跨版本兼容错误 | 位置 `web/pnpm-workspace.yaml:5`、`web/pnpm-lock.yaml:15458` | 问题：b983316 保留了 `minimatch@^9.0.4: '>=11.0.1'`，使 glob@10.5.0 使用 v11；glob 依赖 `p instanceof RegExp`，v11 的匹配片段却是 `Matcher`。实跑同一模式 `src/**/*.test.ts`：app 的 glob@10.5.0＋minimatch@9.0.9 返回 7 个文件，web 的组合返回 0 个 | 影响：通配文件发现静默漏项，依赖该链的 rimraf 通配清理也会受影响；todo.md:245–246 的“兼容 v11／无越权残留”结论错误，修复未新增回归测试 | 修复方向：恢复 glob 支持的 minimatch 主版本，或整体升级兼容的消费链，并增加真实文件匹配断言。——证据：已修复（commit 46075e7）。
  - 根因链：`@umijs/bundler-mako>rimraf@5.0.1` 声明 `glob ^10.2.5`，glob@10.5.0 声明 `minimatch ^9.0.4`；b983316 的收窄 override `minimatch@^9.0.4: '>=11.0.1'` 恰把这一对改成 glob@10 + minimatch@11，而 glob@10 以 `p instanceof RegExp` 判匹配片段、minimatch@11 返回 `Matcher` 实例 → `globSync` 静默返回 0。
  - 为何不能只删 override：删除后 minimatch 回落 9.0.9，`pnpm audit` 立刻命中 moderate「minimatch has potential denial of service via globparts amplification」(`>=7.0.0 <11.0.1`)——这正是当年加 override 的动机（audit 从 16 升至 17）。
  - 修复：`web/pnpm-workspace.yaml` 删 minimatch override、改 `glob@^10.2.5: 14.0.6`。glob@14.0.6 自带 `minimatch ^11.0.5`（官方配对、且 ≥11.0.1），一并解除 glob 自己的 moderate 公告（`>=9.0.0 <=14.0.1`）→ 一条 override 清两条公告；审计 17 → **15**（余 15 条为既有，含 2 high extract-zip 已 BLOCKED）。
  - `web/package.json` 增 devDependency `glob ^14.0.6`：pnpm 隔离布局下 root 无法 require 传递依赖，直接声明才能做真实文件匹配断言（与 lockfile 同一 14.0.6，不新增第二份）。
  - 回归测试 `web/src/globTooling.test.ts`：`globSync('src/**/*.test.{ts,tsx,js,jsx}')` 必须命中真实文件且含 `requestErrorConfig.test.ts` 与本文件自身；不匹配模式返回 `[]`。
  - **变异验证**（证明该测试真能在错配下变红）：`/tmp/globprobe` 用 npm overrides 强制 glob@10.5.0 + minimatch@11.0.5，同一模式 `globSync` 返回 **0**（修复后同模式返回 **8**，含 globTooling 自身）——缺陷复现与修复效果双向实测。
  - rimraf@5.0.1 调用面兼容：其源码为 `glob_1.glob` / `glob_1.globSync`（dist/cjs/src/index.js:9,37,49），glob@14 同名导出；实测 `rimraf('smoke/a/drop-*.txt', { glob: true })` 在 override 下真实删除 drop-1/drop-2 并保留 keep.txt。
  - 门禁：`pnpm install` 重锁（lockfile 内 glob 单一 14.0.6，glob@9/10 全无；glob@7.2.3 仅留于 rimraf@2/3 旧链、不在公告范围）；`pnpm jest` 8 套件 26 例绿（+1 套件 +2 例）；`pnpm tsc` 0 错；`pnpm lint:js` 无输出；prettier 全过；`pnpm build` exit 0。
  - 附注：install 顺带把 `@umijs/max`/`@umijs/lint` 由 4.7.30 重析到 4.7.32，非本次引入——`web/package.json:80-81` 本已声明 `^4.7.32`，lockfile 此前落后于声明，install 属纠偏。
- [Major] 公众号并发排重只阻止重复流水，未阻止重复业务处理 | 位置 `server/src/main/java/com/sinomed/service/impl/WxMessageLogService.java:61`、`server/src/main/java/com/sinomed/service/WxCallbackService.java:164` | 问题：唯一键冲突后只执行 `logReject(..., "duplicate", ...)`，`void logMessage()` 正常返回；调用方仍执行 `router(account).route(msg)` | 影响：两个请求同时通过预检查时，冲突方仍处理关注事件、素材捕获或回复。todo.md:24 引用的并发测试仅断言响应为 success、入站流水为一条，未验证路由执行次数 | 修复方向：原子插入返回是否取得处理权，重复请求立即返回 success；测试强制触发冲突并断言业务仅执行一次。——证据：已修复（commit 34f1611）。
  - `logMessage` 改返回 boolean「处理权」：`saveAndFlush` 插入成功=true；并发撞 dedupe_key 唯一索引=false（吞异常、落 duplicate 拒绝审计、不向上抛，500 重试放大防线不变）。
  - `WxCallbackService` 接线：`!owned` 直接回 success，`router(account).route(msg)` 只由取得处理权的一侧执行——排重语义从「只落一条流水」升级为「只处理一次」。
  - 测试两条腿：新增 `WxMessageLogOwnershipTest` 服务层强制撞唯一索引（预插同键行再写，确定性走冲突分支，2 例）；`WxOfficialAccountHttpTest.concurrentDuplicateBusinessProcessedOnce` 并发双投递 + 关键词规则，断言出站回复流水只 1 条、被动回复只有一侧拿到（重放侧回 success）。
  - 门禁：`mvnw test` 定向 PrescriptionServiceSaveTest/WxMessageLogOwnershipTest/WxOfficialAccountHttpTest 全绿（首跑 23 例唯一失败为所有权测试自踩同类共享库未逐例清表，补 `@BeforeEach deleteAll` 后复跑 2/2 绿）。
- [Major] 切换顾客可携带上一位顾客的接诊单提交 | 位置 `web/src/pages/Prescription/Create/useCustomerLookup.ts:26`、`server/src/main/java/com/sinomed/service/impl/PrescriptionServiceImpl.java:61` | 问题：先执行 `setCustomer(found)`，等待查询后才 `setTreatId(list[0]?.id)`；期间未清除旧接诊单，提交按钮也未受 `finding` 限制。后端直接保存 `view.getTreatId()`，未校验所属顾客 | 影响：已用真实 hook 复现“顾客 2＋顾客 1 的 treatId”；慢请求期间可提交，查询异常时错误关联会持续保留。此为 933845e 拆分后仍存在的缺陷，现有处方测试仅验证请求参数 | 修复方向：切换时立即清除关联状态、拒绝过期响应并限制提交；后端校验接诊单与顾客归属。——证据：已修复（commit f1f90c7）。
  - 前端 `useCustomerLookup`：新查询发起即清 customer/treats/treatId（提交在途也不携带上一位顾客的接诊单）；`lookupSeq` 序号守卫，A→B 连查时 A 的慢响应直接丢弃（不得把顾客翻回 A 却带着 B 的手机号）；`finally` 同样带守卫防旧请求复位 finding。
  - 提交限制：`submit` 增加 `finding` 拦截（「顾客定位中」警告），「存方并收费」按钮 `disabled={finding}`。
  - 后端 `PrescriptionServiceImpl.save`：treatId 非空时必查库验证存在且 `treat.customerId == view.customerId`，否则 IllegalArgumentException（接诊单不存在 / 接诊单不属于该顾客）；无接诊单开方路径不受影响。
  - 测试：新增 `useCustomerLookup.test.ts` 2 例（发起即清 + 过期响应丢弃）；`PrescriptionServiceSaveTest` 增 3 例（同顾客接诊单放行 / 他人接诊单拒绝且整单回滚 / 不存在接诊单拒绝）。
- [Major] 审方失败被显示为检查通过 | 位置 `web/src/pages/Prescription/Create/CheckPanels.tsx:54`、`web/src/pages/Prescription/Create/usePrescriptionChecks.ts:55` | 问题：请求异常时执行 `setCompat(undefined)`，界面随后进入“未发现配伍禁忌”；过敏记录请求失败则 `setAllergyRecordCount(0)`，显示“档案无药物过敏记录” | 影响：网络错误与真实阴性结果无法区分；内存渲染已确认缺失结果会显示上述通过文案。此为 LAY-004 引用实现中的既有问题，服务契约测试无法覆盖 | 修复方向：显式区分待检查、检查中、成功和失败，仅在对应输入检查成功后显示通过结论。——证据：已修复（commit 9106a3a）。
  - `usePrescriptionChecks` 每查带独立失败标志（compatFailed / allergyFailed / allergyRecordFailed / doseFailed / pricingFailed）：请求异常只置 failed 并清结果，绝不回退成通过结论；过敏史条数读取失败不再置 0 冒充「档案无药物过敏记录」。
  - 展示层接线（CheckPanels / PricingPanel）：失败态显示警示文案（配伍「审方服务暂不可用，本次未完成比对」/ 过敏「过敏审方未完成…命中情况未知」「过敏史读取失败，本次无法确认」/ 剂量「剂量审方未完成…超限情况未知」/ 计价「计价服务暂不可用，本次试算未完成」），配伍失败态复用 `.bad` 警示框与绿 `ok` 区分；「未发现配伍禁忌 / 档案无药物过敏记录 / 均未超线」等通过结论只在对应检查成功分支渲染。
- [Major] 清空处方输入未使在途审方请求失效 | 位置 `web/src/pages/Prescription/Create/usePrescriptionChecks.ts:90` | 问题：空输入分支先 `setAllergy(undefined); return`，跳过 `++allergySeq.current`；cleanup 只有 `clearTimeout(timer)`，不能阻止已经发出的请求。同型残留见配伍、剂量、计价分支的 :42、:113、:140 | 影响：真实 hook 测试确认，清空药味后迟到响应仍重新填入旧过敏提示、剂量结果和价格；todo.md:239 所称“序号防竞态”未覆盖该边界 | 修复方向：每轮 effect 开始或 cleanup 时使旧请求失效，并覆盖请求发出后清空输入的测试。——证据：已修复（commit 9106a3a）。
  - 四个防抖 effect 的 cleanup 一律递增各自序号使在途请求失效（`clearTimeout` 挡不住已发出的请求），清空输入分支同样置序——清空药味 / 换药味 / 切换顾客 / 卸载后迟到的响应不再回填状态。
  - 测试 `usePrescriptionChecks.test.tsx` 10 例：可控 Promise 复现「清空后才返回」时序（配伍/计价各 1 例断言不回填、失败标志不残留）；四查请求异常各置对应 failed；CheckPanels 渲染 4 例断言失败态不出现「未发现配伍禁忌 / 档案无药物过敏记录 / 均未超线」通过文案、成功态正常显示。
  - 门禁（web）：`pnpm jest` 10 套件 38 例全绿（较修复前 +2 套件 +12 例）；`pnpm tsc` 0 错；eslint 无输出；prettier 全过；`pnpm build` exit 0。
- [Minor] 动态配置效果文档未达到原验收要求却已勾选 | 位置 `todo.md:23` | 问题：要求是“逐开关 off/on 图＋切换 GIF，流水线可重跑”，完成证据却明确写“文字描述代替截图/GIF”；`docs/guide/config-switches.md:3` 也只声明文字说明和测试类映射 | 影响：文档构建成功不能证明视觉产物和生成流水线已完成，属于完成状态与验收条件不一致 | 修复方向：恢复未完成状态并注明缺项，待对应产物及流水线验收后再勾选。——**已闭环（commit 771244c，2026-11-21 复核验收）**：验收条件已全部满足。产物在位 `docs/public/config-switches/`（**9 PNG + 4 GIF**，覆盖 phone-lookup.mode 三态 / official-account.enabled 两态 / demo.seed 两态 / wechat 凭据两态四个真实开关）；生成流水线 `docs/scripts/gen-config-switch-visuals.sh` 已入库（`git ls-files` 确认），起真 server（每态独立 SQLite 库）→ curl 关键端点 → ImageMagick 渲染 → 拼 GIF，重跑覆盖产物，兼作配置效果回归（内建 expect 断言，漂移即失败）；`todo.md:23` 该条为 `[x]` 且已带「已闭环（commit 771244c）」记录，`771244c` 在 git 历史中（`git log --all` 确认）。2026-11-21 重跑门禁绿：`pnpm -C docs check` 通过（图片引用无断链——本条原质疑「文档构建成功不能证明视觉产物已完成」的直接反证）、`pnpm -C docs build` build complete 223.84s。

## 审核发现（2026-11-21，门禁与 CI 排查）

- [x] CI 文案与事实漂移：Maven wrapper 步骤名硬编码旧版本号 | 位置 `.github/workflows/maven.yml:43` | 问题：步骤名写作 `Build and test with Maven wrapper (3.10.0 pinned in server/.mvn/wrapper)`，但 `server/.mvn/wrapper/maven-wrapper.properties` 的 `distributionUrl` 已由 dependabot PR #26 抬为 `3.10.1`（diff：`apache-maven/3.10.0` → `apache-maven/3.10.1`） | 影响：CI 日志与 README 口径误导排障（本条即排查「为何 `./mvnw` 在本机挂掉」时的干扰源）；仅文案，无行为影响 | 修复方向：把步骤名改为不写死版本，或随 wrapper 同步更新。——**已修复（commit 452b1b7，2026-11-21 代决，用户可推翻）**：`maven.yml:43` 步骤名去掉写死的版本，改 `Build and test with Maven wrapper (version pinned in server/.mvn/wrapper)`（版本号只留在 `server/.mvn/wrapper/maven-wrapper.properties` 一处，文案不再漂移）。代决依据：零行为影响的文案修正、与事实对齐；如需回滚 `git revert 452b1b7`。验证：push `452b1b7` 自动触发 `Java CI with Maven`（run 44866810555）success 21m25s，改名后步骤 `Build and test with Maven wrapper (version pinned in server/.mvn/wrapper)` → success，即 Maven 3.10.1 下 server 构建+测试通过。
- [x] Docker server 镜像构建在镜像内现下载 Maven 发行版、无缓存兜底，一次 429 即红 | 位置 `.github/workflows/docker.yml`（server 镜像 job 的 `./mvnw -B -q package -DskipTests`） | 问题：`./mvnw` 在构建容器内首次运行时去下载 `distributionUrl` 指向的发行版，无预热/缓存层。2026-11-20 PR #26 抬升 wrapper 至 3.10.1 后，该 job 首次需要拉 3.10.1，被 Maven Central 限流：`java.io.IOException: Server returned HTTP response code: 429 for URL: https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.10.1/apache-maven-3.10.1-bin.zip` → `exit code: 1`，`Docker Images` run 44686859977 failure（46s）。同提交 `Java CI with Maven`（44686860088）却 success 2m55s —— 同为 GitHub runner，差异在出网环境 | 影响：构建可用性受外部仓库限流/TLS 抖动支配（本会话在本机表现为另一形态：wrapper 下载器 `SSLHandshakeException: Remote host terminated the handshake`，同一 URL 用 curl 是 HTTP/2 200）。已自愈：PR #25 的 `Docker Images`（f495e34）success 3m16s | 修复方向：构建前预热 wrapper 缓存，或在 `server/Dockerfile` 里改用 `curl`/`wget` 预取发行版再跑 `./mvnw`（本机已验证 curl 可绕过 Java 下载器故障）。——**已修复（commit 452b1b7，2026-11-21 代决，用户可推翻）**：按下方「复核（2026-11-21 二次）」收窄后的方向落地 —— `docker.yml` server job 的 build-push-action 增 `build-args`，透传 `MVN_DIST_MIRROR` / `MAVEN_MIRROR_URL`，经 repo `vars` 取值、未设则回落空串（`${{ vars.X || '' }}`，空串=官方源=**默认行为与改动前逐字节一致**，与 `compose.yml:11-12` 的「空串回退官方源」同口径）。Maven Central 再限流时在仓库 Settings→Variables 配同名 vars 即切国内镜像，无需再改 CI 文件、也无需重发版。代决依据：默认路径零行为变化（不配 vars 就还是官方源）、纯增能力、`git revert 452b1b7` 可回滚；未把镜像设为默认，是避免拿腾讯云可用性换 GitHub runner 原本畅快的官方源出网。验证：push `452b1b7` 自动触发 `Docker Images`（run 44866810577）success 13m56s —— server 镜像与 web 镜像两个 job 均 success，即 build-args 段在真实构建里生效且未破坏镜像推送。
  - 复核（2026-11-21 二次）：**修复方向已收窄，且仓库早留了口子**。`server/Dockerfile` 原生支持两个构建参数 —— `MVN_DIST_MIRROR`（sed 改写 `distributionUrl` 的发行版基址，名字刻意避开 `MVNW_REPOURL` 以防被 wrapper 二次改写）、`MAVEN_MIRROR_URL`（注入 `/root/.m2/settings.xml` 的 `mirrorOf=*`）；`compose.yml:11-12` **已带默认值启用**此模式（`MVN_DIST_MIRROR` / `MAVEN_MIRROR_URL` 均默认 `https://mirrors.cloud.tencent.com/nexus/repository/maven-public`，注释明写「国内源默认开启（出境受限也能构建）；置环境变量为空串即回退官方源」）—— 即自托管/离线构建早已镜像加固。唯一裸奔的是 `docker.yml`：其 `docker/build-push-action` **未传 `build-args`**，故 CI 构建容器内走官方源而被 429。双语文档已记载该参数（`docs/server/deploy.md:79` / `docs/zh/server/deploy.md:79`）。→ 本条落点不是「预热缓存」或「改 Dockerfile 用 curl 预取」，而是**在 `docker.yml` 的 build-push-action 增 `build-args`**（可经 `vars`/`secrets` 开关、默认空串以保持现行为，与 compose 的「空串回退官方源」口径一致）；`maven.yml:43` 的文案漂移与本条同源（同为 PR #26 抬升 wrapper 版本所致）。
- [x] 审核发现：release.yml 的 publish job 必红 —— 发布流程与用户实际发版方式脱节 | 位置 `.github/workflows/release.yml`（`release` job） | 问题：`release` job 无 `actions/checkout`，`gh release create "$RELEASE_TAG" --verify-tag` 内部 shell 出 `git` 探测仓库，无 `.git` → `fatal: not a git repository (or any of the parent directories): .git` → 该 job 的「Create GitHub Release」步骤**必失败**（`--verify-tag` 需要能解析 tag，无 checkout 则无从解析） | 证据：`Release` run 44875632721（2026-11-21 06:27:34Z，tag `v0.1.1` push 触发）failure 5m7s，日志末行 `failed to run git: fatal: not a git repository`；`gh run list --workflow=release.yml` 仅 2 次 run（39171132921 dispatch 2026-10-18、44875632721），均 failure —— 即该 workflow 的发布 job 自建立以来**从未成功过一次** | 现状澄清：`v0.1.1` 的 Release **已由用户自行发布**（author cuihairu，created 05:49:55Z 草案、published 06:27:33Z，含 7 个资产：`sinomed-server-0.1.1.jar`、`sinomed-server-0.1.1-linux-x64.tar.gz`、`sinomed-desktop_0.1.0_amd64.deb`、miniprogram/kiosk/tablet/web 各 zip + 精选 notes）—— 即 tag push（06:27:34Z）触发 workflow 前 1 秒发布已完成，run 的失败是**装饰性**（非发版被卡），且该 workflow 阶段产物只有 `sinomed-0.1.1.jar`+tar.gz（无 desktop/miniprogram/kiosk/tablet/web），与用户实际 7 资产发版方式**不匹配** | 版本红线对照：`v0.1.1` 为用户手动发版（允许）；但 workflow 若修好，下次 tag push 会产出**稀疏版**（仅 server 两资产）并与用户手动发版流程冲突 | 需用户决策（agent 不自行改发版管线）：(a) 修 `release` job（补 `actions/checkout` + 对齐资产到用户 7 资产口径）让 workflow 能真发版；(b) 移除/停用 `release` job（tag push 只做 build+test 校验，不再出现必红的发布步骤）；(c) 维持现状（必红=天然失效保护，但每次 tag push 都亮红灯误导排障）。——证据：已修复（commit 9cc52a1，2026-11-27 用户拍板选 (a)「按用户实际发版方式修正」）：release job 补 actions/checkout（ref 钉同一 tag，修必红根因「无 .git → fatal: not a git repository」）+ 显式 GH_REPO 双保险；资产对齐 v0.1.1 用户 7 资产口径——6 个并行构建 job（server 带 surefire 测试不可绕过 / web lint+tsc+jest+build / app 运行时测试+类型检查+build:weapp（发布资产只含 weapp 不跑 h5）/ kiosk / tablet / desktop deb（逐字复用 desktop.yml linux-deb 配方，含 TAURI_SIGNING_PRIVATE_KEY 签名，runs-on 固定 ubuntu-24.04 与 desktop.yml 一致不走 CI_RUNNER）），release job needs 六者全绿才发布；新增 validate job 前置校验 vX.Y.Z tag 格式；8 类资产统一在 release job 生成 SHA256SUMS（数组展开先于重定向建文件，不自含）；zip 以产物内容为根（与 v0.1.1 实包 unzip -l 逐字核对，index.html 在 zip 根部）；deb 文件名沿用 tauri conf 自身版本不随 tag 改名；自动 notes 含资产 SHA 表与组件说明（用户发布后仍可再改精选 notes，与 v0.1.1 做法一致）；版本红线不变——workflow 仍只在用户打 v* tag push 或显式 dispatch 传入已有 tag 时运行，绝不自动建 tag（--verify-tag 保留）。docs/guide/development.md 与 zh 版 CI 表 Release 行同步改为 7 资产口径。验证：actionlint 0 告警；push 后 Java CI with Maven（run 45936242310）success + Deploy Docs（run 45936242290）success；Release workflow 本身未被触发（无 tag push/点火，未跑正式发版，红线遵守）。
- [x] 审核发现：Maven wrapper 版本文案在 release.yml / README / 文档 9 处仍硬编码 3.10.0 —— 452b1b7 只清了 maven.yml 的漂移，同类残留散落别处 | 证据：`server/.mvn/wrapper/maven-wrapper.properties` 的 `distributionUrl` 已为 `apache-maven-3.10.1-bin.zip`（PR #26 抬升），而 `.github/workflows/release.yml:4`、`.github/workflows/release.yml:57`、`README.md:80`、`docs/zh/guide/getting-started.md:10`、`docs/zh/guide/development.md:52`、`docs/zh/guide/development.md:63`、`docs/guide/getting-started.md:10`、`docs/guide/development.md:52`、`docs/guide/development.md:63` 共 9 处仍写 3.10.0（`docs/.vitepress/dist/**` 为构建生成物、未入库，不计）——**已修复（commit 8c3d150，2026-11-21 代决，用户可推翻）**：9 处一律去硬编码号、改为「版本以 `server/.mvn/wrapper` 为准」，与 452b1b7 对 maven.yml 的口径逐字一致（版本号只留 wrapper 一处不再漂移）。代决依据：纯文案修正、零行为影响、`git revert 8c3d150` 可回滚；**不改发布 job 语义** —— 上方「release.yml 的 publish job 必红」条目（未勾）的 (a)/(b)/(c) 决策仍待用户，本条仅是构建口径文案与事实对齐。验证：push `8c3d150` 自动触发 `Java CI with Maven`（run 44883294129）success + `Deploy Docs`（run 44883294157）success，两门禁均绿。