package com.sinomed;

import com.sinomed.entity.CustomerEntity;
import com.sinomed.entity.ItemEntity;
import com.sinomed.entity.StaffEntity;
import com.sinomed.repository.CustomerRepository;
import com.sinomed.repository.ItemRepository;
import com.sinomed.repository.StaffRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 预约「技师+工位」双维冲突回归（美容业态 b，见 docs/design/salon-extension.md §2-b）：
 * 同工位同小时换技师 → 拒（工位维互斥）；同工位不同小时 → 过；不同工位同小时 → 过；
 * 不带工位时医师单维旧口径不变（同小时同医师拒）。
 * 预约携带 roomId 应保存并可回读。走真实 HTTP。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.datasource.url=jdbc:sqlite:target/appointment-room-conflict-http-test-${random.uuid}.db"})
class AppointmentRoomConflictHttpTest {

    @LocalServerPort
    private int port;

    @Autowired
    private StaffRepository staffRepository;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private ItemRepository itemRepository;

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

    /** 相对今天 plusDays 天、hour 点的 ISO 本地时刻（整点，保证同小时测试落在同一冲突桶） */
    private String at(int plusDays, int hour) {
        return LocalDate.now().plusDays(plusDays).atTime(hour, 0).toString() + ":00";
    }

    private Map<String, Object> seed(String tag) {
        StaffEntity s1 = StaffEntity.getInstanceWithDefault(tag + "-s1");
        StaffEntity s2 = StaffEntity.getInstanceWithDefault(tag + "-s2");
        StaffEntity s3 = StaffEntity.getInstanceWithDefault(tag + "-s3");
        StaffEntity s4 = StaffEntity.getInstanceWithDefault(tag + "-s4");
        s1 = staffRepository.save(s1);
        s2 = staffRepository.save(s2);
        s3 = staffRepository.save(s3);
        s4 = staffRepository.save(s4);

        CustomerEntity customer = new CustomerEntity();
        customer.setName(tag + "-顾客");
        customer = customerRepository.save(customer);

        ItemEntity item = new ItemEntity();
        item.setName(tag + "-卡项");
        item.setDescription("");
        item.setPrice(99);
        item.setEnabled(1);
        item.setSort(0);
        item = itemRepository.save(item);

        Map<String, Object> ids = new HashMap<>();
        ids.put("customerId", customer.getId());
        ids.put("itemId", item.getId());
        ids.put("staff1", s1.getId());
        ids.put("staff2", s2.getId());
        ids.put("staff3", s3.getId());
        ids.put("staff4", s4.getId());
        return ids;
    }

    private Map<?, ?> createRoom(String token, String name) {
        return rest.exchange(url("/api/v1/room/"), HttpMethod.POST,
                authEntity(Map.of("name", name), token), Map.class).getBody();
    }

    /** 建预约：返回 Map.of("ok", true/false, "body", 响应或异常消息) */
    private Map<String, Object> postAppointment(String token, Map<String, Object> ids,
                                                Long staffId, Long roomId, String startTime) {
        Map<String, Object> body = new HashMap<>(Map.of(
                "customerId", ids.get("customerId"),
                "itemId", ids.get("itemId"),
                "startTime", startTime));
        if (staffId != null) {
            body.put("staffId", staffId);
        }
        if (roomId != null) {
            body.put("roomId", roomId);
        }
        try {
            Map<?, ?> resp = rest.exchange(url("/api/v1/appointment/"), HttpMethod.POST,
                    authEntity(body, token), Map.class).getBody();
            return Map.of("ok", true, "body", resp);
        } catch (HttpClientErrorException e) {
            return Map.of("ok", false, "status", e.getStatusCode().value());
        }
    }

    @Test
    void roomIdSavedAndReadBack() {
        String token = token();
        Map<String, Object> ids = seed("room-save-" + System.nanoTime());
        Map<?, ?> room = createRoom(token, "回读工位-" + System.nanoTime());

        Map<String, Object> resp = postAppointment(token, ids,
                (Long) ids.get("staff1"), ((Number) room.get("id")).longValue(), at(3, 10));
        assertTrue((Boolean) resp.get("ok"), "首次预约应成功：" + resp);
        Map<?, ?> created = (Map<?, ?>) resp.get("body");
        long roomId = ((Number) room.get("id")).longValue();
        assertEquals(roomId, ((Number) created.get("roomId")).longValue(), "roomId 应随预约保存：" + created);

        // 分页联表读回：roomId 与 roomName 都能带出
        Map<?, ?> page = rest.exchange(url("/api/v1/appointment/page?current=1&pageSize=50"), HttpMethod.GET,
                authEntity(null, token), Map.class).getBody();
        long apptId = ((Number) created.get("id")).longValue();
        Map<?, ?> row = ((java.util.List<Map<?, ?>>) page.get("data")).stream()
                .filter(r -> ((Number) r.get("id")).longValue() == apptId)
                .findFirst().orElseThrow();
        assertEquals(roomId, ((Number) row.get("roomId")).longValue(), "分页应带出 roomId：" + row);
        assertNotNull(row.get("roomName"), "分页应联出 roomName：" + row);
    }

    @Test
    void sameRoomSameHourDifferentStaffRejected() {
        String token = token();
        Map<String, Object> ids = seed("room-conflict-" + System.nanoTime());
        Map<?, ?> room = createRoom(token, "冲突工位-" + System.nanoTime());
        long roomId = ((Number) room.get("id")).longValue();

        Map<String, Object> first = postAppointment(token, ids,
                (Long) ids.get("staff1"), roomId, at(3, 10));
        assertTrue((Boolean) first.get("ok"), "技师一占工位应成功：" + first);

        Map<String, Object> second = postAppointment(token, ids,
                (Long) ids.get("staff2"), roomId, at(3, 10));
        assertTrue(!(Boolean) second.get("ok") && ((Number) second.get("status")).intValue() == 400,
                "同工位同小时换技师应 400（工位维互斥）：" + second);
    }

    @Test
    void sameRoomDifferentHourAllowed() {
        String token = token();
        Map<String, Object> ids = seed("room-hour-" + System.nanoTime());
        Map<?, ?> room = createRoom(token, "错时工位-" + System.nanoTime());
        long roomId = ((Number) room.get("id")).longValue();

        Map<String, Object> first = postAppointment(token, ids,
                (Long) ids.get("staff1"), roomId, at(3, 10));
        assertTrue((Boolean) first.get("ok"), "占 10 点工位应成功：" + first);

        Map<String, Object> second = postAppointment(token, ids,
                (Long) ids.get("staff2"), roomId, at(3, 12));
        assertTrue((Boolean) second.get("ok"), "同工位不同小时应放行：" + second);
    }

    @Test
    void differentRoomSameHourAllowed() {
        String token = token();
        Map<String, Object> ids = seed("room-split-" + System.nanoTime());
        long room1 = ((Number) createRoom(token, "并行工位一-" + System.nanoTime()).get("id")).longValue();
        long room2 = ((Number) createRoom(token, "并行工位二-" + System.nanoTime()).get("id")).longValue();

        Map<String, Object> first = postAppointment(token, ids,
                (Long) ids.get("staff1"), room1, at(3, 10));
        assertTrue((Boolean) first.get("ok"), "工位一 10 点应成功：" + first);

        Map<String, Object> second = postAppointment(token, ids,
                (Long) ids.get("staff2"), room2, at(3, 10));
        assertTrue((Boolean) second.get("ok"), "同小时不同工位应放行：" + second);
    }

    @Test
    void staffDimensionStillEnforcedWithoutRoom() {
        String token = token();
        Map<String, Object> ids = seed("room-legacy-" + System.nanoTime());

        Map<String, Object> first = postAppointment(token, ids,
                (Long) ids.get("staff3"), null, at(3, 10));
        assertTrue((Boolean) first.get("ok"), "无工位预约（医馆旧口径）应成功：" + first);

        Map<String, Object> sameStaff = postAppointment(token, ids,
                (Long) ids.get("staff3"), null, at(3, 10));
        assertTrue(!(Boolean) sameStaff.get("ok"), "同医师同小时仍应互斥（旧口径不回退）：" + sameStaff);

        Map<String, Object> otherStaff = postAppointment(token, ids,
                (Long) ids.get("staff4"), null, at(3, 10));
        assertTrue((Boolean) otherStaff.get("ok"), "不同医师无工位同小时应放行（互斥在工位维为空组时不参与）：" + otherStaff);
    }
}
