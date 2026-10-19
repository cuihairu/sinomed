package com.sinomed;

import com.sinomed.entity.CustomerEntity;
import com.sinomed.entity.WxAccountEntity;
import com.sinomed.entity.WxBindingLogEntity;
import com.sinomed.entity.WxFanEntity;
import com.sinomed.repository.CustomerRepository;
import com.sinomed.repository.SmsCodeRepository;
import com.sinomed.repository.WxAccountRepository;
import com.sinomed.repository.WxBindingLogRepository;
import com.sinomed.repository.WxFanRepository;
import com.sinomed.repository.WxMessageLogRepository;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 网页授权（snsapi_base）HTTP 回归（设计稿 §5.6，2026-10-19 巡检代决 D-OA1..D-OA5）：
 * start 302 到微信授权页（HMAC state）；callback 用 code 换 openid（出网走可配 base-url，
 * 测试用 JDK HttpServer 桩 api.weixin.qq.com）→ 发一次性 ticket → 302 回 H5 绑定页；
 * bind 支持 ticket 代 openid；全程拒绝（bad_state/bad_exchange/bad_ticket）明确报错并落审计。
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
        "sinomed.official-account.enabled=true",
        "sinomed.official-account.token=OAUTHTOKEN"
})
class WxOauthHttpTest {

    private static final String DB_URL = "jdbc:sqlite:target/wx-oauth-test-" + UUID.randomUUID() + ".db";
    private static final String REDIRECT_BASE = "https://h5.sinomed.local";
    private static final String STUB_OPENID = "oSTUBOPENID1";

    /** 桩 api.weixin.qq.com /sns/oauth2/access_token：code=GOOD 返 openid，其余返 errcode */
    private static final HttpServer WECHAT_API = startWechatApiStub();
    private static final String API_BASE = "http://localhost:" + WECHAT_API.getAddress().getPort();

    @DynamicPropertySource
    static void pins(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DB_URL);
        registry.add("sinomed.official-account.credential-key", () -> "test-cred-key");
        registry.add("sinomed.official-account.oauth-redirect-base", () -> REDIRECT_BASE);
        registry.add("sinomed.official-account.wechat-api-base-url", () -> API_BASE);
    }

    private static HttpServer startWechatApiStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/sns/oauth2/access_token", exchange -> {
                String code = queryParam(exchange.getRequestURI().getRawQuery(), "code");
                String json = "GOOD".equals(code)
                        ? "{\"access_token\":\"AT\",\"expires_in\":7200,\"openid\":\"" + STUB_OPENID + "\",\"scope\":\"snsapi_base\"}"
                        : "{\"errcode\":40029,\"errmsg\":\"invalid code\"}";
                byte[] body = json.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            return server;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String queryParam(String rawQuery, String name) {
        if (rawQuery == null) return null;
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && name.equals(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8))) {
                return URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    @LocalServerPort
    private int port;

    private final RestTemplate rest = new RestTemplate();

    /** 302 断言不能用默认跟随重定向的 RestTemplate，一律走不跟随的 JDK client */
    private final HttpClient noRedirect = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER).build();

    @Autowired
    private WxAccountRepository accountRepository;
    @Autowired
    private WxFanRepository fanRepository;
    @Autowired
    private WxBindingLogRepository bindingLogRepository;
    @Autowired
    private WxMessageLogRepository messageLogRepository;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private SmsCodeRepository smsCodeRepository;

    private static final String PHONE = "13900008001";

    private Long accountId;
    private Long customerId;

    @BeforeEach
    void seed() {
        bindingLogRepository.deleteAll();
        fanRepository.deleteAll();
        smsCodeRepository.deleteAll();
        accountRepository.deleteAll();
        customerRepository.deleteAll();

        WxAccountEntity account = new WxAccountEntity();
        account.setAppId("wxOAAUTH" + System.nanoTime());
        account.setAppSecret("stub-app-secret");
        account.setAccountType("service");
        account.setVerified(1);
        accountId = accountRepository.save(account).getId();

        CustomerEntity customer = new CustomerEntity();
        customer.setName("授权绑定顾客");
        customer.setPhone(PHONE);
        customerId = customerRepository.save(customer).getId();
    }

    private String oauthGetLocation(String pathAndQuery) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + pathAndQuery)).GET().build();
        HttpResponse<byte[]> resp = noRedirect.send(req, HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(302, resp.statusCode(), "OAuth 流程步骤应 302：" + new String(resp.body(), StandardCharsets.UTF_8));
        return resp.headers().firstValue("Location").orElseThrow();
    }

    /** start → callback(GOOD code) → 返回 H5 落地地址（含 ticket） */
    private String fullFlowTicket() throws Exception {
        String startLoc = oauthGetLocation("/api/v1/wx/oauth/" + accountId + "/start");
        String state = queryParam(URI.create(startLoc).getRawQuery(), "state");
        String callbackLoc = oauthGetLocation("/api/v1/wx/oauth/" + accountId + "/callback?code=GOOD&state="
                + URLEncoder.encode(state, StandardCharsets.UTF_8));
        assertTrue(callbackLoc.startsWith(REDIRECT_BASE + "/h5/wx/bind#ticket="),
                "应回 H5 绑定页并带 ticket：" + callbackLoc);
        return callbackLoc.substring((REDIRECT_BASE + "/h5/wx/bind#ticket=").length());
    }

    private String requestCode(String phone) {
        ResponseEntity<String> resp = post("/api/v1/wx/bind/" + accountId + "/code",
                "{\"phone\":\"" + phone + "\"}");
        assertEquals(HttpStatus.OK, resp.getStatusCode(), "发码应成功：" + resp.getBody());
        return smsCodeRepository.findFirstByPhoneOrderByIdDesc(phone).orElseThrow().getCode();
    }

    private ResponseEntity<String> post(String path, String body) {
        var req = RequestEntity.post(URI.create("http://localhost:" + port + path))
                .contentType(MediaType.APPLICATION_JSON).body(body);
        try {
            return rest.exchange(req, String.class);
        } catch (HttpClientErrorException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        }
    }

    @Test
    void startRedirectsToAuthorizeUrlWithState() throws Exception {
        String loc = oauthGetLocation("/api/v1/wx/oauth/" + accountId + "/start");
        assertTrue(loc.startsWith("https://open.weixin.qq.com/connect/oauth2/authorize?"),
                "应 302 到微信授权页：" + loc);
        String query = URI.create(loc).getRawQuery();
        WxAccountEntity account = accountRepository.findById(accountId).orElseThrow();
        assertEquals(account.getAppId(), queryParam(query, "appid"), "appid 应取自账号");
        assertEquals(REDIRECT_BASE + "/api/v1/wx/oauth/" + accountId + "/callback",
                queryParam(query, "redirect_uri"), "redirect_uri 应为 oauth-redirect-base + callback");
        assertEquals("snsapi_base", queryParam(query, "scope"), "静态授权只取 snsapi_base（D-OA2）");
        assertEquals("code", queryParam(query, "response_type"));
        assertTrue(queryParam(query, "state") != null && queryParam(query, "state").contains(":"),
                "state 应为 payload:签名 结构（D-OA3）");

        assertTrue(oauthRejectAudit("account_missing").isEmpty(), "前置：未知账号前无 account_missing 审计");
        HttpClientErrorException ex = org.junit.jupiter.api.Assertions.assertThrows(HttpClientErrorException.class,
                () -> rest.getForEntity(URI.create("http://localhost:" + port + "/api/v1/wx/oauth/999999/start"), String.class));
        assertEquals(400, ex.getStatusCode().value(), "未知账号 start 应明确 400");
        assertFalse(oauthRejectAudit("account_missing").isEmpty(), "未知账号应落 account_missing 拒绝审计");
    }

    @Test
    void callbackExchangesCodeAndTicketBindsFan() throws Exception {
        String ticket = fullFlowTicket();
        assertTrue(ticket.length() >= 16, "ticket 应为不可猜随机串");

        String code = requestCode(PHONE);
        ResponseEntity<String> resp = post("/api/v1/wx/bind/" + accountId,
                "{\"ticket\":\"" + ticket + "\",\"phone\":\"" + PHONE + "\",\"smsCode\":\"" + code + "\"}");
        assertEquals(HttpStatus.OK, resp.getStatusCode(), "ticket 绑定应成功：" + resp.getBody());

        WxFanEntity fan = fanRepository.findByAccountIdAndOpenid(accountId, STUB_OPENID).orElseThrow();
        assertEquals(customerId, fan.getCustomerId(), "openid 应来自 code 换取（桩返回），绑定写入 customer_id");
        WxBindingLogEntity audit = bindingLogRepository.findAll().stream()
                .reduce((first, second) -> second).orElseThrow();
        assertEquals("ok", audit.getResult(), "成功绑定落 ok 审计");
    }

    @Test
    void callbackRejectsBadStateAndBadCodeWithAudit() throws Exception {
        String startLoc = oauthGetLocation("/api/v1/wx/oauth/" + accountId + "/start");
        String state = queryParam(URI.create(startLoc).getRawQuery(), "state");

        // RestTemplate 对 String URL 会再编码（%7C→%257C 毁 state），查询串必须经 URI 对象原样传递
        HttpClientErrorException tampered = org.junit.jupiter.api.Assertions.assertThrows(
                HttpClientErrorException.class,
                () -> rest.getForEntity(URI.create("http://localhost:" + port + "/api/v1/wx/oauth/" + accountId
                        + "/callback?code=GOOD&state=" + URLEncoder.encode(state + "x", StandardCharsets.UTF_8)),
                        String.class));
        assertEquals(400, tampered.getStatusCode().value(), "篡改 state 应明确 400（不静默）");
        assertFalse(oauthRejectAudit("bad_state").isEmpty(), "篡改 state 应落 bad_state 拒绝审计");

        HttpClientErrorException badCode = org.junit.jupiter.api.Assertions.assertThrows(
                HttpClientErrorException.class,
                () -> rest.getForEntity(URI.create("http://localhost:" + port + "/api/v1/wx/oauth/" + accountId
                        + "/callback?code=BAD&state=" + URLEncoder.encode(state, StandardCharsets.UTF_8)),
                        String.class));
        assertEquals(400, badCode.getStatusCode().value(), "换 openid 失败应明确 400");
        assertFalse(oauthRejectAudit("bad_exchange").isEmpty(), "换取失败应落 bad_exchange 拒绝审计");
        assertEquals(0, fanRepository.count(), "失败流程不得建粉丝行");
    }

    @Test
    void ticketIsSingleUse() throws Exception {
        String ticket = fullFlowTicket();
        String code = requestCode(PHONE);
        assertEquals(HttpStatus.OK, post("/api/v1/wx/bind/" + accountId,
                "{\"ticket\":\"" + ticket + "\",\"phone\":\"" + PHONE + "\",\"smsCode\":\"" + code + "\"}")
                .getStatusCode(), "首次 ticket 绑定应成功");

        String code2 = requestCode(PHONE);
        ResponseEntity<String> reused = post("/api/v1/wx/bind/" + accountId,
                "{\"ticket\":\"" + ticket + "\",\"phone\":\"" + PHONE + "\",\"smsCode\":\"" + code2 + "\"}");
        assertEquals(HttpStatus.BAD_REQUEST, reused.getStatusCode(), "同 ticket 二次兑换应拒绝（一次性）");
        assertTrue(bindingLogRepository.findAll().stream().anyMatch(b -> "bad_ticket".equals(b.getResult())),
                "ticket 复用应落 bad_ticket 绑定审计（绑定流拒绝族，同 missing_openid 口径）");
    }

    private java.util.List<String> oauthRejectAudit(String result) {
        return messageLogRepository.findAll().stream()
                .filter(m -> "reject".equals(m.getDirection()) && result.equals(m.getResult()))
                .map(m -> m.getResult())
                .toList();
    }
}
