package com.sinomed;

import com.sinomed.entity.StaffEntity;
import com.sinomed.repository.StaffRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 方法级权限（web.md 权限口径服务端强制）回归：
 * 员工管理写接口（POST/PUT/DELETE /api/v1/staff）限管理员（role 0），
 * 普通员工登录后写员工报 403、读员工列表不受限；库路径随机文件避免污染。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:sqlite:target/staff-permission-test-${random.uuid}.db"
})
class StaffPermissionHttpTest {

    @LocalServerPort
    private int port;

    private final RestTemplate restTemplate = new RestTemplate();

    @BeforeEach
    void passThroughHttpErrors() {
        // 默认实现遇 4xx 直接抛 HttpClientErrorException；这里原样返回响应，便于断言 403
        restTemplate.setErrorHandler(response -> false);
    }

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String adminToken;
    private String clerkToken;

    @BeforeEach
    void seedAndLogin() {
        if (staffRepository.count() >= 2) {
            adminToken = login("admin", "123");
            clerkToken = login("perm-clerk", "123");
            return;
        }
        // 普通员工（role 99，默认值同 StaffEntity.setDefaultValue）
        StaffEntity clerk = new StaffEntity();
        clerk.setName("权限店员");
        clerk.setAccount("perm-clerk");
        clerk.setRole(99);
        clerk.setStatus(1);
        clerk.setPassword(passwordEncoder.encode("123"));
        staffRepository.save(clerk);
        adminToken = login("admin", "123");
        clerkToken = login("perm-clerk", "123");
    }

    private String login(String username, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        var resp = restTemplate.postForEntity("http://localhost:" + port + "/api/v1/user/login",
                new HttpEntity<>(Map.of("username", username, "password", password), headers), Map.class);
        assertEquals(200, resp.getStatusCode().value(), "login failed: " + resp.getBody());
        return (String) resp.getBody().get("token");
    }

    private HttpHeaders auth(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return headers;
    }

    @Test
    void adminCreatesAndDeletesStaff() {
        var resp = restTemplate.postForEntity("http://localhost:" + port + "/api/v1/staff/",
                new HttpEntity<>(Map.of("account", "perm-new", "name", "新员工", "password", "123456"),
                        auth(adminToken)), Map.class);
        assertEquals(200, resp.getStatusCode().value(), "admin create staff failed: " + resp.getBody());
        Number id = (Number) resp.getBody().get("id");
        assertTrue(id != null && id.longValue() > 0);

        var del = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/staff/" + id,
                HttpMethod.DELETE, new HttpEntity<>(auth(adminToken)), Map.class);
        assertEquals(200, del.getStatusCode().value(), "admin delete staff failed: " + del.getBody());
    }

    @Test
    void clerkCannotCreateStaffButCanReadPage() {
        var resp = restTemplate.postForEntity("http://localhost:" + port + "/api/v1/staff/",
                new HttpEntity<>(Map.of("account", "perm-hack", "name", "越权员工", "password", "123456"),
                        auth(clerkToken)), String.class);
        assertEquals(403, resp.getStatusCode().value(), "clerk create staff should be forbidden: " + resp.getBody());

        var page = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/staff/page?current=1&pageSize=10",
                HttpMethod.GET, new HttpEntity<>(auth(clerkToken)), String.class);
        assertEquals(200, page.getStatusCode().value(), "clerk read staff page should pass: " + page.getBody());
    }
}
