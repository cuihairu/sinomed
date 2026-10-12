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

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 剂量审方 HTTP 回归：药材字典 POST /api/v1/herb 带 dose_max 建立上限 → POST /dose-check
 * 超上限命中、等值不命中、未设上限不预警、未收录进 unmatched；空白名单 400、未登录 401。
 * 不开 @Transactional：RANDOM_PORT 下服务端事务看不到测试事务里的未提交种子数据。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.datasource.url=jdbc:sqlite:target/dose-http-test-${random.uuid}.db"})
class PrescriptionDoseHttpTest {

    @LocalServerPort
    private int port;

    private final RestTemplate rest = new RestTemplate();

    private String token() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        var resp = rest.postForEntity(url("/api/v1/user/login"),
                new HttpEntity<>(Map.of("username", "admin", "password", "123"), headers), Map.class);
        return (String) resp.getBody().get("token");
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private HttpEntity<?> authEntity(Object body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return new HttpEntity<>(body, headers);
    }

    private String exchange4xx(String path, Object body, String token) {
        try {
            return rest.exchange(url(path), HttpMethod.POST, authEntity(body, token), String.class).getBody();
        } catch (HttpStatusCodeException e) {
            return e.getResponseBodyAsString();
        }
    }

    /** 药材字典建一味药，doseMax 可空（不传=不预警） */
    private void newHerb(String token, String name, Object price, Object doseMax) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("name", name);
        body.put("price", price);
        if (doseMax != null) {
            body.put("doseMax", doseMax);
        }
        Map<?, ?> resp = rest.exchange(url("/api/v1/herb/"), HttpMethod.POST,
                authEntity(body, token), Map.class).getBody();
        assertNotNull(resp != null ? resp.get("id") : null, "建药应返回 id：" + resp);
    }

    @Test
    @SuppressWarnings("unchecked")
    void doseCheckFlagsWeightAboveLimit() {
        String token = token();
        newHerb(token, "剂量HTTP附子", 100, 10);
        newHerb(token, "剂量HTTP干姜", 80, 6);
        newHerb(token, "剂量HTTP熟地", 120, null);

        Map<?, ?> result = rest.exchange(url("/api/v1/prescription/dose-check"), HttpMethod.POST,
                authEntity(Map.of("herbs", List.of(
                        Map.of("herb", "剂量HTTP附子", "weight", 15),
                        Map.of("herb", "剂量HTTP附子", "weight", 10),
                        Map.of("herb", "剂量HTTP干姜", "weight", 6),
                        Map.of("herb", "剂量HTTP熟地", "weight", 99),
                        Map.of("herb", "剂量HTTP未收录", "weight", 50))), token), Map.class).getBody();
        assertEquals(5, ((Number) result.get("checked")).intValue(), "比对数应为 5：" + result);
        assertEquals(3, ((Number) result.get("limited")).intValue(), "三条带上限药味（附子重复计两次）：" + result);

        List<?> findings = (List<?>) result.get("findings");
        assertEquals(1, findings.size(), "只超上限那味命中：" + findings);
        Map<?, ?> hit = (Map<?, ?>) findings.get(0);
        assertEquals("剂量HTTP附子", hit.get("herb"));
        assertEquals(15.0, ((Number) hit.get("weight")).doubleValue());
        assertEquals(10.0, ((Number) hit.get("limit")).doubleValue());

        List<?> unmatched = (List<?>) result.get("unmatched");
        assertEquals(1, unmatched.size(), "未收录药名应进 unmatched：" + unmatched);
        assertEquals("剂量HTTP未收录", unmatched.get(0));
    }

    @Test
    void doseCheckGuards() {
        String token = token();
        newHerb(token, "剂量HTTP守卫药", 100, 10);

        String blank = exchange4xx("/api/v1/prescription/dose-check",
                Map.of("herbs", List.of(Map.of("herb", "  ", "weight", 10))), token);
        assertTrue(blank != null && blank.contains("药材名单不能为空"), "空白名单应 400：" + blank);

        // 未登录 401（RestTemplate 对 4xx 抛异常）
        boolean threw = false;
        try {
            rest.exchange(url("/api/v1/prescription/dose-check"), HttpMethod.POST, HttpEntity.EMPTY, Map.class);
        } catch (Exception e) {
            threw = true;
        }
        assertTrue(threw, "未登录应 401");
    }
}
