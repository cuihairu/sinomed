# Configuration and deployment

All server-side configuration is centralized in `server/src/main/resources/application.yml` and can be overridden by runtime environment variables. There are three deployment shapes: Docker Compose orchestration, bare-metal systemd, and a development/debug orchestration.

## Configuration

### Environment variables (must-read for deployment)

| Variable | Description | Example |
| ---- | ---- | ---- |
| `DB_HOST` / `DB_PORT` | MySQL address and port | `db` / `3306` |
| `DB_NAME` | Database name | `sinomed` |
| `DB_USERNAME` / `DB_PASSWORD` | Database credentials | `sinomed` |
| `DB_URL` / `DB_DRIVER` / `DB_DIALECT` | Full override of the datasource (used to switch to SQLite in demo environments) | `jdbc:sqlite:/app/data/sinomed.db?journal_mode=WAL&busy_timeout=5000` / `org.sqlite.JDBC` / `org.hibernate.community.dialect.SQLiteDialect` |
| `DEMO_SEED` | Demo seed data switch (idempotent; repeats skip seeding) | `true` |
| `SERVER_PORT` | Service port | `2347` |
| `JAVA_OPTS` | JVM arguments (injected by the Dockerfile) | `-Xms256m -Xmx2048m` |

### Key settings in application.yml

| Config key | Value | Description |
| ---- | ---- | ---- |
| `spring.jpa.hibernate.ddl-auto` | `update` | Auto-creates tables / adds columns; no manual DDL needed |
| `spring.datasource.dbcp2.*` | heartbeat detection | Connection validation (`SELECT 1`) |
| `application.security.jwt.secret-key` | Built-in default | **Must be replaced in production**, Base64 HMAC key |
| `application.security.jwt.expiration` | `86400000` | access token lives 1 day |
| `application.security.jwt.refresh-token.expiration` | `604800000` | refresh token lives 7 days |
| `server.servlet.session.timeout` | `24h` | Session timeout |
| `springdoc` / `knife4j` | enabled | API documentation at `/doc.html`, Chinese interface |
| `logging.level.*` | debug/trace | Hibernate SQL and Security logging; recommended to be lowered in production |
| `spring.servlet.multipart.max-file-size` / `max-request-size` | 100MB | Upload size cap for materials |
| `sinomed.ads.upload-dir` | `data/ads` (overridable by `ADS_UPLOAD_DIR`) | Advertising materials directory, statically mapped at `/media/**` |
| `sinomed.ads.url-prefix` | Only the `@Value` default `/media` (no key in the yml) | Static mapping prefix for ad media, shared by `WebMvcConfig` and the playlist URLs |
| `sinomed.print.templates-dir` | `data/printtemplates` (overridable by `PRINT_TEMPLATES_DIR`) | Print template override directory |
| `sinomed.receipt.clinic-name` | Only the `@Value` default `示例医馆` (no key in the yml) | Clinic name on the settlement receipt (`{{clinicName}}` placeholder replacement) |
| `sinomed.pay.qrcodes-dir` | Only the `@Value` default on `PayQrCodeController` (no key in the yml) | Payment QR code image directory |
| `demo.seed` | `false` (overridable by `DEMO_SEED`) | Idempotent demo-data seeding switch |
| `sinomed.famous-doctor-surge-cap-percent` | `20` (overridable by `FAMOUS_DOCTOR_SURGE_CAP_PERCENT`) | Cap percentage for the famous-doctor registration fee surcharge; the surcharge is shown as a separate line, with no tiered / time-slot differential and no pricing engine |
| `sinomed.phone-lookup.mode` | `off` (overridable by `PHONE_LOOKUP_MODE`) | Phone card lookup mode: `off` (default, disabled) / `openid_only` (openid-bound card lookup) / `sms_verified` (phone number + verification code, two-factor); convention in [kiosk.md](/design/kiosk#phone-card-lookup-mini-program-my-cards-2026-10-14-ruling) |
| `sinomed.wechat.app-id` | Empty (overridable by `WECHAT_APP_ID`) | WeChat mini program appid: used in `openid_only` mode to exchange for an openid via jscode2session; when not configured the openid exchange is explicitly rejected with 400, never silent |
| `sinomed.wechat.app-secret` | Empty (overridable by `WECHAT_APP_SECRET`) | WeChat mini program AppSecret: used only for server-side jscode2session outbound calls, never sent to the frontend |
| `sinomed.official-account.enabled` | `false` (`WX_OA_ENABLED` overrides) | WeChat OA module master switch: default false = callback answers 200 refusing to process with an audit row, never silent; activation needs `WX_OA_TOKEN` and `WX_OA_CREDENTIAL_KEY` |
| `sinomed.official-account.token` | Empty (`WX_OA_TOKEN` overrides) | WeChat message-push token (SHA1 signature check) |
| `sinomed.official-account.material-dir` | `data/wxmaterials` (`WX_OA_MATERIAL_DIR` overrides) | Root dir for image-message media downloads (relative paths stored in DB); failures/oversize only log a warn and never break the callback |
| `sinomed.official-account.material-max-bytes` | `10485760` (`WX_OA_MATERIAL_MAX_BYTES` overrides) | Per-file download cap in bytes; oversize files are skipped with no ledger row |
| `sinomed.official-account.credential-key` | Empty (`WX_OA_CREDENTIAL_KEY` overrides) | Encryption key for `wx_accounts.app_secret`/`aes_key` at rest (AES-GCM, no plaintext credentials in DB); default empty = credential writes rejected, required at activation |
| `sinomed.official-account.oauth-redirect-base` | Empty (`WX_OA_OAUTH_REDIRECT_BASE` overrides) | H5 base URL for web-auth redirects (e.g. `https://clinic.example.com`, design §5.6); default empty = web-auth refuses to start, required at activation |
| `sinomed.official-account.wechat-api-base-url` | `https://api.weixin.qq.com` (`WX_OA_WECHAT_API_BASE_URL` overrides) | Outbound base URL for WeChat APIs (sns oauth2 openid exchange); point at a local stub for tests/integration |
| `server.port` | `${SERVER_PORT:2347}` | Deployments can override with `SERVER_PORT`; a local bare run defaults to 2347 |

### Port conventions

| Port | Purpose |
| ---- | ---- |
| 2347 | Server (exposed through the Nginx reverse proxy `/api`) |
| 3306 | MySQL |
| 80 / 443 | Nginx (admin console static site) |
| 5008 | Remote debugging (dev orchestration only) |

## Build artifact

```bash
cd server
./mvnw -B package -DskipTests   # 产物 target/sinomed-server.jar
```

The Dockerfile is based on `eclipse-temurin:21-jre-alpine`, copies the jar to `/app/server.jar`, allows JVM arguments to be injected via `JAVA_OPTS`, and uses `/app/logs` as the log directory.

## Demo orchestration (root `compose.yml`, one-click SQLite)

Online demos and local trial use the root `compose.yml`: two services (server + web, where web's nginx reverse-proxies `/api` to server), the SQLite database file lands on the `sinomed-data` persistent volume, and `DEMO_SEED=true` idempotently seeds fabricated demo data:

```bash
docker compose up -d
# 管理端 http://localhost:8080 （admin / 123）
# 接口文档 http://localhost:2347/doc.html
```

An empty volume triggers automatic table creation on first start; `admin/123` is auto-created the first time anyone logs in as admin (built-in bcrypt password); restarts de-duplicate on natural keys such as account, phone number, and service-card name, so no duplicate data is produced. To switch back to MySQL, just replace the five `DB_*` environment variables in the compose file (the build layer also has optional domestic-mirror parameters `MVN_DIST_MIRROR` / `MAVEN_MIRROR_URL`, so it can still build where outbound access is restricted).

## Option 1: Docker Compose (full MySQL stack)

`server/docker-compose.yml` orchestrates three services: MySQL (runs `init/init.sql` automatically to create the database and account) + the server image + Nginx (mounts the `web/dist` static site and `init/nginx_http.conf`):

```bash
cd server
docker compose up -d
```

> Note: the server service in `server/docker-compose.yml` already has the full set injected — `DB_HOST` (on the same default network as db, resolved by the service name db) / `DB_PORT` / `DB_NAME` / `DB_USERNAME` / `DB_PASSWORD` / `SERVER_PORT` — so `up` just works. To change the port or database name, override the environment section as shown in `server/dev.yml`; the root `compose.yml` is the demo orchestration (full set injected + SQLite).

## Option 2: development/debug orchestration

What differs in `server/dev.yml`: the server is built from the local Dockerfile, `DB_*` environment variables are injected, port 5008 is opened for remote debugging (plus a 3000:3000 mapping for the admin console dev server to integrate with), and the MySQL data directory and logs land on paths relative to the repo:

```bash
cd server
docker compose -f dev.yml up -d
```

## Option 3: bare metal + systemd

1. Install MySQL 8 and run `server/init/init.sql` to create the database;
2. Install JDK 21 and place `sinomed-server.jar` (e.g. under `/data/sinomed/`);
3. Configure Nginx for the static site and reverse-proxy `/api` to `127.0.0.1:2347` (`server/init/` contains http / https example configs, with SPA routing falling back to `index.html`);
4. Install the systemd service from `server/init/sinomed.service` and enable it on boot:

```bash
sudo cp server/init/sinomed.service /lib/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now sinomed
```

## Online demo plan

- **CI builds images**: the `Docker Images` workflow (`.github/workflows/docker.yml`) builds and pushes to ghcr.io when main is pushed with changes under `server/` or `web/`, tagged `latest` (rolling main) and `sha-<short commit>` (traceable, rollbackable); after the first publish, set `sinomed-server` / `sinomed-web` to **Public** in GitHub Package settings so the demo machine can pull anonymously.
- **Demo machine**: runner-Docker (intranet `192.168.5.5`), deployment directory `/data/deploy/sinomed`; place the repo's `compose.yml`, pin the version with `IMAGE_TAG=sha-<short commit>` and bring it up; rollback = change the tag back to the previous short commit hash and `up -d` again.
- **Domain and tunnel**: `*.cuihairu.site` goes through a Cloudflare Tunnel (domain TBD, placeholder `sinomed.cuihairu.site` for now), pointing at port `8080` of the demo machine's web service, with no public IP or registered-port exposure needed.
- **Data reset**: all demo data is fabricated by the seed script; `docker compose down -v && docker compose up -d` returns to a brand-new demo state.

## Nginx routing

```text
浏览器 → Nginx(80/443)
  ├── /            → web/dist 静态资源（SPA fallback index.html）
  └── /api/**      → http://<server>:2347（应用接口 /api/v1/**，含 ads 下发与叫号拉取）
```

In the repo, `web/nginx.conf` and `server/init/nginx_http.conf` contain only the two locations above. Tablet / Kiosk deployment notes: after placing the `tablet/dist` and `kiosk/dist` build outputs on paths reachable by Nginx, you must **add** `location /tablet/` and `location /kiosk/` yourself (the kiosk build's base is `/kiosk/`); the material upload directory defaults to `data/ads` under the server's working directory, and `/media/**` is currently covered by an application-layer static mapping (`WebMvcConfig`) — if Nginx should serve it directly, add `location /media/` pointing at that directory (or move it to a dedicated disk with `ADS_UPLOAD_DIR` and point at the new path). Upload size is already relaxed to 100MB in `application.yml`; if Nginx has a `client_max_body_size`, relax it in sync.

## Logs

Application logs go to `logs/` (`/app/logs` inside the container): `info.log` rolls by day and at 100MB, `error.log` rolls by day and at 10MB; there are also business log files such as login (`login.log`) and clock-in (`sign.log`), with levels defined in `log4j2-spring.xml`.

## Security hardening checklist (honest record of the current source state)

- The JWT signing key is a built-in default in the source and must be changed in production;
- `admin / 123` is auto-created on the first admin login — change the password before going live;
- The compose files contain example database passwords and must be replaced;
- Method-level permissions cover only the staff-management write endpoints (`@PreAuthorize("hasRole('ADMIN')")`); all other endpoints are usable with any login;
- The Caffeine cache expires after about 50 minutes; `@CacheEvict` invalidation has been added for service-card and treatment-record updates, but a short dirty-read window remains.
