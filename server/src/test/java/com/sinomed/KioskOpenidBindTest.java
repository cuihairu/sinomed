package com.sinomed;

import com.jayway.jsonpath.JsonPath;
import com.sinomed.entity.CardLookupLogEntity;
import com.sinomed.entity.CustomerEntity;
import com.sinomed.repository.CardLookupLogRepository;
import com.sinomed.repository.CustomerRepository;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * openid 自助绑定（openid-bind，openid_only 模式专用，2026-10-15 代决）：
 * openid + 手机号 + 一次性短信验证码自证后，把登录 openid 绑到该手机号对应的顾客档案；
 * 同档案重复绑定幂等，openid 已绑其他档案拒绝（不静默改绑）；每次尝试（含拒绝）落审计。
 * 绑定成功后 openid 查卡即通（此前 customers.openid 只能落库直写，此为唯一登记入口）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:sqlite:target/kiosk-openid-bind-test-${random.uuid}.db",
        "sinomed.phone-lookup.mode=openid_only"
})
class KioskOpenidBindTest {

    @LocalServerPort
    private int port;

    private final RestTemplate rest = new RestTemplate();

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private SmsCodeRepository smsCodeRepository;

    @Autowired
    private CardLookupLogRepository cardLookupLogRepository;

    private static Long customerId;
    private static final String PHONE = "13900006001";
    private static final String OPENID = "oSELF0001";

    @BeforeEach
    void seed() {
        if (customerId == null) {
            CustomerEntity customer = new CustomerEntity();
            customer.setName("自助绑定顾客");
            customer.setPhone(PHONE);
            customerId = customerRepository.save(customer).getId();
        }
        // 测试彼此独立：清码、清审计、清绑定
        smsCodeRepository.deleteAll();
        cardLookupLogRepository.deleteAll();
        CustomerEntity customer = customerRepository.findById(customerId).orElseThrow();
        customer.setOpenid(null);
        customerRepository.save(customer);
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
        return smsCodeRepository.findFirstByPhoneOrderByIdDesc(PHONE).orElseThrow().getCode();
    }

    @Test
    void bindSuccessThenOpenidLookupWorks() {
        String code = requestCode();
        var resp = post("/api/v1/kiosk/openid-bind",
                "{\"openid\":\"" + OPENID + "\",\"phone\":\"" + PHONE + "\",\"smsCode\":\"" + code + "\"}");
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertTrue(((String) JsonPath.read(resp.getBody(), "$.message")).contains("已绑定"));
        Long boundId = customerRepository.findByOpenid(OPENID).orElseThrow().getId();
        assertEquals(customerId.longValue(), boundId.longValue());

        // 绑定后 openid 查卡即通（未绑定时报错引导自助绑定的那句话就此闭环）
        var lookup = post("/api/v1/kiosk/card-lookup", "{\"openid\":\"" + OPENID + "\"}");
        assertEquals(HttpStatus.OK, lookup.getStatusCode());
        assertEquals(customerId.longValue(),
                ((Number) JsonPath.read(lookup.getBody(), "$.customerId")).longValue());
    }

    @Test
    void rebindSameCustomerIdempotent() {
        assertEquals(HttpStatus.OK, post("/api/v1/kiosk/openid-bind",
                "{\"openid\":\"" + OPENID + "\",\"phone\":\"" + PHONE + "\",\"smsCode\":\"" + requestCode() + "\"}")
                .getStatusCode());
        var second = post("/api/v1/kiosk/openid-bind",
                "{\"openid\":\"" + OPENID + "\",\"phone\":\"" + PHONE + "\",\"smsCode\":\"" + requestCode() + "\"}");
        assertEquals(HttpStatus.OK, second.getStatusCode());
        assertEquals("该微信已绑定此档案，无需重复绑定", JsonPath.read(second.getBody(), "$.message"));
        assertEquals(customerId.longValue(),
                customerRepository.findByOpenid(OPENID).orElseThrow().getId().longValue());
    }

    @Test
    void bindConflictsWhenOpenidBoundElsewhere() {
        CustomerEntity other = new CustomerEntity();
        other.setName("已被绑顾客");
        other.setPhone("13900006002");
        other.setOpenid(OPENID);
        customerRepository.save(other);

        String code = requestCode();
        var resp = post("/api/v1/kiosk/openid-bind",
                "{\"openid\":\"" + OPENID + "\",\"phone\":\"" + PHONE + "\",\"smsCode\":\"" + code + "\"}");
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals("该微信已绑定其他顾客档案，请到前台处理", JsonPath.read(resp.getBody(), "$.errorMessage"));
        // 原绑定不被静默改绑
        assertEquals(other.getId().longValue(),
                customerRepository.findByOpenid(OPENID).orElseThrow().getId().longValue());
    }

    @Test
    void wrongCodeRejected() {
        requestCode();
        var resp = post("/api/v1/kiosk/openid-bind",
                "{\"openid\":\"" + OPENID + "\",\"phone\":\"" + PHONE + "\",\"smsCode\":\"000000\"}");
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals("验证码不对，请核对后重试", JsonPath.read(resp.getBody(), "$.errorMessage"));
    }

    @Test
    void codeIsSingleUseForBinding() {
        String code = requestCode();
        assertEquals(HttpStatus.OK, post("/api/v1/kiosk/openid-bind",
                "{\"openid\":\"" + OPENID + "\",\"phone\":\"" + PHONE + "\",\"smsCode\":\"" + code + "\"}")
                .getStatusCode());
        var second = post("/api/v1/kiosk/openid-bind",
                "{\"openid\":\"" + OPENID + "\",\"phone\":\"" + PHONE + "\",\"smsCode\":\"" + code + "\"}");
        assertEquals(HttpStatus.BAD_REQUEST, second.getStatusCode());
        assertEquals("验证码已使用，请重新获取", JsonPath.read(second.getBody(), "$.errorMessage"));
    }

    @Test
    void noCustomerForPhoneRejected() {
        String lonelyPhone = "13900006999";
        assertEquals(HttpStatus.OK, post("/api/v1/kiosk/sms-code", "{\"phone\":\"" + lonelyPhone + "\"}")
                .getStatusCode());
        String code = smsCodeRepository.findFirstByPhoneOrderByIdDesc(lonelyPhone).orElseThrow().getCode();
        var resp = post("/api/v1/kiosk/openid-bind",
                "{\"openid\":\"" + OPENID + "\",\"phone\":\"" + lonelyPhone + "\",\"smsCode\":\"" + code + "\"}");
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals("该手机号无顾客档案，请到前台建档", JsonPath.read(resp.getBody(), "$.errorMessage"));
    }

    @Test
    void missingOpenidRejected() {
        String code = requestCode();
        var resp = post("/api/v1/kiosk/openid-bind",
                "{\"phone\":\"" + PHONE + "\",\"smsCode\":\"" + code + "\"}");
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals("需要 openid（登录态）才能绑定", JsonPath.read(resp.getBody(), "$.errorMessage"));
    }

    @Test
    void missingPhoneOrCodeRejected() {
        var resp = post("/api/v1/kiosk/openid-bind", "{\"openid\":\"" + OPENID + "\"}");
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals("需要手机号与短信验证码才能绑定微信", JsonPath.read(resp.getBody(), "$.errorMessage"));
    }

    @Test
    void badPhoneFormatRejected() {
        var resp = post("/api/v1/kiosk/openid-bind",
                "{\"openid\":\"" + OPENID + "\",\"phone\":\"12345\",\"smsCode\":\"123456\"}");
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals("手机号格式不对：应为 1 开头的 11 位数字", JsonPath.read(resp.getBody(), "$.errorMessage"));
    }

    @Test
    void attemptsLeaveAuditTrail() {
        String code = requestCode();
        assertEquals(HttpStatus.OK, post("/api/v1/kiosk/openid-bind",
                "{\"openid\":\"" + OPENID + "\",\"phone\":\"" + PHONE + "\",\"smsCode\":\"" + code + "\"}")
                .getStatusCode());
        // 再来一次冲突尝试：同一 openid 已绑本档案后再试别家手机号，走 no_customer 分支
        String lonelyPhone = "13900006998";
        assertEquals(HttpStatus.OK, post("/api/v1/kiosk/sms-code", "{\"phone\":\"" + lonelyPhone + "\"}")
                .getStatusCode());
        String lonelyCode = smsCodeRepository.findFirstByPhoneOrderByIdDesc(lonelyPhone).orElseThrow().getCode();
        post("/api/v1/kiosk/openid-bind",
                "{\"openid\":\"oSELF0002\",\"phone\":\"" + lonelyPhone + "\",\"smsCode\":\"" + lonelyCode + "\"}");

        List<CardLookupLogEntity> logs = cardLookupLogRepository.findAll();
        assertEquals(2, logs.size());
        CardLookupLogEntity okLog = logs.stream().filter(l -> "bind_ok".equals(l.getResult())).findFirst().orElseThrow();
        assertEquals(PHONE, okLog.getPhone());
        assertEquals(OPENID, okLog.getOperator());
        assertEquals("openid_only", okLog.getMode());
        assertEquals(customerId, okLog.getCustomerId());
        CardLookupLogEntity rejectLog = logs.stream().filter(l -> "no_customer".equals(l.getResult())).findFirst().orElseThrow();
        assertEquals(lonelyPhone, rejectLog.getPhone());
        assertEquals(null, rejectLog.getCustomerId());
    }
}
