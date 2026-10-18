package com.sinomed;

import com.sinomed.entity.ItemEntity;
import com.sinomed.repository.ItemRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Kiosk 浏览项载荷 HTTP 回归（手机端预约页取此项列表）：
 * 上架项透出 durationMinutes 供预约页展示服务时长（此前视图白名单丢弃该字段，
 * 顾客只能盲选时段）；无时长为空，停用项不出现在列表。走真实 HTTP。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.datasource.url=jdbc:sqlite:target/kiosk-item-http-test-${random.uuid}.db"})
class KioskItemHttpTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ItemRepository itemRepository;

    private final RestTemplate rest = new RestTemplate();

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private ItemEntity seed(String name, int price, Integer durationMinutes, int enabled) {
        ItemEntity item = new ItemEntity();
        item.setName(name);
        item.setDescription("");
        item.setPrice(price);
        item.setEnabled(enabled);
        item.setSort(0);
        if (durationMinutes != null) {
            item.setDurationMinutes(durationMinutes);
        }
        return itemRepository.save(item);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> browse() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return (List<Map<String, Object>>) (List<?>) rest.exchange(url("/api/v1/kiosk/items"),
                HttpMethod.GET, new HttpEntity<>(headers), List.class).getBody();
    }

    private Map<String, Object> row(List<Map<String, Object>> rows, String name) {
        return rows.stream().filter(r -> name.equals(r.get("name"))).findFirst().orElseThrow();
    }

    @Test
    void browseExposesDurationAndSkipsDisabled() {
        String tag = System.nanoTime() + "";
        String withDuration = "时长项-" + tag;
        String withoutDuration = "无时长项-" + tag;
        String disabled = "停用项-" + tag;
        seed(withDuration, 100, 40, 1);
        seed(withoutDuration, 100, null, 1);
        seed(disabled, 100, 60, 0);

        List<Map<String, Object>> rows = browse();
        assertEquals(40, ((Number) row(rows, withDuration).get("durationMinutes")).intValue(),
                "预约页应能取到服务时长：" + rows);
        assertNull(row(rows, withoutDuration).get("durationMinutes"), "无时长应为空，不编造时长：" + rows);
        assertFalse(rows.stream().anyMatch(r -> disabled.equals(r.get("name"))),
                "停用项不应出现在浏览列表：" + rows);
    }
}
