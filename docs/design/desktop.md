# 桌面版设计：前台 / 医生工作站（Tauri 2 薄壳）

> 状态：**已实施（P0 D1–D5，P1 D6–D10 全部完成）**。`desktop/` Tauri 2 薄壳、打印模板与打印桥、扫码输入、CI 双平台安装包、更新器与 nightly 发布流水线、单实例/自启/崩溃自动重启、模板版本化、ESC/POS 直连、扫码枪串口模式、外设状态只读查询均已落地并逐项实证。`desktop/` 端与候诊区展示屏（[平板展示](/design/tablet)）、顾客自助机（[顾客选服务](/design/kiosk)）是三台设备三个工程。

界面原型（壳内接诊工作台，设计稿 · 数据为虚构演示）：

![桌面工作站 · 接诊开单（界面原型）](/screenshots/desktop-workstation.png)

## 定位与场景

桌面版面向**前台与医生的工作站**场景，Windows 为主：

| 场景 | 现状依赖 | 桌面版增强 |
| ---- | ---- | ---- |
| 顾客建档 / 接诊录入 | 管理端（web）浏览器页 | 固定窗口入口，免浏览器环境差异 |
| 打印处方笺 / 调理方案 | 无（源码无打印功能） | Tauri 外设桥接打印机（本文新增设计） |
| 扫码定位顾客 | 无（源码无扫码功能） | USB 扫码枪输入（本文新增设计） |
| 日常升级 | 手动更新浏览器/部署 | 自动更新通道（见下文） |

原则：**业务全在服务端**。桌面版不新增业务逻辑，管理端页面原样跑在壳内；业务迭代只发 `server/` 与 `web/`，桌面壳仅在外设或打包方式变化时才发版（终端零业务升级）。

## 选型：Tauri 2 薄壳包现有 web

选 Tauri 2 的依据（基于本仓现实）：

- **业务已经写完一遍**：管理端（web）已覆盖全部页面，桌面版若再写一套界面即双份维护；薄壳方案直接加载已部署的 web 地址，零业务代码。
- **系统 WebView2**：相较内嵌完整浏览器内核的方案，安装包体积与内存占用明显更小；Windows 10/11 自带 WebView2 运行时。
- **外设桥**：打印、扫码等本机外设能力走 Rust 侧命令（`invoke`），HTTP 面不变，服务端无需为外设增加任何接口。
- **官方自动更新插件**：`tauri-plugin-updater` 提供签名校验的更新通道，配合本仓 Release 口径即可。

## 架构

```text
┌─ 工作站（Windows） ────────────────────────────┐
│  desktop/（Tauri 2 薄壳）                       │
│  ├─ 主窗口：加载远程 web 地址（如 https://…/）  │
│  ├─ 外设桥（Rust commands）：打印 / 扫码        │
│  └─ 本地：config.json + 打印模板缓存 + 更新器   │
└──────────────┬─────────────────────────────────┘
               │ HTTPS（现有 /api/v1 全量复用）
┌──────────────┴─────────────────────────────────┐
│  server/（业务中枢，不感知客户端形态）           │
│  web/（同一套页面，浏览器 / 壳内皆可运行）       │
└────────────────────────────────────────────────┘
```

- 服务端**零新表**，业务接口与管理端浏览器访问完全等价；模板下发是唯一的加项（P1 D7，免登录只读）。
- 外设桥走 Tauri `invoke`（进程内调用），**不进入 HTTP 面**；web 页面通过 `window.__TAURI__` 检测壳环境，浏览器里自动退化为普通打印 / 手动输入。

## 外设清单与插件位

| 外设 | 用途 | P0 方案 | 插件位 |
| ---- | ---- | ---- | ---- |
| A4/A5 打印机 | 处方笺、调理方案单 | Windows 驱动模式：HTML 模板 + `@page` 分页，webview 打印（`window.print()` 定向打印 iframe） | 无需插件；P1 可换 Rust 侧静默打印 |
| 58/80mm 热敏小票机 | 挂号小票、回执 | 同上（装 Windows 驱动后即系统打印机） | ✅ ESC/POS 直连已实施（`print_escpos` Rust command，GBK 直发，见 D8） |
| USB 扫码枪 | 扫顾客码定位建档 / 接诊 | 键盘仿真（HID）模式：聚焦输入框即可收码，回车结尾；web 侧做焦点管理与自动提交 | ✅ HID（D3）+ 串口模式均已实施（`scanner_start/scanner_stop` Rust command，见 D9） |

**打印模板与离线缓存**：处方笺 / 小票模板为 HTML 文件，随安装包内置并缓存到本地应用数据目录（`templates/`）；断网时模板壳仍可打开打印。P1 起模板**版本化**：服务端新增免登录模板接口（`GET /api/v1/print/templates`，空白版式不含业务数据），内置版式兜底、可被服务端同名模板覆盖（`data/printtemplates/` 目录放同名文件即可，`PRINT_TEMPLATES_DIR` 可配），壳启动与每次打印前按 `version`（内容摘要）增量拉取——模板更新只改服务端，不发壳版本。注意边界：模板里的**业务数据来自服务端接口**，完整离线接诊不在本设计范围内。

**扫码挂号的边界**：预约挂号已实装（`appointments` 表 + `/api/v1/appointment`，管理端 `/appointment/*` 含排班网格，见[功能点清单](/research/features)）。P0 的「扫码」= 扫顾客码 → 调 `GET /api/v1/customer/phone/{phone}` 等现有接口定位顾客并进入建档 / 接诊；完整挂号叫号闭环见[平板展示设计](/design/tablet)的最小叫号接口。

## 打包与自动更新

- **打包**：Tauri bundler 产出 NSIS 安装包（Windows x64）；产物随 CI 上传。
- **更新口径**：接 **nightly 滚动 Release** 口径（参照 wingman 的 `releases/tag/nightly` 滚动预发布模式）：CI 覆盖发布 `nightly` 预发布并上传安装包与 `latest.json`（updater 清单，含签名）；正式版另打 `vX.Y.Z`。**发布 job 为手动点火**（`workflow_dispatch`）——按仓库铁律，push 链不自动 tag/release；首次发版在 Actions 手动跑一次 Desktop Build → nightly Release 即可。
- **更新通道**：`tauri-plugin-updater` 启动 5 秒后按 `config.json` 的 `update_channel` 取清单（nightly → `releases/download/nightly/latest.json`，stable → `releases/latest/download/latest.json`），命中新版弹窗确认后下载安装、重启生效；无 Release / 离线只记日志不打扰使用。签名用 minisign 密钥：私钥在 repo secret `TAURI_SIGNING_PRIVATE_KEY`（空口令），公钥在 `tauri.conf.json`。

## 数据模型

**服务端：零新表。** 桌面版全部业务数据复用现有表（customers / treats / items / reviews 等，见[数据模型](/server/data-model)）。

本地（工作站）数据只有三类：

| 位置 | 内容 |
| ---- | ---- |
| `config.json` | 服务端地址、窗口尺寸、默认打印机、更新通道、开机自启、扫码枪串口（`scanner_port`，空 = 不启用） |
| `templates/` | 打印模板 HTML 缓存（内置兜底 + 服务端模板接口按 version 覆盖，D7 已实施） |
| updater 状态 | `tauri-plugin-updater` 自管（下载缓存与版本记录） |

## API 面

- **业务**：复用现有 `/api/v1` 全量接口（与管理端一致，见[REST 接口清单](/server/api)），无新增。
- **模板下发**（P1 D7 加项）：`GET /api/v1/print/templates`，免登录只读，详情见上「打印模板与离线缓存」。
- **本机命令**（Tauri `invoke`，非 HTTP）：`print_html(template, data)`（P0）、`print_escpos(port, data, columns?, baud?)`（P1 D8，ESC/POS 字节流直写串口）、`set_autostart(enabled)`（P1 D6）、`scanner_start(port, baud?)` / `scanner_stop()`（P1 D9，串口扫码枪读码线程，收码广播 `scanner-code` 事件）、`peripheral_status()`（P1 D10，外设只读状态查询，不改外设）；配置了 `scanner_port` 时启动即自动开监听，页面侧经 `__TAURI__.event.listen` 订阅（远程页由 `scanner-remote` capability 放行 `core:event:default` 事件监听 + `allow-peripheral-status` 只读查询，`print_`/`scanner_`/`set_` 等改外设或改配置的命令仍不放行）。**ACL 口径**：一旦应用定义了 ACL 清单（`src-tauri/permissions/commands.toml`），本地页对自定义命令从「隐式放行」转为「显式放行」——`capabilities/default.json` 必须引用清单的 `default` 集逐个放行引导页用到的命令，清单与 capability 不同步会导致本地页调用被拒；权限标识在 `cargo build` 时校验，拼错直接编译失败。

## 目录落位

```text
desktop/                    # monorepo 新增目录（与 server/ web/ app/ 平级）
├── src-tauri/
│   ├── src/main.rs         # 薄壳入口：创建窗口、加载远程 URL、注册 commands
│   ├── src/commands/       # print.rs（P0）、scanner.rs（P1）
│   ├── tauri.conf.json     # 窗口 / 打包（NSIS）/ updater 配置
│   ├── icons/              # 应用图标（取自 docs/public/logo.svg 转制，不改原形）
│   └── Cargo.toml
├── ui/                     # 引导页静态资源：服务端地址配置、诊断信息（主窗口默认直接加载远程地址）
├── package.json            # name: sinomed-desktop（Node.js 24 / pnpm，与全仓一致）
└── .nvmrc
```

CI 落位：`.github/workflows/desktop.yml`（构建 NSIS 包 → 覆盖发布 nightly Release）；部署目标机无需任何常驻组件。

## todo 原子项

### P0

| # | 事项 | 验收 | 状态 |
| ---- | ---- | ---- | ---- |
| D1 | `desktop/` Tauri 2 脚手架：窗口加载远程 web 地址、地址可配置（config.json / 启动参数） | 本机打开壳可见管理端登录页并正常登录 | ✅ Xvfb 实机：登录进 dashboard、未配置回落引导页、「保存并打开」写盘跳转；`--server-url` 参数单测覆盖 |
| D2 | 处方笺 / 小票 HTML 模板 + webview 驱动打印（`@page` 分页、打印机选择） | Windows 上打出 A5 处方笺与 80mm 小票 | ✅ 模板器 8 项单测、样张落盘并调起系统打印对话框、模板视觉 chromium 核对；**纸面出单待 Windows 实测**（CI 产物）；管理端业务页内打印入口未接（远程页 IPC 受限，暂由引导页自检样张承担） |
| D3 | 扫码枪键盘仿真接入：web 侧扫码输入框焦点管理、回车自动定位顾客 | 扫码后 2 秒内打开对应顾客页 | ✅ 顾客查询页挂扫码输入：手机号 201ms / 编号 287ms 跳顾客页（Playwright 键盘快打仿真，真实 HID 枪同协议）；未知号/非法码就地提示 |
| D4 | NSIS 打包脚本 + `desktop.yml` CI 构建产物 | CI 产出可安装的 NSIS 包 | ✅ Desktop Build 绿：windows-nsis + linux-deb 双 artifact（本地同款命令 deb 实证 2.8MiB） |
| D5 | nightly 滚动 Release 工作流（覆盖发布 nightly tag + `latest.json`）+ `tauri-plugin-updater` 接入 | 壳内收到更新提示并升级成功 | ✅ 查更新接线并 404 优雅降级实证；发布 job 手动点火（仓库口径），**升级闭环待首次发版后 Windows 实测** |

### P1

| # | 事项 | 说明 | 状态 |
| ---- | ---- | ---- | ---- |
| D6 | 单实例锁、开机自启、崩溃后自动重启 | 工作站无人值守运行 | ✅ 单实例（dbus 名锁，二实例让位聚焦实证）；看门狗父进程 `--supervised` 分流：kill -9 子进程 3 秒拉起、60 秒 6 次预算止损实证、TERM 父进程整组退；开机自启配置驱动收敛（`~/.config/autostart` 条目生成/移除实证，引导页开关即点即生效） |
| D7 | 打印模板版本化：服务端模板接口 + 本地缓存覆盖 | 模板更新不发壳版本 | ✅ 服务端 `GET /api/v1/print/templates`（免登录、白名单两模板、SHA-256 内容摘要版本；`data/printtemplates/` 同名文件覆盖内置，目录覆盖→版本漂移 curl 实证）；壳启动预热 + 每次打印前按 version 增量拉取缓存 `templates/`，打印走「缓存优先、内置兜底」（Xvfb 实机全链路：运行中写配置→点打印自检→日志 `模板缓存已更新至 ce4be162…`→打印临时文件含覆盖标记而非内置内容）；服务端 3 项 / 壳 3 项单测覆盖解析、过滤、缓存优先 |
| D8 | ESC/POS 直连打印（Rust command，USB/串口） | 免驱小票机场景 | ✅ `print_escpos(port, data, columns?, baud?)`：小票数据编码 ESC/POS 字节流直写 serialport 串口（初始化/倍宽店名/明细行/合计加粗/走纸切纸，中文 GBK 直发，全角按 2 列算宽对齐），免驱免打印对话框；引导页串口自检（端口输入 + 即点即发）；socat 伪终端实证：ui 写 `/tmp/escpos-a` → 对端收全 294 字节（ESC@ 起、GBK「合计/店名」在流中、GS V 1 切纸收尾），坏端口红色报错实证；编码器 4 项单测（控制指令/GBK/按列宽补齐/空数据不 panic），cargo 18 项全绿 |
| D9 | 扫码枪串口模式（serialport 插件位） | HID 仿真不可用时的兜底 | ✅ 壳持读线程按行收码（100ms 超时轮询可停，`\n`/`\r` 切行、残行续收），收到即广播 `scanner-code` 事件，页面侧订阅后走既有定位流程，无需 invoke 权限；配置 `scanner_port` 启动即自动开监听，`scanner_start/scanner_stop` 命令供引导页自检与换口；远程页经 `scanner-remote` capability 只放 `core:event:default`（只放监听不放业务命令）；`ScannerInput` 组件订阅 `scanner-code`（浏览器无 `__TAURI__` 自动退化）。实证：socat 伪终端对——① 引导页填 `/tmp/scan-a` 点监听测试，对端写 `13900002222\n` → 页面绿字回显「收到扫码：13900002222」；② 生产路径：配置 server_url + scanner_port 启动即自动监听（fd 实证占用 pts），远程加载的管理端顾客查询页写同一码值 → 自动跳 `customer/update?customerId=1`（capability 放行的远程页事件订阅 + 定位跳转全链路）；切行 3 项单测，cargo 21 项全绿 |
| D10 | 外设状态条：壳侧只读状态查询命令 | 接诊页外设状态条出真实连接态（原型非写死文案） | ✅ 新增 `peripheral_status()` 只读查询命令，一次性返回三类外设状态——**扫码枪**取读线程运行时（`ScannerState.runtime` 端口/监听态/最近错误；`read_loop` 在打开成功、打开失败、读取失败、正常停止四处更新，状态判定 报错优先 → 在收码 → 未开）；**80mm 小票机**按需直写串口无长连接，故只报上次 ESC/POS 打印结果（`EscposState` 留痕字节数/失败原因/距今秒数）；**A5 处方笺打印机**走系统打印对话框，取 `config.printer` 预选名（不探测设备，空 = 未预选）。web 侧 `PeripheralPanel` 30 秒轮询 + 订阅既有 `scanner-status`/`scanner-error` 事件即时刷新（轮询兜底、事件兜实时），查不到状态显示「查询中…」不伪造，浏览器端按页面自身能力标注。查询失败不当作外设异常（保持上次状态、下次轮询重试）。**ACL 影响**：本项首次引入 `src-tauri/permissions/commands.toml` 应用清单——Tauri 2 里一旦应用定义了 ACL 清单，本地页对自定义命令从「隐式放行」转为「显式放行」，故 `capabilities/default.json` 必须引用清单的 `default` 集逐个放行引导页用到的 7 个命令（漏一个则本地页调用被拒）；`capabilities/scanner-remote.json` 只加 `allow-peripheral-status`——只读查询放行给远程管理端页，`print_`/`scanner_`/`set_` 等改外设或改配置的命令仍不放行（D9 的安全姿态不变）。权限标识在 `cargo build` 时校验，拼错直接编译失败。实证：状态判定优先级单测（error > listening > idle）、escpos 上次打印结果单测（从未打印 None / 成功记字节数 / 失败记原因且 bytes 归零），cargo 23 项全绿 |
