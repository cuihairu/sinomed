# Getting Started

This guide walks through three steps by platform: start the **server**, then the **admin console**, then run the **mini program** as needed. All three live in this repo: `server/` (server), `web/` (admin console), `app/` (mini program).

## Environment You'll Need

| Component | Version requirement | Notes |
| ---- | ---- | ---- |
| JDK | 21 | Server compile and runtime |
| Maven | 3.10.0 (pinned by `server/.mvn/wrapper`; the repo-bundled `./mvnw` avoids a local install) | Server build |
| Node.js | 24 | Admin console and mini program |
| pnpm | 9/10 | Frontend package manager |
| MySQL | 8.0 | Database |
| Docker (optional) | - | One compose command to bring up the database and the whole stack |

## Step 1: Start the Server

### Option A: Docker Compose (recommended, one command for the database)

The repo ships a dev orchestration `server/dev.yml` that starts MySQL (running `server/init/init.sql` to initialize the schema) together with the server container (with remote debugging on 5008 enabled):

```bash
cd server
docker compose -f dev.yml up -d
```

Once it's up:

- Server address: <http://127.0.0.1:2347>
- API docs (Knife4j, in Chinese): <http://127.0.0.1:2347/doc.html>

### Option B: Run on the host

First set up a MySQL 8 instance and run `server/init/init.sql` to initialize the schema (tables are created automatically by JPA — no manual table creation). Then:

```bash
cd server
DB_HOST=127.0.0.1 DB_PORT=3306 DB_NAME=sinomed \
DB_USERNAME=sinomed DB_PASSWORD='你的密码' SERVER_PORT=2347 \
./mvnw spring-boot:run
```

The server reads its database and port configuration from environment variables; the full list is in [Configuration and Deployment](/server/deploy).

::: tip 首次启动会自动创建管理员
On first start, if the database contains no `admin` account, the system automatically creates an administrator account **admin** (default password **123**, a built-in default in the source code). Before connecting to a production environment, log in and change the password, and replace the JWT secret key in `application.yml`.
:::

## Step 2: Start the Admin Console

The admin console is an Ant Design Pro / Umi project. Dev mode disables mock by default and proxies straight to the local server:

```bash
cd web
pnpm install
pnpm dev          # 等价于 REACT_APP_ENV=dev MOCK=none UMI_ENV=dev max dev
```

Once it's running, open `http://localhost:8000`; the dev proxy forwards `/api` to `http://127.0.0.1:2347` (see `web/config/proxy.ts`).

Log in with the `admin / 123` from the previous step to reach the workbench. It's worth walking the clinic's main flow end to end (onboarding → treatment → conditioning plan → follow-up):

1. **顾客建档**: in the patient system, use patient onboarding to enter name / age / gender / phone / membership tier.
2. **接诊**: query patients, use the row action "创建诊断" to fill in the chief complaint, the four examinations (问 / 望 / 切), pulse (left and right hands), the five-phase generation/restraint relations, point selection, diagnosis, and conditioning plan; on follow-up visits, pull the last record from "诊断历史" to prefill it.
3. **卡项**: in the service-card system, create a service card and register the store's services (this lays the groundwork for orders and card consumption).
4. **复盘与回访**: record the daily summary in the feedback system and schedule follow-ups for patients; due follow-ups show up automatically in the daily report (`/dash/day`).

::: tip 关于「开方」的边界
Conditioning plans (`plan`) and dietary advice are recorded as text; the TCM prescription (herbs / doses / number of decoctions) already has a structured model (`prescriptions` table + `/api/v1/prescription`), and registration (appointments) and billing are implemented as well. For the planned scope of the remaining modules, see the [feature list](/research/features).
:::

Also: employee clock-in/clock-out and monthly attendance statistics live in the employee system (clinic operations aid), where admins maintain employee accounts.

## Step 3: Run the Mini Program

The mini program is a Taro project that compiles to WeChat / H5 and other platforms:

```bash
cd app
pnpm install
pnpm dev:h5       # 浏览器 H5 预览
pnpm build:weapp  # 编译微信小程序产物到 dist/
```

The WeChat output lands in `app/dist` — import that directory in WeChat DevTools to preview. Note that `app/project.config.json` currently uses the template placeholder `appid` (`touristappid`); replace it with your own mini program appid before real development.

::: warning 小程序端现状
The mini program has completed project initialization + white-label build + patient home page + self-service booking + mine + card lookup (bottom tab bar; the network request layer is in `src/services/api.ts`; the 宫格拉 source is from a live published project; self-service booking goes through the unauthenticated `POST /api/v1/kiosk/appointments`, and phone-based card lookup follows the three-state toggle in the backend config `sinomed.phone-lookup.mode`, default `off`). For now, authoritative business features follow the admin console (web).
:::

## Next Steps

- For the full server picture: [Architecture Overview](/server/architecture)
- To look up APIs and fields: [REST API List](/server/api), [Data Model](/server/data-model)
- For production deployment: [Configuration and Deployment](/server/deploy)
