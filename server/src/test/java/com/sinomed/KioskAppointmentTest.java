package com.sinomed;

import com.jayway.jsonpath.JsonPath;
import com.sinomed.entity.ItemEntity;
import com.sinomed.repository.ItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 自助约期写入口（POST /api/v1/kiosk/appointments）回归：
 * 校验链（手机号/整点/时段/未来时刻/卡项）+ 频控（同手机号当日限 1 条）+ 幂等建档。
 * 口径见 docs/design/app-booking.md。
 *
 * <p>库路径用随机文件，避免与 target/test-sinomed.db 的既有数据互相污染（频控断言依赖干净库）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:sqlite:target/kiosk-appt-test-${random.uuid}.db"
})
class KioskAppointmentTest {

    @LocalServerPort
    private int port;

    private final RestTemplate rest = new RestTemplate();

    @Autowired
    private ItemRepository itemRepository;

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static Long testItemId;

    @BeforeEach
    void setup() {
        if (testItemId == null) {
            // 随机空库无演示种子：自建一个上架卡项供约期挂载（static 保证全类只插一次）
            ItemEntity item = new ItemEntity();
            item.setName("测试卡项");
            item.setDescription("");
            item.setPrice(99);
            item.setEnabled(1);
            item.setSort(0);
            testItemId = itemRepository.save(item).getId();
        }
    }

    private String at(int plusDays, int hour, int minute) {
        return LocalDate.now().plusDays(plusDays).format(DAY)
                + " " + String.format("%02d:%02d", hour, minute);
    }

    /** 发起约期请求；免登录区无需 token。4xx 转成响应返回，供断言状态码与 errorMessage */
    private ResponseEntity<String> book(String body) {
        var req = RequestEntity.post(URI.create("http://localhost:" + port + "/api/v1/kiosk/appointments"))
                .contentType(MediaType.APPLICATION_JSON).body(body);
        try {
            return rest.exchange(req, String.class);
        } catch (HttpClientErrorException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        }
    }

    @Test
    void happyPathBooksPendingAppointment() {
        var resp = book("{\"phone\":\"13900001001\",\"name\":\"测试甲\",\"itemId\":" + testItemId
                + ",\"startTime\":\"" + at(1, 10, 0) + "\"}");
        assertEquals(HttpStatus.OK, resp.getStatusCode(), "自助约期应成功：" + resp.getBody());
        String body = resp.getBody();
        assertEquals(0, ((Number) JsonPath.read(body, "$.status")).intValue());
        assertTrue(((Number) JsonPath.read(body, "$.appointmentId")).longValue() > 0);
        assertEquals("测试甲", JsonPath.read(body, "$.customerName"));
        assertEquals("测试卡项", JsonPath.read(body, "$.itemName"));
    }

    @Test
    void sameDaySecondBookingRejectedByFrequencyCap() {
        String phone = "13900001002";
        assertEquals(HttpStatus.OK, book("{\"phone\":\"" + phone + "\",\"startTime\":\"" + at(1, 11, 0) + "\"}").getStatusCode());
        var resp = book("{\"phone\":\"" + phone + "\",\"startTime\":\"" + at(1, 15, 0) + "\"}");
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals("当日已有预约，请到店或致电改约", JsonPath.read(resp.getBody(), "$.errorMessage"));
    }

    @Test
    void crossDaySamePhoneMergesToSameCustomer() {
        String phone = "13900001003";
        var first = book("{\"phone\":\"" + phone + "\",\"startTime\":\"" + at(1, 9, 0) + "\"}");
        var second = book("{\"phone\":\"" + phone + "\",\"startTime\":\"" + at(2, 9, 0) + "\"}");
        assertEquals(HttpStatus.OK, first.getStatusCode());
        assertEquals(HttpStatus.OK, second.getStatusCode());
        Number firstId = JsonPath.read(first.getBody(), "$.customerId");
        Number secondId = JsonPath.read(second.getBody(), "$.customerId");
        assertEquals(firstId.longValue(), secondId.longValue());
    }

    @Test
    void sameSlotSecondBookingRejectedBySlotCap() {
        // 每时段限约 1 位：自助约期未指派医师，空组互斥——不同手机号约同一整点，第二条 400
        // 用 16 点独占整点：跨零点跑测试时 LocalDate 漂移会让 plusDays(1)==plusDays(2) 同日，独占小时避免与其他用例撞格
        assertEquals(HttpStatus.OK, book("{\"phone\":\"13900002001\",\"startTime\":\"" + at(2, 16, 0) + "\"}").getStatusCode());
        var resp = book("{\"phone\":\"13900002002\",\"startTime\":\"" + at(2, 16, 0) + "\"}");
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals("该时段已约满（每时段限约 1 位），请换医师或时段", JsonPath.read(resp.getBody(), "$.errorMessage"));
    }

    @Test
    void nonWholeHourRejected() {
        var resp = book("{\"phone\":\"13900001004\",\"startTime\":\"" + at(1, 10, 30) + "\"}");
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals("预约时段须为整点", JsonPath.read(resp.getBody(), "$.errorMessage"));
    }

    @Test
    void pastTimeRejected() {
        var resp = book("{\"phone\":\"13900001005\",\"startTime\":\"" + at(-1, 10, 0) + "\"}");
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals("预约时段须晚于当前时刻", JsonPath.read(resp.getBody(), "$.errorMessage"));
    }

    @Test
    void outOfRangeHourRejected() {
        var resp = book("{\"phone\":\"13900001006\",\"startTime\":\"" + at(1, 18, 0) + "\"}");
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals("可约时段为每日 09:00–17:00 整点", JsonPath.read(resp.getBody(), "$.errorMessage"));
    }

    @Test
    void badPhoneRejected() {
        var resp = book("{\"phone\":\"123\",\"startTime\":\"" + at(1, 10, 0) + "\"}");
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals("手机号格式不对：应为 1 开头的 11 位数字", JsonPath.read(resp.getBody(), "$.errorMessage"));
    }

    @Test
    void missingStartTimeRejected() {
        assertEquals(HttpStatus.BAD_REQUEST, book("{\"phone\":\"13900001007\"}").getStatusCode());
    }

    @Test
    void missingItemFallsBackToDecideOnSite() {
        var resp = book("{\"phone\":\"13900001008\",\"startTime\":\"" + at(1, 12, 0) + "\"}");
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNull(JsonPath.read(resp.getBody(), "$.itemName"));
    }
}
