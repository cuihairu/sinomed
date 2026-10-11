package com.sinomed;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.springframework.web.util.UriComponentsBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 外治处方模板 HTTP 回归：建模板 → 上架列表 → 分页过滤 → 更新 → 删除，以及守卫与未登录 401。
 * 不开 @Transactional：RANDOM_PORT 下服务端事务看不到测试事务里的未提交数据。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.datasource.url=jdbc:sqlite:target/therapy-template-http-test-${random.uuid}.db"})
class TherapyTemplateHttpTest {

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

    @SuppressWarnings("unchecked")
    private Map<String, Object> getMap(String path, String token) {
        return rest.exchange(url(path), HttpMethod.GET, authEntity(null, token), Map.class).getBody();
    }

    /** 中文参数：统一编码后以 URI 直传，避开 RestTemplate 对模板串的双重编码 */
    private Map<String, Object> getMap(URI uri, String token) {
        return rest.exchange(uri, HttpMethod.GET, authEntity(null, token), Map.class).getBody();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getList(String path, String token) {
        return rest.exchange(url(path), HttpMethod.GET, authEntity(null, token), List.class).getBody();
    }

    private Map<String, Object> post(Object body, String token) {
        return rest.postForEntity(url("/api/v1/therapy-template/"), authEntity(body, token), Map.class).getBody();
    }

    @Test
    void createListPageUpdateAndDelete() {
        String token = token();
        Map<String, Object> created = post(Map.of("name", "失眠·耳穴压豆", "method", "耳穴压豆",
                "target", "神门、交感、皮质下", "course", "每周 2 次 × 4 周", "note", "每日揉按"), token);
        long id = ((Number) created.get("id")).longValue();
        assertEquals("失眠·耳穴压豆", created.get("name"));
        assertEquals("耳穴压豆", created.get("method"));
        assertEquals(1, ((Number) created.get("enabled")).intValue());

        post(Map.of("name", "咳喘·穴位贴敷", "method", "穴位贴敷"), token);
        post(Map.of("name", "肾阳虚·艾灸", "method", "艾灸", "enabled", 0), token);

        // 上架列表只回启用项
        List<Map<String, Object>> enabled = getList("/api/v1/therapy-template/enabled", token);
        assertEquals(2, enabled.size(), "停用模板不进接诊页列表");
        assertTrue(enabled.stream().allMatch(r -> Integer.valueOf(1).equals(((Number) r.get("enabled")).intValue())));

        // 分页：全量、名称模糊、方法精确（中文参数经 UriComponentsBuilder 统一编码后以 URI 直传）
        assertEquals(3, ((Number) getMap("/api/v1/therapy-template/page?current=1&pageSize=10", token).get("total")).intValue());
        URI filtered = UriComponentsBuilder.fromUriString(url("/api/v1/therapy-template/page"))
                .queryParam("current", 1).queryParam("pageSize", 10)
                .queryParam("name", "失眠").queryParam("method", "耳穴压豆")
                .build().encode().toUri();
        Map<String, Object> page = getMap(filtered, token);
        assertEquals(1, ((Number) page.get("total")).intValue());
        Map<String, Object> hit = (Map<String, Object>) ((List<?>) page.get("data")).get(0);
        assertEquals(id, ((Number) hit.get("id")).longValue());
        URI missing = UriComponentsBuilder.fromUriString(url("/api/v1/therapy-template/page"))
                .queryParam("current", 1).queryParam("pageSize", 10)
                .queryParam("method", "热烫")
                .build().encode().toUri();
        assertEquals(0, ((Number) getMap(missing, token).get("total")).intValue());

        // 详情
        Map<String, Object> detail = getMap("/api/v1/therapy-template/" + id, token);
        assertEquals("神门、交感、皮质下", detail.get("target"));

        // 更新
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        Map<String, Object> updated = rest.exchange(url("/api/v1/therapy-template/"), HttpMethod.PUT,
                authEntity(Map.of("id", id, "name", "失眠·压豆改方", "method", "耳穴压豆",
                        "target", "神门、心", "enabled", 1), token), Map.class).getBody();
        assertEquals("失眠·压豆改方", updated.get("name"));
        assertEquals("神门、心", updated.get("target"));
        List<Map<String, Object>> reenabled = getList("/api/v1/therapy-template/enabled", token);
        assertEquals(2, reenabled.size(), "改名后仍在上架列表");
        assertTrue(reenabled.stream().anyMatch(r -> "失眠·压豆改方".equals(r.get("name"))), "改名后的模板在上架列表");

        // 删除后分页与上架列表都清掉这条
        Map<String, Object> deleted = rest.exchange(url("/api/v1/therapy-template/" + id), HttpMethod.DELETE,
                authEntity(null, token), Map.class).getBody();
        assertEquals("已删除", deleted.get("message"));
        assertEquals(2, ((Number) getMap("/api/v1/therapy-template/page?current=1&pageSize=10", token).get("total")).intValue());
        assertEquals(1, getList("/api/v1/therapy-template/enabled", token).size());
    }

    @Test
    void guardsAndAuth() throws Throwable {
        String token = token();
        // 模板名与本类首个用例错开：同一测试类共用一个 SQLite 库，避免跨用例撞名
        post(Map.of("name", "痛经·温灸", "method", "艾灸"), token);

        assert400(() -> post(Map.of("name", "痛经·温灸", "method", "艾灸"), token), "外治模板名已存在");
        assert400(() -> post(Map.of("name", " ", "method", "艾灸"), token), "外治模板名不能为空");
        assert400(() -> post(Map.of("name", "一".repeat(21), "method", "艾灸"), token), "外治模板名限 20 字内");
        assert400(() -> post(Map.of("name", "腰痛·留罐", "method", " "), token), "外治方法必填");
        assert400(() -> post(Map.of("name", "腰痛·留罐", "method", "一二三四五六七八九十十一"),
                token), "外治方法限 10 字内");
        assert400(() -> post(Map.of("name", "腰痛·留罐", "method", "拔罐", "target", "一".repeat(101)),
                token), "取穴或部位限 100 字内");
        assert400(() -> rest.exchange(url("/api/v1/therapy-template/"), HttpMethod.PUT,
                authEntity(Map.of("id", 9999, "name", "不存在", "method", "艾灸"), token), Map.class), "模板不存在");
        assert400(() -> rest.exchange(url("/api/v1/therapy-template/9999"), HttpMethod.DELETE,
                authEntity(null, token), Map.class), "模板不存在");

        // 未登录
        try {
            rest.getForEntity(url("/api/v1/therapy-template/enabled"), String.class);
            throw new AssertionError("未登录应报 401");
        } catch (HttpStatusCodeException e) {
            assertEquals(401, e.getStatusCode().value());
        }
    }

    private void assert400(Executable action, String message) throws Throwable {
        try {
            action.execute();
            throw new AssertionError("期望报 400：" + message);
        } catch (HttpStatusCodeException e) {
            assertEquals(400, e.getStatusCode().value(), "状态码");
            assertTrue(e.getResponseBodyAsString().contains(message),
                    "期望包含「" + message + "」，实际：[" + e.getResponseBodyAsString() + "]");
        }
    }
}
