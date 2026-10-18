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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 客群标签 HTTP 回归（美容业态 d，docs/design/salon-extension.md §2-d）：
 * 肤质/偏好 = 轻量 customer_tags（同顾客同标签唯一）；
 * 护理方案 = customer_histories 档案扩展（type 2，不复用外治处方模板）；
 * 敏感信息单独同意：customers 记 beauty_consent 标志+时间+经办员工（PIPL 口径，随 d 同批）。
 * 过敏史复用 customer_histories type 0，本就实装，不在本测试范围。走真实 HTTP。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.datasource.url=jdbc:sqlite:target/customer-tag-http-test-${random.uuid}.db"})
class CustomerTagHttpTest {

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

    private Long seedCustomer(String tag) {
        CustomerEntity customer = new CustomerEntity();
        customer.setName(tag + "-顾客");
        return customerRepository.save(customer).getId();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listTags(String token, long customerId) {
        return (List<Map<String, Object>>) (List<?>) rest.exchange(url("/api/v1/customer/" + customerId + "/tag"),
                HttpMethod.GET, authEntity(null, token), List.class).getBody();
    }

    @Test
    void tagAddedListedAndDuplicateRejected() {
        String token = token();
        long customerId = seedCustomer("tag-add-" + System.nanoTime());

        Map<?, ?> created = rest.exchange(url("/api/v1/customer/" + customerId + "/tag"), HttpMethod.POST,
                authEntity(Map.of("tag", "肤质·油性"), token), Map.class).getBody();
        assertEquals("肤质·油性", created.get("tag"), "标签应保存：" + created);

        rest.exchange(url("/api/v1/customer/" + customerId + "/tag"), HttpMethod.POST,
                authEntity(Map.of("tag", "偏好·力度大"), token), Map.class);

        List<Map<String, Object>> tags = listTags(token, customerId);
        assertEquals(2, tags.size(), "应含两条标签：" + tags);

        int status;
        try {
            rest.exchange(url("/api/v1/customer/" + customerId + "/tag"), HttpMethod.POST,
                    authEntity(Map.of("tag", "肤质·油性"), token), Map.class);
            status = 200;
        } catch (HttpClientErrorException e) {
            status = e.getStatusCode().value();
        }
        assertEquals(400, status, "同顾客同标签重复应 400（去重由唯一性保证）");
    }

    @Test
    void tagDeleted() {
        String token = token();
        long customerId = seedCustomer("tag-del-" + System.nanoTime());
        Map<?, ?> created = rest.exchange(url("/api/v1/customer/" + customerId + "/tag"), HttpMethod.POST,
                authEntity(Map.of("tag", "敏感肌"), token), Map.class).getBody();
        long tagId = ((Number) created.get("id")).longValue();

        rest.exchange(url("/api/v1/customer/tag/" + tagId), HttpMethod.DELETE,
                authEntity(null, token), Map.class);
        assertFalse(listTags(token, customerId).stream().anyMatch(t -> ((Number) t.get("id")).longValue() == tagId),
                "删除后列表不应再含该标签");
    }

    @Test
    @SuppressWarnings("unchecked")
    void sensitiveConsentRecorded() {
        String token = token();
        long customerId = seedCustomer("tag-consent-" + System.nanoTime());

        Map<?, ?> before = rest.exchange(url("/api/v1/customer/" + customerId), HttpMethod.GET,
                authEntity(null, token), Map.class).getBody();
        assertEquals(0, ((Number) before.get("beautyConsent")).intValue(), "默认未同意：" + before);

        Map<?, ?> consented = rest.exchange(url("/api/v1/customer/" + customerId + "/consent"), HttpMethod.PUT,
                authEntity(Map.of("consent", 1), token), Map.class).getBody();
        assertEquals(1, ((Number) consented.get("beautyConsent")).intValue(), "应记同意：" + consented);
        assertNotNull(consented.get("beautyConsentTime"), "应记同意时刻：" + consented);
        assertNotNull(consented.get("beautyConsentStaffId"), "应记经办员工：" + consented);

        Map<?, ?> again = rest.exchange(url("/api/v1/customer/"), HttpMethod.PUT,
                authEntity(Map.of("id", customerId, "name", "改名核验"), token), Map.class).getBody();
        assertTrue(((Number) again.get("beautyConsent")).intValue() == 1, "档案更新不应抹掉同意记录：" + again);
    }

    @Test
    @SuppressWarnings("unchecked")
    void carePlanRidesHistoryType2() {
        String token = token();
        long customerId = seedCustomer("tag-plan-" + System.nanoTime());

        Map<?, ?> plan = rest.exchange(url("/api/v1/customer/" + customerId + "/history"), HttpMethod.POST,
                authEntity(Map.of("type", 2, "content", "每周一次补水护理，共 8 次"), token), Map.class).getBody();
        assertEquals(2, ((Number) plan.get("type")).intValue(), "护理方案应落 type=2：" + plan);

        List<Map<String, Object>> histories = (List<Map<String, Object>>) (List<?>) rest.exchange(
                url("/api/v1/customer/" + customerId + "/history"), HttpMethod.GET,
                authEntity(null, token), List.class).getBody();
        assertTrue(histories.stream().anyMatch(h -> ((Number) h.get("type")).intValue() == 2),
                "档案应含护理方案条目：" + histories);

        int status;
        try {
            rest.exchange(url("/api/v1/customer/" + customerId + "/history"), HttpMethod.POST,
                    authEntity(Map.of("type", 3, "content", "非法类型"), token), Map.class);
            status = 200;
        } catch (HttpClientErrorException e) {
            status = e.getStatusCode().value();
        }
        assertEquals(400, status, "type 白名单外仍应 400");
    }
}
