package com.sinomed;

import com.jayway.jsonpath.JsonPath;
import com.sinomed.entity.ItemEntity;
import com.sinomed.entity.OrderEntity;
import com.sinomed.repository.ItemRepository;
import com.sinomed.repository.OrderItemRepository;
import com.sinomed.repository.OrderRepository;
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
 * Kiosk 下单写入口（POST /api/v1/kiosk/orders）回归：
 * 手机号校验、卡项存在/上架校验、价格快照、按手机号幂等建档、合计计算。
 * 口径见 docs/design/kiosk.md。
 *
 * <p>库路径用随机文件，避免与 target/test-sinomed.db 的既有数据互相污染。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:sqlite:target/kiosk-order-test-${random.uuid}.db"
})
class KioskOrderTest {

    @LocalServerPort
    private int port;

    private final RestTemplate rest = new RestTemplate();

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    private static Long itemA;
    private static Long itemB;
    private static Long itemDisabled;

    @BeforeEach
    void seedItems() {
        // 随机空库无演示种子：自建上架/下架卡项（static 保证全类只插一次；items.name 唯一）
        if (itemA == null) {
            itemA = saveItem("测试下单甲", 198, 1);
            itemB = saveItem("测试下单乙", 680, 1);
            itemDisabled = saveItem("测试下单下架", 99, 0);
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

    /** 发起下单请求；免登录区无需 token。4xx 转成响应返回，供断言状态码与 errorMessage */
    private ResponseEntity<String> order(String body) {
        var req = RequestEntity.post(URI.create("http://localhost:" + port + "/api/v1/kiosk/orders"))
                .contentType(MediaType.APPLICATION_JSON).body(body);
        try {
            return rest.exchange(req, String.class);
        } catch (HttpClientErrorException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        }
    }

    @Test
    void happyPathCreatesTwoPendingOrdersWithTotalFee() {
        var resp = order("{\"phone\":\"13900002001\",\"name\":\"下单甲\",\"itemIds\":[" + itemA + "," + itemB + "]}");
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        String body = resp.getBody();
        List<Number> orderIds = JsonPath.read(body, "$.orders[*].id");
        assertEquals(2, orderIds.size());
        assertEquals(198 + 680, ((Number) JsonPath.read(body, "$.totalFee")).intValue());
        assertEquals("下单甲", JsonPath.read(body, "$.customerName"));
        // 逐单落库待接待
        for (Number id : orderIds) {
            OrderEntity saved = orderRepository.findById(id.longValue()).orElseThrow();
            assertEquals(0, saved.getStatus());
        }
        // 明细行落库：每单一行，名称/单价快照
        var lines = orderItemRepository.findByOrderIdOrderByIdAsc(orderIds.get(0).longValue());
        assertEquals(1, lines.size());
        assertEquals("测试下单甲", lines.get(0).getName());
        assertEquals(198, lines.get(0).getSubtotal());
    }

    @Test
    void orderPriceIsSnapshotAtBookingTime() {
        var resp = order("{\"phone\":\"13900002002\",\"itemIds\":[" + itemA + "]}");
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        Number orderId = ((List<Number>) JsonPath.read(resp.getBody(), "$.orders[*].id")).get(0);
        // 下单后调价，已落订单价格不变（快照）
        ItemEntity item = itemRepository.findById(itemA).orElseThrow();
        item.setPrice(item.getPrice() + 500);
        itemRepository.save(item);
        OrderEntity saved = orderRepository.findById(orderId.longValue()).orElseThrow();
        assertEquals(198, saved.getPrice());
    }

    @Test
    void samePhoneMergesToSameCustomer() {
        String phone = "13900002003";
        var first = order("{\"phone\":\"" + phone + "\",\"itemIds\":[" + itemA + "]}");
        var second = order("{\"phone\":\"" + phone + "\",\"itemIds\":[" + itemB + "]}");
        assertEquals(HttpStatus.OK, first.getStatusCode());
        assertEquals(HttpStatus.OK, second.getStatusCode());
        Number firstId = JsonPath.read(first.getBody(), "$.customerId");
        Number secondId = JsonPath.read(second.getBody(), "$.customerId");
        assertEquals(firstId.longValue(), secondId.longValue());
    }

    @Test
    void badPhoneRejected() {
        var resp = order("{\"phone\":\"123\",\"itemIds\":[" + itemA + "]}");
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals("手机号格式不对：应为 1 开头的 11 位数字", JsonPath.read(resp.getBody(), "$.errorMessage"));
    }

    @Test
    void missingPhoneRejected() {
        assertEquals(HttpStatus.BAD_REQUEST, order("{\"itemIds\":[" + itemA + "]}").getStatusCode());
    }

    @Test
    void missingItemIdsRejected() {
        assertEquals(HttpStatus.BAD_REQUEST, order("{\"phone\":\"13900002004\"}").getStatusCode());
    }

    @Test
    void nonexistentItemRejected() {
        var resp = order("{\"phone\":\"13900002005\",\"itemIds\":[99999]}");
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertTrue(((String) JsonPath.read(resp.getBody(), "$.errorMessage")).startsWith("卡项不存在"));
    }

    @Test
    void disabledItemRejected() {
        var resp = order("{\"phone\":\"13900002006\",\"itemIds\":[" + itemDisabled + "]}");
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertTrue(((String) JsonPath.read(resp.getBody(), "$.errorMessage")).startsWith("卡项已下架"));
    }
}
