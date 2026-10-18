package com.sinomed.service;

import com.sinomed.entity.WxAccountEntity;
import com.sinomed.entity.WxAutoReplyEntity;
import com.sinomed.entity.WxFanEntity;
import com.sinomed.entity.WxMessageLogEntity;
import com.sinomed.repository.WxAccountRepository;
import com.sinomed.repository.WxAutoReplyRepository;
import com.sinomed.repository.WxFanRepository;
import com.sinomed.repository.WxMessageLogRepository;
import com.sinomed.util.DateUtil;
import com.sinomed.util.WxSignatureUtil;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 公众号接入（第一期骨架，设计稿 docs/design/wechat-official-account.md §5/§6）：
 * URL 验签、明文消息验签+排重+全量落流水、subscribe/unsubscribe 粉丝维护、
 * 关键词/欢迎语/兜底被动回复、能力矩阵探测。
 * 「不静默」口径：模块关/账号停用/未知账号一律 200 拒处理（回 "success"，不让微信重试）并落拒绝审计；
 * 验签失败回空 body。安全模式（AES 解密）第二期随 WxJava 接入，本类只实施明文模式。
 */
@Component
public class WxOfficialAccountService {

    private final WxAccountRepository accountRepository;
    private final WxFanRepository fanRepository;
    private final WxAutoReplyRepository autoReplyRepository;
    private final WxMessageLogRepository messageLogRepository;

    /** 模块总开关（env WX_OA_ENABLED）；缺省 false=整体关闭，回调拒处理 */
    @Value("${sinomed.official-account.enabled:false}")
    private boolean enabled;

    /** 消息推送 Token（env WX_OA_TOKEN）；第一期单账号，验签用 */
    @Value("${sinomed.official-account.token:}")
    private String token;

    public WxOfficialAccountService(WxAccountRepository accountRepository,
                                    WxFanRepository fanRepository,
                                    WxAutoReplyRepository autoReplyRepository,
                                    WxMessageLogRepository messageLogRepository) {
        this.accountRepository = accountRepository;
        this.fanRepository = fanRepository;
        this.autoReplyRepository = autoReplyRepository;
        this.messageLogRepository = messageLogRepository;
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

    /** POST 消息推送：明文验签 → 排重 → 落流水 → 路由（事件/文本）→ 被动回复或 "success" */
    public String onMessage(long accountId, String signature, String timestamp, String nonce, String xmlBody) {
        if (!enabled) {
            logReject(accountId, "disabled", "none", null, null);
            return "success";
        }
        Optional<WxAccountEntity> account = accountRepository.findById(accountId);
        if (account.isEmpty() || account.get().getEnabled() == null || account.get().getEnabled() == 0) {
            logReject(accountId, "account_missing", "none", null, null);
            return "success";
        }
        if (!WxSignatureUtil.check(token, timestamp, nonce, signature)) {
            logReject(accountId, "bad_signature", "none", null, null);
            return "";
        }

        Map<String, String> msg = parseXml(xmlBody);
        String msgType = msg.getOrDefault("MsgType", "none");
        String fromUser = msg.get("FromUserName");
        String toUser = msg.get("ToUserName");
        String event = msg.get("Event");
        String content = msg.get("Content");
        String msgId = msg.get("MsgId");
        String createTime = msg.get("CreateTime");

        String dedupeKey = msgId != null ? "M:" + msgId
                : (fromUser != null && createTime != null && event != null ? "E:" + fromUser + ":" + createTime + ":" + event : null);
        if (dedupeKey != null && messageLogRepository.existsByDedupeKey(dedupeKey)) {
            logReject(accountId, "duplicate", msgType, fromUser, toUser);
            return "success";
        }

        logMessage(accountId, "in", msgType, fromUser, toUser, event != null ? event : content, msgId, dedupeKey);

        if ("event".equals(msgType)) {
            return routeEvent(accountId, event, fromUser, toUser);
        }
        if ("text".equals(msgType)) {
            String reply = matchKeywordReply(accountId, content);
            if (reply == null) {
                reply = firstRule(accountId, "fallback");
            }
            return replyTo(accountId, fromUser, toUser, reply);
        }
        // image/voice/video/link/location 等：第一期落日志 + 兜底（不做识别处理）
        return replyTo(accountId, fromUser, toUser, firstRule(accountId, "fallback"));
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

    private String routeEvent(long accountId, String event, String fromUser, String toUser) {
        if ("subscribe".equals(event)) {
            WxFanEntity fan = fanRepository.findByAccountIdAndOpenid(accountId, fromUser).orElseGet(WxFanEntity::new);
            fan.setAccountId(accountId);
            fan.setOpenid(fromUser);
            fan.setSubscribe(1);
            fan.setSubscribeTime(new Date());
            fanRepository.save(fan);
            return replyTo(accountId, fromUser, toUser, firstRule(accountId, "subscribe"));
        }
        if ("unsubscribe".equals(event)) {
            // 取关保留行：subscribe=0，customer_id 不动（再关注后绑定仍在，换人属线下场景走管理端）
            WxFanEntity fan = fanRepository.findByAccountIdAndOpenid(accountId, fromUser).orElseGet(WxFanEntity::new);
            fan.setAccountId(accountId);
            fan.setOpenid(fromUser);
            fan.setSubscribe(0);
            fan.setUnsubscribeTime(new Date());
            fanRepository.save(fan);
            return "success"; // 微信不收取关回复
        }
        return "success"; // CLICK/VIEW/scan 等：第一期仅落流水，不做业务路由
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

    /** 被动回复：规则内容为空回 "success"（5s 内无响应微信会重试）；出站落流水 */
    private String replyTo(long accountId, String fromUser, String toUser, String replyContent) {
        if (fromUser == null || replyContent == null || replyContent.isBlank()) {
            return "success";
        }
        logMessage(accountId, "out", "text", toUser, fromUser, replyContent, null, null);
        return "<xml><ToUserName><![CDATA[" + escapeCdata(fromUser) + "]]></ToUserName>"
                + "<FromUserName><![CDATA[" + escapeCdata(toUser == null ? "" : toUser) + "]]></FromUserName>"
                + "<CreateTime>" + (System.currentTimeMillis() / 1000) + "</CreateTime>"
                + "<MsgType><![CDATA[text]]></MsgType>"
                + "<Content><![CDATA[" + escapeCdata(replyContent) + "]]></Content></xml>";
    }

    /** CDATA 内闭合序列转义（内容含 "]]>" 时防截断） */
    private static String escapeCdata(String value) {
        return value.replace("]]>", "]]]]><![CDATA[>");
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
