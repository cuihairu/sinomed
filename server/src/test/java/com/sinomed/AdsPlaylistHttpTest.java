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

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 平板播放列表 HTTP 回归：免登录拉取（放行段真实走通）→ 无排期兜底全部启用素材（sort 升序、
 * 停用素材排除）→ 排期命中只播定向素材 → 心跳回执（lastSeenAt）；守卫 400（未知屏/停用屏/空参）。
 * 素材字段落库细节见 AdsMaterialCopyHttpTest，本类聚焦 playlist 下发面。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.datasource.url=jdbc:sqlite:target/ads-playlist-http-test-${random.uuid}.db"})
class AdsPlaylistHttpTest {

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

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String path, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(url(path), HttpMethod.POST, new HttpEntity<>(body, headers), Map.class).getBody();
    }

    /** 免登录拉取（无 token，放行段应直接 200） */
    @SuppressWarnings("unchecked")
    private Map<String, Object> pull(String screen) {
        return rest.getForEntity(url("/api/v1/ads/playlist?screen=" + screen), Map.class).getBody();
    }

    private String pull4xx(String screen) {
        try {
            return rest.getForEntity(url("/api/v1/ads/playlist?screen=" + screen), String.class).getBody();
        } catch (HttpStatusCodeException e) {
            return e.getResponseBodyAsString();
        }
    }

    private Map<String, Object> material(String name, int sort, int enabled) {
        return Map.of("name", name, "type", 1, "url", "/media/202610/" + name + ".png",
                "durationMs", 8000, "sort", sort, "enabled", enabled);
    }

    private Long screen(String token, String code, int enabled) {
        return ((Number) post("/api/v1/ads/screens", token,
                Map.of("code", code, "name", code + "屏", "enabled", enabled)).get("id")).longValue();
    }

    @Test
    void anonymousPullFallsBackToEnabledMaterialsSorted() {
        String token = token();
        Long screenId = screen(token, "PAD-HTTP-01", 1);
        post("/api/v1/ads/materials", token, material("主推卡项", 1, 1));
        post("/api/v1/ads/materials", token, material("节气海报", 0, 1));
        post("/api/v1/ads/materials", token, material("已下架素材", 2, 0));

        // 免登录拉取：未带任何凭证应 200（401 会抛异常使测试失败）
        Map<String, Object> playlist = pull("PAD-HTTP-01");
        assertEquals("PAD-HTTP-01", playlist.get("code"));
        assertEquals(screenId, ((Number) playlist.get("screenId")).longValue());
        assertNotNull(playlist.get("version"), "应带内容戳");
        assertNotNull(playlist.get("lastSeenAt"), "拉取应回心跳时间");

        List<Map<String, Object>> items = (List<Map<String, Object>>) playlist.get("items");
        assertEquals(2, items.size(), "无排期兜底全部启用素材，停用素材排除");
        assertEquals("节气海报", items.get(0).get("name"), "sort 升序在前");
        assertEquals("主推卡项", items.get(1).get("name"));
        assertEquals("/media/202610/节气海报.png", items.get(0).get("url"), "素材字段透传");
        assertEquals(8000, ((Number) items.get(0).get("durationMs")).intValue());
    }

    @Test
    void scheduleHitOverridesFallback() {
        String token = token();
        Long screenId = screen(token, "PAD-HTTP-02", 1);
        Long target = ((Number) post("/api/v1/ads/materials", token, material("定向素材", 0, 1)).get("id")).longValue();
        post("/api/v1/ads/materials", token, material("兜底素材", 1, 1));

        // 全天全周排期（时段与星期留空 = 恒命中）只绑定向素材
        post("/api/v1/ads/schedules", token, Map.of("screenId", screenId, "materialId", target,
                "name", "全天排期", "enabled", 1));

        Map<String, Object> playlist = pull("PAD-HTTP-02");
        List<Map<String, Object>> items = (List<Map<String, Object>>) playlist.get("items");
        assertEquals(1, items.size(), "排期命中只播定向素材，不再兜底");
        assertEquals("定向素材", items.get(0).get("name"));
    }

    @Test
    void playlistGuards() {
        // 未知屏 400
        assertTrue(pull4xx("NO-SUCH").contains("屏不存在或已停用"), "未知屏应 400");
        // 停用屏同口径 400
        String token = token();
        screen(token, "PAD-HTTP-03", 0);
        assertTrue(pull4xx("PAD-HTTP-03").contains("屏不存在或已停用"), "停用屏应 400");
        // 空参 400
        assertTrue(pull4xx("").contains("缺少 screen 参数"), "空参应 400");
    }
}
