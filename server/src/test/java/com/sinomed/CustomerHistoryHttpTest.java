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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 顾客病史（过敏史/既往史）HTTP 回归：建档 → 记过敏/既往两条 → 列表新记录在前 → 删一条，
 * 以及顾客不存在、类型无效、内容为空/过长、未知记录、未登录 401。
 * 走真实 HTTP（路径变量与参数绑定层只有真请求才覆盖得到）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.datasource.url=jdbc:sqlite:target/customer-history-http-test-${random.uuid}.db"})
class CustomerHistoryHttpTest {

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

    /** 预期 4xx 的请求：返回服务端错误体原文（RestTemplate 对 4xx 抛异常，这里解包） */
    private String exchange4xx(String method, String path, Object body, String token) {
        try {
            return rest.exchange(url(path), HttpMethod.valueOf(method), authEntity(body, token), String.class).getBody();
        } catch (HttpStatusCodeException e) {
            return e.getResponseBodyAsString();
        }
    }

    private Map<?, ?> createCustomer(String token, String name, String phone) {
        return rest.exchange(url("/api/v1/customer/"), HttpMethod.POST,
                authEntity(Map.of("name", name, "gender", 1, "phone", phone), token), Map.class).getBody();
    }

    private Map<?, ?> addHistory(String token, Object customerId, Object type, String content) {
        return rest.exchange(url("/api/v1/customer/" + customerId + "/history"), HttpMethod.POST,
                authEntity(Map.of("type", type, "content", content), token), Map.class).getBody();
    }

    private List<?> listHistories(String token, Object customerId) {
        return rest.exchange(url("/api/v1/customer/" + customerId + "/history"), HttpMethod.GET,
                authEntity(null, token), List.class).getBody();
    }

    @Test
    void historyFlowsRecordListAndDelete() {
        String token = token();

        Map<?, ?> customer = createCustomer(token, "病史顾客", "13900009101");
        assertTrue(customer != null && customer.get("id") != null, "建档应返回 id：" + customer);
        Object customerId = customer.get("id");

        Map<?, ?> allergy = addHistory(token, customerId, 0, "头孢类抗生素过敏，皮试阳性");
        assertTrue(allergy != null && allergy.get("id") != null, "记过敏应返回 id：" + allergy);
        assertEquals(0, allergy.get("type"));
        assertEquals("头孢类抗生素过敏，皮试阳性", allergy.get("content"));

        Map<?, ?> past = addHistory(token, customerId, 1, "慢性胃炎 5 年，规律复查");
        assertTrue(past != null && past.get("id") != null, "记既往应返回 id：" + past);
        assertEquals(1, past.get("type"));

        List<?> rows = listHistories(token, customerId);
        assertTrue(rows != null && rows.size() == 2, "应有两条病史：" + rows);
        assertEquals(past.get("id"), ((Map<?, ?>) rows.get(0)).get("id"), "新记录在前：应先看到既往史");
        assertEquals(0, ((Map<?, ?>) rows.get(1)).get("type"));
        assertTrue(((Map<?, ?>) rows.get(0)).get("createTime") != null, "病史应带时间");

        String deleted = exchange4xx("DELETE", "/api/v1/customer/history/" + past.get("id"), null, token);
        assertTrue(deleted != null && deleted.contains("删除成功"), "删除应成功：" + deleted);
        List<?> remaining = listHistories(token, customerId);
        assertTrue(remaining != null && remaining.size() == 1, "删除后应剩 1 条：" + remaining);
        assertEquals(allergy.get("id"), ((Map<?, ?>) remaining.get(0)).get("id"));
    }

    @Test
    void historyGuards() {
        String token = token();

        // 不存在的顾客：记病史与查病史都应 400
        String noCustAdd = exchange4xx("POST", "/api/v1/customer/99999/history",
                Map.of("type", 0, "content", "青霉素过敏"), token);
        assertTrue(noCustAdd != null && noCustAdd.contains("顾客不存在"), "未知顾客记病史应 400：" + noCustAdd);
        String noCustList = exchange4xx("GET", "/api/v1/customer/99999/history", null, token);
        assertTrue(noCustList != null && noCustList.contains("顾客不存在"), "未知顾客查病史应 400：" + noCustList);

        Map<?, ?> customer = createCustomer(token, "守卫顾客", "13900009102");
        Object customerId = customer.get("id");

        // 类型无效 400（2 护理方案已合法，改用白名单外的 3）；内容为空 400；内容过长 400
        String badType = exchange4xx("POST", "/api/v1/customer/" + customerId + "/history",
                Map.of("type", 3, "content", "青霉素过敏"), token);
        assertTrue(badType != null && badType.contains("病史类型无效"), "类型无效应 400：" + badType);
        String blank = exchange4xx("POST", "/api/v1/customer/" + customerId + "/history",
                Map.of("type", 0, "content", "   "), token);
        assertTrue(blank != null && blank.contains("病史内容不能为空"), "内容空白应 400：" + blank);
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 210; i++) {
            longText.append("药");
        }
        String tooLong = exchange4xx("POST", "/api/v1/customer/" + customerId + "/history",
                Map.of("type", 1, "content", longText.toString()), token);
        assertTrue(tooLong != null && tooLong.contains("病史内容过长"), "内容过长应 400：" + tooLong);

        // 校验失败不得落库
        assertTrue(listHistories(token, customerId).isEmpty(), "守卫校验后不应落任何病史");

        // 内容首尾空白应 trim 后入库
        Map<?, ?> trimmed = addHistory(token, customerId, 1, "  高血压 6 年  ");
        assertEquals("高血压 6 年", trimmed.get("content"), "内容应 trim 后保存：" + trimmed);

        // 删除未知记录 400
        String gone = exchange4xx("DELETE", "/api/v1/customer/history/99999", null, token);
        assertTrue(gone != null && gone.contains("病史记录不存在"), "未知记录删除应 400：" + gone);

        // 未登录 401（RestTemplate 对 4xx 抛异常）
        boolean threw = false;
        try {
            rest.exchange(url("/api/v1/customer/" + customerId + "/history"), HttpMethod.GET, HttpEntity.EMPTY, List.class);
        } catch (Exception e) {
            threw = true;
        }
        assertTrue(threw, "未登录应 401");
    }
}
