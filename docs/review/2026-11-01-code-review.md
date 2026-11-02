# 代码深审（2026-11-01，全仓）

> 范围：server（Java/Spring Boot）、web（Ant Design Pro）、app（Taro 小程序）、desktop（Tauri 2/Rust）、kiosk、tablet。
> 方法：安全静态扫描（硬编码密钥 / shell 注入 / SQL 拼接 / eval / 反序列化 / 路径穿越）+ 隐藏 bug 猎捕（错误处理 / 竞态 / 越界 / 资源泄漏 / off-by-one / 并发）+ 质量门（lint / 类型检查 / 测试基线对比）。
> 每条发现带 file:line 证据；P0/P1 当场修（一问题一 commit），P2/P3 记 todo.md。

## 一、发现清单（按严重度排序）

| ID | 严重度 | 位置 | 发现 | 证据 |
|----|-------|------|------|------|
| C1 | **P1 安全** | server KioskController.consumeSmsCode（:612-632）+ WxOfficialAccountService.consumeBindSmsCode（:741-760） | 短信验证码 6 位数字可无限次试错：4 个免登录端点（kiosk 查卡 / my-appointments / appointment-cancel / wx bind）共用「取最新码→比对」逻辑，错误尝试不计数、不锁定，5 分钟有效期内暴力枚举 10^6 组合可行；每步虽落审计但无拦截。与隐私红线「裸手机号查卡不做」直接相邻——双因子的短信侧可被爆破后只剩手机号单因子 | KioskController.java:626 `!issued.getCode().equals(submitted.trim())` 仅抛 400，无尝试计数；SmsCodeEntity 无 attempts 列（SmsCodeEntity.java 全文）；两处消费逻辑同形 |
| C2 | **P1 功能** | web/src/requestErrorConfig.ts errorHandler | JWT 过期后整个管理端假死：401 只弹 toast 不跳登录。`ErrorShowType.REDIRECT` 分支是 `// TODO: redirect`（:70-71）；axios 错误分支（:86-92）只 message.error；web 全程无 refresh token 流程（login 响应里的 RefreshHeader 无人消费），JWT 一天过期（application.yml:74）→ 每位员工每天至少触发一次全界面 401 假死，唯一恢复路径是手敲 /user/login | requestErrorConfig.ts:70-71（TODO redirect）、:90（status toast）；web/src 无任何 refresh 调用（grep refresh 仅业务轮询）；server/src/main/resources/application.yml:74 |

P2/P3 见下方「记入 todo 的发现」，与 todo.md 待办一致。

## 二、已核实无问题的攻击面（扫描路径与结论）

- **硬编码密钥**：`grep -rniE '(password|secret|apikey|token)\s*[:=]\s*"..."'` server/web/app/kiosk/tablet/desktop 全部零命中（配置走 env/yml，凭据 AES 加密落库——WxSecretAttributeConverter）。
- **shell 注入 / eval**：`Runtime.getRuntime` / `ProcessBuilder` / `ScriptEngine` / `eval(` / `new Function(` 全仓零命中（desktop 的 `Command::new` 仅拉起自身 exe，main.rs:171-179）。
- **SQL 拼接**：全部 JPQL 命名参数；唯一字符串拼接 `DemoSeedRunner.java:1115 "UPDATE " + table` 的 table 是内部常量 "treats"（:1104），非用户输入。
- **反序列化**：ObjectInputStream / enableDefaultTyping 零命中。
- **路径穿越**：素材读 `resolveLocalPath` normalize 后 `startsWith(root)`（WxOfficialAccountService.java:1195-1201）；素材/广告上传文件名服务端 UUID 生成（:1240、AdsServiceImpl.java:101-105）；收款码 method 白名单封死拼接（PayQrCodeController.java:34/57）；desktop 模板缓存扩展名 regex 白名单（WxOfficialAccountService.java:1466-1473 `ext.matches("[a-z0-9]{1,5}")`）。
- **XSS**：web/app/kiosk/tablet 无 dangerouslySetInnerHTML / innerHTML。
- **off-by-one**：菜单容量 `tops >= 3` / `children >= 5`（≤3/≤5 正确，WxOfficialAccountService.java:1001-1010）；菜单名权重 `weight > 8` / `> 16`（=8/=16 放行正确，:943-946）；排班互斥半开区间 `[hourStart, hourEnd)` 注释明确（AppointmentServiceImpl.java:79-80）。
- **并发**：WxOauthTicketStore.redeem 用 `ConcurrentHashMap.remove` 原子一次性兑换（WxOauthTicketStore.java:31）；WxMpServiceProvider `computeIfAbsent` + 显式 evict（WxMpServiceProvider.java:33-41）；OAuth state 用 `MessageDigest.isEqual` 常量时间比较（WxOfficialAccountService.java:682）。
- **资源泄漏**：无裸流（仅 `MultipartFile.transferTo`，Spring 管理生命周期）；desktop 模板临时目录 Drop 清理（templates.rs:121-128）。

## 三、记入 todo 的发现（P2/P3）

| ID | 严重度 | 位置 | 发现 |
|----|-------|------|------|
| C3 | P2 竞态 | WxOfficialAccountService.onMessage :270-275 | 排重 check-then-insert：`existsByDedupeKey` 与 `logMessage` 落库间无原子性，dedupe_key 唯一索引（WxMessageLogEntity.java:66）会把并发重复消息变成未捕获 DataIntegrityViolation → 500 → 微信重试放大（正是设计要避免的） |
| C4 | P2 竞态 | AppointmentServiceImpl.ensureSlotFree :69-91 + KioskController :249 | 每时段限约 1 位为 check-then-act：两个自助约期并发都过 count==0 后双双落库，时段双占；免登录端点、真实可达 |
| C5 | P3 | WxOfficialAccountService :211 vs :196 | 账号启用判定不一致：onMessage 用 `!= 0`，verifyUrl/其余全用 `== 1`；saveAccount 不校验 enabled∈{0,1}（:1057），脏值 2 会出现「收消息按启用、验 URL 按停用」 |
| C6 | P3 | WxSignatureUtil.java:22 | 回调验签 `equalsIgnoreCase` 非常量时间比较（SHA-1 hex；业界通行写法，利用面极窄） |
| C7 | P3 | WxOauthTicketStore.java:29 | tickets map 只在 redeem 时删除，签发未兑换的票据永久滞留，慢性内存增长（单店低流量，量级极小） |
| C8 | P3 | WxOfficialAccountService.java:1448 | 素材拉取 mimeType 原样存远端 Content-Type（上传路径已做规范白名单，拉取路径未对齐；回放 Content-Type 取该列） |
| C9 | P3 | WxOfficialAccountService.java:1243-1256 | uploadLocalMaterial 落盘后 save 失败会留孤儿文件（远端上传路径有清理，本地治理路径没有） |
| C10 | P3 | web/src/pages/User/Login/index.tsx:91 | JWT 存 localStorage（可被 XSS 窃取；当前无 XSS 注入点，纵深防御项） |
| C11 | P3 | SecurityConfig.java:51-58 | swagger/knife4j/actuator permitAll 无 prod profile 区分（API 面暴露；actuator 无 management 配置=默认只出 health） |

## 四、质量门（基线对比）

| 门 | 结果 | 基线 |
|----|------|------|
| server `./mvnw -B test` | 448/448 绿，BUILD SUCCESS | 448（839342d）持平 |
| web lint:js + tsc + jest | 0 错 + 0 错 + 21/21 绿（6 suites） | 21 持平 |
| app tsc --noEmit --skipLibCheck | 0 错 | 0 错持平 |
| desktop `cargo test` | 23/23 绿 | 23 持平 |
| kiosk / tablet `pnpm build`（tsc -b） | 见收口注记 | — |

## 五、修复记录

- **C1**（P1 安全）→ 已修，commit `b8a0e7d`「fix: cap sms code wrong attempts」：SmsCodeEntity 增 `attempts` 列（ddl-auto 加列）；kiosk 与 wx 两侧 consume 逻辑同口径——错试计数，连续 5 次烧码（used=1 作废须重新取码）；新增 `codeBurnedAfterRepeatedWrongAttempts` 两例（CardLookupSmsTest / WxBindHttpTest）。全量 450 测试绿（基线 448 + 2）。
- **C2**（P1 功能）→ 已修，commit `99db876`「fix: redirect to login on 401」：errorHandler 增 401 分支——清 jwt + `history.push('/user/login')`，登录请求自身（/api/v1/user/login）不重定向避免回环；新增 `requestErrorConfig.test.ts` 3 例锁契约（401 跳转 / 登录请求不跳 / 非 401 维持 toast）。web jest 24/24、tsc 0 错、lint 0 错。

P2/P3（C3-C11）已记入 todo.md 待办（未修复）。

## 六、复审（2026-11-02，HEAD = b096e94）

范围：自首审（e672e38）以来的增量——C1 修复（b8a0e7d）、C2 修复（99db876）、工作区 Wx 角色门禁收尾改动（本轮落地 b096e94）；方法不变（安全静态扫描 + 隐藏 bug 猎捕 + 质量门），首轮扫描面全量复跑。

### 复审发现

| ID | 严重度 | 位置 | 发现 | 处置 |
|----|-------|------|------|------|
| C12 | P1 构建 | docs/package.json:18-22 + docs/pnpm-workspace.yaml:1-3 | docs 构建配置分脑：package.json 残留弃用 `pnpm.overrides`（vite 6.4.4）与 pnpm-workspace.yaml（6.4.3）、lockfile（6.4.4）三处不一致——pnpm ≥ 11 拒跑 docs 脚本（`ERR_PNPM_VERIFY_DEPS_BEFORE_RUN`），CI `--frozen-lockfile`（docs.yml）越过 pnpm 10 即断 | 已修：删死 `pnpm` 字段、workspace yaml 对齐 6.4.4、补 `onlyBuiltDependencies`（esbuild）、重生成 lockfile；`pnpm build` 绿（8.8s） |

### 修复复核（C1 / C2）

- **C1 复核（修于 b8a0e7d）**：consume 逻辑逐行复审——off-by-one 无（attempts 1→5，第 5 次置 `used=1`，计数与烧码同事务）；并发双读同 attempts 最坏提前烧码（安全方向）；烧码后正确码必拒（`used==1` 先判）；4 个免登录端点（kiosk 查卡 / my-appointments / appointment-cancel / wx bind）同口径（kiosk 与 wx 两侧同形）。锁定测试 `codeBurnedAfterRepeatedWrongAttempts` ×2 绿。
- **C2 复核（修于 99db876）**：token 存/读/删同键 `jwt`（Login/index.tsx:91 / requestErrorConfig.ts:23,27,93）；登录请求 `/api/v1/user/login` 与 `/user/login` 路径双豁免不回环；锁定测试 requestErrorConfig.test.ts 3 例绿。

### Wx 角色门禁收尾（工作区既有改动，复审通过后落地 b096e94）

- `access: 'canAdmin'`（routes.ts:306）语义核实：canAdmin = `currentUser.access === 'admin'`（access.ts:7），`access` 来自 `/api/v1/user/current` = `StaffEntity.getAccess()`（`role < 10` → "admin"，StaffEntity.java:27/:95-96/:111-116）——即 **MANAGER+**，与后端 `hasAnyRole('MANAGER','ADMIN')` 同口径，非 ADMIN-only 过锁。
- 分区注释（WxOfficialAccountController.java:181/:266/:316）与分区内 `@PreAuthorize` 口径对齐；WxAdminRoleGuardHttpTest 补 MANAGER 写路径放行（capabilities / 自动回复 CRUD / 菜单 CRUD / push / unbind；push 与 unbind 以业务 400 证明已穿越授权层）。

### 复审扫描结论（与首轮一致，无新增攻击面）

- 硬编码密钥：唯一命中 `StaffServiceImpl.java:41` 默认管理员 bcrypt("123") 哈希（审计 A28 已记录的惰性建号，非泄露）；shell 注入 / eval / ProcessBuilder / ScriptEngine / ObjectInputStream / enableDefaultTyping 全仓零命中；SQL 拼接仅 `DemoSeedRunner.java:1115` 内部常量（非用户输入）；路径穿越 / XSS / off-by-one / 并发 / 资源泄漏维持首轮结论——本轮改动面（SMS consume、401 分支、routes.ts、测试）无新增问题。

### 质量门（复审）

| 门 | 结果 | 基线 |
|----|------|------|
| server `./mvnw -B test` | 450/450 绿，BUILD SUCCESS | 450（b8a0e7d 后）持平 |
| web lint:js + tsc + jest | 0 错 + 0 错 + 24/24 绿（7 suites） | 24（99db876 后）持平 |
| docs `pnpm check` + `pnpm build` | 双绿（C12 修复后 8.8s） | 首轮全绿（C12 后） |
| kiosk / tablet `pnpm build`（tsc -b） | 双绿（3.1s / 3.1s） | 本轮无源码改动，复跑确认 |
| app / desktop | 本轮无改动，未重跑 | 首轮绿 |

### 剩余

- P2/P3（C3-C11）仍未修，已记 todo.md 待办；本轮新增 C12 已当场修（P1 构建），无新增未修项。
