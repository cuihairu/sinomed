package com.sinomed.service;

import com.sinomed.entity.WxAccountEntity;
import com.sinomed.entity.WxAutoReplyEntity;
import com.sinomed.entity.WxFanEntity;
import com.sinomed.repository.WxAccountRepository;
import com.sinomed.repository.WxAutoReplyRepository;
import com.sinomed.repository.WxFanRepository;
import com.sinomed.repository.WxMessageLogRepository;
import com.sinomed.service.impl.WxMaterialAdminService;
import com.sinomed.service.impl.WxMessageLogService;
import com.sinomed.util.WxSignatureUtil;
import me.chanjar.weixin.mp.api.WxMpMessageRouter;
import me.chanjar.weixin.mp.bean.message.WxMpXmlMessage;
import me.chanjar.weixin.mp.bean.message.WxMpXmlOutMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 公众号回调接入（设计稿 docs/design/wechat-official-account.md §5）：URL 验签、明文/加密消息验签+
 * 排重+全量落流水、subscribe/unsubscribe 粉丝维护、关键词/欢迎语/兜底被动回复、账号级路由缓存。
 * 「不静默」口径：模块关/账号停用/未知账号一律 200 拒处理（回 "success"，不让微信重试）并落拒绝审计；
 * 验签失败回空 body。安全/兼容模式（AES-256-CBC 信封，设计稿 §5.3）由 WxMsgCryptor 承担：
 * msg_signature 四段验签 + 解密进主流程，安全模式回复密封（官方规范手写实现，不引 SDK）。
 */
@Component
public class WxCallbackService {

    private final WxAccountRepository accountRepository;
    private final WxFanRepository fanRepository;
    private final WxAutoReplyRepository autoReplyRepository;
    private final WxMessageLogRepository messageLogRepository;
    private final WxMsgCryptor msgCryptor;
    private final WxMpServiceProvider wxMpServiceProvider;
    private final WxAccountTokenResolver tokenResolver;
    private final WxMessageLogService messageLogService;
    private final WxMaterialAdminService materialAdminService;

    /** 模块总开关（env WX_OA_ENABLED）；缺省 false=整体关闭，回调拒处理 */
    private final boolean enabled;

    /** 账号级消息路由（设计稿 §5.1：WxMpMessageRouter 按 accountId 实例化并缓存，不能单例跨账号） */
    private final Map<Long, WxMpMessageRouter> routers = new ConcurrentHashMap<>();

    public WxCallbackService(WxAccountRepository accountRepository,
                             WxFanRepository fanRepository,
                             WxAutoReplyRepository autoReplyRepository,
                             WxMessageLogRepository messageLogRepository,
                             WxMsgCryptor msgCryptor,
                             WxMpServiceProvider wxMpServiceProvider,
                             WxAccountTokenResolver tokenResolver,
                             WxMessageLogService messageLogService,
                             WxMaterialAdminService materialAdminService,
                             @Value("${sinomed.official-account.enabled:false}") boolean enabled) {
        this.accountRepository = accountRepository;
        this.fanRepository = fanRepository;
        this.autoReplyRepository = autoReplyRepository;
        this.messageLogRepository = messageLogRepository;
        this.msgCryptor = msgCryptor;
        this.wxMpServiceProvider = wxMpServiceProvider;
        this.tokenResolver = tokenResolver;
        this.messageLogService = messageLogService;
        this.materialAdminService = materialAdminService;
        this.enabled = enabled;
    }

    /** GET URL 验证：通过原样返回 echostr；拒绝路径回 "success"/空并落审计 */
    public String verifyUrl(long accountId, String signature, String timestamp, String nonce, String echostr) {
        if (!enabled) {
            messageLogService.logReject(accountId, "disabled", "none", null, null);
            return "success";
        }
        WxAccountEntity account = accountRepository.findById(accountId)
                .filter(a -> a.getEnabled() != null && a.getEnabled() == 1).orElse(null);
        if (account == null) {
            messageLogService.logReject(accountId, "account_missing", "none", null, null);
            return "success";
        }
        if (!WxSignatureUtil.check(tokenResolver.resolveToken(account), timestamp, nonce, signature)) {
            messageLogService.logReject(accountId, "bad_signature", "none", null, null);
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
            messageLogService.logReject(accountId, "disabled", "none", null, null);
            return "success";
        }
        WxAccountEntity account = accountRepository.findById(accountId)
                .filter(a -> a.getEnabled() != null && a.getEnabled() == 1).orElse(null);
        if (account == null) {
            messageLogService.logReject(accountId, "account_missing", "none", null, null);
            return "success";
        }

        Map<String, String> envelope = parseXml(xmlBody);
        String encrypt = envelope.get("Encrypt");
        String innerXml = xmlBody;
        boolean encrypted = encrypt != null && !encrypt.isBlank();
        if (encrypted) {
            if (!msgCryptor.checkMsgSignature(tokenResolver.resolveToken(account), timestamp, nonce, encrypt, msgSignature)) {
                messageLogService.logReject(accountId, "bad_msg_signature", "none", null, null);
                return "";
            }
            try {
                innerXml = msgCryptor.decrypt(account.getAesKey(), account.getAppId(), encrypt);
            } catch (IllegalArgumentException e) {
                messageLogService.logReject(accountId, "bad_encrypt", "none", null, null);
                return "";
            }
        } else {
            if ("safe".equals(account.getEncryptMode())) {
                messageLogService.logReject(accountId, "missing_encrypt", "none", null, null);
                return "";
            }
            if (!WxSignatureUtil.check(tokenResolver.resolveToken(account), timestamp, nonce, signature)) {
                messageLogService.logReject(accountId, "bad_signature", "none", null, null);
                return "";
            }
        }

        final WxMpXmlMessage msg;
        try {
            msg = WxMpXmlMessage.fromXml(innerXml);
        } catch (RuntimeException e) {
            // 设计原则：不可解析报文不抛异常（微信会重试放大），落拒绝审计回 success
            messageLogService.logReject(accountId, "bad_xml", "none", null, null);
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
            messageLogService.logReject(accountId, "duplicate", msgType, fromUser, toUser);
            return "success";
        }

        messageLogService.logMessage(accountId, "in", msgType, fromUser, toUser, event != null ? event : content, msgId, dedupeKey);

        // 路由骨架（设计稿 §5.4）：SDK WxMpMessageRouter 按账号实例化 + 同步规则（5s 窗口）；
        // 规则不命中或 handler 无回复时 Route 返回 null → 回 "success"。repo 侧排重仍由 DB 承担，
        // SDK 内置内存排重置为 no-op，避免与 wx_message_logs.dedupe_key 双写冲突。
        WxMpXmlOutMessage out = router(account).route(msg);
        String reply = out == null ? "success" : out.toXml();
        return sealReply(account, encrypted, nonce, reply);
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
                    materialAdminService.captureMaterial(accountId, message.getMediaId(), message.getPicUrl());
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
        String sign = msgCryptor.signMsg(tokenResolver.resolveToken(account), timestamp, nonce, cipher);
        return "<xml><Encrypt><![CDATA[" + cipher + "]]></Encrypt>"
                + "<MsgSignature><![CDATA[" + sign + "]]></MsgSignature>"
                + "<TimeStamp>" + timestamp + "</TimeStamp>"
                + "<Nonce><![CDATA[" + (nonce == null ? "" : nonce) + "]]></Nonce></xml>";
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
        messageLogService.logMessage(accountId, "out", "text", msg.getToUser(), msg.getFromUser(), replyContent, null, null);
        return WxMpXmlOutMessage.TEXT()
                .toUser(msg.getFromUser())
                .fromUser(msg.getToUser())
                .content(replyContent)
                .build();
    }

    /** XXE 加固的明文 XML 解析（微信回调报文无 DTD；禁外部实体） */
    private static Map<String, String> parseXml(String xml) {
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
