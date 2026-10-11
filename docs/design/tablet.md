# 平板展示设计：候诊区 Kiosk（广告 / 科普 / 叫号）

> 状态：**已实施（T1–T10、T12）**。`tablet/` 端、服务端 ads/calls 模块、dash 四个管理页均已落地并逐项实证；T11（WS 广播）评估后**不采用**——叫号轮询降到 1 秒一次，实测上屏 ~1.5s，远低于 3s 验收线，不值得为它给服务端引入 WebSocket 依赖。

界面原型（广告轮播 + 叫号上屏，设计稿 · 数据为虚构演示）：

![候诊区展示屏 · 广告轮播与叫号（界面原型）](/screenshots/tablet-screen.png)

管理端配套页面的界面原型（素材管理 + 屏幕状态 + 快捷叫号，设计稿）：

![管理后台 · 广告屏管理（界面原型）](/screenshots/admin-ads.png)

## 定位与场景

`tablet/` 是跑在**候诊区平板**上的 Kiosk 全屏网页（无交互或极简交互），三类内容：

1. **广告轮播**：门店卡项、活动宣传（图片 / 视频）；
2. **健康科普**：中医养生内容素材；
3. **叫号提示**：前台叫号时全屏切换「诊室 + 号码」，播完自动回轮播。

素材管理不在平板上做——**管理端（web dash）新增素材 / 排期 / 屏管理页面**，服务端新增 ads 模块统一下发。

```text
web dash（素材/排期/屏/叫号管理） ──► server ads/calls 模块（表 + 下发接口）
                                          │
                         tablet/（Kiosk 全屏页）◄── 轮询拉取排期（30s）与叫号（1s）
```

## 数据模型（服务端新表）

按现有 JPA 惯例设计（自增 `id`、审计字段 `create_time`/`update_time`、无外键约束、`ddl-auto` 建表），全部为**新增表**：

### ad_materials — 素材

| 字段 | 类型 | 含义 |
| ---- | ---- | ---- |
| id | Long | 主键 |
| name | String | 素材名（非空） |
| kicker | String | 叠加层眉题（可空；四项文案全空即纯素材播放） |
| title | String | 叠加层大标题（可空） |
| copy | String | 叠加层文案副题（可空） |
| price | String | 价格行，自由文本（可空） |
| type | Integer | 1 图片、2 视频 |
| url | String | 文件相对路径（服务端落盘后由 Nginx 静态托管） |
| duration_ms | Integer | 轮播停留时长（视频可取实际时长） |
| enabled | Integer | 1 启用、0 停用 |
| sort | Integer | 轮播顺序 |

### ad_screens — 屏（Kiosk 注册）

| 字段 | 类型 | 含义 |
| ---- | ---- | ---- |
| id | Long | 主键 |
| code | String | 屏标识（唯一，平板配置里填） |
| name | String | 名称（如「一楼候诊区」） |
| location | String | 定向位置（候诊区 / 诊室） |
| enabled | Integer | 1 启用、0 停用 |
| last_seen_at | Date | 最近心跳（平板拉排期时顺带上报） |

### ad_schedules — 排期

| 字段 | 类型 | 含义 |
| ---- | ---- | ---- |
| id | Long | 主键 |
| screen_id | Long | 屏 id（业务外键） |
| material_id | Long | 素材 id（业务外键） |
| weekdays | String | 生效星期（如 `1,2,3,4,5`，空 = 每天） |
| start_time / end_time | String | 生效时段（`HH:mm`，空 = 全天） |
| enabled | Integer | 1 启用、0 停用 |

### queue_calls — 叫号记录

预约挂号已实装（`appointments` 表 + `/api/v1/appointment`，见[功能点清单](/research/features)），叫号仍以**独立最小闭环**落地：前台在管理端手动叫号，与预约表尚未联动（未做「到号自动叫号」）。

| 字段 | 类型 | 含义 |
| ---- | ---- | ---- |
| id | Long | 主键（同时作平板拉取游标 `since`） |
| screen_id | Long | 定向屏（业务外键；为空 = 全部屏） |
| number | String | 号码（如 `08`） |
| room | String | 诊室名（如「第二诊室」） |
| patient_masked | String | 脱敏姓名（如 `张*`，隐私默认） |
| status | Integer | 0 待叫、1 已叫 |
| called_at | Date | 叫号时间 |

## API 面（服务端新增 `/api/v1/ads`、`/api/v1/calls`）

| 分组 | 接口 | 说明 |
| ---- | ---- | ---- |
| 素材管理 | `POST/PUT/DELETE /api/v1/ads/materials`、`GET /api/v1/ads/materials/page` | dash 用，需登录 |
| 媒体上传 | `POST /api/v1/ads/materials/upload`（multipart） | 落盘 `data/ads/`，Nginx 静态托管；**服务端当前无任何文件上传代码，此为新增能力** |
| 排期管理 | `POST/PUT/DELETE /api/v1/ads/schedules`、`GET …/page` | dash 用 |
| 屏管理 | `POST/PUT/DELETE /api/v1/ads/screens`、`GET …/page` | dash 用 |
| 下发 | `GET /api/v1/ads/playlist?screen={code}` | 平板拉取：返回 `{ version, items[] }`，`version` 为内容戳，未变化时平板不重拉媒体；顺带心跳更新 `last_seen_at` |
| 叫号动作 | `POST /api/v1/calls` | 前台触发（P0 手动输入号码 / 选顾客） |
| 叫号拉取 | `GET /api/v1/calls/latest?screen={code}&since={id}` | 平板轮询，返回 `since` 之后的新叫号 |

安全边界（如实）：平板为内网设备，下发接口按**只读 + 屏 code 校验**放行（`/api/v1/ads/playlist`、`/api/v1/calls/latest`、`/media/**` 免登录）；如暴露公网，再补屏 token。轮询节奏：排期 30s 一次，叫号 1s 一次（索引查询开销小，实测叫号上屏 ~1.5s）；WS 广播评估后不采用——收益不足以抵消服务端新增 WebSocket 依赖与连接管理。

## 排期模型

平板渲染序列 = `f(屏, 当前时刻)`：

```text
命中的排期 = ad_schedules 中 screen=本屏 ∧ enabled ∧ (weekdays 含今天) ∧ (start_time ≤ now ≤ end_time)
播放序列   = 命中排期关联的启用素材，按 sort 排序
兜底       = 无命中排期时，播放该屏全部启用素材
```

- dash 上可按**诊室 / 候诊区定向**：不同 `screen` 配不同排期（如叫号屏只播科普 + 叫号）。
- 内容戳 `version`：任一素材 / 排期变更即更新（取相关表 `max(update_time)`），平板比对后差异刷新并预缓存新媒体。

## 叫号联动链路

```text
前台（web dash 叫号按钮） → POST /api/v1/calls（写 queue_calls）
tablet 轮询 calls/latest?since=… → 发现新叫号
  → 全屏切换「诊室 + 号码 + 脱敏姓名」，提示音（Web Audio）
  → 停留 N 秒（默认 15s，可配）
  → 自动回轮播；轮询游标推进
```

语音播报（TTS）已按「按设备可用性开关」落地（T12）：Web Speech API 播「请 X 号到 Y 诊室就诊」，不支持或无中文语音引擎的设备静默跳过，只留提示音。

## 离线容错

- **媒体预缓存**：Service Worker / Cache API 把当前 `version` 的全部媒体缓存到本地；首次加载后断网可播。
- **断网行为**：继续循环播放缓存版本；屏幕角落出现「离线」状态角标；恢复后自动补拉排期与叫号游标。
- **如实边界**：断网期间**叫号不可达**（叫号事件来自服务端）；恢复后不补播错过的叫号（取最新 `since` 起的记录）。

## 目录落位

```text
tablet/                     # monorepo 目录（与 server/ web/ app/ 平级）
├── src/
│   ├── player/             # Carousel 轮播引擎 + CallOverlay 叫号提示层 + chime/speech 提示音与播报
│   ├── net/                # 排期/叫号拉取、version 比对、心跳、媒体预缓存调用
│   └── App.tsx             # 屏标识读取、Wake Lock、全屏、离线角标
├── public/sw.js            # Service Worker：/media 缓存优先 + 后台刷新（媒体预缓存）
├── index.html              # 全屏、禁止休眠（Wake Lock API 尽力而为）
├── package.json            # name: sinomed-tablet（Vite + React + TS，Node.js 24 / pnpm）
└── .nvmrc

web/src/pages/Ads/          # dash：素材管理 / 排期管理 / 屏管理 / 叫号操作（Calls）
server/src/main/java/com/sinomed/
├── entity/                 # AdMaterialEntity / AdScreenEntity / AdScheduleEntity / QueueCallEntity
├── controller/             # AdsController / CallController
└── service / repository    # 对应分层
```

部署落位：Nginx 增加两段静态托管——`/tablet/`（`tablet/dist`）与 `/media/`（`data/ads/`）；平板浏览器打开 `https://…/tablet/?screen=xxx` 并设为全屏启动页。

## todo 原子项（已全部验收）

### P0

| # | 事项 | 验收 | 状态 |
| ---- | ---- | ---- | ---- |
| T1 | server ads 模块：三表实体 + 素材/排期/屏 CRUD 接口 + multipart 上传落盘 | Knife4j 可调通全部管理接口 | ✅ curl 全接口实证 |
| T2 | 下发接口：`playlist`（含 version）与心跳 | curl 按屏拿到正确序列与内容戳 | ✅ 含错屏报错、无排期兜底 |
| T3 | `tablet/` 脚手架 + 轮播引擎（图片/视频、时长、顺序） | 浏览器全屏轮播 dash 配置的素材 | ✅ headless 双素材轮换实证 |
| T4 | dash 素材管理页（上传 / 启停 / 排序 / 时长） | 页面完成素材全生命周期维护 | ✅ 浏览器实证 |
| T5 | dash 排期 + 屏管理页（时段、星期、按屏定向） | 两块屏配置不同排期各自生效 | ✅ PAD-02 / PAD-TEST 双屏实证 |
| T6 | tablet 拉取与 version 比对、媒体预缓存 | 内容变更后 30s 内自动更新 | ✅ 换图 27s 上屏 |
| T7 | 离线容错：断网续播 + 离线角标 + 恢复补拉 | 拔网续播、插网恢复 | ✅ 角标即时切换、断网续播 |

### P1

| # | 事项 | 说明 | 状态 |
| ---- | ---- | ---- | ---- |
| T8 | server 叫号模块：`queue_calls` 表 + `POST /api/v1/calls` + `calls/latest` | 最小叫号闭环接口可用 | ✅ curl 定向/广播/游标全通 |
| T9 | dash 叫号操作入口（前台按钮：选诊室 / 输号码） | 前台一键叫号 | ✅ `/ads/calls` 浏览器实证 |
| T10 | tablet 叫号提示页（全屏切换、提示音、N 秒自动回轮播） | 叫号事件 3s 内上屏 | ✅ 实测 1.5s 上屏 |
| T11 | WS 广播替代叫号轮询（可选） | 上屏延迟进一步降低 | ⛔ 评估后不采用（1s 轮询已达标，不引 WS 依赖） |
| T12 | 语音播报（Web Speech API，按设备可用性开关） | 可选增强 | ✅ 代码路径实证，可听效果以真机为准 |
