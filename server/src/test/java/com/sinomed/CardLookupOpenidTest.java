package com.sinomed;

import com.jayway.jsonpath.JsonPath;
import com.sinomed.entity.CardLookupLogEntity;
import com.sinomed.entity.CustomerCardEntity;
import com.sinomed.entity.CustomerEntity;
import com.sinomed.entity.ItemEntity;
import com.sinomed.repository.CardLookupLogRepository;
import com.sinomed.repository.CustomerCardRepository;
import com.sinomed.repository.CustomerRepository;
import com.sinomed.repository.ItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * phone-lookup openid_only 模式：openid 绑定查卡——登录态天然带 openid，按 openid 查绑定顾客；
 * 缺 openid / 未绑定都拒绝，不回退裸手机号；成功与拒绝都落审计。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:sqlite:target/card-lookup-openid-test-${random.uuid}.db",
        "sinomed.phone-lookup.mode=openid_only"
})
class CardLookupOpenidTest {

    @LocalServerPort
    private int port;

    private final RestTemplate rest = new RestTemplate();

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private CustomerCardRepository customerCardRepository;

    @Autowired
    private CardLookupLogRepository cardLookupLogRepository;

    private static Long itemId;
    private static Long boundCustomerId;
    private static final String OPENID = "oBOUND001";

    @BeforeEach
    void seed() {
        if (itemId == null) {
            ItemEntity item = new ItemEntity();
            item.setName("openid 查卡测试项");
            item.setDescription("");
            item.setPrice(299);
            item.setEnabled(1);
            item.setSort(0);
            itemId = itemRepository.save(item).getId();

            CustomerEntity customer = new CustomerEntity();
            customer.setName("openid 顾客");
            customer.setPhone("13900004001");
            customer.setOpenid(OPENID);
            boundCustomerId = customerRepository.save(customer).getId();

            CustomerCardEntity card = new CustomerCardEntity();
            card.setCustomerId(boundCustomerId);
            card.setItemId(itemId);
            card.setTotalTimes(10);
            card.setRemainingTimes(7);
            card.setStatus(1);
            customerCardRepository.save(card);
        }
        cardLookupLogRepository.deleteAll();
    }

    private ResponseEntity<String> lookup(String body) {
        var req = RequestEntity.post(URI.create("http://localhost:" + port + "/api/v1/kiosk/card-lookup"))
                .contentType(MediaType.APPLICATION_JSON).body(body);
        try {
            return rest.exchange(req, String.class);
        } catch (HttpClientErrorException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        }
    }

    @Test
    void openidPathReturnsBoundCards() {
        var resp = lookup("{\"openid\":\"" + OPENID + "\"}");
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(boundCustomerId.longValue(), ((Number) JsonPath.read(resp.getBody(), "$.customerId")).longValue());
        assertEquals("openid 顾客", JsonPath.read(resp.getBody(), "$.customerName"));
        List<Object> cards = JsonPath.read(resp.getBody(), "$.cards");
        assertEquals(1, cards.size());
        assertEquals("openid 查卡测试项", JsonPath.read(resp.getBody(), "$.cards[0].itemName"));
        assertEquals(10, ((Number) JsonPath.read(resp.getBody(), "$.cards[0].totalTimes")).intValue());
        assertEquals(7, ((Number) JsonPath.read(resp.getBody(), "$.cards[0].remainingTimes")).intValue());
    }

    @Test
    void missingOpenidRejected() {
        var resp = lookup("{\"phone\":\"13900004001\"}");
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals("需要 openid（登录态）才能查卡", JsonPath.read(resp.getBody(), "$.errorMessage"));
    }

    @Test
    void unboundOpenidRejected() {
        var resp = lookup("{\"openid\":\"oUNKNOWN\"}");
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals("该 openid 未绑定顾客档案，请先完成微信绑定", JsonPath.read(resp.getBody(), "$.errorMessage"));
    }

    @Test
    void smsCodeIssuedInOpenidModeForBinding() {
        var req = RequestEntity.post(URI.create("http://localhost:" + port + "/api/v1/kiosk/sms-code"))
                .contentType(MediaType.APPLICATION_JSON).body("{\"phone\":\"13900004001\"}");
        ResponseEntity<String> resp;
        try {
            resp = rest.exchange(req, String.class);
        } catch (HttpClientErrorException e) {
            resp = ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        }
        // openid_only 下验证码供自助绑定用（查卡仍只走 openid），发码放行
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals("验证码已发送（5 分钟内有效，演示环境请向店员索取）",
                JsonPath.read(resp.getBody(), "$.message"));
    }

    @Test
    void myAppointmentsRejectedInOpenidMode() {
        var req = RequestEntity.post(URI.create("http://localhost:" + port + "/api/v1/kiosk/my-appointments"))
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"phone\":\"13900004001\",\"smsCode\":\"123456\"}");
        ResponseEntity<String> resp;
        try {
            resp = rest.exchange(req, String.class);
        } catch (HttpClientErrorException e) {
            resp = ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        }
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals("当前查卡方式不支持预约查询，请走手机号验证码",
                JsonPath.read(resp.getBody(), "$.errorMessage"));
    }

    @Test
    void successAndRejectBothLeaveAuditLog() {
        lookup("{\"openid\":\"" + OPENID + "\"}");
        lookup("{\"openid\":\"oUNKNOWN\"}");
        List<CardLookupLogEntity> logs = cardLookupLogRepository.findAll();
        assertEquals(2, logs.size());
        CardLookupLogEntity okLog = logs.stream().filter(l -> "ok".equals(l.getResult())).findFirst().orElseThrow();
        assertEquals(OPENID, okLog.getOperator());
        assertEquals("openid_only", okLog.getMode());
        assertEquals(boundCustomerId, okLog.getCustomerId());
        assertEquals("13900004001", okLog.getPhone());
        CardLookupLogEntity rejectLog = logs.stream().filter(l -> "no_binding".equals(l.getResult())).findFirst().orElseThrow();
        assertEquals("oUNKNOWN", rejectLog.getOperator());
        assertNull(rejectLog.getCustomerId());
    }
}
