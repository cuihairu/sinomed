package com.sinomed;

import com.jayway.jsonpath.JsonPath;
import com.sinomed.entity.CardLookupLogEntity;
import com.sinomed.repository.CardLookupLogRepository;
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

/**
 * phone-lookup off 模式（默认）：裸手机号查卡一律拒绝——即使带了手机号/验证码也不放行，
 * 且每次拒绝都落审计。口径见 2026-10-14 裁定单（隐私红线）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:sqlite:target/card-lookup-off-test-${random.uuid}.db",
        "sinomed.phone-lookup.mode=off"
})
class CardLookupOffTest {

    @LocalServerPort
    private int port;

    private final RestTemplate rest = new RestTemplate();

    @Autowired
    private CardLookupLogRepository cardLookupLogRepository;

    @BeforeEach
    void clearLogs() {
        cardLookupLogRepository.deleteAll();
    }

    /** 免登录区无需 token；4xx 转成响应返回，供断言状态码与 errorMessage */
    private ResponseEntity<String> post(String path, String body) {
        var req = RequestEntity.post(URI.create("http://localhost:" + port + path))
                .contentType(MediaType.APPLICATION_JSON).body(body);
        try {
            return rest.exchange(req, String.class);
        } catch (HttpClientErrorException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        }
    }

    @Test
    void cardLookupRejectedEvenWithPhone() {
        var resp = post("/api/v1/kiosk/card-lookup", "{\"phone\":\"13900003001\"}");
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals("在线查卡已关闭，请到店前台或致电门店查询",
                JsonPath.read(resp.getBody(), "$.errorMessage"));
    }

    @Test
    void cardLookupRejectedEvenWithOpenid() {
        var resp = post("/api/v1/kiosk/card-lookup", "{\"openid\":\"oOFF123\"}");
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals("在线查卡已关闭，请到店前台或致电门店查询",
                JsonPath.read(resp.getBody(), "$.errorMessage"));
    }

    @Test
    void smsCodeRejected() {
        var resp = post("/api/v1/kiosk/sms-code", "{\"phone\":\"13900003001\"}");
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals("在线查卡已关闭，请到店前台或致电门店查询",
                JsonPath.read(resp.getBody(), "$.errorMessage"));
    }

    @Test
    void openidSessionRejected() {
        var resp = post("/api/v1/kiosk/openid-session", "{\"code\":\"anyCode\"}");
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals("在线查卡已关闭，请到店前台或致电门店查询",
                JsonPath.read(resp.getBody(), "$.errorMessage"));
    }

    @Test
    void myAppointmentsRejected() {
        var resp = post("/api/v1/kiosk/my-appointments", "{\"phone\":\"13900003001\",\"smsCode\":\"123456\"}");
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals("在线自助查询已关闭，请到店前台或致电门店",
                JsonPath.read(resp.getBody(), "$.errorMessage"));
    }

    @Test
    void everyRejectLeavesAuditLog() {
        post("/api/v1/kiosk/card-lookup", "{\"phone\":\"13900003001\"}");
        post("/api/v1/kiosk/card-lookup", "{\"openid\":\"oOFF123\"}");
        post("/api/v1/kiosk/sms-code", "{\"phone\":\"13900003001\"}");
        List<CardLookupLogEntity> logs = cardLookupLogRepository.findAll();
        assertEquals(3, logs.size());
        assertEquals("disabled", logs.get(0).getResult());
        assertEquals("off", logs.get(0).getMode());
    }
}
