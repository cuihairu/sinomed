package com.sinomed.seed;

import com.sinomed.entity.CardUsageEntity;
import com.sinomed.entity.CustomerCardEntity;
import com.sinomed.entity.ItemEntity;
import com.sinomed.entity.OrderEntity;
import com.sinomed.entity.RechargeEntity;
import com.sinomed.entity.RefundLogEntity;
import com.sinomed.entity.SettlementEntity;
import com.sinomed.repository.CardUsageRepository;
import com.sinomed.repository.CustomerCardRepository;
import com.sinomed.repository.ItemRepository;
import com.sinomed.repository.OrderRepository;
import com.sinomed.repository.RechargeRepository;
import com.sinomed.repository.RefundLogRepository;
import com.sinomed.repository.SettlementRepository;
import com.sinomed.util.DateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 账务域种子（LAY-002 拆分）：储值充值流水、今日自助机待结算单、次卡与历史核销、
 * 收费月报已结算单（含次卡当日核销）、退款演示。全部按自然日判重，逐条回写演示时间线。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BillingSeedService {

    private final RechargeRepository rechargeRepository;
    private final OrderRepository orderRepository;
    private final ItemRepository itemRepository;
    private final CustomerCardRepository cardRepository;
    private final CardUsageRepository usageRepository;
    private final SettlementRepository settlementRepository;
    private final RefundLogRepository refundLogRepository;
    private final SeedSupport support;

    /** 收费结算演示数据：两笔储值充值 + 两条今日自助机待结算单（staffId 空 = 自助机来源） */
    public void seedBilling(Map<String, Long> customers) {
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
            Date when = support.at(r.dayOffset(), 10, 0);
            recharge.setCreateTime(when);
            RechargeEntity saved = rechargeRepository.save(recharge);
            support.backdateRow("recharges", saved.getId(), when); // @CreatedDate 会覆盖显式时间，回写演示时间线
        }
        log.info("种子·储值：检查完成");

        record KioskOrderSeed(String phone, String itemName, int hour, int minute) {}
        for (KioskOrderSeed k : List.of(
                new KioskOrderSeed("13900000005", "艾灸温阳调理（5 次卡）", 10, 20),
                new KioskOrderSeed("13900000002", "经络推拿（10 次卡）", 11, 5)
        )) {
            Long customerId = customers.get(k.phone());
            Long itemId = k.itemName() == null ? null : support.itemByName(k.itemName());
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

    /** 次卡：演示顾客持「经络推拿（10 次卡）」已用 3 次 */
    public void seedCards(Map<String, Long> customers) {
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
        Long staffId = support.staffByAccount("shen");
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
            support.backdateRow("card_usages", usage.getId(), cal.getTime());
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
    public void seedSettlements(Map<String, Long> customers) {
        Long staffId = support.staffByAccount("shen");
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
            Long itemId = support.itemByName(s.itemName());
            if (customerId == null || itemId == null
                    || kioskOrderOnDayExists(customerId, itemId, s.dayOffset())) {
                continue;
            }
            insertSettledOrder(customerId, itemId, staffId, s.payType(), support.at(s.dayOffset(), s.hour(), s.minute()));
        }

        // 次卡核销：演示推拿卡当日核 1 次（第 4 次，带订单号），扣余次——与结算链路同口径
        Long customerId = customers.get("13900000001");
        Long itemId = support.itemByName("经络推拿（10 次卡）");
        Long cardId = customerId == null || itemId == null ? null : cardRepository.findAll().stream()
                .filter(c -> customerId.equals(c.getCustomerId()) && itemId.equals(c.getItemId())
                        && Integer.valueOf(1).equals(c.getStatus()))
                .findFirst().map(CustomerCardEntity::getId).orElse(null);
        if (cardId == null || kioskOrderOnDayExists(customerId, itemId, 0)) {
            return;
        }
        Long orderId = insertSettledOrder(customerId, itemId, staffId, 5, support.at(0, 9, 40));
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

    /** 退款演示：退掉一笔历史现金结算单（原路退回=仅落退款记录），订单回退待结算；幂等按退款流水有无 */
    public void seedRefunds() {
        if (refundLogRepository.count() > 0) {
            return;
        }
        SettlementEntity target = null;
        for (SettlementEntity s : settlementRepository.findAll()) {
            if (!Integer.valueOf(4).equals(s.getPayType()) || s.getMoney() == null) {
                continue; // 只退现金单：储值/次卡退款会动余额与核销演示数据
            }
            OrderEntity order = orderRepository.findById(s.getOrderId()).orElse(null);
            if (order == null || !Integer.valueOf(2).equals(order.getStatus())
                    || s.getCreateTime() == null || !s.getCreateTime().before(support.at(0, 0, 0))) {
                continue;
            }
            if (target == null || s.getId() < target.getId()) {
                target = s;
            }
        }
        if (target == null) {
            return;
        }
        Date when = support.at(0, 12, 5);
        RefundLogEntity refund = new RefundLogEntity();
        refund.setSettlementId(target.getId());
        refund.setOrderId(target.getOrderId());
        refund.setMoney(target.getMoney());
        refund.setPayType(target.getPayType());
        refund.setReason("顾客临时有事取消，退单演示");
        refund.setCreateTime(when);
        Long refundId = refundLogRepository.save(refund).getId();
        support.backdateRow("refund_logs", refundId, when);
        orderRepository.findById(target.getOrderId()).ifPresent(order -> {
            order.setStatus(1); // 已完成 → 已接单，演示「退了重收」
            orderRepository.save(order);
        });
        log.info("种子·退款：结算单 {} 已退 {} 元，订单回退待结算", target.getId(), target.getMoney());
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
        support.backdateRow("orders", orderId, when);

        SettlementEntity settlement = new SettlementEntity();
        settlement.setOrderId(orderId);
        settlement.setUserId(customerId);
        settlement.setPayType(payType);
        settlement.setMoney(payType == 5 ? 0 : price);
        settlement.setCreateTime(when);
        Long settlementId = settlementRepository.save(settlement).getId();
        support.backdateRow("settlements", settlementId, when);

        if (payType == 1) {
            // 储值支付扣减流水（正常结算走 SettlementServiceImpl.settle，种子直接补齐同形数据）
            RechargeEntity deduct = new RechargeEntity();
            deduct.setUserId(customerId);
            deduct.setMoney(-price);
            deduct.setCreateTime(when);
            Long deductId = rechargeRepository.save(deduct).getId();
            support.backdateRow("recharges", deductId, when);
        }
        return orderId;
    }

    /** 该顾客在 offset 天当日是否已有储值流水（按自然日判重） */
    private boolean rechargeOnDayExists(Long customerId, int dayOffset) {
        return rechargeRepository.findAll().stream()
                .anyMatch(r -> customerId.equals(r.getUserId())
                        && r.getCreateTime() != null
                        && DateUtil.isSomeDay(r.getCreateTime(), support.at(dayOffset, 12, 0)));
    }

    /** 该顾客该卡项当日是否已有自助机单（任意状态，含已结算，避免重启后重复补单） */
    private boolean kioskOrderOnDayExists(Long customerId, Long itemId, int dayOffset) {
        return orderRepository.findAll().stream()
                .anyMatch(o -> customerId.equals(o.getUserId())
                        && itemId.equals(o.getItemId())
                        && o.getCreateTime() != null
                        && DateUtil.isSomeDay(o.getCreateTime(), support.at(dayOffset, 12, 0)));
    }
}
