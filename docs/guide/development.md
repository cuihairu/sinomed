# Development Guide

## Directory Layout

```text
sinomed/
├── server/    # 服务端（Java 21 / Spring Boot 4.1 / Maven）
│   └── src/main/java/com/sinomed/   # controller / service / repository / entity / security / config …
├── web/       # 管理端（Ant Design Pro / Umi，Node.js 24）
├── app/       # 小程序端（Taro 3.6 / React / NutUI，Node.js 24）
├── docs/      # 文档站（VitePress，本站源码）与调研资料
└── .github/workflows/               # CI（Maven 构建、Docker 镜像、文档站部署、桌面端构建、前端三端构建）
```

- The three frontends are built independently with no cross-dependencies; the admin console and mini program talk to the server through its `/api/v1` API.
- The frontend uses pnpm as the unified package manager; Java builds use the repo-bundled `mvnw` (no local Maven install needed).
- `.nvmrc` pins Node 24 in both `web/` and `app/`.

## Builds

| Target | Command | Artifact |
| ---- | ---- | ---- |
| Server | `cd server && ./mvnw -B package -DskipTests` | `server/target/sinomed-server.jar` |
| Server (with tests) | `cd server && ./mvnw -B test` (self-contained, see below) | - |
| Admin console | `cd web && pnpm install && pnpm build` | `web/dist/` |
| Admin console dev | `cd web && pnpm dev` | `http://localhost:8000` |
| Mini program H5 | `cd app && pnpm build:h5` | `app/dist/` |
| Mini program WeChat | `cd app && pnpm build:weapp` | `app/dist/` (import into WeChat DevTools) |
| Kiosk | `cd kiosk && pnpm build` (`tsc -b && vite build`) | `kiosk/dist/` |
| Waiting-area tablet | `cd tablet && pnpm build` (`tsc -b && vite build`) | `tablet/dist/` |

## Tests

- **Server**: tests are self-contained — the surefire configuration in `server/pom.xml` uniformly injects SQLite environment variables (`DB_DRIVER=org.sqlite.JDBC`, `DB_URL=jdbc:sqlite:target/test-sinomed.db`, `SERVER_PORT=0`, etc.), so a bare `./mvnw test` runs green on a clean machine with no database to start first. To run against an external test database, override the same-named environment variables:

```bash
cd server
DB_HOST=127.0.0.1 DB_PORT=3308 DB_NAME=sinomed \
DB_USERNAME=root DB_PASSWORD=test123 SERVER_PORT=2347 \
./mvnw -B test
```

- **Admin console**: `pnpm lint` (eslint + prettier + tsc), `pnpm jest`;
- **Mini program**: no test script yet; multi-platform builds passing is the gate; for type checking also run `pnpm exec tsc --noEmit --skipLibCheck` (`--skipLibCheck` suppresses the ~97 type errors from the Taro / `@types` libraries themselves; the bar is zero errors in this repo's own source).
- **Kiosk / tablet**: no standalone test script; the `tsc -b` inside `pnpm build` is the type gate.
- **Desktop workstation**: `cd desktop && pnpm test` (i.e. `cargo test --manifest-path src-tauri/Cargo.toml`) runs the 23 `#[test]`s inside src-tauri (ESC/POS byte streams, print templates, config parsing, scan-line splitting, crash budget, and other core logic); on Linux first install the wry / serialport prerequisites `libwebkit2gtk-4.1-dev libgtk-3-dev libsoup-3.0-dev libjavascriptcoregtk-4.1-dev librsvg2-dev libudev-dev`. frontendDist points at the committed `desktop/ui` static assets, so it compiles without a frontend build.

CI (GitHub Actions):

| Workflow | Trigger | Contents |
| ---- | ---- | ---- |
| `Java CI with Maven` | push / PR to main (ignores docs-only changes) | `cd server && ./mvnw -B package` (Java 21, **with tests**, not `-DskipTests`; Maven version pinned to 3.10.0 by the wrapper, no runner preinstall needed) |
| `Frontend Build` | push to main with changes in `app/` `kiosk/` `tablet/` `web/` | app type check + dual-platform builds (weapp + h5), kiosk / tablet `pnpm build`, web eslint + type check + jest (the web build is covered when `Docker Images` produces the image) |
| `Docker Images` | push to main with changes in `server/` `web/` | build and push ghcr.io images |
| `Deploy Docs` | push to main with `docs/**` changes | docs site check + build + deploy to github-pages |
| `Desktop Build` | push to main with `desktop/` changes | Windows NSIS / Linux deb installers + Rust unit tests (a separate `cargo test` job, parallel to the build matrix); nightly releases are manually triggered |

## Environment Version Requirements

| Target | Requirement |
| ---- | ---- |
| server | Java 21 (`pom.xml` `java.version`, mirrored in CI), Maven 3.10.0 (pinned by `server/.mvn/wrapper`, same version in CI and locally) |
| web / app | Node.js 24 (`engines` + `.nvmrc` + CI constraint), pnpm 9/10 |
| Docs site | Node.js 24, `cd docs && pnpm install && pnpm build` |

## Dependency Upgrades

Current baseline (after the 2026-10 upgrades): Spring Boot **4.1.1**, caffeine 3.3.0, jjwt 0.13.0, mysql-connector-j (managed with Boot); @umijs/max **4.7.22**, antd **5.29.3**, pro-components **2.8.10**; Taro **3.6.40**.

Major-version upgrade status: Spring Boot 4.x is **done** (4.1.1, knife4j-openapi3 4.5.0 coexists with Boot 4, regressions all green). The following are still **on hold**; their breaking points are listed below — open a validation effort first before touching them:

| Upgrade | Current → Target | Breaking points |
| ---- | ---- | ---- |
| React 19 + antd 6 | 18 / 5.x → 19 / 6.x | pro-components 2.x targets antd 5; Umi max's React 19 support needs verification; full-site component regression |
| Taro 4 | 3.6.40 → 4.x | Compiler and plugin system changes; NutUI needs a companion 3.x upgrade (its latest is still the `-cpp` prerelease stream) |
| mysql-connector-j 26.x | managed with Boot (9.x) → 26.x | The MySQL driver switched to calendar versioning; the new major needs stability observation |

In-scope minor / patch upgrades are handled with `pnpm update` (frontend) and by editing version numbers directly (Maven); after upgrading, run the builds and tests above green before committing.

## Docs Site Development

```bash
cd docs
pnpm install
pnpm dev      # 本地预览
pnpm build    # 产物 docs/.vitepress/dist
```

Writing conventions: Chinese body text; APIs and fields follow the source code (nothing invented); feature status is honestly labeled "placeholder / incomplete"; pushing `docs/**` automatically triggers the deploy workflow.
