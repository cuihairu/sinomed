package com.sinomed;

import com.sinomed.entity.CustomerEntity;
import com.sinomed.entity.TreatEntity;
import com.sinomed.repository.CustomerRepository;
import com.sinomed.repository.TreatRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
 * 按顾客批量查就诊次数 HTTP 回归（GET /api/v1/treat/visited?ids=）：
 * 有诊疗记录的顾客回次数、无记录顾客不出现在结果里（排班页据此标「首诊/复诊」角标）；ids 缺失 400。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.datasource.url=jdbc:sqlite:target/treat-visited-http-test-${random.uuid}.db"})
class TreatVisitedHttpTest {

    @LocalServerPort
    private int port;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private TreatRepository treatRepository;

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

    private int statusOf(String path, String token) {
        try {
            return rest.exchange(url(path), HttpMethod.GET, authEntity(null, token), String.class)
                    .getStatusCode().value();
        } catch (HttpStatusCodeException e) {
            return e.getStatusCode().value();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> visited(String ids, String token) {
        return rest.exchange(url("/api/v1/treat/visited?ids=" + ids), HttpMethod.GET,
                authEntity(null, token), List.class).getBody();
    }

    @Test
    void visitedCountReturnedAndMissingCustomerOmitted() {
        String token = token();
        CustomerEntity seen = new CustomerEntity();
        seen.setName("复诊顾客");
        seen.setPhone("13800011101");
        long seenId = customerRepository.save(seen).getId();

        CustomerEntity fresh = new CustomerEntity();
        fresh.setName("首诊顾客");
        fresh.setPhone("13800011102");
        long freshId = customerRepository.save(fresh).getId();

        TreatEntity t1 = new TreatEntity();
        t1.setCustomerId(seenId);
        treatRepository.save(t1);
        TreatEntity t2 = new TreatEntity();
        t2.setCustomerId(seenId);
        treatRepository.save(t2);

        List<Map<String, Object>> rows = visited(seenId + "," + freshId + "," + 999999, token);
        assertEquals(1, rows.size(), "无诊疗记录的顾客（含不存在的 id）不应出现在结果里：" + rows);
        assertEquals(seenId, ((Number) rows.get(0).get("customerId")).longValue());
        assertEquals(2, ((Number) rows.get(0).get("visits")).intValue(), "就诊次数应按 treats 计数");
    }

    @Test
    void missingIdsParamRejected() {
        String token = token();
        assertTrue(statusOf("/api/v1/treat/visited", token) == 400,
                "ids 必填，缺失应报 400");
    }
}
