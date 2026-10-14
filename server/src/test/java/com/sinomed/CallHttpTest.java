package com.sinomed;

import com.sinomed.entity.AdScreenEntity;
import com.sinomed.repository.AdScreenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
 * 叫号 HTTP 回归：前台发起（广播 + 定向屏）→ 平板游标拉取（免登录，合并去游标升序）
 * → 最近叫号；屏守卫 400（不存在/停用）、必填 400、未登录 401。
 * 走真实 HTTP 覆盖参数绑定层与放行段。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.datasource.url=jdbc:sqlite:target/call-http-test-${random.uuid}.db"})
class CallHttpTest {

    @LocalServerPort
    private int port;

    @Autowired
    private AdScreenRepository screenRepository;

    private final RestTemplate rest = new RestTemplate();

    private static Long screenId;

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

    private <T> T post(String path, String token, Object body, Class<T> type) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(url(path), HttpMethod.POST, new HttpEntity<>(body, headers), type).getBody();
    }

    private String post4xx(String path, String token, Object body) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            headers.setContentType(MediaType.APPLICATION_JSON);
            return rest.exchange(url(path), HttpMethod.POST, new HttpEntity<>(body, headers), String.class).getBody();
        } catch (HttpStatusCodeException e) {
            return e.getResponseBodyAsString();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> latest(String screen, long since) {
        return rest.getForEntity(url("/api/v1/calls/latest?screen=" + screen + "&since=" + since), Map.class).getBody();
    }

    @BeforeEach
    void seedScreen() {
        if (screenId == null) {
            AdScreenEntity screen = new AdScreenEntity();
            screen.setCode("PAD-01");
            screen.setName("候诊屏一");
            screen.setEnabled(1);
            screenId = screenRepository.save(screen).getId();
        }
    }

    @Test
    void callBroadcastAndTargetedThenCursorPull() {
        String token = token();

        // 广播叫号（不定向屏）
        Map<String, Object> broadcast = post("/api/v1/calls", token,
                Map.of("number", "08", "room", "第二诊室", "patientMasked", "张*"), Map.class);
        assertEquals("08", broadcast.get("number"));
        assertEquals(1, ((Number) broadcast.get("status")).intValue(), "写入即已叫");
        assertNotNull(broadcast.get("calledAt"));
        assertTrue(broadcast.containsKey("screenId") && broadcast.get("screenId") == null, "广播叫号不定向屏");

        // 定向本屏叫号
        Map<String, Object> targeted = post("/api/v1/calls", token,
                Map.of("screenId", screenId, "number", "09", "room", "第三诊室"), Map.class);
        assertEquals(screenId, ((Number) targeted.get("screenId")).longValue());

        // 平板游标拉取（免登录）：广播 + 定向本屏合并升序，游标 = 最大 id
        Map<String, Object> first = latest("PAD-01", 0);
        List<Map<String, Object>> calls = (List<Map<String, Object>>) first.get("calls");
        assertEquals(2, calls.size());
        assertTrue(((Number) calls.get(0).get("id")).longValue() < ((Number) calls.get(1).get("id")).longValue(),
                "按 id 升序");
        assertEquals("08", calls.get(0).get("number"));
        assertEquals("09", calls.get(1).get("number"));
        Long cursor = ((Number) first.get("since")).longValue();

        // 游标去重：再拉无新记录，游标不回退
        Map<String, Object> second = latest("PAD-01", cursor);
        assertEquals(0, ((List<?>) second.get("calls")).size());
        assertEquals(cursor, ((Number) second.get("since")).longValue());

        // 管理端最近叫号
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        List<Map<String, Object>> recent = (List<Map<String, Object>>) rest.exchange(url("/api/v1/calls/recent"),
                HttpMethod.GET, new HttpEntity<>(headers), List.class).getBody();
        assertNotNull(recent);
        assertTrue(recent.size() >= 2, "应含本轮两条叫号");
        assertEquals("09", recent.get(0).get("number"), "最近叫号新记录在前");
    }

    @Test
    void callGuards() {
        String token = token();

        // 必填缺失 400
        assertTrue(post4xx("/api/v1/calls", token, Map.of("room", "诊室")).contains("success"),
                "缺号码应 400");
        assertTrue(post4xx("/api/v1/calls", token, Map.of("number", "10")).contains("success"),
                "缺诊室应 400");
        // 屏不存在 400
        assertTrue(post4xx("/api/v1/calls", token, Map.of("screenId", 99999L, "number", "10", "room", "诊室"))
                        .contains("屏不存在"), "定向不存在屏应 400");
        // 屏停用 400
        AdScreenEntity off = new AdScreenEntity();
        off.setCode("PAD-02");
        off.setName("停用屏");
        off.setEnabled(0);
        Long offId = screenRepository.save(off).getId();
        assertTrue(post4xx("/api/v1/calls", token, Map.of("screenId", offId, "number", "10", "room", "诊室"))
                        .contains("屏已停用"), "定向停用屏应 400");
        // 平板拉取未知屏 400
        boolean latestThrew = false;
        try {
            latest("NO-SUCH-SCREEN", 0);
        } catch (HttpStatusCodeException e) {
            latestThrew = e.getResponseBodyAsString().contains("屏不存在");
        }
        assertTrue(latestThrew, "未知屏拉取应 400");
    }

    @Test
    void unauthenticatedRejected() {
        boolean callThrew = false;
        try {
            rest.exchange(url("/api/v1/calls"), HttpMethod.POST,
                    new HttpEntity<>(Map.of("number", "10", "room", "诊室")), String.class);
        } catch (Exception e) {
            callThrew = true;
        }
        assertTrue(callThrew, "未登录叫号应 401");

        boolean recentThrew = false;
        try {
            rest.exchange(url("/api/v1/calls/recent"), HttpMethod.GET, HttpEntity.EMPTY, String.class);
        } catch (Exception e) {
            recentThrew = true;
        }
        assertTrue(recentThrew, "未登录查最近叫号应 401");
    }
}
