[English](README.md) | [中文](README.zh.md)

<h1 align="center">Sinomed</h1>
<p align="center"><img src="docs/public/logo.svg" width="64" alt="Sinomed logo" /></p>
<p align="center"><b>TCM Clinic Management System</b>: customer records · treatment records · service packages · review and follow-up</p>
<p align="center">
  <img src="https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white" alt="Java 21" />
  <img src="https://img.shields.io/badge/Spring-Boot-6DB33F?logo=springboot&logoColor=white" alt="Spring Boot" />
  <img src="https://img.shields.io/badge/React-18-20232A?logo=react&logoColor=61DAFB" alt="React 18" />
  <img src="https://img.shields.io/badge/MiniProgram-Taro-3AA3F0?logo=taro&logoColor=white" alt="Taro" />
  <img src="https://img.shields.io/badge/SQLite-Docker-2496ED?logo=docker&logoColor=white" alt="SQLite / Docker" />
  <img src="https://img.shields.io/badge/docs-VitePress-646CFF?logo=vitepress&logoColor=white" alt="VitePress" />
  <a href="https://opensource.org/licenses/Apache-2.0"><img src="https://img.shields.io/badge/License-Apache--2.0-blue.svg" alt="Apache 2.0" /></a>
</p>
<p align="center">Documentation site: <a href="https://cuihairu.github.io/sinomed/">cuihairu.github.io/sinomed</a></p>

## Demo Site

**Demo site https://sinomed.cuihairu.site/ | Demo account `admin` / `123`** (for sandbox exploration; data is reset periodically)

> The demo environment starts with a single `compose.yml` at the repo root: server + admin frontend + a persistent SQLite volume. With `DEMO_SEED=true`, it idempotently seeds a fully fictional, de-identified demo dataset (customers, TCM treatment records, service packages, review and follow-up, appointments, stored-value ledger, and pending-checkout orders). Staff demo accounts: `gu` (director), `shen` (TCM physician), `su` (front desk), all with the password `123`. Images are published to ghcr.io by CI (`latest` + `sha-<short commit>`).

## Product Preview

A single design language runs across five device targets: a graphite × antique gold theme (the grey and gold from the brand logo), a rice-paper background, and serif headings, plus three switchable theme colors — pine green, cinnabar, and indigo. Below are screenshots of the implemented pages for each target (all data is fictional demo data).

**Admin frontend** (web · customer records / consultation chart)

<p align="center"><img src="docs/public/screenshots/admin-customer.png" width="880" alt="Admin frontend · customer records (implemented)" /></p>

<p align="center"><img src="docs/public/screenshots/admin-diagnosis.png" width="880" alt="Admin frontend · consultation chart: four examinations, pulse reading, acupoint selection, and five-element relationships (implemented)" /></p>

**Theme switching** (admin frontend · appearance settings; four theme colors, one click each)

<p align="center"><img src="docs/public/screenshots/admin-themes.png" width="880" alt="Admin frontend · four theme colors: graphite × antique gold (default, from the logo) / pine green / cinnabar / indigo" /></p>

**Appointment scheduling** (admin frontend · booking / arrival list / arrival-to-consult handoff / per-day doctor-by-time-slot schedule grid; mini-program self-service booking implemented)

<p align="center"><img src="docs/public/screenshots/admin-booking.png" width="880" alt="Appointment scheduling · booking, arrival list, schedule grid, and mini-program self-service booking implemented" /></p>

**Billing and checkout** (admin frontend · settlement desk: pending-checkout queue + stored-value / WeChat / Alipay / cash / per-visit card deduction payments, receipt printing, refund and daily reconciliation)

<p align="center"><img src="docs/public/screenshots/admin-billing.png" width="880" alt="Billing and checkout · pending-checkout queue, stored-value recharge, per-visit card deduction, and receipt printing implemented" /></p>

**Herbal prescription** (admin frontend · prescription create / query: herb doses and special decoction methods; compatibility review, allergy check, pricing, decoction and paste fulfillment, and the dispensary workbench)

<p align="center"><img src="docs/public/screenshots/admin-herbprescription.png" width="880" alt="Herbal prescription · sheet create and query, compatibility review, pricing, decoction and paste fulfillment implemented" /></p>

**Desktop workstation** (desktop · consultation charting + receipt printer / barcode scanner status)

<p align="center"><img src="docs/public/screenshots/desktop-workstation.png" width="880" alt="Desktop workstation · consultation charting (implemented)" /></p>

**Mini program** (app · bottom tabs: home / self-service booking / card / profile; card lookup and self-service queries pending an identity-verification scheme)

<p align="center"><img src="docs/public/screenshots/mobile-home.png" width="300" alt="Mini program · home (implemented)" /></p>

**In-store kiosk** (kiosk · service selection and ordering)

<p align="center"><img src="docs/public/screenshots/kiosk-menu.png" width="400" alt="In-store kiosk · service selection (implemented)" /></p>

**Waiting-area display** (tablet · promo carousel + queue calls)

<p align="center"><img src="docs/public/screenshots/tablet-screen.png" width="880" alt="Waiting-area display · promo carousel and queue calls (implemented)" /></p>

The admin frontend also has designs for the daily report, service packages, orders, ad displays, appearance settings, and more; see [`docs/design/mockups/`](docs/design/mockups/) and the [documentation site](https://cuihairu.github.io/sinomed/).

## Planned Features

Implemented today: appointment scheduling (admin list / booking / arrival handoff / per-day schedule grid, plus mini-program self-service booking), billing (settlement desk with stored-value / WeChat / Alipay / cash / per-visit card deduction, receipt printing, refund and daily reconciliation), herbal prescription (create / query with compatibility review, allergy check, pricing, decoction and paste fulfillment, dispensary workbench), desktop workstation, in-store kiosk, waiting-area display.

Still open, by recorded decision: mini-program card lookup and self-service queries (pending a customer identity-verification scheme), Western / patent medicine prescriptions (pending prototype design), online payment gateway (out of scope by policy), multi-store chains, purchase orders and stocktaking, and famous-doctor tiered scheduling. Scope rulings live in `docs/research/features.md`; the audit trail is `docs/审计-文档一致性.md`.

## Repository Layout

```
sinomed/
├── server/    # Server (Java 21 / Maven, includes Dockerfile and deployment scripts)
├── web/       # Admin frontend (Ant Design Pro / Umi, Node.js 24, pnpm)
├── app/       # Mini program (Taro multi-platform project, Node.js 24, pnpm)
├── desktop/   # Desktop workstation shell (Tauri 2, Windows)
├── kiosk/     # In-store kiosk (service selection and ordering)
├── tablet/    # Waiting-area display (promo carousel + queue calls)
└── docs/      # Documentation site (VitePress) and research notes
```

- Source code for `server/`, `app/`, and `web/` is maintained directly in this repository.
- Backend build (requires Java 21): `mvn -B package --file server/pom.xml`.
- Frontend build (requires Node.js 24): in `web/`, run `pnpm install && pnpm build`; the mini program under `app/` works the same way.

For details on each component, see the README in each directory and the [documentation site](https://cuihairu.github.io/sinomed/).

## 本地测试口径

CI 与本地开发按改动面三路分流——改一行文档不该全量编译。

1. **文档改动**（`*.md`、`docs/**`、图片）：不编译。主 CI workflow 的
   `paths-ignore` 已跳过这些路径。
2. **代码改动**：只跑改动子集，不全量。
   - `mvn -pl server -am test`
   - `mvn -pl <改动模块> -am test`
3. **全量**（`mvn -B package --file server/pom.xml`）：仅以下情形触发——pom.xml、
   `.github/**`、Docker/Compose 配置、发布前。

