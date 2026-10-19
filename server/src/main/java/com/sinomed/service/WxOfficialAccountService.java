package com.sinomed.service;

import com.sinomed.entity.CustomerEntity;
import com.sinomed.entity.SmsCodeEntity;
import com.sinomed.entity.WxAccountEntity;
import com.sinomed.entity.WxAutoReplyEntity;
import com.sinomed.entity.WxBindingLogEntity;
import com.sinomed.entity.WxFanEntity;
import com.sinomed.entity.WxMaterialEntity;
import com.sinomed.entity.WxMessageLogEntity;
import com.sinomed.repository.CustomerRepository;
import com.sinomed.repository.SmsCodeRepository;
import com.sinomed.repository.WxAccountRepository;
import com.sinomed.repository.WxAutoReplyRepository;
import com.sinomed.repository.WxBindingLogRepository;
import com.sinomed.repository.WxFanRepository;
import com.sinomed.repository.WxMaterialRepository;
import com.sinomed.repository.WxMessageLogRepository;
import com.sinomed.util.DateUtil;
import com.sinomed.util.WxSignatureUtil;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 公众号接入（第一期骨架，设计稿 docs/design/wechat-official-account.md §5/§6）：
 * URL 验签、明文消息验签+排重+全量落流水、subscribe/unsubscribe 粉丝维护、
 * 关键词/欢迎语/兜底被动回复、能力矩阵探测。
 * 「不静默」口径：模块关/账号停用/未知账号一律 200 拒处理（回 "success"，不让微信重试）并落拒绝审计；
 * 验签失败回空 body。安全模式（AES 解密）第二期随 WxJava 接入，本类只实施明文模式。
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

    /** 绑定短信验证码有效期（分钟），与 Kiosk 短信通道同口径 */
    private static final long BIND_SMS_TTL_MINUTES = 5;

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
                                    WxMaterialRepository materialRepository) {
        this.accountRepository = accountRepository;
        this.fanRepository = fanRepository;
        this.autoReplyRepository = autoReplyRepository;
        this.messageLogRepository = messageLogRepository;
        this.bindingLogRepository = bindingLogRepository;
        this.smsCodeRepository = smsCodeRepository;
        this.customerRepository = customerRepository;
        this.materialRepository = materialRepository;
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
        if ("image".equals(msgType)) {
            // 设计稿 §4.5 第一期：图片消息拉取 PicUrl 落 wx_materials（type=temp，供管理端查看）
            captureMaterial(accountId, msg.get("MediaId"), msg.get("PicUrl"));
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
