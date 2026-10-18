package com.sinomed;

import com.sinomed.entity.CustomerEntity;
import com.sinomed.entity.TreatEntity;
import com.sinomed.repository.CustomerRepository;
import com.sinomed.repository.TreatRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * /treat/fuzzy 模糊检索契约 HTTP 回归（巡检 2026-10-19）：
 * api.md:61 白纸黑字「模糊检索（按 name 圈定顾客）」，但 fuzzy 的 name 路径委托
 * CustomerServiceImpl.findAllByPage 做精确匹配——顾客姓名传部分词静默空结果，文档契约违约。
 * 随顾客分页 name 改包含口径后此处锁定端到端行为：部分词按包含圈定顾客再出诊疗记录。
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {"spring.datasource.url=jdbc:sqlite:target/treat-fuzzy-name-test-${random.uuid}.db"})
class TreatFuzzyNameHttpTest {

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

    private long seedCustomerWithTreats(String name, String phone, int treats) {
        CustomerEntity customer = new CustomerEntity();
        customer.setName(name);
        customer.setPhone(phone);
        long customerId = customerRepository.save(customer).getId();
        for (int i = 0; i < treats; i++) {
            TreatEntity t = new TreatEntity();
            t.setCustomerId(customerId);
            treatRepository.save(t);
        }
        return customerId;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fuzzy(String token, String name) {
        // UriComponentsBuilder 只编码一次（手拼 %E8… 会被 RestTemplate 再编码致 LIKE 不命中）
        var uri = org.springframework.web.util.UriComponentsBuilder
                .fromUriString(url("/api/v1/treat/fuzzy"))
                .queryParam("current", 1)
                .queryParam("pageSize", 50)
                .queryParam("name", name)
                .build().encode().toUri();
        return rest.exchange(uri, HttpMethod.GET, authEntity(null, token), Map.class).getBody();
    }

    @Test
    void fuzzyNameContainsAcrossPartialNames() {
        String token = token();
        String tag = String.valueOf(System.nanoTime());
        seedCustomerWithTreats("模糊检索王" + tag, "13800030001", 2);
        seedCustomerWithTreats("模糊检索李" + tag, "13800030002", 1);

        Map<String, Object> resp = fuzzy(token, "王" + tag);
        assertEquals(2, ((Number) resp.get("total")).intValue(),
                "name 部分词应按包含圈定顾客并出其全部诊疗（模糊契约）：" + resp);

        Map<String, Object> common = fuzzy(token, "模糊检索");
        assertEquals(3, ((Number) common.get("total")).intValue(),
                "公共子串应圈定两位顾客共 3 条（2+1）：" + common);
    }

    @Test
    void fuzzyNameNoMatchReturnsEmptyNotError() {
        String token = token();
        Map<String, Object> resp = fuzzy(token, "无此顾客姓" + System.nanoTime());
        assertEquals(0, ((Number) resp.get("total")).intValue(), "不命中应空结果不报错：" + resp);
        assertTrue(((List<?>) resp.get("data")).isEmpty(), "data 应为空数组：" + resp.get("data"));
    }

    @Test
    void fuzzyByIdMissingReturnsConsistentEmpty() {
        // 修复前该路径 total 从未赋值返回 null、且 pages=1 与空 data 自相矛盾
        String token = token();
        var uri = org.springframework.web.util.UriComponentsBuilder
                .fromUriString(url("/api/v1/treat/fuzzy"))
                .queryParam("current", 1)
                .queryParam("pageSize", 10)
                .queryParam("id", 999999999L)
                .build().encode().toUri();
        Map<String, Object> resp = rest.exchange(uri, HttpMethod.GET,
                authEntity(null, token), Map.class).getBody();
        assertEquals(0, ((Number) resp.get("total")).intValue(), "id 未命中 total 应为 0 而非 null：" + resp);
        assertEquals(0, ((Number) resp.get("pages")).intValue(), "id 未命中 pages 应与空结果一致为 0：" + resp);
        assertTrue(((List<?>) resp.get("data")).isEmpty(), "data 应为空数组：" + resp.get("data"));
    }
}
