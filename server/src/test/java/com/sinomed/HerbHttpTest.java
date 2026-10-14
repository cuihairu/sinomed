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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 药材字典 HTTP 回归：收录（名称去空白）→ 重复收录 400 → 改价 → 关键字分页 → 删除；
 * 校验 400（价格非正/单剂上限非正/药材不存在/缺 id）、未登录 401。
 * 服务层校验见 HerbServiceTest，本类走真实 HTTP 覆盖参数绑定层。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.datasource.url=jdbc:sqlite:target/herb-http-test-${random.uuid}.db"})
class HerbHttpTest {

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

    private <T> T exchange(String method, String path, String token, Object body, Class<T> type) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(url(path), HttpMethod.valueOf(method), new HttpEntity<>(body, headers), type).getBody();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> page(String token, String keyword) {
        var builder = org.springframework.web.util.UriComponentsBuilder.fromUriString(url("/api/v1/herb/page"));
        builder.queryParam("current", 1).queryParam("pageSize", 10);
        if (keyword != null) {
            builder.queryParam("keyword", keyword);
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return rest.exchange(builder.build().encode().toUri(), HttpMethod.GET,
                new HttpEntity<>(headers), Map.class).getBody();
    }

    private String err4xx(String method, String path, String token, Object body) {
        try {
            return exchange(method, path, token, body, String.class);
        } catch (HttpStatusCodeException e) {
            return e.getResponseBodyAsString();
        }
    }

    @Test
    void herbCrudFlow() {
        String token = token();

        // 收录：名称两侧空白应去除
        Map<String, Object> created = exchange("POST", "/api/v1/herb/", token,
                Map.of("name", " 当归 ", "price", 50), Map.class);
        assertEquals("当归", created.get("name"), "收录应去除名称空白");
        assertEquals(50, ((Number) created.get("price")).intValue());
        Long id = ((Number) created.get("id")).longValue();

        // 重复收录 400
        assertTrue(err4xx("POST", "/api/v1/herb/", token, Map.of("name", "当归", "price", 60))
                        .contains("药材已收录：当归"), "重复收录应 400");

        // 改价 + 补单剂上限
        Map<String, Object> updated = exchange("PUT", "/api/v1/herb/", token,
                Map.of("id", id, "name", "当归", "price", 55, "doseMax", 12.0), Map.class);
        assertEquals(55, ((Number) updated.get("price")).intValue());
        assertEquals(12.0, ((Number) updated.get("doseMax")).doubleValue(), 0.001);

        // 关键字分页：按名称包含过滤
        Map<String, Object> result = page(token, "当归");
        assertEquals(1, ((Number) result.get("total")).intValue());
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("data");
        assertEquals("当归", rows.get(0).get("name"));
        assertEquals(55, ((Number) rows.get(0).get("price")).intValue());

        // 删除后关键字查无
        Map<String, Object> deleted = exchange("DELETE", "/api/v1/herb/" + id, token, null, Map.class);
        assertTrue(String.valueOf(deleted.get("message")).contains("已删除药材"), "删除应回执消息");
        assertEquals(0, ((Number) page(token, "当归").get("total")).intValue(), "删后应查无");
    }

    @Test
    void herbGuards() {
        String token = token();
        Map<String, Object> created = exchange("POST", "/api/v1/herb/", token,
                Map.of("name", "白术", "price", 30), Map.class);
        Long id = ((Number) created.get("id")).longValue();

        // 校验 400：名称空白 / 价格非正 / 单剂上限非正
        Map<String, Object> blank = new HashMap<>();
        blank.put("name", " ");
        blank.put("price", 10);
        assertTrue(err4xx("POST", "/api/v1/herb/", token, blank).contains("success"), "名称空白应 400");
        assertTrue(err4xx("POST", "/api/v1/herb/", token, Map.of("name", "黄芪", "price", 0))
                        .contains("每克价格必须大于 0"), "价格 0 应 400");
        assertTrue(err4xx("POST", "/api/v1/herb/", token, Map.of("name", "黄芪", "price", 10, "doseMax", 0))
                        .contains("单剂上限必须大于 0"), "单剂上限 0 应 400");
        // 更新守卫：缺 id / 不存在 / 改成他名
        assertTrue(err4xx("PUT", "/api/v1/herb/", token, Map.of("name", "白术", "price", 31))
                        .contains("药材id不能为空"), "更新缺 id 应 400");
        assertTrue(err4xx("PUT", "/api/v1/herb/", token, Map.of("id", 99999L, "name", "白术", "price", 31))
                        .contains("药材不存在"), "更新不存在应 400");
        // 删除不存在 400
        assertTrue(err4xx("DELETE", "/api/v1/herb/99999", token, null).contains("药材不存在"),
                "删除不存在应 400");
    }

    @Test
    void unauthenticatedRejected() {
        for (String method : new String[]{"POST", "PUT", "DELETE"}) {
            boolean threw = false;
            try {
                exchange(method, "/api/v1/herb/" + ("DELETE".equals(method) ? "1" : ""), null,
                        Map.of("name", "x", "price", 1), Map.class);
            } catch (HttpStatusCodeException e) {
                threw = e.getStatusCode().value() == 401;
            }
            assertTrue(threw, "未登录 " + method + " 药材应 401");
        }
        boolean pageThrew = false;
        try {
            rest.exchange(url("/api/v1/herb/page?current=1&pageSize=10"), HttpMethod.GET,
                    HttpEntity.EMPTY, String.class);
        } catch (HttpStatusCodeException e) {
            pageThrew = e.getStatusCode().value() == 401;
        }
        assertTrue(pageThrew, "未登录查字典应 401");
    }
}
