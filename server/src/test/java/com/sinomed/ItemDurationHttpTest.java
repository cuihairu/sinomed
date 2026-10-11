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

/**
 * 卡项时长字段 HTTP 回归：创建带 durationMinutes（分钟，可空展示字段），读回一致；不带时按空存取。走真实 HTTP。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.datasource.url=jdbc:sqlite:target/item-duration-http-test-${random.uuid}.db"})
class ItemDurationHttpTest {

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

    @Test
    void durationSavedAndReadBack() {
        String token = token();
        Map<?, ?> created = rest.exchange(url("/api/v1/item/"), HttpMethod.POST,
                authEntity(Map.of("name", "艾灸调理-" + System.nanoTime(), "price", 100,
                        "description", "温经散寒", "durationMinutes", 40), token), Map.class).getBody();
        assertEquals(40, ((Number) created.get("durationMinutes")).intValue(), "时长应随卡项保存：" + created);

        long id = ((Number) created.get("id")).longValue();
        Map<?, ?> fetched = rest.exchange(url("/api/v1/item/" + id), HttpMethod.GET,
                authEntity(null, token), Map.class).getBody();
        assertEquals(40, ((Number) fetched.get("durationMinutes")).intValue(), "时长应可回读：" + fetched);
    }
}
