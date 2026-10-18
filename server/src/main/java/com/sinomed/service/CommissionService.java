package com.sinomed.service;

import com.sinomed.entity.OrderEntity;
import com.sinomed.entity.SettlementEntity;

/**
 * 提成记账（美容业态 c，docs/design/salon-extension.md §2-c）：
 * 结算时按 order_items 行小计 × 规则比例落记录；退单时写等额负向冲销。
 * 业绩归属锚点 orders.staff_id；规则匹配「项目×岗位」（staffs.title）。
 */
public interface CommissionService {

    /** 结算完成后的记账：员工/岗位/规则任一缺失不记；insert-only */
    void bookForSettlement(OrderEntity order, SettlementEntity settlement);

    /** 退单后的冲销：按结算单找正向记录逐条写负向副本，净额归零 */
    void reverseForSettlement(SettlementEntity settlement);
}
