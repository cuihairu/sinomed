package com.sinomed.seed;

import com.sinomed.entity.CustomerEntity;
import com.sinomed.entity.CustomerHistoryEntity;
import com.sinomed.entity.TreatEntity;
import com.sinomed.repository.CustomerHistoryRepository;
import com.sinomed.repository.CustomerRepository;
import com.sinomed.repository.TreatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 顾客域种子（LAY-002 拆分）：顾客建档（回写建档时间线）、中医诊疗记录（含针灸处方补录）、
 * 病史（过敏/既往）。逐条按自然键（手机号/顾客）判重。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerSeedService {

    private final CustomerRepository customerRepository;
    private final TreatRepository treatRepository;
    private final CustomerHistoryRepository historyRepository;
    private final JdbcTemplate jdbcTemplate;
    private final SeedSupport support;

    /** 顾客建档：虚构脱敏人名，手机号取明显虚构号段 */
    public Map<String, Long> seedCustomers() {
        record CustomerSeed(String name, int gender, int age, int level,
                            String phone, String address, int birthYear, int birthMonth, int birthDay,
                            int registeredDaysAgo) {}
        List<CustomerSeed> seeds = List.of(
                new CustomerSeed("王慕清", 0, 34, 2, "13900000001", "南京市秦淮区", 1992, 3, 14, 30),
                new CustomerSeed("李景和", 1, 47, 3, "13900000002", "苏州市姑苏区", 1979, 8, 2, 14),
                new CustomerSeed("陈屿", 1, 29, 1, "13900000003", "杭州市上城区", 1997, 11, 26, 21),
                new CustomerSeed("赵蘅", 0, 52, 3, "13900000004", "泰州市海陵区", 1974, 5, 9, 10),
                new CustomerSeed("孙泽宇", 1, 41, 1, "13900000005", "无锡市梁溪区", 1985, 1, 20, 5),
                new CustomerSeed("吴清禾", 0, 38, 2, "13900000006", "常州市天宁区", 1988, 7, 7, 16)
        );
        Map<String, Long> byPhone = new HashMap<>();
        for (CustomerSeed s : seeds) {
            Long existing = customerByPhone(s.phone());
            if (existing != null) {
                byPhone.put(s.phone(), existing);
                continue;
            }
            CustomerEntity customer = new CustomerEntity();
            customer.setName(s.name());
            customer.setGender(s.gender());
            customer.setAge(s.age());
            customer.setLevel(s.level());
            customer.setPhone(s.phone());
            customer.setAddress(s.address());
            customer.setBirthday(Date.from(LocalDate.of(s.birthYear(), s.birthMonth(), s.birthDay())
                    .atStartOfDay(ZoneId.systemDefault()).toInstant()));
            customerRepository.save(customer);
            // 建档时间回写过去 N 天，顾客档案「最近到店」/每日报表「新客建档」才有真实时间线
            backdateCustomer(customer.getId(), Date.from(LocalDate.now().minusDays(s.registeredDaysAgo())
                    .atStartOfDay(ZoneId.systemDefault()).toInstant()));
            byPhone.put(s.phone(), customer.getId());
        }
        log.info("种子·顾客：检查完成");
        return byPhone;
    }

    /**
     * 中医诊疗记录：主诉 / 望闻问切 / 脉象 / 取穴 / 诊断与方案，按顾客各一条、可复诊调阅。
     * 五行、难经等结构化字段留空（演示数据不硬造术语），由医生实际接诊时录入。
     */
    public void seedTreats(Map<String, Long> customers) {
        Long wang = customers.get("13900000001");
        Long li = customers.get("13900000002");
        Long zhao = customers.get("13900000004");
        if (wang != null) {
            treatIfAbsent(wang, support.daysAgoAt(14),
                    "近一月夜寐不安，多梦易醒，晨起头昏乏力。",
                    "饮食尚可，二便调，经量偏少色淡，久坐伏案。",
                    "面色少华，舌淡苔薄白，唇色偏淡。",
                    "腹软无压痛，腰骶轻叩痛（-）。",
                    "细",
                    "左关脉弦细，尺脉沉",
                    "右寸脉弱，关脉平",
                    "百会、内关、神门",
                    "三阴交、足三里、太冲",
                    "不寐——心脾两虚",
                    "归脾汤加减内服；配合耳穴压豆（神门、心、脾），两周后复诊。",
                    "忌浓茶咖啡，戌时后少食，睡前热水泡脚 15 分钟。",
                    "首次针灸留针 25 分钟，灸后温阳茶饮一杯。");
            // 针灸处方补录（字段为空才写，幂等）
            Long wangTreat = treatIdByCustomerId(wang);
            if (wangTreat != null) {
                seedTreatAcupuncture(wangTreat, "毫针、耳穴压豆", 25, "平补平泻", "每周 2 次 × 2 周");
            }
        }
        if (li != null) {
            treatIfAbsent(li, support.daysAgoAt(7),
                    "腰部酸痛两月，久坐加重，俯仰不利，无下肢放射痛。",
                    "久坐司机，喜卧软床，二便调。",
                    "舌暗红苔薄白，腰肌紧张。",
                    "腰 4/5 棘突旁压痛（+），直腿抬高试验（-）。",
                    "沉紧",
                    "左尺脉沉",
                    "右尺脉沉，关脉弦",
                    "肾俞、腰阳关、委中",
                    "大肠俞、阿是穴、昆仑",
                    "腰痛——肾虚络瘀",
                    "独活寄生汤加减内服；配合温针灸与推拿，一周两次，共三次。",
                    "避免久坐寒湿，护腰佩戴，睡硬板床。",
                    "本次推拿松解腰背 30 分钟，温针灸 20 分钟。");
            Long liTreat = treatIdByCustomerId(li);
            if (liTreat != null) {
                seedTreatAcupuncture(liTreat, "温针", 20, "补法", "一周两次 × 3 次");
            }
        }
        if (zhao != null) {
            treatIfAbsent(zhao, support.daysAgoAt(2),
                    "咽干微痛反复三月，晨起有痰，久语加重。",
                    "用嗓较多，喜辛辣，夜间口干，二便调。",
                    "咽部黏膜偏暗红，舌红少津苔薄。",
                    "咽部充血（+），甲状腺未及肿大。",
                    "细数",
                    "左寸脉细数",
                    "右寸脉细数，关脉滑",
                    "列缺、照海、天突",
                    "合谷、鱼际、廉泉",
                    "慢喉痹——肺阴不足",
                    "养阴清肺汤加减内服；忌辛辣煎炸，两周后复诊评估。",
                    "少食辛辣，室内保湿，罗汉果胖大海代茶饮。",
                    "本次穴位贴敷（天突、列缺），嘱声休。");
        }
        log.info("种子·诊疗记录：检查完成");
    }

    /** 病史（过敏/既往）：给两位演示顾客各补几条，顾客档案「病史」栏可查 */
    public void seedHistories(Map<String, Long> customers) {
        if (historyRepository.count() > 0) {
            return;
        }
        record HistorySeed(String phone, int type, String content, int daysAgo) {}
        List<HistorySeed> seeds = List.of(
                new HistorySeed("13900000001", 0, "青霉素过敏（皮试阳性）", 28),
                new HistorySeed("13900000001", 0, "阿胶、蜂蜜过敏", 20),
                new HistorySeed("13900000001", 1, "高血压 8 年，规律服药，血压控制平稳", 28),
                new HistorySeed("13900000004", 0, "海鲜类食物过敏，易发风疹", 9)
        );
        Calendar cal = Calendar.getInstance();
        for (HistorySeed s : seeds) {
            Long customerId = customers.get(s.phone());
            if (customerId == null) {
                continue;
            }
            CustomerHistoryEntity history = new CustomerHistoryEntity();
            history.setCustomerId(customerId);
            history.setType(s.type());
            history.setContent(s.content());
            history = historyRepository.save(history);
            cal.setTime(new Date());
            cal.add(Calendar.DAY_OF_MONTH, -s.daysAgo());
            support.backdateRow("customer_histories", history.getId(), cal.getTime());
        }
        log.info("种子·病史：检查完成");
    }

    /** 按顾客找接诊单 id（演示种子内用；找不到返回 null） */
    private Long treatIdByCustomerId(Long customerId) {
        return treatRepository.findAll().stream()
                .filter(t -> customerId.equals(t.getCustomerId()))
                .map(TreatEntity::getId)
                .findFirst().orElse(null);
    }

    /** 针灸处方补录：给已存在的接诊单补 针法/留针/手法/疗程（针法为空才写，幂等） */
    private void seedTreatAcupuncture(Long treatId, String acuMethod, Integer retentionMinutes,
                                      String manipulation, String acuCourse) {
        treatRepository.findById(treatId).ifPresent(t -> {
            if (t.getAcuMethod() == null) {
                t.setAcuMethod(acuMethod);
                t.setRetentionMinutes(retentionMinutes);
                t.setManipulation(manipulation);
                t.setAcuCourse(acuCourse);
                treatRepository.save(t);
            }
        });
    }

    private Long treatIfAbsent(Long customerId, Date createTime, String desc, String inquiry,
                               String observation, String palpation, String pulse,
                               String pulseLeft, String pulseRight,
                               String acupointLeft, String acupointRight,
                               String diagnose, String plan, String diet, String conditioning) {
        boolean exists = treatRepository.findAll().stream()
                .anyMatch(t -> customerId.equals(t.getCustomerId()));
        if (exists) {
            return null;
        }
        TreatEntity treat = new TreatEntity();
        treat.setCustomerId(customerId);
        treat.setDesc(desc);
        treat.setInquiry(inquiry);
        treat.setObservation(observation);
        treat.setPalpation(palpation);
        treat.setPulse(pulse);
        treat.setPulseLeft(pulseLeft);
        treat.setPulseRight(pulseRight);
        treat.setAcupointLeft(acupointLeft);
        treat.setAcupointRight(acupointRight);
        treat.setDiagnose(diagnose);
        treat.setPlan(plan);
        treat.setDiet(diet);
        treat.setConditioning(conditioning);
        treat.setCreateTime(createTime);
        TreatEntity saved = treatRepository.save(treat);
        support.backdateTreat(saved.getId(), createTime); // @CreatedDate 会覆盖显式时间，落库后回写演示时间线
        return saved.getId();
    }

    private Long customerByPhone(String phone) {
        return customerRepository.findAll().stream()
                .filter(c -> phone.equals(c.getPhone()))
                .map(CustomerEntity::getId)
                .findFirst().orElse(null);
    }

    private void backdateCustomer(Long customerId, Date createTime) {
        try {
            jdbcTemplate.update("UPDATE customers SET create_time = ?, update_time = ? WHERE id = ?",
                    new Timestamp(createTime.getTime()), new Timestamp(createTime.getTime()), customerId);
        } catch (Exception e) {
            log.warn("种子·顾客建档时间回写失败（仅影响演示时间线，不影响功能）", e);
        }
    }
}
