# Open-Source Project Inventory

> Survey date: 2026-10-05. Every project was verified live via GitHub `gh api` or Gitee `gitee.com/api/v5`; anything not found is marked "not found" as such. Feature lists are excerpted from each project's README; no inference was made.

## Verification of starting leads

| Lead | Verdict |
| ---- | ---- |
| TANGKUO/HIS (microservice HIS) | **Verified to exist** (GitHub) |
| gitee njpaper/hisystem (small/mid hospital BS) | **Verified to exist** (a fork of `sensay/hisystem`; the parent repo is no longer accessible) |
| gitee DzmHIS (大宅门医疗门诊) | **DzmHIS is an account name, not a repository name**; the original `gitee.com/DzmHIS/OpenHIS` repo now 404s; account ownership was confirmed via a fork's homepage field, and the accessible copies remaining are several forks |
| OpenHIS (大宅门) | Original repo is offline; note the distinction from the same-named `tntlinking-opensource/openhis` (天天开源) on GitHub — the two are entirely different products |

## Verified projects

### 1. wolongHyy/Yukang-Smart-Cloud-Clinic-Platform (愈康云诊所) — highest match to this project

- **Repo**: <https://github.com/wolongHyy/Yukang-Smart-Cloud-Clinic-Platform>
- **Stack**: JavaScript / Electron-shaped, local-first SQLite, LAN access from PC/phone/tablet
- **Feature list** (README): single store / chain HQ / branch in three forms (branches keep separate databases, HQ aggregates, invitation-code onboarding); EMR covers chief complaint, present illness, past history, allergy history, physical examination, **TCM four examinations, diagnosis, pattern differentiation**, medical orders; **prescription types cover Chinese/Western patent medicines, infusion, Chinese herbal, herbal application, external therapy**; drug categories include **Chinese processed herbs, Chinese patent medicines, herbal granules**; pharmacy inventory (batch/expiry/stocktaking), outpatient charging (cash/WeChat/Alipay/bank card/medical insurance), business statistics; **AI-assisted prescribing and prescription review**; daily snapshot backup
- **Activity**: 105 stars / 12 forks, last push **2026-10-02** (active)
- **License**: MIT
- **Borrowable points**: the chart–prescription model of TCM four examinations + pattern differentiation + herbal/herbal application/external therapy prescriptions; the local-first + chain-aggregation multi-store architecture

### 2. tntlinking-opensource/openhis-clinic (天天开源 · clinic edition)

- **Repo**: <https://github.com/tntlinking-opensource/openhis-clinic>
- **Stack**: C#/.NET (`Code/medical2.0` contains back + client + docker-compose) + SQL scripts
- **Feature list** (README): aimed at community health stations / village clinics / private clinics, **5 core modules: appointment & registration, consultation, charging & dispensing, report queries, inventory management**; supports localized / SaaS deployment, medical insurance integration, and can form county medical consortiums with the OpenHIS hospital system
- **Activity**: 24 stars, last push **2026-09-10** (active, with community and docs site)
- **License**: GPL-3.0 (plus a separate 天天开源 community edition license; a commercial edition runs in parallel)
- **Borrowable points**: the "5-module clinic closed loop" fits TCM hall scale best; mind GPL-3.0's viral nature — evaluate before reusing code
- Same-org `tntlinking-opensource/openhis` (general hospital edition, 142 stars, active) targets secondary-level and below hospitals, with 10 major modules including physical exams, charging & settlement, care-team collaboration, pharmacy and EMR

### 3. ZainZhao/HIS — highest-starred Chinese HIS of its kind

- **Repo**: <https://github.com/ZainZhao/HIS>
- **Stack**: Spring Cloud Netflix (Eureka/Config/Feign/Zuul/Hystrix) + Spring Boot 2.0.3 + MyBatis; frontend Vue 2.6 + Element-UI, patient side uni-app; repo contains both a monolith (`HIS-master`) and microservices (`his-cloud`)
- **Feature list** (README): **six workstations — outpatient physician, pharmacy physician, medical-technician physician, cashier, reconciler, administrator**; offers an online demo
- **Activity**: **1635 stars / 603 forks**, last push 2025-02-24
- **License**: Apache-2.0
- **Borrowable points**: the multi-workstation role split of outpatient business; dual monolith/microservice implementations of the same shape (start a small hall on one machine, split into services after chaining)

### 4. TANGKUO/HIS

- **Repo**: <https://github.com/TANGKUO/HIS>
- **Stack**: same origin as ZainZhao/HIS (a Neusoft practical-training brief): Spring Cloud Netflix + Spring Boot 2.0.3 + MyBatis + MySQL, with Redis/RabbitMQ/ES; frontend Vue + Element-UI + uni-app
- **Feature list** (README): clinical care / drug management / financial management / patient management, **six workstations** (outpatient physician, pharmacy physician, medical-technician physician, cashier, reconciler, administrator), with PC and App screenshots
- **Activity**: 600 stars / 321 forks, last push 2022-06-21 (stalled; the author describes it as a training course project)
- **License**: Apache-2.0
- **Borrowable points**: the outpatient main-chain paradigm; the dual-client structure (PC care side + patient App)

### 5. Fankekeke/chinese_medicine_store_cos (Chinese medicine shop management system)

- **Repo**: <https://github.com/Fankekeke/chinese_medicine_store_cos>
- **Stack**: SpringBoot + Vue (graduation-project grade)
- **Feature list** (README): purchase/sales/inventory for Chinese processed herbs and Chinese prescription circulation; admin side: herb management, stock alerts, suppliers, electronic prescriptions, **symptom-pattern prescription templates**, multi-store, sales statistics; physician side: symptom-pattern prescriptions, orders; user side: herb purchase, online payment, order reviews; order flow includes dispensing, double-check and delivery
- **Activity**: 10 stars, pushed 2026-03-13
- **License**: none (all rights reserved by default; code may not be reused directly)
- **Borrowable points**: the processed-herb chain "herb catalog – purchasing – storeroom alerts – dispensing double-check delivery"; the symptom-pattern prescription template library

### 6. xrl-workshop/herb-ms-ssm + herb-ms-vue (Chinese herbal prescription management system HerbMS)

- **Repo**: <https://github.com/xrl-workshop/herb-ms-ssm> (backend), <https://github.com/xrl-workshop/herb-ms-vue> (frontend)
- **Stack**: Java 17 + SSM (Spring/SpringMVC/MyBatis) + Tomcat 10 + MySQL 8 (Jakarta EE); frontend Vue 3
- **Feature list**: Chinese herbal prescription management (herbs–prescriptions–pricing), four-layer backend abstraction
- **Activity**: 10 stars / 6 stars, both pushed 2024-01-12
- **License**: WTFPL
- **Borrowable points**: a small, clean Chinese herbal prescription domain model; limited functional depth

### 7. njpaper/hisystem (small/mid hospital information management system, Gitee)

- **Repo**: <https://gitee.com/njpaper/hisystem>
- **Stack**: Java SpringBoot + Bootstrap (B/S monolith); includes card-reader SDK (D3 IC card reader by 德卡)
- **Feature list** (README): registration & charging, outpatient management, pricing & charging, pharmacy pickup, physical exam management, pharmacy management, system maintenance; **visit cards support both manual entry and IC card reading**
- **Activity**: 10 stars / 4 forks, pushed 2021-03-07 (stalled)
- **License**: not stated
- **Borrowable points**: physical visit card / card reader integration; lightweight monolith structure

### 8. OpenHIS 1.0 "大宅门云诊所系统" (original Gitee repo offline, verified via forks)

- **Original repo**: `DzmHIS/OpenHIS` 404s; accessible copies: `wangzhixuan/OpenHIS`, `agaobiao/OpenHIS` (Gitee), `1638824607/OpenHIS` (GitHub, 59 stars), among others; `mali218/OpenHIS`, once marked 805 stars, is now inaccessible
- **Stack**: PHP ≥5.5 + ThinkPHP 3.2.3 + MySQL
- **Feature list** (README inside the fork): full clinic flow (registration → physician → pricing → charging → dispensing), WeChat-bound QR login, Alipay/WeChat pay, chart statistics; self-described as a "cloud clinic system, open source + PC + mobile"
- **Activity**: stalled after 2017–2018
- **License**: third-party listing pages mark Apache; the original repo is unreachable — reuse with caution
- **Borrowable points**: an early domestic cloud-clinic implementation; its full-clinic-flow diagram works as a business baseline

### 9. Other verified small TCM-focused projects

| Repo | Description | Activity | License |
| ---- | ---- | ---- | ---- |
| `spunky5/his_mvp` (GitHub) | TCM HIS MVP: registration/consultation/charging/pharmacy/inventory/finance, with **pattern-differentiation prescribing, Chinese formulas (with pinyin input), herb stock in/out and stocktaking**; FastAPI + SQLite + Vue 3 | 0 stars, push 2026-09-08 | none |
| `feitas/zhong_yi` (GitHub) | Odoo V10 TCM outpatient module | 11 stars, push 2017-04 | none |
| `AnthonyLeeDevelopment/hospital` (Gitee) | SpringBoot + Vue TCM hall business information management system (graduation project; title only, no feature detail) | 0 stars, push 2024-11 | MIT |
| `Mahongsheng/neusoft-cloud-his` (GitHub) | Neusoft cloud HIS (mostly screenshots) | 28 stars, push 2024-08 | none |

## Not found / in doubt

- **gitee `DzmHIS` account and the `mali218/OpenHIS` main copy**: inaccessible (account deregistered or made private).
- **`sensay/hisystem` (njpaper/hisystem parent repo)**: Not Found.
- **A mature open-source system dedicated to TCM hall chains**: none found — no mature open-source project with high stars, ongoing maintenance, and a primary focus on TCM hall chain management. TCM-specific projects cluster at graduation-project or personal-MVP grade, with activity and completeness clearly below general outpatient HIS — this gap is itself one of the survey's conclusions.

## Verification of public material on the 仲正堂 App

**Conclusion: no official "仲正堂" app could be found in public channels.**

- Apple App Store (China storefront) iTunes Search API search for "仲正堂" returns 0 relevant apps (the control query "中医馆" correctly returns real TCM hall apps such as 天大馆 and 小鹿中医, confirming the search works).
- Android channels such as 应用宝 / 华为应用市场 / 豌豆荚 have anti-scraping measures, so machine verification could not be completed.
- In public material, "仲正堂" appears as a **TCM tuina/moxibustion wellness chain and training organization** (上海仲正堂教育, founded 2013; specialties tuina and Guanyuan moxibustion; the 安心加盟网 franchise site claims 26 branches in Shanghai — that site is a franchise-aggregation site of medium credibility); its visible digitalization is a **third-party SaaS (有赞美业)**, not a self-built app.

Therefore this project's functional scope is grounded in **source-code implementation plus the verified reality of the open-source projects above**; 仲正堂 is used only as a "reference" for its store business format (tuina, moxibustion and other course-based service scenarios), and no unverifiable feature list is cited.

## Reference-value ranking

1. **Business model**: Yukang (MIT, active; four examinations / pattern differentiation / herbal prescriptions / chain branches) overlaps most with this project's TCM hall requirements;
2. **Clinic module closed loop**: openhis-clinic (5 modules, active, mind GPL-3.0) and ZainZhao/HIS (Apache-2.0, six-workstation role model);
3. **TCM-specific domain objects**: processed-herb / granule categories, symptom-pattern prescription templates, incompatibility prescription review, herb purchase/sales/inventory — see chinese_medicine_store_cos, his_mvp, herb-ms-ssm;
4. **License cautions**: most small TCM-specific projects carry no license (code may not be reused directly); the tntlinking family is GPL-3.0 + commercial dual licensing; the original 大宅门 OpenHIS repo is unreachable.
