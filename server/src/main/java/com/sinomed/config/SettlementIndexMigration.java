package com.sinomed.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 退费口径迁移：退款后订单回退可重收，一单可产生多张结算单（docs/design/reconciliation.md），
 * 老库 settlements.order_id 上的唯一索引与新口径冲突，启动时移除（新库实体已不声明）。
 */
@Component
@RequiredArgsConstructor
public class SettlementIndexMigration implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute("DROP INDEX IF EXISTS idx_settlement_order_id");
    }
}
