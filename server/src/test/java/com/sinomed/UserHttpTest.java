package com.sinomed;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 认证 HTTP 回归：登录成功回执（status/type/currentAuthority）→ /current 回显当前用户；
 * 注册新员工（需登录）→ 新账户登录为 user 权限 → 重复注册 400；
 * 登录守卫 400（用户不存在/密码错/缺用户名/密码过短）、未登录 401。走真实 HTTP 覆盖参数绑定层。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.datasource.url=jdbc:sqlite:target/user-http-test-${random.uuid}.db"})
class UserHttpTest {

    @LocalServerPort
    private int port;

    private final RestTemplate rest = new RestTemplate();

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    /** 缺字段用例可传 null（Map.of 不收 null 值） */
    private Map<String, Object> authBody(String username, String password) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("username", username);
        body.put("password", password);
        return body;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> login(String username, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.postForEntity(url("/api/v1/user/login"),
                new HttpEntity<>(authBody(username, password), headers), Map.class).getBody();
    }

    private String login4xx(String username, String password) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            return rest.postForEntity(url("/api/v1/user/login"),
                    new HttpEntity<>(authBody(username, password), headers), String.class).getBody();
        } catch (HttpStatusCodeException e) {
            return e.getResponseBodyAsString();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> register(String token, String username, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(url("/api/v1/user/register"), HttpMethod.POST,
                new HttpEntity<>(Map.of("username", username, "password", password), headers), Map.class).getBody();
    }

    private String register4xx(String token, Object body) {
        try {
            HttpHeaders headers = new HttpHeaders();
            if (token != null) {
                headers.setBearerAuth(token);
            }
            headers.setContentType(MediaType.APPLICATION_JSON);
            return rest.exchange(url("/api/v1/user/register"), HttpMethod.POST,
                    new HttpEntity<>(body, headers), String.class).getBody();
        } catch (HttpStatusCodeException e) {
            return e.getResponseBodyAsString();
        }
    }

    @Test
    void loginSuccessThenCurrentUser() {
        Map<String, Object> body = login("admin", "123");
        assertEquals("ok", body.get("status"), "登录成功应回 ok");
        assertEquals("account", body.get("type"));
        assertNotNull(body.get("token"), "登录应发 token");
        assertEquals("admin", body.get("currentAuthority"), "admin 账户应为 admin 权限");

        // 带有效 token 查当前用户：回显登录名
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth((String) body.get("token"));
        Map<String, Object> current = rest.exchange(url("/api/v1/user/current"), HttpMethod.GET,
                new HttpEntity<>(headers), Map.class).getBody();
        assertEquals("admin", current.get("userid"), "/current 应回显登录名");
    }

    @Test
    void registerThenLoginThenDuplicateGuard() {
        String token = (String) login("admin", "123").get("token");

        // 注册不在放行段，未登录应 401（entry point 裸写文本无 Content-Type，按状态码断言不读 body）
        boolean anonThrew = false;
        try {
            register(null, "tester01", "pass123");
        } catch (HttpStatusCodeException e) {
            anonThrew = e.getStatusCode().value() == 401;
        }
        assertTrue(anonThrew, "未登录注册应 401");

        Map<String, Object> registered = register(token, "tester01", "pass123");
        assertNotNull(registered.get("token"), "注册应直接发 token");

        // 新账户登录：默认角色非 0 → user 权限
        Map<String, Object> body = login("tester01", "pass123");
        assertEquals("ok", body.get("status"));
        assertEquals("user", body.get("currentAuthority"), "新注册账户应为 user 权限");

        // 重复注册 400
        String dup = register4xx(token, Map.of("username", "tester01", "password", "pass123"));
        assertTrue(dup != null && dup.contains("用户名已经存在，请选择其他用户名"), "重复注册应 400：" + dup);
    }

    @Test
    void loginGuards() {
        // 用户不存在（业务校验在先）→ 400
        assertTrue(login4xx("nobody99", "pass123").contains("该用户不存在"), "用户不存在应 400");
        // 密码错 → 400（BadCredentials 归入参数错误映射）
        String badPw = login4xx("admin", "wrongpw");
        assertTrue(badPw != null && badPw.contains("success") && badPw.contains("false"),
                "密码错应 400：" + badPw);
        // 必填与长度校验 400
        assertTrue(login4xx(null, "pass123").contains("用户名不能为空"), "缺用户名应 400");
        assertTrue(login4xx(null, null).contains("success"), "全空应 400");
        assertTrue(login4xx("admin", "1").contains("密码长度2-20"), "密码过短应 400");
    }
}
