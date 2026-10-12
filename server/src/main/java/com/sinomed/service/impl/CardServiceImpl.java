package com.sinomed.service.impl;

import com.sinomed.entity.CardAdjustEntity;
import com.sinomed.entity.CardUsageEntity;
import com.sinomed.entity.CustomerCardEntity;
import com.sinomed.entity.OrderEntity;
import com.sinomed.repository.CardAdjustRepository;
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
    private final CardAdjustRepository adjustRepository;
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

    /** 类型常量：1 赠次 / 2 转卡（card_adjusts.type） */
    private static final int TYPE_GIFT = 1;
    private static final int TYPE_TRANSFER = 2;

    @Override
    @Transactional
    public CustomerCardEntity adjust(Long cardId, Integer times, String reason, Long staffId) {
        CustomerCardEntity card = requireActiveCard(cardId);
        if (times == null || times < 1 || times > 99) {
            throw new IllegalArgumentException("赠加次数需在 1-99");
        }
        checkReason(reason);
        card.setTotalTimes(card.getTotalTimes() + times);
        card.setRemainingTimes(card.getRemainingTimes() + times);
        card = cardRepository.save(card);
        saveLog(card.getId(), TYPE_GIFT, times, null, reason, staffId);
        log.info("持卡赠次：card={} +{} 次，剩余 {} 次", card.getId(), times, card.getRemainingTimes());
        return card;
    }

    @Override
    @Transactional
    public CustomerCardEntity transfer(Long cardId, Long toCustomerId, String reason, Long staffId) {
        CustomerCardEntity card = requireActiveCard(cardId);
        if (toCustomerId == null) {
            throw new IllegalArgumentException("转卡目标顾客id不能为空");
        }
        customerRepository.findById(toCustomerId)
                .orElseThrow(() -> new IllegalArgumentException("转卡目标顾客不存在：" + toCustomerId));
        if (toCustomerId.equals(card.getCustomerId())) {
            throw new IllegalArgumentException("该卡已是此顾客持卡");
        }
        checkReason(reason);
        card.setCustomerId(toCustomerId);
        card = cardRepository.save(card);
        saveLog(card.getId(), TYPE_TRANSFER, 0, toCustomerId, reason, staffId);
        log.info("持卡转卡：card={} 转入顾客 {}", card.getId(), toCustomerId);
        return card;
    }

    @Override
    public List<CardAdjustEntity> listAdjusts(Long cardId) {
        if (cardId == null) {
            throw new IllegalArgumentException("持卡id不能为空");
        }
        cardRepository.findById(cardId)
                .orElseThrow(() -> new IllegalArgumentException("持卡不存在：" + cardId));
        return adjustRepository.findByCardIdOrderByIdDesc(cardId);
    }

    /** 取有效卡：不存在/已停用均 400（过期不拦——赠次/转卡后有效期照旧，可先调期再用） */
    private CustomerCardEntity requireActiveCard(Long cardId) {
        if (cardId == null) {
            throw new IllegalArgumentException("持卡id不能为空");
        }
        CustomerCardEntity card = cardRepository.findById(cardId)
                .orElseThrow(() -> new IllegalArgumentException("持卡不存在：" + cardId));
        if (card.getStatus() == null || card.getStatus() != STATUS_ACTIVE) {
            throw new IllegalArgumentException("持卡已停用，请先恢复再操作");
        }
        return card;
    }

    private void checkReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("调整理由必填");
        }
        if (reason.length() > 100) {
            throw new IllegalArgumentException("调整理由需 ≤100 字");
        }
    }

    private void saveLog(Long cardId, int type, int times, Long customerId, String reason, Long staffId) {
        CardAdjustEntity entity = new CardAdjustEntity();
        entity.setCardId(cardId);
        entity.setType(type);
        entity.setTimes(times);
        entity.setCustomerId(customerId);
        entity.setReason(reason);
        entity.setStaffId(staffId);
        adjustRepository.save(entity);
    }
}
