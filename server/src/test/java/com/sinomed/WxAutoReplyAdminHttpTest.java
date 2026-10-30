package com.sinomed;

import com.sinomed.entity.WxAccountEntity;
import com.sinomed.repository.WxAccountRepository;
import com.sinomed.repository.WxAutoReplyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 管理端自动回复规则 CRUD HTTP 回归（设计稿第二期「管理端规则编辑 UI」服务端半边）：
 * 增删改查 + 校验（ruleType/matchType/replyType 枚举、keyword 必填约束、subscribe/fallback
 * 单账号单条生效=实体注释口径的服务层约束）+ 未登录 401 + 管理端建规则驱动回调被动回复端到端。
 * 与 message-logs 同口径：不依赖模块开关（模块关着也能编辑规则，先建后激活）。
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:sqlite:target/wx-reply-admin-test-${random.uuid}.db",
        "sinomed.official-account.enabled=true",
        "sinomed.official-account.token=REPLYADMINTOKEN"
})
class WxAutoReplyAdminHttpTest {

    private static final String TOKEN = "REPLYADMINTOKEN";

    @LocalServerPort
    private int port;

    private final RestTemplate rest = new RestTemplate();

    private String token;

    @Autowired
    private WxAccountRepository accountRepository;
    @Autowired
    private WxAutoReplyRepository autoReplyRepository;

    private Long accountId;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @BeforeEach
    void seed() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        var resp = rest.postForEntity(url("/api/v1/user/login"),
                new HttpEntity<>(Map.of("username", "admin", "password", "123"), headers), Map.class);
        token = (String) resp.getBody().get("token");

        autoReplyRepository.deleteAll();
        accountRepository.deleteAll();
        WxAccountEntity account = new WxAccountEntity();
        account.setAppId("wxREPLY1");
        account.setAccountName("规则管理测试号");
        account.setAccountType("service");
        account.setVerified(1);
        accountId = accountRepository.save(account).getId();
    }

    private HttpHeaders auth() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return headers;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String body) {
        return rest.exchange(url("/api/v1/wx/auto-replies"), HttpMethod.POST,
                new HttpEntity<>(body, auth()), Map.class).getBody();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> put(String body) {
        return rest.exchange(url("/api/v1/wx/auto-replies"), HttpMethod.PUT,
                new HttpEntity<>(body, auth()), Map.class).getBody();
    }

    /** 拒绝统一走 400 + {success:false,errorMessage}（ResponseExceptionAdvice 口径） */
    private void postExpect400(String body) {
        try {
            Map<String, Object> resp = post(body);
            throw new AssertionError("应拒绝但成功落库：" + body + " => " + resp);
        } catch (HttpClientErrorException e) {
            assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode(), "应 400：" + body + " => " + e.getResponseBodyAsString());
        }
    }

    private void putExpect400(String body) {
        try {
            Map<String, Object> resp = put(body);
            throw new AssertionError("应拒绝但成功：" + body + " => " + resp);
        } catch (HttpClientErrorException e) {
            assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode(), "应 400：" + body + " => " + e.getResponseBodyAsString());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> page(String query) {
        return rest.exchange(url("/api/v1/wx/auto-replies/page" + query), HttpMethod.GET,
                new HttpEntity<>(auth()), Map.class).getBody();
    }

    private long totalOf(Map<String, Object> resp) {
        return ((Number) resp.get("total")).longValue();
    }

    @SuppressWarnings("unchecked")
    private java.util.List<Map<String, Object>> rows(Map<String, Object> resp) {
        return (java.util.List<Map<String, Object>>) resp.get("data");
    }

    private String ruleBody(Long id, String ruleType, String keyword, String matchType,
                            String content, int priority, int enabled) {
        StringBuilder sb = new StringBuilder("{");
        if (id != null) sb.append("\"id\":").append(id).append(",");
        sb.append("\"accountId\":").append(accountId);
        sb.append(",\"ruleType\":\"").append(ruleType).append("\"");
        if (keyword != null) sb.append(",\"keyword\":\"").append(keyword).append("\"");
        if (matchType != null) sb.append(",\"matchType\":\"").append(matchType).append("\"");
        sb.append(",\"content\":\"").append(content).append("\"");
        sb.append(",\"priority\":").append(priority);
        sb.append(",\"enabled\":").append(enabled).append("}");
        return sb.toString();
    }

    // ---------- 校验 ----------

    @Test
    void rejectsInvalidRuleInput() {
        postExpect400(ruleBody(null, "keyword", "营业", "exact", "9:00-18:00", 0, 1)
                .replace(accountId.toString(), "999999")); // 未知账号
        postExpect400(ruleBody(null, "greeting", "营业", "exact", "欢迎", 0, 1)); // 非法 ruleType
        postExpect400(ruleBody(null, "keyword", null, "exact", "欢迎", 0, 1)); // keyword 型缺关键词
        postExpect400(ruleBody(null, "keyword", "  ", "exact", "欢迎", 0, 1)); // keyword 型空白关键词
        postExpect400(ruleBody(null, "keyword", "营业", "fuzzy", "欢迎", 0, 1)); // 非法 matchType
        postExpect400(ruleBody(null, "keyword", "营业", "exact", "欢迎", 0, 1)
                .replace("\"matchType\":\"exact\"", "\"replyType\":\"news\"")); // replyType 第一期仅 text
        postExpect400(ruleBody(null, "keyword", "营业", "exact", "  ", 0, 1)); // 空回复内容
        assertEquals(0, autoReplyRepository.count(), "全部拒绝不得落库");
    }

    @Test
    void createsRulesAndPagesWithFilters() {
        Map<String, Object> kw = post(ruleBody(null, "keyword", "营业", "contains", "9:00-18:00", 10, 1));
        assertTrue(((Number) kw.get("id")).longValue() > 0, "创建应返回 id：" + kw);

        Map<String, Object> sub = post(ruleBody(null, "subscribe", "被忽略的关键词", null, "欢迎关注", 0, 1));
        assertNull(sub.get("keyword"), "非 keyword 型规则的关键词应被清空，不污染匹配：" + sub);
        assertEquals("exact", sub.get("matchType"), "matchType 缺省 exact");

        post(ruleBody(null, "keyword", "预约", "exact", "请到前台预约", 5, 0));

        Map<String, Object> all = page("?current=1&pageSize=10&accountId=" + accountId);
        assertEquals(3L, totalOf(all));
        assertEquals(0, rows(all).get(0).get("priority"), "默认按 priority ASC（小者先试）");
        assertEquals(5, rows(all).get(1).get("priority"));
        assertEquals(10, rows(all).get(2).get("priority"));

        assertEquals(2L, totalOf(page("?current=1&pageSize=10&ruleType=keyword")), "按类型过滤");
        assertEquals(2L, totalOf(page("?current=1&pageSize=10&enabled=1")), "按启用态过滤");
        assertEquals(1L, totalOf(page("?current=1&pageSize=10&ruleType=keyword&enabled=1")), "组合过滤 AND");
    }

    @Test
    void keepsSingleEnabledSubscribeAndFallbackPerAccount() {
        assertTrue(((Number) post(ruleBody(null, "subscribe", null, null, "欢迎关注", 0, 1)).get("id")).longValue() > 0);
        postExpect400(ruleBody(null, "subscribe", null, null, "第二条欢迎语", 0, 1)); // 已有启用的 subscribe

        // fallback 同口径
        assertTrue(((Number) post(ruleBody(null, "fallback", null, null, "兜底话术", 0, 1)).get("id")).longValue() > 0);
        postExpect400(ruleBody(null, "fallback", null, null, "第二条兜底", 0, 1));

        // keyword 型不受单条约束
        assertTrue(((Number) post(ruleBody(null, "keyword", "A", "exact", "回复A", 0, 1)).get("id")).longValue() > 0);
        assertTrue(((Number) post(ruleBody(null, "keyword", "B", "exact", "回复B", 0, 1)).get("id")).longValue() > 0);

        // 停用旧的 subscribe 后可建新的启用 subscribe
        Number firstId = ((Number) rows(page("?current=1&pageSize=10&ruleType=subscribe")).get(0).get("id"));
        rest.exchange(url("/api/v1/wx/auto-replies"), HttpMethod.PUT,
                new HttpEntity<>(ruleBody(firstId.longValue(), "subscribe", null, null, "欢迎关注", 0, 0), auth()), Map.class);
        assertTrue(((Number) post(ruleBody(null, "subscribe", null, null, "新的欢迎语", 0, 1)).get("id")).longValue() > 0);
    }

    @Test
    void updatesAndDeletesRuleLifecycle() {
        Number id = (Number) post(ruleBody(null, "keyword", "营业", "exact", "9:00-18:00", 10, 1)).get("id");

        Map<String, Object> updated = rest.exchange(url("/api/v1/wx/auto-replies"), HttpMethod.PUT,
                new HttpEntity<>(ruleBody(id.longValue(), "keyword", "上班", "contains", "随时到店", 3, 0), auth()), Map.class
        ).getBody();
        assertEquals("上班", updated.get("keyword"), "更新应生效");
        assertEquals(0, updated.get("enabled"));

        putExpect400(ruleBody(999999L, "keyword", "营业", "exact", "x", 0, 1)); // 不存在的 id

        assertEquals(HttpStatus.OK, rest.exchange(url("/api/v1/wx/auto-replies/" + id), HttpMethod.DELETE,
                new HttpEntity<>(auth()), Map.class).getStatusCode());
        assertEquals(0L, totalOf(page("?current=1&pageSize=10")), "删除后列表应清空");

        // 再删同 id 应明确 400，不静默
        try {
            rest.exchange(url("/api/v1/wx/auto-replies/" + id), HttpMethod.DELETE, new HttpEntity<>(auth()), Map.class);
            throw new AssertionError("重复删除应 400");
        } catch (HttpClientErrorException e) {
            assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
        }
    }

    @Test
    void adminCreatedRuleDrivesCallbackReply() throws Exception {
        post(ruleBody(null, "keyword", "营业", "contains", "9:00-18:00", 0, 1));

        String reply = postXml(accountId, "1760000100", "nonceR1", textXml("oFANX", 8001L, "请问营业时间几点"));
        assertTrue(reply.contains("9:00-18:00"), "管理端建的 contains 规则应直接驱动被动回复（被动回复为 text XML）：" + reply);

        // 停用规则后不再命中
        Number id = (Number) rows(page("?current=1&pageSize=10&ruleType=keyword")).get(0).get("id");
        rest.exchange(url("/api/v1/wx/auto-replies"), HttpMethod.PUT,
                new HttpEntity<>(ruleBody(id.longValue(), "keyword", "营业", "contains", "9:00-18:00", 0, 0), auth()), Map.class);
        String after = postXml(accountId, "1760000101", "nonceR2", textXml("oFANX", 8002L, "请问营业时间几点"));
        assertEquals("success", after, "停用规则不应回复（走兜底/无规则口径）");
    }

    @Test
    void listsAccountsForAdminWithoutSecrets() {
        // 端点返回数组形（单店账号量小，不分页）
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> list = rest.exchange(url("/api/v1/wx/accounts"),
                HttpMethod.GET, new HttpEntity<>(auth()), java.util.List.class).getBody();
        assertEquals(1, list.size(), "应含种子账号：" + list);
        Map<String, Object> row = list.get(0);
        assertEquals("wxREPLY1", row.get("appId"));
        assertEquals("规则管理测试号", row.get("accountName"));
        assertEquals("service", row.get("accountType"));
        assertEquals(1, row.get("verified"));
        assertEquals(1, row.get("enabled"));
        // 种子账号未配置任何密钥：未配置字段回显 null，明文绝不下发（§6.3「never echoes back」）
        assertNull(row.get("appSecret"), "未配置 appSecret 应回显 null：" + row);
        assertNull(row.get("token"), "未配置 token 应回显 null：" + row);
        assertNull(row.get("aesKey"), "未配置 aesKey 应回显 null：" + row);
    }

    @Test
    void requiresLogin() {
        HttpHeaders plain = new HttpHeaders();
        plain.setContentType(MediaType.APPLICATION_JSON);
        HttpClientErrorException ex = assertThrows(HttpClientErrorException.class,
                () -> rest.exchange(url("/api/v1/wx/auto-replies/page?current=1&pageSize=10"),
                        HttpMethod.GET, new HttpEntity<>(plain), Map.class));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode(), "未登录分页应 401");

        HttpClientErrorException postEx = assertThrows(HttpClientErrorException.class,
                () -> rest.postForEntity(url("/api/v1/wx/auto-replies"),
                        new HttpEntity<>(ruleBody(null, "keyword", "x", "exact", "y", 0, 1), plain), Map.class));
        assertEquals(HttpStatus.UNAUTHORIZED, postEx.getStatusCode(), "未登录创建应 401");

        HttpClientErrorException accountsEx = assertThrows(HttpClientErrorException.class,
                () -> rest.exchange(url("/api/v1/wx/accounts"), HttpMethod.GET,
                        new HttpEntity<>(plain), java.util.List.class));
        assertEquals(HttpStatus.UNAUTHORIZED, accountsEx.getStatusCode(), "未登录账号列表应 401");
    }

    // ---------- 回调端到端辅助（与 WxOfficialAccountHttpTest 同规格独立实现） ----------

    private static String sha1Hex(String input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest(input.getBytes(StandardCharsets.UTF_8))) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private String postXml(Object accountId2, String timestamp, String nonce, String xml) throws Exception {
        String joined = Arrays.stream(new String[]{TOKEN, timestamp, nonce}).sorted()
                .collect(Collectors.joining());
        String signature = sha1Hex(joined);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "xml", StandardCharsets.UTF_8));
        return rest.exchange(url("/api/v1/wx/callback/" + accountId2
                        + "?signature=" + signature + "&timestamp=" + timestamp + "&nonce=" + nonce),
                HttpMethod.POST, new HttpEntity<>(xml, headers), String.class).getBody();
    }

    private String textXml(String fromUser, long msgId, String content) {
        return "<xml><ToUserName><![CDATA[gh_test]]></ToUserName>"
                + "<FromUserName><![CDATA[" + fromUser + "]]></FromUserName>"
                + "<CreateTime>1760000000</CreateTime><MsgType><![CDATA[text]]></MsgType>"
                + "<Content><![CDATA[" + content + "]]></Content>"
                + "<MsgId>" + msgId + "</MsgId></xml>";
    }
}
