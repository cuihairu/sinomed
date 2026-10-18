package com.sinomed;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 卡项类目 HTTP 回归（美容业态 a：项目类型扩展，见 docs/design/salon-extension.md）：
 * 创建带 category（0 医馆服务 / 1 皮肤管理 / 2 身体护理 / 3 中医美容）读回一致；
 * 不带时按 0 医馆服务缺省（存量卡项不动）；更新可改类目；分页可按类目过滤。走真实 HTTP。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.datasource.url=jdbc:sqlite:target/item-category-http-test-${random.uuid}.db"})
class ItemCategoryHttpTest {

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

    private Map<?, ?> createItem(String token, String name, Object category) {
        Map<String, Object> body = new java.util.HashMap<>(Map.of(
                "name", name,
                "price", 100,
                "description", "美容业态类目回归"));
        if (category != null) {
            body.put("category", category);
        }
        return rest.exchange(url("/api/v1/item/"), HttpMethod.POST,
                authEntity(body, token), Map.class).getBody();
    }

    @Test
    void categorySavedAndReadBack() {
        String token = token();
        Map<?, ?> created = createItem(token, "皮肤管理-" + System.nanoTime(), 1);
        assertEquals(1, ((Number) created.get("category")).intValue(), "类目应随卡项保存：" + created);

        long id = ((Number) created.get("id")).longValue();
        Map<?, ?> fetched = rest.exchange(url("/api/v1/item/" + id), HttpMethod.GET,
                authEntity(null, token), Map.class).getBody();
        assertEquals(1, ((Number) fetched.get("category")).intValue(), "类目应可回读：" + fetched);
    }

    @Test
    void createWithoutCategoryDefaultsToClinic() {
        String token = token();
        Map<?, ?> created = createItem(token, "腹部推拿-" + System.nanoTime(), null);
        assertEquals(0, ((Number) created.get("category")).intValue(),
                "不带类目应缺省 0 医馆服务（存量口径不变）：" + created);
    }

    @Test
    void updateChangesCategory() {
        String token = token();
        Map<?, ?> created = createItem(token, "面部刮痧-" + System.nanoTime(), 1);
        long id = ((Number) created.get("id")).longValue();

        Map<String, Object> patch = new java.util.HashMap<>();
        patch.put("id", id);
        patch.put("name", created.get("name"));
        patch.put("price", 100);
        patch.put("description", "美容业态类目回归");
        patch.put("category", 3);
        Map<?, ?> updated = rest.exchange(url("/api/v1/item/"), HttpMethod.PUT,
                authEntity(patch, token), Map.class).getBody();
        assertEquals(3, ((Number) updated.get("category")).intValue(), "更新应可改类目：" + updated);
    }

    @Test
    void pageFiltersByCategory() {
        String token = token();
        long cat1 = ((Number) createItem(token, "身体护理-" + System.nanoTime(), 2).get("id")).longValue();
        long cat3 = ((Number) createItem(token, "中医美容-" + System.nanoTime(), 3).get("id")).longValue();

        Map<?, ?> filtered = rest.exchange(
                url("/api/v1/item/page?current=1&pageSize=50&category=2"), HttpMethod.GET,
                authEntity(null, token), Map.class).getBody();
        java.util.List<?> rows = (java.util.List<?>) filtered.get("data");
        assertTrue(rows.stream().anyMatch(r -> cat1 == ((Number) ((Map<?, ?>) r).get("id")).longValue()),
                "过滤 category=2 应含身体护理卡：" + rows);
        assertTrue(rows.stream().noneMatch(r -> cat3 == ((Number) ((Map<?, ?>) r).get("id")).longValue()),
                "过滤 category=2 不应含中医美容卡：" + rows);

        Map<?, ?> unfiltered = rest.exchange(
                url("/api/v1/item/page?current=1&pageSize=50"), HttpMethod.GET,
                authEntity(null, token), Map.class).getBody();
        java.util.List<?> all = (java.util.List<?>) unfiltered.get("data");
        assertTrue(all.stream().anyMatch(r -> cat3 == ((Number) ((Map<?, ?>) r).get("id")).longValue()),
                "不过滤应能查到中医美容卡：" + all);
    }
}
