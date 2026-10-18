package com.sinomed;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
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
 * 顾客分页过滤 HTTP 回归（巡检 2026-10-19 收口顾客/员工 name 精确匹配静默空结果）：
 * name 按包含（不区分大小写，同 ItemController 全仓口径——web 搜索框与 /treat/fuzzy 的
 * 「模糊检索」契约都按包含语义使用）、phone 精确、可叠加；不传即不过滤。走真实 HTTP。
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {"spring.datasource.url=jdbc:sqlite:target/customer-filter-http-test-${random.uuid}.db"})
class CustomerFilterHttpTest {

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

    private long createCustomer(String token, String name, String phone) {
        Map<?, ?> created = rest.exchange(url("/api/v1/customer/"), HttpMethod.POST,
                authEntity(Map.of("name", name, "phone", phone, "gender", 1), token), Map.class).getBody();
        return ((Number) created.get("id")).longValue();
    }

    /** UriComponentsBuilder 只编码一次（手拼 %E8… 会被 RestTemplate 再编码，服务端收到乱码致 LIKE 不命中） */
    private java.net.URI pageUri(String name, String phone) {
        var builder = org.springframework.web.util.UriComponentsBuilder
                .fromUriString(url("/api/v1/customer/page"))
                .queryParam("current", 1)
                .queryParam("pageSize", 50);
        if (name != null) {
            builder.queryParam("name", name);
        }
        if (phone != null) {
            builder.queryParam("phone", phone);
        }
        return builder.build().encode().toUri();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> query(String token, String name, String phone) {
        Map<?, ?> page = rest.exchange(pageUri(name, phone),
                HttpMethod.GET, authEntity(null, token), Map.class).getBody();
        return (List<Map<String, Object>>) (List<?>) page.get("data");
    }

    private boolean hasId(List<Map<String, Object>> rows, long id) {
        return rows.stream().anyMatch(r -> id == ((Number) r.get("id")).longValue());
    }

    @Test
    void pageFiltersByNameContains() {
        String token = token();
        long shuibu = createCustomer(token, "补水顾客甲" + System.nanoTime(), "13800020001");
        long guasha = createCustomer(token, "刮痧顾客乙" + System.nanoTime(), "13800020002");

        List<Map<String, Object>> rows = query(token, "补水", null);
        assertTrue(hasId(rows, shuibu), "name 包含过滤应命中补水顾客：" + rows);
        assertTrue(!hasId(rows, guasha), "name 包含过滤不应命中刮痧顾客：" + rows);
    }

    @Test
    void pageFiltersNameIgnoreCase() {
        String token = token();
        long latin = createCustomer(token, "ABC体验客" + System.nanoTime(), "13800020011");

        List<Map<String, Object>> rows = query(token, "abc", null);
        assertTrue(hasId(rows, latin), "name 过滤应不区分大小写命中 ABC 顾客：" + rows);
    }

    @Test
    void pageFiltersByPhoneExact() {
        String token = token();
        long p1 = createCustomer(token, "号码过滤甲" + System.nanoTime(), "13800020021");
        long p2 = createCustomer(token, "号码过滤乙" + System.nanoTime(), "13800020022");

        List<Map<String, Object>> rows = query(token, null, "13800020021");
        assertTrue(hasId(rows, p1), "phone 精确过滤应命中甲：" + rows);
        assertTrue(!hasId(rows, p2), "phone 精确过滤不应命中乙：" + rows);
    }

    @Test
    void pageFiltersNameAndPhoneCombinedAnd() {
        String token = token();
        long hit = createCustomer(token, "叠加甲" + System.nanoTime(), "13800020031");
        long sameNameOtherPhone = createCustomer(token, "叠加甲" + System.nanoTime(), "13800020032");

        List<Map<String, Object>> rows = query(token, "叠加", "13800020031");
        assertTrue(hasId(rows, hit), "name+phone 叠加应命中同项：" + rows);
        assertTrue(!hasId(rows, sameNameOtherPhone), "name+phone 叠加不应命中同名异号（AND 语义）：" + rows);

        List<Map<String, Object>> rowsByPhoneOnly = query(token, null, "13800020031");
        assertTrue(rowsByPhoneOnly.size() == 1, "phone 单过滤应唯一命中：" + rowsByPhoneOnly);

        List<Map<String, Object>> rowsWrongName = query(token, "异名", "13800020031");
        assertTrue(rowsWrongName.isEmpty(), "name+phone 叠加中 name 不匹配应空结果（AND 语义）：" + rowsWrongName);
    }

    @Test
    void pageWithoutFiltersReturnsAll() {
        String token = token();
        long a = createCustomer(token, "不过滤顾客甲" + System.nanoTime(), "13800020041");
        long b = createCustomer(token, "不过滤顾客乙" + System.nanoTime(), "13800020042");

        List<Map<String, Object>> rows = query(token, null, null);
        assertTrue(hasId(rows, a) && hasId(rows, b), "不传过滤参数应两者都返回：" + rows);
    }
}
