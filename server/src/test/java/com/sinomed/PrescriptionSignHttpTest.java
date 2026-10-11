package com.sinomed;

import com.sinomed.entity.StaffEntity;
import com.sinomed.repository.StaffRepository;
import org.junit.jupiter.api.BeforeEach;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 处方签名行 + 领取方式 HTTP 回归：开方带药师审核/调配复核人（存档读回）、
 * 代煎带领取方式（仅代煎保留，不代煎忽略）、无效员工 400。走真实 HTTP。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.datasource.url=jdbc:sqlite:target/rx-sign-http-test-${random.uuid}.db"})
class PrescriptionSignHttpTest {

    @LocalServerPort
    private int port;

    @Autowired
    private StaffRepository staffRepository;

    private final RestTemplate rest = new RestTemplate();

    private Long reviewerId;
    private Long checkerId;

    @BeforeEach
    void setUpStaff() {
        // staffs.account 唯一约束：每例独立账号（@BeforeEach 每测试实例各跑一次）
        reviewerId = staffRepository.save(newStaff("审方药师")).getId();
        checkerId = staffRepository.save(newStaff("调配复核")).getId();
    }

    private StaffEntity newStaff(String name) {
        StaffEntity staff = new StaffEntity();
        staff.setName(name);
        staff.setAccount("rx-sign-" + System.nanoTime());
        staff.setPassword("x");
        staff.setStatus(1);
        return staff;
    }

    private String token() {
        // 默认馆长账号（admin/123，UserDetailsServiceImpl 自举）直接登录
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

    private String exchange4xx(String method, String path, Object body, String token) {
        try {
            return rest.exchange(url(path), HttpMethod.valueOf(method), authEntity(body, token), String.class).getBody();
        } catch (HttpStatusCodeException e) {
            return e.getResponseBodyAsString();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String path, Object body, String token) {
        return rest.exchange(url(path), HttpMethod.POST, authEntity(body, token), Map.class).getBody();
    }

    private long createCustomer(String token, String name, String phone) {
        Map<String, Object> customer = post("/api/v1/customer/",
                Map.of("name", name, "gender", 1, "phone", phone), token);
        return ((Number) customer.get("id")).longValue();
    }

    @Test
    void signAndDeliveryFieldsSavedAndReadBack() {
        String token = token();
        long customerId = createCustomer(token, "签名行顾客", "13900013001");

        Map<String, Object> created = post("/api/v1/prescription/", Map.of(
                "customerId", customerId, "doses", 7,
                "decoction", true, "deliveryType", 1,
                "reviewStaffId", reviewerId, "checkStaffId", checkerId,
                "herbs", List.of(Map.of("herb", "党参", "weight", 15))), token);
        assertTrue(created.get("id") != null, "开方应返回 id：" + created);
        assertEquals(reviewerId.longValue(), ((Number) created.get("reviewStaffId")).longValue(),
                "药师审核人应存档：" + created);
        assertEquals(checkerId.longValue(), ((Number) created.get("checkStaffId")).longValue(),
                "调配复核人应存档：" + created);
        assertEquals(1, created.get("deliveryType"), "代煎领取方式应存档：" + created);
        assertEquals(1, created.get("decoctionStatus"), "代煎落「待煎」：" + created);

        // 详情读回一致
        long id = ((Number) created.get("id")).longValue();
        Map<String, Object> detail = rest.exchange(url("/api/v1/prescription/" + id),
                HttpMethod.GET, authEntity(null, token), Map.class).getBody();
        assertEquals(reviewerId.longValue(), ((Number) detail.get("reviewStaffId")).longValue());
        assertEquals(checkerId.longValue(), ((Number) detail.get("checkStaffId")).longValue());
        assertEquals(1, detail.get("deliveryType"));
    }

    @Test
    void deliveryTypeIgnoredWithoutDecoction() {
        String token = token();
        long customerId = createCustomer(token, "不代煎顾客", "13900013002");

        Map<String, Object> created = post("/api/v1/prescription/", Map.of(
                "customerId", customerId, "doses", 7,
                "deliveryType", 1,
                "herbs", List.of(Map.of("herb", "白术", "weight", 10))), token);
        assertTrue(created.get("id") != null, "开方应返回 id：" + created);
        assertNull(created.get("deliveryType"), "不代煎时领取方式应忽略：" + created);
    }

    @Test
    void invalidReviewStaffRejected() {
        String token = token();
        long customerId = createCustomer(token, "坏签名顾客", "13900013003");

        String body = exchange4xx("POST", "/api/v1/prescription/", Map.of(
                "customerId", customerId, "doses", 7,
                "reviewStaffId", 999999,
                "herbs", List.of(Map.of("herb", "茯苓", "weight", 12))), token);
        assertTrue(body.contains("员工不存在"), "无效药师审核人应 400：" + body);
    }
}
