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
 * 员工分页过滤 HTTP 回归（巡检 2026-10-19 收口顾客/员工 name 精确匹配静默空结果）：
 * name 按包含（不区分大小写，同 ItemController 全仓口径——web 搜索框按包含语义使用），
 * 不传即不过滤；排序口径（famousLevel 降序、同等级创建时间升序）不变。走真实 HTTP。
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {"spring.datasource.url=jdbc:sqlite:target/staff-filter-http-test-${random.uuid}.db"})
class StaffFilterHttpTest {

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

    private long createStaff(String token, String account, String name) {
        Map<?, ?> created = rest.exchange(url("/api/v1/staff/"), HttpMethod.POST,
                authEntity(Map.of("account", account, "password", "pw123456", "name", name), token), Map.class)
                .getBody();
        return ((Number) created.get("id")).longValue();
    }

    /** UriComponentsBuilder 只编码一次（手拼 %E8… 会被 RestTemplate 再编码，服务端收到乱码致 LIKE 不命中） */
    private java.net.URI pageUri(String name) {
        var builder = org.springframework.web.util.UriComponentsBuilder
                .fromUriString(url("/api/v1/staff/page"))
                .queryParam("current", 1)
                .queryParam("pageSize", 50);
        if (name != null) {
            builder.queryParam("name", name);
        }
        return builder.build().encode().toUri();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> query(String token, String name) {
        Map<?, ?> page = rest.exchange(pageUri(name), HttpMethod.GET,
                authEntity(null, token), Map.class).getBody();
        return (List<Map<String, Object>>) (List<?>) page.get("data");
    }

    private boolean hasId(List<Map<String, Object>> rows, long id) {
        return rows.stream().anyMatch(r -> id == ((Number) r.get("id")).longValue());
    }

    @Test
    void pageFiltersByNameContains() {
        String token = token();
        String tag = String.valueOf(System.nanoTime());
        long yueke = createStaff(token, "acc-yk-" + tag, "岳客医师甲" + tag);
        long zhenjiu = createStaff(token, "acc-zj-" + tag, "针灸医师乙" + tag);

        List<Map<String, Object>> rows = query(token, "岳客");
        assertTrue(hasId(rows, yueke), "name 包含过滤应命中岳客医师：" + rows);
        assertTrue(!hasId(rows, zhenjiu), "name 包含过滤不应命中针灸医师：" + rows);
    }

    @Test
    void pageFiltersNameIgnoreCase() {
        String token = token();
        String tag = String.valueOf(System.nanoTime());
        long latin = createStaff(token, "acc-latin-" + tag, "DrABC" + tag);

        List<Map<String, Object>> rows = query(token, "drabc");
        assertTrue(hasId(rows, latin), "name 过滤应不区分大小写命中 DrABC：" + rows);
    }

    @Test
    void pageWithoutFiltersReturnsAll() {
        String token = token();
        String tag = String.valueOf(System.nanoTime());
        long a = createStaff(token, "acc-all-a-" + tag, "不过滤员工甲" + tag);
        long b = createStaff(token, "acc-all-b-" + tag, "不过滤员工乙" + tag);

        List<Map<String, Object>> rows = query(token, null);
        assertTrue(hasId(rows, a) && hasId(rows, b), "不传过滤参数应两者都返回（含 admin 自身）：" + rows);
    }
}
