package com.sinomed.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * SQLite 方言启动期唯一索引补建（同 SettlementIndexMigration 先例）。
 * Hibernate 6.6 + community SQLite dialect 只把「单列」unique 内联到列定义上
 * （如 wx_message_logs.dedupe_key）；@Table(uniqueConstraints=...) 的「多列」唯一约束
 * 既不内联也不生成独立约束——静默丢弃。因此 customer_tags / commission_rules /
 * wx_fans / wx_materials 声明的 (a,b) 唯一性此前从未真正生效。
 * 这里按实体声明补建唯一索引（IF NOT EXISTS 幂等）；老库若已有重复数据则建索引
 * 失败——只记错误不阻断启动（判重仍有业务层路径），人工清理后重启即可生效。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DbUniqueIndexInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    /** 与实体 @Table(uniqueConstraints) 声明一一对应：索引名 / 表名 / 列清单 */
    private static final String[][] DECLARED_UNIQUE_INDEXES = {
            {"uk_customer_tag_customer_tag", "customer_tags", "customer_id, tag"},
            {"uk_commission_rule_item_title", "commission_rules", "item_id, title"},
            {"uk_wx_fan_account_openid", "wx_fans", "account_id, openid"},
            {"uk_wx_material_account_media", "wx_materials", "account_id, media_id"}
    };

    @Override
    public void run(ApplicationArguments args) {
        for (String[] def : DECLARED_UNIQUE_INDEXES) {
            try {
                jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS " + def[0]
                        + " ON " + def[1] + " (" + def[2] + ")");
            } catch (Exception e) {
                log.error("唯一索引 {} 建失败（老库可能存在重复数据，人工清理后重启生效）：{}", def[0], e.getMessage());
            }
        }
    }
}
