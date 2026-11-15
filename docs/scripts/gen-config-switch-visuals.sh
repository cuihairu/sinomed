#!/usr/bin/env bash
# 配置开关两态视觉产物流水线（todo「配置开关效果动态文档页」验收项）。
# 真实起 server → curl 关键端点（单请求取状态码+响应体）→ ImageMagick 渲 PNG → 拼 GIF。
# 幂等可重跑：每态独立 SQLite 库，产物固定写 docs/public/config-switches/，重跑覆盖。
# 前置：./mvnw -DskipTests package（server/target/sinomed-server.jar）；依赖 curl / ImageMagick convert。
# 不进 CI：本脚本起停 9 次 server 并演示出网 jscode2session，产物入库、脚本入库、重跑本地。
#
# 覆盖 4 个真实开关：
#   sinomed.phone-lookup.mode        off / openid_only / sms_verified（3 帧 GIF）
#   sinomed.official-account.enabled false / true（2 帧 GIF）
#   demo.seed                        false / true（2 帧 GIF）
#   sinomed.wechat.app-id            未配置 / 已配置假凭据（2 帧 GIF）
# famous-doctor-surge-cap-percent 在 server 代码内无消费点（幽灵开关），不出两态图。
set -euo pipefail

# 失败即报行号与命令（curl 的 exit 3/7 等静默码在 set -e 下不会带说明）
trap 'echo "!! FAILED at line $LINENO: $BASH_COMMAND (exit $?)" >&2' ERR

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
JAR="$ROOT/server/target/sinomed-server.jar"
OUT="$ROOT/docs/public/config-switches"
PORT=18081
BASE="http://127.0.0.1:$PORT"
LOG=/tmp/cswitch-server.log

FONT='Noto-Sans-Mono-CJK-SC'
BG='#1e1e2e'; FG='#cdd6f4'; BAD='#f38ba8'; GOOD='#a6e3a1'; INFO='#89b4fa'

mkdir -p "$OUT"

# 预检：端口被残留 server 占着会让健康探测「秒过」并打到上一轮的实例上
if curl -sf --max-time 2 -o /dev/null "$BASE/api/v1/kiosk/items" 2>/dev/null; then
  echo "!! $BASE 已有服务在监听（疑似残留 sinomed-server），请先停掉再重跑"; exit 1
fi

# ---- 渲染：标题行（按语义着色）+ 正文 → PNG ----
render_frame() { # render_frame <png-name> <title> <title-color> ; body from stdin
  local png=$1 title=$2 color=$3 body
  # label 按整行取宽：超长单行（如播种后的卡项 JSON）会把单帧撑到 1200px+，GIF 两态间画布跳变；
  # 优先在空格处折（fold -s），短行不动
  body=$(cat | LC_ALL=C.UTF-8 fold -s -w 76)
  convert -background "$BG" -fill "$color" -font "$FONT" -pointsize 16 \
    -bordercolor "$BG" -border 10 label:"$title" /tmp/cswitch-title.png
  convert -background "$BG" -fill "$FG" -font "$FONT" -pointsize 14 \
    -bordercolor "$BG" -border 10 label:"$body" /tmp/cswitch-body.png
  convert /tmp/cswitch-title.png /tmp/cswitch-body.png -background "$BG" -append +repage "$OUT/$png"
}
gif_pair() { # gif_pair <out.gif> <frames...>（帧名相对 $OUT 解析，避免依赖脚本 cwd）
  local gif=$1; shift
  local frames=()
  for f in "$@"; do frames+=("$OUT/$f"); done
  # 不做 -layers optimize：文本帧裁差分后 disposal 表现不稳（抽出单帧发黑），整帧直拼更可靠且体积可控
  convert -delay 100 -loop 0 "${frames[@]}" +repage "$OUT/$gif"
}

# ---- server 起停 ----
SRV_PID=""
start_server() { # start_server <sqlite-tag> [KEY=VAL...]
  # application.yml 数据源占位符走 env：DB_URL/DB_DRIVER/DB_DIALECT（jar 默认 MySQL）
  local tag=$1; shift
  rm -f "/tmp/cswitch-$tag.db"
  env "$@" DB_DRIVER=org.sqlite.JDBC DB_DIALECT=org.hibernate.community.dialect.SQLiteDialect \
    DB_URL="jdbc:sqlite:/tmp/cswitch-$tag.db?journal_mode=WAL&busy_timeout=5000" \
    java -jar "$JAR" --server.port=$PORT \
    --spring.jpa.show-sql=false --spring.jpa.properties.hibernate.show_sql=false > "$LOG" 2>&1 &
  SRV_PID=$!
  # 健康探针用免登录只读端点（/api/v1/customer/page 需 JWT，401 探不出就绪）；
  # 冷启动建 schema 可能 >3 分钟（实测 94s ~ 210s，随机器负载浮动），轮询给足 8 分钟
  for _ in $(seq 1 480); do
    if curl -sf --max-time 5 -o /dev/null "$BASE/api/v1/kiosk/items"; then return 0; fi
    sleep 1
  done
  echo "server failed to start ($tag), log tail:"; tail -20 "$LOG"; exit 1
}
stop_server() {
  [ -n "$SRV_PID" ] && kill "$SRV_PID" 2>/dev/null || true
  wait "$SRV_PID" 2>/dev/null || true
  SRV_PID=""
}
trap stop_server EXIT

# ---- HTTP：单请求取状态码 + 响应体（REPLY_CODE / REPLY_BODY 全局回传）----
fetch() { # fetch <method> <url> [json-or-xml-body] [jwt]
  local method=$1 url=$2 body=${3:-} jwt=${4:-} resp auth=()
  [ -n "$jwt" ] && auth=(-H "Authorization: Bearer $jwt")
  if [ -n "$body" ]; then
    resp=$(curl -s --max-time 60 -w '\n%{http_code}' -X "$method" -H 'Content-Type: application/json' "${auth[@]}" -d "$body" "$url")
  else
    resp=$(curl -s --max-time 60 -w '\n%{http_code}' -X "$method" "${auth[@]}" "$url")
  fi
  REPLY_CODE=$(printf '%s' "$resp" | tail -n 1)
  REPLY_BODY=$(printf '%s' "$resp" | sed '$d' | head -c 380)
}


# ---- 断言与微信助手：expect 让本流水线兼作配置效果回归，空跑/静默失败即报错 ----
expect() { # expect <needle> <haystack> <断言说明>
  case $2 in *"$1"*) return 0;; esac
  echo "!! 断言失败：$3 —— 期望含 [$1]，实际 [$2]"; exit 1
}

sign_wx() { # 微信验签 SHA1(字典序排序[TOKEN,ts,nonce]拼接)，与 WxSignatureUtil 同规格
  # LC_ALL=C 必需：Java 侧按自然字符序，en_US.UTF-8 的 sort 走不区分大小写 collation，
  # 会把小写开头的 nonce 排到 TESTTOKEN 前 → 签名全失配、回调落 bad_signature（已实测）
  printf '%s\n%s\n%s' "$WX_TOKEN" "$1" "$2" | LC_ALL=C sort | tr -d '\n' | sha1sum | cut -d' ' -f1
}

post_wx_msg() { # post_wx_msg <account-id> <ts> <nonce> <xml>
  local id=$1 ts=$2 nonce=$3 xml=$4 sig
  sig=$(sign_wx "$ts" "$nonce")
  curl -s --max-time 30 -H 'Content-Type: text/xml; charset=utf-8' \
    -X POST "$BASE/api/v1/wx/callback/$id?signature=$sig&timestamp=$ts&nonce=$nonce" \
    --data-binary "$xml"
}

register_jwt() {
  # 登录端点在免登录白名单（SecurityConfig LoginAPI），全新库自带 admin/123 种子账号；
  # /api/v1/user/register 需认证不能用于首登
  local tok
  tok=$(curl -s -X POST -H 'Content-Type: application/json' \
    -d '{"username":"admin","password":"123"}' \
    "$BASE/api/v1/user/login" | sed -E 's/.*"token":"([^"]+)".*/\1/')
  [ -n "$tok" ] || { echo "login failed (admin/123)"; exit 1; }
  echo "$tok"
}
create_wx_account() { # create_wx_account <jwt> -> account id
  local body id
  body=$(curl -s -X POST -H 'Content-Type: application/json' -H "Authorization: Bearer $1" \
    -d '{"appId":"wxdemo000000001","accountName":"演示服务号","accountType":"service","verified":1,"enabled":1,"token":"TESTTOKEN"}' \
    "$BASE/api/v1/wx/accounts")
  id=$(printf '%s' "$body" | sed -E 's/.*"id":([0-9]+).*/\1/')
  [[ "$id" =~ ^[0-9]+$ ]] || { echo "create wx account failed: $body"; exit 1; }
  echo "$id"
}

# ---- 1/4 phone-lookup.mode：off / openid_only / sms_verified ----
echo "== 1/4 phone-lookup.mode：off / openid_only / sms_verified =="
for MODE in off openid_only sms_verified; do
  start_server "pl-$MODE" PHONE_LOOKUP_MODE="$MODE"
  fetch POST "$BASE/api/v1/kiosk/card-lookup" '{"phone":"13900003001"}'
  case $MODE in
    off)          expect "在线查卡已关闭" "$REPLY_BODY" "off 态应拒绝查卡" ;;
    openid_only)  expect "需要 openid" "$REPLY_BODY" "openid_only 态应要求 openid" ;;
    sms_verified) expect "短信验证码" "$REPLY_BODY" "sms_verified 态应要求短信验证码" ;;
  esac
  COLOR=$BAD; [ "$MODE" != "off" ] && COLOR=$INFO
  render_frame "phone-lookup-$MODE.png" "sinomed.phone-lookup.mode = $MODE" "$COLOR" <<EOF
\$ curl -X POST $BASE/api/v1/kiosk/card-lookup -d '{"phone":"13900003001"}'
HTTP $REPLY_CODE
响应: $REPLY_BODY
EOF
  stop_server
done
gif_pair "phone-lookup-toggle.gif" phone-lookup-off.png phone-lookup-openid_only.png phone-lookup-sms_verified.png

# ---- 2/4 official-account.enabled：false / true ----
echo "== 2/4 official-account.enabled：false / true =="

WX_MSG_XML='<xml><ToUserName><![CDATA[gh_demo]]></ToUserName><FromUserName><![CDATA[oFAN-demo]]></FromUserName><CreateTime>1760000000</CreateTime><MsgType><![CDATA[text]]></MsgType><Content><![CDATA[营业时间]]></Content><MsgId>77000</MsgId></xml>'
# 签名用的 token：true 态起 server 时注入 WX_OA_TOKEN=TESTTOKEN，sign_wx 按同名取值
WX_TOKEN=TESTTOKEN

# false：模块整体关闭——门控先于验签（已实测：故意错误签名仍回 success 且落 result=disabled）
# 用死签名而非合法签名正是为了证明「拦住它的是模块开关，不是验签」
start_server "wxF" WX_OA_ENABLED=false
JWT=$(register_jwt)
ACC=$(create_wx_account "$JWT")
fetch POST "$BASE/api/v1/wx/callback/$ACC?signature=deadbeef&timestamp=1760000000&nonce=nonceVfalse" "$WX_MSG_XML"
REPLY_F=$REPLY_BODY
expect "success" "$REPLY_F" "false 态回调应回 success（不静默）"
render_frame "wx-enabled-false.png" "sinomed.official-account.enabled = false" "$BAD" <<EOF
POST /api/v1/wx/callback/$ACC ← 粉丝发「营业时间」（故意用错误签名）
HTTP $REPLY_CODE，响应体: $REPLY_F
（模块整体关闭：门控先于验签，死签名也照样 success；wx_message_logs 落 result=disabled 审计）
EOF
stop_server

# true：进入业务处理——关键词命中回被动回复
start_server "wxT" WX_OA_ENABLED=true WX_OA_TOKEN=TESTTOKEN
JWT=$(register_jwt)
ACC=$(create_wx_account "$JWT")
# 规则写入必须断言成功：漏写规则会让空回复看起来像「true 也能工作」
RULE_RESP=$(curl -s -X POST -H 'Content-Type: application/json' -H "Authorization: Bearer $JWT" \
  -d "{\"accountId\":$ACC,\"ruleType\":\"keyword\",\"keyword\":\"营业时间\",\"matchType\":\"exact\",\"content\":\"营业时间 9:00-18:00\",\"priority\":0,\"enabled\":1}" \
  "$BASE/api/v1/wx/auto-replies")
expect "9:00-18:00" "$RULE_RESP" "关键词规则应创建成功"
REPLY_T=$(post_wx_msg "$ACC" 1760000001 nonceVtrue "$WX_MSG_XML")
expect "9:00-18:00" "$REPLY_T" "true 态关键词命中应被动回复规则内容"
RULE_TEXT=$(printf '%s' "$REPLY_T" | sed -n 's/.*<Content><!\[CDATA\[\(.*\)\]\]><\/Content>.*/\1/p')
render_frame "wx-enabled-true.png" "sinomed.official-account.enabled = true" "$GOOD" <<EOF
POST /api/v1/wx/callback/$ACC ← 粉丝发「营业时间」（签名通过）
响应体: 微信被动回复 XML，<MsgType>text
回复内容: $RULE_TEXT
（关键词规则命中，被动回复规则内容；wx_message_logs 落 in/out 两条 ok）
EOF
stop_server
gif_pair "wx-enabled-toggle.gif" wx-enabled-false.png wx-enabled-true.png

# ---- 3/4 demo.seed：false / true ----
echo "== 3/4 demo.seed：false / true =="
# 探针必须免登录：顾客分页需 JWT（两态都 401，看不出播种差异）；卡项列表 permitAll
ITEMS_URL="$BASE/api/v1/kiosk/items"
start_server "seedF" DEMO_SEED=false
fetch GET "$ITEMS_URL"
expect "[]" "$REPLY_BODY" "false 态卡项应为空列表（未播种）"
render_frame "demo-seed-false.png" "demo.seed = false（默认）" "$BAD" <<EOF
\$ curl "$ITEMS_URL"
HTTP $REPLY_CODE
响应: $REPLY_BODY
（不播种：卡项/顾客/员工表均为空，管理端无可演示档案）
EOF
stop_server
start_server "seedT" DEMO_SEED=true
fetch GET "$ITEMS_URL"
expect "\"id\"" "$REPLY_BODY" "true 态卡项列表应非空（已播种）"
render_frame "demo-seed-true.png" "demo.seed = true" "$GOOD" <<EOF
\$ curl "$ITEMS_URL"
HTTP $REPLY_CODE
响应: $(printf '%s' "$REPLY_BODY" | head -c 200)…
（启动播种演示员工/顾客/卡项/方剂库等，逐表按自然键幂等）
EOF
stop_server
gif_pair "demo-seed-toggle.gif" demo-seed-false.png demo-seed-true.png

# ---- 4/4 wechat 凭据：未配置 / 已配置假凭据 ----
echo "== 4/4 wechat 凭据：未配置 / 已配置假凭据 =="
# 必须 PHONE_LOOKUP_MODE=openid_only：phone-lookup.mode=off 的守卫先于凭据检查触发，
# 缺模式会让两态都回「在线查卡已关闭」，凭据开关本身一个字节都没被测到
OS_URL="$BASE/api/v1/kiosk/openid-session"
start_server "credM" PHONE_LOOKUP_MODE=openid_only
fetch POST "$OS_URL" '{"code":"demo-code"}'
expect "微信凭据未配置" "$REPLY_BODY" "未配置态应报凭据缺失"
render_frame "wechat-credential-missing.png" "sinomed.wechat.app-id =（未配置，默认）" "$BAD" <<EOF
\$ curl -X POST "$OS_URL" -d '{"code":"demo-code"}'
HTTP $REPLY_CODE
响应: $REPLY_BODY
（激活前置：凭据未配置 → 400 落审计，不静默）
EOF
stop_server
start_server "credS" PHONE_LOOKUP_MODE=openid_only WECHAT_APP_ID=wxfake000000000 WECHAT_APP_SECRET=fake-secret
fetch POST "$OS_URL" '{"code":"demo-code"}'
expect "微信登录失败" "$REPLY_BODY" "已配置态应出网换取 openid 并映射微信错误"
render_frame "wechat-credential-set.png" "sinomed.wechat.app-id = wxfake000000000（假凭据演示出网）" "$GOOD" <<EOF
\$ curl -X POST "$OS_URL" -d '{"code":"demo-code"}'
HTTP $REPLY_CODE
响应: $REPLY_BODY
（已配置 → 真实出网 jscode2session；微信侧拒绝假 appid（errcode=40013）→ 400 落审计）
EOF
stop_server
gif_pair "wechat-credential-toggle.gif" wechat-credential-missing.png wechat-credential-set.png

echo "== done =="
ls -la "$OUT"
