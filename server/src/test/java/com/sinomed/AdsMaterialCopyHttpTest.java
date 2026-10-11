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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 广告素材文案字段（叠加层）HTTP 回归：开方同款签名行口径——
 * 创建素材带 眉题/大标题/文案/价格 四字段，分页读回一致。走真实 HTTP。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.datasource.url=jdbc:sqlite:target/ads-copy-http-test-${random.uuid}.db"})
class AdsMaterialCopyHttpTest {

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
    @Test
    void materialCopyFieldsSavedAndReadBack() {
        String token = token();
        String name = "三九贴活动" + System.nanoTime();
        Map<String, Object> created = rest.exchange(url("/api/v1/ads/materials"), HttpMethod.POST,
                authEntity(Map.of(
                        "name", name, "type", 1, "url", "/media/202610/test.png",
                        "durationMs", 8000, "enabled", 1, "sort", 10,
                        "kicker", "冬病夏治",
                        "title", "三伏艾灸，借阳祛寒正当时",
                        "copy", "督脉灸配伍中脘、足三里，适合手脚怕冷的顾客。",
                        "price", "¥880 / 10 次"), token), Map.class).getBody();
        assertTrue(created.get("id") != null, "建素材应返回 id：" + created);
        assertEquals("冬病夏治", created.get("kicker"), "眉题应存档：" + created);
        assertEquals("¥880 / 10 次", created.get("price"), "价格行应存档：" + created);

        // 分页读回
        Map<String, Object> page = rest.exchange(url("/api/v1/ads/materials/page?current=1&pageSize=20"),
                HttpMethod.GET, authEntity(null, token), Map.class).getBody();
        List<Map<String, Object>> data = (List<Map<String, Object>>) page.get("data");
        Map<String, Object> found = data.stream()
                .filter(m -> name.equals(m.get("name")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("分页应含新建素材：" + page));
        assertEquals("三伏艾灸，借阳祛寒正当时", found.get("title"));
        assertEquals("督脉灸配伍中脘、足三里，适合手脚怕冷的顾客。", found.get("copy"));
        assertEquals("冬病夏治", found.get("kicker"));
        assertEquals("¥880 / 10 次", found.get("price"));
    }
}
