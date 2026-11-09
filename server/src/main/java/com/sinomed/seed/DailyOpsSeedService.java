package com.sinomed.seed;

import com.sinomed.entity.AppointmentEntity;
import com.sinomed.entity.ReviewCustomerEntity;
import com.sinomed.entity.ReviewEntity;
import com.sinomed.entity.ReviewStaffEntity;
import com.sinomed.entity.SignEntity;
import com.sinomed.repository.AppointmentRepository;
import com.sinomed.repository.ReviewCustomerRepository;
import com.sinomed.repository.ReviewRepository;
import com.sinomed.repository.ReviewStaffRepository;
import com.sinomed.repository.SignRepository;
import com.sinomed.util.DateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 每日运营种子（LAY-002 拆分）：每日复盘、顾客回访待办、员工日复盘、今日考勤打卡、
 * 预约（今日/明日/昨日取消）。按自然日判重，重复启动不重复补。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DailyOpsSeedService {

    private final AppointmentRepository appointmentRepository;
    private final ReviewRepository reviewRepository;
    private final ReviewCustomerRepository reviewCustomerRepository;
    private final ReviewStaffRepository reviewStaffRepository;
    private final SignRepository signRepository;
    private final SeedSupport support;

    /** 每日复盘：昨日一条，沉淀到报表 */
    public void seedReviews() {
        Date yesterday = support.daysAgo(1);
        boolean exists = reviewRepository.findAll().stream()
                .anyMatch(r -> r.getDay() != null && DateUtil.isSomeDay(r.getDay(), yesterday));
        if (exists) {
            return;
        }
        ReviewEntity review = new ReviewEntity();
        review.setDay(yesterday);
        review.setGood("今日到店 6 位，三位老客复诊记录均已完善；次卡核销 3 次。");
        review.setImprovement("午间高峰前台排队偏长，下周把预约时段打散；顾客回访有 1 位未接通，改约明日再访。");
        reviewRepository.save(review);
        log.info("种子·每日复盘：已写入");
    }

    /** 顾客回访：一条待办（回访日=明日）+ 一条已回访 */
    public void seedReviewCustomers(Map<String, Long> customers) {
        Long wang = customers.get("13900000001");
        Long li = customers.get("13900000002");
        if (wang != null && !reviewCustomerExists(wang, support.daysAhead(1))) {
            ReviewCustomerEntity rc = new ReviewCustomerEntity();
            rc.setName("王慕清");
            rc.setCustomerId(wang);
            rc.setLast(support.daysAgo(3));
            rc.setDay(support.daysAhead(1)); // 明日回访：出现在每日报表待办
            rc.setAdvice("复诊后回访：询问睡眠改善情况，提醒调理卡月底到期可续。");
            reviewCustomerRepository.save(rc);
        }
        if (li != null && !reviewCustomerExists(li, support.daysAgo(1))) {
            ReviewCustomerEntity rc = new ReviewCustomerEntity();
            rc.setName("李景和");
            rc.setCustomerId(li);
            rc.setLast(support.daysAgo(9));
            rc.setDay(support.daysAgo(1)); // 昨日回访：已完成
            rc.setAdvice("已回访：腰痛明显缓解，嘱继续硬板床与腰背锻炼，下周复诊。");
            reviewCustomerRepository.save(rc);
        }
        log.info("种子·顾客回访：检查完成");
    }

    /** 员工日复盘：昨日一条（耗卡/已做/建议） */
    public void seedReviewStaffs() {
        Date yesterday = support.daysAgo(1);
        Long shen = support.staffByAccount("shen");
        if (shen == null || reviewStaffExists(shen, yesterday)) {
            return;
        }
        ReviewStaffEntity rs = new ReviewStaffEntity();
        rs.setStaffId(shen);
        rs.setName("沈知远");
        rs.setCost(3);
        rs.setDone("卡项已有：次卡 12 张、季卡 2 张；今日做：推拿 2、艾灸 1。");
        rs.setAdvice("王慕清调理卡月底到期，建议本周沟通续季卡；新客陈屿体质辨识后可引导办次卡。");
        rs.setDay(yesterday);
        reviewStaffRepository.save(rs);
        log.info("种子·员工复盘：已写入");
    }

    /** 考勤（诊所运营）：今日上班打卡各一条 */
    public void seedSigns() {
        if (signRepository.count() > 0) {
            return;
        }
        for (String account : List.of("gu", "shen", "su")) {
            Long staffId = support.staffByAccount(account);
            if (staffId == null) {
                continue;
            }
            SignEntity sign = new SignEntity();
            sign.setStaffId(staffId);
            sign.setType(1); // 1 上班
            signRepository.save(sign);
        }
        log.info("种子·考勤：已写入今日上班打卡");
    }

    /** 预约：今日 3 条（1 条已到店）+ 明日 1 条 + 昨日取消 1 条，逐条按顾客+当日判重 */
    public void seedAppointments(Map<String, Long> customers) {
        record ApptSeed(String phone, String staffAccount, String itemName,
                        int dayOffset, int hour, int minute, int duration, int status, String remark) {}
        List<ApptSeed> seeds = List.of(
                new ApptSeed("13900000001", "shen", "艾灸温阳调理（5 次卡）", 0, 9, 30, 60, 1, "艾灸第 4 次复诊，已到店"),
                new ApptSeed("13900000002", null, null, 0, 11, 0, 60, 0, "肩颈不适初诊"),
                new ApptSeed("13900000004", "shen", "经络推拿（10 次卡）", 0, 15, 0, 60, 0, null),
                new ApptSeed("13900000003", "su", "中医体质辨识（单次）", 1, 10, 0, 30, 0, "首次到店体验"),
                new ApptSeed("13900000005", null, null, -1, 16, 0, 60, 9, "临时改期，改约下周")
        );
        for (ApptSeed a : seeds) {
            Long customerId = customers.get(a.phone());
            if (customerId == null || appointmentOnDayExists(customerId, a.dayOffset())) {
                continue;
            }
            AppointmentEntity appointment = new AppointmentEntity();
            appointment.setCustomerId(customerId);
            appointment.setStaffId(a.staffAccount() == null ? null : support.staffByAccount(a.staffAccount()));
            appointment.setItemId(a.itemName() == null ? null : support.itemByName(a.itemName()));
            appointment.setStartTime(support.at(a.dayOffset(), a.hour(), a.minute()));
            appointment.setDuration(a.duration());
            appointment.setStatus(a.status());
            appointment.setRemark(a.remark());
            appointmentRepository.save(appointment);
        }
        log.info("种子·预约：检查完成");
    }

    /** 该顾客在 offset 天（0=今天）当日是否已有预约（按自然日判重，时段不同也视同已约） */
    private boolean appointmentOnDayExists(Long customerId, int dayOffset) {
        return appointmentRepository.findAll().stream()
                .anyMatch(a -> customerId.equals(a.getCustomerId())
                        && a.getStartTime() != null
                        && DateUtil.isSomeDay(a.getStartTime(), support.at(dayOffset, 12, 0)));
    }

    private boolean reviewCustomerExists(Long customerId, Date day) {
        return reviewCustomerRepository.findAll().stream()
                .anyMatch(rc -> customerId.equals(rc.getCustomerId())
                        && rc.getDay() != null && DateUtil.isSomeDay(rc.getDay(), day));
    }

    private boolean reviewStaffExists(Long staffId, Date day) {
        return reviewStaffRepository.findAll().stream()
                .anyMatch(rs -> staffId.equals(rs.getStaffId())
                        && rs.getDay() != null && DateUtil.isSomeDay(rs.getDay(), day));
    }
}
