# 配置开关效果

> 本页把 `server/src/main/resources/application.yml` 的行为开关逐个列成 **off / on 两态** 的生效说明（文字描述代替截图/GIF），并标注对应的回归测试——**可重跑、兼作配置效果回归**：改任一开关后跑对应测试类，绿灯即该开关的行为未被破坏。测试口径见 [开发指南](/zh/guide/development#测试)（`./mvnw test` 自带 SQLite，无需外库）。

## 手机查卡开关 `sinomed.phone-lookup.mode`

三态（`off` / `openid_only` / `sms_verified`），默认 `off`，环境变量 `PHONE_LOOKUP_MODE` 覆盖。隐私红线：不做裸手机号查卡。

### off（默认）

- **效果**：`/api/v1/kiosk/card-lookup`、`/api/v1/kiosk/my-appointments`、`/api/v1/kiosk/appointment-cancel`、`/api/v1/kiosk/openid-session`、`/api/v1/kiosk/openid-bind` 一律 400「在线查卡已关闭，请到店前台或致电门店查询」（查卡）/「在线自助查询已关闭」（预约/取消），即使带了手机号或验证码也不放行；每次拒绝落 `card_lookup_logs` 审计（result=disabled）。
- **回归**：`CardLookupOffTest`（8 项：裸手机号拒、openid 拒、取码拒、openid-session 拒、my-appointments 拒、appointment-cancel 拒、openid-bind 拒、每次拒绝落审计）。

### openid_only

- **效果**：`/api/v1/kiosk/card-lookup` 按 openid 查绑定顾客；缺 openid / 未绑定都 400；不发短信码。`my-appointments` / `appointment-cancel` 走 openid 登录态。取码接口仅用于绑定（`/api/v1/kiosk/sms-code` 支持绑定场景）。openid 换取走 `/api/v1/kiosk/openid-session`（`wx.login` code → openid）；小程序凭据未配置时该换取 400「微信凭据未配置」（不静默）。
- **回归**：`CardLookupOpenidTest`（7 项：openid 查卡命中、缺 openid 拒、未绑定拒、取码供绑定、my-appointments 拒、appointment-cancel 拒、成功与拒绝落审计）、`KioskOpenidSessionNoCredentialTest`（凭据未配置拒绝）、`KioskOpenidSessionTest`（换 openid、微信错误 400、缺 code 拒）。

### sms_verified

- **效果**：`/api/v1/kiosk/card-lookup` 用手机号+一次性验证码双因子查卡；取码 `/api/v1/kiosk/sms-code`（5 分钟有效、一次性、错 5 次烧码）；通过后按手机号查顾客档案，查无即 400。绑定路径（`openid-bind`）在短信模式拒绝。
- **回归**：`CardLookupSmsTest`（14 项：发码、有效码命中、attempts 初始 0、原子递增烧码、错码拒、缺码拒、未取码拒、过期拒、错满烧码、一次性、并发正确码单次消费、查无档案拒、短信模式 openid-bind 拒、成功与拒绝落审计）。

## 公众号模块开关 `sinomed.official-account.enabled`

布尔，默认 `false`，环境变量 `WX_OA_ENABLED` 覆盖。总开关：**false = 模块整体关闭**，激活需 `WX_OA_TOKEN` 与 `WX_OA_CREDENTIAL_KEY`。

### false（默认）

- **效果**：`/api/v1/wx/callback/{accountId}` GET 验证不回显 echostr、POST 推送一律 200 回 `success` 拒处理（不静默），落 `wx_message_logs` 审计（result=disabled）；`/api/v1/wx/bind/{accountId}/code` 与 `/api/v1/wx/bind/{accountId}` 一律 400 拒绝，落 `wx_binding_logs` 审计（result=disabled），不落消息流水。
- **回归**：`WxCallbackDisabledModuleTest`（2 项：GET 验证+POST 拒处理落审计、绑定取码与绑定 400 落绑定审计）。

### true

- **效果**：回调进入业务处理——URL 验证合法签名回显 echostr；文本消息按关键字规则自动回复；关注/取关事件更新粉丝表；同一 `MsgId` 重复投递排重（并发双投递不 500）；账号脏值（enabled=2）在回调与验 URL 一致拒绝。素材拉取/上传、菜单推送、网页授权在 `true` 下按各自凭据门禁运行。
- **回归**：`WxOfficialAccountHttpTest`（10 项：URL 验证回显、坏签名拒、关键字文本回复、关注 upsert 粉丝+欢迎语、取关、重复 MsgId 忽略、脏值一致拒绝、并发重复 MsgId 双成功、未知账号拒+审计、无规则兜底）、`WxMaterialCaptureHttpTest`（6 项）、`WxMenuPushHttpTest`、`WxOauthHttpTest`（5 项）、`WxCredentialCryptoTest`（3 项）等。

## 演示种子开关 `demo.seed`

布尔，默认 `false`，环境变量 `DEMO_SEED` 覆盖。`@ConditionalOnProperty(name = "demo.seed", havingValue = "true")` 才启用 `DemoSeedRunner`。

### false（默认）

- **效果**：启动不播种演示数据；库为空时管理端/接口无可演示档案。
- **回归**：无独立测试类（条件装配，`SinomedApplicationTests` 默认不触发）。

### true

- **效果**：启动播种虚构脱敏数据（staff `gu`/`shen`/`su`、演示顾客、卡项等），逐表以自然键（账号/手机号/卡项名/复盘日）判重、幂等，重复启动不产生重复数据；某节失败只记日志不阻断启动，下次启动补齐。
- **回归**：`SinomedApplicationTests`（`DEMO_SEED=true` 时上下文装配含种子 runner，`./mvnw test` 不设该变量故默认不播）。

## 名医溢价上限 `sinomed.famous-doctor-surge-cap-percent`

数值，默认 `20`，环境变量 `FAMOUS_DOCTOR_SURGE_CAP_PERCENT` 覆盖。溢价值单列展示，不做阶梯/时段差异。

- **效果**：名医挂号费溢价不得超过该百分比；仅影响列表排序与标识徽章（`staffs.famous_level` 0-3）。后台可改。
- **回归**：无独立测试类；由 `StaffLookupTest` / 管理端列表接口覆盖。

## 微信小程序凭据 `sinomed.wechat.app-id` / `app-secret`

空串默认，环境变量 `WECHAT_APP_ID` / `WECHAT_APP_SECRET` 覆盖。openid 换取路径的**激活前置**，非行为开关本身。

- **未配置（默认）**：`/api/v1/kiosk/openid-session` 换 openid 400「微信凭据未配置」并落审计（result=code_exchange_failed），不静默。
- **已配置**：经 `jscode2session` 出网换 openid，成功进入绑定/查卡流程；微信返回错误映射 400 落审计。

> 注：本页「效果」用文字描述代替截图/GIF；「回归」列对应测试类跑绿即该开关行为未破坏，流水线 `./mvnw test`（server）与 `pnpm build`（docs）可重跑。
