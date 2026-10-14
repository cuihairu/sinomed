package com.sinomed;

import com.jayway.jsonpath.JsonPath;
import com.sinomed.entity.CardLookupLogEntity;
import com.sinomed.entity.CustomerCardEntity;
import com.sinomed.entity.CustomerEntity;
import com.sinomed.entity.ItemEntity;
import com.sinomed.entity.SmsCodeEntity;
import com.sinomed.repository.CardLookupLogRepository;
import com.sinomed.repository.CustomerCardRepository;
import com.sinomed.repository.CustomerRepository;
import com.sinomed.repository.ItemRepository;
import com.sinomed.repository.SmsCodeRepository;
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
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * phone-lookup sms_verified 模式：手机号+验证码双因子查卡——验证码 5 分钟有效、一次性；
 * 通过后按手机号查顾客（查无即拒绝）。成功与拒绝都落审计。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:sqlite:target/card-lookup-sms-test-${random.uuid}.db",
        "sinomed.phone-lookup.mode=sms_verified"
})
class CardLookupSmsTest {

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
    private SmsCodeRepository smsCodeRepository;

    @Autowired
    private CardLookupLogRepository cardLookupLogRepository;

    private static Long itemId;
    private static Long customerId;
    private static final String PHONE = "13900005001";

    @BeforeEach
    void seed() {
        if (itemId == null) {
            ItemEntity item = new ItemEntity();
            item.setName("sms 查卡测试项");
            item.setDescription("");
            item.setPrice(199);
            item.setEnabled(1);
            item.setSort(0);
            itemId = itemRepository.save(item).getId();

            CustomerEntity customer = new CustomerEntity();
            customer.setName("sms 顾客");
            customer.setPhone(PHONE);
            customerId = customerRepository.save(customer).getId();

            CustomerCardEntity card = new CustomerCardEntity();
            card.setCustomerId(customerId);
            card.setItemId(itemId);
            card.setTotalTimes(5);
            card.setRemainingTimes(3);
            card.setStatus(1);
            customerCardRepository.save(card);
        }
        smsCodeRepository.deleteAll();
        cardLookupLogRepository.deleteAll();
    }

    private ResponseEntity<String> post(String path, String body) {
        var req = RequestEntity.post(URI.create("http://localhost:" + port + path))
                .contentType(MediaType.APPLICATION_JSON).body(body);
        try {
            return rest.exchange(req, String.class);
        } catch (HttpClientErrorException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        }
    }

    /** 发码并取回服务端生成的码（本系统无短信网关，测试从库读码） */
    private String requestCode() {
        var resp = post("/api/v1/kiosk/sms-code", "{\"phone\":\"" + PHONE + "\"}");
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        SmsCodeEntity issued = smsCodeRepository.findFirstByPhoneOrderByIdDesc(PHONE).orElseThrow();
        return issued.getCode();
    }

    @Test
    void smsCodeIssuedOnce() {
        String code = requestCode();
        assertEquals(6, code.length());
        assertTrue(issuedNotUsed());
    }

    @Test
    void phonePlusValidCodeReturnsCards() {
        String code = requestCode();
        var resp = post("/api/v1/kiosk/card-lookup", "{\"phone\":\"" + PHONE + "\",\"smsCode\":\"" + code + "\"}");
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(customerId.longValue(), ((Number) JsonPath.read(resp.getBody(), "$.customerId")).longValue());
        assertEquals("sms 查卡测试项", JsonPath.read(resp.getBody(), "$.cards[0].itemName"));
        assertEquals(3, ((Number) JsonPath.read(resp.getBody(), "$.cards[0].remainingTimes")).intValue());
    }

    @Test
    void wrongCodeRejected() {
        requestCode();
        var resp = post("/api/v1/kiosk/card-lookup", "{\"phone\":\"" + PHONE + "\",\"smsCode\":\"000000\"}");
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals("验证码不对，请核对后重试", JsonPath.read(resp.getBody(), "$.errorMessage"));
    }

    @Test
    void missingCodeRejected() {
        var resp = post("/api/v1/kiosk/card-lookup", "{\"phone\":\"" + PHONE + "\"}");
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals("需要手机号与短信验证码才能查卡", JsonPath.read(resp.getBody(), "$.errorMessage"));
    }

    @Test
    void noCodeIssuedRejected() {
        var resp = post("/api/v1/kiosk/card-lookup", "{\"phone\":\"" + PHONE + "\",\"smsCode\":\"123456\"}");
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals("验证码不存在，请先获取验证码", JsonPath.read(resp.getBody(), "$.errorMessage"));
    }

    @Test
    void expiredCodeRejected() {
        requestCode();
        SmsCodeEntity issued = smsCodeRepository.findFirstByPhoneOrderByIdDesc(PHONE).orElseThrow();
        issued.setExpiresAt(new Date(System.currentTimeMillis() - 60_000L));
        smsCodeRepository.save(issued);
        var resp = post("/api/v1/kiosk/card-lookup",
                "{\"phone\":\"" + PHONE + "\",\"smsCode\":\"" + issued.getCode() + "\"}");
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals("验证码已过期，请重新获取", JsonPath.read(resp.getBody(), "$.errorMessage"));
    }

    @Test
    void codeIsSingleUse() {
        String code = requestCode();
        assertEquals(HttpStatus.OK,
                post("/api/v1/kiosk/card-lookup", "{\"phone\":\"" + PHONE + "\",\"smsCode\":\"" + code + "\"}").getStatusCode());
        var second = post("/api/v1/kiosk/card-lookup", "{\"phone\":\"" + PHONE + "\",\"smsCode\":\"" + code + "\"}");
        assertEquals(HttpStatus.BAD_REQUEST, second.getStatusCode());
        assertEquals("验证码已使用，请重新获取", JsonPath.read(second.getBody(), "$.errorMessage"));
    }

    @Test
    void phoneWithoutCustomerProfileRejected() {
        String lonelyPhone = "13900005999";
        var issuedResp = post("/api/v1/kiosk/sms-code", "{\"phone\":\"" + lonelyPhone + "\"}");
        assertEquals(HttpStatus.OK, issuedResp.getStatusCode());
        String code = smsCodeRepository.findFirstByPhoneOrderByIdDesc(lonelyPhone).orElseThrow().getCode();
        var resp = post("/api/v1/kiosk/card-lookup", "{\"phone\":\"" + lonelyPhone + "\",\"smsCode\":\"" + code + "\"}");
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals("该手机号无顾客档案，请到前台建档", JsonPath.read(resp.getBody(), "$.errorMessage"));
    }

    @Test
    void successAndRejectsLeaveAuditLog() {
        String code = requestCode();
        assertEquals(HttpStatus.OK,
                post("/api/v1/kiosk/card-lookup", "{\"phone\":\"" + PHONE + "\",\"smsCode\":\"" + code + "\"}").getStatusCode());
        requestCode();
        post("/api/v1/kiosk/card-lookup", "{\"phone\":\"" + PHONE + "\",\"smsCode\":\"000000\"}");
        List<CardLookupLogEntity> logs = cardLookupLogRepository.findAll();
        assertEquals(2, logs.size());
        CardLookupLogEntity okLog = logs.stream().filter(l -> "ok".equals(l.getResult())).findFirst().orElseThrow();
        assertEquals(PHONE, okLog.getOperator());
        assertEquals(PHONE, okLog.getPhone());
        assertEquals("sms_verified", okLog.getMode());
        assertEquals(customerId, okLog.getCustomerId());
        CardLookupLogEntity rejectLog = logs.stream().filter(l -> "bad_code".equals(l.getResult())).findFirst().orElseThrow();
        assertEquals(PHONE, rejectLog.getOperator());
    }

    private boolean issuedNotUsed() {
        SmsCodeEntity issued = smsCodeRepository.findFirstByPhoneOrderByIdDesc(PHONE).orElseThrow();
        return issued.getUsed() == 0;
    }
}
