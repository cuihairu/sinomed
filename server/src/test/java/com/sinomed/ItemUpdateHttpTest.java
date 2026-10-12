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

/**
 * 卡项编辑 HTTP 回归：PUT /api/v1/item/ 带 id 真实更新（前端编辑页此前一律调 createItem，
 * 编辑实为建新卡的误用隐患）；不带 id / 不存在的 id 报 400。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.datasource.url=jdbc:sqlite:target/item-update-http-test-${random.uuid}.db"})
class ItemUpdateHttpTest {

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

    private int statusOf(Object body, String token) {
        try {
            return rest.exchange(url("/api/v1/item/"), HttpMethod.PUT,
                    authEntity(body, token), Map.class).getStatusCode().value();
        } catch (HttpStatusCodeException e) {
            return e.getStatusCode().value();
        }
    }

    @Test
    void updateWithIdChangesFieldsAndKeepsId() {
        String token = token();
        Map<?, ?> created = rest.exchange(url("/api/v1/item/"), HttpMethod.POST,
                authEntity(Map.of("name", "腹部推拿-" + System.nanoTime(), "price", 200,
                        "description", "松解腰背"), token), Map.class).getBody();
        long id = ((Number) created.get("id")).longValue();

        Map<String, Object> patch = new java.util.HashMap<>(Map.of("name", created.get("name")));
        patch.put("id", id);
        patch.put("price", 260);
        patch.put("description", "松解腰背（含下肢）");
        Map<?, ?> updated = rest.exchange(url("/api/v1/item/"), HttpMethod.PUT,
                authEntity(patch, token), Map.class).getBody();

        assertEquals(id, ((Number) updated.get("id")).longValue(), "更新应回到同一条，不建新卡");
        assertEquals(260, ((Number) updated.get("price")).intValue());

        Map<?, ?> fetched = rest.exchange(url("/api/v1/item/" + id), HttpMethod.GET,
                authEntity(null, token), Map.class).getBody();
        assertEquals(260, ((Number) fetched.get("price")).intValue(), "改动应落库可回读：" + fetched);
        assertEquals("松解腰背（含下肢）", fetched.get("description"));
    }

    @Test
    void updateWithoutIdRejected() {
        assertEquals(400, statusOf(Map.of("name", "无Id编辑-" + System.nanoTime(), "price", 1), token()),
                "PUT 不带 id 应报 400");
    }

    @Test
    void updateUnknownIdRejected() {
        assertEquals(400, statusOf(Map.of("id", 999999L, "name", "幽灵卡项-" + System.nanoTime()), token()),
                "PUT 不存在的 id 应报 400");
    }
}
