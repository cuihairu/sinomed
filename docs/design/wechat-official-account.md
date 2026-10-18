# 公众号管理模块设计（服务号 / 订阅号接入）

> 🌐 [English](wechat-official-account.en.md) ｜ **本页为中文**

> 状态：**设计稿（未排期）**。本页回答三件事：公众号能力如何接入本仓（选型结论：方案 a，引入 WxJava SDK 自研薄封装）、数据模型如何与顾客/会员实体衔接（公众号粉丝 ≠ 小程序 openid，经 `wx_fans.customer_id` 桥接）、以及分期如何切（第一期只做「接入 + 消息收发 + 关联顾客」）。

---

## 1. 定位与范围

微信公众号是医馆触达顾客的第二条线上通道（第一条是小程序，已实装）。定位为**运营辅助模块**，不是诊疗或收银链路的一环：

- **做**：消息收发（被动回复）、自动回复规则、顾客档案关联、自定义菜单、素材管理、（服务号）模板消息与群发；
- **不做**：微信支付（沿用现有收银的支付口径，见 README「Planned Features」）、多门店矩阵号、小程序内嵌公众号组件、内容合规审查工具化。

单店口径：默认**一个公众号账号**，表结构预留多账号（`account_id` 维度），但第一期 UI 只暴露单账号配置。

---

## 2. 开源方案对比

| 方案 | Star | 许可 | 技术栈 | 功能覆盖 | 与本仓适配度 |
| ---- | ---- | ---- | ------ | -------- | ------------ |
| [binarywang/WxJava](https://github.com/binarywang/WxJava) | 33.2k | Apache-2.0 | Java SDK（含 `wx-java-mp-spring-boot-starter`） | 公众号/小程序/企业微信/微信支付全套 API 封装 | **最高**：同为 Java/Spring Boot，只引 SDK 不引架构，本仓自研薄封装 |
| [niefy/wx-manage](https://github.com/niefy/wx-manage)（前端）+ [wx-api](https://github.com/niefy/wx-api)（后端） | 1.6k | Apache-2.0 | Vue2 + ElementUI；Spring Boot + **Shiro** + MyBatis-Plus + WxJava | 菜单、自动回复、素材、简易 CMS、模板消息、粉丝、带参二维码 | **低**：仓库已归档（archived）；Shiro 与本仓 Spring Security 冲突、MyBatis-Plus 与本仓 JPA 冲突，整套引入=双套鉴权与双套 ORM |
| [anchel/wechat-official-account-admin](https://github.com/anchel/wechat-official-account-admin) | 41 | MIT | Go + MongoDB + Redis | 功能最全：多公众号、收发消息（明文/密文）、自定义菜单含个性化、素材、二维码、用户标签 | **仅参考设计**：Go 版不可直接用；MongoDB/Redis 本仓均未引入，其数据模型与「个性化菜单」交互设计可借鉴 |
| [vluma/wechat_official_account_mcp](https://github.com/vluma/wechat_official_account_mcp) | 3 | **无许可（未声明）** | Python MCP | 素材/草稿/发布；明确区分服务号/认证订阅号/未认证订阅号权限边界 | **仅参考文档**：MCP 形态不适用本仓服务端；无许许可复用性存疑；其账号类型权限矩阵（§6）有参考价值 |

> 数据来源：GitHub API，2026-10-19 实测（star 数、license、archived 标志均取自各仓库 API 响应）。

**结论：方案 a（直接引入 WxJava SDK，自研薄封装）**，理由：

1. **技术栈同源**：本仓 Java 21 + Spring Boot（`server/pom.xml:5-8`），WxJava 是纯 Java SDK，经 `wx-java-mp-spring-boot-starter` 与 Spring Boot 配置直接绑定；wx-manage 的 Shiro/MyBatis-Plus 与本仓 Spring Security/JPA 直接冲突，引入即背双套架构。
2. **依赖最小**：只加一个 Maven 依赖 + 一个配置段，不新增进程/中间件（b 方案等于并一个独立后端，c 方案引入 MongoDB/Redis）。
3. **可控性最好**：回调路由、顾客绑定、审计落库都是本仓强口径（见 §3、§5），必须自己写；SDK 只替代「HTTP + XML + AES + access_token 管理」这些无差异体力活。
4. **活跃度**：WxJava 2026-10 仍在发版（仓库 API `pushed_at=2026-10-17`）；wx-manage 已归档。

---

## 3. 本仓现状盘点（file:line）

### 3.1 模块划分与技术栈

- **单模块后端**，无聚合父 pom：`server/pom.xml:1-4`，artifactId `sinomed`，version `0.1.0-SNAPSHOT`；CI 只认单模块构建（`.github/workflows/maven.yml:29`）。
- **Java 21 + Spring Boot**：`server/pom.xml:25-27`（`java.version=21`，parent 为 `spring-boot-starter-parent`）。
- **包结构固定为 10 层**（`server/src/main/java/com/sinomed/`）：`config/`（11 个装配类）、`controller/`（30 个 REST 控制器）、`entity/`（43 个 JPA 实体）、`repository/`（39 个仓库）、`service/`+`service/impl/`（26 对接口-实现）、`security/`（5 类）、`vo/`（60+ 视图对象）、`params/`、`util/`、`seed/`。

### 3.2 数据访问层

- **JPA + Hibernate**，`spring-boot-starter-data-jpa`（`server/pom.xml:71-73`）；DDL 策略 `ddl-auto: update`（`server/src/main/resources/application.yml:24-25`）——新表/新列由实体注解自动建，无需迁移脚本。
- **默认 MySQL**，演示环境可切 SQLite：`application.yml:10-20` 用 `DB_DRIVER`/`DB_DIALECT`/`DB_URL` 环境变量切换；`mysql-connector-j` + `sqlite-jdbc` 双驱动并存（`server/pom.xml:50-60`）。
- **仓库基线**：`JpaRepository<T,Long>` + `JpaSpecificationExecutor<T>`（例：`CustomerRepository.java:9`）。
- **无外键约束**：实体间不用 DB 外键，靠业务 id 字段在服务层关联（`docs/server/data-model.md:5`）。
- **审计字段**：全局 `@EnableJpaAuditing`（`PersistenceConfig.java:6-7`），实体挂 `@EntityListeners(AuditingEntityListener.class)` + `@CreatedDate/@LastModifiedDate`（例：`CardLookupLogEntity.java:27-57`）。
- **插入省略策略**：`@DynamicInsert` 配合 `columnDefinition = "integer default 0"`，种子路径不必逐字段 set（`CustomerEntity.java:14-50`）。

### 3.3 鉴权

- **Spring Security + JWT**：`spring-boot-starter-security`（`server/pom.xml:76-78`）+ `jjwt 0.13.0` 三件套（`server/pom.xml:62-70`）。
- **过滤器链**：`JwtRequestFilter extends OncePerRequestFilter`（`security/JwtRequestFilter.java:24-39`），从 `Authorization: Bearer` 解析 JWT，校验后写入 `SecurityContextHolder` 与会话属性。
- **放行清单**：`SecurityConfig.java:51-64` 用 `requestMatchers(...).permitAll()` 列举白名单（Swagger、`/api/v1/ads/playlist`、`/api/v1/calls/latest`、`/api/v1/kiosk/**`、`/api/v1/print/templates`、`/media/**`、`/actuator/**`、登录接口），其余 `.anyRequest().authenticated()`（`SecurityConfig.java:65`）。
- **角色与权限**：`Role` 枚举 `USER/ADMIN/MANAGER`（`security/Role.java:16-38`），`ADMIN` 含全部权限；`Permission` 枚举 `admin:read/update/create/delete` + `management:*`（`security/Permission.java:7-15`）。
- **方法级鉴权**：`@EnableMethodSecurity`（`SecurityConfig.java:25`）；现有用法仅 `@PreAuthorize("hasRole('ADMIN')")`（`StaffController.java:72,156`），无 `hasAuthority` 细粒度用法。
- **密码**：`BCryptPasswordEncoder`（`config/EncryptConfig.java:10-13`）。

### 3.4 已有微信触点（本模块要衔接的既有资产）

- **小程序凭据已落地**：`application.yml:51-53` 的 `sinomed.wechat.app-id` / `app-secret`（env `WECHAT_APP_ID`/`WECHAT_APP_SECRET`，缺省空串=未配置）。
- **`WechatConfig`** 仅装配一个 `RestTemplate` bean（`config/WechatConfig.java:11-17`），注释明确「便于测试用 MockRestServiceServer 绑定替身」。
- **`WechatClient` 只覆盖 jscode2session**（`service/WechatClient.java:19-55`）：`WECHAT_APP_ID`/`WECHAT_APP_SECRET` 未配置时抛 `IllegalArgumentException` 由全局异常透 400（不静默）；`session_key` 不出服务端。这是公众号模块**唯一已存在的微信 HTTP 出口**——公众号接入要新增出口，不能复用这个 bean（不同 appid 体系）。
- **顾客档案已有一列 openid**：`CustomerEntity.java:36-38` 的 `openid` 是**小程序 openid**（unique、可空），配 `idx_customer_openid` 索引（`CustomerEntity.java:20`）；查询入口 `CustomerRepository.java:15` 的 `findByOpenid`。**这列不是公众号 openid**，两者属不同微信 appid 体系、值不同，必须分开存（详见 §4.7）。
- **openid 绑定路径已实装**：`KioskController.java:308-331` 的 `/openid-session`（jscode2session 换 openid）与 `:352-397` 的 `/openid-bind`（openid + 手机号 + 一次性短信验证码自证后绑定到顾客档案），口径由 `sinomed.phone-lookup.mode`（`off`/`openid_only`/`sms_verified`，默认 `off`，`application.yml:48-49`）控制。
- **审计模式已有样板**：`CardLookupLogEntity.java:17-57`——每次尝试（含拒绝）落一条，记录「查了谁的卡（phone/customerId）、谁查的（operator）、什么模式（mode）、结果（result）」；`KioskController.java:383-397` 展示写入模式。公众号绑定沿用同一审计口径。
- **顾客标签已有表**：`CustomerTagEntity.java:18-44`（`customer_tags`，唯一约束 `(customer_id, tag)`），是公众号粉丝标签在本仓的**复用锚点**——粉丝标签不另建表，经 `wx_fans.customer_id` 关联查询（见 §4.6）。

---

## 4. 数据模型设计

遵循本仓纪律：JPA 实体 + `ddl-auto: update` 自动建表、无 DB 外键（业务 id 关联）、`@CreatedDate/@LastModifiedDate` 审计字段（见 §3.2）。所有表带 `account_id` 维度但第一期恒为单账号。

### 4.1 wx_accounts — 公众号账号

| 字段 | 类型 | 含义 |
| ---- | ---- | ---- |
| id | Long | 主键 |
| app_id | String | AppID（唯一） |
| app_secret | String | AppSecret（**加密落库**，不复用 `EncryptConfig` 的 BCrypt——那是单向哈希；用 AES 可逆加密，密钥走 env） |
| account_name | String | 账号名称（展示用） |
| account_type | String | `service`（服务号）/ `subscription`（订阅号） |
| verified | Integer | 微信认证：1 已认证 / 0 未认证（决定能力矩阵，见 §6） |
| token | String | 消息推送 Token（公众号后台配置的服务器验证令牌） |
| aes_key | String | EncodingAESKey（43 字符，消息加解密密钥；**加密落库**） |
| encrypt_mode | String | `plaintext`（明文）/ `compatible`（兼容）/ `safe`（安全）——对齐公众号后台三种消息加密模式 |
| enabled | Integer | 1 启用 / 0 停用（停用后回调直接拒绝） |
| remark | String | 备注 |
| create_time / update_time | Date | 审计时间 |

唯一约束 `app_id`；索引 `enabled`。第一期**配置入口**：优先直接写库（管理端表单）或 env 覆盖，`WechatClient` 现有「未配置明确拒绝」口径沿用（不静默跳过）。

### 4.2 wx_fans — 公众号粉丝（核心，桥接顾客档案）

| 字段 | 类型 | 含义 |
| ---- | ---- | ---- |
| id | Long | 主键 |
| account_id | Long | 公众号 id（wx_accounts.id，业务外键） |
| openid | String | **公众号 openid**（同账号内唯一；与 `customers.openid` 的小程序 openid **不同体系、值不同**，见 §4.7） |
| unionid | String | 开放平台 unionid（可空；仅当公众号与小程序绑到同一开放平台账号下才有，是跨端自动关联的唯一凭据） |
| nickname | String | 昵称 |
| head_img_url | String | 头像 URL |
| subscribe | Integer | 1 已关注 / 0 已取消关注（取消关注保留行，供回归分析） |
| subscribe_time | Date | 关注时刻（事件推送 `subscribe` 时写入；取关时写 unsubscribe_time） |
| unsubscribe_time | Date | 取关时刻（可空） |
| **customer_id** | Long | **顾客档案 id（可空；绑定后写入，见 §4.7）** |
| remark | String | 运营备注 |
| create_time / update_time | Date | 审计时间 |

唯一约束 `(account_id, openid)`；索引 `customer_id`、`unionid`。

### 4.3 wx_menus — 自定义菜单（第一期建表不建 UI）

| 字段 | 类型 | 含义 |
| ---- | ---- | ---- |
| id | Long | 主键 |
| account_id | Long | 公众号 id |
| parent_id | Long | 父菜单 id（顶级为空；最多两级，微信协议上限） |
| name | String | 菜单名（微信限制：一级 ≤ 4 汉字、二级 ≤ 8 汉字，服务端校验） |
| menu_type | String | `view`/`click`/`scancode_push`/`scancode_waitmsg`/`pic_sysphoto`/`pic_photo_or_album`/`pic_weixin`/`location_select`/`view_miniprogram` |
| url | String | `view` 类型跳转 URL |
| media_id | String | 素材类菜单的 media_id |
| miniprogram_appid / miniprogram_pagepath | String | `view_miniprogram` 的小程序跳转参数（**可指向本仓小程序**） |
| event_key | String | `click` 类型的事件 key（回调时透传，用于路由） |
| sort | Integer | 展示顺序 |
| enabled | Integer | 1 启用 / 0 草稿（草稿态不推送微信） |
| create_time / update_time | Date | 审计时间 |

索引 `(account_id, parent_id)`。参考 anchel 的「个性化菜单」设计，**第一期不做**（单店场景无人群差异化菜单需求），表结构不预留。

### 4.4 wx_auto_replies — 自动回复规则

| 字段 | 类型 | 含义 |
| ---- | ---- | ---- |
| id | Long | 主键 |
| account_id | Long | 公众号 id |
| rule_type | String | `subscribe`（关注欢迎语，一账号一条生效）/ `keyword`（关键词回复）/ `fallback`（兜底回复，一账号一条） |
| keyword | String | 关键词（`rule_type=keyword` 时非空） |
| match_type | String | `exact`（全等）/ `contains`（包含） |
| reply_type | String | `text` / `image` / `news`（图文，media_id 引用 wx_materials） |
| content | TEXT | 文本内容；素材类存 media_id |
| priority | Integer | 匹配优先级，小者先试 |
| enabled | Integer | 1 启用 / 0 停用 |
| create_time / update_time | Date | 审计时间 |

索引 `(account_id, rule_type, enabled)`。同账号同 `rule_type=subscribe`/`fallback` 各最多一条生效（服务层约束，不加 DB 唯一约束——避免草稿态冲突）。

### 4.5 wx_materials — 素材台账

| 字段 | 类型 | 含义 |
| ---- | ---- | ---- |
| id | Long | 主键 |
| account_id | Long | 公众号 id |
| media_id | String | 微信侧 media_id（唯一键之一） |
| material_type | String | `image`/`voice`/`video`/`news`（图文）/`temp`（临时素材） |
| name | String | 素材名 |
| url | String | 微信返回的访问 URL（临时素材有时效，仅记录不依赖） |
| local_path | String | 本仓本地落盘路径（复用 `sinomed.ads.upload-dir` 同款挂载思路，`application.yml:39-41`） |
| size / mime_type | Long / String | 文件元信息 |
| create_time / update_time | Date | 审计时间 |

唯一约束 `(account_id, media_id)`。第一期**只做消息内临时素材的拉取落日志**（收到图片消息时下载 PicUrl 到 `local_path`，供管理端查看），不做上传/管理 UI。

### 4.6 粉丝标签：不建表，复用 customer_tags

粉丝标签（公众号侧的「用户标签」）**不另建表**：本仓已有 `customer_tags`（`CustomerTagEntity.java:18-44`，同顾客同标签唯一），粉丝标签经 `wx_fans.customer_id` 关联到顾客后直接查询顾客标签。理由：

1. 医馆运营关心的是「这个粉丝对应哪位顾客、有什么标签」，不是微信侧独立的粉丝标签体系；
2. 未绑定顾客的粉丝（无 `customer_id`）暂无标签需求——第一期连绑定都是引导式，未绑定粉丝的管理价值低；
3. 不产生两套并行标签实体（对齐 salon-extension.md §6 红线）。

若后续确需微信侧标签（群发按微信标签定向），再补 `wx_fan_tags` 表，属第三期以后。

### 4.7 与顾客/会员实体的关系（关键设计决策）

**两个 openid 是不同体系**：

```text
小程序（已有）                          公众号（新增）
customers.openid  ←—— jscode2session   wx_fans.openid   ←—— 消息回调 FromUserName
   （小程序 openid，unique）                （公众号 openid，per-account 唯一）
        │                                        │
        │      同一开放平台下 unionid 才相等       │
        └──────────────┬─────────────────────────┘
                       ▼
                 customers.id（顾客档案）
                  ↗ 经 wx_fans.customer_id 桥接
```

- **不合并**：`customers.openid` 保持小程序语义不动（`CustomerEntity.java:36-38`、`docs/server/data-model.md:18`），公众号 openid 存 `wx_fans.openid`。
- **绑定路径（第一期，显式）**：公众号侧引导顾客绑定 → 复用 Kiosk 已验证的「手机号 + 一次性短信验证码双因子」口径（`KioskController.java:352-397` 的 `/openid-bind` 模式）：粉丝点菜单/回复关键词进入 H5 → 输入手机号 + 短信码 → 服务端校验后写 `wx_fans.customer_id` → 每次尝试（含拒绝）落 `wx_binding_logs`（§4.8）。
- **自动关联路径（后续，需开放平台）**：若公众号与小程序绑定同一微信开放平台账号，消息回调携带的 openid 可换 unionid，与 `wx_fans.unionid` 或小程序侧 unionid 比对自动关联——**第一期不做**（单店通常不为此单独开开放平台主体），设计只保留 `unionid` 字段。
- **解除绑定**：管理端可解绑（清 `customer_id` + 审计）；顾客取关不自动解绑（保留行、`subscribe=0`，再关注后 `customer_id` 仍在——换人关注同一 openid 属线下场景，走管理端人工处理）。

### 4.8 wx_binding_logs — 粉丝绑定审计

沿用 `card_lookup_logs` 口径（`CardLookupLogEntity.java:17-57`）：每次尝试（含拒绝）落一条。

| 字段 | 类型 | 含义 |
| ---- | ---- | ---- |
| id | Long | 主键 |
| account_id | Long | 公众号 id |
| fan_openid | String | 发起绑定的粉丝 openid |
| phone | String | 被绑定顾客手机号（审计：绑了谁） |
| customer_id | Long | 命中的顾客 id（未命中为空） |
| result | String | `ok` / `missing_code` / `bad_code` / `expired_code` / `used_code` / `no_customer` / `bound_other`（该粉丝已绑其他顾客）/ `conflict`（该顾客已被其他粉丝绑定） |
| create_time | Date | 时间 |

索引 `fan_openid`、`phone`。**不共用 `card_lookup_logs`**：那是「查卡」语义，这里是「绑定」语义，混表会让 result 枚举膨胀且查询意图不清——同口径、不同表。

---

## 5. 消息接入流程

> 来源：[服务器配置](https://developers.weixin.qq.com/doc/offiaccount/Getting_Started/服务器配置.html)、[接收普通消息](https://developers.weixin.qq.com/doc/offiaccount/Basic_Information/接收普通消息.html)、[消息加解密说明](https://developers.weixin.qq.com/doc/offiaccount/Basic_Information/消息加解密说明.html)、[被动回复用户消息](https://developers.weixin.qq.com/doc/offiaccount/Basic_Information/被动回复用户消息.html)、[接收事件推送](https://developers.weixin.qq.com/doc/offiaccount/Basic_Information/接收事件推送.html)

### 5.1 回调 URL 与放行

- **路径**：`GET/POST /api/v1/wx/callback/{accountId}`（`accountId` 对应 `wx_accounts.id`；单账号场景便于日后扩多账号，无需改 URL）。
- **免登录放行**：微信服务器 POST 无 JWT，须加入 `SecurityConfig.java:51-64` 的 permitAll 清单：`"/api/v1/wx/callback/**"`。这是本模块唯一的新放行项——管理端其余接口仍走 JWT。
- **来源可信靠签名**，不靠登录态：见 §5.2。
- **实例约束**：WxJava 的 `WxMpMessageRouter` 须按 `accountId` 实例化并缓存（`WxMpMultiServices` 思路），不能单例复用多账号配置。

### 5.2 签名验证（两种场景，同一算法）

算法：`signature = SHA1(sort(token, timestamp, nonce))` 字典序拼接后 SHA1-Hex。

| 场景 | 触发 | 校验内容 | 成功返回 |
| ---- | ---- | -------- | -------- |
| **URL 验证（GET）** | 公众号后台保存服务器配置时 | `signature/timestamp/nonce`，`echostr` 原样返回 | `echostr` 明文 |
| **消息推送（POST）** | 每次用户发消息/事件 | `msg_signature`（密文模式= `SHA1(token+timestamp+nonce+encrypt)`），验签后再解密 | 见 §5.4 |

验签失败一律返回 200 + 空 body（微信要求异常时也不能回 5xx，否则持续重试 3 次后放弃），同时落审计日志。

### 5.3 加解密（AES-256-CBC）

- **明文模式**（`encrypt_mode=plaintext`）：POST body 即明文 XML，验签后直接解析。
- **兼容/安全模式**（`compatible`/`safe`）：body 是 `<xml><Encrypt>…</Encrypt></xml>`，流程：`msg_signature` 验签 → Base64 解码 → 取 16 字节随机串 + 4 字节 msg_len + 明文 XML + receiveid（`Base64(AES-256-CBC(随机串+XML))`，密钥 = `Base64Decode(EncodingAESKey + "=")`，IV = 密钥前 16 字节）→ PKCS7 反填充得明文 XML。
- **实现归属**：全套（验签、AES、PKCS7、XML 绑定）由 WxJava `WxMpCryptUtil` / `WxMpXmlMessage` 承担，本模块只做「取 `aes_key` → 调 SDK → 拿到结构化消息」，不手写密码学代码。
- **被动回复加密**：安全模式下回复体也须加密（同 key），SDK 同一工具类处理。

### 5.4 消息接收与分发

```text
微信服务器
  │ POST /api/v1/wx/callback/{accountId}（XML）
  ▼
WxCallbackController
  ├─ 1. 按 accountId 取 wx_accounts（停用/未配置 → 200 "success" 拒处理 + 审计）
  ├─ 2. 验签（§5.2）+ 解密（§5.3）→ WxMpXmlMessage
  ├─ 3. 排重：MsgId（消息）或 EventKey+FromUserName+CreateTime（事件）唯一键落库，
  │      重复直接 200 "success"（微信 5s 超时会重试，总共 3 次）
  ├─ 4. 落 wx_message_logs（direction=in，全量）
  ├─ 5. 路由（WxMpMessageRouter，同步规则部分 5s 内完成）：
  │      ├─ event=subscribe   → wx_fans.upsert(subscribe=1) + 订阅欢迎语规则
  │      ├─ event=unsubscribe → wx_fans.subscribe=0（不回复，微信不收）
  │      ├─ event=CLICK/VIEW  → 菜单事件（第一期仅落日志）
  │      ├─ msg=text          → wx_auto_replies 关键词匹配（exact→contains，priority）
  │      └─ msg=image/voice/… → 落日志 + 兜底回复（不做语音识别/图片处理）
  └─ 6. 回复：被动回复 XML（5s 内）或客服消息接口（异步，48h 窗口内）
```

**5 秒约束**（官方：5 秒收不到响应会断连并重试，共 3 次；无需回复时直接回 `"success"` 空串）：

- 规则匹配 + 被动回复同步完成（纯内存/本地库操作，5s 富余）；
- 素材下载、顾客绑定等慢操作**不阻塞**回复线程：先回 `"success"`，异步线程处理，需要触达用户时走客服消息接口；
- 全部回复（被动 + 客服）也落 `wx_message_logs`（`direction=out`），构成完整会话记录。

### 5.5 消息类型支持矩阵（第一期）

| 类型 | 接收 | 被动回复 | 说明 |
| ---- | ---- | -------- | ---- |
| text | ✅ | ✅ | 关键词匹配主体 |
| image | ✅ | ✅（回固定文案） | PicUrl 拉取落 `wx_materials.local_path` |
| voice | ✅ | ✅（回固定文案） | 落日志，Format/MediaId 记录，不做识别 |
| video / shortvideo | ✅ | ✅（回固定文案） | 落日志 |
| link / location | ✅ | ✅（回固定文案） | 落日志 |
| event: subscribe / unsubscribe | ✅ | 欢迎语 / 不回复 | 粉丝增删主入口 |
| event: CLICK / VIEW | ✅ | 落日志 | 菜单事件，第一期不做业务路由 |
| event: 其他（scan 等） | ✅ | 落日志 | 不识别的事件统一落日志不报错 |

**设计原则**：未识别类型永远「落日志 + 静默/兜底」，不抛异常——微信侧的回调重试会放大错误。

---

## 6. 权限边界（账号类型 × 能力矩阵与降级）

> 来源：[服务号介绍](https://developers.weixin.qq.com/doc/offiaccount/Getting_Started/服务器配置.html)（服务号/公众号文档已分列）、[自定义菜单介绍](https://developers.weixin.qq.com/doc/offiaccount/Custom_Menus/自定义菜单介绍.html)、[素材管理](https://developers.weixin.qq.com/doc/offiaccount/Asset_Management/素材管理.html)；账号能力差异亦参考 vluma MCP 对三类账号的边界划分（见 §2 对比表）。

### 6.1 能力矩阵

| 能力 | 服务号 | 认证订阅号 | 未认证订阅号 |
| ---- | ------ | ---------- | ------------ |
| 消息回调（接收 + 被动回复） | ✅ | ✅ | ✅ |
| 客服消息（48h 窗口） | ✅ | ✅ | ❌ |
| 自定义菜单 | ✅ | ✅ | ❌（后台手动配置亦受限） |
| 素材管理 | ✅ | ✅ | ✅（上传/读取） |
| 群发（订阅通知/一次性订阅消息） | ✅（模板消息/订阅通知） | ✅（订阅通知，一天一条） | ❌ |
| 模板消息 | ✅ | ❌（订阅号无模板消息） | ❌ |
| 网页授权（snsapi_base/userinfo） | ✅ | ✅ | ✅ |
| 带参二维码 | ✅ | ✅ | ✅ |

> 边界判定以官方文档当前口径为准；本表是**产品设计口径**，实施时以 `wx_accounts.account_type + verified` 两字段驱动，不写死枚举判断。

### 6.2 未认证订阅号的降级策略（要求点）

能力探测不写死在代码分支，而是**统一由 `WxCapabilities` 服务返回**：入参 `wx_accounts` 行，出参该账号可用能力集合。前端（web 管理端）首次加载时拉取能力集，据此渲染：

| 入口 | 服务号/认证订阅号 | 未认证订阅号 |
| ---- | ----------------- | ------------ |
| 自定义菜单页 | 可编辑可推送 | **入口隐藏**，账号设置页提示「未认证订阅号不支持自定义菜单」 |
| 群发/模板消息 | 可用 | **入口隐藏**，同上提示 |
| 客服消息回复 | 可用（48h） | **入口隐藏**，消息工作台仅被动回复模式 |
| 素材管理 | 可用 | 可用 |
| 消息收发 / 顾客关联 | 可用 | 可用（本模块第一期全部价值在此） |

降级提示样式与现有管理端一致（禁用态 + tooltip），不做二次确认弹窗——能力缺失是**账号属性**，不是操作风险。

### 6.3 管理端操作权限（本仓角色）

复用现有 `Role/Permission` 体系（`security/Role.java:16-38`、`Permission.java:7-15`），**不新增权限枚举**（现网只有 `ADMIN`/`MANAGER` 两档有效）：

- 公众号账号配置（含 AppSecret/Token/AESKey 读写）：`ADMIN`（对齐 `StaffController.java:72` 的 `@PreAuthorize("hasRole('ADMIN')")` 用法）；
- 菜单/自动回复/素材/消息工作台：`MANAGER` 及以上；
- 回调接口：免登录（§5.1），靠签名验证。

**AppSecret 不回显**：管理端查询接口只返回掩码（`****`），修改时留空=不更新——对齐 `WechatClient.java:15-16`「凭据不下发前端」口径。

---

## 7. 配置与凭据

沿用 `sinomed.*` 配置段风格（`application.yml:38-53`）：

```yaml
sinomed:
  # 微信小程序（已有，不动）
  wechat:
    app-id: ${WECHAT_APP_ID:}
    app-secret: ${WECHAT_APP_SECRET:}
  # 公众号（新增；第一期单账号，与小程序凭据分离，不同 appid 体系）
  official-account:
    enabled: ${WX_OA_ENABLED:false}            # 缺省 false=模块整体关闭，回调入口返回 200 拒处理
    account-id: ${WX_OA_ACCOUNT_ID:1}          # 第一期恒为 1，多账号预留
    token: ${WX_OA_TOKEN:}                     # 服务器配置 Token
    aes-key: ${WX_OA_AES_KEY:}                 # EncodingAESKey（43 字符）
    encrypt-mode: ${WX_OA_ENCRYPT_MODE:plaintext}  # plaintext/compatible/safe
    # app_id/app_secret 走 wx_accounts 表（加密落库），不走 env——凭据属业务数据，可后台改
```

- **`enabled=false` 是默认值**：与 `sinomed.phone-lookup.mode` 默认 `off`（`application.yml:49`）同款「不静默」口径——模块未启用时回调返回 200 `"success"` 并落审计，不让微信侧无限重试；
- **token/aes_key 走 env 是第一期妥协**：单店部署时免建库配置；第二期管理端表单落库后，env 退化为仅开发/演示用；
- **凭据不入库明文**：`wx_accounts.app_secret`/`aes_key` 用 AES 可逆加密（密钥走 env `WX_OA_CREDENTIAL_KEY`），BCrypt 单向不可用（凭据需回读调用 API）。

---

## 8. 分期计划

### 第一期：最小可用 ——「接入 + 消息收发 + 关联顾客」

**目标**：顾客在公众号发消息，医馆能看到是谁（若已绑定顾客档案）、能自动回复；管理端能看会话流水。不做任何管理 UI 的增删改查——配置走 env + 种子数据，规则表先手工/SQL 维护。

| # | 交付物 | 说明 |
| --- | ------ | ---- |
| 1 | `wx_accounts` + env 配置 + 回调 Controller | `GET` 验签回 `echostr`；`POST` 验签、解密、排重、落日志、回 `"success"`/被动回复 |
| 2 | `wx_fans` upsert | `subscribe`/`unsubscribe` 事件维护粉丝行 |
| 3 | 自动回复（读规则表） | `subscribe` 欢迎语 + `keyword` 匹配 + `fallback`，规则表先手工维护 |
| 4 | `wx_message_logs` 全量收发流水 | 管理端只读列表（按粉丝/时间/类型过滤），不做对话式 UI |
| 5 | 粉丝 ↔ 顾客绑定 | H5 授权拿 openid → 手机号 + 短信验证码双因子（复用 `sms_codes` 与 Kiosk 口径，`KioskController.java:352-397`）→ 写 `wx_fans.customer_id` + `wx_binding_logs` 审计 |
| 6 | 能力探测接口 `WxCapabilities` | 返回账号能力集（§6.2），第一期前端只用它决定隐藏哪些入口 |

**验收口径**：未绑定粉丝发「营业时间」→ 收到关键词回复；已绑定顾客发消息 → 消息流水可见对应顾客姓名；关注事件 → 欢迎语；所有收发（含被拒验签）都有日志。

### 第二期：运营工具化 ——「菜单 + 素材」

- 自定义菜单管理页（编辑/草稿/推送微信），`wx_menus` 已在第一期建好；
- 素材上传与管理（永久素材台账 + 本地落盘），支撑菜单与自动回复引用；
- 管理端规则编辑 UI（自动回复不再手工改库）；
- 消息工作台升级为对话式视图 + 人工客服回复（48h 窗口，服务号/认证订阅号）。

### 第三期：触达 ——「带参二维码 + 群发/模板消息」

- 带参二维码（场景值绑定顾客来源，扫码即建粉丝 + 预填 `customer_id`）；
- 服务号模板消息（预约提醒、复诊提醒——对接现有 `appointments`/`review` 计划）；
- 订阅号群发（认证订阅号一天一条的约束在 UI 层强提示）；
- 粉丝标签（若需微信侧定向）再评估是否建 `wx_fan_tags`。

**依赖关系**：第三期的模板消息/群发**强依赖账号类型**（§6.1），单店若只持有未认证订阅号，第三期永久搁置——这不影响第一、二期价值（消息收发与菜单/素材与账号类型无关）。
