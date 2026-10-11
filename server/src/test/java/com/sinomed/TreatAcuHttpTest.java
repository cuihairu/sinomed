package com.sinomed;

import com.sinomed.entity.CustomerEntity;
import com.sinomed.repository.CustomerRepository;
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

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 针灸处方字段 HTTP 回归：接诊单创建/更新携带 针法/留针/手法/灸法/疗程，覆盖请求体绑定层与守卫、未登录 401。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.datasource.url=jdbc:sqlite:target/treat-acu-http-test-${random.uuid}.db"})
class TreatAcuHttpTest {

    @LocalServerPort
    private int port;

    @Autowired
    private CustomerRepository customerRepository;

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

    private String exchange4xx(String path, HttpMethod method, Object body, String token) {
        try {
            return rest.exchange(url(path), method, authEntity(body, token), String.class).getBody();
        } catch (HttpStatusCodeException e) {
            return e.getResponseBodyAsString();
        }
    }

    @Test
    void acupunctureFieldsBindOnCreateUpdateAndRead() {
        String token = token();
        CustomerEntity customer = new CustomerEntity();
        customer.setName("针灸顾客");
        customer.setPhone("13900009701");
        Long customerId = customerRepository.save(customer).getId();

        Map<String, Object> createBody = new java.util.HashMap<>();
        createBody.put("customerId", customerId);
        createBody.put("desc", "夜寐不安");
        createBody.put("acuMethod", "毫针、耳穴压豆");
        createBody.put("retentionMinutes", 25);
        createBody.put("manipulation", "平补平泻");
        createBody.put("moxaMethod", "艾条悬灸，每穴 15 分钟");
        createBody.put("acuCourse", "每周 2 次 × 2 周");
        createBody.put("pulseCunLeft", "弦");
        createBody.put("pulseGuanLeft", "沉迟");
        createBody.put("pulseChiLeft", "弱");
        createBody.put("pulseCunRight", "浮");
        createBody.put("pulseGuanRight", "沉细");
        createBody.put("pulseChiRight", "沉");
        Map<?, ?> created = rest.exchange(url("/api/v1/treat/"), HttpMethod.POST,
                authEntity(createBody, token), Map.class).getBody();
        assertTrue(created != null && created.get("id") != null, "创建应返回 id：" + created);
        Long id = ((Number) created.get("id")).longValue();
        assertEquals("毫针、耳穴压豆", created.get("acuMethod"), "针法应随单保存");
        assertEquals(25, ((Number) created.get("retentionMinutes")).intValue(), "留针分钟应随单保存");
        assertEquals("沉迟", created.get("pulseGuanLeft"), "左手关脉应随单保存");

        Map<?, ?> fetched = rest.exchange(url("/api/v1/treat/" + id), HttpMethod.GET,
                authEntity(null, token), Map.class).getBody();
        assertEquals("平补平泻", fetched.get("manipulation"), "手法应可回读");
        assertEquals("艾条悬灸，每穴 15 分钟", fetched.get("moxaMethod"), "灸法应可回读");
        assertEquals("每周 2 次 × 2 周", fetched.get("acuCourse"), "疗程应可回读");
        assertEquals("弦", fetched.get("pulseCunLeft"), "左手寸脉应可回读");
        assertEquals("弱", fetched.get("pulseChiLeft"), "左手尺脉应可回读");
        assertEquals("浮", fetched.get("pulseCunRight"), "右手寸脉应可回读");
        assertEquals("沉细", fetched.get("pulseGuanRight"), "右手关脉应可回读");
        assertEquals("沉", fetched.get("pulseChiRight"), "右手尺脉应可回读");

        Map<?, ?> updated = rest.exchange(url("/api/v1/treat/"), HttpMethod.PUT,
                authEntity(Map.of("id", id, "customerId", customerId, "desc", "复诊",
                        "acuMethod", "温针", "retentionMinutes", 30, "manipulation", "补法"), token),
                Map.class).getBody();
        assertEquals("温针", updated.get("acuMethod"), "更新应生效");
        assertEquals(30, ((Number) updated.get("retentionMinutes")).intValue());
        assertNull(updated.get("acuCourse"), "未带字段按全量更新应清空");
    }

    @Test
    void acupunctureGuardsAndAuth() {
        String token = token();
        CustomerEntity customer = new CustomerEntity();
        customer.setName("守卫顾客");
        customer.setPhone("13900009702");
        Long customerId = customerRepository.save(customer).getId();

        String badRetention = exchange4xx("/api/v1/treat/", HttpMethod.POST,
                Map.of("customerId", customerId, "retentionMinutes", 300), token);
        assertTrue(badRetention != null && badRetention.contains("留针时长无效"), "留针越界应 400：" + badRetention);

        String badMethod = exchange4xx("/api/v1/treat/", HttpMethod.POST,
                Map.of("customerId", customerId, "acuMethod", "字".repeat(31)), token);
        assertTrue(badMethod != null && badMethod.contains("针法限 30 字内"), "针法超长应 400：" + badMethod);

        boolean threw = false;
        try {
            rest.exchange(url("/api/v1/treat/" + customerId), HttpMethod.GET, HttpEntity.EMPTY, Map.class);
        } catch (Exception e) {
            threw = true;
        }
        assertTrue(threw, "未登录应 401");
    }
}
