---
layout: home

hero:
  name: Sinomed
  text: TCM Clinic Management System
  tagline: Designed around the daily flow of a TCM clinic — patient records, treatment records (four examinations / pulse / five phases / point selection / diagnosis and conditioning plan), service cards, retrospectives and follow-ups. One repository for the server, the admin console and the mini program.
  actions:
    - theme: brand
      text: Get started
      link: /guide/getting-started
    - theme: alt
      text: Server APIs
      link: /server/api

features:
  - icon:
      src: /icons/stethoscope.svg
      alt: Treatment
    title: Patients & treatment records
    details: Patient record creation, search and update (allergy history and past medical history recorded entry by entry); treatment records cover the chief complaint, the four examinations, pulse presentation (both hands), five-phase generation and restraint, acupoint selection, diagnosis and conditioning plan — follow-up visits can reopen the history.
  - icon:
      src: /icons/receipt-yuan.svg
      alt: Service cards
    title: Service cards & membership
    details: Create, query and maintain the clinic's service cards (treatment items); patient records carry a membership level, laying the ground for member operations and orders.
  - icon:
      src: /icons/report-analytics.svg
      alt: Retrospective
    title: Retrospectives & follow-ups
    details: Daily summaries are accumulated per day; staff retrospectives and patient follow-ups are aggregated by day; overdue follow-ups surface automatically in the daily report; the monthly billing report aggregates daily order counts / amounts actually received and the payment-method mix by settlement time.
  - icon:
      src: /icons/prescription.svg
      alt: Roadmap
    title: Clinic module roadmap
    details: Appointments and scheduling, treatment records (including acupoint-dictionary point selection, acupuncture prescription logging and external-therapy template application), herbal prescriptions (compatibility review / dose review / allergy alerts / per-gram herb pricing / decoction service and paste-pickup / pharmacy dispensing workbench / syndrome templates / formula library with ready-made formulas), billing and settlement (stored value / visit-card write-off / receipt overprint / refunds and daily reconciliation), staff leave, periodic staff shift tables with a weekly timesheet comparison, and processed-herb stock-in/stock-out with expiry alerts are all implemented; purchase orders and online payment are planned. Layered roadmap and reference sources are in "Research · feature checklist".
  - icon:
      src: /icons/device-desktop.svg
      alt: Desktop
    title: Desktop workstation
    details: A Windows thin shell (Tauri 2) for the front desk / doctor workstation — loads the admin console pages directly, prints prescription notes and receipts, locates a patient by barcode scan, auto-updates; business logic stays in the server. See "Design · Desktop".
  - icon:
      src: /icons/device-tablet.svg
      alt: Tablet
    title: Lobby display
    details: Waiting-area tablet Kiosk — advertising and health-education rotation, front-desk calling displayed within about a second, offline playback continuation; materials / schedules / screens / calling are configured in the admin console. See "Design · Lobby display".
  - icon:
      src: /icons/receipt-yuan.svg
      alt: Ordering
    title: Patient self-service ordering
    details: Patients pick service cards on a front-desk iPad Kiosk and place an order by phone number; the admin console's order management handles acceptance and status flow. See "Design · Patient service selection".
  - icon:
      src: /icons/api.svg
      alt: APIs
    title: APIs & documentation
    details: REST APIs under a unified `/api/v1` prefix, with built-in Knife4j Chinese API docs (`doc.html`) and JWT login authentication.
  - icon:
      src: /icons/inventory.svg
      alt: Deployment
    title: Containerized deployment
    details: Dockerfile and docker-compose orchestration (SQLite + server + Nginx) are provided; bare-metal deployment via systemd is also supported.
---

## Product preview

One visual language runs across all five device surfaces: the graphite × antique-gold theme (grey and gold taken from the brand logo), rice-paper backgrounds and serif headings, plus three further switchable theme colors — pine smoke green / cinnabar / indigo. Below are screenshots of the implemented pages on each surface (all data is fictional demo data).

**Admin console** (web · patient records / treatment record)

![Admin console · patient record (implemented)](/screenshots/admin-customer.png)

![Admin console · treatment record (implemented)](/screenshots/admin-diagnosis.png)

**Theme switching** (admin console · appearance settings, the four theme colors apply on tap)

![Admin console · four switchable theme colors (implemented)](/screenshots/admin-themes.png)

**Appointments and scheduling** (admin console · create appointment / not-yet-arrived list / convert a check-in into a treatment record / per-day doctor × time-slot scheduling grid; mini-program self-service booking is implemented: unauthenticated endpoint + mini-program booking page)

![Appointment scheduling · create appointment, not-yet-arrived list, doctor × time-slot grid and mini-program self-service booking all implemented](/screenshots/admin-booking.png)

**Billing and settlement** (admin console · settlement counter / daily reconciliation: unsettled queue + stored value / WeChat / Alipay / cash / visit-card payments + receipt overprint + refund-on-original-path and daily reconciliation; the prescription-herb rows in the prototype are illustrative and were ruled out of scope)

![Billing and settlement · unsettled queue, stored-value top-up / payment, visit-card write-off and receipt printing implemented; prescription-herb rows are illustrative](/screenshots/admin-billing.png)

**Herbal prescriptions** (admin console · writing a prescription / prescription search / pharmacy workbench: herb dosage and special decoction methods; compatibility review, dose review, pricing, decoction service and paste pickup, and dispensing confirmation are all implemented)

![Herbal prescription · writing, search, compatibility review, allergy alerts, live pricing, decoction service and paste pickup all implemented](/screenshots/admin-herbprescription.png)

**Desktop workstation** (desktop · treatment entry + peripheral status)

![Desktop workstation · treatment entry (implemented)](/screenshots/desktop-workstation.png)

**Mini program** (app · bottom four tabs: home / booking / cards / me; the cards page has **phone-based card lookup implemented** — openid and SMS-code two-factor, gated by the server-side `sinomed.phone-lookup.mode` tri-state switch (default `off`) and audited; see the [phone card lookup design](/design/kiosk#phone-card-lookup-mini-program-my-cards-2026-10-14-ruling). A logged-in patient session and card purchase are still planned features)

![Mini program · home page (implemented)](/screenshots/mobile-home.png)

**In-store Kiosk** (kiosk · pick a service and place an order)

![In-store Kiosk · service selection (implemented)](/screenshots/kiosk-menu.png)

**Waiting-area display** (tablet · advertising rotation + queue calling)

![Waiting-area display · advertising rotation and queue calling (implemented)](/screenshots/tablet-screen.png)

The admin console also has prototypes for the daily report, service cards, orders, advertising screens, appearance settings and more — see `docs/design/mockups/` in the repository.
