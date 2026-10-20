package com.sinomed;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sinomed.entity.WxAccountEntity;
import com.sinomed.entity.WxMenuEntity;
import com.sinomed.repository.WxAccountRepository;
import com.sinomed.repository.WxMenuRepository;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 菜单推送微信半边回归（设计稿 §4.3「推送」；真实出网压凭据，本机走 wechat-api-base-url 指向
 * JDK HttpServer 本地桩——D-OA4/WxMaterialCaptureHttpTest 先例，零外网）：
 * 取 token（cgi-bin/token）→ POST cgi-bin/menu/create；只推启用菜单（草稿不推送）、
 * 父带子只带 name+sub_button、子与无子父带类型字段；微信 errcode 透传不静默。
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
        "sinomed.official-account.enabled=true"
})
class WxMenuPushHttpTest {

    private static final String DB_URL = "jdbc:sqlite:target/wx-menu-push-test-" + java.util.UUID.randomUUID() + ".db";

    /** 微信桩：静态起一次，端口进 @DynamicPropertySource（上下文起之前就要拿到） */
    static volatile String tokenPayload = "{\"access_token\":\"TOKEN123\",\"expires_in\":7200}";
    static volatile String menuReply = "{\"errcode\":0,\"errmsg\":\"ok\"}";
    static final AtomicInteger tokenHits = new AtomicInteger();
    static final AtomicInteger menuHits = new AtomicInteger();
    static volatile String lastCreateQuery;
    static volatile String lastCreateBody;

    private static final HttpServer STUB = newStub();

    private static HttpServer newStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", exchange -> {
                String path = exchange.getRequestURI().getPath();
                String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                boolean isToken = path.endsWith("/cgi-bin/token");
                if (isToken) {
                    tokenHits.incrementAndGet();
                } else {
                    menuHits.incrementAndGet();
                    lastCreateQuery = exchange.getRequestURI().getRawQuery();
                    lastCreateBody = requestBody;
                }
                byte[] out = (isToken ? tokenPayload : menuReply).getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, out.length);
                exchange.getResponseBody().write(out);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException("微信桩起不来", e);
        }
    }

    @DynamicPropertySource
    static void pin(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DB_URL);
        registry.add("sinomed.official-account.credential-key", () -> "test-cred-key");
        registry.add("sinomed.official-account.wechat-api-base-url", () -> "http://localhost:" + STUB.getAddress().getPort());
    }

    @AfterAll
    static void stopStub() {
        STUB.stop(0);
    }

    @LocalServerPort
    private int port;

    private final RestTemplate rest = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    private WxAccountRepository accountRepository;
    @Autowired
    private WxMenuRepository menuRepository;

    private String token;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private HttpHeaders auth() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private Map<String, Object> login() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        var resp = rest.postForEntity(url("/api/v1/user/login"),
                new HttpEntity<>(Map.of("username", "admin", "password", "123"), headers), Map.class);
        return resp.getBody();
    }

    private WxAccountEntity seedAccount(String appId, boolean withSecret) {
        WxAccountEntity account = new WxAccountEntity();
        account.setAppId(appId);
        account.setAccountName("推送测试-" + appId);
        account.setAccountType("service");
        account.setVerified(1);
        if (withSecret) {
            account.setAppSecret("PUSH-SECRET");
        }
        return accountRepository.save(account);
    }

    private WxMenuEntity menu(WxAccountEntity account, WxMenuEntity parent, String name, String type,
                              String url, String eventKey, String appid, String pagepath, int sort, int enabled) {
        WxMenuEntity m = new WxMenuEntity();
        m.setAccountId(account.getId());
        m.setParentId(parent == null ? null : parent.getId());
        m.setName(name);
        m.setMenuType(type);
        m.setUrl(url);
        m.setEventKey(eventKey);
        m.setMiniprogramAppid(appid);
        m.setMiniprogramPagepath(pagepath);
        m.setSort(sort);
        m.setEnabled(enabled);
        return menuRepository.save(m);
    }

    private Map<String, Object> push(long accountId) {
        return rest.exchange(url("/api/v1/wx/menus/push/" + accountId), HttpMethod.POST,
                new HttpEntity<>(auth()), Map.class).getBody();
    }

    @BeforeEach
    void reset() {
        token = (String) login().get("token");
        menuRepository.deleteAll();
        accountRepository.deleteAll();
        tokenHits.set(0);
        menuHits.set(0);
        lastCreateQuery = null;
        lastCreateBody = null;
        tokenPayload = "{\"access_token\":\"TOKEN123\",\"expires_in\":7200}";
        menuReply = "{\"errcode\":0,\"errmsg\":\"ok\"}";
    }

    @Test
    void pushBuildsTwoLevelMenuAndExcludesDrafts() throws Exception {
        WxAccountEntity account = seedAccount("wxPUSH1", true);
        WxMenuEntity top1 = menu(account, null, "服务", "view", "https://a.cn/svc", null, null, null, 0, 1);
        menu(account, top1, "自测", "click", null, "OPEN_SELF", null, null, 0, 1);
        menu(account, top1, "草稿子", "click", null, "OPEN_DRAFT", null, null, 1, 0); // 草稿不推送
        WxMenuEntity top2 = menu(account, null, "预约", "view", "https://a.cn/book", null, null, null, 1, 1);
        menu(account, top2, "扫码", "scancode_push", null, "SCAN_1", null, null, 0, 1);
        menu(account, null, "小程序", "view_miniprogram", "https://a.cn/fallback", null, "wxMP", "pages/index/index", 2, 1);

        Map<String, Object> resp = push(account.getId());
        assertTrue(String.valueOf(resp.get("message")).contains("3"), "应推 3 个一级菜单：" + resp);

        assertEquals("access_token=TOKEN123", lastCreateQuery, "token 要拼进 menu/create 查询串");
        assertEquals(1, tokenHits.get());
        assertEquals(1, menuHits.get());

        Map<String, Object> body = mapper.readValue(lastCreateBody, Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> buttons = (List<Map<String, Object>>) body.get("button");
        assertEquals(3, buttons.size());

        // 带子菜单的父级只带 name+sub_button（微信忽略父级动作），草稿子菜单被剔除
        Map<String, Object> b0 = buttons.get(0);
        assertEquals("服务", b0.get("name"));
        assertTrue(!b0.containsKey("type") && !b0.containsKey("url"), "带子菜单的父级不应带动作字段：" + b0);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> subs0 = (List<Map<String, Object>>) b0.get("sub_button");
        assertEquals(1, subs0.size());
        assertEquals("自测", subs0.get(0).get("name"));
        assertEquals("click", subs0.get(0).get("type"));
        assertEquals("OPEN_SELF", subs0.get(0).get("key"));

        Map<String, Object> b1 = buttons.get(1);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> subs1 = (List<Map<String, Object>>) b1.get("sub_button");
        assertEquals("scancode_push", subs1.get(0).get("type"));
        assertEquals("SCAN_1", subs1.get(0).get("key"));

        // 无子菜单的父级带自身动作字段；miniprogram 带备用网页 url + appid + pagepath
        Map<String, Object> b2 = buttons.get(2);
        assertEquals("view_miniprogram", b2.get("type"));
        assertEquals("https://a.cn/fallback", b2.get("url"));
        assertEquals("wxMP", b2.get("appid"));
        assertEquals("pages/index/index", b2.get("pagepath"));
    }

    @Test
    void wechatErrorIsPropagatedNotSilent() {
        WxAccountEntity account = seedAccount("wxPUSH2", true);
        menu(account, null, "服务", "view", "https://a.cn/svc", null, null, null, 0, 1);
        menuReply = "{\"errcode\":40014,\"errmsg\":\"invalid access_token\"}";

        HttpClientErrorException e = assertThrows(HttpClientErrorException.class, () -> push(account.getId()));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
        assertTrue(e.getResponseBodyAsString(StandardCharsets.UTF_8).contains("40014"),
                "errcode 要透传给管理端：" + e.getResponseBodyAsString());
    }

    @Test
    void tokenFailureIsRejectedWithErrcode() {
        WxAccountEntity account = seedAccount("wxPUSH3", true);
        menu(account, null, "服务", "view", "https://a.cn/svc", null, null, null, 0, 1);
        tokenPayload = "{\"errcode\":40013,\"errmsg\":\"invalid appid\"}";

        HttpClientErrorException e = assertThrows(HttpClientErrorException.class, () -> push(account.getId()));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
        assertTrue(e.getResponseBodyAsString(StandardCharsets.UTF_8).contains("40013"),
                "取 token 失败也要带 errcode：" + e.getResponseBodyAsString());
        assertEquals(0, menuHits.get(), "token 失败不应再调 menu/create");
    }

    @Test
    void accountWithoutSecretOrNoEnabledMenusIsRejected() {
        WxAccountEntity noSecret = seedAccount("wxPUSH4", false);
        menu(noSecret, null, "服务", "view", "https://a.cn/svc", null, null, null, 0, 1);
        HttpClientErrorException e1 = assertThrows(HttpClientErrorException.class, () -> push(noSecret.getId()));
        assertEquals(HttpStatus.BAD_REQUEST, e1.getStatusCode());
        assertEquals(0, tokenHits.get(), "缺 AppSecret 应在出网前拒绝");
        assertTrue(e1.getResponseBodyAsString(StandardCharsets.UTF_8).contains("AppSecret"));

        WxAccountEntity draftOnly = seedAccount("wxPUSH5", true);
        menu(draftOnly, null, "草稿", "view", "https://a.cn/x", null, null, null, 0, 0);
        HttpClientErrorException e2 = assertThrows(HttpClientErrorException.class, () -> push(draftOnly.getId()));
        assertEquals(HttpStatus.BAD_REQUEST, e2.getStatusCode());
        assertEquals(0, tokenHits.get(), "无启用菜单应在出网前拒绝");
    }

    @Test
    void unauthenticatedIsRejected() {
        HttpClientErrorException e = assertThrows(HttpClientErrorException.class,
                () -> rest.exchange(url("/api/v1/wx/menus/push/1"), HttpMethod.POST,
                        new HttpEntity<>(new HttpHeaders()), Map.class));
        assertEquals(HttpStatus.UNAUTHORIZED, e.getStatusCode());
    }
}
