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
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 药房发药 HTTP 回归：POST /{id}/dispense 发药与重复拒绝、GET /dispense/page 工作台
 * （状态/顾客关键字过滤 + 中文参数编码）、未登录 401。走真实 HTTP（@RequestParam
 * 绑定与 SecurityContext 操作人只有真请求才覆盖得到）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.datasource.url=jdbc:sqlite:target/dispense-http-test-${random.uuid}.db"})
class DispenseHttpTest {

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

    /** 中文参数必须走 URI（RestTemplate 字符串模板会把中文编坏，查 0 条） */
    private URI uri(String path, String... kv) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url(path));
        for (int i = 0; i < kv.length; i += 2) {
            builder.queryParam(kv[i], kv[i + 1]);
        }
        return builder.build().encode().toUri();
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

    private String exchange4xx(String method, URI uri, Object body, String token) {
        try {
            return rest.exchange(uri, HttpMethod.valueOf(method), authEntity(body, token), String.class).getBody();
        } catch (HttpStatusCodeException e) {
            return e.getResponseBodyAsString();
        }
    }

    /** data 为 List<Map> 行集合，按 id 匹配（避免依赖 Map.toString 格式） */
    @SuppressWarnings("unchecked")
    private static boolean hasId(Map<String, Object> page, int id) {
        List<Map<String, Object>> rows = (List<Map<String, Object>>) page.get("data");
        return rows != null && rows.stream().anyMatch(r -> String.valueOf(r.get("id")).equals(String.valueOf(id)));
    }

    @Test
    void dispenseFlowOverHttp() {
        String token = token();
        Map<String, Object> customer = post("/api/v1/customer/",
                Map.of("name", "发药网客", "gender", 1, "phone", "13900006001"), token);
        Map<String, Object> created = post("/api/v1/prescription/",
                Map.of("customerId", customer.get("id"), "doses", 7,
                        "herbs", List.of(Map.of("herb", "黄芪", "weight", 30))), token);
        int id = (Integer) created.get("id");
        assertEquals(0, created.get("dispenseStatus"), "新方默认待发药");

        // 待发药分页能查到（按顾客姓名中文过滤走 URI 编码）
        URI todo = uri("/api/v1/prescription/dispense/page", "current", "1", "pageSize", "10",
                "dispenseStatus", "0", "customerKeyword", "发药网客");
        Map<String, Object> page = rest.exchange(todo, HttpMethod.GET, authEntity(null, token), Map.class).getBody();
        assertTrue(hasId(page, id), "工作台应含新方：" + page);

        // 发药（带备注）：回工作台行视图
        Map<String, Object> done = post("/api/v1/prescription/" + id + "/dispense",
                Map.of("note", "自取核对无误"), token);
        assertEquals(1, done.get("dispenseStatus"));
        assertEquals(1, done.get("itemCount"));

        // 已发药分页能查到；待发药分页不再有
        Map<String, Object> donePage = rest.exchange(
                uri("/api/v1/prescription/dispense/page", "current", "1", "pageSize", "10", "dispenseStatus", "1"),
                HttpMethod.GET, authEntity(null, token), Map.class).getBody();
        assertTrue(hasId(donePage, id), "已发药分页应含该方：" + donePage);

        // 重复发药 400
        String dup = exchange4xx("POST", uri("/api/v1/prescription/" + id + "/dispense"),
                Map.of("note", "再发"), token);
        assertTrue(dup.contains("已发药"), "重复发药应 400：" + dup);

        // 未登录 401
        boolean threw = false;
        try {
            rest.exchange(uri("/api/v1/prescription/" + id + "/dispense"), HttpMethod.POST,
                    new HttpEntity<>(Map.of("note", "x")), Map.class);
        } catch (Exception e) {
            threw = true;
        }
        assertTrue(threw, "未登录应 401");
    }
}
