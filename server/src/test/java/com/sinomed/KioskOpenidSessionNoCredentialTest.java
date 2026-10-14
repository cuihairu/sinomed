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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 凭据未配置（WECHAT_APP_ID/WECHAT_APP_SECRET 缺省空串）时 openid 换取明确拒绝——
 * 400 透出「微信凭据未配置」，不静默、不落 500，且每次尝试落审计（code_exchange_failed）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:sqlite:target/openid-session-nocred-test-${random.uuid}.db",
        "sinomed.phone-lookup.mode=openid_only"
})
class KioskOpenidSessionNoCredentialTest {

    @LocalServerPort
    private int port;

    private final RestTemplate rest = new RestTemplate();

    @Autowired
    private CardLookupLogRepository cardLookupLogRepository;

    @BeforeEach
    void clearLogs() {
        cardLookupLogRepository.deleteAll();
    }

    private ResponseEntity<String> post(String body) {
        var req = RequestEntity.post(URI.create("http://localhost:" + port + "/api/v1/kiosk/openid-session"))
                .contentType(MediaType.APPLICATION_JSON).body(body);
        try {
            return rest.exchange(req, String.class);
        } catch (HttpClientErrorException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        }
    }

    @Test
    void missingCredentialsRejectedWithAudit() {
        var resp = post("{\"code\":\"anyCode\"}");
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        String message = JsonPath.read(resp.getBody(), "$.errorMessage");
        assertTrue(message.contains("微信凭据未配置"), message);
        List<CardLookupLogEntity> logs = cardLookupLogRepository.findAll();
        assertEquals(1, logs.size());
        assertEquals("code_exchange_failed", logs.get(0).getResult());
        assertEquals("openid_only", logs.get(0).getMode());
    }
}
