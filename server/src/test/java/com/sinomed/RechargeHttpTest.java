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

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 储值 HTTP 回归：充值落正数流水 → 余额 = 流水合计（储值支付扣减为负）→ 流水分页联出顾客名；
 * 参数校验 400（缺顾客id / 金额非正 / 顾客不存在）、未登录 401。走真实 HTTP 覆盖参数绑定层。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.datasource.url=jdbc:sqlite:target/recharge-http-test-${random.uuid}.db"})
class RechargeHttpTest {

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

    /** 带 token 的 GET（中文参数安全编码） */
    private <T> T get(String path, String token, Map<String, String> params, Class<T> type) {
        var builder = org.springframework.web.util.UriComponentsBuilder.fromUriString(url(path));
        params.forEach(builder::queryParam);
        URI uri = builder.build().encode().toUri();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return rest.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), type).getBody();
    }

    /** 带 token 的 POST */
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

    private String exchangeGet4xx(String path, String token) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            return rest.exchange(url(path), HttpMethod.GET, new HttpEntity<>(headers), String.class).getBody();
        } catch (HttpStatusCodeException e) {
            return e.getResponseBodyAsString();
        }
    }

    @Test
    void rechargeBalancePageThenGuards() {
        String token = token();

        // 备料：顾客 + 充值 100
        Long customerId = ((Number) post("/api/v1/customer/", token,
                Map.of("name", "储值网客", "gender", 1, "phone", "13900009001"), Map.class).get("id")).longValue();
        Map<String, Object> recharge = post("/api/v1/recharge/", token,
                Map.of("customerId", customerId, "money", 100), Map.class);
        assertEquals(customerId, ((Number) recharge.get("customerId")).longValue());
        assertEquals(100, ((Number) recharge.get("money")).intValue());
        assertEquals("储值网客", recharge.get("customerName"), "充值流水应联出顾客名");

        // 余额 = 流水合计；储值支付 30 后扣减为负数流水，余额 100-30=70
        Map<String, Object> balance = get("/api/v1/recharge/balance", token,
                Map.of("customerId", String.valueOf(customerId)), Map.class);
        assertEquals(100L, ((Number) balance.get("balance")).longValue(), "无支付时余额即充值额");

        Long itemId = ((Number) post("/api/v1/item/", token,
                Map.of("name", "储值HTTP项目", "description", "", "price", 30, "enabled", 1, "sort", 0), Map.class).get("id")).longValue();
        Map<String, Object> order = post("/api/v1/order/", token,
                Map.of("customerId", customerId, "itemId", itemId, "status", 0, "price", 30), Map.class);
        post("/api/v1/settlement/", token,
                Map.of("orderId", ((Number) order.get("id")).longValue(), "payType", 1), Map.class);

        balance = get("/api/v1/recharge/balance", token,
                Map.of("customerId", String.valueOf(customerId)), Map.class);
        assertEquals(70L, ((Number) balance.get("balance")).longValue(), "余额 = 充值 - 储值支付");

        // 分页按顾客过滤：+100 充值与 -30 扣减两行，均联出顾客名
        Map<String, Object> page = get("/api/v1/recharge/page", token,
                Map.of("customerId", String.valueOf(customerId), "current", "1", "pageSize", "10"), Map.class);
        assertEquals(2, ((Number) page.get("total")).intValue());
        List<Map<String, Object>> rows = (List<Map<String, Object>>) page.get("data");
        assertEquals(2, rows.size());
        assertTrue(rows.stream().anyMatch(r -> Integer.valueOf(100).equals(r.get("money"))), "应含 +100 充值流水");
        assertTrue(rows.stream().anyMatch(r -> Integer.valueOf(-30).equals(r.get("money"))), "应含 -30 储值支付流水");
        assertTrue(rows.stream().allMatch(r -> "储值网客".equals(r.get("customerName"))), "流水行均应联出顾客名");

        // 参数校验 400
        assertTrue(post4xx("/api/v1/recharge/", token, Map.of("money", 10)).contains("顾客id不能为空"),
                "缺顾客id应 400");
        assertTrue(post4xx("/api/v1/recharge/", token, Map.of("customerId", customerId)).contains("充值金额必须大于 0"),
                "缺金额应 400");
        assertTrue(post4xx("/api/v1/recharge/", token, Map.of("customerId", customerId, "money", 0))
                        .contains("充值金额必须大于 0"), "金额 0 应 400");
        assertTrue(post4xx("/api/v1/recharge/", token, Map.of("customerId", customerId, "money", -5))
                        .contains("充值金额必须大于 0"), "负金额应 400");
        assertTrue(post4xx("/api/v1/recharge/", token, Map.of("customerId", 99999L, "money", 10))
                        .contains("顾客不存在"), "顾客不存在应 400");
        // 余额必填顾客id
        assertTrue(exchangeGet4xx("/api/v1/recharge/balance", token).contains("success"),
                "缺 customerId 应 400");
    }

    @Test
    void unauthenticatedRejected() {
        boolean rechargeThrew = false;
        try {
            rest.exchange(url("/api/v1/recharge/"), HttpMethod.POST,
                    new HttpEntity<>(Map.of("customerId", 1, "money", 10)), String.class);
        } catch (Exception e) {
            rechargeThrew = true;
        }
        assertTrue(rechargeThrew, "未登录充值应 401");

        boolean balanceThrew = false;
        try {
            rest.exchange(url("/api/v1/recharge/balance?customerId=1"), HttpMethod.GET,
                    HttpEntity.EMPTY, String.class);
        } catch (Exception e) {
            balanceThrew = true;
        }
        assertTrue(balanceThrew, "未登录查余额应 401");
    }
}
