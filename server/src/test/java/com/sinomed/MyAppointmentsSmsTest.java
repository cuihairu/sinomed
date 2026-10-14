package com.sinomed;

import com.jayway.jsonpath.JsonPath;
import com.sinomed.entity.AppointmentEntity;
import com.sinomed.entity.CardLookupLogEntity;
import com.sinomed.entity.CustomerEntity;
import com.sinomed.entity.ItemEntity;
import com.sinomed.repository.AppointmentRepository;
import com.sinomed.repository.CardLookupLogRepository;
import com.sinomed.repository.CustomerRepository;
import com.sinomed.repository.ItemRepository;
import com.sinomed.repository.SmsCodeRepository;
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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 手机自助查预约（跨设备）：手机号+验证码双因子（与查卡同一套一次性验证码），
 * 只回未过期（今天 0 点起）的待到店预约（status=0）；错码/缺身份拒绝、无档案拒绝，均落审计。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:sqlite:target/my-appointments-test-${random.uuid}.db",
        "sinomed.phone-lookup.mode=sms_verified"
})
class MyAppointmentsSmsTest {

    @LocalServerPort
    private int port;

    private final RestTemplate rest = new RestTemplate();

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private SmsCodeRepository smsCodeRepository;

    @Autowired
    private CardLookupLogRepository cardLookupLogRepository;

    private static final String PHONE = "13900005001";
    private static Long customerId;

    @BeforeEach
    void seed() {
        if (customerId == null) {
            ItemEntity item = new ItemEntity();
            item.setName("查预约测试项");
            item.setDescription("");
            item.setPrice(199);
            item.setEnabled(1);
            item.setSort(0);
            Long itemId = itemRepository.save(item).getId();

            CustomerEntity customer = new CustomerEntity();
            customer.setName("查预约顾客");
            customer.setPhone(PHONE);
            customerId = customerRepository.save(customer).getId();

            // 待到店且未过期（明天 09:00，带项目）
            appointmentRepository.save(appt(customerId, itemId,
                    LocalDateTime.now().plusDays(1).withHour(9).withMinute(0), 0));
            // 昨日待到店（已过期，不回）
            appointmentRepository.save(appt(customerId, itemId,
                    LocalDateTime.now().minusDays(1).withHour(10).withMinute(0), 0));
            // 明天但已到店（status=1，不回）
            appointmentRepository.save(appt(customerId, itemId,
                    LocalDateTime.now().plusDays(1).withHour(15).withMinute(0), 1));
            // 明天待到店但约期未选项目（itemName 回 null，不造假数据）
            appointmentRepository.save(appt(customerId, null,
                    LocalDateTime.now().plusDays(2).withHour(11).withMinute(0), 0));
        }
        cardLookupLogRepository.deleteAll();
    }

    private static AppointmentEntity appt(Long cid, Long itemId, LocalDateTime start, int status) {
        AppointmentEntity appt = new AppointmentEntity();
        appt.setCustomerId(cid);
        appt.setItemId(itemId);
        appt.setStaffId(null);
        appt.setStartTime(Date.from(start.atZone(ZoneId.systemDefault()).toInstant()));
        appt.setDuration(60);
        appt.setStatus(status);
        appt.setRemark("小程序自助");
        return appt;
    }

    private ResponseEntity<String> post(String path, String body) {
        var req = RequestEntity.post(URI.create("http://localhost:" + port + path))
                .contentType(MediaType.APPLICATION_JSON).body(body);
        try {
            return rest.exchange(req, String.class);
        } catch (HttpClientErrorException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        }
    }

    /** 演示无短信网关：走 sms-code 桩发码后从库取码（与查卡测试同一取码口径） */
    private String issueCode() {
        post("/api/v1/kiosk/sms-code", "{\"phone\":\"" + PHONE + "\"}");
        return smsCodeRepository.findFirstByPhoneOrderByIdDesc(PHONE).orElseThrow().getCode();
    }

    @Test
    void returnsUpcomingPendingAppointmentsOnly() {
        String code = issueCode();
        var resp = post("/api/v1/kiosk/my-appointments",
                "{\"phone\":\"" + PHONE + "\",\"smsCode\":\"" + code + "\"}");
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        List<?> appts = JsonPath.read(resp.getBody(), "$.appointments");
        assertEquals(2, appts.size());
        String startTime = JsonPath.read(resp.getBody(), "$.appointments[0].startTime");
        assertTrue(startTime.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}"), startTime);
        // 新约在前：index 0 是未选项目的预约（itemName 回 null），index 1 是带项目的
        assertNull(JsonPath.read(resp.getBody(), "$.appointments[0].itemName"));
        assertEquals("查预约测试项", JsonPath.read(resp.getBody(), "$.appointments[1].itemName"));
        assertEquals(0, ((Number) JsonPath.read(resp.getBody(), "$.appointments[0].status")).intValue());
    }

    @Test
    void wrongCodeRejectedWithAudit() {
        issueCode();
        var resp = post("/api/v1/kiosk/my-appointments",
                "{\"phone\":\"" + PHONE + "\",\"smsCode\":\"000000\"}");
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals("验证码不对，请核对后重试", JsonPath.read(resp.getBody(), "$.errorMessage"));
        List<CardLookupLogEntity> logs = cardLookupLogRepository.findAll();
        assertEquals(1, logs.size());
        assertEquals("bad_code", logs.get(0).getResult());
        assertEquals(PHONE, logs.get(0).getPhone());
    }

    @Test
    void missingIdentityRejected() {
        var resp = post("/api/v1/kiosk/my-appointments", "{\"phone\":\"" + PHONE + "\"}");
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals("需要手机号与短信验证码才能查询预约", JsonPath.read(resp.getBody(), "$.errorMessage"));
    }

    @Test
    void successLeavesOkAudit() {
        String code = issueCode();
        post("/api/v1/kiosk/my-appointments", "{\"phone\":\"" + PHONE + "\",\"smsCode\":\"" + code + "\"}");
        List<CardLookupLogEntity> logs = cardLookupLogRepository.findAll();
        assertEquals(1, logs.size());
        CardLookupLogEntity okLog = logs.get(0);
        assertEquals("ok", okLog.getResult());
        assertEquals("sms_verified", okLog.getMode());
        assertEquals(customerId, okLog.getCustomerId());
        assertEquals(PHONE, okLog.getPhone());
    }
}
