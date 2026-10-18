package com.sinomed;

import com.sinomed.entity.WxAccountEntity;
import com.sinomed.entity.WxAutoReplyEntity;
import com.sinomed.entity.WxFanEntity;
import com.sinomed.repository.WxAccountRepository;
import com.sinomed.repository.WxAutoReplyRepository;
import com.sinomed.repository.WxFanRepository;
import com.sinomed.repository.WxMessageLogRepository;
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
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 公众号回调 HTTP 回归（第一期骨架，设计稿 docs/design/wechat-official-account.md §5）：
 * URL 验签回 echostr、明文消息验签+排重+落流水、关键词/欢迎语被动回复、subscribe/unsubscribe 粉丝维护。
 * 凭据无关（token 走测试配置），模块开关与账号开关均「拒绝处理回 success」不静默。走真实 HTTP。
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:sqlite:target/wx-oa-callback-test-${random.uuid}.db",
        "sinomed.official-account.enabled=true",
        "sinomed.official-account.token=TESTTOKEN"
})
class WxOfficialAccountHttpTest {

    @LocalServerPort
    private int port;

    private final RestTemplate rest = new RestTemplate();

    @Autowired
    private WxAccountRepository accountRepository;
    @Autowired
    private WxAutoReplyRepository autoReplyRepository;
    @Autowired
    private WxFanRepository fanRepository;
    @Autowired
    private WxMessageLogRepository messageLogRepository;

    private static final String TOKEN = "TESTTOKEN";
    private Long accountId;

    @BeforeEach
    void seed() {
        messageLogRepository.deleteAll();
        autoReplyRepository.deleteAll();
        fanRepository.deleteAll();
        accountRepository.deleteAll();
        WxAccountEntity account = new WxAccountEntity();
        account.setAppId("wxOA1001");
        account.setAccountName("测试服务号");
        account.setAccountType("service");
        account.setVerified(1);
        accountId = accountRepository.save(account).getId();
    }

    /** 独立实现微信验签算法（与被测代码同规格：SHA1(字典序排序拼接)），测试侧自算 */
    private static String sha1Hex(String input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest(input.getBytes(StandardCharsets.UTF_8))) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private String sign(String timestamp, String nonce) throws Exception {
        String joined = Arrays.stream(new String[]{TOKEN, timestamp, nonce}).sorted()
                .collect(Collectors.joining());
        return sha1Hex(joined);
    }

    private String callbackUrl(Object accountId, String query) {
        return "http://localhost:" + port + "/api/v1/wx/callback/" + accountId + query;
    }

    private String postXml(Object accountId, String timestamp, String nonce, String xml) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        // 显式 UTF-8：RestTemplate 对无 charset 的 text/xml 按 ISO-8859-1 编码字符串，中文上线前即毁（与 UriComponentsBuilder 单次编码同族的传输层坑）
        headers.setContentType(new MediaType("text", "xml", StandardCharsets.UTF_8));
        return rest.exchange(callbackUrl(accountId,
                        "?signature=" + sign(timestamp, nonce) + "&timestamp=" + timestamp + "&nonce=" + nonce),
                HttpMethod.POST, new HttpEntity<>(xml, headers), String.class).getBody();
    }

    private String textXml(String fromUser, long msgId, String content) {
        return "<xml><ToUserName><![CDATA[gh_test]]></ToUserName>"
                + "<FromUserName><![CDATA[" + fromUser + "]]></FromUserName>"
                + "<CreateTime>1760000000</CreateTime><MsgType><![CDATA[text]]></MsgType>"
                + "<Content><![CDATA[" + content + "]]></Content>"
                + "<MsgId>" + msgId + "</MsgId></xml>";
    }

    private String eventXml(String fromUser, String event) {
        return "<xml><ToUserName><![CDATA[gh_test]]></ToUserName>"
                + "<FromUserName><![CDATA[" + fromUser + "]]></FromUserName>"
                + "<CreateTime>1760000001</CreateTime><MsgType><![CDATA[event]]></MsgType>"
                + "<Event><![CDATA[" + event + "]]></Event></xml>";
    }

    private long countLogs(String direction) {
        return messageLogRepository.findAll().stream()
                .filter(l -> direction.equals(l.getDirection())).count();
    }

    @Test
    void urlVerificationEchoesEchostrOnValidSignature() throws Exception {
        String echostr = "ECHO-8391";
        String body = rest.getForObject(callbackUrl(accountId,
                "?signature=" + sign("1700000000", "nonceA")
                        + "&timestamp=1700000000&nonce=nonceA&echostr=" + echostr), String.class);
        assertEquals(echostr, body, "URL 验签通过应原样返回 echostr：" + body);
    }

    @Test
    void urlVerificationRejectsBadSignature() {
        String body = rest.getForObject(callbackUrl(accountId,
                "?signature=deadbeef&timestamp=1700000000&nonce=nonceA&echostr=SHOULD-NOT-ECHO"), String.class);
        assertFalse("SHOULD-NOT-ECHO".equals(body), "验签失败不得回显 echostr：" + body);
        assertEquals(1, countLogs("reject"), "验签失败应落一条拒绝审计：" + messageLogRepository.findAll());
    }

    @Test
    void plaintextTextMessageMatchesKeywordRuleAndReplies() throws Exception {
        WxAutoReplyEntity rule = new WxAutoReplyEntity();
        rule.setAccountId(accountId);
        rule.setRuleType("keyword");
        rule.setKeyword("营业时间");
        rule.setMatchType("exact");
        rule.setContent("营业时间 9:00-18:00");
        autoReplyRepository.save(rule);

        String reply = postXml(accountId, "1700000010", "nonceB", textXml("oFAN1", 9001L, "营业时间"));
        assertTrue(reply.contains("9:00-18:00"), "关键词命中应被动回复规则内容：" + reply);
        assertEquals(1, countLogs("in"), "入站应落流水：" + messageLogRepository.findAll());
        assertEquals(1, countLogs("out"), "出站回复应落流水");
    }

    @Test
    void subscribeEventUpsertsFanAndRepliesWelcome() throws Exception {
        WxAutoReplyEntity welcome = new WxAutoReplyEntity();
        welcome.setAccountId(accountId);
        welcome.setRuleType("subscribe");
        welcome.setContent("欢迎光临仁济堂");
        autoReplyRepository.save(welcome);

        String reply = postXml(accountId, "1700000020", "nonceC", eventXml("oFAN2", "subscribe"));
        assertTrue(reply.contains("欢迎光临仁济堂"), "关注事件应回欢迎语：" + reply);

        WxFanEntity fan = fanRepository.findByAccountIdAndOpenid(accountId, "oFAN2").orElseThrow();
        assertEquals(1, fan.getSubscribe(), "关注事件应 upsert 粉丝行 subscribe=1");
    }

    @Test
    void unsubscribeMarksFanAndReturnsSuccess() throws Exception {
        WxFanEntity fan = new WxFanEntity();
        fan.setAccountId(accountId);
        fan.setOpenid("oFAN3");
        fan.setSubscribe(1);
        fanRepository.save(fan);

        String body = postXml(accountId, "1700000030", "nonceD", eventXml("oFAN3", "unsubscribe"));
        assertEquals("success", body, "取关不回复，应回 success：" + body);
        assertEquals(0, fanRepository.findByAccountIdAndOpenid(accountId, "oFAN3").orElseThrow().getSubscribe(),
                "取关事件应置 subscribe=0（保留行）");
    }

    @Test
    void duplicateMsgIdSecondPostIgnored() throws Exception {
        String first = postXml(accountId, "1700000040", "nonceE", textXml("oFAN4", 7777L, "你好"));
        assertTrue(first.isEmpty() || "success".equals(first), "无规则命中的文本应回 success（兜底未配）：" + first);

        postXml(accountId, "1700000041", "nonceF", textXml("oFAN4", 7777L, "你好"));
        assertEquals(1, countLogs("in"), "同 MsgId 重发（微信 5s 重试）应排重只落一条流水");
    }

    @Test
    void unknownAccountReturnsSuccessAndAudits() throws Exception {
        String body = postXml(999999L, "1700000050", "nonceG", textXml("oFAN5", 8888L, "营业时间"));
        assertEquals("success", body, "未知账号应拒处理回 success（不让微信重试）：" + body);
        assertEquals(1, countLogs("reject"), "未知账号应落拒绝审计");
    }

    @Test
    void fallbackReplyWhenNoRuleMatches() throws Exception {
        WxAutoReplyEntity fallback = new WxAutoReplyEntity();
        fallback.setAccountId(accountId);
        fallback.setRuleType("fallback");
        fallback.setContent("收到，稍后人工回复您");
        autoReplyRepository.save(fallback);

        String reply = postXml(accountId, "1700000060", "nonceH", textXml("oFAN6", 9999L, "随便说什么"));
        assertTrue(reply.contains("人工回复"), "未命中关键词应回兜底规则：" + reply);
    }
}
