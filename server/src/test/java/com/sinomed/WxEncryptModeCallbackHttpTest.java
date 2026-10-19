package com.sinomed;

import com.sinomed.entity.WxAccountEntity;
import com.sinomed.entity.WxAutoReplyEntity;
import com.sinomed.entity.WxFanEntity;
import com.sinomed.repository.WxAccountRepository;
import com.sinomed.repository.WxAutoReplyRepository;
import com.sinomed.repository.WxFanRepository;
import com.sinomed.repository.WxMessageLogRepository;
import com.sinomed.service.WxMsgCryptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 安全/兼容模式回调 HTTP 回归（设计稿 §5.3 第二期）：
 * 加密信封走 msg_signature 四段验签（token,timestamp,nonce,encrypt 字典序 SHA1）→ AES 解密进主流程；
 * 安全模式回复必须包加密信封（success/空体保持明文），兼容模式回复明文。
 * 信封构造用 WxMsgCryptor（其正确性由 WxMsgCryptorTest 的 OpenSSL KAT 非同源锚定），
 * msg_signature 侧用测试内独立 SHA1 实现互检，同 WxOfficialAccountHttpTest 先例。
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:sqlite:target/wx-encrypt-callback-test-${random.uuid}.db",
        "sinomed.official-account.enabled=true",
        "sinomed.official-account.token=TESTTOKEN"
})
class WxEncryptModeCallbackHttpTest {

    /** aes_key 落库加密密钥（WxSecretAttributeConverter）；固定值避开 random.uuid 多解析坑 */
    @org.springframework.test.context.DynamicPropertySource
    static void credentialKey(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("sinomed.official-account.credential-key", () -> "test-cred-key");
    }


    private static final String TOKEN = "TESTTOKEN";
    private static final String ENC_KEY = "30KYCOUPMps+YQjLeVHL+2EpiyizimzctwXj9lYTYL4";
    private static final String APP_ID = "wxSAFE1001";

    @LocalServerPort
    private int port;

    private final RestTemplate rest = new RestTemplate();
    private final WxMsgCryptor cryptor = new WxMsgCryptor();

    @Autowired
    private WxAccountRepository accountRepository;
    @Autowired
    private WxAutoReplyRepository autoReplyRepository;
    @Autowired
    private WxFanRepository fanRepository;
    @Autowired
    private WxMessageLogRepository messageLogRepository;

    private Long accountId;

    @BeforeEach
    void seed() {
        messageLogRepository.deleteAll();
        autoReplyRepository.deleteAll();
        fanRepository.deleteAll();
        accountRepository.deleteAll();
        WxAccountEntity account = new WxAccountEntity();
        account.setAppId(APP_ID);
        account.setAccountName("安全模式测试号");
        account.setAccountType("service");
        account.setVerified(1);
        account.setAesKey(ENC_KEY);
        account.setEncryptMode("safe");
        accountId = accountRepository.save(account).getId();

        WxAutoReplyEntity rule = new WxAutoReplyEntity();
        rule.setAccountId(accountId);
        rule.setRuleType("keyword");
        rule.setKeyword("会员卡");
        rule.setMatchType("exact");
        rule.setContent("会员卡请到前台办理");
        autoReplyRepository.save(rule);
    }

    // ---------- 独立实现（与被测代码同规格，测试侧自算） ----------

    private static String sha1Hex(String input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest(input.getBytes(StandardCharsets.UTF_8))) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static String sortedSha1(String... parts) throws Exception {
        return sha1Hex(Arrays.stream(parts).sorted().collect(Collectors.joining()));
    }

    private static String textXml(String fromUser, long msgId, String content) {
        return "<xml><ToUserName><![CDATA[gh_test]]></ToUserName>"
                + "<FromUserName><![CDATA[" + fromUser + "]]></FromUserName>"
                + "<CreateTime>1760000000</CreateTime><MsgType><![CDATA[text]]></MsgType>"
                + "<Content><![CDATA[" + content + "]]></Content>"
                + "<MsgId>" + msgId + "</MsgId></xml>";
    }

    /** 构造加密信封报文（微信安全/兼容模式推送同款布局） */
    private String envelopeXml(String innerXml, String timestamp, String nonce) throws Exception {
        String encrypt = cryptor.encrypt(ENC_KEY, APP_ID, innerXml);
        return "<xml><Encrypt><![CDATA[" + encrypt + "]]></Encrypt></xml>";
    }

    private String postEnvelope(Object accountIdParam, String body, String timestamp, String nonce,
                                String msgSignature) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "xml", StandardCharsets.UTF_8));
        return rest.exchange("http://localhost:" + port + "/api/v1/wx/callback/" + accountIdParam
                        + "?signature=whatever&timestamp=" + timestamp + "&nonce=" + nonce
                        + "&msg_signature=" + msgSignature + "&encrypt_type=safe",
                HttpMethod.POST, new HttpEntity<>(body, headers), String.class).getBody();
    }

    private String msgSignatureOf(String encrypt, String timestamp, String nonce) throws Exception {
        return sortedSha1(TOKEN, timestamp, nonce, encrypt);
    }

    /** 取信封非 CDATA 数值元素（TimeStamp 无 CDATA 包裹） */
    private static String plainTag(String body, String tag) {
        Matcher matcher = Pattern.compile("<" + tag + ">(\\d+)</" + tag + ">").matcher(body);
        assertTrue(matcher.find(), "应含 " + tag + " 元素：" + body);
        return matcher.group(1);
    }

    /** 取信封元素的 CDATA 内容；CDATA[ 的前后方括号都必须转义，否则正则开出不闭合字符类永不命中 */
    private static String cdata(String body, String tag) {
        Matcher matcher = Pattern.compile("<" + tag + "><!\\[CDATA\\[(.*?)\\]\\]></" + tag + ">", Pattern.DOTALL)
                .matcher(body);
        assertTrue(matcher.find(), "应含 " + tag + " 元素：" + body);
        return matcher.group(1);
    }

    /** RestTemplate 对空 200 body 回 null，归一为 "" 再断言 */
    private static String bodyOrEmpty(String body) {
        return body == null ? "" : body;
    }

    private long countLogs(String direction, String result) {
        return messageLogRepository.findAll().stream()
                .filter(l -> direction.equals(l.getDirection()) && result.equals(l.getResult())).count();
    }

    // ---------- 用例 ----------

    @Test
    void safeModeEncryptedPushGetsEncryptedReplyEnvelope() throws Exception {
        String timestamp = "1700000100";
        String nonce = "nonceS1";
        String encrypt = cryptor.encrypt(ENC_KEY, APP_ID, textXml("openidSafe", 300101L, "会员卡"));
        String body = postEnvelope(accountId,
                "<xml><Encrypt><![CDATA[" + encrypt + "]]></Encrypt></xml>",
                timestamp, nonce, msgSignatureOf(encrypt, timestamp, nonce));

        // 回复必须是加密信封：四元素齐全，明文回复内容不得外泄
        assertTrue(body != null && body.startsWith("<xml><Encrypt><![CDATA["), "安全模式回复应为信封：" + body);
        assertFalse(body.contains("会员卡"), "明文回复内容不得出现在信封外：" + body);
        assertTrue(body.contains("<MsgSignature><![CDATA["), "信封应带 MsgSignature：" + body);
        assertTrue(body.matches("(?s).*<TimeStamp>\\d+</TimeStamp>.*"), "信封应带 TimeStamp：" + body);
        assertTrue(body.contains("<Nonce><![CDATA[" + nonce + "]]></Nonce>"), "信封应回带 nonce：" + body);

        // msg_signature 独立验签 → 解密回原回复 XML（KAT 锚定的 cryptor 侧）
        String replyEncrypt = cdata(body, "Encrypt");
        assertEquals(msgSignatureOf(replyEncrypt, plainTag(body, "TimeStamp"), cdata(body, "Nonce")),
                cdata(body, "MsgSignature"), "回复信封 msg_signature 必须可独立验签");
        String replyXml = cryptor.decrypt(ENC_KEY, APP_ID, replyEncrypt);
        assertTrue(replyXml.contains("会员卡请到前台办理"), "解密后应为关键词回复：" + replyXml);
        assertTrue(replyXml.contains("<ToUserName><![CDATA[openidSafe]]></ToUserName>"),
                "回复对象应为粉丝 openid：" + replyXml);

        // 入站消息按解密后报文进主流程：排重键从解密后 MsgId 取（不依赖任何信封元素）
        assertEquals(1, countLogs("in", "ok"), "解密后应正常落入站流水");
        assertTrue(messageLogRepository.findAll().stream()
                        .filter(l -> "in".equals(l.getDirection())).anyMatch(l -> "M:300101".equals(l.getDedupeKey())),
                "排重键应取自解密后报文的 MsgId：" + messageLogRepository.findAll());
    }

    @Test
    void safeModeEncryptedSubscribeEventMaintainsFanAndSealsWelcome() throws Exception {
        WxAutoReplyEntity welcome = new WxAutoReplyEntity();
        welcome.setAccountId(accountId);
        welcome.setRuleType("subscribe");
        welcome.setContent("欢迎关注本店");
        autoReplyRepository.save(welcome);

        String timestamp = "1700000104";
        String nonce = "nonceE1";
        String inner = "<xml><ToUserName><![CDATA[gh_test]]></ToUserName>"
                + "<FromUserName><![CDATA[openidSub]]></FromUserName>"
                + "<CreateTime>1760000001</CreateTime><MsgType><![CDATA[event]]></MsgType>"
                + "<Event><![CDATA[subscribe]]></Event></xml>";
        String encrypt = cryptor.encrypt(ENC_KEY, APP_ID, inner);
        String body = postEnvelope(accountId,
                "<xml><Encrypt><![CDATA[" + encrypt + "]]></Encrypt></xml>",
                timestamp, nonce, msgSignatureOf(encrypt, timestamp, nonce));

        assertTrue(body != null && body.startsWith("<xml><Encrypt><![CDATA["), "欢迎语回复应为信封：" + body);
        String replyXml = cryptor.decrypt(ENC_KEY, APP_ID, cdata(body, "Encrypt"));
        assertTrue(replyXml.contains("欢迎关注本店"), "解密后应为欢迎语回复：" + replyXml);

        // 粉丝维护按解密后事件照常执行
        assertEquals(1, countLogs("in", "ok"), "事件应落入站流水");
        assertEquals(1, fanRepository.findByAccountIdAndOpenid(accountId, "openidSub")
                .map(f -> f.getSubscribe() == 1 ? 1 : 0).orElse(0), "关注事件应写入粉丝行（subscribe=1）");
    }

    @Test
    void safeModePlaintextPushIsRejected() throws Exception {
        String timestamp = "1700000101";
        String nonce = "nonceS2";
        String joined = Arrays.stream(new String[]{TOKEN, timestamp, nonce}).sorted()
                .collect(Collectors.joining());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "xml", StandardCharsets.UTF_8));
        String body = bodyOrEmpty(rest.exchange("http://localhost:" + port + "/api/v1/wx/callback/" + accountId
                        + "?signature=" + sha1Hex(joined) + "&timestamp=" + timestamp + "&nonce=" + nonce,
                HttpMethod.POST, new HttpEntity<>(textXml("openidSafe", 300102L, "会员卡"), headers),
                String.class).getBody());
        assertEquals("", body, "安全模式账号收明文推送应拒绝（真实微信不会这样发）");
        assertEquals(1, countLogs("reject", "missing_encrypt"), "应落 missing_encrypt 拒绝审计");
    }

    @Test
    void badMsgSignatureIsRejectedWithAudit() throws Exception {
        String timestamp = "1700000102";
        String nonce = "nonceS3";
        String encrypt = cryptor.encrypt(ENC_KEY, APP_ID, textXml("openidSafe", 300103L, "会员卡"));
        String body = bodyOrEmpty(postEnvelope(accountId,
                "<xml><Encrypt><![CDATA[" + encrypt + "]]></Encrypt></xml>",
                timestamp, nonce, "deadbeefdeadbeef"));
        assertEquals("", body, "msg_signature 不符必须拒绝且不回显");
        assertEquals(1, countLogs("reject", "bad_msg_signature"), "应落 bad_msg_signature 拒绝审计");
        assertEquals(0, countLogs("in", "ok"), "验签不过不得进入主流程");
    }

    @Test
    void compatibleModeEncryptedPushGetsPlaintextReply() throws Exception {
        WxAccountEntity account = accountRepository.findById(accountId).orElseThrow();
        account.setEncryptMode("compatible");
        accountRepository.save(account);

        String timestamp = "1700000103";
        String nonce = "nonceC1";
        String encrypt = cryptor.encrypt(ENC_KEY, APP_ID, textXml("openidCompat", 300104L, "会员卡"));
        String body = postEnvelope(accountId,
                "<xml><Encrypt><![CDATA[" + encrypt + "]]></Encrypt></xml>",
                timestamp, nonce, msgSignatureOf(encrypt, timestamp, nonce));

        assertTrue(body != null && body.contains("会员卡请到前台办理"), "兼容模式回复保持明文：" + body);
        assertFalse(body.contains("<Encrypt>"), "兼容模式回复不得包信封：" + body);
        assertEquals(1, countLogs("in", "ok"), "兼容模式解密后应正常落流水");
    }
}
