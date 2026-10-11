package com.sinomed;

import com.jayway.jsonpath.JsonPath;
import com.sinomed.entity.CustomerEntity;
import com.sinomed.entity.ItemEntity;
import com.sinomed.repository.CustomerRepository;
import com.sinomed.repository.ItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 订单多品项/备注 HTTP 回归：多行建单（items）、明细行联出（存量单回退一行）、
 * 整单备注写入与清空、列表按创建时间倒序。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:sqlite:target/order-http-test-${random.uuid}.db"
})
class OrderHttpTest {

    @LocalServerPort
    private int port;

    private final RestTemplate rest = new RestTemplate();

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ItemRepository itemRepository;

    private static Long customerId;
    private static Long itemA;
    private static Long itemB;

    @BeforeEach
    void seed() {
        if (itemA == null) {
            CustomerEntity customer = new CustomerEntity();
            customer.setName("多品项顾客");
            customer.setPhone("13900007001");
            customerId = customerRepository.save(customer).getId();

            itemA = saveItem("多品项甲", 120, 1);
            itemB = saveItem("多品项乙", 80, 1);
        }
    }

    private Long saveItem(String name, int price, int enabled) {
        ItemEntity item = new ItemEntity();
        item.setName(name);
        item.setDescription("");
        item.setPrice(price);
        item.setEnabled(enabled);
        item.setSort(0);
        return itemRepository.save(item).getId();
    }

    private String token() {
        var resp = rest.postForEntity(url("/api/v1/user/login"),
                new HttpEntity<>(Map.of("username", "admin", "password", "123"), jsonHeaders()), Map.class);
        return (String) resp.getBody().get("token");
    }

    private HttpHeaders jsonHeaders() {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    /** 带 token 的请求；4xx 转成响应返回，供断言状态码与 errorMessage */
    private ResponseEntity<String> exchange(String method, String path, String token, String body) {
        RequestEntity<?> req;
        if (body == null) {
            req = RequestEntity.method(HttpMethod.valueOf(method), URI.create(url(path)))
                    .header("Authorization", "Bearer " + token).build();
        } else {
            req = RequestEntity.method(HttpMethod.valueOf(method), URI.create(url(path)))
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON).body(body);
        }
        try {
            return rest.exchange(req, String.class);
        } catch (HttpClientErrorException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        }
    }

    @Test
    void multiLineCreatePageDetailAndRemarkFlow() {
        String token = token();

        // 多行建单：甲 ×1 + 乙 ×2，合计 120+160=280
        var created = exchange("POST", "/api/v1/order/", token, "{\"customerId\":" + customerId
                + ",\"items\":[{\"itemId\":" + itemA + ",\"qty\":1},{\"itemId\":" + itemB + ",\"qty\":2}]}");
        assertEquals(HttpStatus.OK, created.getStatusCode());
        Number orderId = JsonPath.read(created.getBody(), "$.id");
        assertEquals(280, ((Number) JsonPath.read(created.getBody(), "$.price")).intValue());
        assertEquals(2, ((List<?>) JsonPath.read(created.getBody(), "$.items[*]")).size());
        assertEquals("多品项甲", JsonPath.read(created.getBody(), "$.items[0].itemName"));
        assertEquals(160, ((Number) JsonPath.read(created.getBody(), "$.items[1].subtotal")).intValue());

        // 整单备注：写入与清空
        var remarked = exchange("PUT", "/api/v1/order/remark", token,
                "{\"id\":" + orderId + ",\"remark\":\"代煎 7 袋，前两袋先试饮\"}");
        assertEquals(HttpStatus.OK, remarked.getStatusCode());
        assertEquals("代煎 7 袋，前两袋先试饮", JsonPath.read(remarked.getBody(), "$.remark"));
        var cleared = exchange("PUT", "/api/v1/order/remark", token, "{\"id\":" + orderId + ",\"remark\":\"\"}");
        assertEquals(HttpStatus.OK, cleared.getStatusCode());
        assertTrue(JsonPath.read(cleared.getBody(), "$.remark") == null);

        // 列表联出明细行与备注
        var page = exchange("GET", "/api/v1/order/page?current=1&pageSize=10", token, null);
        assertEquals(HttpStatus.OK, page.getStatusCode());
        assertTrue(((List<?>) JsonPath.read(page.getBody(), "$.data[*].items[*]")).size() >= 2);

        // 详情同联明细行
        var detail = exchange("GET", "/api/v1/order/" + orderId, token, null);
        assertEquals(HttpStatus.OK, detail.getStatusCode());
        assertEquals(2, ((Number) JsonPath.read(detail.getBody(), "$.items[1].qty")).intValue());

        // 备注超长 400
        var tooLong = exchange("PUT", "/api/v1/order/remark", token,
                "{\"id\":" + orderId + ",\"remark\":\"" + "长".repeat(201) + "\"}");
        assertEquals(HttpStatus.BAD_REQUEST, tooLong.getStatusCode());
        assertEquals("备注最长 200 字", JsonPath.read(tooLong.getBody(), "$.errorMessage"));
    }

    @Test
    void legacySingleCreateFallsBackToOneLine() {
        String token = token();
        var created = exchange("POST", "/api/v1/order/", token,
                "{\"customerId\":" + customerId + ",\"itemId\":" + itemB + "}");
        assertEquals(HttpStatus.OK, created.getStatusCode());
        Number orderId = JsonPath.read(created.getBody(), "$.id");
        assertEquals(80, ((Number) JsonPath.read(created.getBody(), "$.price")).intValue());

        var detail = exchange("GET", "/api/v1/order/" + orderId, token, null);
        assertEquals(HttpStatus.OK, detail.getStatusCode());
        assertEquals(1, ((List<?>) JsonPath.read(detail.getBody(), "$.items[*]")).size());
        assertEquals("多品项乙", JsonPath.read(detail.getBody(), "$.items[0].itemName"));
        assertEquals(1, ((Number) JsonPath.read(detail.getBody(), "$.items[0].qty")).intValue());
    }

    @Test
    void pageListsNewOrdersFirst() {
        String token = token();
        Number firstId = JsonPath.read(
                exchange("POST", "/api/v1/order/", token, "{\"customerId\":" + customerId + ",\"itemId\":" + itemA + "}").getBody(),
                "$.id");
        Number secondId = JsonPath.read(
                exchange("POST", "/api/v1/order/", token, "{\"customerId\":" + customerId + ",\"itemId\":" + itemA + "}").getBody(),
                "$.id");
        var page = exchange("GET", "/api/v1/order/page?current=1&pageSize=10", token, null);
        List<Number> ids = JsonPath.read(page.getBody(), "$.data[*].id");
        assertTrue(ids.indexOf(secondId) < ids.indexOf(firstId), "列表新单在前（createTime 倒序，同秒 id 倒序）");
    }
}
