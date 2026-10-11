package com.sinomed.service;

import com.sinomed.entity.RefundLogEntity;
import com.sinomed.entity.SettlementEntity;
import com.sinomed.vo.ReconciliationView;
import com.sinomed.vo.SettlementMonthReportView;
import com.sinomed.vo.SettlementView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SettlementService {
    /**
     * 收费结算：订单状态 0/1 → 2 已完成，落一单结算记录（一单一结算）；
     * 储值支付先校验余额并落负数流水。整个动作一个事务。
     */
    SettlementEntity settle(SettlementView view);

    /**
     * 结算单分页（时间倒序由调用方排序）
     */
    Page<SettlementEntity> findPage(Pageable pageable);

    /**
     * 月度收费报表：按结算时间取当月结算单，聚合逐日与支付方式构成；
     * 月份格式非 yyyy-MM 报 400。
     */
    SettlementMonthReportView monthReport(String month);

    /**
     * 退单：一笔结算只能退一次，退款 = 退整单、原路退回（储值补正数流水、
     * 次卡作废核销回补余次、微信/支付宝/现金仅落退款记录）；
     * 订单 2 已完成 → 1 已接单可重收。理由必填 ≤100 字。整个动作一个事务。
     */
    RefundLogEntity refund(Long settlementId, Long staffId, String reason);

    /**
     * 日对账单：按自然日（结算/充值/退款时间）聚合收款/充值/退款三段与净额、
     * 现金净额；退款补回的储值正数流水不计入充值；日期格式非 yyyy-MM-dd 报 400。
     */
    ReconciliationView reconciliation(String date);
}
