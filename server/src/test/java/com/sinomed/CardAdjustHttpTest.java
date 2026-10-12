package com.sinomed;

import com.sinomed.entity.CustomerEntity;
import com.sinomed.entity.ItemEntity;
import com.sinomed.repository.CustomerRepository;
import com.sinomed.repository.ItemRepository;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 疗程卡营销（赠次/转卡）HTTP 回归：赠次加 total/remaining 并落流水；转卡换归属顾客；
 * 守卫——停用卡/次数越界/目标顾客缺失/理由缺失均 400。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.datasource.url=jdbc:sqlite:target/card-adjust-http-test-${random.uuid}.db"})
class CardAdjustHttpTest {

    @LocalServerPort
    private int port;

    @Autowired
    private CustomerRepository customerRepository;

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

    private int statusOf(String path, HttpMethod method, Object body, String token) {
        try {
            return rest.exchange(url(path), method, authEntity(body, token), Map.class)
                    .getStatusCode().value();
        } catch (HttpStatusCodeException e) {
            return e.getStatusCode().value();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> cardListOf(Long customerId, String token) {
        return rest.exchange(url("/api/v1/card/list?customerId=" + customerId), HttpMethod.GET,
                authEntity(null, token), List.class).getBody();
    }

    @Test
    void adjustGiftsTimesAndLogs() {
        String token = token();
        CustomerEntity owner = new CustomerEntity();
        owner.setName("赠次顾客");
        owner.setPhone("13700022201");
        long ownerId = customerRepository.save(owner).getId();
        ItemEntity item = new ItemEntity();
        item.setName("艾灸次卡-" + System.nanoTime());
        item.setPrice(880);
        item.setDescription("");
        item.setEnabled(1);
        item.setSort(0);
        long itemId = itemRepository.save(item).getId();

        Map<?, ?> card = rest.exchange(url("/api/v1/card/"), HttpMethod.POST,
                authEntity(Map.of("customerId", ownerId, "itemId", itemId, "totalTimes", 10), token), Map.class)
                .getBody();
        long cardId = ((Number) card.get("id")).longValue();

        Map<?, ?> adjusted = rest.exchange(url("/api/v1/card/" + cardId + "/adjust"), HttpMethod.POST,
                authEntity(Map.of("times", 5, "reason", "老客回馈"), token), Map.class).getBody();
        assertEquals(15, ((Number) adjusted.get("totalTimes")).intValue(), "赠次应加总次数");
        assertEquals(15, ((Number) adjusted.get("remainingTimes")).intValue(), "赠次应加余次");

        List<Map<String, Object>> logs = rest.exchange(url("/api/v1/card/" + cardId + "/adjusts"),
                HttpMethod.GET, authEntity(null, token), List.class).getBody();
        assertEquals(1, logs.size());
        assertEquals(1, ((Number) logs.get(0).get("type")).intValue(), "应落赠次流水");
        assertEquals(5, ((Number) logs.get(0).get("times")).intValue());
        assertEquals("老客回馈", logs.get(0).get("reason"));
    }

    @Test
    void transferMovesCardToTargetCustomer() {
        String token = token();
        CustomerEntity from = new CustomerEntity();
        from.setName("转出顾客");
        from.setPhone("13700022202");
        long fromId = customerRepository.save(from).getId();
        CustomerEntity to = new CustomerEntity();
        to.setName("转入顾客");
        to.setPhone("13700022203");
        long toId = customerRepository.save(to).getId();
        ItemEntity item = new ItemEntity();
        item.setName("推拿次卡-" + System.nanoTime());
        item.setPrice(680);
        item.setDescription("");
        item.setEnabled(1);
        item.setSort(0);
        long itemId = itemRepository.save(item).getId();

        Map<?, ?> card = rest.exchange(url("/api/v1/card/"), HttpMethod.POST,
                authEntity(Map.of("customerId", fromId, "itemId", itemId, "totalTimes", 8), token), Map.class)
                .getBody();
        long cardId = ((Number) card.get("id")).longValue();

        Map<?, ?> moved = rest.exchange(url("/api/v1/card/" + cardId + "/transfer"), HttpMethod.POST,
                authEntity(Map.of("customerId", toId, "reason", "顾客转赠"), token), Map.class).getBody();
        assertEquals(toId, ((Number) moved.get("customerId")).longValue(), "归属应换到目标顾客");
        assertEquals(8, ((Number) moved.get("remainingTimes")).intValue(), "余次照旧");

        assertTrue(cardListOf(fromId, token).isEmpty(), "原顾客名下不应再有此卡");
        assertEquals(1, cardListOf(toId, token).size());

        List<Map<String, Object>> logs = rest.exchange(url("/api/v1/card/" + cardId + "/adjusts"),
                HttpMethod.GET, authEntity(null, token), List.class).getBody();
        assertEquals(2, ((Number) logs.get(0).get("type")).intValue(), "应落转卡流水");
        assertEquals(toId, ((Number) logs.get(0).get("customerId")).longValue());
        assertEquals("转入顾客", logs.get(0).get("customerName"), "流水应联出目标顾客名");
    }

    @Test
    void guardsRejectInvalidAdjustAndTransfer() {
        String token = token();
        CustomerEntity owner = new CustomerEntity();
        owner.setName("守卫顾客");
        owner.setPhone("13700022204");
        long ownerId = customerRepository.save(owner).getId();
        ItemEntity item = new ItemEntity();
        item.setName("守卫卡项-" + System.nanoTime());
        item.setPrice(100);
        item.setDescription("");
        item.setEnabled(1);
        item.setSort(0);
        long itemId = itemRepository.save(item).getId();
        Map<?, ?> card = rest.exchange(url("/api/v1/card/"), HttpMethod.POST,
                authEntity(Map.of("customerId", ownerId, "itemId", itemId, "totalTimes", 3), token), Map.class)
                .getBody();
        long cardId = ((Number) card.get("id")).longValue();

        // 次数越界 / 理由缺失
        assertEquals(400, statusOf("/api/v1/card/" + cardId + "/adjust", HttpMethod.POST,
                Map.of("times", 0, "reason", "x"), token));
        assertEquals(400, statusOf("/api/v1/card/" + cardId + "/adjust", HttpMethod.POST,
                Map.of("times", 5), token));
        // 停用卡不可操作（先停用）
        rest.exchange(url("/api/v1/card/" + cardId + "/status?status=0"), HttpMethod.PUT,
                authEntity(null, token), Map.class);
        assertEquals(400, statusOf("/api/v1/card/" + cardId + "/adjust", HttpMethod.POST,
                new HashMap<>(Map.of("times", 5, "reason", "停用后加次")), token));
        // 恢复后转卡：目标顾客不存在 / 转给自己
        rest.exchange(url("/api/v1/card/" + cardId + "/status?status=1"), HttpMethod.PUT,
                authEntity(null, token), Map.class);
        assertEquals(400, statusOf("/api/v1/card/" + cardId + "/transfer", HttpMethod.POST,
                Map.of("customerId", 987654, "reason", "幽灵顾客"), token));
        assertEquals(400, statusOf("/api/v1/card/" + cardId + "/transfer", HttpMethod.POST,
                Map.of("customerId", ownerId, "reason", "转给自己"), token));
    }
}
