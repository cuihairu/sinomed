package com.sinomed.seed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 演示种子数据编排入口（全部为虚构脱敏人名与内容，仅用于在线演示环境；LAY-002 拆分后
 * 领域构造分居 {@link CatalogSeedService}（医药字典）、{@link ClinicSeedService}（门店运营基础）、
 * {@link CustomerSeedService}（顾客/诊疗/病史）、{@link DailyOpsSeedService}（复盘/考勤/预约）、
 * {@link BillingSeedService}（储值/卡项/结算/退款）、{@link PharmacySeedService}（处方/发药），
 * 共享回写与时刻工具在 {@link SeedSupport}）。
 *
 * <p>由环境变量 DEMO_SEED=true 开关（compose 演示环境默认开启，开发环境默认关闭）——
 * 生命周期开关只在本类，种子服务本身不判开关。
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

    private final CatalogSeedService catalogSeedService;
    private final ClinicSeedService clinicSeedService;
    private final CustomerSeedService customerSeedService;
    private final DailyOpsSeedService dailyOpsSeedService;
    private final BillingSeedService billingSeedService;
    private final PharmacySeedService pharmacySeedService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            clinicSeedService.seedStaff();
            clinicSeedService.seedItems();
            catalogSeedService.seedHerbs();
            catalogSeedService.seedTemplates();
            catalogSeedService.seedFormulas();
            catalogSeedService.seedAcupoints();
            clinicSeedService.seedShifts();
            clinicSeedService.seedHerbStock();
            clinicSeedService.seedTherapyTemplates();
            Map<String, Long> customers = customerSeedService.seedCustomers();
            customerSeedService.seedTreats(customers);
            dailyOpsSeedService.seedReviews();
            dailyOpsSeedService.seedReviewCustomers(customers);
            dailyOpsSeedService.seedReviewStaffs();
            dailyOpsSeedService.seedSigns();
            dailyOpsSeedService.seedAppointments(customers);
            billingSeedService.seedBilling(customers);
            billingSeedService.seedCards(customers);
            billingSeedService.seedSettlements(customers);
            billingSeedService.seedRefunds();
            pharmacySeedService.seedPrescriptions(customers);
            pharmacySeedService.seedDispense();
            customerSeedService.seedHistories(customers);
            log.info("演示种子数据检查完成（逐表幂等，已有数据自动跳过）；"
                    + "广告素材/排期/屏幕为运营自建数据，无种子");
        } catch (Exception e) {
            // 播种失败不阻断启动：演示站优先可用，下次启动按自然键补齐缺项
            log.error("演示种子数据失败（服务继续启动）", e);
        }
    }
}
