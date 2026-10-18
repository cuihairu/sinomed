package com.sinomed;

import com.sinomed.entity.CustomerCardEntity;
import com.sinomed.entity.CustomerEntity;
import com.sinomed.entity.ItemEntity;
import com.sinomed.entity.StaffEntity;
import com.sinomed.repository.CustomerCardRepository;
import com.sinomed.repository.CustomerRepository;
import com.sinomed.repository.ItemRepository;
import com.sinomed.repository.StaffRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 提成记账 HTTP 回归（美容业态 c，docs/design/salon-extension.md §2-c）：
 * 结算按 order_items 行小计 × 规则比例落提成记录（次卡抵扣实收 0 也记——提成按业绩非实收）；
 * 无规则岗位不落；退单生成等额负向冲销（净额归零）；汇总接口按员工合计。
 * 业绩归属锚点：orders.staff_id；规则匹配：项目 × staffs.title 岗位。走真实 HTTP。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.datasource.url=jdbc:sqlite:target/commission-record-http-test-${random.uuid}.db"})
class CommissionRecordHttpTest {

    @LocalServerPort
    private int port;

    @Autowired
    private StaffRepository staffRepository;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private CustomerCardRepository customerCardRepository;
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

    /** tag 区分各测试数据：顾客/卡项/技师(title 技师)/医师(title 医师) */
    private Map<String, Object> seed(String tag) {
        StaffEntity tech = StaffEntity.getInstanceWithDefault(tag + "-tech");
        tech.setTitle("技师");
        tech = staffRepository.save(tech);
        StaffEntity doc = StaffEntity.getInstanceWithDefault(tag + "-doc");
        doc.setTitle("医师");
        doc = staffRepository.save(doc);

        CustomerEntity customer = new CustomerEntity();
        customer.setName(tag + "-顾客");
        customer = customerRepository.save(customer);

        ItemEntity item = new ItemEntity();
        item.setName(tag + "-卡项");
        item.setDescription("");
        item.setPrice(200);
        item.setEnabled(1);
        item.setSort(0);
        item = itemRepository.save(item);

        Map<String, Object> ids = new HashMap<>();
        ids.put("techId", tech.getId());
        ids.put("techName", tech.getName());
        ids.put("docId", doc.getId());
        ids.put("customerId", customer.getId());
        ids.put("itemId", item.getId());
        return ids;
    }

    private void createRule(String token, Map<String, Object> ids, String title, int ratio) {
        rest.exchange(url("/api/v1/commission/rule"), HttpMethod.POST,
                authEntity(Map.of("itemId", ids.get("itemId"), "title", title, "ratio", ratio), token), Map.class);
    }

    private Map<?, ?> createOrder(String token, Map<String, Object> ids, Long staffId, int qty) {
        return rest.exchange(url("/api/v1/order/"), HttpMethod.POST,
                authEntity(Map.of(
                        "customerId", ids.get("customerId"),
                        "staffId", staffId,
                        "items", List.of(Map.of("itemId", ids.get("itemId"), "qty", qty))), token), Map.class).getBody();
    }

    private Map<?, ?> settle(String token, long orderId, int payType) {
        return rest.exchange(url("/api/v1/settlement/"), HttpMethod.POST,
                authEntity(Map.of("orderId", orderId, "payType", payType), token), Map.class).getBody();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> records(String token, String query) {
        Map<?, ?> page = rest.exchange(url("/api/v1/commission/record/page" + query), HttpMethod.GET,
                authEntity(null, token), Map.class).getBody();
        return (List<Map<String, Object>>) (List<?>) page.get("data");
    }

    @Test
    void settleBooksCommissionPerOrderItemLine() {
        String token = token();
        Map<String, Object> ids = seed("cm-book-" + System.nanoTime());
        createRule(token, ids, "技师", 20);

        Map<?, ?> order = createOrder(token, ids, (Long) ids.get("techId"), 2); // 200 × 2 = 400 行小计
        long orderId = ((Number) order.get("id")).longValue();
        settle(token, orderId, 4); // 现金

        List<Map<String, Object>> rows = records(token, "?current=1&pageSize=50&orderId=" + orderId);
        assertEquals(1, rows.size(), "应按订单明细行落 1 条提成：" + rows);
        Map<String, Object> row = rows.get(0);
        assertEquals(400, ((Number) row.get("subtotal")).intValue(), "快照行小计 400：" + row);
        assertEquals(20, ((Number) row.get("ratio")).intValue(), "快照比例 20：" + row);
        assertEquals(80, ((Number) row.get("amount")).intValue(), "提成 400×20% = 80：" + row);
        assertEquals(ids.get("techName"), row.get("staffName"), "联出员工名（业绩归属=订单建单人）：" + row);
    }

    @Test
    void noRuleTitleNoRecord() {
        String token = token();
        Map<String, Object> ids = seed("cm-norule-" + System.nanoTime());
        createRule(token, ids, "技师", 20); // 只配了技师，医师无规则

        Map<?, ?> order = createOrder(token, ids, (Long) ids.get("docId"), 1);
        long orderId = ((Number) order.get("id")).longValue();
        settle(token, orderId, 4);

        List<Map<String, Object>> rows = records(token, "?current=1&pageSize=50&orderId=" + orderId);
        assertTrue(rows.isEmpty(), "岗位无规则不应落提成记录：" + rows);
    }

    @Test
    void cardDeductSettleStillBooks() {
        String token = token();
        Map<String, Object> ids = seed("cm-card-" + System.nanoTime());
        createRule(token, ids, "技师", 20);

        // 直接落一张有效余次卡，走次卡抵扣结算（实收 0）
        CustomerCardEntity card = new CustomerCardEntity();
        card.setCustomerId((Long) ids.get("customerId"));
        card.setItemId((Long) ids.get("itemId"));
        card.setTotalTimes(10);
        card.setRemainingTimes(10);
        card.setStatus(1); // 实体无 @DynamicInsert，直存须显式置位
        card.setValidUntil(Date.from(java.time.LocalDate.now().plusDays(1)
                .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()));
        customerCardRepository.save(card);

        Map<?, ?> order = createOrder(token, ids, (Long) ids.get("techId"), 1);
        long orderId = ((Number) order.get("id")).longValue();
        settle(token, orderId, 5); // 次卡抵扣，实收 0

        List<Map<String, Object>> rows = records(token, "?current=1&pageSize=50&orderId=" + orderId);
        assertEquals(1, rows.size(), "次卡服务也应有提成（按业绩不按实收）：" + rows);
        assertEquals(40, ((Number) rows.get(0).get("amount")).intValue(),
                "提成按行小计 200×20% = 40，与实收 0 无关：" + rows.get(0));
    }

    @Test
    void refundReversesCommission() {
        String token = token();
        Map<String, Object> ids = seed("cm-refund-" + System.nanoTime());
        createRule(token, ids, "技师", 20);

        Map<?, ?> order = createOrder(token, ids, (Long) ids.get("techId"), 2); // 小计 400
        long orderId = ((Number) order.get("id")).longValue();
        Map<?, ?> settlement = settle(token, orderId, 4);
        long settlementId = ((Number) settlement.get("id")).longValue();

        List<Map<String, Object>> before = records(token, "?current=1&pageSize=50&orderId=" + orderId);
        assertEquals(1, before.size(), "结算后 1 条正向：" + before);

        rest.exchange(url("/api/v1/settlement/" + settlementId + "/refund"), HttpMethod.POST,
                authEntity(Map.of("reason", "顾客整单退"), token), Map.class);

        List<Map<String, Object>> after = records(token, "?current=1&pageSize=50&orderId=" + orderId);
        assertEquals(2, after.size(), "退单后应有 1 条负向冲销：" + after);
        int net = after.stream().mapToInt(r -> ((Number) r.get("amount")).intValue()).sum();
        assertEquals(0, net, "冲销后净额归零：" + after);
        assertTrue(after.stream().anyMatch(r -> ((Number) r.get("amount")).intValue() == -80), "负向 -80：" + after);
    }

    @Test
    @SuppressWarnings("unchecked")
    void summaryAggregatesByStaff() {
        String token = token();
        Map<String, Object> ids = seed("cm-sum-" + System.nanoTime());
        createRule(token, ids, "技师", 20);
        long techId = (Long) ids.get("techId");

        for (int i = 0; i < 2; i++) {
            Map<?, ?> order = createOrder(token, ids, techId, 1); // 各 40
            settle(token, ((Number) order.get("id")).longValue(), 4);
        }

        List<Map<String, Object>> summary = (List<Map<String, Object>>) (List<?>) rest.exchange(
                url("/api/v1/commission/record/summary"), HttpMethod.GET, authEntity(null, token), List.class).getBody();
        Map<String, Object> mine = summary.stream()
                .filter(s -> ((Number) s.get("staffId")).longValue() == techId)
                .findFirst().orElseThrow();
        assertEquals(80, ((Number) mine.get("total")).intValue(), "两单合计 40×2=80：" + mine);
        assertTrue(mine.get("staffName") != null, "汇总应联员工名：" + mine);
    }
}
