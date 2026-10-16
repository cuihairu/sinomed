package com.sinomed;

import com.jayway.jsonpath.JsonPath;
import com.sinomed.entity.AppointmentEntity;
import com.sinomed.entity.CardLookupLogEntity;
import com.sinomed.entity.CustomerEntity;
import com.sinomed.entity.ItemEntity;
import com.sinomed.entity.SmsCodeEntity;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 手机自助取消预约（POST /api/v1/kiosk/appointment-cancel）回归：手机号+验证码双因子（同 my-appointments
 * 一次性验证码通道）只允许取消本人「待到店且未过期」的预约；错码/缺身份/缺预约 id/非本人/已受理/已过期/
 * 已取消/未知预约均 400，全部落 card_lookup_logs 审计。
 *
 * <p>口径见 docs/design/app-booking.md（2026-10-16 代决：原拍板 2 的前提「取消不核验身份」已因
 * 2026-10-14/15 手机号+一次性验证码双因子与 openid 绑定落地而失效）。
 *
 * <p>库路径用随机文件，避免与其他用例的库互相污染；每个用例自建预约，避免用例间状态串扰。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:sqlite:target/appt-cancel-test-${random.uuid}.db",
        "sinomed.phone-lookup.mode=sms_verified"
})
class KioskAppointmentCancelTest {

    @LocalServerPort
    private int port;

    private final RestTemplate rest = new RestTemplate();

    @Autowired
    private ItemRepository itemRepository;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private AppointmentRepository appointmentRepository;
    @Autowired
    private SmsCodeRepository smsCodeRepository;
    @Autowired
    private CardLookupLogRepository cardLookupLogRepository;

    private static final String PHONE = "13900006001";
    private static final String OTHER_PHONE = "13900006002";
    private static final String UNKNOWN_PHONE = "13900006999";
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static Long customerId;
    private static Long otherCustomerId;
    private static Long itemId;

    @BeforeEach
    void seed() {
        if (customerId == null) {
            ItemEntity item = new ItemEntity();
            item.setName("取消预约测试项");
            item.setDescription("");
            item.setPrice(199);
            item.setEnabled(1);
            item.setSort(0);
            itemId = itemRepository.save(item).getId();

            CustomerEntity me = new CustomerEntity();
            me.setName("取消预约顾客");
            me.setPhone(PHONE);
            customerId = customerRepository.save(me).getId();

            CustomerEntity other = new CustomerEntity();
            other.setName("他人顾客");
            other.setPhone(OTHER_PHONE);
            otherCustomerId = customerRepository.save(other).getId();
        }
        smsCodeRepository.deleteAll();
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

    private String at(int plusDays, int hour, int minute) {
        return LocalDate.now().plusDays(plusDays).format(DAY)
                + " " + String.format("%02d:%02d", hour, minute);
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

    /** 演示无短信网关：走 sms-code 桩发码后从库取码（与查卡/查预约测试同一取码口径） */
    private String issueCode(String phone) {
        post("/api/v1/kiosk/sms-code", "{\"phone\":\"" + phone + "\"}");
        return smsCodeRepository.findFirstByPhoneOrderByIdDesc(phone).orElseThrow().getCode();
    }

    private int statusOf(Long appointmentId) {
        return appointmentRepository.findById(appointmentId).orElseThrow().getStatus();
    }

    private Long newPending(int plusDays, int hour) {
        return appointmentRepository.save(appt(customerId, itemId,
                LocalDateTime.now().plusDays(plusDays).withHour(hour).withMinute(0), 0)).getId();
    }

    private String cancelBody(String phone, String code, Long appointmentId) {
        return "{\"phone\":\"" + phone + "\",\"smsCode\":\"" + code + "\",\"appointmentId\":" + appointmentId + "}";
    }

    @Test
    void cancelsOwnPendingAppointment() {
        Long pendingId = newPending(1, 9);
        String code = issueCode(PHONE);
        var resp = post("/api/v1/kiosk/appointment-cancel", cancelBody(PHONE, code, pendingId));
        assertEquals(HttpStatus.OK, resp.getStatusCode(), resp.getBody());
        String message = JsonPath.read(resp.getBody(), "$.message");
        assertTrue(message.contains("已取消"), message);
        assertEquals(9, statusOf(pendingId));
        List<CardLookupLogEntity> logs = cardLookupLogRepository.findAll();
        assertEquals(1, logs.size());
        assertEquals("cancel_ok", logs.get(0).getResult());
        assertEquals(PHONE, logs.get(0).getPhone());
        assertEquals(customerId, logs.get(0).getCustomerId());
        assertEquals("sms_verified", logs.get(0).getMode());
    }

    @Test
    void wrongCodeRejectedWithAuditAndNoChange() {
        Long pendingId = newPending(1, 10);
        issueCode(PHONE);
        var resp = post("/api/v1/kiosk/appointment-cancel", cancelBody(PHONE, "000000", pendingId));
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals("验证码不对，请核对后重试", JsonPath.read(resp.getBody(), "$.errorMessage"));
        assertEquals(0, statusOf(pendingId));
        List<CardLookupLogEntity> logs = cardLookupLogRepository.findAll();
        assertEquals(1, logs.size());
        assertEquals("bad_code", logs.get(0).getResult());
        assertEquals(PHONE, logs.get(0).getPhone());
    }

    @Test
    void usedCodeRejected() {
        Long pendingId = newPending(2, 9);
        String code = issueCode(PHONE);
        // 先用同码查预约（验证码一次性），再拿同码取消
        assertEquals(HttpStatus.OK, post("/api/v1/kiosk/my-appointments",
                "{\"phone\":\"" + PHONE + "\",\"smsCode\":\"" + code + "\"}").getStatusCode());
        var resp = post("/api/v1/kiosk/appointment-cancel", cancelBody(PHONE, code, pendingId));
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals("验证码已使用，请重新获取", JsonPath.read(resp.getBody(), "$.errorMessage"));
        assertEquals(0, statusOf(pendingId));
        List<CardLookupLogEntity> logs = cardLookupLogRepository.findAll();
        assertEquals(2, logs.size());
        assertEquals("used_code", logs.get(1).getResult());
    }

    @Test
    void missingCodeRejected() {
        Long pendingId = newPending(1, 11);
        var resp = post("/api/v1/kiosk/appointment-cancel",
                "{\"phone\":\"" + PHONE + "\",\"appointmentId\":" + pendingId + "}");
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals("需要手机号与短信验证码才能取消预约", JsonPath.read(resp.getBody(), "$.errorMessage"));
        assertEquals(0, statusOf(pendingId));
        List<CardLookupLogEntity> logs = cardLookupLogRepository.findAll();
        assertEquals(1, logs.size());
        assertEquals("missing_code", logs.get(0).getResult());
    }

    @Test
    void missingAppointmentIdDoesNotBurnCode() {
        Long pendingId = newPending(5, 14);
        String code = issueCode(PHONE);
        var resp = post("/api/v1/kiosk/appointment-cancel", "{\"phone\":\"" + PHONE + "\",\"smsCode\":\"" + code + "\"}");
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals("缺少要取消的预约 id", JsonPath.read(resp.getBody(), "$.errorMessage"));
        assertEquals("missing_appointment", cardLookupLogRepository.findAll().get(0).getResult());
        // 缺 id 不烧码：同一码随后仍可正常取消
        SmsCodeEntity issued = smsCodeRepository.findFirstByPhoneOrderByIdDesc(PHONE).orElseThrow();
        assertEquals(0, issued.getUsed().intValue());
        assertEquals(HttpStatus.OK, post("/api/v1/kiosk/appointment-cancel", cancelBody(PHONE, code, pendingId)).getStatusCode());
        assertEquals(9, statusOf(pendingId));
    }

    @Test
    void badPhoneRejected() {
        Long pendingId = newPending(1, 12);
        var resp = post("/api/v1/kiosk/appointment-cancel", cancelBody("123", "123456", pendingId));
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals("手机号格式不对：应为 1 开头的 11 位数字", JsonPath.read(resp.getBody(), "$.errorMessage"));
        assertEquals(0, statusOf(pendingId));
    }

    @Test
    void unknownAppointmentRejected() {
        String code = issueCode(PHONE);
        var resp = post("/api/v1/kiosk/appointment-cancel", cancelBody(PHONE, code, 999999L));
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals("预约不存在：999999", JsonPath.read(resp.getBody(), "$.errorMessage"));
        assertEquals("missing_appointment", cardLookupLogRepository.findAll().get(0).getResult());
    }

    @Test
    void anotherCustomersAppointmentRejected() {
        Long otherPendingId = appointmentRepository.save(appt(otherCustomerId, itemId,
                LocalDateTime.now().plusDays(3).withHour(11).withMinute(0), 0)).getId();
        String code = issueCode(PHONE);
        var resp = post("/api/v1/kiosk/appointment-cancel", cancelBody(PHONE, code, otherPendingId));
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals("该预约不属于此手机号，请到前台处理", JsonPath.read(resp.getBody(), "$.errorMessage"));
        assertEquals(0, statusOf(otherPendingId));
        List<CardLookupLogEntity> logs = cardLookupLogRepository.findAll();
        assertEquals(1, logs.size());
        assertEquals("not_owner", logs.get(0).getResult());
    }

    @Test
    void receivedAppointmentRejected() {
        Long receivedId = appointmentRepository.save(appt(customerId, itemId,
                LocalDateTime.now().plusDays(1).withHour(15).withMinute(0), 1)).getId();
        String code = issueCode(PHONE);
        var resp = post("/api/v1/kiosk/appointment-cancel", cancelBody(PHONE, code, receivedId));
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals("该预约已受理或已过号，不能自助取消，请到前台处理",
                JsonPath.read(resp.getBody(), "$.errorMessage"));
        assertEquals(1, statusOf(receivedId));
        assertEquals("not_cancelable", cardLookupLogRepository.findAll().get(0).getResult());
    }

    @Test
    void alreadyCancelledAppointmentRejected() {
        Long cancelledId = appointmentRepository.save(appt(customerId, itemId,
                LocalDateTime.now().plusDays(2).withHour(10).withMinute(0), 9)).getId();
        String code = issueCode(PHONE);
        var resp = post("/api/v1/kiosk/appointment-cancel", cancelBody(PHONE, code, cancelledId));
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals("该预约已取消，无需重复取消", JsonPath.read(resp.getBody(), "$.errorMessage"));
        assertEquals(9, statusOf(cancelledId));
        assertEquals("not_cancelable", cardLookupLogRepository.findAll().get(0).getResult());
    }

    @Test
    void expiredPendingAppointmentRejected() {
        Long expiredId = appointmentRepository.save(appt(customerId, itemId,
                LocalDateTime.now().minusDays(1).withHour(10).withMinute(0), 0)).getId();
        String code = issueCode(PHONE);
        var resp = post("/api/v1/kiosk/appointment-cancel", cancelBody(PHONE, code, expiredId));
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals("预约时间已过，不能自助取消", JsonPath.read(resp.getBody(), "$.errorMessage"));
        assertEquals(0, statusOf(expiredId));
        assertEquals("not_cancelable", cardLookupLogRepository.findAll().get(0).getResult());
    }

    @Test
    void noCustomerRejected() {
        String code = issueCode(UNKNOWN_PHONE);
        var resp = post("/api/v1/kiosk/appointment-cancel", cancelBody(UNKNOWN_PHONE, code, 1L));
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals("该手机号无顾客档案，请到前台建档", JsonPath.read(resp.getBody(), "$.errorMessage"));
        List<CardLookupLogEntity> logs = cardLookupLogRepository.findAll();
        assertEquals(1, logs.size());
        assertEquals("no_customer", logs.get(0).getResult());
        assertEquals(UNKNOWN_PHONE, logs.get(0).getPhone());
    }

    @Test
    void cancelledSlotFreesForRebooking() {
        // 每时段限约 1 位（空组互斥）：先约一格，他人同格被拒；取消占用后同格重新放行
        String slotPhone = "13900006101";
        String otherSlotPhone = "13900006102";
        String slot = at(4, 16, 0);
        var first = post("/api/v1/kiosk/appointments",
                "{\"phone\":\"" + slotPhone + "\",\"startTime\":\"" + slot + "\"}");
        assertEquals(HttpStatus.OK, first.getStatusCode(), first.getBody());
        Number bookedId = JsonPath.read(first.getBody(), "$.appointmentId");

        var blocked = post("/api/v1/kiosk/appointments",
                "{\"phone\":\"" + otherSlotPhone + "\",\"startTime\":\"" + slot + "\"}");
        assertEquals(HttpStatus.BAD_REQUEST, blocked.getStatusCode());
        assertEquals("该时段已约满（每时段限约 1 位），请换医师或时段",
                JsonPath.read(blocked.getBody(), "$.errorMessage"));

        String code = issueCode(slotPhone);
        assertEquals(HttpStatus.OK, post("/api/v1/kiosk/appointment-cancel",
                cancelBody(slotPhone, code, bookedId.longValue())).getStatusCode());

        assertEquals(HttpStatus.OK, post("/api/v1/kiosk/appointments",
                "{\"phone\":\"" + otherSlotPhone + "\",\"startTime\":\"" + slot + "\"}").getStatusCode());
    }
}
