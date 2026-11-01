package com.sinomed;

import com.sinomed.entity.StaffEntity;
import com.sinomed.entity.WxAccountEntity;
import com.sinomed.entity.WxAutoReplyEntity;
import com.sinomed.entity.WxMenuEntity;
import com.sinomed.repository.StaffRepository;
import com.sinomed.repository.WxAccountRepository;
import com.sinomed.repository.WxAutoReplyRepository;
import com.sinomed.repository.WxMenuRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 公众号管理端角色门禁回归（设计稿 §6.3「Admin Operation Permissions」）：
 * 菜单/自动回复/素材/消息控制台 = MANAGER 及以上；账号配置写 = ADMIN；
 * 能力探测/账号列表/解绑属管理端工作台基础数据，同为 MANAGER+。
 * 素材 4 端点已有各自测试锁定（WxMaterialLocalUploadHttpTest / WxMaterialRemoteUploadHttpTest 403 断言），
 * 本类集中锁其余管理端端点：USER（role 99）一律 403，MANAGER（role 5）放行，未登录 401。
 * 回调/bind/oauth 粉丝侧端点不在本类（免登录，另由 WxOfficialAccountHttpTest 等覆盖）。
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = "sinomed.official-account.enabled=true")
class WxAdminRoleGuardHttpTest {

    private static final String DB_URL =
            "jdbc:sqlite:target/wx-admin-role-guard-" + UUID.randomUUID() + ".db";

    @DynamicPropertySource
    static void pin(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DB_URL);
    }

    @LocalServerPort
    private int port;

    private final RestTemplate rest = new RestTemplate();

    @Autowired
    private StaffRepository staffRepository;
    @Autowired
    private WxAccountRepository accountRepository;
    @Autowired
    private WxAutoReplyRepository autoReplyRepository;
    @Autowired
    private WxMenuRepository menuRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private String adminToken;
    private String managerToken;
    private String userToken;
    private Long accountId;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private String login(String username, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        var resp = rest.postForEntity(url("/api/v1/user/login"),
                new HttpEntity<>(Map.of("username", username, "password", password), headers), Map.class);
        return (String) resp.getBody().get("token");
    }

    private HttpEntity<Void> bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return new HttpEntity<>(headers);
    }

    private HttpEntity<Map<String, Object>> json(String token, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return new HttpEntity<>(body, headers);
    }

    @BeforeEach
    void seed() {
        accountRepository.deleteAll();
        menuRepository.deleteAll();
        autoReplyRepository.deleteAll();
        staffRepository.findAll().forEach(s -> {
            if ("wx-role-admin".equals(s.getAccount()) || "wx-role-manager".equals(s.getAccount())
                    || "wx-role-user".equals(s.getAccount())) {
                staffRepository.delete(s);
            }
        });

        StaffEntity admin = new StaffEntity();
        admin.setName("角色管理员");
        admin.setAccount("wx-role-admin");
        admin.setRole(0);
        admin.setStatus(1);
        admin.setPassword(passwordEncoder.encode("123"));
        staffRepository.save(admin);
        adminToken = login("wx-role-admin", "123");

        StaffEntity manager = new StaffEntity();
        manager.setName("角色经理");
        manager.setAccount("wx-role-manager");
        manager.setRole(5); // 1-9 = MANAGER
        manager.setStatus(1);
        manager.setPassword(passwordEncoder.encode("123"));
        staffRepository.save(manager);
        managerToken = login("wx-role-manager", "123");

        StaffEntity user = new StaffEntity();
        user.setName("角色普通员工");
        user.setAccount("wx-role-user");
        user.setRole(99); // >=10 = USER
        user.setStatus(1);
        user.setPassword(passwordEncoder.encode("123"));
        staffRepository.save(user);
        userToken = login("wx-role-user", "123");

        WxAccountEntity account = new WxAccountEntity();
        account.setAppId("wxROLE" + System.nanoTime());
        account.setAccountName("角色门禁测试号");
        account.setAccountType("service");
        account.setVerified(1);
        accountId = accountRepository.save(account).getId();
    }

    private void assertForbidden(String method, String path, Map<String, Object> body) {
        HttpClientErrorException e = assertThrows(HttpClientErrorException.class,
                () -> rest.exchange(url(path), HttpMethod.valueOf(method),
                        body == null ? bearer(userToken) : json(userToken, body), Map.class),
                "USER 应 403：" + method + " " + path);
        assertEquals(HttpStatus.FORBIDDEN, e.getStatusCode(), "USER 应 403：" + method + " " + path);
    }

    @Test
    void userIsForbiddenOnAllManagerEndpoints() {
        // 消息控制台（设计 §6.3 消息控制台 = MANAGER+）
        assertForbidden("GET", "/api/v1/wx/message-logs?current=1&pageSize=10", null);
        // 能力探测（管理端工作台基础）
        assertForbidden("GET", "/api/v1/wx/capabilities/" + accountId, null);
        // 粉丝解绑（消息/粉丝控制台 = MANAGER+）
        assertForbidden("POST", "/api/v1/wx/fans/" + accountId + "/unbind", Map.of("openid", "oX"));
        // 账号列表（管理端选择器，密钥仅掩码）
        assertForbidden("GET", "/api/v1/wx/accounts", null);
        // 自动回复 CRUD
        assertForbidden("GET", "/api/v1/wx/auto-replies/page?current=1&pageSize=10", null);
        assertForbidden("POST", "/api/v1/wx/auto-replies",
                Map.of("accountId", accountId, "ruleType", "keyword", "keyword", "x", "content", "y"));
        assertForbidden("PUT", "/api/v1/wx/auto-replies",
                Map.of("id", 1L, "accountId", accountId, "ruleType", "keyword", "keyword", "x", "content", "y"));
        assertForbidden("DELETE", "/api/v1/wx/auto-replies/1", null);
        // 菜单 CRUD + 推送
        assertForbidden("GET", "/api/v1/wx/menus?accountId=" + accountId, null);
        assertForbidden("POST", "/api/v1/wx/menus",
                Map.of("accountId", accountId, "name", "菜单", "menuType", "view", "url", "https://a.cn/x"));
        assertForbidden("PUT", "/api/v1/wx/menus",
                Map.of("id", 1L, "accountId", accountId, "name", "菜单", "menuType", "view", "url", "https://a.cn/x"));
        assertForbidden("DELETE", "/api/v1/wx/menus/1", null);
        assertForbidden("POST", "/api/v1/wx/menus/push/" + accountId, null);
    }

    @Test
    void userIsForbiddenOnAccountConfigWrite() {
        // 账号配置写 = ADMIN（设计 §6.3）；USER 403，即使账号写走 hasRole('ADMIN')
        assertForbidden("POST", "/api/v1/wx/accounts",
                Map.of("appId", "wxROLE-NEW" + System.nanoTime(), "accountType", "service"));
        assertForbidden("PUT", "/api/v1/wx/accounts/" + accountId,
                Map.of("appId", "wxROLE" + System.nanoTime(), "accountType", "service"));
    }

    @Test
    void managerIsAllowedOnManagerEndpoints() {
        // MANAGER 放行代表性子集（消息控制台/菜单/规则/账号列表）
        var logs = rest.exchange(url("/api/v1/wx/message-logs?current=1&pageSize=10"),
                HttpMethod.GET, bearer(managerToken), Map.class);
        assertEquals(HttpStatus.OK, logs.getStatusCode(), "MANAGER 应可读消息流水");
        assertTrue(((Number) ((Map<?, ?>) logs.getBody()).get("total")).longValue() >= 0);

        var menus = rest.exchange(url("/api/v1/wx/menus?accountId=" + accountId),
                HttpMethod.GET, bearer(managerToken), java.util.List.class);
        assertEquals(HttpStatus.OK, menus.getStatusCode(), "MANAGER 应可读菜单列表");

        var rules = rest.exchange(url("/api/v1/wx/auto-replies/page?current=1&pageSize=10"),
                HttpMethod.GET, bearer(managerToken), Map.class);
        assertEquals(HttpStatus.OK, rules.getStatusCode(), "MANAGER 应可读规则列表");

        var accounts = rest.exchange(url("/api/v1/wx/accounts"),
                HttpMethod.GET, bearer(managerToken), java.util.List.class);
        assertEquals(HttpStatus.OK, accounts.getStatusCode(), "MANAGER 应可读账号列表");

        Map<String, Object> capabilities = rest.exchange(url("/api/v1/wx/capabilities/" + accountId),
                HttpMethod.GET, bearer(managerToken), Map.class).getBody();
        assertEquals(accountId.intValue(), capabilities.get("accountId"), "MANAGER 应可读能力矩阵");

        WxAutoReplyEntity createdReply = rest.exchange(url("/api/v1/wx/auto-replies"),
                HttpMethod.POST,
                json(managerToken, Map.of(
                        "accountId", accountId,
                        "ruleType", "keyword",
                        "keyword", "菜单" + System.nanoTime(),
                        "content", "请点击菜单查看预约入口")),
                WxAutoReplyEntity.class).getBody();
        assertEquals("keyword", createdReply.getRuleType(), "MANAGER 应可创建自动回复规则");

        createdReply.setContent("请点击菜单查看预约入口（已更新）");
        WxAutoReplyEntity updatedReply = rest.exchange(url("/api/v1/wx/auto-replies"),
                HttpMethod.PUT, json(managerToken, Map.of(
                        "id", createdReply.getId(),
                        "accountId", createdReply.getAccountId(),
                        "ruleType", createdReply.getRuleType(),
                        "keyword", createdReply.getKeyword(),
                        "content", createdReply.getContent())),
                WxAutoReplyEntity.class).getBody();
        assertEquals("请点击菜单查看预约入口（已更新）", updatedReply.getContent(), "MANAGER 应可更新自动回复规则");

        rest.exchange(url("/api/v1/wx/auto-replies/" + createdReply.getId()),
                HttpMethod.DELETE, bearer(managerToken), Map.class);
        assertTrue(autoReplyRepository.findById(createdReply.getId()).isEmpty(), "MANAGER 应可删除自动回复规则");

        WxMenuEntity createdMenu = rest.exchange(url("/api/v1/wx/menus"),
                HttpMethod.POST,
                json(managerToken, Map.of(
                        "accountId", accountId,
                        "name", "预约",
                        "menuType", "view",
                        "url", "https://example.com/appointment",
                        "enabled", 0)),
                WxMenuEntity.class).getBody();
        assertEquals("预约", createdMenu.getName(), "MANAGER 应可创建菜单");

        createdMenu.setName("挂号");
        WxMenuEntity updatedMenu = rest.exchange(url("/api/v1/wx/menus"),
                HttpMethod.PUT, json(managerToken, Map.of(
                        "id", createdMenu.getId(),
                        "accountId", createdMenu.getAccountId(),
                        "name", createdMenu.getName(),
                        "menuType", createdMenu.getMenuType(),
                        "url", createdMenu.getUrl(),
                        "enabled", 0)),
                WxMenuEntity.class).getBody();
        assertEquals("挂号", updatedMenu.getName(), "MANAGER 应可更新菜单");

        HttpClientErrorException pushError = assertThrows(HttpClientErrorException.class,
                () -> rest.exchange(url("/api/v1/wx/menus/push/" + accountId),
                        HttpMethod.POST, bearer(managerToken), Map.class));
        assertEquals(HttpStatus.BAD_REQUEST, pushError.getStatusCode(), "无凭据时推送应达授权层并返回业务 400");

        rest.exchange(url("/api/v1/wx/menus/" + createdMenu.getId()),
                HttpMethod.DELETE, bearer(managerToken), Map.class);
        assertTrue(menuRepository.findById(createdMenu.getId()).isEmpty(), "MANAGER 应可删除菜单");

        HttpClientErrorException unbindError = assertThrows(HttpClientErrorException.class,
                () -> rest.exchange(url("/api/v1/wx/fans/" + accountId + "/unbind"),
                        HttpMethod.POST,
                        json(managerToken, Map.of("openid", "oMANAGER-UNBIND")),
                        Map.class));
        assertEquals(HttpStatus.BAD_REQUEST, unbindError.getStatusCode(), "解绑应达授权层并按无粉丝返回业务 400");
    }

    @Test
    void managerIsForbiddenOnAccountConfigWrite() {
        HttpClientErrorException e = assertThrows(HttpClientErrorException.class,
                () -> rest.exchange(url("/api/v1/wx/accounts"), HttpMethod.POST,
                        json(managerToken, Map.of("appId", "wxROLE-MGR" + System.nanoTime(), "accountType", "service")),
                        Map.class));
        assertEquals(HttpStatus.FORBIDDEN, e.getStatusCode(), "MANAGER 写账号配置应 403（ADMIN 专用）");
    }

    @Test
    void unauthenticatedIsRejected() {
        for (String path : new String[]{
                "/api/v1/wx/message-logs?current=1&pageSize=10",
                "/api/v1/wx/capabilities/" + accountId,
                "/api/v1/wx/accounts",
                "/api/v1/wx/auto-replies/page?current=1&pageSize=10",
                "/api/v1/wx/menus?accountId=" + accountId}) {
            HttpClientErrorException e = assertThrows(HttpClientErrorException.class,
                    () -> rest.exchange(url(path), HttpMethod.GET, bearer(null), Map.class),
                    "未登录应 401：" + path);
            assertEquals(HttpStatus.UNAUTHORIZED, e.getStatusCode(), "应 401：" + path);
        }
    }
}
