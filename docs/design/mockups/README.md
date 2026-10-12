# 界面原型渲染记录与文件清单

> 统一品牌语言：石墨×古金（logo 灰 #666666 + 金 #C7A674 派生），宣纸底、宋体标题。四套可切换主题色：石墨（默认）、松烟绿、朱砂、黛蓝。数据宇宙全仓一致（王女士/金卡/艾灸卡余6次/三伏灸¥880/王中医师等）。

---

## 1. 源文件清单（`docs/design/mockups/`）

| 文件 | 端 | 状态 | 说明 |
|---|---|---|---|
| `tokens.css` | 全端 | **设计系统内核** | CSS 变量四主题、色板、字体、间距、圆角、阴影 |
| `admin-customer.html` | web | ✅ 已实现功能 | 顾客档案：建档/查询/更新/诊断历史/回访计划/持卡卡项（次卡发卡与余次） |
| `admin-diagnosis.html` | web | ✅ 已实现功能 | 接诊诊疗单：四诊/脉象/五行/取穴/诊断/方案 |
| `admin-dash-day.html` | web | ✅ 已实现功能 | 每日报表：员工总结/今日回访/今日总结/近7日接诊柱状图 |
| `admin-items.html` | web | ✅ 已实现功能 | 卡项管理：封面/上架/排序/编辑/删除/分页 |
| `admin-orders.html` | web | ✅ 已实现功能 | 订单管理：分页/状态流转（接单/完成/取消/删除）/查看详情/多品项明细（order_item 行，业务单号 `YYYYMMDD-NNN`、项目列「名 ×数量」、前台建单多选卡项带数量） |
| `admin-ads.html` | web | ✅ 已实现功能 | 广告屏管理：素材/排期/屏幕/叫号四标签 |
| `admin-appearance.html` | web | ✅ 已实现功能 | 外观设置：四主题卡即时切换 + 实时预览网格 |
| `admin-booking.html` | web | ✅ 已实现功能 | 预约排班：待到店列表/建约/到店接待转接诊与医师×时段排班网格已实装（`/appointment/schedule`，右栏今日预约四格统计/待到店列表/小程序联动说明）；过号状态（0→8）与每时段限约 1 位（同医师同小时，后端校验）已实装；小程序自助约期已实装（`POST /api/v1/kiosk/appointments` + 小程序 `pages/booking`）；✅ 2026-10-12 网格/待到店行「第 N 疗程/复诊/首诊」角标已实装——疗程卡标记优先（同项目第 N 张卡），无卡按就诊记录标复诊/首诊（`GET /treat/visited?ids=` 按顾客批量查 treats 聚合计数；treats 无项目维度，按顾客口径拍板） |
| `admin-herbprescription.html` | web | ✅ 已实现功能 | 中药处方：处方笺开方/查询已实装（`/prescription/create`、`/prescription/query`，药材自由文本）+ 配伍审方已实装（`POST /prescription/compatibility` 十八反/十九畏静态规则）+ 计价已实装（`/api/v1/herb` 字典 + `POST /prescription/price` 试算，开方页随写随查，未收录药名不计费）+ 代煎领取已实装（开方勾选代煎袋数=剂数落待煎，查询页流转 待煎→可取→已取，服务费 ¥3/袋 仅提示，141f295/47e4828）；todo 11 条 2026-10-12 全清（rx 头/特殊煎法提醒/剂量审方 dose-check/签名三签/领取方式/三键/炮制列/用法三格/拼音检索/过敏常显态，代煎费拍板入三行账合计）；仅药典剂量**权威数据源**余卡点（机制已落、由诊所自录 dose_max，数据源待用户定夺） |
| `admin-billing.html` | web | ✅ 已实现功能 | 收费结算：待结算队列/储值支付与充值/微信·支付宝·现金已实装（`/billing/settle`，结算台形态）+ 次卡抵扣已实装（payType 5，扣 1 次实收 0）+ 小票打印已实装（服务端套打 `GET /api/v1/print/receipt/{settlementId}`，收款后打印/最近结算补打，f57f10d/1b33703）+ 结算明细多行表/整单备注（`PUT /api/v1/order/remark`，≤200 字）/队列业务单号已实装（2026-10-11）+ 到店扫码收款码展示面已实装（`GET /api/v1/pay-qrcode/{method}` 出图，缺图如实提示，2026-10-12）；原型里的处方饮片行已裁定不做（依据「收费以卡项订单结算为准」，处方计价为试算/提示口径）；在线支付真实通道（#88）红线不碰 |
| `desktop-workstation.html` | desktop | ✅ 已实现功能 | 接诊开单 + 打印/扫码外设状态 |
| `mobile-home.html` | app | ✅ 已实现功能 | 小程序首页：品牌头（右侧铃铛/头像圆钮，称呼取本机自助约期留下的称呼）/预约横幅（有本机未过期待到店预约显提醒态，否则自助约期入口，均跳 `pages/booking` tab）/今日宜养（宫格实拉，超 4 项显示「全部项目」展开）/我的卡项/养生贴士；底部四标签 tabBar（首页/预约/卡项/我的）；白标自检自顾客首页挪至 `pages/me`（工程验收用）；卡项余次无接口，`pages/card` 与首页均如实「规划功能」占位 |
| `kiosk-menu.html` | kiosk | ✅ 已实现功能 | 选服务下单：卡项网格/购物篮/手机号下单/回执 |
| `tablet-screen.html` | tablet | ✅ 已实现功能 | 广告轮播 + 叫号上屏（脱敏姓名） |

---

## 2. 截图产物（`docs/public/screenshots/`）

### 2.1 基础 14 张（1440×900 @2x → 2880×1800；tablet/desktop 1920×1080 @1.5x → 2880×1620）

| 产物 | 尺寸 | 源文件 | 渲染命令 |
|---|---|---|---|
| `admin-customer.png` | 2880×1800 | `admin-customer.html` | 见下 |
| `admin-diagnosis.png` | 2880×1800 | `admin-diagnosis.html` | 同上 |
| `admin-dash-day.png` | 2880×1800 | `admin-dash-day.html` | 同上 |
| `admin-items.png` | 2880×1800 | `admin-items.html` | 同上 |
| `admin-orders.png` | 2880×1800 | `admin-orders.html` | 同上 |
| `admin-ads.png` | 2880×1800 | `admin-ads.html` | 同上 |
| `admin-appearance.png` | 2880×1800 | `admin-appearance.html` | 同上 |
| `admin-booking.png` | 2880×1800 | `admin-booking.html` | 同上 |
| `admin-herbprescription.png` | 2880×1800 | `admin-herbprescription.html` | 同上 |
| `admin-billing.png` | 2880×1800 | `admin-billing.html` | 同上 |
| `desktop-workstation.png` | 2880×1620 | `desktop-workstation.html` | 同上 |
| `mobile-home.png` | 780×1688 | `mobile-home.html` | 390×844 @2x |
| `kiosk-menu.png` | 1800×2400 | `kiosk-menu.html` | 900×1200 @2x（竖屏） |
| `tablet-screen.png` | 2880×1620 | `tablet-screen.html` | 同上 |

### 2.2 主题切换拼图（2×2）

| 产物 | 尺寸 | 来源 | 生成方式 |
|---|---|---|---|
| `admin-themes.png` | 2880×1872 | `admin-customer.html` 四主题变体 | `convert` 逐张等比缩放 → `montage -tile 2x2 -geometry +0+18 -background '#3a3d3b'` |

---

## 3. 渲染命令（可复现）

### 3.1 环境准备

```bash
# 字体（必须，否则宋体回退无衬线）
sudo apt-get install -y fonts-noto-cjk-extra && fc-cache -f
fc-list | grep -ci "noto serif cjk"   # 应输出 ≥ 30

# Headless Chromium（Playwright 自带）
cd /home/cui/workspaces/sinomed/docs/design/mockups
```

### 3.2 基础 14 张渲染

```bash
# 统一参数：--force-device-scale-factor=2 --hide-scrollbars --no-sandbox --disable-gpu
# 管理端页面：1440x900 @2x
for f in admin-customer admin-diagnosis admin-dash-day admin-items admin-orders admin-ads admin-appearance admin-booking admin-herbprescription admin-billing; do
  /home/cui/.cache/ms-playwright/chromium-1243/chrome-linux64/chrome \
    --headless=new --no-sandbox --disable-gpu --hide-scrollbars \
    --screenshot=/home/cui/workspaces/sinomed/docs/public/screenshots/${f}.png \
    --window-size=1440,900 \
    --force-device-scale-factor=2 \
    "file:///home/cui/workspaces/sinomed/docs/design/mockups/${f}.html"
done

# 桌面/平板：1920x1080 @1.5x
for f in desktop-workstation tablet-screen; do
  /home/cui/.cache/ms-playwright/chromium-1243/chrome-linux64/chrome \
    --headless=new --no-sandbox --disable-gpu --hide-scrollbars \
    --screenshot=/home/cui/workspaces/sinomed/docs/public/screenshots/${f}.png \
    --window-size=1920,1080 \
    --force-device-scale-factor=1.5 \
    "file:///home/cui/workspaces/sinomed/docs/design/mockups/${f}.html"
done

# 移动端：390x844 @2x
/home/cui/.cache/ms-playwright/chromium-1243/chrome-linux64/chrome \
  --headless=new --no-sandbox --disable-gpu --hide-scrollbars \
  --screenshot=/home/cui/workspaces/sinomed/docs/public/screenshots/mobile-home.png \
  --window-size=390,844 \
  --force-device-scale-factor=2 \
  "file:///home/cui/workspaces/sinomed/docs/design/mockups/mobile-home.html"

# 自助机：900x1200 @2x（竖屏）
/home/cui/.cache/ms-playwright/chromium-1243/chrome-linux64/chrome \
  --headless=new --no-sandbox --disable-gpu --hide-scrollbars \
  --screenshot=/home/cui/workspaces/sinomed/docs/public/screenshots/kiosk-menu.png \
  --window-size=900,1200 \
  --force-device-scale-factor=2 \
  "file:///home/cui/workspaces/sinomed/docs/design/mockups/kiosk-menu.html"
```

### 3.3 四主题变体渲染（以 admin-customer 为例，其余同理）

```bash
cd /home/cui/workspaces/sinomed/docs/design/mockups

for theme in pine cinnabar indigo graphite; do
  # graphite = 无 data-theme（默认）
  if [ "$theme" = "graphite" ]; then
    cp admin-customer.html _theme-graphite.html
  else
    sed "s/<body>/<body data-theme=\"${theme}\">/" admin-customer.html > _theme-${theme}.html
  done
done

# 渲染四张
for theme in graphite pine cinnabar indigo; do
  /home/cui/.cache/ms-playwright/chromium-1243/chrome-linux64/chrome \
    --headless=new --no-sandbox --disable-gpu --hide-scrollbars \
    --screenshot=/home/cui/workspaces/sinomed/docs/public/screenshots/admin-customer-theme-${theme}.png \
    --window-size=1440,900 --force-device-scale-factor=2 \
    "file:///home/cui/workspaces/sinomed/docs/design/mockups/_theme-${theme}.html"
done
```

### 3.4 拼图生成 `admin-themes.png`（关键：必须先等比缩放再拼）

```bash
cd /home/cui/workspaces/sinomed/docs/public/screenshots

# 1) 逐张等比缩放到 1440x900（留白不裁剪）
for theme in graphite pine cinnabar indigo; do
  convert admin-customer-theme-${theme}.png \
    -resize 1440x900 \
    -background '#3a3d3b' -gravity center -extent 1440x900 \
    _tile-${theme}.png
done

# 2) 2x2 拼图，格间距 18px，背景色同石墨卡底
montage _tile-graphite.png _tile-pine.png _tile-cinnabar.png _tile-indigo.png \
  -tile 2x2 -geometry +0+18 -background '#3a3d3b' \
  admin-themes.png

# 3) 清理中间文件
rm _tile-*.png
```

> ⚠️ **避坑**：`montage -geometry 1440x900+0+18` 会**中心裁剪**而非等比缩放，导致四格内容被切。必须先 `convert -resize 1440x900` 再拼。

---

## 4. 数据宇宙一致性（跨页核对表）

| 实体 | 关键值 | 出现页面 |
|---|---|---|
| 顾客 王女士 | 137****3333、金卡会员、艾灸卡余 6→5 次 | admin-customer, admin-diagnosis, admin-billing, admin-dash-day, admin-orders, mobile-home, admin-herbprescription |
| 医师 王中医师 | 中医师、处方签名、接诊单创建 | admin-diagnosis, admin-dash-day, admin-herbprescription, admin-billing |
| 医师 沈中医师 | 中医师、腹部推拿、员工总结已提交 | admin-dash-day, admin-booking, admin-orders |
| 技师 苏技师 | 技师、足浴熏蒸、药师复核/调配 | admin-dash-day, admin-herbprescription |
| 馆长 顾馆长 | 馆长、巡店对账、药师审核 | admin-dash-day, admin-herbprescription |
| 卡项：艾灸调理 | ¥100/次、10次卡 | admin-items, admin-customer, admin-billing, admin-orders, kiosk-menu |
| 卡项：腹部推拿 | ¥180/次、30分钟 | admin-items, admin-customer, admin-billing, admin-orders, kiosk-menu |
| 卡项：肩颈推拿 | ¥160/次 | admin-items, admin-customer, admin-billing, admin-orders, kiosk-menu |
| 卡项：足浴熏蒸 | ¥120/次 | admin-items, admin-customer, admin-billing, admin-orders, kiosk-menu |
| 卡项：三伏灸疗程卡 | ¥880 | admin-items, admin-customer, mobile-home |

---

## 5. 文档嵌入对照（VitePress `/sinomed/` base 路径）

| 文档 | 嵌入语法 | 说明 |
|---|---|---|
| `README.md` | `![alt](/screenshots/xxx.png)` | 相对根，VitePress 自动加 base |
| `docs/index.md` | 同上 | 首页产品预览 + 未来功能规划 |
| `docs/web.md` | 同上 | 顾客档案/诊疗单/订单/广告屏四图 |
| `docs/app.md` | 同上 | 移动端首页原型（标注界面原型尚未实现） |
| `docs/design/desktop.md` | 同上 | 桌面工作站原型 |
| `docs/design/kiosk.md` | 同上 | 自助机原型 |
| `docs/design/tablet.md` | 同上 | 平板原型 + admin-ads.png |
| `docs/research/features.md` | 表格内交叉链接 | 预约/中药/收费三行尾注截图路径 |

> **注意**：Markdown 图片语法 `![](path)` 会被 VitePress 重写为 `/sinomed/path`；若在 HTML 标签 `<img src="/screenshots/...">` 中写绝对路径，**不会**被重写，会 404。

---

## 6. 最近一次全量重渲染记录

| 时间 | 触发原因 | 关键修复 |
|---|---|---|
| 2026-10-08 | 审计发现 admin-items 空洞、admin-dash-day 布局错位、admin-themes.png 裁剪错误、Noto Serif CJK 缺失 | 1) 补卡项页第 6 行 + 分页条 2) 报表柱状图移入右列栈底 3) 字体安装 35 变体 4) 拼图改用 `convert -resize` 等比缩放 |

---

## 7. 后续维护清单

- [ ] 新增页面 → 同步补 `tokens.css` 变量引用、数据宇宙一致性、渲染命令
- [ ] 主题色调整 → 同步改 `tokens.css` 四主题块 → 全量重渲 14+4 张
- [ ] 文档站 build 前 → `pnpm -C docs build` 验证图片路径无 404