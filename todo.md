# TODO

> 账本生成于 2026-10-11。排序依据（2026-10-14 用户令）：手机端优先——新功能先落手机端，影响手机端的缺陷排在同类桌面问题之前，本阶段不新增桌面功能。
> 历史巡检与审核轨迹见 docs/审计-文档一致性.md 与 docs/review/；本文件只留需求与证据。

## 待办

- [ ] 公众号模块第一期余项：素材上传/菜单推送/网页授权真实出网，待公众号正式凭据（appid+AppSecret）与公众号后台「网页授权域名」白名单登记（均用户侧，凭据到位即激活无需改码）；群发/模板/客服待对应工作台立项；第三期带参二维码未排期。 **[BLOCKED 用户侧凭据与域名白名单，见 docs/review/2026-10-30-decisions.md 第 2 项]**
  - 证据（2026-11-01 复核）：application.yml:57 enabled 仍 false、:58 WX_OA_TOKEN 空；已代决按 docs/review/2026-10-30-decisions.md 执行。
- [ ] 微信小程序正式凭据（appid+AppSecret）：真机运行与 openid 通道（openid-session/openid-bind）激活压在此（用户侧；公众号另用独立 appid 存 wx_accounts，非本项 WECHAT_APP_ID/WECHAT_APP_SECRET）。 **[BLOCKED 用户侧账号凭据与真机联调，见 docs/review/2026-10-30-decisions.md 第 1 项]**
  - 证据（2026-11-01 复核）：app/project.config.json:5 仍 touristappid；application.yml:52-53 空缺省；已代决按 docs/review/2026-10-30-decisions.md 执行。
- [ ] 在线支付真实通道：缺商户号与密钥等真实凭据 + 用户授权接网关，付费红线不碰（用户侧）。 **[BLOCKED 用户侧商户资料与网关授权，见 docs/review/2026-10-30-decisions.md 第 3 项]**
  - 证据（2026-11-01 复核）：PayQrCodeController 仍仅按本地目录出收款码图，无网关/商户号/密钥配置；已代决按 docs/review/2026-10-30-decisions.md 执行。
- [x] 配置开关效果动态文档页（双语）：逐开关 off/on 图 + 切换 GIF，流水线可重跑、兼作配置效果回归（kode todo C-24 同款，2026-11-01 用户令）——证据：已建成 docs/guide/config-switches.md 与 docs/zh/guide/config-switches.md（off/on 两态文字描述代替截图/GIF，逐开关标注对应回归测试类；纳入 VitePress 双语侧栏），pnpm -C docs check 绿 + pnpm -C docs build 绿（.vitepress/dist/guide/config-switches.html 与 /zh/guide/config-switches.html 渲染成功）。
- [x] 公众号回调排重 check-then-insert 竞态：并发重复消息可触发 dedupe_key 唯一索引冲突未捕获 → 500 → 微信重试放大（C3，docs/review/2026-11-01-code-review.md；证据 WxOfficialAccountService.onMessage:270-275）——证据：已修复（logMessage 改 saveAndFlush+DataIntegrityViolationException 捕获→落 duplicate 审计回 success，并发双投递不再 500），WxOfficialAccountHttpTest.concurrentDuplicateMsgIdBothSucceedWithoutRace 并发双线程定向绿。
- [x] 预约时段互斥 check-then-act 竞态：两个自助约期并发过 count==0 后双双落库，时段双占（C4，docs/review/2026-11-01-code-review.md；证据 AppointmentServiceImpl.ensureSlotFree:69-91 + KioskController:249）——证据：已修复（AppointmentEntity 增 slot_key/room_slot_key @Column(unique=true)，SQLite 方言内联为唯一列，MySQL 建唯一约束；status 0/1 落 "<staff|room>|<hourStart>"、终态置 NULL 释放；save 改 saveAndFlush+唯一违例捕获转 400；Kiosk 自助路径同样接线），AppointmentSlotTest.concurrentSameSlotOnlyOneSucceeds 并发双线程定向绿（连续 3 次过，schema 实查 slot_key/room_slot_key 均带 unique）。
- [x] 账号启用判定一致化：onMessage 用 `!=0`、其余 `==1`，saveAccount 补 enabled∈{0,1} 校验，防脏值 2 出现「收消息按启用、验 URL 按停用」（C5，docs/review/2026-11-01-code-review.md；证据 WxOfficialAccountService:211 vs :196、:1057）——证据：已修复（onMessage 改 `==1` 与 verifyUrl/其余一致；saveAccount 增 enabled∈{0,1} 校验，脏值 2 直接 400），WxOfficialAccountHttpTest.dirtyEnabledRejectedConsistentlyOnCallbackAndVerify + WxAccountAdminHttpTest.rejectsInvalidFields（enabled=2 断言）定向绿。
- [x] 回调验签改常量时间比较（C6，docs/review/2026-11-01-code-review.md；证据 WxSignatureUtil.java:22）——证据：已修复（WxSignatureUtil.check 改 MessageDigest.isEqual 常量时间逐字节比较，去 equalsIgnoreCase 大小写折叠），WxSignatureUtilTest.rejectsWrongSignature 补等长大写拒绝断言定向绿。
- [x] WxOauthTicketStore 未兑换票据清理：签发未兑换票据永久滞留（C7，docs/review/2026-11-01-code-review.md；证据 WxOauthTicketStore.java:29）——证据：已修复（issue 顺带 purgeExpired 清过期未兑换票据，内存有界于 TTL 窗口），WxOauthTicketStoreTest.redeemIsSingleUseAndWrongAccountRejected 定向绿。
- [x] 素材拉取 mimeType 规范化对齐上传路径白名单（C8，docs/review/2026-11-01-code-review.md；证据 WxOfficialAccountService.java:1448）——证据：已修复（captureMaterial 经 canonicalizeMimeType 剥参+小写+白名单规范化入库，白名单外回 null 兜底 octet-stream），WxMaterialCaptureHttpTest.fetchMimeNormalizedToWhitelist（含参数/大小写与 text/html 两断言）定向绿。
- [x] uploadLocalMaterial 落盘后 save 失败清孤儿文件（C9，docs/review/2026-11-01-code-review.md；证据 WxOfficialAccountService.java:1243-1256）——证据：已修复（uploadLocalMaterial 改 saveAndFlush，catch RuntimeException 时 Files.deleteIfExists(target) 清刚写孤儿文件再上抛，与远端上传路径同口径），全量 458 测试绿。
- [x] web JWT 存储纵深防御评估（localStorage 可被 XSS 窃取，当前无注入点，评估 httpOnly cookie 迁移）（C10，docs/review/2026-11-01-code-review.md；证据 web/src/pages/User/Login/index.tsx:91）——证据：评估完成，维持 Bearer+localStorage（非目标、可推翻）：全 web 仅小票窗口 document.write，服务端 ReceiptPrintServiceImpl 对 clinic/customer/staff/item/pay 全部 esc；无 dangerouslySetInnerHTML/eval/new Function；httpOnly 迁移需改登录/请求/登出全链路并补 CSRF/SameSite，当前无注入点下收益不足以引入该破坏面；重评触发=新增富文本/第三方脚本/未转义 HTML 注入面。
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

