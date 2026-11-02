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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.ArrayList;
import java.util.List;
import java.util.Date;

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
    void attemptsInitializedToZeroOnIssue() {
        requestCode();
        SmsCodeEntity issued = smsCodeRepository.findFirstByPhoneOrderByIdDesc(PHONE).orElseThrow();
        assertEquals(0, issued.getAttempts(), "发码应显式初始化 attempts=0（不再靠列默认/读取端容错）");
    }

    @Test
    void atomicIncrementBurnsExactlyAtMax() {
        SmsCodeEntity issued = new SmsCodeEntity();
        issued.setPhone(PHONE);
        issued.setCode("121212");
        issued.setUsed(0);
        issued.setAttempts(0);
        issued.setExpiresAt(new Date(System.currentTimeMillis() + 300_000L));
        SmsCodeEntity saved = smsCodeRepository.save(issued);
        // 前 4 次错试只累计不烧码；第 5 次原子烧码（单条 UPDATE，并发不丢增量/不绕上限）
        for (int i = 1; i <= 4; i++) {
            assertEquals(1, smsCodeRepository.incrementAttempts(saved.getId(), 5), "应更新 1 行");
            SmsCodeEntity cur = smsCodeRepository.findFirstByPhoneOrderByIdDesc(PHONE).orElseThrow();
            assertEquals(i, cur.getAttempts());
            assertEquals(0, cur.getUsed(), "未达上限不应烧码");
        }
        assertEquals(1, smsCodeRepository.incrementAttempts(saved.getId(), 5), "应更新 1 行");
        SmsCodeEntity cur = smsCodeRepository.findFirstByPhoneOrderByIdDesc(PHONE).orElseThrow();
        assertEquals(5, cur.getAttempts());
        assertEquals(1, cur.getUsed(), "第 5 次错试应烧码");
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
    void codeBurnedAfterRepeatedWrongAttempts() {
        String code = requestCode();
        // 连续 5 次错试：每次均 400，且不因错试而提前放行
        for (int i = 0; i < 5; i++) {
            var resp = post("/api/v1/kiosk/card-lookup", "{\"phone\":\"" + PHONE + "\",\"smsCode\":\"000000\"}");
            assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
            assertEquals("验证码不对，请核对后重试", JsonPath.read(resp.getBody(), "$.errorMessage"));
        }
        // 第 5 次错试后验证码已烧：正确码也必须重新获取（防爆破契约锁定）
        var resp = post("/api/v1/kiosk/card-lookup", "{\"phone\":\"" + PHONE + "\",\"smsCode\":\"" + code + "\"}");
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals("验证码已使用，请重新获取", JsonPath.read(resp.getBody(), "$.errorMessage"));
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
    void concurrentCorrectCodeConsumedOnlyOnce() throws Exception {
        // 正确码并发双投递：原子烧码（markUsed 单条 UPDATE）只放行一个，另一个读到已用 → 400。
        // 这是「一次性」语义的竞态加固——read-modify-write（快检 setUsed+save）会双双放行。
        String code = requestCode();
        int threads = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<ResponseEntity<String>>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return post("/api/v1/kiosk/card-lookup", "{\"phone\":\"" + PHONE + "\",\"smsCode\":\"" + code + "\"}");
                }));
            }
            start.countDown();
            int ok = 0;
            int used = 0;
            for (Future<ResponseEntity<String>> f : futures) {
                ResponseEntity<String> r = f.get();
                if (r.getStatusCode() == HttpStatus.OK) {
                    ok++;
                } else if (r.getStatusCode() == HttpStatus.BAD_REQUEST
                        && "验证码已使用，请重新获取".equals(JsonPath.read(r.getBody(), "$.errorMessage"))) {
                    used++;
                }
            }
            assertEquals(1, ok, "并发正确码应恰一次成功");
            assertEquals(1, used, "并发正确码应恰一次已用拒绝");
        } finally {
            pool.shutdownNow();
        }
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
    void openidBindRejectedInSmsMode() {
        String code = requestCode();
        var resp = post("/api/v1/kiosk/openid-bind",
                "{\"openid\":\"oSMS001\",\"phone\":\"" + PHONE + "\",\"smsCode\":\"" + code + "\"}");
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals("当前查卡方式不支持微信绑定，请走手机号验证码查询",
                JsonPath.read(resp.getBody(), "$.errorMessage"));
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
