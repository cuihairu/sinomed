[English](README.md) | [中文](README.zh.md)

<h1 align="center">Sinomed</h1>
<p align="center"><img src="docs/public/logo.svg" width="64" alt="Sinomed logo" /></p>
<p align="center"><b>TCM Clinic Management System</b>: customer records · treatment records · service packages · review and follow-up</p>
<p align="center">
  <img src="https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white" alt="Java 21" />
  <img src="https://img.shields.io/badge/Spring-Boot-6DB33F?logo=springboot&logoColor=white" alt="Spring Boot" />
  <img src="https://img.shields.io/badge/Vue-3-42b883?logo=vuedotjs&logoColor=white" alt="Vue 3" />
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

A single design language runs across five device targets: a graphite × antique gold theme (the grey and gold from the brand logo), a rice-paper background, and serif headings, plus three switchable theme colors — pine green, cinnabar, and indigo. Below are the interface prototypes for each target (design mockups; all data is fictional demo data). Source files live in [`docs/design/mockups/`](docs/design/mockups/).

**Admin frontend** (web · customer records / consultation chart)

<p align="center"><img src="docs/public/screenshots/admin-customer.png" width="880" alt="Admin frontend · customer records (interface prototype)" /></p>

<p align="center"><img src="docs/public/screenshots/admin-diagnosis.png" width="880" alt="Admin frontend · consultation chart: four examinations, pulse reading, acupoint selection, and five-element relationships (interface prototype)" /></p>

**Theme switching** (admin frontend · appearance settings; four theme colors, one click each)

<p align="center"><img src="docs/public/screenshots/admin-themes.png" width="880" alt="Admin frontend · four theme colors: graphite × antique gold (default, from the logo) / pine green / cinnabar / indigo" /></p>

**Appointment scheduling** (admin frontend · booking / arrival list / arrival-to-consult handoff / per-day doctor-by-time-slot schedule grid; mini-program self-service booking planned)

<p align="center"><img src="docs/public/screenshots/admin-booking.png" width="880" alt="Appointment scheduling · booking, arrival list, and the doctor-by-time-slot schedule grid implemented; mini-program self-service booking remains a design prototype" /></p>

**Billing and checkout** (admin frontend · settlement desk: pending-checkout queue + stored-value / WeChat / Alipay / cash payments; the prescription line items, package redemption, and receipt printing from the prototype remain design drafts)

<p align="center"><img src="docs/public/screenshots/admin-billing.png" width="880" alt="Billing and checkout · pending-checkout queue with stored-value recharge and payment implemented; prescription line items, package redemption, and receipt printing remain design prototypes" /></p>

**Herbal prescription** (admin frontend · prescription create / query: herb doses and special decoction methods; compatibility review, pricing, and decoction service are planned features)

<p align="center"><img src="docs/public/screenshots/admin-herbprescription.png" width="880" alt="Herbal prescription · sheet create and query implemented; compatibility review and decoction service remain design prototypes" /></p>

**Desktop workstation** (desktop · consultation charting + receipt printer / barcode scanner status)

<p align="center"><img src="docs/public/screenshots/desktop-workstation.png" width="880" alt="Desktop workstation · consultation charting (interface prototype)" /></p>

**Mini program** (app · customer home; business pages planned)

<p align="center"><img src="docs/public/screenshots/mobile-home.png" width="300" alt="Mini program · home (interface prototype)" /></p>

**In-store kiosk** (kiosk · service selection and ordering)

<p align="center"><img src="docs/public/screenshots/kiosk-menu.png" width="400" alt="In-store kiosk · service selection (interface prototype)" /></p>

**Waiting-area display** (tablet · promo carousel + queue calls)

<p align="center"><img src="docs/public/screenshots/tablet-screen.png" width="880" alt="Waiting-area display · promo carousel and queue calls (interface prototype)" /></p>

The admin frontend also has prototypes for the daily report, service packages, orders, ad displays, appearance settings, and more; see [`docs/design/mockups/`](docs/design/mockups/) and the [documentation site](https://cuihairu.github.io/sinomed/).

## Planned Features (Design Prototypes)

For appointment scheduling, the admin-side list / booking / arrival handoff and the per-day schedule grid are implemented; for billing, the settlement desk with stored-value recharge is implemented; for herbal prescription, the sheet create / query is implemented — see Product Preview above. What stays as design prototypes: mini-program self-service booking, receipt printing and per-visit card deduction, and compatibility review with decoction service.

## Repository Layout

```
sinomed/
├── server/    # Server (Java 21 / Maven, includes Dockerfile and deployment scripts)
├── app/       # Mini program (Taro multi-platform project, Node.js 24, pnpm)
├── web/       # Admin frontend (Ant Design Pro / Umi, Node.js 24, pnpm)
└── docs/      # Documentation site (VitePress) and research notes
```

- Source code for `server/`, `app/`, and `web/` is maintained directly in this repository.
- Backend build (requires Java 21): `mvn -B package --file server/pom.xml`.
- Frontend build (requires Node.js 24): in `web/`, run `pnpm install && pnpm build`; the mini program under `app/` works the same way.

For details on each component, see the README in each directory and the [documentation site](https://cuihairu.github.io/sinomed/).
