package com.sinomed;

import com.sinomed.entity.StaffEntity;
import com.sinomed.entity.WxAccountEntity;
import com.sinomed.repository.StaffRepository;
import com.sinomed.repository.WxAccountRepository;
import jakarta.persistence.EntityManager;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 公众号账号配置写入口回归（设计稿 §4.1「配置入口」/§6.3「账号配置 ADMIN」/§7 凭据加密落库）：
 * 创建/更新账号基础字段与凭据（appSecret/token/aesKey 加密落库、回显掩码、更新留空保持），
 * 仅 ADMIN 可写、未登录 401、非 ADMIN 403；凭据经 JPA converter 穿透直查必须非明文。
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
        "sinomed.official-account.enabled=true"
})
class WxAccountAdminHttpTest {

    private static final String DB_URL =
            "jdbc:sqlite:target/wx-account-admin-test-" + UUID.randomUUID() + ".db";

    @DynamicPropertySource
    static void pin(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DB_URL);
        registry.add("sinomed.official-account.credential-key", () -> "test-account-admin-key");
    }

    @LocalServerPort
    private int port;

    private final RestTemplate rest = new RestTemplate();

    private String adminToken;
    private String userToken;

    @Autowired
    private WxAccountRepository accountRepository;
    @Autowired
    private StaffRepository staffRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private EntityManager entityManager;

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

    private HttpEntity<Map<String, Object>> json(Map<String, Object> body, String jwt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (jwt != null) {
            headers.setBearerAuth(jwt);
        }
        return new HttpEntity<>(body, headers);
    }

    @BeforeEach
    void seed() {
        accountRepository.deleteAll();
        staffRepository.findAll().forEach(s -> {
            if ("wx-acct-admin".equals(s.getAccount()) || "wx-acct-clerk".equals(s.getAccount())) {
                staffRepository.delete(s);
            }
        });
        StaffEntity admin = new StaffEntity();
        admin.setName("公众号管理员");
        admin.setAccount("wx-acct-admin");
        admin.setRole(0);
        admin.setStatus(1);
        admin.setPassword(passwordEncoder.encode("123"));
        staffRepository.save(admin);
        adminToken = login("wx-acct-admin", "123");

        StaffEntity clerk = new StaffEntity();
        clerk.setName("公众号店员");
        clerk.setAccount("wx-acct-clerk");
        clerk.setRole(99);
        clerk.setStatus(1);
        clerk.setPassword(passwordEncoder.encode("123"));
        staffRepository.save(clerk);
        userToken = login("wx-acct-clerk", "123");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> createAccount(Map<String, Object> body, String jwt) {
        return rest.exchange(url("/api/v1/wx/accounts"), HttpMethod.POST,
                json(body, jwt), Map.class).getBody();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> updateAccount(long id, Map<String, Object> body, String jwt) {
        return rest.exchange(url("/api/v1/wx/accounts/" + id), HttpMethod.PUT,
                json(body, jwt), Map.class).getBody();
    }

    private String rawColumn(long id, String column) {
        return (String) entityManager.createNativeQuery(
                        "SELECT " + column + " FROM wx_accounts WHERE id = ?1")
                .setParameter(1, id).getSingleResult();
    }

    @Test
    void createsAccountWithEncryptedCredentialsAndMasksSecrets() {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("appId", "wxACCT" + System.nanoTime());
        body.put("accountName", "测试服务号");
        body.put("accountType", "service");
        body.put("verified", 1);
        body.put("appSecret", "SECRET-ABC-123");
        body.put("token", "TOKEN-XYZ");
        body.put("aesKey", "AESKEY-43-CHAR-ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789");
        body.put("encryptMode", "safe");
        body.put("enabled", 1);

        Map<String, Object> created = createAccount(body, adminToken);
        assertNotNull(created.get("id"), "创建应返回 id");
        long id = ((Number) created.get("id")).longValue();
        assertEquals("测试服务号", created.get("accountName"));
        assertEquals("service", created.get("accountType"));
        assertEquals(1, created.get("verified"));
        assertEquals("****", created.get("appSecret"), "创建响应回显掩码而非明文（§6.3 never echoes back）");
        assertEquals("****", created.get("token"), "创建响应回显掩码而非明文");
        assertEquals("****", created.get("aesKey"), "创建响应回显掩码而非明文");

        String rawSecret = rawColumn(id, "app_secret");
        assertTrue(rawSecret.startsWith("enc:v1:"), "appSecret 落库应加密：" + rawSecret);
        assertFalse(rawSecret.contains("SECRET-ABC-123"), "落库不得含明文 appSecret");
        String rawAes = rawColumn(id, "aes_key");
        assertTrue(rawAes.startsWith("enc:v1:"), "aesKey 落库应加密：" + rawAes);
        assertFalse(rawAes.contains("AESKEY-43"), "落库不得含明文 aesKey");

        WxAccountEntity back = accountRepository.findById(id).orElseThrow();
        assertEquals("SECRET-ABC-123", back.getAppSecret(), "读回应还原明文 appSecret");
        assertEquals("TOKEN-XYZ", back.getToken(), "token 读回应还原（列值可读）");
        assertEquals("AESKEY-43-CHAR-ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789", back.getAesKey());
    }

    @Test
    void updateKeepsSecretsWhenBlankAndRotatesWhenProvided() {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("appId", "wxUPD" + System.nanoTime());
        body.put("accountName", "服务号");
        body.put("accountType", "service");
        body.put("verified", 1);
        body.put("appSecret", "OLD-SECRET");
        body.put("token", "OLD-TOKEN");
        long id = ((Number) createAccount(body, adminToken).get("id")).longValue();

        // 留空=保持既有（设计稿 §6.3「empty on update = keep」）
        Map<String, Object> keep = new java.util.HashMap<>();
        keep.put("appId", "wxUPD" + System.nanoTime());
        keep.put("accountName", "改名服务号");
        keep.put("accountType", "subscription");
        keep.put("verified", 0);
        updateAccount(id, keep, adminToken);
        WxAccountEntity after = accountRepository.findById(id).orElseThrow();
        assertEquals("改名服务号", after.getAccountName());
        assertEquals("subscription", after.getAccountType());
        assertEquals("OLD-SECRET", after.getAppSecret(), "appSecret 留空应保持既有");
        assertEquals("OLD-TOKEN", after.getToken(), "token 留空应保持既有");

        // 提供新值=轮换（密文不同）
        Map<String, Object> rotate = new java.util.HashMap<>();
        rotate.put("appId", "wxUPD" + System.nanoTime());
        rotate.put("accountName", "改名服务号");
        rotate.put("accountType", "subscription");
        rotate.put("verified", 0);
        rotate.put("appSecret", "NEW-SECRET");
        updateAccount(id, rotate, adminToken);
        assertEquals("NEW-SECRET", accountRepository.findById(id).orElseThrow().getAppSecret(),
                "提供新 appSecret 应轮换");
        assertEquals("OLD-TOKEN", accountRepository.findById(id).orElseThrow().getToken(),
                "未提供的 token 保持既有");
    }

    @Test
    void rejectsInvalidFields() {
        Map<String, Object> badType = new java.util.HashMap<>();
        badType.put("appId", "wxBAD" + System.nanoTime());
        badType.put("accountType", "bogus");
        HttpClientErrorException e = assertThrows(HttpClientErrorException.class,
                () -> createAccount(badType, adminToken));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode(), "非法 accountType 应 400");

        Map<String, Object> missingAppId = new java.util.HashMap<>();
        missingAppId.put("accountType", "service");
        HttpClientErrorException e2 = assertThrows(HttpClientErrorException.class,
                () -> createAccount(missingAppId, adminToken));
        assertEquals(HttpStatus.BAD_REQUEST, e2.getStatusCode(), "缺 appId 应 400");

        Map<String, Object> badEncrypt = new java.util.HashMap<>();
        badEncrypt.put("appId", "wxBAD" + System.nanoTime());
        badEncrypt.put("accountType", "service");
        badEncrypt.put("encryptMode", "wrong");
        HttpClientErrorException e3 = assertThrows(HttpClientErrorException.class,
                () -> createAccount(badEncrypt, adminToken));
        assertEquals(HttpStatus.BAD_REQUEST, e3.getStatusCode(), "非法 encryptMode 应 400");
    }

    @Test
    void requiresAdminAndAuth() {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("appId", "wxPERM" + System.nanoTime());
        body.put("accountType", "service");
        // 未登录 401
        HttpClientErrorException unauth = assertThrows(HttpClientErrorException.class,
                () -> createAccount(body, null));
        assertEquals(HttpStatus.UNAUTHORIZED, unauth.getStatusCode(), "未登录应 401");
        // 普通 USER 403（设计稿 §6.3 账号配置 ADMIN 专用）
        HttpClientErrorException forbidden = assertThrows(HttpClientErrorException.class,
                () -> createAccount(body, userToken));
        assertEquals(HttpStatus.FORBIDDEN, forbidden.getStatusCode(), "非 ADMIN 应 403");
    }

    @Test
    void updateNonexistentIdReturns400() {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("appId", "wxNOID" + System.nanoTime());
        body.put("accountType", "service");
        HttpClientErrorException e = assertThrows(HttpClientErrorException.class,
                () -> updateAccount(999999L, body, adminToken));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode(), "更新不存在 id 应 400（不静默覆盖、不 500）");
    }

    @Test
    void createDuplicateAppIdReturns400() {
        Map<String, Object> first = new java.util.HashMap<>();
        first.put("appId", "wxDUP" + System.nanoTime());
        first.put("accountType", "service");
        createAccount(first, adminToken);

        Map<String, Object> dup = new java.util.HashMap<>();
        dup.put("appId", first.get("appId"));
        dup.put("accountType", "service");
        dup.put("accountName", "重复 AppID 号");
        HttpClientErrorException e = assertThrows(HttpClientErrorException.class,
                () -> createAccount(dup, adminToken));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode(), "appId 唯一冲突应 400（不静默覆盖、不 500）");
        assertEquals(1, accountRepository.findAll().stream()
                .filter(a -> first.get("appId").equals(a.getAppId())).count(), "冲突不得新增行");
    }
}
