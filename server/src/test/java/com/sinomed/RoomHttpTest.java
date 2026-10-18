package com.sinomed;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 工位/房间 HTTP 回归（美容业态 b：预约资源模型，见 docs/design/salon-extension.md §2-b）：
 * 建工位读回（enabled 缺省 1 在用）、改名/停用更新、删除。
 * 走真实 HTTP。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.datasource.url=jdbc:sqlite:target/room-http-test-${random.uuid}.db"})
class RoomHttpTest {

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

    private Map<?, ?> createRoom(String token, String name) {
        return rest.exchange(url("/api/v1/room/"), HttpMethod.POST,
                authEntity(Map.of("name", name), token), Map.class).getBody();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listRooms(String token) {
        return (List<Map<String, Object>>) (List<?>) rest.exchange(url("/api/v1/room/"), HttpMethod.GET,
                authEntity(null, token), List.class).getBody();
    }

    @Test
    void roomCreatedAndReadBack() {
        String token = token();
        String name = "美容工位-" + System.nanoTime();
        Map<?, ?> created = createRoom(token, name);
        assertEquals(name, created.get("name"), "工位名应保存：" + created);
        assertEquals(1, ((Number) created.get("enabled")).intValue(), "新工位缺省在用：" + created);

        List<Map<String, Object>> rooms = listRooms(token);
        assertTrue(rooms.stream().anyMatch(r -> name.equals(r.get("name"))),
                "列表应含新工位：" + rooms.size() + " 条");
    }

    @Test
    void roomUpdateRenameAndDisable() {
        String token = token();
        String name = "护理间-" + System.nanoTime();
        Map<?, ?> created = createRoom(token, name);
        long id = ((Number) created.get("id")).longValue();

        String newName = name + "-改名";
        Map<?, ?> updated = rest.exchange(url("/api/v1/room/"), HttpMethod.PUT,
                authEntity(Map.of("id", id, "name", newName, "enabled", 0), token), Map.class).getBody();
        assertEquals(newName, updated.get("name"), "改名应生效：" + updated);
        assertEquals(0, ((Number) updated.get("enabled")).intValue(), "停用应生效：" + updated);
    }

    @Test
    void roomDelete() {
        String token = token();
        Map<?, ?> created = createRoom(token, "待删工位-" + System.nanoTime());
        long id = ((Number) created.get("id")).longValue();

        rest.exchange(url("/api/v1/room/" + id), HttpMethod.DELETE,
                authEntity(null, token), Map.class);

        int status;
        try {
            rest.exchange(url("/api/v1/room/" + id), HttpMethod.GET, authEntity(null, token), Map.class);
            status = 200;
        } catch (HttpClientErrorException e) {
            status = e.getStatusCode().value();
        }
        assertEquals(400, status, "删除后按 id 查应报不存在");
    }
}
