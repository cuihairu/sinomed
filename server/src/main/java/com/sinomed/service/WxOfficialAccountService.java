package com.sinomed.service;

import com.sinomed.entity.CustomerEntity;
import com.sinomed.entity.SmsCodeEntity;
import com.sinomed.entity.WxAccountEntity;
import com.sinomed.entity.WxAutoReplyEntity;
import com.sinomed.entity.WxBindingLogEntity;
import com.sinomed.entity.WxFanEntity;
import com.sinomed.entity.WxMaterialEntity;
import com.sinomed.entity.WxMenuEntity;
import com.sinomed.entity.WxMessageLogEntity;
import com.sinomed.repository.CustomerRepository;
import com.sinomed.repository.SmsCodeRepository;
import com.sinomed.repository.WxAccountRepository;
import com.sinomed.repository.WxAutoReplyRepository;
import com.sinomed.repository.WxBindingLogRepository;
import com.sinomed.repository.WxFanRepository;
import com.sinomed.repository.WxMaterialRepository;
import com.sinomed.repository.WxMenuRepository;
import com.sinomed.repository.WxMessageLogRepository;
import com.sinomed.util.DateUtil;
import com.sinomed.util.WxSignatureUtil;
import me.chanjar.weixin.mp.api.WxMpMessageRouter;
import me.chanjar.weixin.mp.bean.message.WxMpXmlMessage;
import me.chanjar.weixin.mp.bean.message.WxMpXmlOutMessage;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.criteria.Predicate;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 公众号接入（第一期骨架，设计稿 docs/design/wechat-official-account.md §5/§6）：
 * URL 验签、明文消息验签+排重+全量落流水、subscribe/unsubscribe 粉丝维护、
 * 关键词/欢迎语/兜底被动回复、能力矩阵探测。
 * 「不静默」口径：模块关/账号停用/未知账号一律 200 拒处理（回 "success"，不让微信重试）并落拒绝审计；
 * 验签失败回空 body。安全/兼容模式（AES-256-CBC 信封，设计稿 §5.3）由 WxMsgCryptor 承担：
 * msg_signature 四段验签 + 解密进主流程，安全模式回复密封（官方规范手写实现，不引 SDK）。
 */
@Slf4j
@Component
public class WxOfficialAccountService {

    private final WxAccountRepository accountRepository;
    private final WxFanRepository fanRepository;
    private final WxAutoReplyRepository autoReplyRepository;
    private final WxMessageLogRepository messageLogRepository;
    private final WxBindingLogRepository bindingLogRepository;
    private final SmsCodeRepository smsCodeRepository;
    private final CustomerRepository customerRepository;
    private final WxMaterialRepository materialRepository;
    private final WxMenuRepository menuRepository;
    private final WxOauthTicketStore ticketStore;
    private final RestTemplate wechatRestTemplate;
    private final WxMsgCryptor msgCryptor;
    private final WxMpServiceProvider wxMpServiceProvider;

    /** 账号级消息路由（设计稿 §5.1：WxMpMessageRouter 按 accountId 实例化并缓存，不能单例跨账号） */
    private final Map<Long, WxMpMessageRouter> routers = new ConcurrentHashMap<>();

    /** 绑定短信验证码有效期（分钟），与 Kiosk 短信通道同口径 */
    private static final long BIND_SMS_TTL_MINUTES = 5;

    /** OAuth state 有效期（秒，D-OA3：无状态 HMAC，10 分钟内完成授权回跳） */
    private static final long OAUTH_STATE_TTL_SECONDS = 600;

    /** 规则类型与匹配方式合法值（设计稿 §4.4：subscribe/keyword/fallback × exact/contains） */
    private static final Set<String> RULE_TYPES = Set.of("subscribe", "keyword", "fallback");
    private static final Set<String> MATCH_TYPES = Set.of("exact", "contains");

    /** 菜单类型合法值（设计稿 §4.3，微信自定义菜单协议全集） */
    private static final Set<String> MENU_TYPES = Set.of("view", "click", "scancode_push", "scancode_waitmsg",
            "pic_sysphoto", "pic_photo_or_album", "pic_weixin", "location_select", "view_miniprogram");
    /** 一级菜单仅允许的类型（微信协议：扫码/媒体类只能做二级） */
    private static final Set<String> TOP_MENU_TYPES = Set.of("view", "click");
    /** 需要事件 key 的菜单类型（微信协议：除 view 系外回调都要带 key 路由） */
    private static final Set<String> KEY_MENU_TYPES = Set.of("click", "scancode_push", "scancode_waitmsg",
            "pic_sysphoto", "pic_photo_or_album", "pic_weixin", "location_select");

    /** 模块总开关（env WX_OA_ENABLED）；缺省 false=整体关闭，回调拒处理 */
    @Value("${sinomed.official-account.enabled:false}")
    private boolean enabled;

    /** 消息推送 Token（env WX_OA_TOKEN）；第一期单账号，验签用 */
    @Value("${sinomed.official-account.token:}")
    private String token;

    /** 素材落盘根目录（env WX_OA_MATERIAL_DIR），相对路径入库、绝对寻址靠它拼 */
    @Value("${sinomed.official-account.material-dir:data/wxmaterials}")
    private String materialDir;

    /** 单素材下载上限（字节，env WX_OA_MATERIAL_MAX_BYTES），超限跳过不放大 */
    @Value("${sinomed.official-account.material-max-bytes:10485760}")
    private long materialMaxBytes;

    /** OAuth 回跳 H5 基址（env WX_OA_OAUTH_REDIRECT_BASE，如 https://clinic.example.com）；空则网页授权拒启动 */
    @Value("${sinomed.official-account.oauth-redirect-base:}")
    private String oauthRedirectBase;

    /** 微信 API 出网基址（默认官方域；测试指向本地桩，D-OA4） */
    @Value("${sinomed.official-account.wechat-api-base-url:https://api.weixin.qq.com}")
    private String wechatApiBaseUrl;

    /** 素材下载客户端：连接/单请求均 3s 上限，护住微信回调 5s 窗口 */
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3)).build();

    public WxOfficialAccountService(WxAccountRepository accountRepository,
                                    WxFanRepository fanRepository,
                                    WxAutoReplyRepository autoReplyRepository,
                                    WxMessageLogRepository messageLogRepository,
                                    WxBindingLogRepository bindingLogRepository,
                                    SmsCodeRepository smsCodeRepository,
                                    CustomerRepository customerRepository,
                                    WxMaterialRepository materialRepository,
                                    WxMenuRepository menuRepository,
                                    WxOauthTicketStore ticketStore,
                                    RestTemplate wechatRestTemplate,
                                    WxMsgCryptor msgCryptor,
                                    WxMpServiceProvider wxMpServiceProvider) {
        this.accountRepository = accountRepository;
        this.fanRepository = fanRepository;
        this.autoReplyRepository = autoReplyRepository;
        this.messageLogRepository = messageLogRepository;
        this.bindingLogRepository = bindingLogRepository;
        this.smsCodeRepository = smsCodeRepository;
        this.customerRepository = customerRepository;
        this.materialRepository = materialRepository;
        this.menuRepository = menuRepository;
        this.ticketStore = ticketStore;
        this.wechatRestTemplate = wechatRestTemplate;
        this.msgCryptor = msgCryptor;
        this.wxMpServiceProvider = wxMpServiceProvider;
    }

    /** GET URL 验证：通过原样返回 echostr；拒绝路径回 "success"/空并落审计 */
    public String verifyUrl(long accountId, String signature, String timestamp, String nonce, String echostr) {
        if (!enabled) {
            logReject(accountId, "disabled", "none", null, null);
            return "success";
        }
        Optional<WxAccountEntity> account = accountRepository.findById(accountId)
                .filter(a -> a.getEnabled() != null && a.getEnabled() == 1);
        if (account.isEmpty()) {
            logReject(accountId, "account_missing", "none", null, null);
            return "success";
        }
        if (!WxSignatureUtil.check(token, timestamp, nonce, signature)) {
            logReject(accountId, "bad_signature", "none", null, null);
            return "";
        }
        return echostr;
    }

    /**
     * POST 消息推送：验签 → （加密信封则解密）→ 排重 → 落流水 → 路由（事件/文本）→ 被动回复或 "success"。
     * 验签双轨（设计稿 §5.3）：报文含 Encrypt 元素（安全/兼容模式）按官方口径走 msg_signature
     * 四段验签 + AES 解密，不再做三段 signature；明文报文沿用三段 signature。安全模式账号
     * 收到明文推送按伪造拒绝（真实微信不会这样发）；安全模式回复包加密信封，success 保持明文。
     */
    public String onMessage(long accountId, String signature, String timestamp, String nonce,
                            String msgSignature, String xmlBody) {
        if (!enabled) {
            logReject(accountId, "disabled", "none", null, null);
            return "success";
        }
        Optional<WxAccountEntity> account = accountRepository.findById(accountId);
        if (account.isEmpty() || account.get().getEnabled() == null || account.get().getEnabled() == 0) {
            logReject(accountId, "account_missing", "none", null, null);
            return "success";
        }

        Map<String, String> envelope = parseXml(xmlBody);
        String encrypt = envelope.get("Encrypt");
        String innerXml = xmlBody;
        boolean encrypted = encrypt != null && !encrypt.isBlank();
        if (encrypted) {
            if (!msgCryptor.checkMsgSignature(token, timestamp, nonce, encrypt, msgSignature)) {
                logReject(accountId, "bad_msg_signature", "none", null, null);
                return "";
            }
            try {
                innerXml = msgCryptor.decrypt(account.get().getAesKey(), account.get().getAppId(), encrypt);
            } catch (IllegalArgumentException e) {
                logReject(accountId, "bad_encrypt", "none", null, null);
                return "";
            }
        } else {
            if ("safe".equals(account.get().getEncryptMode())) {
                logReject(accountId, "missing_encrypt", "none", null, null);
                return "";
            }
            if (!WxSignatureUtil.check(token, timestamp, nonce, signature)) {
                logReject(accountId, "bad_signature", "none", null, null);
                return "";
            }
        }

        final WxMpXmlMessage msg;
        try {
            msg = WxMpXmlMessage.fromXml(innerXml);
        } catch (RuntimeException e) {
            // 设计原则：不可解析报文不抛异常（微信会重试放大），落拒绝审计回 success
            logReject(accountId, "bad_xml", "none", null, null);
            return "success";
        }
        String msgType = msg.getMsgType() == null ? "none" : msg.getMsgType();
        String fromUser = msg.getFromUser();
        String toUser = msg.getToUser();
        String event = msg.getEvent();
        String content = msg.getContent();
        String msgId = msg.getMsgId() == null ? null : String.valueOf(msg.getMsgId());
        String createTime = msg.getCreateTime() == null ? null : String.valueOf(msg.getCreateTime());

        String dedupeKey = msgId != null ? "M:" + msgId
                : (fromUser != null && createTime != null && event != null ? "E:" + fromUser + ":" + createTime + ":" + event : null);
        if (dedupeKey != null && messageLogRepository.existsByDedupeKey(dedupeKey)) {
            logReject(accountId, "duplicate", msgType, fromUser, toUser);
            return "success";
        }

        logMessage(accountId, "in", msgType, fromUser, toUser, event != null ? event : content, msgId, dedupeKey);

        // 路由骨架（设计稿 §5.4）：SDK WxMpMessageRouter 按账号实例化 + 同步规则（5s 窗口）；
        // 规则不命中或 handler 无回复时 Route 返回 null → 回 "success"。repo 侧排重仍由 DB 承担，
        // SDK 内置内存排重置为 no-op，避免与 wx_message_logs.dedupe_key 双写冲突。
        WxMpXmlOutMessage out = router(account.get()).route(msg);
        String reply = out == null ? "success" : out.toXml();
        return sealReply(account.get(), encrypted, nonce, reply);
    }

    /** 账号级消息路由：懒建缓存（设计稿 §5.1，按 accountId 实例化；配置变更后由 evict 重建） */
    private WxMpMessageRouter router(WxAccountEntity account) {
        return routers.computeIfAbsent(account.getId(), id -> buildRouter(account));
    }

    /** evict 用户：账号凭据/开关变化后清路由与服务缓存，下次回调按新实体重建 */
    public void evictAccount(long accountId) {
        wxMpServiceProvider.evict(accountId);
        routers.remove(accountId);
    }

    /**
     * 组装账号消息路由（订阅/取关粉丝维护、文本关键词/兜底、图片素材拉取+兜底、事件日志只读）：
     * 规则从细到粗、全部同步（微信 5s 窗口内必须回包）；事件类除订阅/取关外无回复（只落流水），
     * 非文本媒体走兜底；未识别类型最后兜底不抛异常（微信会重试放大）。
     */
    private WxMpMessageRouter buildRouter(WxAccountEntity account) {
        long accountId = account.getId();
        WxMpMessageRouter router = new WxMpMessageRouter(wxMpServiceProvider.forAccount(account));
        // SDK 内置单例内存排重会与 wx_message_logs.dedupe_key DB 排重冲突，置为 no-op（见 router() 注释）
        router.setMessageDuplicateChecker(messageId -> false);

        router.rule().async(false)
                .event("subscribe")
                .handler((message, context, mpService, session) -> {
                    upsertFan(accountId, message.getFromUser(), 1, true);
                    return textReply(accountId, message, firstRule(accountId, "subscribe"));
                }).end();

        router.rule().async(false)
                .event("unsubscribe")
                .handler((message, context, mpService, session) -> {
                    upsertFan(accountId, message.getFromUser(), 0, false);
                    return null; // 微信不收取关回复
                }).end();

        router.rule().async(false)
                .msgType("event") // 其余事件（CLICK/VIEW/scan）：第一期只落流水，不做业务路由
                .handler((message, context, mpService, session) -> null).end();

        router.rule().async(false)
                .msgType("text")
                .handler((message, context, mpService, session) -> {
                    String matched = matchKeywordReply(accountId, message.getContent());
                    if (matched == null) {
                        matched = firstRule(accountId, "fallback");
                    }
                    return textReply(accountId, message, matched);
                }).end();

        router.rule().async(false)
                .msgType("image")
                .handler((message, context, mpService, session) -> {
                    // 设计稿 §4.5 第一期：图片消息拉取 PicUrl 落 wx_materials（type=temp，供管理端查看）
                    captureMaterial(accountId, message.getMediaId(), message.getPicUrl());
                    return textReply(accountId, message, firstRule(accountId, "fallback"));
                }).end();

        // voice/video/link/location 等非文本媒体：落日志 + 兜底（不做识别处理）
        router.rule().async(false)
                .handler((message, context, mpService, session) ->
                        textReply(accountId, message, firstRule(accountId, "fallback"))).end();
        return router;
    }

    /**
     * 安全模式出站密封（设计稿 §5.3）：入站为加密信封且账号 encrypt_mode=safe 且确有回复 XML 时，
     * 包成官方信封（Encrypt + MsgSignature + TimeStamp + Nonce）；"success"/空体/兼容模式保持明文。
     * TimeStamp/签名用服务器当前时间（官方示例同款），Nonce 回带入站值。
     */
    private String sealReply(WxAccountEntity account, boolean encryptedInbound, String nonce, String reply) {
        if (!encryptedInbound || reply == null || !reply.startsWith("<xml>")
                || !"safe".equals(account.getEncryptMode())) {
            return reply;
        }
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String cipher = msgCryptor.encrypt(account.getAesKey(), account.getAppId(), reply);
        String sign = msgCryptor.signMsg(token, timestamp, nonce, cipher);
        return "<xml><Encrypt><![CDATA[" + cipher + "]]></Encrypt>"
                + "<MsgSignature><![CDATA[" + sign + "]]></MsgSignature>"
                + "<TimeStamp>" + timestamp + "</TimeStamp>"
                + "<Nonce><![CDATA[" + (nonce == null ? "" : nonce) + "]]></Nonce></xml>";
    }

    /** 能力矩阵（设计稿 §6.1/§6.2）：account_type + verified 两字段驱动，不写死枚举分支 */
    public Map<String, Object> capabilities(long accountId) {
        WxAccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("公众号账号不存在"));
        boolean service = "service".equals(account.getAccountType());
        boolean verified = account.getVerified() != null && account.getVerified() == 1;
        Map<String, Object> caps = new LinkedHashMap<>();
        caps.put("accountId", account.getId());
        caps.put("accountName", account.getAccountName());
        caps.put("accountType", account.getAccountType());
        caps.put("verified", verified);
        caps.put("callback", true);          // 三档账号都可回调（矩阵 §6.1）
        caps.put("materials", true);          // 素材上传三档都可
        caps.put("webAuth", true);            // 网页授权三档都可
        caps.put("menuManagement", service || verified);
        caps.put("customerService", service || verified);
        caps.put("massMessaging", service || verified);
        caps.put("templateMessage", service); // 模板消息仅服务号
        return caps;
    }

    /** 粉丝维护（设计稿 §4.2）：关注 upsert subscribe=1，取关注保留行 subscribe=0（customer_id 不动） */
    private void upsertFan(long accountId, String openid, int subscribe, boolean isSubscribe) {
        if (openid == null) {
            return;
        }
        WxFanEntity fan = fanRepository.findByAccountIdAndOpenid(accountId, openid).orElseGet(WxFanEntity::new);
        fan.setAccountId(accountId);
        fan.setOpenid(openid);
        fan.setSubscribe(subscribe);
        if (isSubscribe) {
            fan.setSubscribeTime(new Date());
        } else {
            fan.setUnsubscribeTime(new Date());
        }
        fanRepository.save(fan);
    }

    /** 关键词匹配：启用规则按 priority 升序，exact 全等 / contains 包含，首个命中生效 */
    private String matchKeywordReply(long accountId, String content) {
        if (content == null) {
            return null;
        }
        List<WxAutoReplyEntity> rules = autoReplyRepository
                .findByAccountIdAndRuleTypeAndEnabledOrderByPriorityAsc(accountId, "keyword", 1);
        for (WxAutoReplyEntity rule : rules) {
            if (rule.getKeyword() == null) {
                continue;
            }
            boolean hit = "contains".equals(rule.getMatchType())
                    ? content.contains(rule.getKeyword())
                    : content.equals(rule.getKeyword());
            if (hit) {
                return rule.getContent();
            }
        }
        return null;
    }

    private String firstRule(long accountId, String ruleType) {
        return autoReplyRepository
                .findByAccountIdAndRuleTypeAndEnabledOrderByPriorityAsc(accountId, ruleType, 1)
                .stream().findFirst().map(WxAutoReplyEntity::getContent).orElse(null);
    }

    /**
     * 被动文本回复（设计稿 §5.4）：规则内容为空返回 null（上层回 "success"，5s 内无响应微信会重试）；
     * 出站落流水后由 SDK builder 产出 XML（子节点 CDATA 转义由 SDK 承担）。
     */
    private WxMpXmlOutMessage textReply(long accountId, WxMpXmlMessage msg, String replyContent) {
        if (msg.getFromUser() == null || replyContent == null || replyContent.isBlank()) {
            return null;
        }
        logMessage(accountId, "out", "text", msg.getToUser(), msg.getFromUser(), replyContent, null, null);
        return WxMpXmlOutMessage.TEXT()
                .toUser(msg.getFromUser())
                .fromUser(msg.getToUser())
                .content(replyContent)
                .build();
    }

    private void logReject(long accountId, String result, String msgType, String fromUser, String toUser) {
        WxMessageLogEntity log = new WxMessageLogEntity();
        log.setAccountId(accountId);
        log.setDirection("reject");
        log.setMsgType(msgType);
        log.setFromUser(fromUser);
        log.setToUser(toUser);
        log.setResult(result);
        messageLogRepository.save(log);
    }

    private void logMessage(long accountId, String direction, String msgType, String fromUser, String toUser,
                            String content, String msgId, String dedupeKey) {
        WxMessageLogEntity log = new WxMessageLogEntity();
        log.setAccountId(accountId);
        log.setDirection(direction);
        log.setMsgType(msgType);
        log.setFromUser(fromUser);
        log.setToUser(toUser);
        // 流水留痕截断：超长文本只存前 500 字（审计价值在「说了什么类别」，不是全文备份）
        log.setContent(content != null && content.length() > 500 ? content.substring(0, 500) : content);
        log.setMsgId(msgId != null ? parseLongOrNull(msgId) : null);
        log.setDedupeKey(dedupeKey);
        log.setResult("ok");
        messageLogRepository.save(log);
    }

    private static Long parseLongOrNull(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 绑定取码（粉丝 H5 侧）：复用 Kiosk 短信通道桩（sms_codes，一次性、5 分钟有效），
     * 无真实短信网关、发码只落库与日志；模块关/账号停用拒绝并审计（不静默）。
     */
    public String issueBindCode(long accountId, String phone) {
        if (!enabled) {
            auditBinding(accountId, null, phone, null, "disabled");
            throw new IllegalArgumentException("公众号模块未开启，无法发送验证码");
        }
        if (accountRepository.findById(accountId).filter(a -> a.getEnabled() != null && a.getEnabled() == 1).isEmpty()) {
            auditBinding(accountId, null, phone, null, "account_missing");
            throw new IllegalArgumentException("公众号账号不存在或已停用，无法发送验证码");
        }
        if (phone == null || !phone.matches("^1\\d{10}$")) {
            throw new IllegalArgumentException("手机号格式不对：应为 1 开头的 11 位数字");
        }
        SmsCodeEntity code = new SmsCodeEntity();
        code.setPhone(phone);
        code.setCode(String.valueOf(100000 + ThreadLocalRandom.current().nextInt(900000)));
        code.setUsed(0);
        code.setExpiresAt(new Date(System.currentTimeMillis() + BIND_SMS_TTL_MINUTES * 60_000L));
        smsCodeRepository.save(code);
        // 无短信网关：演示环境的送达方式（测试用 SmsCodeRepository 直接取码）
        org.slf4j.LoggerFactory.getLogger(WxOfficialAccountService.class)
                .info("SMS_CODE_DEMO phone={} code={}", phone, code.getCode());
        return "验证码已发送（5 分钟内有效，演示环境请向店员索取）";
    }

    /**
     * 粉丝↔顾客绑定（设计稿第一期交付物 5，复用 Kiosk /openid-bind「手机号 + 一次性短信验证码」双因子口径）：
     * 校验通过写 wx_fans.customer_id，每次尝试（含拒绝）落 wx_binding_logs（§4.8）。
     * 粉丝行不存在时补建（subscribe=1）：菜单/H5 入口仅关注用户可见，绑定价值大于粉丝计数精度，
     * 管理端可人工改（已代决）。顾客冲突按公众号内部语义判（wx_fans 同账号占位），
     * 与 customer.openid 的小程序绑定互不影响。
     */
    public String bind(long accountId, String openid, String phone, String smsCode) {
        if (!enabled) {
            auditBinding(accountId, openid, phone, null, "disabled");
            throw new IllegalArgumentException("公众号模块未开启，无法绑定");
        }
        if (accountRepository.findById(accountId).filter(a -> a.getEnabled() != null && a.getEnabled() == 1).isEmpty()) {
            auditBinding(accountId, openid, phone, null, "account_missing");
            throw new IllegalArgumentException("公众号账号不存在或已停用，无法绑定");
        }
        if (openid == null || openid.isBlank()) {
            auditBinding(accountId, openid, phone, null, "missing_openid");
            throw new IllegalArgumentException("需要 openid（网页授权登录态）才能绑定");
        }
        if (phone == null || phone.isBlank() || smsCode == null || smsCode.isBlank()) {
            auditBinding(accountId, openid, phone, null, "missing_code");
            throw new IllegalArgumentException("需要手机号与短信验证码才能绑定");
        }
        if (!phone.matches("^1\\d{10}$")) {
            throw new IllegalArgumentException("手机号格式不对：应为 1 开头的 11 位数字");
        }
        // 自证身份与查卡 sms_verified 同一通道：一次性验证码，消耗即绑定凭据（同 Kiosk 顺序：先验码后查档案）
        consumeBindSmsCode(accountId, openid, phone, smsCode);
        CustomerEntity customer = customerRepository.findByPhone(phone).orElse(null);
        if (customer == null) {
            auditBinding(accountId, openid, phone, null, "no_customer");
            throw new IllegalArgumentException("该手机号无顾客档案，请到前台建档");
        }
        Optional<WxFanEntity> occupied = fanRepository.findByAccountIdAndCustomerId(accountId, customer.getId());
        if (occupied.isPresent() && !occupied.get().getOpenid().equals(openid)) {
            auditBinding(accountId, openid, phone, customer.getId(), "conflict");
            throw new IllegalArgumentException("该顾客档案已绑定其他粉丝，请到前台处理");
        }
        WxFanEntity fan = fanRepository.findByAccountIdAndOpenid(accountId, openid).orElseGet(() -> {
            WxFanEntity created = new WxFanEntity();
            created.setAccountId(accountId);
            created.setOpenid(openid);
            created.setSubscribe(1);
            return fanRepository.save(created);
        });
        if (fan.getCustomerId() != null && !fan.getCustomerId().equals(customer.getId())) {
            auditBinding(accountId, openid, phone, customer.getId(), "bound_other");
            throw new IllegalArgumentException("该粉丝已绑定其他顾客档案，请先解绑或到前台处理");
        }
        fan.setCustomerId(customer.getId());
        fanRepository.save(fan);
        auditBinding(accountId, openid, phone, customer.getId(), "ok");
        if (occupied.isPresent()) {
            return "该粉丝已绑定此档案，无需重复绑定";
        }
        return "已绑定到「" + customer.getName() + "」的顾客档案";
    }

    /** 管理端解绑（设计稿 §4.7：清 customer_id + 审计）；顾客取关不自动解绑，换人关注属线下场景走管理端 */
    public String unbind(long accountId, String openid) {
        WxFanEntity fan = fanRepository.findByAccountIdAndOpenid(accountId, openid).orElse(null);
        if (fan == null) {
            auditBinding(accountId, openid, null, null, "no_fan");
            throw new IllegalArgumentException("该 openid 无粉丝记录，无需解绑");
        }
        if (fan.getCustomerId() == null) {
            auditBinding(accountId, openid, null, null, "not_bound");
            throw new IllegalArgumentException("该粉丝未绑定顾客档案，无需解绑");
        }
        Long customerId = fan.getCustomerId();
        fan.setCustomerId(null);
        fanRepository.save(fan);
        auditBinding(accountId, openid, null, customerId, "unbind");
        return "已解除粉丝与顾客档案的绑定";
    }

    /** 流水列表的顾客姓名展示（设计稿第一期验收：已绑定顾客发消息 → 消息流水可见对应顾客姓名）；未绑定回 null */
    public String resolveCustomerName(Long accountId, String fromUser) {
        if (accountId == null || fromUser == null) {
            return null;
        }
        return fanRepository.findByAccountIdAndOpenid(accountId, fromUser)
                .map(WxFanEntity::getCustomerId)
                .flatMap(customerRepository::findById)
                .map(CustomerEntity::getName)
                .orElse(null);
    }

    /**
     * 网页授权发起（设计稿 §5.6，D-OA1..D-OA3）：拼 snsapi_base 授权地址，
     * state=「accountId|nonce|过期秒」+ HMAC-SHA1(token) 无状态防伪（10 分钟）。
     * 拒绝（模块关/账号停用/缺 redirect 基址）不静默：落 reject 审计并抛 400。
     */
    public String oauthAuthorizeUrl(long accountId) {
        if (!enabled) {
            logReject(accountId, "disabled", "oauth", null, null);
            throw new IllegalArgumentException("公众号模块未开启，无法发起网页授权");
        }
        WxAccountEntity account = accountRepository.findById(accountId)
                .filter(a -> a.getEnabled() != null && a.getEnabled() == 1).orElse(null);
        if (account == null) {
            logReject(accountId, "account_missing", "oauth", null, null);
            throw new IllegalArgumentException("公众号账号不存在或已停用，无法发起网页授权");
        }
        if (isBlank(oauthRedirectBase)) {
            logReject(accountId, "missing_redirect_base", "oauth", null, null);
            throw new IllegalArgumentException("未配置网页授权回跳基址（oauth-redirect-base），无法发起授权");
        }
        long exp = System.currentTimeMillis() / 1000 + OAUTH_STATE_TTL_SECONDS;
        String payload = accountId + "|" + UUID.randomUUID().toString().replace("-", "").substring(0, 8) + "|" + exp;
        String state = payload + ":" + hmacSha1Hex(token, payload);
        return "https://open.weixin.qq.com/connect/oauth2/authorize"
                + "?appid=" + urlEncode(account.getAppId())
                + "&redirect_uri=" + urlEncode(oauthRedirectBase + "/api/v1/wx/oauth/" + accountId + "/callback")
                + "&response_type=code&scope=snsapi_base&state=" + urlEncode(state)
                + "#wechat_redirect";
    }

    /**
     * 网页授权回调（设计稿 §5.6）：state 验签 → code 换 openid（出网可配基址）→
     * 发一次性 ticket → 回跳 H5 绑定页（#ticket=...）。openid 不进前端落地地址，
     * H5 凭 ticket 兑换；失败路径全部落 reject 审计并明确报错。
     */
    public String oauthCallbackRedirectUrl(long accountId, String code, String state) {
        if (!enabled) {
            logReject(accountId, "disabled", "oauth", null, null);
            throw new IllegalArgumentException("公众号模块未开启");
        }
        WxAccountEntity account = accountRepository.findById(accountId)
                .filter(a -> a.getEnabled() != null && a.getEnabled() == 1).orElse(null);
        if (account == null) {
            logReject(accountId, "account_missing", "oauth", null, null);
            throw new IllegalArgumentException("公众号账号不存在或已停用");
        }
        if (!verifyOauthState(accountId, state)) {
            logReject(accountId, "bad_state", "oauth", null, null);
            throw new IllegalArgumentException("授权 state 校验失败，请重新发起授权");
        }
        String openid = exchangeOpenid(account, code);
        if (openid == null) {
            logReject(accountId, "bad_exchange", "oauth", null, null);
            throw new IllegalArgumentException("授权 code 换取 openid 失败，请重新发起授权");
        }
        return oauthRedirectBase + "/h5/wx/bind?account=" + accountId + "#ticket=" + ticketStore.issue(accountId, openid);
    }

    /**
     * H5 凭 ticket 兑换 openid（一次性，D-OA5）：无效/过期/错账号拒绝并落绑定审计，
     * 兑到的 openid 直接进 bind 主流程（手机号+验证码双因子不变）。
     */
    public String redeemTicket(long accountId, String ticket) {
        return ticketStore.redeem(accountId, ticket).orElseGet(() -> {
            auditBinding(accountId, null, null, null, "bad_ticket");
            throw new IllegalArgumentException("授权 ticket 无效或已过期，请重新发起网页授权");
        });
    }

    /** state 验签：结构 payload:签名 → 常量时间比对 HMAC → 账号一致 + 未过期 */
    private boolean verifyOauthState(long accountId, String state) {
        if (isBlank(state) || isBlank(token)) {
            return false;
        }
        int split = state.lastIndexOf(':');
        if (split <= 0 || split == state.length() - 1) {
            return false;
        }
        String payload = state.substring(0, split);
        byte[] expected = hmacSha1Hex(token, payload).getBytes(StandardCharsets.US_ASCII);
        byte[] actual = state.substring(split + 1).getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(expected, actual)) {
            return false;
        }
        String[] parts = payload.split("\\|");
        if (parts.length != 3) {
            return false;
        }
        try {
            return Long.parseLong(parts[0]) == accountId
                    && System.currentTimeMillis() / 1000 <= Long.parseLong(parts[2]);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** code→openid 换取（D-OA4：可配基址；AppSecret 取自动态解密列；RestTemplate+DTO 同 WechatClient 口径）；失败回 null 不抛 */
    private String exchangeOpenid(WxAccountEntity account, String code) {
        if (isBlank(code) || isBlank(account.getAppSecret())) {
            return null;
        }
        try {
            String url = wechatApiBaseUrl + "/sns/oauth2/access_token?appid=" + urlEncode(account.getAppId())
                    + "&secret=" + urlEncode(account.getAppSecret())
                    + "&code=" + urlEncode(code) + "&grant_type=authorization_code";
            AccessTokenExchange resp = wechatRestTemplate.getForObject(url, AccessTokenExchange.class);
            if (resp == null || (resp.getErrcode() != null && resp.getErrcode() != 0) || isBlank(resp.getOpenid())) {
                return null;
            }
            return resp.getOpenid();
        } catch (Exception e) {
            log.warn("wx oauth exchange failed account={}: {}", account.getId(), e.getMessage());
            return null;
        }
    }

    /** 微信 sns/oauth2/access_token 响应（snsapi_base 只需 openid）；errcode 非 0 时无 openid */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class AccessTokenExchange {
        private String openid;
        private Integer errcode;
    }

    private static String hmacSha1Hex(String key, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            StringBuilder hex = new StringBuilder();
            for (byte b : mac.doFinal(payload.getBytes(StandardCharsets.UTF_8))) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("HMAC 计算失败", e);
        }
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private void consumeBindSmsCode(long accountId, String openid, String phone, String submitted) {
        SmsCodeEntity issued = smsCodeRepository.findFirstByPhoneOrderByIdDesc(phone).orElse(null);
        if (issued == null) {
            auditBinding(accountId, openid, phone, null, "bad_code");
            throw new IllegalArgumentException("验证码不存在，请先获取验证码");
        }
        if (issued.getUsed() != null && issued.getUsed() == 1) {
            auditBinding(accountId, openid, phone, null, "used_code");
            throw new IllegalArgumentException("验证码已使用，请重新获取");
        }
        if (issued.getExpiresAt() != null && issued.getExpiresAt().before(new Date())) {
            auditBinding(accountId, openid, phone, null, "expired_code");
            throw new IllegalArgumentException("验证码已过期，请重新获取");
        }
        if (!issued.getCode().equals(submitted.trim())) {
            auditBinding(accountId, openid, phone, null, "bad_code");
            throw new IllegalArgumentException("验证码不对，请核对后重试");
        }
        issued.setUsed(1);
        smsCodeRepository.save(issued);
    }

    private void auditBinding(long accountId, String openid, String phone, Long customerId, String result) {
        WxBindingLogEntity row = new WxBindingLogEntity();
        row.setAccountId(accountId);
        row.setFanOpenid(openid);
        row.setPhone(phone);
        row.setCustomerId(customerId);
        row.setResult(result);
        bindingLogRepository.save(row);
    }

    /**
     * 管理端只读流水列表（设计稿第一期交付物 4）：按账号/粉丝/方向/类型/结果/时间窗过滤（AND 语义），
     * 排序由 Pageable 携带（默认 createTime、id 倒序=最新在前）。不依赖模块开关——
     * disabled 期间的 reject 审计也要可查（不静默）。不做对话式 UI。
     */
    public Page<WxMessageLogEntity> pageLogs(Long accountId, String fromUser, String direction, String msgType,
                                             String result, String startTime, String endTime, Pageable pageable) {
        if (accountId == null && isBlank(fromUser) && isBlank(direction) && isBlank(msgType)
                && isBlank(result) && isBlank(startTime) && isBlank(endTime)) {
            return messageLogRepository.findAll(pageable);
        }
        Specification<WxMessageLogEntity> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new LinkedList<>();
            if (accountId != null) {
                predicates.add(criteriaBuilder.equal(root.get("accountId").as(Long.class), accountId));
            }
            if (!isBlank(fromUser)) {
                predicates.add(criteriaBuilder.equal(root.get("fromUser").as(String.class), fromUser.trim()));
            }
            if (!isBlank(direction)) {
                predicates.add(criteriaBuilder.equal(root.get("direction").as(String.class), direction.trim()));
            }
            if (!isBlank(msgType)) {
                predicates.add(criteriaBuilder.equal(root.get("msgType").as(String.class), msgType.trim()));
            }
            if (!isBlank(result)) {
                predicates.add(criteriaBuilder.equal(root.get("result").as(String.class), result.trim()));
            }
            // createTime 落库为毫秒整数，需按 Date 比较；字符串词法比较在 SQLite 永不命中
            Date start = DateUtil.parseParam(startTime);
            if (start != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.<Date>get("createTime"), start));
            }
            Date end = DateUtil.parseParam(endTime);
            if (end != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.<Date>get("createTime"), end));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
        return messageLogRepository.findAll(spec, pageable);
    }

    /**
     * 管理端规则保存（设计稿第二期「管理端规则编辑」，创建与更新共用）：校验枚举值、
     * keyword 型必填关键词、非 keyword 型强制清空关键词（不污染匹配）、matchType/replyType 缺省、
     * subscribe/fallback 同账号同时最多一条生效（实体注释口径的服务层约束，不加 DB 唯一——避免草稿态冲突）。
     * 与 pageLogs 同口径：不依赖模块开关（模块关着也能编辑规则，先建后激活）。
     */
    public WxAutoReplyEntity saveAutoReply(WxAutoReplyEntity incoming) {
        if (incoming.getAccountId() == null || accountRepository.findById(incoming.getAccountId()).isEmpty()) {
            throw new IllegalArgumentException("公众号账号不存在");
        }
        String ruleType = incoming.getRuleType() == null ? "" : incoming.getRuleType().trim();
        if (!RULE_TYPES.contains(ruleType)) {
            throw new IllegalArgumentException("ruleType 必须是 subscribe/keyword/fallback");
        }
        String matchType = isBlank(incoming.getMatchType()) ? "exact" : incoming.getMatchType().trim();
        if (!MATCH_TYPES.contains(matchType)) {
            throw new IllegalArgumentException("matchType 必须是 exact/contains");
        }
        String replyType = isBlank(incoming.getReplyType()) ? "text" : incoming.getReplyType().trim();
        if (!"text".equals(replyType)) {
            throw new IllegalArgumentException("第一期仅支持 text 回复（素材类第二期）");
        }
        if (isBlank(incoming.getContent())) {
            throw new IllegalArgumentException("回复内容不能为空");
        }
        String keyword = null;
        if ("keyword".equals(ruleType)) {
            if (isBlank(incoming.getKeyword())) {
                throw new IllegalArgumentException("keyword 规则必须填写关键词");
            }
            keyword = incoming.getKeyword().trim();
        }

        WxAutoReplyEntity entity;
        if (incoming.getId() == null) {
            entity = new WxAutoReplyEntity();
        } else {
            entity = autoReplyRepository.findById(incoming.getId())
                    .orElseThrow(() -> new IllegalArgumentException("规则不存在"));
        }
        entity.setAccountId(incoming.getAccountId());
        entity.setRuleType(ruleType);
        entity.setKeyword(keyword);
        entity.setMatchType(matchType);
        entity.setReplyType(replyType);
        entity.setContent(incoming.getContent().trim());
        entity.setPriority(incoming.getPriority() == null ? 0 : incoming.getPriority());
        entity.setEnabled(incoming.getEnabled() == null ? 1 : incoming.getEnabled());

        if (entity.getEnabled() == 1 && ("subscribe".equals(ruleType) || "fallback".equals(ruleType))) {
            String typeLabel = "subscribe".equals(ruleType) ? "欢迎语" : "兜底";
            autoReplyRepository.findByAccountIdAndRuleTypeAndEnabledOrderByPriorityAsc(
                            entity.getAccountId(), ruleType, 1)
                    .stream().filter(other -> !other.getId().equals(entity.getId()))
                    .findAny()
                    .ifPresent(other -> {
                        throw new IllegalArgumentException("该账号已有一条启用的" + typeLabel + "规则，请先停用旧规则");
                    });
        }
        return autoReplyRepository.save(entity);
    }

    /** 管理端规则分页（AND 过滤），默认 priority ASC、id ASC（小者先试，同优先级按创建序） */
    public Page<WxAutoReplyEntity> pageAutoReplies(Long accountId, String ruleType, Integer enabled,
                                                   Pageable pageable) {
        if (accountId == null && isBlank(ruleType) && enabled == null) {
            return autoReplyRepository.findAll(pageable);
        }
        Specification<WxAutoReplyEntity> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new LinkedList<>();
            if (accountId != null) {
                predicates.add(criteriaBuilder.equal(root.get("accountId").as(Long.class), accountId));
            }
            if (!isBlank(ruleType)) {
                predicates.add(criteriaBuilder.equal(root.get("ruleType").as(String.class), ruleType.trim()));
            }
            if (enabled != null) {
                predicates.add(criteriaBuilder.equal(root.get("enabled").as(Integer.class), enabled));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
        return autoReplyRepository.findAll(spec, pageable);
    }

    public void deleteAutoReply(Long id) {
        autoReplyRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("规则不存在"));
        autoReplyRepository.deleteById(id);
    }

    /**
     * 管理端菜单保存（设计稿 §4.3「编辑/草稿」本地半边，创建与更新共用；推送微信半边压凭据）：
     * 两级结构（父必须是同账号顶级菜单）、一级仅 view/click、名字数按微信口径加权校验
     * （汉字/非 ASCII 记 2 分、ASCII 记 1 分；一级 ≤8 分、二级 ≤16 分，即「4/8 汉字或 2 倍字母」）、
     * view/click/view_miniprogram 必填字段、非 view 类型强制清空 url（微信忽略，本地不留误导数据）、
     * 容量上限（每号 ≤3 一级、每个一级 ≤5 二级，更新排除自身）。
     * 与 saveAutoReply 同口径：不依赖模块开关，先建后激活。
     */
    public WxMenuEntity saveMenu(WxMenuEntity incoming) {
        if (incoming.getAccountId() == null || accountRepository.findById(incoming.getAccountId()).isEmpty()) {
            throw new IllegalArgumentException("公众号账号不存在");
        }
        String menuType = incoming.getMenuType() == null ? "" : incoming.getMenuType().trim();
        if (!MENU_TYPES.contains(menuType)) {
            throw new IllegalArgumentException("menuType 必须是微信协议枚举（view/click/scancode_push/"
                    + "scancode_waitmsg/pic_sysphoto/pic_photo_or_album/pic_weixin/location_select/view_miniprogram）");
        }
        if (isBlank(incoming.getName())) {
            throw new IllegalArgumentException("菜单名不能为空");
        }
        String name = incoming.getName().trim();

        WxMenuEntity parent = null;
        if (incoming.getParentId() != null) {
            parent = menuRepository.findById(incoming.getParentId())
                    .orElseThrow(() -> new IllegalArgumentException("父菜单不存在"));
            if (!parent.getAccountId().equals(incoming.getAccountId())) {
                throw new IllegalArgumentException("父菜单必须是同账号的菜单");
            }
            if (parent.getParentId() != null) {
                throw new IllegalArgumentException("菜单最多两级，不能挂在二级菜单下");
            }
        }
        if (parent == null && !TOP_MENU_TYPES.contains(menuType)) {
            throw new IllegalArgumentException("一级菜单只能是 view 或 click（扫码/媒体类仅二级）");
        }
        int weight = menuNameWeight(name);
        if (parent == null && weight > 8) {
            throw new IllegalArgumentException("一级菜单名最长 4 个汉字（8 个字母等效）");
        }
        if (parent != null && weight > 16) {
            throw new IllegalArgumentException("二级菜单名最长 8 个汉字（16 个字母等效）");
        }

        String url = null;
        if ("view".equals(menuType)) {
            url = requireMenuUrl(incoming.getUrl(), "view 菜单必须填写跳转地址");
        }
        String eventKey = null;
        if (KEY_MENU_TYPES.contains(menuType)) {
            if (isBlank(incoming.getEventKey())) {
                throw new IllegalArgumentException(menuType + " 菜单必须填写事件 key");
            }
            eventKey = incoming.getEventKey().trim();
        }
        String miniprogramAppid = null;
        String miniprogramPagepath = null;
        if ("view_miniprogram".equals(menuType)) {
            if (isBlank(incoming.getMiniprogramAppid()) || isBlank(incoming.getMiniprogramPagepath())) {
                throw new IllegalArgumentException("view_miniprogram 菜单必须填写小程序 appid 与 pagepath");
            }
            // 备用网页是微信协议必填（低版本客户端兜底），建菜单时就要求，避免推送时才失败
            url = requireMenuUrl(incoming.getUrl(), "view_miniprogram 菜单必须填写备用网页地址");
            miniprogramAppid = incoming.getMiniprogramAppid().trim();
            miniprogramPagepath = incoming.getMiniprogramPagepath().trim();
        }

        WxMenuEntity entity;
        if (incoming.getId() == null) {
            entity = new WxMenuEntity();
        } else {
            entity = menuRepository.findById(incoming.getId())
                    .orElseThrow(() -> new IllegalArgumentException("菜单不存在"));
            if (!entity.getAccountId().equals(incoming.getAccountId())) {
                throw new IllegalArgumentException("菜单不能跨账号移动（子菜单会悬空）");
            }
        }
        entity.setAccountId(incoming.getAccountId());
        entity.setParentId(incoming.getParentId());
        entity.setName(name);
        entity.setMenuType(menuType);
        entity.setUrl(url);
        entity.setEventKey(eventKey);
        entity.setMiniprogramAppid(miniprogramAppid);
        entity.setMiniprogramPagepath(miniprogramPagepath);
        entity.setMediaId(isBlank(incoming.getMediaId()) ? null : incoming.getMediaId().trim());
        entity.setSort(incoming.getSort() == null ? 0 : incoming.getSort());
        entity.setEnabled(incoming.getEnabled() == null ? 1 : incoming.getEnabled());

        if (entity.getParentId() == null) {
            long tops = menuRepository.findAllByAccountIdAndParentIdIsNull(entity.getAccountId())
                    .stream().filter(other -> !other.getId().equals(entity.getId())).count();
            if (tops >= 3) {
                throw new IllegalArgumentException("每个公众号最多 3 个一级菜单");
            }
        } else {
            long children = menuRepository.findAllByParentId(entity.getParentId())
                    .stream().filter(other -> !other.getId().equals(entity.getId())).count();
            if (children >= 5) {
                throw new IllegalArgumentException("每个一级菜单最多 5 个二级菜单");
            }
        }
        return menuRepository.save(entity);
    }

    /** 管理端菜单列表：单店量小不分页，平铺返回（parentId 表达层级），sort 升序、id 兜底 */
    public List<WxMenuEntity> listMenus(Long accountId) {
        if (accountId == null) {
            return menuRepository.findAll(Sort.by(Sort.Direction.ASC, "accountId", "sort", "id"));
        }
        return menuRepository.findAllByAccountIdOrderBySortAscIdAsc(accountId);
    }

    public void deleteMenu(Long id) {
        WxMenuEntity menu = menuRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("菜单不存在"));
        if (!menuRepository.findAllByParentId(id).isEmpty()) {
            throw new IllegalArgumentException("该菜单下还有二级菜单，请先删除子菜单");
        }
        menuRepository.deleteById(id);
    }

    /** 菜单名权重：非 ASCII（汉字等）记 2 分、ASCII 记 1 分——对应微信「N 汉字或 2N 字母」口径 */
    private int menuNameWeight(String name) {
        return name.codePoints().map(cp -> cp > 127 ? 2 : 1).sum();
    }

    /** 菜单 URL 必填 + http(s) 前缀校验（view 与 view_miniprogram 备用网页共用） */
    private String requireMenuUrl(String raw, String requiredMessage) {
        if (isBlank(raw)) {
            throw new IllegalArgumentException(requiredMessage);
        }
        String url = raw.trim();
        if (!(url.startsWith("http://") || url.startsWith("https://"))) {
            throw new IllegalArgumentException("菜单地址必须以 http:// 或 https:// 开头");
        }
        return url;
    }

    /**
     * 菜单推送微信（设计稿 §4.3「推送」半边；出网走可配基址 wechat-api-base-url，测试指本地桩——D-OA4 先例）：
     * 取 token（cgi-bin/token, client_credential）→ POST cgi-bin/menu/create；只推 enabled=1（草稿不推送），
     * 父菜单带子菜单时只带 name+sub_button（微信忽略父级动作），无子菜单带自身类型字段。
     * 错误不静默：缺 AppSecret/无启用菜单/微信 errcode 非 0 一律拒绝并带 errcode 透传给管理端。
     * 已代决：不做 token 缓存——管理端手工触发、单店低频，先把出网与错误映射做对。
     */
    public long pushMenus(long accountId) {
        WxAccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("公众号账号不存在"));
        if (isBlank(account.getAppSecret())) {
            throw new IllegalArgumentException("该账号未配置 AppSecret，无法推送微信");
        }
        List<Map<String, Object>> buttons = new ArrayList<>();
        for (WxMenuEntity top : menuRepository.findAllByAccountIdAndParentIdIsNullOrderBySortAscIdAsc(accountId)) {
            if (top.getEnabled() == null || top.getEnabled() != 1) {
                continue;
            }
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("name", top.getName());
            List<WxMenuEntity> children = menuRepository
                    .findAllByParentIdAndAccountIdOrderBySortAscIdAsc(top.getId(), accountId).stream()
                    .filter(child -> child.getEnabled() != null && child.getEnabled() == 1)
                    .toList();
            if (children.isEmpty()) {
                putMenuActionFields(node, top);
            } else {
                List<Map<String, Object>> subButtons = new ArrayList<>();
                for (WxMenuEntity child : children) {
                    Map<String, Object> sub = new LinkedHashMap<>();
                    sub.put("name", child.getName());
                    putMenuActionFields(sub, child);
                    subButtons.add(sub);
                }
                node.put("sub_button", subButtons);
            }
            buttons.add(node);
        }
        if (buttons.isEmpty()) {
            throw new IllegalArgumentException("没有启用的一级菜单可推送（草稿不推送微信）");
        }

        String tokenUrl = wechatApiBaseUrl + "/cgi-bin/token?grant_type=client_credential"
                + "&appid=" + urlEncode(account.getAppId())
                + "&secret=" + urlEncode(account.getAppSecret());
        AccessTokenGrant grant = wechatRestTemplate.getForObject(URI.create(tokenUrl), AccessTokenGrant.class);
        if (grant == null || isBlank(grant.getAccessToken())) {
            throw new IllegalArgumentException("微信 access_token 获取失败（errcode=" + (grant == null ? null : grant.getErrcode())
                    + " errmsg=" + (grant == null ? null : grant.getErrmsg()) + "）");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String createUrl = wechatApiBaseUrl + "/cgi-bin/menu/create?access_token=" + urlEncode(grant.getAccessToken());
        Map<String, Object> resp = wechatRestTemplate
                .postForEntity(URI.create(createUrl), new HttpEntity<>(Map.of("button", buttons), headers), Map.class)
                .getBody();
        Object errcode = resp == null ? null : resp.get("errcode");
        if (!(errcode instanceof Number code) || code.intValue() != 0) {
            throw new IllegalArgumentException("微信菜单推送失败（errcode=" + errcode
                    + " errmsg=" + (resp == null ? null : resp.get("errmsg")) + "）");
        }
        return buttons.size();
    }

    /** 菜单动作字段：view 系带 url（小程序加 appid/pagepath），其余带 key */
    private void putMenuActionFields(Map<String, Object> node, WxMenuEntity menu) {
        node.put("type", menu.getMenuType());
        if ("view".equals(menu.getMenuType())) {
            node.put("url", menu.getUrl());
        } else if ("view_miniprogram".equals(menu.getMenuType())) {
            node.put("url", menu.getUrl());
            node.put("appid", menu.getMiniprogramAppid());
            node.put("pagepath", menu.getMiniprogramPagepath());
        } else {
            node.put("key", menu.getEventKey());
        }
    }

    /** 微信 cgi-bin/token 响应（client_credential 换取；errcode 非 0 时无 token） */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class AccessTokenGrant {
        @com.fasterxml.jackson.annotation.JsonProperty("access_token")
        private String accessToken;
        @com.fasterxml.jackson.annotation.JsonProperty("expires_in")
        private Integer expiresIn;
        private Integer errcode;
        private String errmsg;
    }

    /**
     * 管理端素材台账只读分页（设计稿 §4.5「供管理端查看」，粉丝发图此前落盘后无处可见）：
     * 按账号/类型/时间窗 AND 过滤（同 pageLogs 口径），排序由 Pageable 携带（默认最新在前）。
     * 不依赖模块开关——素材是已落库的台账数据，模块关闭期间也要可查（不静默）。
     */
    public Page<WxMaterialEntity> pageMaterials(Long accountId, String materialType,
                                                String startTime, String endTime, Pageable pageable) {
        if (accountId == null && isBlank(materialType) && isBlank(startTime) && isBlank(endTime)) {
            return materialRepository.findAll(pageable);
        }
        Specification<WxMaterialEntity> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new LinkedList<>();
            if (accountId != null) {
                predicates.add(criteriaBuilder.equal(root.get("accountId").as(Long.class), accountId));
            }
            if (!isBlank(materialType)) {
                predicates.add(criteriaBuilder.equal(root.get("materialType").as(String.class), materialType.trim()));
            }
            Date start = DateUtil.parseParam(startTime);
            if (start != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.<Date>get("createTime"), start));
            }
            Date end = DateUtil.parseParam(endTime);
            if (end != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.<Date>get("createTime"), end));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
        return materialRepository.findAll(spec, pageable);
    }

    /** 素材本地副本（管理端取图）：path 已解析并校验落在素材根目录内 */
    public record MaterialFile(Path path, String mimeType) {
    }

    /**
     * 管理端取素材本地文件：localPath 相对素材根目录解析，normalize 后必须仍在根内（防 ../ 穿越），
     * 无本地副本/文件缺失/非法路径一律 IllegalArgumentException（400 口径，管理端无 404 特例）。
     */
    public MaterialFile loadMaterialFile(long id) {
        WxMaterialEntity material = materialRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("素材不存在"));
        if (isBlank(material.getLocalPath())) {
            throw new IllegalArgumentException("该素材无本地副本");
        }
        Path root = Paths.get(materialDir).toAbsolutePath().normalize();
        Path target = root.resolve(material.getLocalPath()).normalize();
        if (!target.startsWith(root) || !Files.isRegularFile(target)) {
            throw new IllegalArgumentException("素材文件不存在或路径不合法");
        }
        String mime = isBlank(material.getMimeType()) ? "application/octet-stream" : material.getMimeType().trim();
        try {
            org.springframework.http.MediaType.parseMediaType(mime);
        } catch (Exception e) {
            mime = "application/octet-stream"; // 台账 mime 来自下载响应头，坏值兜底不让取图 500
        }
        return new MaterialFile(target, mime);
    }

    /** 管理端账号列表（规则/粉丝页选账号用，单店量小不分页，id 升序）；密钥列绝不读回（见 WxAccountView） */
    public List<WxAccountEntity> listAccounts() {
        return accountRepository.findAll(Sort.by("id"));
    }

    /**
     * 图片消息素材拉取（设计稿 §4.5 第一期口径：下载 PicUrl 到 local_path 落 wx_materials 台账）。
     * 同 (account, media_id) 已有台账则跳过重下（微信超时重推同图）；下载失败、非 200、超上限一律
     * 只记 warn 不落行——回调仍按无素材路径走兜底（微信 5s 约束不得放大异常）。
     * 下载在回调线程内同步做，单次上限 3s；超时被微信重发时由 MsgId 排重兜底（不重复拉取）。
     */
    private void captureMaterial(long accountId, String mediaId, String picUrl) {
        if (isBlank(picUrl)) {
            return;
        }
        if (!isBlank(mediaId)
                && materialRepository.findByAccountIdAndMediaId(accountId, mediaId).isPresent()) {
            return; // 已入台账（含此前成功下载），不重复下载
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(picUrl))
                    .timeout(Duration.ofSeconds(3)).GET().build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                log.warn("wx material download http {} account={} media={}",
                        response.statusCode(), accountId, mediaId);
                return;
            }
            byte[] body = response.body();
            if (body.length > materialMaxBytes) {
                log.warn("wx material exceeds {} bytes, skip account={} media={}",
                        materialMaxBytes, accountId, mediaId);
                return;
            }
            String mime = response.headers().firstValue("Content-Type").orElse("");
            String relative = Paths.get(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM")),
                    UUID.randomUUID() + "." + extensionFor(picUrl, mime))
                    .toString().replace('\\', '/');
            Path target = Paths.get(materialDir, relative);
            Files.createDirectories(target.getParent());
            Files.write(target, body);

            WxMaterialEntity material = new WxMaterialEntity();
            material.setAccountId(accountId);
            material.setMediaId(mediaId);
            material.setMaterialType("temp");
            material.setUrl(picUrl);
            material.setLocalPath(relative);
            material.setSize((long) body.length);
            material.setMimeType(mime.isBlank() ? null : mime);
            materialRepository.save(material);
        } catch (Exception e) {
            log.warn("wx material download failed account={} media={}: {}", accountId, mediaId, e.getMessage());
        }
    }

    /** 落盘扩展名：优先 Content-Type，其次 URL 路径后缀，兜底 bin */
    private static String extensionFor(String url, String mime) {
        String lower = mime == null ? "" : mime.toLowerCase(Locale.ROOT);
        if (lower.contains("jpeg") || lower.contains("jpg")) {
            return "jpg";
        }
        if (lower.contains("png")) {
            return "png";
        }
        if (lower.contains("gif")) {
            return "gif";
        }
        if (lower.contains("webp")) {
            return "webp";
        }
        int slash = url.lastIndexOf('/');
        int dot = url.lastIndexOf('.');
        if (dot > slash && dot < url.length() - 1) {
            String ext = url.substring(dot + 1).toLowerCase(Locale.ROOT);
            if (ext.matches("[a-z0-9]{1,5}")) {
                return ext;
            }
        }
        return "bin";
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    /** XXE 加固的明文 XML 解析（微信回调报文无 DTD；禁外部实体） */
    static Map<String, String> parseXml(String xml) {
        Map<String, String> values = new LinkedHashMap<>();
        if (xml == null || xml.isBlank()) {
            return values;
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            Element root = factory.newDocumentBuilder()
                    .parse(new InputSource(new StringReader(xml))).getDocumentElement();
            var children = root.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                if (children.item(i) instanceof Element child) {
                    values.put(child.getTagName(), child.getTextContent());
                }
            }
        } catch (Exception e) {
            // 解析失败返回空 map：上层按 unknown 类型落流水回 success，不抛异常放大微信重试
        }
        return values;
    }
}
