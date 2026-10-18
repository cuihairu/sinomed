package com.sinomed;

import com.sinomed.entity.ItemEntity;
import com.sinomed.repository.ItemRepository;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 提成规则 HTTP 回归（美容业态 c：技师提成 P0 固定比例「项目×岗位」，docs/design/salon-extension.md §2-c）：
 * 建规则读回、同项目同岗位重复拒绝、改比例、删除。走真实 HTTP。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.datasource.url=jdbc:sqlite:target/commission-rule-http-test-${random.uuid}.db"})
class CommissionRuleHttpTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ItemRepository itemRepository;

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
    private List<Map<String, Object>> listRules(String token) {
        return (List<Map<String, Object>>) (List<?>) rest.exchange(url("/api/v1/commission/rule"),
                HttpMethod.GET, authEntity(null, token), List.class).getBody();
    }

    @Test
    void ruleCreatedAndReadBack() {
        String token = token();
        ItemEntity item = itemRepository.save(newItem("皮肤管理-" + System.nanoTime()));

        Map<?, ?> created = rest.exchange(url("/api/v1/commission/rule"), HttpMethod.POST,
                authEntity(Map.of("itemId", item.getId(), "title", "技师", "ratio", 20), token), Map.class).getBody();
        assertEquals(item.getId(), ((Number) created.get("itemId")).longValue(), "规则应挂卡项：" + created);
        assertEquals("技师", created.get("title"), "规则岗位应保存：" + created);
        assertEquals(20, ((Number) created.get("ratio")).intValue(), "比例应保存：" + created);

        assertTrue(listRules(token).stream()
                .anyMatch(r -> "技师".equals(r.get("title"))
                        && ((Number) r.get("itemId")).longValue() == item.getId()),
                "列表应含新规则");
    }

    @Test
    void duplicateItemTitleRejected() {
        String token = token();
        ItemEntity item = itemRepository.save(newItem("身体护理-" + System.nanoTime()));
        rest.exchange(url("/api/v1/commission/rule"), HttpMethod.POST,
                authEntity(Map.of("itemId", item.getId(), "title", "技师", "ratio", 20), token), Map.class).getBody();

        int status;
        try {
            rest.exchange(url("/api/v1/commission/rule"), HttpMethod.POST,
                    authEntity(Map.of("itemId", item.getId(), "title", "技师", "ratio", 30), token), Map.class);
            status = 200;
        } catch (HttpClientErrorException e) {
            status = e.getStatusCode().value();
        }
        assertEquals(400, status, "同项目同岗位重复建规则应 400（一岗一比例）");
    }

    @Test
    void ruleUpdateRatioAndDelete() {
        String token = token();
        ItemEntity item = itemRepository.save(newItem("中医美容-" + System.nanoTime()));
        Map<?, ?> created = rest.exchange(url("/api/v1/commission/rule"), HttpMethod.POST,
                authEntity(Map.of("itemId", item.getId(), "title", "美容师", "ratio", 15), token), Map.class).getBody();
        long id = ((Number) created.get("id")).longValue();

        Map<?, ?> updated = rest.exchange(url("/api/v1/commission/rule"), HttpMethod.PUT,
                authEntity(Map.of("id", id, "itemId", item.getId(), "title", "美容师", "ratio", 25), token), Map.class).getBody();
        assertEquals(25, ((Number) updated.get("ratio")).intValue(), "改比例应生效：" + updated);

        rest.exchange(url("/api/v1/commission/rule/" + id), HttpMethod.DELETE, authEntity(null, token), Map.class);
        assertFalse(listRules(token).stream().anyMatch(r -> ((Number) r.get("id")).longValue() == id),
                "删除后列表不应再含该规则");
    }

    private ItemEntity newItem(String name) {
        ItemEntity item = new ItemEntity();
        item.setName(name);
        item.setDescription("");
        item.setPrice(100);
        item.setEnabled(1);
        item.setSort(0);
        return item;
    }
}
