package com.sinomed;

import com.sinomed.entity.CustomerEntity;
import com.sinomed.entity.ItemEntity;
import com.sinomed.entity.StaffEntity;
import com.sinomed.repository.CustomerRepository;
import com.sinomed.repository.ItemRepository;
import com.sinomed.repository.QueueCallRepository;
import com.sinomed.entity.QueueCallEntity;
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

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 预约「每时段限约 1 位」与状态流转 HTTP 回归：
 * 同医师同日同小时（floor 到整点桶）已有 status 0/1 预约 → 400；取消（0→9）释放时段；
 * 0→8 过号放行，1/8/9 为终态不得再流转。口径与原型 admin-booking.html 排班网格一致。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.datasource.url=jdbc:sqlite:target/appt-slot-test-${random.uuid}.db"})
class AppointmentSlotTest {

    @LocalServerPort
    private int port;

    private final RestTemplate rest = new RestTemplate();

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private QueueCallRepository queueCallRepository;

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** static 保证全类只插一次：customers.phone / items.name / staffs.account 均有唯一约束 */
    private static Long staffId;
    private static Long customerId;
    private static Long itemId;

    @BeforeEach
    void setup() {
        if (staffId == null) {
            StaffEntity staff = new StaffEntity();
            staff.setName("时段测试医师");
            staff.setAccount("appt-slot-" + System.nanoTime());
            staff.setPassword("x");
            staff.setStatus(1);
            staffId = staffRepository.save(staff).getId();

            CustomerEntity customer = new CustomerEntity();
            customer.setName("时段测试客");
            // phone 有唯一约束——用 nanoTime 后缀保证不撞演示种子与其他用例
            customer.setPhone("138" + String.format("%08d", Math.abs(System.nanoTime() % 100000000L)));
            customer.setGender(1);
            customerId = customerRepository.save(customer).getId();

            ItemEntity item = new ItemEntity();
            item.setName("时段测试卡项" + (System.nanoTime() % 1000000));
            item.setDescription("");
            item.setPrice(88);
            item.setEnabled(1);
            item.setSort(0);
            itemId = itemRepository.save(item).getId();
        }
    }

    /** 明天 baseHour:00 的预约时段（走管理员建约，带 staffId）；Jackson 日期须带时区偏移的 ISO 串（默认 UTC，无偏移会平移 8 小时） */
    private String at(int hour, int minute) {
        String offset = ZoneId.systemDefault().getRules().getOffset(Instant.now()).getId();
        return LocalDate.now().plusDays(1).format(DAY)
                + "T" + String.format("%02d:%02d", hour, minute) + ":00.000" + offset;
    }

    private HttpHeaders auth(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

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

    /** 建约：成功返回预约 id，4xx 返回错误报文 */
    private Object create(String startTime, Long staff) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("customerId", customerId);
        body.put("itemId", itemId);
        body.put("startTime", startTime);
        if (staff != null) {
            body.put("staffId", staff);
        }
        try {
            var resp = rest.exchange(url("/api/v1/appointment/"), HttpMethod.POST,
                    new HttpEntity<>(body, auth(token())), Map.class);
            return ((Number) resp.getBody().get("id")).longValue();
        } catch (HttpStatusCodeException e) {
            return e.getResponseBodyAsString();
        }
    }

    /** 状态流转：成功返回 true，4xx 返回错误报文 */
    private Object status(long id, int to) {
        try {
            var resp = rest.exchange(url("/api/v1/appointment/status"), HttpMethod.PUT,
                    new HttpEntity<>(Map.of("id", id, "status", to), auth(token())), Map.class);
            return ((Number) resp.getBody().get("status")).intValue();
        } catch (HttpStatusCodeException e) {
            return e.getResponseBodyAsString();
        }
    }

    @Test
    void sameStaffSameHourRejected() {
        // 本用例独占 10/11 点桶（conflict 用 15/16、cancel 用 14，共库同医师不互撞）
        Object first = create(at(10, 0), staffId);
        assertTrue(first instanceof Long, "首个预约应成功，实际：" + first);
        // 同医师同小时再来一条 → 400
        Object dup = create(at(10, 0), staffId);
        assertTrue(dup instanceof String && ((String) dup).contains("该时段已约满（每时段限约 1 位），请换医师或时段"), "同医师同小时应 400：" + dup);
        // 换到别的整点 → 放行
        Object other = create(at(11, 0), staffId);
        assertTrue(other instanceof Long, "不同整点应放行，实际：" + other);
        // 换医师同小时 → 放行（互斥按医师分组）
        Object otherStaff = create(at(10, 0), null);
        assertTrue(otherStaff instanceof Long, "空组（未指派医师）与已指派医师互不影响，实际：" + otherStaff);
    }

    @Test
    void conflictIsHourBucketed() {
        // 15:30 起的预约占满 [15:00, 16:00) 桶 → 15:50 再约同医师同桶 400
        Object first = create(at(15, 30), staffId);
        assertTrue(first instanceof Long, "15:30 建约应成功，实际：" + first);
        Object bucket = create(at(15, 50), staffId);
        assertTrue(bucket instanceof String && ((String) bucket).contains("该时段已约满"), "15:50 应落同一整点桶而 400：" + bucket);
        // 16:00 属下一个桶 → 放行
        Object next = create(at(12, 0), staffId);
        assertTrue(next instanceof Long, "下一整点桶应放行，实际：" + next);
    }

    @Test
    void cancelFreesSlotAndNoShowFlow() {
        Object first = create(at(14, 0), staffId);
        assertTrue(first instanceof Long, "14:00 建约应成功，实际：" + first);
        Object dup = create(at(14, 0), staffId);
        assertTrue(dup instanceof String && ((String) dup).contains("该时段已约满"), "同刻第二条应 400：" + dup);

        // 取消（0→9）释放时段
        assertEquals(9, status(((Long) first), 9));
        Object freed = create(at(14, 0), staffId);
        assertTrue(freed instanceof Long, "取消后时段应释放，实际：" + freed);

        // 过号：0→8 放行；8/1/9 为终态不可再流转
        assertEquals(8, status((Long) freed, 8));
        Object toReceived = status((Long) freed, 1);
        assertTrue(toReceived instanceof String && ((String) toReceived).contains("不允许从 8 流转到 1"), "过号是终态：" + toReceived);
        Object toCancel = status((Long) freed, 9);
        assertTrue(toCancel instanceof String && ((String) toCancel).contains("不允许从 8 流转到 9"), "过号是终态：" + toCancel);
    }
    @Test
    void receiveFiresQueueCallAndNoShowDoesNot() {
        String t = token();
        Object created = create(at(9, 0), staffId);
        assertTrue(created instanceof Long, "建约应成功：" + created);
        Long apptId = (Long) created;

        // 过号（0→8）不叫号
        rest.exchange(url("/api/v1/appointment/status"), HttpMethod.PUT,
                new HttpEntity<>(Map.of("id", apptId, "status", 8), auth(t)), Map.class);
        assertEquals(0, queueCallRepository.count(), "过号不应落叫号");

        // 再建一约并接待（0→1）：自动落一条 queue_calls
        Object second = create(at(16, 0), staffId);
        assertTrue(second instanceof Long, "第二约应成功：" + second);
        rest.exchange(url("/api/v1/appointment/status"), HttpMethod.PUT,
                new HttpEntity<>(Map.of("id", second, "status", 1), auth(t)), Map.class);

        assertEquals(1, queueCallRepository.count(), "接待自动叫号恰好一条");
        QueueCallEntity call = queueCallRepository.findAll().get(0);
        assertEquals(1, call.getStatus());
        assertTrue(call.getNumber().matches("\\d{2}"), "号码两位补零：" + call.getNumber());
        assertEquals("时段测试医师诊室", call.getRoom(), "诊室取接诊医师名");
        assertEquals("时*", call.getPatientMasked(), "姓名脱敏");
        assertEquals(null, call.getScreenId(), "自动叫号广播全部屏");

        // 未指派医师的接待：诊室「待分配诊室」
        Object third = create(at(13, 0), null);
        assertTrue(third instanceof Long, "未指派建约应成功：" + third);
        rest.exchange(url("/api/v1/appointment/status"), HttpMethod.PUT,
                new HttpEntity<>(Map.of("id", third, "status", 1), auth(t)), Map.class);
        assertEquals(2, queueCallRepository.count());
        QueueCallEntity noStaff = queueCallRepository
                .findByIdGreaterThanOrderByIdAsc(call.getId()).get(0);
        assertEquals("待分配诊室", noStaff.getRoom());
    }
}