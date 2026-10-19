# Official Account Module Design (Service / Subscription Account Integration)

> 🌐 **English** ｜ [中文](wechat-official-account.md)

> Status: **design draft (unscheduled)**. This page answers three things: how official-account capabilities integrate into this repo (choice: option a, a thin in-house wrapper over the WxJava SDK), how the data model connects to the customer/membership entities (official-account fans ≠ mini-program openids, bridged via `wx_fans.customer_id`), and how the phases are cut (phase 1 does "integration + message send/receive + customer linking" only).

---

## 1. Positioning and Scope

The WeChat official account is the clinic's second online channel to reach customers (the first is the mini program, already implemented). It is positioned as an **operations support module**, not part of the diagnosis or billing flow:

- **Do**: message send/receive (passive replies), auto-reply rules, customer-record linking, custom menus, material management, (service accounts) template messages and mass messaging;
- **Don't**: WeChat Pay (keep the existing billing payment conventions, see README "Planned Features"), multi-store account matrices, mini-program embedded official-account components, content-compliance review tooling.

Single-store convention: **one official account** by default; table structures reserve an `account_id` dimension, but phase-1 UI exposes only single-account configuration.

---

## 2. Open-source Option Comparison

| Option | Stars | License | Stack | Coverage | Fit for this repo |
| ------ | ----- | ------- | ----- | -------- | ----------------- |
| [binarywang/WxJava](https://github.com/binarywang/WxJava) | 33.2k | Apache-2.0 | Java SDK (incl. `wx-java-mp-spring-boot-starter`) | Full API wrappers for official accounts / mini programs / WeCom / WeChat Pay | **Highest**: same Java/Spring Boot stack; bring the SDK only, not the architecture; this repo writes the thin wrapper |
| [niefy/wx-manage](https://github.com/niefy/wx-manage) (frontend) + [wx-api](https://github.com/niefy/wx-api) (backend) | 1.6k | Apache-2.0 | Vue2 + ElementUI; Spring Boot + **Shiro** + MyBatis-Plus + WxJava | Menus, auto-replies, materials, simple CMS, template messages, fans, parametric QR codes | **Low**: repo archived; Shiro conflicts with this repo's Spring Security, MyBatis-Plus with its JPA — bringing the whole thing means dual auth and dual ORM |
| [anchel/wechat-official-account-admin](https://github.com/anchel/wechat-official-account-admin) | 41 | MIT | Go + MongoDB + Redis | Most complete: multiple accounts, message send/receive (plaintext/ciphertext), custom menus incl. personalized, materials, QR codes, user tags | **Design reference only**: Go version not directly usable; MongoDB/Redis not in this repo; its data model and "personalized menu" interaction design are worth borrowing |
| [vluma/wechat_official_account_mcp](https://github.com/vluma/wechat_official_account_mcp) | 3 | **No license (none declared)** | Python MCP | Materials/drafts/publish; explicitly distinguishes permission boundaries across service / verified subscription / unverified subscription | **Docs reference only**: MCP form doesn't fit this repo's server; reusability questionable without a license; its account-type permission matrix (§6) has reference value |

> Data source: GitHub API, measured 2026-10-19 (stars, license and archived flags all from each repo's API response).

**Conclusion: option a (bring in the WxJava SDK directly, write a thin in-house wrapper)**, because:

1. **Same tech stack**: this repo is Java 21 + Spring Boot (`server/pom.xml:5-8`); WxJava is a pure Java SDK that binds to Spring Boot config via `wx-java-mp-spring-boot-starter`; wx-manage's Shiro/MyBatis-Plus directly conflict with this repo's Spring Security/JPA.
2. **Minimal dependencies**: one Maven dependency + one config block; no new process/middleware (option b means running a second backend, option c brings MongoDB/Redis).
3. **Best controllability**: callback routing, customer binding, and audit logging are all strong repo conventions (§3, §5) that must be written in-house; the SDK only replaces the undifferentiated plumbing of "HTTP + XML + AES + access_token management".
4. **Activity**: WxJava still releases as of 2026-10 (repo API `pushed_at=2026-10-17`); wx-manage is archived.

---

## 3. Current State of the Repo (file:line)

### 3.1 Module Layout and Tech Stack

- **Single-module backend**, no aggregate parent pom: `server/pom.xml:1-4`, artifactId `sinomed`, version `0.1.0-SNAPSHOT`; CI builds the single module only (`.github/workflows/maven.yml:29`).
- **Java 21 + Spring Boot**: `server/pom.xml:25-27` (`java.version=21`, parent is `spring-boot-starter-parent`).
- **Package layout of 10 layers** (`server/src/main/java/com/sinomed/`): `config/` (11 config classes), `controller/` (30 REST controllers), `entity/` (43 JPA entities), `repository/` (39 repositories), `service/` + `service/impl/` (26 interface-impl pairs), `security/` (5 classes), `vo/` (60+ view objects), `params/`, `util/`, `seed/`.

### 3.2 Data Access Layer

- **JPA + Hibernate**, `spring-boot-starter-data-jpa` (`server/pom.xml:71-73`); DDL strategy `ddl-auto: update` (`server/src/main/resources/application.yml:24-25`) — new tables/columns are created automatically from entity annotations, no migration scripts.
- **MySQL by default**, switchable to SQLite for demos: `application.yml:10-20` switches via `DB_DRIVER`/`DB_DIALECT`/`DB_URL` env vars; `mysql-connector-j` + `sqlite-jdbc` both present (`server/pom.xml:50-60`).
- **Repository baseline**: `JpaRepository<T,Long>` + `JpaSpecificationExecutor<T>` (e.g. `CustomerRepository.java:9`).
- **No DB foreign keys**: entities don't use DB FKs; business id fields relate them at the service layer (`docs/server/data-model.md:5`).
- **Audit fields**: global `@EnableJpaAuditing` (`PersistenceConfig.java:6-7`); entities carry `@EntityListeners(AuditingEntityListener.class)` + `@CreatedDate/@LastModifiedDate` (e.g. `CardLookupLogEntity.java:27-57`).
- **Insert-omit strategy**: `@DynamicInsert` with `columnDefinition = "integer default 0"`, so seed paths needn't set every field (`CustomerEntity.java:14-50`).

### 3.3 Authentication

- **Spring Security + JWT**: `spring-boot-starter-security` (`server/pom.xml:76-78`) + the `jjwt 0.13.0` trio (`server/pom.xml:62-70`).
- **Filter chain**: `JwtRequestFilter extends OncePerRequestFilter` (`security/JwtRequestFilter.java:24-39`) parses the JWT from `Authorization: Bearer`, validates it, and writes to `SecurityContextHolder` and the session.
- **Permit list**: `SecurityConfig.java:51-64` enumerates `requestMatchers(...).permitAll()` entries (Swagger, `/api/v1/ads/playlist`, `/api/v1/calls/latest`, `/api/v1/kiosk/**`, `/api/v1/print/templates`, `/media/**`, `/actuator/**`, login); everything else `.anyRequest().authenticated()` (`SecurityConfig.java:65`).
- **Roles and permissions**: `Role` enum `USER/ADMIN/MANAGER` (`security/Role.java:16-38`); `ADMIN` holds all permissions; `Permission` enum `admin:read/update/create/delete` + `management:*` (`security/Permission.java:7-15`).
- **Method-level auth**: `@EnableMethodSecurity` (`SecurityConfig.java:25`); the only existing use is `@PreAuthorize("hasRole('ADMIN')")` (`StaffController.java:72,156`); no fine-grained `hasAuthority` usage.
- **Passwords**: `BCryptPasswordEncoder` (`config/EncryptConfig.java:10-13`).

### 3.4 Existing WeChat Touchpoints (assets this module must connect to)

- **Mini-program credentials landed**: `application.yml:51-53` `sinomed.wechat.app-id` / `app-secret` (env `WECHAT_APP_ID`/`WECHAT_APP_SECRET`, empty default = unconfigured).
- **`WechatConfig`** only wires a `RestTemplate` bean (`config/WechatConfig.java:11-17`), commented for `MockRestServiceServer` stubbing in tests.
- **`WechatClient` covers jscode2session only** (`service/WechatClient.java:19-55`): when `WECHAT_APP_ID`/`WECHAT_APP_SECRET` are missing it throws `IllegalArgumentException` surfaced as 400 by the global handler (no silence); `session_key` never leaves the server. This is the **only existing WeChat HTTP egress** — official-account integration needs a new egress and must not reuse this bean (different appid system).
- **The customer record already has an openid column**: `CustomerEntity.java:36-38` `openid` is the **mini-program openid** (unique, nullable), with the `idx_customer_openid` index (`CustomerEntity.java:20`); lookup via `CustomerRepository.java:15` `findByOpenid`. **This is not the official-account openid** — different appid systems, different values, must be stored separately (§4.7).
- **The openid binding path is implemented**: `KioskController.java:308-331` `/openid-session` (jscode2session exchange) and `:352-397` `/openid-bind` (openid + phone + one-time SMS code self-binding to the customer record), governed by `sinomed.phone-lookup.mode` (`off`/`openid_only`/`sms_verified`, default `off`, `application.yml:48-49`).
- **Audit pattern has a template**: `CardLookupLogEntity.java:17-57` — one row per attempt (including rejections), recording "whose card was looked up (phone/customerId), who looked (operator), which mode, which result"; the write pattern at `KioskController.java:383-397`. Official-account binding follows the same audit convention.
- **Customer tags table exists**: `CustomerTagEntity.java:18-44` (`customer_tags`, unique `(customer_id, tag)`) — the **reuse anchor** for fan tags; fan tags get no separate table, queried through `wx_fans.customer_id` (§4.6).

---

## 4. Data Model

Follows repo discipline: JPA entities + `ddl-auto: update` auto-tables, no DB FKs (business id relations), `@CreatedDate/@LastModifiedDate` audits (§3.2). Every table carries `account_id` but phase 1 is single-account.

### 4.1 wx_accounts — Official Account

| Column | Type | Meaning |
| ------ | ---- | ------- |
| id | Long | PK |
| app_id | String | AppID (unique) |
| app_secret | String | AppSecret (**encrypted at rest** — not `EncryptConfig`'s BCrypt, that's one-way; use reversible AES with the key from env) |
| account_name | String | Display name |
| account_type | String | `service` / `subscription` |
| verified | Integer | WeChat verification: 1 verified / 0 not (drives the capability matrix, §6) |
| token | String | Message-push Token (server verification token configured in the WeChat console) |
| aes_key | String | EncodingAESKey (43 chars, message crypto key; **encrypted at rest**) |
| encrypt_mode | String | `plaintext` / `compatible` / `safe` — mirrors the console's three message-encryption modes |
| enabled | Integer | 1 on / 0 off (off → callbacks refuse processing) |
| remark | String | Notes |
| create_time / update_time | Date | Audit times |

Unique on `app_id`; index on `enabled`. Phase-1 **configuration entry**: write the DB directly (admin form) or env override; `WechatClient`'s "explicit rejection when unconfigured" convention carries over (no silent skip).

### 4.2 wx_fans — Official Account Fans (core, bridges customer records)

| Column | Type | Meaning |
| ------ | ---- | ------- |
| id | Long | PK |
| account_id | Long | Account id (wx_accounts.id, business FK) |
| openid | String | **Official-account openid** (unique per account; a **different system and different values** from the mini-program openid in `customers.openid`, §4.7) |
| unionid | String | Open Platform unionid (nullable; present only when the account and mini program sit under the same Open Platform account — the only credential for automatic cross-app linking) |
| nickname | String | Nickname |
| head_img_url | String | Avatar URL |
| subscribe | Integer | 1 subscribed / 0 unsubscribed (unsubscribes keep the row for regression analysis) |
| subscribe_time | Date | Subscribe time (written on `subscribe` event; unsubscribe writes unsubscribe_time) |
| unsubscribe_time | Date | Unsubscribe time (nullable) |
| **customer_id** | Long | **Customer record id (nullable; written once bound, §4.7)** |
| remark | String | Operations note |
| create_time / update_time | Date | Audit times |

Unique `(account_id, openid)`; indexes on `customer_id`, `unionid`.

### 4.3 wx_menus — Custom Menus (phase 1: table only, no UI)

| Column | Type | Meaning |
| ------ | ---- | ------- |
| id | Long | PK |
| account_id | Long | Account id |
| parent_id | Long | Parent id (empty for top level; max two levels, WeChat protocol limit) |
| name | String | Menu name (WeChat limits: level-1 ≤ 4 CJK chars, level-2 ≤ 8; validated server-side) |
| menu_type | String | `view`/`click`/`scancode_push`/`scancode_waitmsg`/`pic_sysphoto`/`pic_photo_or_album`/`pic_weixin`/`location_select`/`view_miniprogram` |
| url | String | Jump URL for `view` |
| media_id | String | media_id for material menus |
| miniprogram_appid / miniprogram_pagepath | String | Mini-program jump params for `view_miniprogram` (**may point at this repo's mini program**) |
| event_key | String | Event key for `click` (passed through on callback, for routing) |
| sort | Integer | Display order |
| enabled | Integer | 1 published / 0 draft (drafts are not pushed to WeChat) |
| create_time / update_time | Date | Audit times |

Index `(account_id, parent_id)`. Anchel's "personalized menus" are **not in phase 1** (single store has no audience-differentiated menu need); no table reservation.

### 4.4 wx_auto_replies — Auto-reply Rules

| Column | Type | Meaning |
| ------ | ---- | ------- |
| id | Long | PK |
| account_id | Long | Account id |
| rule_type | String | `subscribe` (welcome message, at most one effective per account) / `keyword` / `fallback` (at most one per account) |
| keyword | String | Keyword (non-empty when `rule_type=keyword`) |
| match_type | String | `exact` / `contains` |
| reply_type | String | `text` / `image` / `news` (media_id referencing wx_materials) |
| content | TEXT | Text content; media types store media_id |
| priority | Integer | Match priority, lower tries first |
| enabled | Integer | 1 on / 0 off |
| create_time / update_time | Date | Audit times |

Index `(account_id, rule_type, enabled)`. At most one effective `subscribe`/`fallback` per account (service-layer constraint, no DB unique — avoids draft-state conflicts).

### 4.5 wx_materials — Material Ledger

| Column | Type | Meaning |
| ------ | ---- | ------- |
| id | Long | PK |
| account_id | Long | Account id |
| media_id | String | WeChat-side media_id (one of the unique keys) |
| material_type | String | `image`/`voice`/`video`/`news`/`temp` |
| name | String | Material name |
| url | String | WeChat-returned access URL (temp materials expire; recorded, never relied on) |
| local_path | String | Local storage path (reuses the `sinomed.ads.upload-dir` mount idea, `application.yml:39-41`) |
| size / mime_type | Long / String | File metadata |
| create_time / update_time | Date | Audit times |

Unique `(account_id, media_id)`. Phase 1 **only pulls message-embedded temp materials to disk + logs** (downloads PicUrl into `local_path` when an image message arrives, for admin viewing); no upload/manage UI.

### 4.6 Fan Tags: No New Table, Reuse customer_tags

Fan tags (WeChat-side "user tags") get **no separate table**: this repo already has `customer_tags` (`CustomerTagEntity.java:18-44`, unique per customer+tag); fan tags join to customers via `wx_fans.customer_id` and query those directly. Rationale:

1. Clinic operations care about "which customer is this fan, what tags do they have", not a standalone WeChat-side tag system;
2. Unbound fans (no `customer_id`) have no tag needs yet — phase-1 binding is guided, so unbound fans carry little management value;
3. Avoids two parallel tag entities (aligns with salon-extension.md §6 red line).

If WeChat-side tags are truly needed later (mass messaging targeted by WeChat tag), add `wx_fan_tags` then — phase 3+.

### 4.7 Relation to Customer/Membership Entities (key design decision)

**The two openids are different systems**:

```text
Mini program (existing)                    Official account (new)
customers.openid  ←— jscode2session   wx_fans.openid   ←— callback FromUserName
   (mini-program openid, unique)           (official-account openid, unique per account)
        │                                        │
        │    unionid equal only under same        │
        │        Open Platform account            │
        └──────────────────┬─────────────────────┘
                           ▼
                    customers.id (customer record)
                     ↗ bridged via wx_fans.customer_id
```

- **No merge**: `customers.openid` keeps its mini-program semantics untouched (`CustomerEntity.java:36-38`, `docs/server/data-model.md:18`); the official-account openid lives in `wx_fans.openid`.
- **Binding path (phase 1, explicit)**: official-account-side guided binding → reuse Kiosk's proven "phone + one-time SMS code two-factor" convention (`KioskController.java:352-397` `/openid-bind` pattern): the fan opens an H5 from menu/keyword → enters phone + SMS code → the server verifies and writes `wx_fans.customer_id` → every attempt (including rejections) lands in `wx_binding_logs` (§4.8).
- **Automatic linking path (later, needs Open Platform)**: if the account and mini program sit under one Open Platform account, the callback openid can be exchanged for a unionid and matched against `wx_fans.unionid` or the mini-program-side unionid for automatic linking — **not in phase 1** (single stores rarely open an Open Platform entity just for this); the design only reserves the `unionid` column.
- **Unbinding**: the admin can unbind (clear `customer_id` + audit); a customer unsubscribing does not auto-unbind (row kept, `subscribe=0`; `customer_id` persists after re-subscribing — a different person following the same openid is an offline scenario, handled manually by the admin).

### 4.8 wx_binding_logs — Fan Binding Audit

Follows `card_lookup_logs` conventions (`CardLookupLogEntity.java:17-57`): one row per attempt (including rejections).

| Column | Type | Meaning |
| ------ | ---- | ------- |
| id | Long | PK |
| account_id | Long | Account id |
| fan_openid | String | The binding fan's openid |
| phone | String | The bound customer's phone (audit: who got bound) |
| customer_id | Long | Matched customer id (empty on miss) |
| result | String | `ok` / `missing_code` / `bad_code` / `expired_code` / `used_code` / `no_customer` / `bound_other` (this fan already bound to another customer) / `conflict` (this customer already bound to another fan) |
| create_time | Date | Time |

Indexes `fan_openid`, `phone`. **Does not share `card_lookup_logs`**: that's "card lookup" semantics, this is "binding" — mixing tables would bloat the result enum and blur query intent. Same convention, different table.

---

## 5. Message Integration Flow

> Sources: [Server configuration](https://developers.weixin.qq.com/doc/offiaccount/Getting_Started/服务器配置.html), [Receiving messages](https://developers.weixin.qq.com/doc/offiaccount/Basic_Information/接收普通消息.html), [Message crypto](https://developers.weixin.qq.com/doc/offiaccount/Basic_Information/消息加解密说明.html), [Passive replies](https://developers.weixin.qq.com/doc/offiaccount/Basic_Information/被动回复用户消息.html), [Event push](https://developers.weixin.qq.com/doc/offiaccount/Basic_Information/接收事件推送.html)

### 5.1 Callback URL and Whitelisting

- **Path**: `GET/POST /api/v1/wx/callback/{accountId}` (`accountId` = wx_accounts.id; single account today, but the URL survives multi-account later).
- **No-login whitelisting**: WeChat's POSTs carry no JWT, so add to `SecurityConfig.java:51-64`'s permitAll: `"/api/v1/wx/callback/**"`. This is the module's only new permit entry — all other admin endpoints keep JWT.
- **Source trust comes from the signature**, not a login state: §5.2.
- **Instance constraint**: WxJava's `WxMpMessageRouter` must be instantiated per `accountId` and cached (`WxMpMultiServices` idea); a singleton cannot serve multiple account configs.

### 5.2 Signature Verification (two scenarios, one algorithm)

Algorithm: `signature = SHA1(sort(token, timestamp, nonce))` — sort lexicographically, concatenate, SHA1-Hex.

| Scenario | Trigger | Verifies | On success |
| -------- | ------- | -------- | ---------- |
| **URL verification (GET)** | Saving server config in the console | `signature/timestamp/nonce`; returns `echostr` verbatim | `echostr` plaintext |
| **Message push (POST)** | Every user message/event | `msg_signature` (safe mode = `SHA1(token+timestamp+nonce+encrypt)`), then decrypt | §5.4 |

Signature failures always return 200 + empty body (WeChat requires no 5xx, else it retries 3 times then gives up), with an audit row.

### 5.3 Crypto (AES-256-CBC)

- **Plaintext mode** (`encrypt_mode=plaintext`): the POST body is plaintext XML, parse after verification.
- **Compatible/safe** (`compatible`/`safe`): body is `<xml><Encrypt>…</Encrypt></xml>`; flow: `msg_signature` verify → Base64 decode → 16-byte random + 4-byte msg_len + plaintext XML + receiveid (`Base64(AES-256-CBC(random+XML))`, key = `Base64Decode(EncodingAESKey + "=")`, IV = first 16 key bytes) → PKCS7 unpad.
- **Implementation home**: all of it (verify, AES, PKCS7, XML binding) belongs to WxJava's `WxMpCryptUtil` / `WxMpXmlMessage`; this module only does "take `aes_key` → call SDK → get a structured message" — no hand-written crypto.
- **Encrypted replies**: in safe mode replies are encrypted with the same key; the SDK's same utility handles it.

### 5.4 Message Reception and Dispatch

```text
WeChat servers
  │ POST /api/v1/wx/callback/{accountId} (XML)
  ▼
WxCallbackController
  ├─ 1. load wx_accounts by accountId (off/unconfigured → 200 "success" refuse + audit)
  ├─ 2. verify signature (§5.2) + decrypt (§5.3) → WxMpXmlMessage
  ├─ 3. dedupe: MsgId (messages) or EventKey+FromUserName+CreateTime (events) unique in DB,
  │      duplicates → 200 "success" (5s timeout → 3 retries)
  ├─ 4. write wx_message_logs (direction=in, everything)
  ├─ 5. route (WxMpMessageRouter, synchronous rules within 5s):
  │      ├─ event=subscribe   → wx_fans.upsert(subscribe=1) + welcome rule
  │      ├─ event=unsubscribe → wx_fans.subscribe=0 (no reply; WeChat won't accept one)
  │      ├─ event=CLICK/VIEW  → menu events (phase 1: log only)
  │      ├─ msg=text          → wx_auto_replies keyword match (exact→contains, priority)
  │      └─ msg=image/voice/… → log + fallback reply (no speech/image processing)
  └─ 6. reply: passive XML (within 5s) or customer-service API (async, 48h window)
```

**The 5-second constraint** (official: no response within 5s → disconnect and retry, 3 times total; return `"success"` when no reply is needed):

- Rule matching + passive reply complete synchronously (memory/local DB only, 5s is ample);
- Slow operations (material download, customer binding) **must not block** the reply thread: reply `"success"` first, process async, reach the user via the customer-service API;
- All replies (passive + customer-service) also land in `wx_message_logs` (`direction=out`), forming a complete conversation record.

### 5.5 Message Type Matrix (phase 1)

| Type | Receive | Passive reply | Notes |
| ---- | ------- | ------------- | ----- |
| text | ✅ | ✅ | The keyword-match workhorse |
| image | ✅ | ✅ (fixed copy) | PicUrl pulled into `wx_materials.local_path` |
| voice | ✅ | ✅ (fixed copy) | Logged with Format/MediaId, no recognition |
| video / shortvideo | ✅ | ✅ (fixed copy) | Logged |
| link / location | ✅ | ✅ (fixed copy) | Logged |
| event: subscribe / unsubscribe | ✅ | Welcome / no reply | The fan add/remove main entry |
| event: CLICK / VIEW | ✅ | Log only | Menu events, no business routing in phase 1 |
| event: others (scan etc.) | ✅ | Log only | Unrecognized events log silently, no errors |

**Design principle**: unrecognized types always "log + silence/fallback", never throw — WeChat-side retries would amplify the error.

---

### 5.6 Web authorization (snsapi_base) and the H5 binding flow (patrol decision record 2026-10-19; scheduling status remains "unscheduled")

Phase 1 does **snsapi_base silent authorization for the openid only**; snsapi_userinfo (a user-consent page + scope approval + the unionid decrypt chain) is not in phase 1 — binding needs only the openid. The technical decisions below were made by the patrol under the 2026-10-14 delegation (versioning/releases, license, paid purchases, and asset licensing remain with the user).

**Flow**:

```
H5 page /h5/wx/bind?account=1
  → GET /api/v1/wx/oauth/1/start        (302, issues state)
  → open.weixin.qq.com/connect/oauth2/authorize?appid&redirect_uri&response_type=code&scope=snsapi_base&state#wechat_redirect
  → WeChat silently redirects back to GET /api/v1/wx/oauth/1/callback?code&state
  → verify state → call sns/oauth2/access_token for the openid (secret decrypted from wx_accounts; token used once, never stored)
  → issue a one-time ticket (5 minutes, single redemption)
  → 302 /h5/wx/bind#ticket=…
  → the H5 page calls POST /api/v1/wx/bind/1 with the ticket (ticket or openid, either one; phone + SMS two-factor unchanged)
```

**Decision record**:

| # | Topic | Adopted | Rejected | One-line rationale |
| --- | --- | --- | --- | --- |
| D-OA1 | CSRF protection for state | Stateless signature `SHA1(token, accountId|nonce|exp)`, 10-minute validity | Server-side challenge table / Redis; a pre-row in wx_binding_logs | Successful binding still requires the SMS two-factor, so a leaked state is not an attack surface — no storage needed |
| D-OA2 | redirect_uri source | New config `sinomed.official-account.oauth-redirect-base` (env `WX_OA_OAUTH_REDIRECT_BASE`); the callback is fixed at `{base}/api/v1/wx/oauth/{accountId}/callback` | Deriving it from the request Host | The WeChat console registers the web-auth domain as an exact whitelist entry; Host derivation can be poisoned by header injection |
| D-OA3 | code→openid exchange | Direct call via `java.net.http` (3s timeout, same as the media download) | Pulling in the WxJava SDK | Phase-1 "no new deps first" policy; the SDK is evaluated with the phase-2 AES safe mode |
| D-OA4 | H5 host | A same-origin static single page (web static output or a data/-style directory behind the same Nginx origin), path `/h5/wx/bind` | A new subdomain / SSR | Phase 1 is a single binding form; a subdomain adds another whitelist entry |
| D-OA5 | Handing the openid to H5 | A one-time ticket (5 minutes, single redemption) | Passing the openid in the URL; a long-lived cookie | The openid is a long-term identifier — no plaintext URLs or long-lived credentials; the ticket dies on redemption |

**Endpoint contract (next round's TDD anchors)**: `GET /api/v1/wx/oauth/{accountId}/start` (302, permitAll, whitelisted with `/api/v1/wx/oauth/**`), `GET /api/v1/wx/oauth/{accountId}/callback` (permitAll; an invalid state or failed exchange returns an explicit error page, never silent, with a full rejection audit in `wx_message_logs`), and the existing `POST /api/v1/wx/bind/{accountId}` gains a `ticket` parameter; tests stub the outbound call with a JDK HttpServer (WeChat API base URL configurable in tests, same "test-only injection" precedent as ads/payqrcodes).

**Activation checklist (user side, once credentials arrive)**: register the `oauth-redirect-base` domain under the console's "web authorization domain"; set `WX_OA_ENABLED`/`WX_OA_TOKEN`/`WX_OA_CREDENTIAL_KEY`; store the appid + AppSecret in `wx_accounts` (encrypted at rest).

---

## 6. Permission Boundaries (account type × capability matrix and degradation)

> Sources: [Service account intro](https://developers.weixin.qq.com/doc/offiaccount/Getting_Started/服务器配置.html), [Custom menus](https://developers.weixin.qq.com/doc/offiaccount/Custom_Menus/自定义菜单介绍.html), [Materials](https://developers.weixin.qq.com/doc/offiaccount/Asset_Management/素材管理.html); account capability differences also reference the vluma MCP's three-type boundary split (§2 table).

### 6.1 Capability Matrix

| Capability | Service | Verified subscription | Unverified subscription |
| ---------- | ------- | -------------------- | ----------------------- |
| Message callback (receive + passive reply) | ✅ | ✅ | ✅ |
| Customer service messages (48h window) | ✅ | ✅ | ❌ |
| Custom menus | ✅ | ✅ | ❌ (console config also limited) |
| Material management | ✅ | ✅ | ✅ (upload/read) |
| Mass messaging (subscription notifications) | ✅ (template messages/subscription notifications) | ✅ (subscription notifications, one per day) | ❌ |
| Template messages | ✅ | ❌ (subscription accounts have none) | ❌ |
| Web authorization (snsapi_base/userinfo) | ✅ | ✅ | ✅ |
| Parametric QR codes | ✅ | ✅ | ✅ |

> Boundaries follow the official docs of the day; this table is the **product design view**, driven at implementation by the `wx_accounts.account_type + verified` pair, no hardcoded enum branches.

### 6.2 Unverified Subscription Account Degradation (required)

Capability detection is not scattered over code branches; a unified **`WxCapabilities` service** returns the capability set for a given `wx_accounts` row. The web admin fetches it on load and renders accordingly:

| Entry | Service / verified subscription | Unverified subscription |
| ----- | ------------------------------- | ----------------------- |
| Menu page | editable, publishable | **hidden**, account settings show "unverified subscription accounts don't support custom menus" |
| Mass/template messages | available | **hidden**, same note |
| Customer service replies | available (48h) | **hidden**, message console is passive-reply only |
| Materials | available | available |
| Messaging / customer linking | available | available (all of phase-1's value lives here) |

Degradation styling matches the existing admin (disabled + tooltip), no confirm dialogs — a missing capability is an **account attribute**, not an operation risk.

### 6.3 Admin Operation Permissions (this repo's roles)

Reuse the existing `Role/Permission` system (`security/Role.java:16-38`, `Permission.java:7-15`), **no new permission enums** (only `ADMIN`/`MANAGER` are effectively in use):

- Account configuration (incl. AppSecret/Token/AESKey read/write): `ADMIN` (aligning with `StaffController.java:72`'s `@PreAuthorize("hasRole('ADMIN')")` usage);
- Menus / auto-replies / materials / message console: `MANAGER` and above;
- Callback endpoints: no login (§5.1), trusted by signature.

**AppSecret never echoes back**: the admin query returns a mask (`****`); empty on update = keep — aligning with `WechatClient.java:15-16` "credentials never reach the frontend".

---

## 7. Configuration and Credentials

Follows the `sinomed.*` config style (`application.yml:38-53`):

```yaml
sinomed:
  # WeChat mini program (existing, untouched)
  wechat:
    app-id: ${WECHAT_APP_ID:}
    app-secret: ${WECHAT_APP_SECRET:}
  # Official account (new; phase-1 single account, credentials separate from the mini program, different appid system)
  official-account:
    # —— phase 1 implemented (current state: application.yml:56-64) ——
    enabled: ${WX_OA_ENABLED:false}            # default false = module fully off; callbacks refuse with 200
    token: ${WX_OA_TOKEN:}                     # server-config Token
    material-dir: ${WX_OA_MATERIAL_DIR:data/wxmaterials}      # root dir for image-message media downloads (§4.5)
    material-max-bytes: ${WX_OA_MATERIAL_MAX_BYTES:10485760}  # per-file download cap in bytes; oversize files skipped
    credential-key: ${WX_OA_CREDENTIAL_KEY:}   # encryption key for wx_accounts.app_secret/aes_key at rest; default empty = credential writes rejected
    # —— phase 2 reserved (not read by code yet; do not configure activation from this block) ——
    account-id: ${WX_OA_ACCOUNT_ID:1}          # reserved for multi-account; the current callback rides the path variable {accountId}
    aes-key: ${WX_OA_AES_KEY:}                 # EncodingAESKey (43 chars), lands together with the AES safe mode
    encrypt-mode: ${WX_OA_ENCRYPT_MODE:plaintext}  # plaintext/compatible/safe, phase 2 as above
    # app_id/app_secret go into wx_accounts (encrypted at rest), not env — credentials are business data, editable from the console
```

- **`enabled=false` is the default**: same "no silence" convention as `sinomed.phone-lookup.mode` default `off` (`application.yml:49`) — with the module off, callbacks return 200 `"success"` plus an audit row, so WeChat doesn't retry forever;
- **token/aes_key in env is the phase-1 compromise**: single-store deployment avoids DB config; phase 2 moves them into the admin form and env becomes dev/demo-only;
- **No plaintext credentials in the DB**: `wx_accounts.app_secret`/`aes_key` use reversible AES (key from env `WX_OA_CREDENTIAL_KEY`); BCrypt's one-wayness can't work (credentials must be read back to call APIs).

---

## 8. Phasing

### Phase 1: Minimal Viable — "integration + messaging + customer linking"

**Goal**: a customer messages the account, the clinic sees who they are (if bound to a record) and can auto-reply; the admin can read the conversation log. No admin CRUD UI — config via env + seed data, rules maintained by hand/SQL.

| # | Deliverable | Notes |
| - | ----------- | ----- |
| 1 | `wx_accounts` + env config + callback controller | `GET` verification returns `echostr`; `POST` verifies, decrypts, dedupes, logs, replies `"success"`/passive reply |
| 2 | `wx_fans` upsert | `subscribe`/`unsubscribe` events maintain fan rows |
| 3 | Auto-replies (reads rules) | `subscribe` welcome + `keyword` match + `fallback`; rules maintained by hand |
| 4 | `wx_message_logs` full send/receive log | Admin read-only list (filter by fan/time/type), no conversational UI |
| 5 | Fan ↔ customer binding | H5 authorization gets openid → phone + SMS two-factor (reuses `sms_codes` and Kiosk conventions, `KioskController.java:352-397`) → writes `wx_fans.customer_id` + `wx_binding_logs` audit |
| 6 | Capability endpoint `WxCapabilities` | Returns the account capability set (§6.2); phase-1 frontend uses it only to hide gated entries |

**Acceptance**: an unbound fan sends "营业时间" → gets the keyword reply; a bound customer messages → the log shows their name; a follow event → welcome message; everything sent/received (including rejected signatures) is logged.

### Phase 2: Operations Tooling — "menus + materials"

- Custom menu management page (edit/draft/publish), `wx_menus` table already built;
- Material upload and management (permanent ledger + local storage), supporting menu and auto-reply references;
- Admin rule editor UI (auto-replies no longer hand-maintained in DB);
- Message console upgraded to a conversational view + human customer-service replies (48h window, service/verified subscription).

### Phase 3: Outreach — "parametric QR + mass/template messages"

- Parametric QR codes (scene values bind customer source; scanning creates the fan with pre-filled `customer_id`);
- Service-account template messages (appointment reminders, follow-up reminders — against existing `appointments`/`review` plans);
- Subscription mass messaging (the verified-subscription one-per-day limit gets a strong UI hint);
- Fan tags (if WeChat-side targeting is needed) — re-evaluate `wx_fan_tags` then.

**Dependency**: phase 3's template/mass messaging **depends on the account type** (§6.1); a store holding only an unverified subscription account shelved phase 3 forever — this doesn't affect phase 1-2 value (messaging and menus/materials are account-type independent).
