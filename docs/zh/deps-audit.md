# Dependabot 依赖告警清理（分批升级轮）

对照基准：GitHub dependabot open alerts **188 条**（快照 2026-10-08：critical 29 / high 74 / medium 74 / low 11）。
分布：npm 187（`app/pnpm-lock.yaml` 131、`web/pnpm-lock.yaml` 52、`docs/pnpm-lock.yaml` 4）+ rust 1（`desktop/src-tauri/Cargo.lock` glib）。

口径约定：

- 告警全部落在 **lockfile 传递依赖**上（manifest 均为 lockfile，非直接依赖声明）。修复三档：
  1. **范围内升级**：`pnpm up --depth=Infinity --lockfile-only <pkg>@<fix>` 改 lockfile resolution，不动 node_modules；
  2. **overrides 钉版**：修复版超出父依赖 semver 范围、但属于 dev-only 或未使用路径时，package.json `pnpm.overrides` 强制（构建+测试全绿兜底）；
  3. **upstream-blocked 登记**：无补丁版本（NO-PATCH）、或修复版被上游依赖树钉死（如 Taro 3.6.x 钉 swiper 6）→ 如实登记不动。
- **每批全绿才提交推送下一批**：server `./mvnw test` + web/kiosk/tablet/app 各端构建 + desktop `cargo check`。
- 告警计数随升级自动关闭，最终以 dependabot 面板归零（或剩余=blocked 登记）为完成标准。

## 批次进度

- [x] 批1 critical（29 条）→ commit 见 git log：lockfile up vm2@3.12.2/proxy-addr@2.0.8/websocket-driver@0.7.5/form-data@2.5.6+4.0.6（app+web）、underscore→1.13.7（web 范围内）；overrides 补钉 form-data@2.3.3→2.5.6（app+web，request@2.88.2 精确钉死）+ immer@8→9.0.21（web，dva-immer ^8 范围外）；swiper critical 1 条 blocked（见登记表）。全端绿：server 4/4、web/kiosk/tablet/app build 过、cargo check 过
- [x] 批2 high（74 条）：app up source-map-js/fast-uri/brace-expansion×2/lodash-es/joi/js-yaml/browserslist/svgo/immutable/minimatch/qs/ws/glob + web up react-router/node-fetch/path-to-regexp×2/@babel/core；overrides 新增 app{serialize-javascript@6→7.0.5, tough-cookie@2→4.1.4, tmp→0.2.7, adm-zip@0.4.16→0.6.1, glob@10.2.6→10.5.0, compression@1.7.4→1.8.2, http-cache-semantics@3.8.1→4.1.1（后改范围形式 `<4.3.0: 4.3.0`，见登记表）, minimatch@3.0.8/9.0.3→修复版, node-fetch@1→2.7.0} web{axios@0.27.2→0.34.0, underscore@1.7.0→1.13.8, debug@0.7.4→2.6.9, postcss@7→8.5.28, path-to-regexp@8.2.0→8.4.0}；app 的 postcss@7 override 实测破坏 Taro 老 autoprefixer（unprefixed undefined 构建崩）已撤销→blocked 登记。全端绿（app 重建 0，仅框架自带 2 warning）。audit 余量：app crit3/high8/med29/low0、web crit0/high4/med15/low1——剩余=blocked 清单（见下表）
- [x] 批3 medium（74 条）：app up http-proxy-middleware→2.0.10/@humanfs/node→0.16.8/colord→2.9.4/qs→6.16.0 + web up @babel/runtime→7.29.10/qs；overrides app{got@8.3.2/9.6.0→11.8.5, esbuild@0.19.12/0.14.54→0.25.0, uuid@8.3.2/3.4.0→11.1.1, qs@6.5.5/6.14.2→6.16.0, decode-uri-component→0.5.0, postcss-selector-parser@6.1.4→7.1.6, webpack-dev-server@4.11.1→5.2.6（实测撤销，见登记）} web{log4js@1.1.1→6.4.0, esbuild@0.18.20/0.21.4→0.25.0, uuid@3.4.0→11.1.1, decode-uri-component→0.5.0, postcss-selector-parser@6→7.1.6, react-router@6.3.0→6.30.6, @babel/runtime@7.23.6→7.29.10} docs{esbuild@0.21.5→0.25.0}；webpack-dev-server override 实测破坏 Taro dev:h5（wds5 options schema 不兼容 Taro 3.6.40 编程式集成，dev server 起不来）已撤销→blocked 登记，dev:h5 冒烟复测 6s 返回 200。全端绿（七端含 docs VitePress build）。audit 余量：app crit3/high8/med13/low0、web crit0/high4/med4/low1、docs med2/high1——剩余=blocked 清单
- [x] 批4 low（11 条）：无可动项——app low 已被前三批顺带清零（body-parser→1.20.6、postcss-selector-parser、@babel/core→7.29.6、@babel/runtime→7.29.10 均已到位）；web 唯一 low=elliptic 6.6.1 NO-PATCH（已登记）
- [x] 批5 rust：glib 0.18.5 唯一告警——`cargo update -p glib --precise 0.20.0` 实测被依赖图拒绝（tauri 2.12.1 → gtk ^0.18 → glib ^0.18，0.x 语义跨 minor 即 breaking），blocked 登记；Cargo.lock 零变更
- [x] 终验（2026-10-08）：dependabot open **188 → 36**（-81%：critical 29→3、high 74→12、medium 74→20、low 11→1），面板剩余 36 条按包聚合 17 项与 upstream-blocked 登记表逐一吻合、零遗漏——swiper/decompress/braces/git-clone/html-minifier/node-forge/http-cache-semantics/webpack-dev-middleware/postcss/mockjs/extract-zip/elliptic/request/sprintf-js/webpack-dev-server/react-router(7.x 线)/vite(vitepress 钉)/glib。全部为 NO-PATCH 或上游依赖树钉死，解除路径=工具链大版本升级（Taro/umi/vitepress/tauri），按轮次口径不动
- [x] 补批（2026-10-12）：web mockjs high 1 条解除——mockjs 本身 NO-PATCH，但其依赖链 `umi-presets-pro → @umijs/max-plugin-openapi → @umijs/openapi` 属未使用功能（服务层手写维护），移除该 devDep 及 config 中 openAPI/requestRecord 配置，锁文件 -1179 行零 mockjs；web jest 17 绿 + build 绿；dependabot #12 归 fixed。剩余 blocked 35 条口径不变
- [x] 收口（2026-10-14 复核）：dependabot 面板 open **0**，完成标准「面板归零」达成（fixed 100+、dismissed 18——dismissed 集中在 2026-10-11 手工登记，not_used 14 / tolerable_risk 4，10 包 swiper/decompress/braces/git-clone/extract-zip/request/sprintf-js/elliptic/glib/react-router 全部在登记表内）；登记表 18 行中 webpack-dev-server/vite 已解除（2026-10-13 范围形式 override），余 16 行 NO-PATCH/钉死维持，解除路径仍=工具链大版本升级（Taro/umi/vitepress/tauri），按轮次口径不动

## upstream-blocked 登记

| 包 | manifest | 告警 | blocked 原因 |
|---|---|---|---|
| swiper | app/pnpm-lock.yaml | critical 1 | 依赖树钉死：@tarojs/components 3.6.40 → swiper 6.8.0，修复版 12.1.2 为 major 跳版，升级需 Taro 框架升级（3.6.40 生态未适配 swiper 12） |
| decompress 4.2.1 | app/pnpm-lock.yaml | critical 2 | NO-PATCH（上游弃坑无修复版，@tarojs 工具链传递） |
| braces 3.0.3 | app+web/pnpm-lock.yaml | high 各1 | NO-PATCH（3.0.3 已是发布线最新，advisory 无补丁版本） |
| git-clone 0.1.0 | app/pnpm-lock.yaml | high 1 | NO-PATCH |
| html-minifier 4.0.0 | app/pnpm-lock.yaml | high 1 | NO-PATCH（上游弃坑，替代品 html-minifier-terser 需换父链） |
| node-forge 1.4.0 | app/pnpm-lock.yaml | high 1 | NO-PATCH（1.4.0 已最新，advisory 无补丁） |
| http-cache-semantics 4.1.1 | app/pnpm-lock.yaml | high 1 | ✅ 已解除（2026-10-13）：NO-PATCH，但 override 换范围形式钉修复版 `http-cache-semantics@<4.3.0: 4.3.0`（app/package.json:106），锁文件已全量 4.3.0 |
| webpack-dev-middleware 5.3.4 | app/pnpm-lock.yaml | high 1 | ✅ 已解除（2026-10-13）：override `webpack-dev-middleware@<7.4.6: 7.4.6` 钉修复版（app/package.json），Taro webpack5-runner 兼容 |
| postcss 7.0.39 | app/pnpm-lock.yaml | high/medium | ✅ 已解除（2026-10-13）：override 换范围形式 `postcss@<8.5.23: 8.5.28` + `autoprefixer@9: ^10.4.21`（app/package.json:107-108），构建绿 |
| mockjs 1.1.0 | web/pnpm-lock.yaml | high 1 | ✅ 已解除（2026-10-12）：NO-PATCH（上游停更），但依赖链 `umi-presets-pro → @umijs/max-plugin-openapi → @umijs/openapi → mockjs` 属未使用功能（服务层手写维护、openAPI/requestRecord 配置一并移除），整体摘除后锁文件零 mockjs，alert #12 归 fixed |
| extract-zip 1.7.0 | web/pnpm-lock.yaml | high 2 | NO-PATCH（pro-cli 老工具链传递） |
| elliptic 6.6.1 | web/pnpm-lock.yaml | low 1 | NO-PATCH（6.6.1 已最新线） |
| request 2.88.2 | app+web/pnpm-lock.yaml | medium | NO-PATCH（上游弃坑；其漏洞主链 form-data 已 override 2.5.6 缓解） |
| sprintf-js | app+web/pnpm-lock.yaml | medium | NO-PATCH（1.1.2 修复未发布） |
| webpack-dev-server 4.11.1 | app/pnpm-lock.yaml | medium 2/high 2 | ✅ 已解除（2026-10-13）：override 换范围形式 `webpack-dev-server@<5.2.6: 5.2.6`（app/package.json:109）——只钉住依赖树内传递实例，Taro 编程式集成入口不受 wds5 schema 影响 |
| react-router 6.30.6 | web/pnpm-lock.yaml | medium 1 | 该实例剩余 1 条 advisory 修复版 7.18.0 为 major 跳版，umi max 集成 react-router 6，跳 7 破坏路由栈（6.3.0 旧实例已 override 到 6.30.6） |
| vite 5.4.21 | docs/pnpm-lock.yaml | high 1/medium 1 | ✅ 已解除（2026-10-13）：docs/package.json:20 override `vite@<6.4.3: 6.4.3` 钉修复版，vitepress 1.6.4 构建绿 |
| glib 0.18.5 | desktop/src-tauri/Cargo.lock | medium 1 | tauri 2.12.1 → gtk ^0.18 → glib ^0.18 钉死，0.20.0 需 gtk-rs 0.20 线（tauri/wry 升级跟随） |
