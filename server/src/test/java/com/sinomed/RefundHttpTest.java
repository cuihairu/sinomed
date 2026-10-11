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
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 退费与日对账 HTTP 回归：退款 → 对账单读取 → 重复退款 400；
 * date 缺失/非法 400；未登录 401。走真实 HTTP 覆盖参数绑定层。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.datasource.url=jdbc:sqlite:target/refund-http-test-${random.uuid}.db"})
class RefundHttpTest {

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

    @SuppressWarnings("unchecked")
    private Map<String, Object> settle(Map<String, Object> order, String token) {
        return post("/api/v1/settlement/", token, Map.of("orderId", ((Number) order.get("id")).longValue(), "payType", 4), Map.class);
    }

    @Test
    void refundThenReconciliationThenGuards() {
        String token = token();

        // 备料：顾客 + 项目 + 订单 + 现金结算
        Long customerId = ((Number) post("/api/v1/customer/", token,
                Map.of("name", "退款网客", "gender", 1, "phone", "13900008001"), Map.class).get("id")).longValue();
        Long itemId = ((Number) post("/api/v1/item/", token,
                Map.of("name", "退款HTTP项目", "description", "", "price", 66, "enabled", 1, "sort", 0), Map.class).get("id")).longValue();
        Map<String, Object> order = post("/api/v1/order/", token,
                Map.of("customerId", customerId, "itemId", itemId, "status", 0, "price", 66), Map.class);
        Map<String, Object> settlement = settle(order, token);
        Long settlementId = ((Number) settlement.get("id")).longValue();

        // 退款（理由必填）
        Map<String, Object> refund = post("/api/v1/settlement/" + settlementId + "/refund", token,
                Map.of("reason", "收错款演示"), Map.class);
        assertEquals(settlementId, ((Number) refund.get("settlementId")).longValue());
        assertEquals(66, ((Number) refund.get("money")).intValue());
        assertEquals(4, ((Number) refund.get("payType")).intValue());
        assertEquals("收错款演示", refund.get("reason"));
        assertEquals("现金", refund.get("payTypeText"));
        assertEquals("退款网客", refund.get("customerName"), "退款单应联出顾客名");
        assertTrue(refund.get("staffName") == null || refund.get("staffName").toString().length() > 0);

        // 日对账单：今日 1 笔收款 66 元 + 1 笔退款 66 元，净额 0、现金净额 0
        String today = LocalDate.now().toString();
        Map<String, Object> report = get("/api/v1/settlement/reconciliation", token, Map.of("date", today), Map.class);
        assertEquals(today, report.get("date"));
        assertEquals(1, ((Number) report.get("incomeCount")).intValue());
        assertEquals(66, ((Number) report.get("incomeMoney")).intValue());
        assertEquals(1, ((Number) report.get("refundCount")).intValue());
        assertEquals(66, ((Number) report.get("refundMoney")).intValue());
        assertEquals(0, ((Number) report.get("netMoney")).intValue());
        assertEquals(0, ((Number) report.get("cashNetMoney")).intValue());
        java.util.List<Map<String, Object>> refunds = (java.util.List<Map<String, Object>>) report.get("refunds");
        assertEquals(1, refunds.size());
        assertEquals("退款网客", refunds.get(0).get("customerName"), "对账退款明细应联出顾客名");

        // 重复退款 400
        String dup = post4xx("/api/v1/settlement/" + settlementId + "/refund", token, Map.of("reason", "再退"));
        assertTrue(dup != null && dup.contains("该结算已退款"), "重复退款应 400：" + dup);

        // 理由空白 400
        Long settlement2 = ((Number) settle(post("/api/v1/order/", token,
                Map.of("customerId", customerId, "itemId", itemId, "status", 0, "price", 10), Map.class), token).get("id")).longValue();
        String blank = post4xx("/api/v1/settlement/" + settlement2 + "/refund", token, Map.of("reason", "  "));
        assertTrue(blank != null && blank.contains("退款理由不能为空"), "理由空白应 400：" + blank);

        // date 缺失/非法 400
        String missing = exchangeGet4xx("/api/v1/settlement/reconciliation", token);
        assertTrue(missing != null && missing.contains("success") && missing.contains("false"),
                "date 缺失应 400：" + missing);
        String illegal = exchangeGet4xx("/api/v1/settlement/reconciliation?date=abc", token);
        assertTrue(illegal != null && illegal.contains("对账日期格式无效"), "非法日期应 400：" + illegal);
    }

    @Test
    void unauthenticatedRejected() {
        boolean refundThrew = false;
        try {
            rest.exchange(url("/api/v1/settlement/1/refund"), HttpMethod.POST,
                    new HttpEntity<>(Map.of("reason", "x")), String.class);
        } catch (Exception e) {
            refundThrew = true;
        }
        assertTrue(refundThrew, "未登录退款应 401");

        boolean reportThrew = false;
        try {
            rest.exchange(url("/api/v1/settlement/reconciliation?date=" + LocalDate.now()),
                    HttpMethod.GET, HttpEntity.EMPTY, String.class);
        } catch (Exception e) {
            reportThrew = true;
        }
        assertTrue(reportThrew, "未登录对账应 401");
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
}
