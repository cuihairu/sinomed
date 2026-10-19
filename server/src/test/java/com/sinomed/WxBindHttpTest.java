package com.sinomed;

import com.sinomed.entity.CustomerEntity;
import com.sinomed.entity.SmsCodeEntity;
import com.sinomed.entity.WxAccountEntity;
import com.sinomed.entity.WxBindingLogEntity;
import com.sinomed.entity.WxFanEntity;
import com.sinomed.repository.CustomerRepository;
import com.sinomed.repository.SmsCodeRepository;
import com.sinomed.repository.WxAccountRepository;
import com.sinomed.repository.WxBindingLogRepository;
import com.sinomed.repository.WxFanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 粉丝↔顾客绑定 HTTP 回归（设计稿第一期交付物 5，§4.7/§4.8）：H5 粉丝 openid + 手机号 +
 * 一次性短信验证码双因子（复用 Kiosk 口径与 sms_codes 通道桩），成功写 wx_fans.customer_id，
 * 每次尝试（含拒绝）落 wx_binding_logs 审计；管理端解绑清绑 + 审计；同档案幂等、他档案/他粉丝拒绝。
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:sqlite:target/wx-bind-test-${random.uuid}.db",
        "sinomed.official-account.enabled=true"
})
class WxBindHttpTest {

    @LocalServerPort
    private int port;

    private final RestTemplate rest = new RestTemplate();

    @Autowired
    private WxAccountRepository accountRepository;
    @Autowired
    private WxFanRepository fanRepository;
    @Autowired
    private WxBindingLogRepository bindingLogRepository;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private SmsCodeRepository smsCodeRepository;

    private static final String PHONE = "13900007001";
    private static final String OTHER_PHONE = "13900007002";
    private static final String OPENID = "oFANBIND1";

    private Long accountId;
    private Long customerId;
    private Long otherCustomerId;

    @BeforeEach
    void seed() {
        bindingLogRepository.deleteAll();
        fanRepository.deleteAll();
        smsCodeRepository.deleteAll();
        accountRepository.deleteAll();
        customerRepository.deleteAll();

        WxAccountEntity account = new WxAccountEntity();
        account.setAppId("wxOABIND" + System.nanoTime());
        account.setAccountType("service");
        account.setVerified(1);
        accountId = accountRepository.save(account).getId();

        CustomerEntity customer = new CustomerEntity();
        customer.setName("绑定顾客甲");
        customer.setPhone(PHONE);
        customerId = customerRepository.save(customer).getId();

        CustomerEntity other = new CustomerEntity();
        other.setName("绑定顾客乙");
        other.setPhone(OTHER_PHONE);
        otherCustomerId = customerRepository.save(other).getId();
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
    private String requestCode(String phone) {
        ResponseEntity<String> resp = post("/api/v1/wx/bind/" + accountId + "/code",
                "{\"phone\":\"" + phone + "\"}");
        assertEquals(HttpStatus.OK, resp.getStatusCode(), "发码应成功：" + resp.getBody());
        return smsCodeRepository.findFirstByPhoneOrderByIdDesc(phone).orElseThrow().getCode();
    }

    private ResponseEntity<String> bind(String openid, String phone, String code) {
        return post("/api/v1/wx/bind/" + accountId,
                "{\"openid\":\"" + openid + "\",\"phone\":\"" + phone + "\",\"smsCode\":\"" + code + "\"}");
    }

    private WxBindingLogEntity lastAudit() {
        return bindingLogRepository.findAll().stream()
                .reduce((first, second) -> second).orElseThrow();
    }

    @Test
    void issueCodeThenBindSuccessWritesCustomerIdAndAudits() {
        String code = requestCode(PHONE);
        ResponseEntity<String> resp = bind(OPENID, PHONE, code);
        assertEquals(HttpStatus.OK, resp.getStatusCode(), "绑定应成功：" + resp.getBody());
        assertTrue(resp.getBody().contains("绑定顾客甲"), "回执应含顾客姓名：" + resp.getBody());

        WxFanEntity fan = fanRepository.findByAccountIdAndOpenid(accountId, OPENID).orElseThrow();
        assertEquals(customerId, fan.getCustomerId(), "粉丝行应写入 customer_id");
        assertEquals(1, fan.getSubscribe(), "H5 补建的粉丝行 subscribe=1");
        WxBindingLogEntity audit = lastAudit();
        assertEquals("ok", audit.getResult(), "成功绑定应落审计：" + audit);
        assertEquals(customerId, audit.getCustomerId(), "审计应记命中的顾客");
    }

    @Test
    void rebindSameCustomerIsIdempotent() {
        String code = requestCode(PHONE);
        assertEquals(HttpStatus.OK, bind(OPENID, PHONE, code).getStatusCode());
        String code2 = requestCode(PHONE);
        ResponseEntity<String> again = bind(OPENID, PHONE, code2);
        assertEquals(HttpStatus.OK, again.getStatusCode(), "同档案重复绑定应幂等：" + again.getBody());
        assertTrue(again.getBody().contains("无需重复绑定"), "幂等回执应明说：" + again.getBody());
        assertEquals(customerId, fanRepository.findByAccountIdAndOpenid(accountId, OPENID).orElseThrow().getCustomerId());
    }

    @Test
    void missingParamsRejectedAndAudited() {
        ResponseEntity<String> resp = bind(OPENID, PHONE, "");
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals("missing_code", lastAudit().getResult(), "缺码应落 missing_code 审计");

        ResponseEntity<String> noOpenid = bind("", PHONE, "123456");
        assertEquals(HttpStatus.BAD_REQUEST, noOpenid.getStatusCode());
        assertEquals("missing_openid", lastAudit().getResult(), "缺 openid 应落 missing_openid 审计");
    }

    @Test
    void wrongAndUnknownCodesRejectedAndAudited() {
        String code = requestCode(PHONE);
        ResponseEntity<String> wrong = bind(OPENID, PHONE, "000000".equals(code) ? "999999" : "000000");
        assertEquals(HttpStatus.BAD_REQUEST, wrong.getStatusCode());
        assertEquals("bad_code", lastAudit().getResult(), "错码应落 bad_code 审计");

        ResponseEntity<String> unknown = bind(OPENID, PHONE, "654321");
        assertEquals(HttpStatus.BAD_REQUEST, unknown.getStatusCode());
        assertEquals("bad_code", lastAudit().getResult(), "未发过码应落 bad_code（同 Kiosk「验证码不存在」口径）");
    }

    @Test
    void usedAndExpiredCodesRejectedAndAudited() {
        String code = requestCode(PHONE);
        assertEquals(HttpStatus.OK, bind(OPENID, PHONE, code).getStatusCode());
        // 一次性：同码再用（换粉丝绕开幂等路径）
        String code2 = requestCode(PHONE);
        assertEquals(HttpStatus.OK, bind(OPENID, PHONE, code2).getStatusCode());
        ResponseEntity<String> reused = bind("oFANBIND2", PHONE, code2);
        assertEquals(HttpStatus.BAD_REQUEST, reused.getStatusCode());
        assertEquals("used_code", lastAudit().getResult(), "已用码应落 used_code 审计");

        SmsCodeEntity expired = new SmsCodeEntity();
        expired.setPhone(OTHER_PHONE);
        expired.setCode("313131");
        expired.setUsed(0);
        expired.setExpiresAt(new Date(System.currentTimeMillis() - 60_000L));
        smsCodeRepository.save(expired);
        ResponseEntity<String> expiredResp = bind("oFANBIND3", OTHER_PHONE, "313131");
        assertEquals(HttpStatus.BAD_REQUEST, expiredResp.getStatusCode());
        assertEquals("expired_code", lastAudit().getResult(), "过期码应落 expired_code 审计");
    }

    @Test
    void unknownAccountAndPhoneRejectedAndAudited() {
        ResponseEntity<String> noAccount = post("/api/v1/wx/bind/999999/code", "{\"phone\":\"" + PHONE + "\"}");
        assertEquals(HttpStatus.BAD_REQUEST, noAccount.getStatusCode());
        assertEquals("account_missing", lastAudit().getResult(), "未知账号取码应落 account_missing 审计");

        // 无档案手机号：直插一条有效码，验码通过但查无档案 → no_customer
        SmsCodeEntity forGhost = new SmsCodeEntity();
        forGhost.setPhone("13111110000");
        forGhost.setCode("246810");
        forGhost.setUsed(0);
        forGhost.setExpiresAt(new Date(System.currentTimeMillis() + 300_000L));
        smsCodeRepository.save(forGhost);
        ResponseEntity<String> resp = bind(OPENID, "13111110000", "246810");
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals("no_customer", lastAudit().getResult(), "无档案手机号应落 no_customer 审计");
    }

    @Test
    void fanBoundToOtherCustomerRejected() {
        // 粉丝已绑顾客乙，再用有效码绑顾客甲 → bound_other（不静默改绑）
        String code = requestCode(OTHER_PHONE);
        assertEquals(HttpStatus.OK, bind(OPENID, OTHER_PHONE, code).getStatusCode());
        String code2 = requestCode(PHONE);
        ResponseEntity<String> resp = bind(OPENID, PHONE, code2);
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals("bound_other", lastAudit().getResult(), "粉丝已绑他档应落 bound_other 审计");
        assertEquals(otherCustomerId,
                fanRepository.findByAccountIdAndOpenid(accountId, OPENID).orElseThrow().getCustomerId(),
                "拒绝后原绑定不得被改");
    }

    @Test
    void customerBoundToOtherFanRejected() {
        // 顾客甲已被粉丝 A 绑定，粉丝 B 再绑同一顾客 → conflict
        String codeA = requestCode(PHONE);
        assertEquals(HttpStatus.OK, bind("oFANA", PHONE, codeA).getStatusCode());
        String codeB = requestCode(PHONE);
        ResponseEntity<String> resp = bind("oFANB", PHONE, codeB);
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals("conflict", lastAudit().getResult(), "顾客被占应落 conflict 审计");
    }

    @Test
    void adminUnbindClearsCustomerAndAudits() {
        String code = requestCode(PHONE);
        assertEquals(HttpStatus.OK, bind(OPENID, PHONE, code).getStatusCode());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        var login = rest.postForEntity("http://localhost:" + port + "/api/v1/user/login",
                new HttpEntity<>(Map.of("username", "admin", "password", "123"), headers), Map.class);
        headers.setBearerAuth((String) login.getBody().get("token"));
        ResponseEntity<String> unbind = rest.exchange(
                "http://localhost:" + port + "/api/v1/wx/fans/" + accountId + "/unbind",
                HttpMethod.POST, new HttpEntity<>("{\"openid\":\"" + OPENID + "\"}", headers), String.class);
        assertEquals(HttpStatus.OK, unbind.getStatusCode(), "解绑应成功：" + unbind.getBody());
        assertNull(fanRepository.findByAccountIdAndOpenid(accountId, OPENID).orElseThrow().getCustomerId(),
                "解绑应清 customer_id（保留粉丝行）");
        WxBindingLogEntity audit = lastAudit();
        assertEquals("unbind", audit.getResult(), "解绑应落审计：" + audit);
        assertEquals(customerId, audit.getCustomerId(), "解绑审计应记原顾客");

        ResponseEntity<String> again;
        try {
            again = rest.exchange("http://localhost:" + port + "/api/v1/wx/fans/" + accountId + "/unbind",
                    HttpMethod.POST, new HttpEntity<>("{\"openid\":\"" + OPENID + "\"}", headers), String.class);
        } catch (HttpClientErrorException e) {
            // 预期 400：exchange 对 4xx 抛异常，捕获后断言状态码
            again = ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        }
        assertEquals(HttpStatus.BAD_REQUEST, again.getStatusCode(), "重复解绑应明确拒绝");
        assertEquals("not_bound", lastAudit().getResult());
    }

    @Test
    void unbindRequiresAuthentication() {
        HttpClientErrorException ex = org.junit.jupiter.api.Assertions.assertThrows(HttpClientErrorException.class,
                () -> rest.exchange("http://localhost:" + port + "/api/v1/wx/fans/" + accountId + "/unbind",
                        HttpMethod.POST, new HttpEntity<>("{\"openid\":\"x\"}"), String.class));
        assertEquals(401, ex.getStatusCode().value(), "管理端解绑不得免登录");
    }

    @Test
    void badPhoneFormatRejectedWithoutAudit() {
        ResponseEntity<String> resp = post("/api/v1/wx/bind/" + accountId + "/code", "{\"phone\":\"12345\"}");
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode(), "手机号格式错应拒绝：" + resp.getBody());
        assertEquals(0, bindingLogRepository.count(), "格式错不落绑定审计（同 Kiosk 口径：非尝试语义）");
    }
}
