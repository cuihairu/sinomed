package com.sinomed.service.impl;

import com.sinomed.entity.CardUsageEntity;
import com.sinomed.entity.CustomerCardEntity;
import com.sinomed.entity.OrderEntity;
import com.sinomed.repository.CardUsageRepository;
import com.sinomed.repository.CustomerCardRepository;
import com.sinomed.repository.CustomerRepository;
import com.sinomed.repository.ItemRepository;
import com.sinomed.repository.OrderRepository;
import com.sinomed.service.CardService;
import com.sinomed.vo.CardView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * 次卡/疗程卡实现：发卡全量次数、抵扣按「早发的先扣」并落核销流水；
 * validUntil 可空=长期有效，过期卡不参与抵扣。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CardServiceImpl implements CardService {

    private static final int STATUS_ACTIVE = 1;

    private final CustomerCardRepository cardRepository;
    private final CardUsageRepository usageRepository;
    private final CustomerRepository customerRepository;
    private final ItemRepository itemRepository;
    private final OrderRepository orderRepository;

    @Override
    @Transactional
    public CustomerCardEntity issue(CardView view) {
        if (view.getCustomerId() == null) {
            throw new IllegalArgumentException("顾客id不能为空");
        }
        customerRepository.findById(view.getCustomerId())
                .orElseThrow(() -> new IllegalArgumentException("顾客不存在：" + view.getCustomerId()));
        if (view.getItemId() == null) {
            throw new IllegalArgumentException("卡项id不能为空");
        }
        itemRepository.findById(view.getItemId())
                .orElseThrow(() -> new IllegalArgumentException("卡项不存在：" + view.getItemId()));
        if (view.getTotalTimes() == null || view.getTotalTimes() < 1) {
            throw new IllegalArgumentException("总次数必须 ≥ 1");
        }
        if (view.getSourceOrderId() != null) {
            orderRepository.findById(view.getSourceOrderId())
                    .orElseThrow(() -> new IllegalArgumentException("订单不存在：" + view.getSourceOrderId()));
        }

        CustomerCardEntity card = new CustomerCardEntity();
        card.setCustomerId(view.getCustomerId());
        card.setItemId(view.getItemId());
        card.setTotalTimes(view.getTotalTimes());
        card.setRemainingTimes(view.getTotalTimes());
        card.setStatus(STATUS_ACTIVE);
        card.setSourceOrderId(view.getSourceOrderId());
        card.setValidUntil(view.getValidUntil());
        return cardRepository.save(card);
    }

    @Override
    public List<CustomerCardEntity> listByCustomer(Long customerId) {
        if (customerId == null) {
            throw new IllegalArgumentException("顾客id不能为空");
        }
        return cardRepository.findByCustomerIdOrderByIdDesc(customerId);
    }

    @Override
    public List<CustomerCardEntity> listByCustomerIds(List<Long> customerIds) {
        if (customerIds == null || customerIds.isEmpty()) {
            return List.of();
        }
        return cardRepository.findByCustomerIdIn(customerIds);
    }

    @Override
    @Transactional
    public CustomerCardEntity setStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new IllegalArgumentException("状态无效：1 有效 / 0 停用");
        }
        CustomerCardEntity card = cardRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("持卡不存在：" + id));
        card.setStatus(status);
        return cardRepository.save(card);
    }

    @Override
    @Transactional
    public CustomerCardEntity deduct(Long customerId, Long itemId, Long orderId) {
        List<CustomerCardEntity> candidates = cardRepository
                .findByCustomerIdAndItemIdAndStatusAndRemainingTimesGreaterThanOrderByIdAsc(
                        customerId, itemId, STATUS_ACTIVE, 0);
        // 过期卡不参与抵扣（validUntil 空为长期有效）
        Date now = new Date();
        candidates.removeIf(c -> c.getValidUntil() != null && c.getValidUntil().before(now));
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("顾客无可抵扣次卡（卡项 " + itemId + "）");
        }
        CustomerCardEntity card = candidates.get(0);
        card.setRemainingTimes(card.getRemainingTimes() - 1);
        card = cardRepository.save(card);

        OrderEntity order = orderId == null ? null
                : orderRepository.findById(orderId).orElse(null);
        CardUsageEntity usage = new CardUsageEntity();
        usage.setCardId(card.getId());
        usage.setOrderId(orderId);
        usage.setStaffId(order == null ? null : order.getStaffId());
        usage.setTimesUsed(card.getTotalTimes() - card.getRemainingTimes());
        usageRepository.save(usage);
        log.info("次卡抵扣：card={} 第 {} 次，剩余 {} 次", card.getId(), usage.getTimesUsed(), card.getRemainingTimes());
        return card;
    }

    @Override
    public List<CardUsageEntity> listUsages(Long cardId) {
        if (cardId == null) {
            throw new IllegalArgumentException("持卡id不能为空");
        }
        cardRepository.findById(cardId)
                .orElseThrow(() -> new IllegalArgumentException("持卡不存在：" + cardId));
        return usageRepository.findByCardIdOrderByIdDesc(cardId);
    }
}
