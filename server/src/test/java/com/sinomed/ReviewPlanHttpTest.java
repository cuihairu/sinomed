package com.sinomed;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 顾客回访计划 HTTP 回归：/review/customer 建计划带回访原因（advice 存档读回）、
 * /review/customer/list/customer 按顾客查回访计划（回访日倒序，供档案页下次回访统计）。走真实 HTTP。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.datasource.url=jdbc:sqlite:target/review-plan-http-test-${random.uuid}.db"})
class ReviewPlanHttpTest {

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
    private Map<String, Object> post(String path, Object body, String token) {
        return rest.exchange(url(path), HttpMethod.POST, authEntity(body, token), Map.class).getBody();
    }

    private long createCustomer(String token, String name, String phone) {
        Map<String, Object> customer = post("/api/v1/customer/",
                Map.of("name", name, "gender", 1, "phone", phone), token);
        return ((Number) customer.get("id")).longValue();
    }

    /** 回访日 → 当地零点毫秒（JSON Date 字段用时间戳，免时区串改） */
    private long day(String d) {
        return LocalDate.parse(d).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /** 回读的 day 兼容时间戳数字与 ISO 字符串两种序列化 */
    private long dayMillis(Object v) {
        if (v instanceof Number) {
            return ((Number) v).longValue();
        }
        return java.time.Instant.parse(v.toString().replace("+00:00", "Z")).toEpochMilli();
    }

    private List<Map<String, Object>> listByCustomer(long customerId, String token) {
        return rest.exchange(url("/api/v1/review/customer/list/customer?customerId=" + customerId),
                HttpMethod.GET, authEntity(null, token),
                new ParameterizedTypeReference<List<Map<String, Object>>>() {}).getBody();
    }

    @Test
    void planWithAdviceSavedAndReadBackByCustomer() {
        String token = token();
        long customerId = createCustomer(token, "回访计划顾客", "13900014001");

        Map<String, Object> created = post("/api/v1/review/customer", Map.of(
                "customerId", customerId,
                "name", "回访计划顾客",
                "last", day("2026-10-05"),
                "day", day("2026-10-19"),
                "advice", "艾灸第 4 疗程结束后 7 天，回访睡眠与饮食情况"), token);
        assertTrue(created.get("id") != null, "建计划应返回 id：" + created);
        assertEquals("艾灸第 4 疗程结束后 7 天，回访睡眠与饮食情况", created.get("advice"),
                "回访原因应存档：" + created);
        assertEquals(day("2026-10-19"), dayMillis(created.get("day")), "回访日应存档：" + created);

        List<Map<String, Object>> list = listByCustomer(customerId, token);
        assertEquals(1, list.size(), "按顾客应查到 1 条计划：" + list);
        assertEquals(customerId, ((Number) list.get(0).get("customerId")).longValue());
        assertEquals("艾灸第 4 疗程结束后 7 天，回访睡眠与饮食情况", list.get(0).get("advice"));
        assertEquals(day("2026-10-19"), dayMillis(list.get(0).get("day")));
        assertEquals(day("2026-10-05"), dayMillis(list.get(0).get("last")));
    }

    @Test
    void plansOrderByDayDescAndIsolatedPerCustomer() {
        String token = token();
        long customerId = createCustomer(token, "回访排序顾客", "13900014002");
        long otherId = createCustomer(token, "回访隔离顾客", "13900014003");

        post("/api/v1/review/customer", Map.of(
                "customerId", customerId, "name", "回访排序顾客",
                "day", day("2026-10-20"), "advice", "近期计划"), token);
        post("/api/v1/review/customer", Map.of(
                "customerId", customerId, "name", "回访排序顾客",
                "day", day("2026-11-02"), "advice", "更远计划"), token);
        post("/api/v1/review/customer", Map.of(
                "customerId", otherId, "name", "回访隔离顾客",
                "day", day("2026-10-21"), "advice", "别人的计划"), token);

        List<Map<String, Object>> list = listByCustomer(customerId, token);
        assertEquals(2, list.size(), "不应串到别的顾客：" + list);
        assertEquals(day("2026-11-02"), dayMillis(list.get(0).get("day")),
                "回访日倒序，首条即最近安排：" + list);
        assertEquals(day("2026-10-20"), dayMillis(list.get(1).get("day")));
        assertEquals("更远计划", list.get(0).get("advice"));

        List<Map<String, Object>> other = listByCustomer(otherId, token);
        assertEquals(1, other.size());
        assertEquals("别人的计划", other.get(0).get("advice"));
    }
}
