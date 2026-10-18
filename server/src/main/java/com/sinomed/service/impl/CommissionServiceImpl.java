package com.sinomed.service.impl;

import com.sinomed.entity.CommissionRecordEntity;
import com.sinomed.entity.OrderEntity;
import com.sinomed.entity.OrderItemEntity;
import com.sinomed.entity.SettlementEntity;
import com.sinomed.entity.StaffEntity;
import com.sinomed.repository.CommissionRecordRepository;
import com.sinomed.repository.CommissionRuleRepository;
import com.sinomed.repository.OrderItemRepository;
import com.sinomed.repository.StaffRepository;
import com.sinomed.service.CommissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 提成记账（美容业态 c）：见接口注释。P0 固定比例「项目×岗位」，无规则即不记（0 不建规则即得）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommissionServiceImpl implements CommissionService {

    private final CommissionRuleRepository ruleRepository;
    private final CommissionRecordRepository recordRepository;
    private final OrderItemRepository orderItemRepository;
    private final StaffRepository staffRepository;

    @Override
    public void bookForSettlement(OrderEntity order, SettlementEntity settlement) {
        if (order == null || settlement == null || order.getStaffId() == null) {
            return; // 无业绩归属（前台单）不记
        }
        Optional<StaffEntity> staff = staffRepository.findById(order.getStaffId());
        if (staff.isEmpty() || staff.get().getTitle() == null || staff.get().getTitle().isBlank()) {
            return; // 员工无岗位不匹配任何规则
        }
        String title = staff.get().getTitle().trim();
        for (OrderItemEntity line : orderItemRepository.findByOrderIdOrderByIdAsc(order.getId())) {
            if (line.getItemId() == null) {
                continue;
            }
            ruleRepository.findByItemIdAndTitle(line.getItemId(), title).ifPresent(rule -> {
                int subtotal = line.getSubtotal() == null ? 0 : line.getSubtotal();
                CommissionRecordEntity record = new CommissionRecordEntity();
                record.setOrderId(order.getId());
                record.setSettlementId(settlement.getId());
                record.setOrderItemId(line.getId());
                record.setStaffId(order.getStaffId());
                record.setItemId(line.getItemId());
                record.setItemName(line.getName());
                record.setSubtotal(subtotal);
                record.setRatio(rule.getRatio());
                // 提成按业绩（行小计）非实收：次卡抵扣实收 0 也照记；整数元向下取整
                record.setAmount(subtotal * rule.getRatio() / 100);
                recordRepository.save(record);
                log.info("提成记账：订单 {} 行 {} 员工 {}（{}）小计 {} × {}% = {}",
                        order.getId(), line.getId(), order.getStaffId(), title,
                        subtotal, rule.getRatio(), record.getAmount());
            });
        }
    }

    @Override
    public void reverseForSettlement(SettlementEntity settlement) {
        if (settlement == null) {
            return;
        }
        for (CommissionRecordEntity record : recordRepository.findBySettlementId(settlement.getId())) {
            if (record.getAmount() != null && record.getAmount() < 0) {
                continue; // 本身是冲销记录，不再冲销
            }
            CommissionRecordEntity reversal = new CommissionRecordEntity();
            reversal.setOrderId(record.getOrderId());
            reversal.setSettlementId(record.getSettlementId());
            reversal.setOrderItemId(record.getOrderItemId());
            reversal.setStaffId(record.getStaffId());
            reversal.setItemId(record.getItemId());
            reversal.setItemName(record.getItemName());
            reversal.setSubtotal(record.getSubtotal());
            reversal.setRatio(record.getRatio());
            reversal.setAmount(record.getAmount() == null ? 0 : -record.getAmount());
            reversal.setRemark("退单冲销 #" + settlement.getId());
            recordRepository.save(reversal);
            log.info("提成冲销：结算 {} 记录 {} 金额 {} → {}",
                    settlement.getId(), record.getId(), record.getAmount(), reversal.getAmount());
        }
    }
}
