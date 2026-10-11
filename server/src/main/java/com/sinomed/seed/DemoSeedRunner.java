package com.sinomed.seed;

import com.sinomed.entity.AppointmentEntity;
import com.sinomed.entity.CardUsageEntity;
import com.sinomed.entity.CustomerEntity;
import com.sinomed.entity.CustomerHistoryEntity;
import com.sinomed.entity.ItemEntity;
import com.sinomed.entity.OrderEntity;
import com.sinomed.entity.PrescriptionEntity;
import com.sinomed.entity.PrescriptionItemEntity;
import com.sinomed.entity.PrescriptionTemplateEntity;
import com.sinomed.entity.PrescriptionTemplateItemEntity;
import com.sinomed.entity.RechargeEntity;
import com.sinomed.entity.ReviewCustomerEntity;
import com.sinomed.entity.FormulaEntity;
import com.sinomed.entity.FormulaItemEntity;
import com.sinomed.entity.AcupointEntity;
import com.sinomed.entity.ReviewEntity;
import com.sinomed.entity.ReviewStaffEntity;
import com.sinomed.entity.SettlementEntity;
import com.sinomed.entity.SignEntity;
import com.sinomed.entity.StaffEntity;
import com.sinomed.entity.StaffShiftEntity;
import com.sinomed.entity.TherapyTemplateEntity;
import com.sinomed.entity.TreatEntity;
import com.sinomed.repository.AppointmentRepository;
import com.sinomed.repository.CardUsageRepository;
import com.sinomed.repository.CustomerRepository;
import com.sinomed.repository.CustomerHistoryRepository;
import com.sinomed.repository.ItemRepository;
import com.sinomed.repository.PrescriptionTemplateItemRepository;
import com.sinomed.repository.PrescriptionTemplateRepository;
import com.sinomed.repository.HerbRepository;
import com.sinomed.repository.HerbStockLogRepository;
import com.sinomed.entity.HerbEntity;
import com.sinomed.entity.HerbStockLogEntity;
import com.sinomed.repository.CustomerCardRepository;
import com.sinomed.entity.CustomerCardEntity;
import com.sinomed.repository.OrderRepository;
import com.sinomed.repository.PrescriptionItemRepository;
import com.sinomed.repository.PrescriptionRepository;
import com.sinomed.repository.RechargeRepository;
import com.sinomed.repository.SettlementRepository;
import com.sinomed.repository.FormulaRepository;
import com.sinomed.repository.FormulaItemRepository;
import com.sinomed.repository.AcupointRepository;
import com.sinomed.repository.ReviewCustomerRepository;
import com.sinomed.repository.ReviewRepository;
import com.sinomed.repository.ReviewStaffRepository;
import com.sinomed.repository.SignRepository;
import com.sinomed.repository.StaffRepository;
import com.sinomed.repository.StaffShiftRepository;
import com.sinomed.repository.TherapyTemplateRepository;
import com.sinomed.repository.TreatRepository;
import com.sinomed.util.DateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 演示种子数据（全部为虚构脱敏人名与内容，仅用于在线演示环境）。
 *
 * <p>由环境变量 DEMO_SEED=true 开关（compose 演示环境默认开启，开发环境默认关闭）。
 * 逐表幂等：以自然键（账号 / 手机号 / 卡项名 / 复盘日）判重，已有数据即跳过，
 * 重复启动不会产生重复数据；某一节失败只记录日志、不阻断服务启动，下次启动会补齐缺项。
 *
 * <p>说明：广告素材模块源码尚未实现，暂无对应种子数据（如实留空）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "demo.seed", havingValue = "true")
public class DemoSeedRunner implements ApplicationRunner {

    private final StaffRepository staffRepository;
    private final AppointmentRepository appointmentRepository;
    private final RechargeRepository rechargeRepository;
    private final OrderRepository orderRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final PrescriptionItemRepository prescriptionItemRepository;
    private final CustomerRepository customerRepository;
    private final CustomerHistoryRepository historyRepository;
    private final TreatRepository treatRepository;
    private final ItemRepository itemRepository;
    private final HerbRepository herbRepository;
    private final PrescriptionTemplateRepository templateRepository;
    private final PrescriptionTemplateItemRepository templateItemRepository;
    private final CustomerCardRepository cardRepository;
    private final CardUsageRepository usageRepository;
    private final SettlementRepository settlementRepository;
    private final FormulaRepository formulaRepository;
    private final FormulaItemRepository formulaItemRepository;
    private final AcupointRepository acupointRepository;
    private final StaffShiftRepository shiftRepository;
    private final HerbStockLogRepository herbStockLogRepository;
    private final TherapyTemplateRepository therapyTemplateRepository;
    private final ReviewRepository reviewRepository;
    private final ReviewCustomerRepository reviewCustomerRepository;
    private final ReviewStaffRepository reviewStaffRepository;
    private final SignRepository signRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            seedStaff();
            seedItems();
            seedHerbs();
            seedTemplates();
            seedFormulas();
            seedAcupoints();
            seedShifts();
            seedHerbStock();
            seedTherapyTemplates();
            Map<String, Long> customers = seedCustomers();
            seedTreats(customers);
            seedReviews();
            seedReviewCustomers(customers);
            seedReviewStaffs();
            seedSigns();
            seedAppointments(customers);
            seedBilling(customers);
            seedCards(customers);
            seedSettlements(customers);
            seedPrescriptions(customers);
            seedHistories(customers);
            log.info("演示种子数据检查完成（逐表幂等，已有数据自动跳过）；"
                    + "广告素材/排期/屏幕为运营自建数据，无种子");
        } catch (Exception e) {
            // 播种失败不阻断启动：演示站优先可用，下次启动按自然键补齐缺项
            log.error("演示种子数据失败（服务继续启动）", e);
        }
    }

    /** 员工：馆长 + 中医师 + 前台，密码统一为演示账号 123（bcrypt） */
    private void seedStaff() {
        record StaffSeed(String name, String account, int role, int gender, int age,
                         String phone, String address, int birthYear, int birthMonth, int birthDay) {}
        List<StaffSeed> seeds = List.of(
                new StaffSeed("顾景明", "gu", 0, 1, 45, "13800000001", "南京市秦淮区", 1981, 4, 18),
                new StaffSeed("沈知远", "shen", 99, 1, 38, "13800000002", "南京市秦淮区", 1988, 9, 3),
                new StaffSeed("苏文若", "su", 99, 0, 26, "13800000003", "南京市秦淮区", 2000, 2, 11)
        );
        for (StaffSeed s : seeds) {
            if (staffExists(s.account())) {
                continue;
            }
            StaffEntity staff = StaffEntity.getInstanceWithDefault(s.account());
            staff.setName(s.name());
            staff.setRole(s.role());
            staff.setGender(s.gender());
            staff.setAge(s.age());
            staff.setPhone(s.phone());
            staff.setAddress(s.address());
            staff.setBirthday(Date.from(LocalDate.of(s.birthYear(), s.birthMonth(), s.birthDay())
                    .atStartOfDay(ZoneId.systemDefault()).toInstant()));
            staff.setPassword(passwordEncoder.encode("123"));
            staffRepository.save(staff);
        }
        log.info("种子·员工：检查完成");
    }

    /** 卡项（服务项目）：单次体验与次卡/季卡组合 */
    private void seedItems() {
        record ItemSeed(String name, int price, String description) {}
        List<ItemSeed> seeds = List.of(
                new ItemSeed("中医体质辨识（单次）", 198,
                        "四诊合参体质辨识一次，含辨识报告与调理建议。"),
                new ItemSeed("经络推拿（10 次卡）", 680,
                        "经络推拿十次卡，有效期一年，可指定技师。"),
                new ItemSeed("艾灸温阳调理（5 次卡）", 480,
                        "艾灸温阳调理五次卡，含灸后温阳茶饮。"),
                new ItemSeed("四季膏方调理（季卡）", 1280,
                        "四季膏方调理季度服务，含辨证开方与两次复诊调整。")
        );
        int sort = 0;
        for (ItemSeed s : seeds) {
            if (itemExists(s.name())) {
                continue;
            }
            ItemEntity item = new ItemEntity();
            item.setName(s.name());
            item.setPrice(s.price());
            item.setDescription(s.description());
            // enabled/sort 为 NOT NULL 列（ItemView 默认 enabled=1/sort=0），种子直插实体需显式带上
            item.setEnabled(1);
            item.setSort(++sort);
            itemRepository.save(item);
        }
        log.info("种子·卡项：检查完成");
    }

    /** 顾客建档：虚构脱敏人名，手机号取明显虚构号段 */
    private Map<String, Long> seedCustomers() {
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
        Map<String, Long> byPhone = new java.util.HashMap<>();
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
    private void seedTreats(Map<String, Long> customers) {
        Long wang = customers.get("13900000001");
        Long li = customers.get("13900000002");
        Long zhao = customers.get("13900000004");
        if (wang != null) {
            treatIfAbsent(wang, daysAgoAt(14),
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
            treatIfAbsent(li, daysAgoAt(7),
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
            treatIfAbsent(zhao, daysAgoAt(2),
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

    /** 每日复盘：昨日一条，沉淀到报表 */
    private void seedReviews() {
        Date yesterday = daysAgo(1);
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
    private void seedReviewCustomers(Map<String, Long> customers) {
        Long wang = customers.get("13900000001");
        Long li = customers.get("13900000002");
        if (wang != null && !reviewCustomerExists(wang, daysAhead(1))) {
            ReviewCustomerEntity rc = new ReviewCustomerEntity();
            rc.setName("王慕清");
            rc.setCustomerId(wang);
            rc.setLast(daysAgo(3));
            rc.setDay(daysAhead(1)); // 明日回访：出现在每日报表待办
            rc.setAdvice("复诊后回访：询问睡眠改善情况，提醒调理卡月底到期可续。");
            reviewCustomerRepository.save(rc);
        }
        if (li != null && !reviewCustomerExists(li, daysAgo(1))) {
            ReviewCustomerEntity rc = new ReviewCustomerEntity();
            rc.setName("李景和");
            rc.setCustomerId(li);
            rc.setLast(daysAgo(9));
            rc.setDay(daysAgo(1)); // 昨日回访：已完成
            rc.setAdvice("已回访：腰痛明显缓解，嘱继续硬板床与腰背锻炼，下周复诊。");
            reviewCustomerRepository.save(rc);
        }
        log.info("种子·顾客回访：检查完成");
    }

    /** 员工日复盘：昨日一条（耗卡/已做/建议） */
    private void seedReviewStaffs() {
        Date yesterday = daysAgo(1);
        Long shen = staffByAccount("shen");
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
    /** 预约：今日 3 条（1 条已到店）+ 明日 1 条 + 昨日取消 1 条，逐条按顾客+当日判重 */
    private void seedAppointments(Map<String, Long> customers) {
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
            appointment.setStaffId(a.staffAccount() == null ? null : staffByAccount(a.staffAccount()));
            appointment.setItemId(a.itemName() == null ? null : itemByName(a.itemName()));
            appointment.setStartTime(at(a.dayOffset(), a.hour(), a.minute()));
            appointment.setDuration(a.duration());
            appointment.setStatus(a.status());
            appointment.setRemark(a.remark());
            appointmentRepository.save(appointment);
        }
        log.info("种子·预约：检查完成");
    }

    /** 收费结算演示数据：两笔储值充值 + 两条今日自助机待结算单（staffId 空 = 自助机来源） */
    private void seedBilling(Map<String, Long> customers) {
        record RechargeSeed(String phone, int money, int dayOffset) {}
        for (RechargeSeed r : List.of(
                new RechargeSeed("13900000005", 500, -2),
                new RechargeSeed("13900000001", 2000, -6)
        )) {
            Long customerId = customers.get(r.phone());
            if (customerId == null || rechargeOnDayExists(customerId, r.dayOffset())) {
                continue;
            }
            RechargeEntity recharge = new RechargeEntity();
            recharge.setUserId(customerId);
            recharge.setMoney(r.money());
            Date when = at(r.dayOffset(), 10, 0);
            recharge.setCreateTime(when);
            RechargeEntity saved = rechargeRepository.save(recharge);
            backdateRow("recharges", saved.getId(), when); // @CreatedDate 会覆盖显式时间，回写演示时间线
        }
        log.info("种子·储值：检查完成");

        record KioskOrderSeed(String phone, String itemName, int hour, int minute) {}
        for (KioskOrderSeed k : List.of(
                new KioskOrderSeed("13900000005", "艾灸温阳调理（5 次卡）", 10, 20),
                new KioskOrderSeed("13900000002", "经络推拿（10 次卡）", 11, 5)
        )) {
            Long customerId = customers.get(k.phone());
            Long itemId = k.itemName() == null ? null : itemByName(k.itemName());
            if (customerId == null || itemId == null || kioskOrderOnDayExists(customerId, itemId, 0)) {
                continue;
            }
            OrderEntity order = new OrderEntity();
            order.setUserId(customerId);
            order.setItemId(itemId);
            order.setStaffId(null); // 自助机单，无接待员工
            order.setStatus(0); // 已下单，待前台结算
            ItemEntity item = itemRepository.findById(itemId).orElse(null);
            order.setPrice(item == null ? null : item.getPrice()); // 下单时刻价格快照
            orderRepository.save(order); // create_time 由审计写为今天，幂等判重按当日即可
        }
        log.info("种子·待结算单：检查完成");
    }

    /** 中药处方演示数据：王慕清昨日一张逍遥散化裁（药材名为自由文本，虚构演示） */
    /** 药材字典：覆盖种子处方 7 味与常用饮片，价格（分/克）为虚构演示值 */
    private void seedHerbs() {
        record HerbPrice(String name, int pricePerGram) {}
        List<HerbPrice> herbs = List.of(
                new HerbPrice("柴胡", 8), new HerbPrice("白芍", 5), new HerbPrice("当归", 12),
                new HerbPrice("茯苓", 4), new HerbPrice("白术", 6), new HerbPrice("薄荷", 3),
                new HerbPrice("炙甘草", 2), new HerbPrice("甘草", 2), new HerbPrice("黄芪", 6),
                new HerbPrice("丹参", 8), new HerbPrice("半夏", 9), new HerbPrice("陈皮", 3),
                new HerbPrice("川芎", 7), new HerbPrice("地黄", 8), new HerbPrice("麦冬", 10),
                // 方剂库补常用饮片（逐条判重，老库缺项自动补齐）
                new HerbPrice("人参", 25), new HerbPrice("熟地黄", 10), new HerbPrice("山药", 5),
                new HerbPrice("泽泻", 4), new HerbPrice("牡丹皮", 6), new HerbPrice("苍术", 5),
                new HerbPrice("厚朴", 4), new HerbPrice("升麻", 5), new HerbPrice("生姜", 2),
                new HerbPrice("山茱萸", 12));
        for (HerbPrice herb : herbs) {
            if (herbRepository.existsByName(herb.name())) {
                continue;
            }
            HerbEntity entity = new HerbEntity();
            entity.setName(herb.name());
            entity.setPrice(herb.pricePerGram());
            herbRepository.save(entity);
        }
        log.info("种子·药材字典：检查完成");
    }

    /** 方剂库演示数据：经典方剂与药味组成（公版方剂文献口径），供开方页「方剂库」按方名/拼音检索带出 */
    private void seedFormulas() {
        if (formulaRepository.count() > 0) {
            return;
        }
        record HerbComp(String herb, double weight, String special) {}
        record FormulaSeed(String name, String pinyin, String source, String indication, List<HerbComp> herbs) {}
        List<FormulaSeed> seeds = List.of(
                new FormulaSeed("四君子汤", "sijunzitang", "太平惠民和剂局方", "补气健脾，用于脾胃虚弱、食少便溏",
                        List.of(new HerbComp("人参", 9, null), new HerbComp("白术", 9, null),
                                new HerbComp("茯苓", 9, null), new HerbComp("甘草", 6, null))),
                new FormulaSeed("四物汤", "siwutang", "太平惠民和剂局方", "补血调血，用于血虚萎黄、月经不调",
                        List.of(new HerbComp("熟地黄", 12, null), new HerbComp("当归", 9, null),
                                new HerbComp("白芍", 9, null), new HerbComp("川芎", 6, null))),
                new FormulaSeed("逍遥散", "xiaoyaosan", "太平惠民和剂局方", "疏肝健脾养血，用于肝郁血虚脾弱",
                        List.of(new HerbComp("柴胡", 9, null), new HerbComp("当归", 9, null),
                                new HerbComp("白芍", 9, null), new HerbComp("白术", 9, null),
                                new HerbComp("茯苓", 9, null), new HerbComp("薄荷", 6, "后下"),
                                new HerbComp("生姜", 9, null), new HerbComp("甘草", 6, null))),
                new FormulaSeed("补中益气汤", "buzhongyiqitang", "内外伤辨惑论", "补中益气升阳，用于脾虚气陷、少气懒言",
                        List.of(new HerbComp("黄芪", 15, null), new HerbComp("人参", 9, null),
                                new HerbComp("白术", 9, null), new HerbComp("当归", 9, null),
                                new HerbComp("陈皮", 6, null), new HerbComp("升麻", 6, null),
                                new HerbComp("柴胡", 6, null), new HerbComp("甘草", 6, null))),
                new FormulaSeed("玉屏风散", "yupingfengsan", "丹溪心法", "益气固表止汗，用于表虚自汗、易感风寒",
                        List.of(new HerbComp("黄芪", 15, null), new HerbComp("白术", 9, null),
                                new HerbComp("防风", 6, null))),
                new FormulaSeed("六味地黄丸", "liuweidihuangwan", "小儿药证直诀", "滋补肾阴，用于肾阴虚亏、腰膝酸软",
                        List.of(new HerbComp("熟地黄", 24, null), new HerbComp("山茱萸", 12, null),
                                new HerbComp("山药", 12, null), new HerbComp("泽泻", 9, null),
                                new HerbComp("茯苓", 9, null), new HerbComp("牡丹皮", 9, null))),
                new FormulaSeed("平胃散", "pingweisan", "太平惠民和剂局方", "燥湿运脾行气，用于湿滞脾胃、脘腹胀满",
                        List.of(new HerbComp("苍术", 12, null), new HerbComp("厚朴", 9, null),
                                new HerbComp("陈皮", 9, null), new HerbComp("甘草", 6, null))),
                new FormulaSeed("二陈汤", "erchantang", "太平惠民和剂局方", "燥湿化痰理气，用于痰湿停滞、咳嗽痰多",
                        List.of(new HerbComp("半夏", 9, null), new HerbComp("陈皮", 9, null),
                                new HerbComp("茯苓", 9, null), new HerbComp("甘草", 6, null))),
                new FormulaSeed("八珍汤", "bazhentang", "正体类要", "气血双补，用于气血两虚、面色萎黄",
                        List.of(new HerbComp("人参", 9, null), new HerbComp("白术", 9, null),
                                new HerbComp("茯苓", 9, null), new HerbComp("甘草", 6, null),
                                new HerbComp("熟地黄", 12, null), new HerbComp("当归", 9, null),
                                new HerbComp("白芍", 9, null), new HerbComp("川芎", 6, null)))
        );
        for (FormulaSeed f : seeds) {
            FormulaEntity formula = new FormulaEntity();
            formula.setName(f.name());
            formula.setPinyin(f.pinyin());
            formula.setSource(f.source());
            formula.setIndication(f.indication());
            FormulaEntity saved = formulaRepository.save(formula);
            backdateRow("formulas", saved.getId(), at(-20, 10, 0));
            for (int i = 0; i < f.herbs().size(); i++) {
                HerbComp h = f.herbs().get(i);
                FormulaItemEntity item = new FormulaItemEntity();
                item.setFormulaId(saved.getId());
                item.setHerb(h.herb());
                item.setWeight(h.weight());
                item.setSpecial(h.special());
                item.setSort(i);
                formulaItemRepository.save(item);
            }
        }
        log.info("种子·方剂库：检查完成");
    }

    /** 穴位字典：常用经络穴位 27 穴（归经/定位/主治为文献参考口径） */
    private void seedAcupoints() {
        if (acupointRepository.count() > 0) {
            return;
        }
        record PointSeed(String name, String pinyin, String meridian, String location, String indication) {}
        List<PointSeed> seeds = List.of(
                new PointSeed("足三里", "zusanli", "足阳明胃经", "犊鼻下 3 寸，胫骨前嵴外 1 横指", "健脾和胃、扶正培元，主治胃痛、腹胀、虚劳"),
                new PointSeed("三阴交", "sanyinjiao", "足太阴脾经", "内踝尖上 3 寸，胫骨内侧面后缘", "健脾利湿、调经止痛，主治月经不调、失眠"),
                new PointSeed("合谷", "hegu", "手阳明大肠经", "手背第 1、2 掌骨间，第 2 掌骨桡侧中点", "疏风解表、镇痛通络，主治头痛、牙痛、面口疾患"),
                new PointSeed("太冲", "taichong", "足厥阴肝经", "足背第 1、2 跖骨间，跖骨结合部前凹陷", "疏肝理气、平肝熄风，主治头痛眩晕、胁痛"),
                new PointSeed("内关", "neiguan", "手厥阴心包经", "腕横纹上 2 寸，掌长肌腱与桡侧腕屈肌腱之间", "宁心安神、和胃降逆，主治心悸、失眠、呕恶"),
                new PointSeed("外关", "waiguan", "手少阳三焦经", "腕背横纹上 2 寸，尺骨与桡骨之间", "疏风清热、通经活络，主治热病、肩背痛"),
                new PointSeed("曲池", "quchi", "手阳明大肠经", "屈肘，肘横纹外侧端凹陷", "清热解表、通络止痛，主治发热、高血压、肘臂痛"),
                new PointSeed("阳陵泉", "yanglingquan", "足少阳胆经", "腓骨头前下方凹陷", "疏肝利胆、舒筋活络，主治胁痛、下肢痿痹"),
                new PointSeed("委中", "weizhong", "足太阳膀胱经", "腘横纹中点", "舒筋通络、凉血泄热，主治腰背痛"),
                new PointSeed("百会", "baihui", "督脉", "前发际正中直上 5 寸（两耳尖连线中点）", "升阳举陷、醒脑开窍，主治头痛、眩晕、脱肛"),
                new PointSeed("风池", "fengchi", "足少阳胆经", "枕骨下，斜方肌与胸锁乳突肌之间凹陷", "祛风解表、明目醒脑，主治感冒、颈项强痛、眩晕"),
                new PointSeed("大椎", "dazhui", "督脉", "第 7 颈椎棘突下凹陷", "清热解表、截疟止痛，主治感冒发热、项强"),
                new PointSeed("关元", "guanyuan", "任脉", "前正中线上，脐下 3 寸", "培元固本、温阳益气，主治虚劳、遗尿、宫寒"),
                new PointSeed("气海", "qihai", "任脉", "前正中线上，脐下 1.5 寸", "益气补虚、调理气机，主治气虚乏力、脘腹胀满"),
                new PointSeed("中脘", "zhongwan", "任脉", "前正中线上，脐上 4 寸", "健脾和胃、消积化滞，主治胃脘痛、呕吐、纳呆"),
                new PointSeed("神阙", "shenque", "任脉", "脐窝正中", "温阳救逆、健运脾胃，主治腹痛、泄泻（多灸不针）"),
                new PointSeed("涌泉", "yongquan", "足少阴肾经", "足底前 1/3 凹陷处", "滋阴益肾、引火下行，主治失眠、高血压、足心热"),
                new PointSeed("太溪", "taixi", "足少阴肾经", "内踝尖与跟腱之间凹陷", "滋阴补肾、调理下焦，主治腰痛、耳鸣、遗精"),
                new PointSeed("血海", "xuehai", "足太阴脾经", "髌骨内上缘上 2 寸", "理血调经、祛风止痒，主治月经不调、皮肤瘙痒"),
                new PointSeed("阴陵泉", "yinlingquan", "足太阴脾经", "胫骨内侧髁下缘凹陷", "健脾利湿、通利小便，主治水肿、泄泻、膝痛"),
                new PointSeed("肩井", "jianjing", "足少阳胆经", "第 7 颈椎棘突与肩峰连线中点", "祛风活络、消肿散结，主治肩背痛、乳痈"),
                new PointSeed("命门", "mingmen", "督脉", "第 2 腰椎棘突下凹陷", "温肾壮阳、强腰固本，主治腰痛、遗尿、阳痿"),
                new PointSeed("肾俞", "shenshu", "足太阳膀胱经", "第 2 腰椎棘突下旁开 1.5 寸", "益肾固精、强腰明目，主治腰痛、耳鸣、肾虚诸症"),
                new PointSeed("脾俞", "pishu", "足太阳膀胱经", "第 11 胸椎棘突下旁开 1.5 寸", "健脾化湿、统血生气，主治腹胀、泄泻、水肿"),
                new PointSeed("胃俞", "weishu", "足太阳膀胱经", "第 12 胸椎棘突下旁开 1.5 寸", "和胃健脾、消食化滞，主治胃脘痛、呕吐"),
                new PointSeed("印堂", "yintang", "经外奇穴", "两眉头连线中点", "宁神醒脑、通窍止痛，主治失眠、头痛、鼻渊"),
                new PointSeed("太阳", "taiyang", "经外奇穴", "眉梢与目外眦之间后约 1 横指凹陷", "疏风止痛、明目，主治头痛、偏头痛、眼疾"));
        for (PointSeed p : seeds) {
            AcupointEntity point = new AcupointEntity();
            point.setName(p.name());
            point.setPinyin(p.pinyin());
            point.setMeridian(p.meridian());
            point.setLocation(p.location());
            point.setIndication(p.indication());
            AcupointEntity saved = acupointRepository.save(point);
            backdateRow("acupoints", saved.getId(), at(-18, 9, 30));
        }
        log.info("种子·穴位字典：检查完成");
    }

    /** 员工班表：馆长/中医师/前台各一组周期班次（按周循环，同员工同星期唯一；考勤仍以打卡为准） */
    private void seedShifts() {
        if (shiftRepository.count() > 0) {
            return;
        }
        Long shen = staffByAccount("shen");
        Long su = staffByAccount("su");
        Long gu = staffByAccount("gu");
        record ShiftSeed(Long staffId, int weekday, String start, String end) {}
        List<ShiftSeed> seeds = new ArrayList<>();
        // 沈知远（中医师）：周一 ~ 周五 09:00-18:00
        for (int weekday = 1; weekday <= 5; weekday++) {
            seeds.add(new ShiftSeed(shen, weekday, "09:00", "18:00"));
        }
        // 苏文若（前台）：周一 ~ 周六 09:30-18:30
        for (int weekday = 1; weekday <= 6; weekday++) {
            seeds.add(new ShiftSeed(su, weekday, "09:30", "18:30"));
        }
        // 顾景明（馆长）：周三 ~ 周日 10:00-19:00
        for (int weekday = 3; weekday <= 7; weekday++) {
            seeds.add(new ShiftSeed(gu, weekday, "10:00", "19:00"));
        }
        for (ShiftSeed s : seeds) {
            if (s.staffId() == null) {
                continue;
            }
            StaffShiftEntity shift = new StaffShiftEntity();
            shift.setStaffId(s.staffId());
            shift.setWeekday(s.weekday());
            shift.setStart(s.start());
            shift.setEnd(s.end());
            StaffShiftEntity saved = shiftRepository.save(shift);
            backdateRow("staff_shifts", saved.getId(), at(-16, 9, 0));
        }
        log.info("种子·员工班表：检查完成");
    }

    /** 饮片库存：常用药材入库（带批次效期/供货方）+ 一笔出库演示；台账口径，不涉采购单据与结算 */
    private void seedHerbStock() {
        if (herbStockLogRepository.count() > 0) {
            return;
        }
        record StockSeed(String herb, int type, int quantity, Integer expiryInDays, String supplier, String note) {}
        List<StockSeed> seeds = List.of(
                new StockSeed("黄芪", 1, 5000, 120, "亳州药市", null),
                new StockSeed("当归", 1, 3000, 90, "亳州药市", null),
                new StockSeed("茯苓", 1, 4000, 200, "安徽亳州", null),
                new StockSeed("白术", 1, 2500, 25, "浙江磐安", "近期到期"),
                new StockSeed("甘草", 1, 6000, 300, "内蒙古", null),
                new StockSeed("甘草", 0, 800, null, null, "门诊领用"));
        for (StockSeed s : seeds) {
            HerbEntity herb = herbRepository.findByName(s.herb());
            if (herb == null) {
                continue;
            }
            HerbStockLogEntity entity = new HerbStockLogEntity();
            entity.setHerbId(herb.getId());
            entity.setType(s.type());
            entity.setQuantity(s.quantity());
            entity.setExpiry(s.expiryInDays() == null ? null : at(s.expiryInDays(), 12, 0));
            entity.setSupplier(s.supplier());
            entity.setNote(s.note());
            HerbStockLogEntity saved = herbStockLogRepository.save(entity);
            backdateRow("herb_stock_logs", saved.getId(), at(-15, 9, 0));
        }
        log.info("种子·饮片库存：检查完成");
    }

    /** 外治处方模板：耳穴压豆/穴位贴敷/艾灸/拔罐常用组合各一；模板只存建议值，接诊页套用后随单自由改 */
    private void seedTherapyTemplates() {
        if (therapyTemplateRepository.count() > 0) {
            return;
        }
        record TplSeed(String name, String method, String target, String course, String note) {}
        List<TplSeed> seeds = List.of(
                new TplSeed("失眠·耳穴压豆", "耳穴压豆", "神门、交感、皮质下、心", "每周 2 次 × 4 周，留豆 3 天一换", "每日睡前自行揉按 1 分钟，胶布过敏停用"),
                new TplSeed("咳喘·穴位贴敷", "穴位贴敷", "肺俞、定喘、膻中", "贴敷 4~6 小时，隔日一换 × 7 次", "皮肤发红属正常，灼热起疱即取下"),
                new TplSeed("肾阳虚·温灸关元", "艾灸", "关元、气海、命门", "每日 1 次，每次 20 分钟 × 2 周", "灸后避风保暖，口干舌燥减量"),
                new TplSeed("腰痛·留罐", "拔罐", "肾俞、大肠俞、委中", "每周 2 次 × 3 周，留罐 10 分钟", "罐斑紫暗属正常，皮肤破损不拔"));
        for (TplSeed s : seeds) {
            TherapyTemplateEntity entity = new TherapyTemplateEntity();
            entity.setName(s.name());
            entity.setMethod(s.method());
            entity.setTarget(s.target());
            entity.setCourse(s.course());
            entity.setNote(s.note());
            entity.setEnabled(1);
            TherapyTemplateEntity saved = therapyTemplateRepository.save(entity);
            backdateRow("therapy_templates", saved.getId(), at(-14, 10, 0));
        }
        log.info("种子·外治模板：检查完成");
    }

    /** 次卡：演示顾客持「经络推拿（10 次卡）」已用 3 次 */
    private void seedCards(Map<String, Long> customers) {
        Long customerId = customers.get("13900000001");
        if (customerId == null || cardRepository.count() > 0) {
            return;
        }
        ItemEntity item = itemRepository.findByName("经络推拿（10 次卡）").orElse(null);
        if (item == null) {
            return;
        }
        CustomerCardEntity card = new CustomerCardEntity();
        card.setCustomerId(customerId);
        card.setItemId(item.getId());
        card.setTotalTimes(10);
        card.setRemainingTimes(7);
        card.setStatus(1);
        cardRepository.save(card);
        // 历史核销：余 7/10 ↔ 已用 3 次（老卡补录口径，无订单号），演示核销记录查询
        Long staffId = staffByAccount("shen");
        Calendar cal = Calendar.getInstance();
        for (int i = 1; i <= 3; i++) {
            CardUsageEntity usage = new CardUsageEntity();
            usage.setCardId(card.getId());
            usage.setStaffId(staffId);
            usage.setTimesUsed(i);
            usage = usageRepository.save(usage);
            // 第 1 次 30 天前 → 第 3 次 10 天前
            cal.setTime(new Date());
            cal.add(Calendar.DAY_OF_MONTH, i * 10 - 40);
            backdateRow("card_usages", usage.getId(), cal.getTime());
        }
        // 艾灸疗程卡：满卡未消费
        itemRepository.findByName("艾灸温阳调理（5 次卡）").ifPresent(moxa -> {
            CustomerCardEntity moxaCard = new CustomerCardEntity();
            moxaCard.setCustomerId(customerId);
            moxaCard.setItemId(moxa.getId());
            moxaCard.setTotalTimes(5);
            moxaCard.setRemainingTimes(5);
            moxaCard.setStatus(1);
            cardRepository.save(moxaCard);
        });
        log.info("种子·次卡：检查完成");
    }

    /** 收费月报演示数据：当月数笔已结算单（微信/支付宝/现金/储值/次卡），按日回写时间线 */
    private void seedSettlements(Map<String, Long> customers) {
        Long staffId = staffByAccount("shen");
        record SettledSeed(String phone, String itemName, int dayOffset, int hour, int minute, int payType) {}
        List<SettledSeed> seeds = List.of(
                new SettledSeed("13900000002", "中医体质辨识（单次）", -9, 10, 10, 2),
                new SettledSeed("13900000005", "经络推拿（10 次卡）", -7, 14, 20, 3),
                new SettledSeed("13900000002", "艾灸温阳调理（5 次卡）", -5, 11, 0, 4),
                new SettledSeed("13900000001", "四季膏方调理（季卡）", -3, 15, 40, 1),
                new SettledSeed("13900000005", "经络推拿（10 次卡）", -1, 10, 30, 2),
                new SettledSeed("13900000001", "中医体质辨识（单次）", -1, 16, 0, 4)
        );
        for (SettledSeed s : seeds) {
            Long customerId = customers.get(s.phone());
            Long itemId = itemByName(s.itemName());
            if (customerId == null || itemId == null
                    || kioskOrderOnDayExists(customerId, itemId, s.dayOffset())) {
                continue;
            }
            insertSettledOrder(customerId, itemId, staffId, s.payType(), at(s.dayOffset(), s.hour(), s.minute()));
        }

        // 次卡核销：演示推拿卡当日核 1 次（第 4 次，带订单号），扣余次——与结算链路同口径
        Long customerId = customers.get("13900000001");
        Long itemId = itemByName("经络推拿（10 次卡）");
        Long cardId = customerId == null || itemId == null ? null : cardRepository.findAll().stream()
                .filter(c -> customerId.equals(c.getCustomerId()) && itemId.equals(c.getItemId())
                        && Integer.valueOf(1).equals(c.getStatus()))
                .findFirst().map(CustomerCardEntity::getId).orElse(null);
        if (cardId == null || kioskOrderOnDayExists(customerId, itemId, 0)) {
            return;
        }
        Long orderId = insertSettledOrder(customerId, itemId, staffId, 5, at(0, 9, 40));
        int usedTimes = (int) usageRepository.findAll().stream()
                .filter(u -> cardId.equals(u.getCardId())).count();
        CardUsageEntity usage = new CardUsageEntity();
        usage.setCardId(cardId);
        usage.setOrderId(orderId);
        usage.setStaffId(staffId);
        usage.setTimesUsed(usedTimes + 1);
        usageRepository.save(usage); // 当日核销，时间由审计写为今天
        cardRepository.findById(cardId).ifPresent(card -> {
            card.setRemainingTimes(Math.max(0, card.getRemainingTimes() - 1));
            cardRepository.save(card);
        });
        log.info("种子·结算：检查完成");
    }

    /** 落一单已完结订单 + 结算单（payType 5 次卡实收 0，1 储值同口径落负数流水），按 when 回写时间线 */
    private Long insertSettledOrder(Long customerId, Long itemId, Long staffId, int payType, Date when) {
        ItemEntity item = itemRepository.findById(itemId).orElse(null);
        int price = item == null || item.getPrice() == null ? 0 : item.getPrice();
        OrderEntity order = new OrderEntity();
        order.setUserId(customerId);
        order.setItemId(itemId);
        order.setStaffId(staffId);
        order.setStatus(2); // 已完结（正常结算流转落此状态）
        order.setPrice(price);
        order.setCreateTime(when);
        Long orderId = orderRepository.save(order).getId();
        backdateRow("orders", orderId, when);

        SettlementEntity settlement = new SettlementEntity();
        settlement.setOrderId(orderId);
        settlement.setUserId(customerId);
        settlement.setPayType(payType);
        settlement.setMoney(payType == 5 ? 0 : price);
        settlement.setCreateTime(when);
        Long settlementId = settlementRepository.save(settlement).getId();
        backdateRow("settlements", settlementId, when);

        if (payType == 1) {
            // 储值支付扣减流水（正常结算走 SettlementServiceImpl.settle，种子直接补齐同形数据）
            RechargeEntity deduct = new RechargeEntity();
            deduct.setUserId(customerId);
            deduct.setMoney(-price);
            deduct.setCreateTime(when);
            Long deductId = rechargeRepository.save(deduct).getId();
            backdateRow("recharges", deductId, when);
        }
        return orderId;
    }

    /** 病史（过敏/既往）：给两位演示顾客各补几条，顾客档案「病史」栏可查 */
    private void seedHistories(Map<String, Long> customers) {
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
            backdateRow("customer_histories", history.getId(), cal.getTime());
        }
        log.info("种子·病史：检查完成");
    }

    private void seedPrescriptions(Map<String, Long> customers) {
        record HerbSeed(String herb, double weight, String special) {}
        Long customerId = customers.get("13900000001");
        Long staffId = staffByAccount("shen");
        if (customerId == null) {
            return;
        }
        // 汤剂（代煎）：已有昨日处方即跳过
        if (!prescriptionOnDayExists(customerId, -1)) {
            List<HerbSeed> herbs = List.of(
                    new HerbSeed("柴胡", 12, null),
                    new HerbSeed("白芍", 15, null),
                    new HerbSeed("当归", 10, null),
                    new HerbSeed("茯苓", 15, null),
                    new HerbSeed("白术", 12, null),
                    new HerbSeed("薄荷", 6, "后下"),
                    new HerbSeed("炙甘草", 6, null)
            );
            PrescriptionEntity prescription = new PrescriptionEntity();
            prescription.setCustomerId(customerId);
            prescription.setStaffId(staffId);
            prescription.setDoses(7);
            prescription.setPrescriptionType(0);
            prescription.setPasteStatus(0);
            // 昨日开方今日可取（0 无需/1 待煎/2 可取/3 已取），留给演示走「已取」流转
            prescription.setDecoctionStatus(2);
            prescription.setDecoctionBags(7);
            prescription.setUsage("水煎服，日一剂，早晚温服；代煎 7 袋");
            prescription.setRemark("复诊请带近期睡眠记录");
            prescription = prescriptionRepository.save(prescription);
            for (int i = 0; i < herbs.size(); i++) {
                HerbSeed herb = herbs.get(i);
                PrescriptionItemEntity item = new PrescriptionItemEntity();
                item.setPrescriptionId(prescription.getId());
                item.setHerb(herb.herb());
                item.setWeight(herb.weight());
                item.setSpecial(herb.special());
                item.setSort(i);
                prescriptionItemRepository.save(item);
            }
            backdateRow("prescriptions", prescription.getId(), at(-1, 10, 30)); // 回写昨日开方时间线
        }
        // 膏方（炼蜜收膏）：演示膏方领取流转，已有膏方即跳过
        if (customersHasPaste(customerId)) {
            return;
        }
        List<HerbSeed> pasteHerbs = List.of(
                new HerbSeed("熟地黄", 60, null),
                new HerbSeed("党参", 30, null),
                new HerbSeed("白芍", 30, null),
                new HerbSeed("茯苓", 30, null),
                new HerbSeed("陈皮", 30, null),
                new HerbSeed("砂仁", 10, "后下"),
                new HerbSeed("阿胶", 20, "烊化"),
                new HerbSeed("龟板胶", 20, "烊化"),
                new HerbSeed("鹿角胶", 10, "烊化")
        );
        PrescriptionEntity paste = new PrescriptionEntity();
        paste.setCustomerId(customerId);
        paste.setStaffId(staffId);
        paste.setDoses(30);
        paste.setPrescriptionType(1);
        paste.setPasteStatus(2); // 三日可取，留给演示走「已取」流转
        paste.setDecoctionStatus(0); // 膏方按料计，不走代煎（列 NOT NULL 需显式置 0）
        paste.setCraft("炼蜜");
        paste.setUsage("温水冲服，日 2 次，每次 15g；一料服约 30 日");
        paste.setRemark("空腹服用，忌生冷；服期间停用峻补之品");
        paste = prescriptionRepository.save(paste);
        for (int i = 0; i < pasteHerbs.size(); i++) {
            HerbSeed herb = pasteHerbs.get(i);
            PrescriptionItemEntity item = new PrescriptionItemEntity();
            item.setPrescriptionId(paste.getId());
            item.setHerb(herb.herb());
            item.setWeight(herb.weight());
            item.setSpecial(herb.special());
            item.setSort(i);
            prescriptionItemRepository.save(item);
        }
        backdateRow("prescriptions", paste.getId(), at(-3, 10, 0)); // 回写三日制膏时间线
        log.info("种子·处方：检查完成");
    }

    /** 该顾客是否已有膏方（种子幂等键） */
    private boolean customersHasPaste(Long customerId) {
        return prescriptionRepository.findAll().stream()
                .anyMatch(p -> customerId.equals(p.getCustomerId())
                        && Integer.valueOf(1).equals(p.getPrescriptionType()));
    }

    /** 病症处方模板：两条常见病症，开方页「套用模板」演示（按病症名幂等） */
    private void seedTemplates() {
        record TemplateSeed(String name, int doses, int decoction, String usage, String remark,
                            List<String[]> herbs) {}
        List<TemplateSeed> seeds = List.of(
                new TemplateSeed("风寒感冒", 7, 0, "水煎服，日一剂，早晚温服", "避风寒，多饮温水",
                        List.of(new String[]{"荆芥", "10"}, new String[]{"防风", "10"}, new String[]{"紫苏叶", "9"})),
                new TemplateSeed("脾胃虚弱", 14, 1, "水煎服，日一剂，早晚温服；代煎", "忌生冷油腻",
                        List.of(new String[]{"白术", "12"}, new String[]{"茯苓", "15"}, new String[]{"炙甘草", "6"})));
        for (TemplateSeed seed : seeds) {
            if (templateRepository.findByName(seed.name()).isPresent()) {
                continue;
            }
            PrescriptionTemplateEntity template = new PrescriptionTemplateEntity();
            template.setName(seed.name());
            template.setDoses(seed.doses());
            template.setDecoction(seed.decoction());
            template.setUsage(seed.usage());
            template.setRemark(seed.remark());
            template.setEnabled(1);
            template = templateRepository.save(template);
            List<String[]> herbs = seed.herbs();
            for (int i = 0; i < herbs.size(); i++) {
                PrescriptionTemplateItemEntity item = new PrescriptionTemplateItemEntity();
                item.setTemplateId(template.getId());
                item.setHerb(herbs.get(i)[0]);
                item.setWeight(Double.parseDouble(herbs.get(i)[1]));
                item.setSort(i);
                templateItemRepository.save(item);
            }
        }
        log.info("种子·病症处方模板：检查完成");
    }

    private void seedSigns() {
        if (signRepository.count() > 0) {
            return;
        }
        for (String account : List.of("gu", "shen", "su")) {
            Long staffId = staffByAccount(account);
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

    // ---------- 幂等判重与工具 ----------

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
        backdate(saved.getId(), createTime); // @CreatedDate 会覆盖显式时间，落库后回写演示时间线
        return saved.getId();
    }

    /**
     * 把诊疗记录的创建时间回写到过去的接诊日，让复诊历史/按日查询有真实时间线。
     * 仅影响演示数据；失败只记日志，不影响其余种子。
     */
    private void backdate(Long treatId, Date createTime) {
        backdateRow("treats", treatId, createTime);
    }

    /** 演示时间线回写通用化：把某行 create_time/update_time 改写到过去；失败只记日志 */
    private void backdateRow(String table, Long id, Date createTime) {
        try {
            jdbcTemplate.update("UPDATE " + table + " SET create_time = ?, update_time = ? WHERE id = ?",
                    new Timestamp(createTime.getTime()), new Timestamp(createTime.getTime()), id);
        } catch (Exception e) {
            log.warn("种子·时间回写失败（表 " + table + "，仅影响演示时间线，不影响功能）", e);
        }
    }

    private void backdateCustomer(Long customerId, Date createTime) {
        try {
            jdbcTemplate.update("UPDATE customers SET create_time = ?, update_time = ? WHERE id = ?",
                    new Timestamp(createTime.getTime()), new Timestamp(createTime.getTime()), customerId);
        } catch (Exception e) {
            log.warn("种子·顾客建档时间回写失败（仅影响演示时间线，不影响功能）", e);
        }
    }

    private boolean staffExists(String account) {
        return staffRepository.findAll().stream().anyMatch(s -> account.equals(s.getAccount()));
    }

    private Long staffByAccount(String account) {
        return staffRepository.findAll().stream()
                .filter(s -> account.equals(s.getAccount()))
                .map(StaffEntity::getId)
                .findFirst().orElse(null);
    }

    private Long customerByPhone(String phone) {
        return customerRepository.findAll().stream()
                .filter(c -> phone.equals(c.getPhone()))
                .map(CustomerEntity::getId)
                .findFirst().orElse(null);
    }

    private boolean itemExists(String name) {
        return itemRepository.findAll().stream().anyMatch(i -> name.equals(i.getName()));
    }

    private Long itemByName(String name) {
        return itemRepository.findAll().stream()
                .filter(i -> name.equals(i.getName()))
                .map(ItemEntity::getId)
                .findFirst().orElse(null);
    }

    /** 该顾客在 offset 天（0=今天）当日是否已有预约（按自然日判重，时段不同也视同已约） */
    private boolean appointmentOnDayExists(Long customerId, int dayOffset) {
        return appointmentRepository.findAll().stream()
                .anyMatch(a -> customerId.equals(a.getCustomerId())
                        && a.getStartTime() != null
                        && DateUtil.isSomeDay(a.getStartTime(), at(dayOffset, 12, 0)));
    }

    /** 该顾客在 offset 天当日是否已有储值流水（按自然日判重） */
    private boolean rechargeOnDayExists(Long customerId, int dayOffset) {
        return rechargeRepository.findAll().stream()
                .anyMatch(r -> customerId.equals(r.getUserId())
                        && r.getCreateTime() != null
                        && DateUtil.isSomeDay(r.getCreateTime(), at(dayOffset, 12, 0)));
    }

    /** 该顾客该卡项当日是否已有自助机单（任意状态，含已结算，避免重启后重复补单） */
    private boolean kioskOrderOnDayExists(Long customerId, Long itemId, int dayOffset) {
        return orderRepository.findAll().stream()
                .anyMatch(o -> customerId.equals(o.getUserId())
                        && itemId.equals(o.getItemId())
                        && o.getCreateTime() != null
                        && DateUtil.isSomeDay(o.getCreateTime(), at(dayOffset, 12, 0)));
    }

    /** 该顾客在 offset 天当日是否已有处方（按自然日判重） */
    private boolean prescriptionOnDayExists(Long customerId, int dayOffset) {
        return prescriptionRepository.findAll().stream()
                .anyMatch(p -> customerId.equals(p.getCustomerId())
                        && p.getCreateTime() != null
                        && DateUtil.isSomeDay(p.getCreateTime(), at(dayOffset, 12, 0)));
    }

    /** 相对当天的某时刻（dayOffset 天后 hour:minute） */
    private Date at(int dayOffset, int hour, int minute) {
        return Date.from(LocalDate.now().plusDays(dayOffset).atTime(hour, minute)
                .atZone(ZoneId.systemDefault()).toInstant());
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

    /** n 天前 */
    private Date daysAgo(int n) {
        return day(LocalDate.now().minusDays((long) n));
    }

    /** n 天前的上午 10:30——诊疗落在营业时间；分页 endTime 为闭区间，00:00 整点会误入前一日的边界 */
    private Date daysAgoAt(int n) {
        return Date.from(LocalDate.now().minusDays((long) n).atTime(10, 30)
                .atZone(ZoneId.systemDefault()).toInstant());
    }

    /** n 天后 */
    private Date daysAhead(int n) {
        return day(LocalDate.now().plusDays((long) n));
    }

    private Date day(LocalDate date) {
        return Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }
}
