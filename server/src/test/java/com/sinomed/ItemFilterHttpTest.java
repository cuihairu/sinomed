package com.sinomed;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 卡项分页过滤 HTTP 回归（巡检 2026-10-19 收口 ItemController 静默忽略的 name/price 查询参数）：
 * name 按包含（不区分大小写）、price 精确、category 精确，可与类目叠加；不传即不过滤。
 * 与 ItemCategoryHttpTest 的 category 过滤同口径（排序 sort ASC、id DESC 不变）。走真实 HTTP。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.datasource.url=jdbc:sqlite:target/item-filter-http-test-${random.uuid}.db"})
class ItemFilterHttpTest {

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

    private long createItem(String token, String name, int price, int category) {
        Map<?, ?> created = rest.exchange(url("/api/v1/item/"), HttpMethod.POST,
                authEntity(Map.of("name", name, "price", price, "description", "过滤回归", "category", category),
                        token), Map.class).getBody();
        return ((Number) created.get("id")).longValue();
    }

    /** UriComponentsBuilder 只编码一次（手拼 %E8… 会被 RestTemplate 再编码，服务端收到乱码致 LIKE 不命中） */
    private java.net.URI pageUri(String name, Integer price, Integer category) {
        var builder = org.springframework.web.util.UriComponentsBuilder
                .fromUriString(url("/api/v1/item/page"))
                .queryParam("current", 1)
                .queryParam("pageSize", 50);
        if (name != null) {
            builder.queryParam("name", name);
        }
        if (price != null) {
            builder.queryParam("price", price);
        }
        if (category != null) {
            builder.queryParam("category", category);
        }
        return builder.build().encode().toUri();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> query(String token, String name, Integer price, Integer category) {
        Map<?, ?> page = rest.exchange(pageUri(name, price, category),
                HttpMethod.GET, authEntity(null, token), Map.class).getBody();
        return (List<Map<String, Object>>) (List<?>) page.get("data");
    }

    private boolean hasId(List<Map<String, Object>> rows, long id) {
        return rows.stream().anyMatch(r -> id == ((Number) r.get("id")).longValue());
    }

    @Test
    void pageFiltersByNameContains() {
        String token = token();
        long mask = createItem(token, "补水面膜-" + System.nanoTime(), 100, 1);
        long guasha = createItem(token, "刮痧拨筋-" + System.nanoTime(), 100, 1);

        List<Map<String, Object>> rows = query(token, "补水", null, null);
        assertTrue(hasId(rows, mask), "name 包含过滤应命中补水面膜：" + rows);
        assertTrue(!hasId(rows, guasha), "name 包含过滤不应命中刮痧卡：" + rows);
    }

    @Test
    void pageFiltersNameIgnoreCase() {
        String token = token();
        long hifu = createItem(token, "HIFU面部-" + System.nanoTime(), 100, 1);

        List<Map<String, Object>> rows = query(token, "hifu", null, null);
        assertTrue(hasId(rows, hifu), "name 过滤应不区分大小写命中 HIFU 卡：" + rows);
    }

    @Test
    void pageFiltersByPriceExact() {
        String token = token();
        long p188 = createItem(token, "价格过滤-" + System.nanoTime(), 188, 0);
        long p777 = createItem(token, "价格过滤-" + System.nanoTime(), 777, 0);

        List<Map<String, Object>> rows = query(token, null, 188, null);
        assertTrue(hasId(rows, p188), "price 精确过滤应命中 188 卡：" + rows);
        assertTrue(!hasId(rows, p777), "price 精确过滤不应命中 777 卡：" + rows);
    }

    @Test
    void pageFiltersNameAndCategoryCombined() {
        String token = token();
        long bodyMask = createItem(token, "补水身体膜-" + System.nanoTime(), 100, 2);
        long essence = createItem(token, "补水精华-" + System.nanoTime(), 100, 3);

        List<Map<String, Object>> rows = query(token, "补水", null, 2);
        assertTrue(hasId(rows, bodyMask), "name+category 叠加应命中身体膜：" + rows);
        assertTrue(!hasId(rows, essence), "name+category 叠加不应命中他类精华：" + rows);
    }

    @Test
    void pageWithoutFiltersReturnsAll() {
        String token = token();
        long a = createItem(token, "不过滤甲-" + System.nanoTime(), 100, 0);
        long b = createItem(token, "不过滤乙-" + System.nanoTime(), 100, 1);

        List<Map<String, Object>> rows = query(token, null, null, null);
        assertTrue(hasId(rows, a) && hasId(rows, b), "不传过滤参数应两者都返回：" + rows);
    }
}
