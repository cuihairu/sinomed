package com.sinomed.service.impl;

import com.sinomed.entity.CardUsageEntity;
import com.sinomed.entity.CustomerCardEntity;
import com.sinomed.entity.OrderEntity;
import com.sinomed.entity.RechargeEntity;
import com.sinomed.entity.RefundLogEntity;
import com.sinomed.entity.SettlementEntity;
import com.sinomed.entity.TreatEntity;
import com.sinomed.repository.CardUsageRepository;
import com.sinomed.repository.CustomerCardRepository;
import com.sinomed.repository.OrderRepository;
import com.sinomed.repository.RechargeRepository;
import com.sinomed.repository.RefundLogRepository;
import com.sinomed.repository.SettlementRepository;
import com.sinomed.repository.TreatRepository;
import com.sinomed.service.CardService;
import com.sinomed.service.CommissionService;
import com.sinomed.service.SettlementService;
import com.sinomed.vo.ReconciliationView;
import com.sinomed.vo.ReconciliationView.RechargeRow;
import com.sinomed.vo.ReconciliationView.RefundRow;
import com.sinomed.vo.SettlementMonthReportView;
import com.sinomed.vo.SettlementMonthReportView.DayRow;
import com.sinomed.vo.SettlementMonthReportView.PayRow;
import com.sinomed.vo.SettlementTrendView;
import com.sinomed.vo.SettlementTrendView.MonthRow;
import com.sinomed.vo.SettlementView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

@Slf4j
@RequiredArgsConstructor
@Service
public class SettlementServiceImpl implements SettlementService {

    /** 支付方式：1 储值 / 2 微信 / 3 支付宝 / 4 现金 / 5 次卡抵扣 */
    static final int PAY_STORED_VALUE = 1;
    static final int PAY_WECHAT = 2;
    static final int PAY_ALIPAY = 3;
    static final int PAY_CASH = 4;
    static final int PAY_CARD = 5;

    private final SettlementRepository settlementRepository;
    private final OrderRepository orderRepository;
    private final RechargeRepository rechargeRepository;
    private final CardService cardService;
    private final RefundLogRepository refundLogRepository;
    private final CardUsageRepository cardUsageRepository;
    private final CustomerCardRepository customerCardRepository;
    private final TreatRepository treatRepository;
    private final CommissionService commissionService;

    /**
     * 收费结算：订单 0 已下单 / 1 已确认 → 2 已完成；落一条结算单（一单一结算，order_id 唯一兜底）。
     * 储值支付先校验余额，足够则落一条负数流水扣减。金额取订单价格快照，空价格按 0 收。
     * 次卡抵扣（payType 5）：订单卡项须有有效余次卡，扣 1 次，实收记 0（money=实收口径）。
     */
    @Override
    @Transactional
    public SettlementEntity settle(SettlementView view) {
        if (view.getOrderId() == null) {
            throw new IllegalArgumentException("订单id不能为空");
        }
        int payType = view.getPayType() == null ? 0 : view.getPayType();
        if (payType < PAY_STORED_VALUE || payType > PAY_CARD) {
            throw new IllegalArgumentException("支付方式无效：" + view.getPayType()
                    + "（1 储值 / 2 微信 / 3 支付宝 / 4 现金 / 5 次卡抵扣）");
        }
        OrderEntity order = orderRepository.findById(view.getOrderId())
                .orElseThrow(() -> new IllegalArgumentException("订单不存在：" + view.getOrderId()));
        Integer current = order.getStatus();
        if (current == null || current == 2 || current == 9) {
            throw new IllegalArgumentException("订单状态 " + current + " 已完结，不能结算");
        }
        // 退费后重收：一单可多张结算单，只拦「最近一张未退款」的重复收款
        if (settlementRepository.findFirstByOrderIdOrderByIdDesc(view.getOrderId()).stream()
                .anyMatch(last -> refundLogRepository.findBySettlementId(last.getId()).isEmpty())) {
            throw new IllegalArgumentException("订单已结算过：" + view.getOrderId());
        }
        int price = order.getPrice() == null ? 0 : order.getPrice();

        int paid = price;
        if (payType == PAY_STORED_VALUE) {
            long balance = rechargeRepository.sumMoneyByUserId(order.getUserId());
            if (balance < price) {
                throw new IllegalArgumentException("储值余额不足（余额 " + balance + " 元，应收 " + price + " 元），请先充值或改用其他支付");
            }
            RechargeEntity deduct = new RechargeEntity();
            deduct.setUserId(order.getUserId());
            deduct.setMoney(-price);
            rechargeRepository.save(deduct);
        } else if (payType == PAY_CARD) {
            cardService.deduct(order.getUserId(), order.getItemId(), order.getId());
            paid = 0;
        }

        SettlementEntity settlement = new SettlementEntity();
        settlement.setOrderId(order.getId());
        settlement.setUserId(order.getUserId());
        settlement.setPayType(payType);
        settlement.setMoney(paid);
        settlement = settlementRepository.save(settlement);

        order.setStatus(2);
        orderRepository.save(order);
        // 提成记账（美容业态 c）：按订单明细行×规则比例落记录，无归属/无岗位/无规则自然不记
        commissionService.bookForSettlement(order, settlement);
        return settlement;
    }

    /**
     * 结算单分页
     */
    @Override
    public Page<SettlementEntity> findPage(Pageable pageable) {
        return settlementRepository.findAll(pageable);
    }

    /** 报表月份格式：yyyy-MM */
    static final Pattern MONTH_PATTERN = Pattern.compile("\\d{4}-\\d{2}");

    /**
     * 月度收费报表：按结算时间取当月结算单（[月初, 下月初)），Java 内聚合
     * （SQLite 方言不便做日期分组）。次卡核销计入单数、实收为 0；
     * 支付方式构成只列出现过的，按 payType 升序。
     */
    @Override
    public SettlementMonthReportView monthReport(String month) {
        if (month == null || !MONTH_PATTERN.matcher(month).matches()) {
            throw new IllegalArgumentException("报表月份格式无效：" + month + "（应为 yyyy-MM）");
        }
        LocalDate firstDay;
        try {
            firstDay = LocalDate.parse(month + "-01");
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("报表月份无效：" + month);
        }
        Date start = Date.from(firstDay.atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date end = Date.from(firstDay.plusMonths(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
        List<SettlementEntity> rows = settlementRepository.findByCreateTimeBetween(start, end);

        // day -> [单数, 实收]；payType -> [单数, 实收]，TreeMap 保证升序
        Map<String, int[]> byDay = new TreeMap<>();
        Map<Integer, int[]> byPay = new TreeMap<>();
        int cardCount = 0;
        for (SettlementEntity row : rows) {
            int money = row.getMoney() == null ? 0 : row.getMoney();
            Date time = row.getCreateTime();
            if (time != null) {
                String day = LocalDate.ofInstant(time.toInstant(), ZoneId.systemDefault()).toString();
                int[] dayAgg = byDay.computeIfAbsent(day, k -> new int[2]);
                dayAgg[0]++;
                dayAgg[1] += money;
            }
            int payType = row.getPayType() == null ? 0 : row.getPayType();
            int[] payAgg = byPay.computeIfAbsent(payType, k -> new int[2]);
            payAgg[0]++;
            payAgg[1] += money;
            if (payType == PAY_CARD) {
                cardCount++;
            }
        }
        List<DayRow> days = byDay.entrySet().stream()
                .map(e -> DayRow.builder().day(e.getKey()).count(e.getValue()[0]).money(e.getValue()[1]).build())
                .toList();
        List<PayRow> payTypes = byPay.entrySet().stream()
                .map(e -> PayRow.builder().payType(e.getKey()).payTypeText(payTypeText(e.getKey()))
                        .count(e.getValue()[0]).money(e.getValue()[1]).build())
                .toList();
        return SettlementMonthReportView.builder()
                .month(month)
                .totalCount(rows.size())
                .totalMoney(rows.stream().mapToInt(r -> r.getMoney() == null ? 0 : r.getMoney()).sum())
                .cardCount(cardCount)
                .days(days)
                .payTypes(payTypes)
                .build();
    }

    @Override
    public SettlementTrendView trend(int months) {
        if (months < 1 || months > 24) {
            throw new IllegalArgumentException("趋势月数需在 1-24");
        }
        YearMonth current = YearMonth.now();
        YearMonth first = current.minusMonths(months - 1L);
        Date start = Date.from(first.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date end = Date.from(current.plusMonths(1).atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant());

        // 月键 yyyy-MM -> [单数, 实收, 次卡单数]；TreeMap 升序
        Map<String, int[]> byMonth = new TreeMap<>();
        for (SettlementEntity row : settlementRepository.findByCreateTimeBetween(start, end)) {
            Date time = row.getCreateTime();
            if (time == null) {
                continue;
            }
            String key = YearMonth.from(time.toInstant().atZone(ZoneId.systemDefault())).toString();
            int[] agg = byMonth.computeIfAbsent(key, k -> new int[3]);
            int money = row.getMoney() == null ? 0 : row.getMoney();
            int payType = row.getPayType() == null ? 0 : row.getPayType();
            agg[0]++;
            agg[1] += money;
            if (payType == PAY_CARD) {
                agg[2]++;
            }
        }

        Map<String, Integer> treatsByMonth = new TreeMap<>();
        for (TreatEntity treat : treatRepository.findByCreateTimeBetween(start, end)) {
            Date time = treat.getCreateTime();
            if (time == null) {
                continue;
            }
            String key = YearMonth.from(time.toInstant().atZone(ZoneId.systemDefault())).toString();
            treatsByMonth.merge(key, 1, Integer::sum);
        }

        // 无数据月零填充，月份升序
        List<MonthRow> trendRows = new ArrayList<>();
        for (YearMonth m = first; !m.isAfter(current); m = m.plusMonths(1)) {
            String key = m.toString();
            int[] agg = byMonth.getOrDefault(key, new int[3]);
            trendRows.add(MonthRow.builder()
                    .month(key)
                    .money(agg[1])
                    .count(agg[0])
                    .cardCount(agg[2])
                    .treatCount(treatsByMonth.getOrDefault(key, 0))
                    .build());
        }
        return SettlementTrendView.builder().months(months).rows(trendRows).build();
    }

    private static String payTypeText(int payType) {
        return switch (payType) {
            case PAY_STORED_VALUE -> "储值";
            case PAY_WECHAT -> "微信";
            case PAY_ALIPAY -> "支付宝";
            case PAY_CASH -> "现金";
            case PAY_CARD -> "次卡抵扣";
            default -> "未知(" + payType + ")";
        };
    }

    /** 对账日期格式：yyyy-MM-dd */
    static final Pattern DATE_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    /**
     * 退单：校验结算存在 / 未退过 / 订单已完结，按原 payType 原路退回——
     * 储值补一条正数流水（note=退单 #结算id），次卡作废该订单核销流水并回补余次，
     * 微信/支付宝/现金演示口径仅落退款记录；订单 2 → 1 可重收。
     * 退款金额 = 原结算实收（次卡为 0），理由必填 ≤100 字。整单退、一单只退一次。
     */
    @Override
    @Transactional
    public RefundLogEntity refund(Long settlementId, Long staffId, String reason) {
        if (settlementId == null) {
            throw new IllegalArgumentException("结算单id不能为空");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("退款理由不能为空");
        }
        String trimmed = reason.trim();
        if (trimmed.length() > 100) {
            throw new IllegalArgumentException("退款理由限 100 字内");
        }
        SettlementEntity settlement = settlementRepository.findById(settlementId)
                .orElseThrow(() -> new IllegalArgumentException("结算单不存在：" + settlementId));
        if (refundLogRepository.findBySettlementId(settlementId).isPresent()) {
            throw new IllegalArgumentException("该结算已退款");
        }
        OrderEntity order = orderRepository.findById(settlement.getOrderId())
                .orElseThrow(() -> new IllegalArgumentException("订单不存在：" + settlement.getOrderId()));
        if (order.getStatus() == null || order.getStatus() != 2) {
            throw new IllegalArgumentException("订单未完结，不能退款");
        }
        int payType = settlement.getPayType() == null ? 0 : settlement.getPayType();
        int money = settlement.getMoney() == null ? 0 : settlement.getMoney();

        if (payType == PAY_STORED_VALUE) {
            // 原路退回：储值余额补回，正数流水 note 标明来源（日对账据此不计入充值）
            RechargeEntity back = new RechargeEntity();
            back.setUserId(settlement.getUserId());
            back.setMoney(money);
            back.setNote("退单 #" + settlementId);
            rechargeRepository.save(back);
        } else if (payType == PAY_CARD) {
            // 次卡：作废该订单核销流水（演示口径无留痕），持卡余次回补
            List<CardUsageEntity> usages = cardUsageRepository.findByOrderId(settlement.getOrderId());
            for (CardUsageEntity usage : usages) {
                customerCardRepository.findById(usage.getCardId()).ifPresent(card -> {
                    card.setRemainingTimes(card.getRemainingTimes() + 1);
                    customerCardRepository.save(card);
                });
            }
            cardUsageRepository.deleteAll(usages);
        }
        // 微信/支付宝/现金：演示口径无真实支付通道，仅落退款记录

        RefundLogEntity refund = new RefundLogEntity();
        refund.setSettlementId(settlementId);
        refund.setOrderId(settlement.getOrderId());
        refund.setMoney(money);
        refund.setPayType(payType);
        refund.setStaffId(staffId);
        refund.setReason(trimmed);
        refund = refundLogRepository.save(refund);

        order.setStatus(1); // 已完成 → 已接单，可重新走结算台收款
        orderRepository.save(order);
        // 提成冲销（美容业态 c）：退单写等额负向记录，净额归零；重收后按新结算单重新记账
        commissionService.reverseForSettlement(settlement);
        log.info("退单：settlement={} 订单 {} 回退待结算，退款 {} 元（payType {}），理由：{}",
                settlementId, order.getId(), money, payType, trimmed);
        return refund;
    }

    /**
     * 日对账单：按自然日聚合收款（结算时间）、充值（充值时间，退款补回的
     * 「退单」流水不计）、退款（退款时间）三段；净额 = 实收 - 退款，
     * 现金净额 = 现金收款 - 现金退款。Java 内聚合（SQLite 方言不做日期分组）。
     */
    @Override
    public ReconciliationView reconciliation(String date) {
        if (date == null || !DATE_PATTERN.matcher(date).matches()) {
            throw new IllegalArgumentException("对账日期格式无效：" + date + "（应为 yyyy-MM-dd）");
        }
        LocalDate day;
        try {
            day = LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("对账日期无效：" + date);
        }
        Date start = Date.from(day.atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date end = Date.from(day.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());

        // 收款段
        List<SettlementEntity> settlements = settlementRepository.findByCreateTimeBetween(start, end);
        Map<Integer, int[]> incomeByPay = new TreeMap<>();
        int incomeMoney = 0;
        int cardCount = 0;
        for (SettlementEntity row : settlements) {
            int money = row.getMoney() == null ? 0 : row.getMoney();
            int payType = row.getPayType() == null ? 0 : row.getPayType();
            int[] agg = incomeByPay.computeIfAbsent(payType, k -> new int[2]);
            agg[0]++;
            agg[1] += money;
            incomeMoney += money;
            if (payType == PAY_CARD) {
                cardCount++;
            }
        }

        // 充值段：只算真实充值（正数流水）；储值支付扣减归收款侧、退款补回（note=退单 #…）归退款侧
        List<RechargeRow> rechargeRows = rechargeRepository.findByCreateTimeBetween(start, end).stream()
                .filter(r -> r.getMoney() != null && r.getMoney() > 0)
                .filter(r -> r.getNote() == null || !r.getNote().startsWith("退单"))
                .map(RechargeRow::from)
                .toList();
        int rechargeMoney = rechargeRows.stream().mapToInt(r -> r.getMoney() == null ? 0 : r.getMoney()).sum();

        // 退款段：customerId 由结算单联出
        List<RefundLogEntity> refundLogs = refundLogRepository.findByCreateTimeBetween(start, end);
        Map<Long, Long> customerBySettlement = settlementRepository.findAllById(
                        refundLogs.stream().map(RefundLogEntity::getSettlementId).toList()).stream()
                .collect(java.util.stream.Collectors.toMap(
                        SettlementEntity::getId, SettlementEntity::getUserId));
        List<RefundRow> refundRows = refundLogs.stream()
                .map(r -> {
                    RefundRow row = RefundRow.from(r);
                    row.setCustomerId(customerBySettlement.get(r.getSettlementId()));
                    row.setPayTypeText(payTypeText(r.getPayType() == null ? 0 : r.getPayType()));
                    return row;
                })
                .sorted(java.util.Comparator.comparing(RefundRow::getId,
                        java.util.Comparator.reverseOrder()))
                .toList();
        Map<Integer, int[]> refundByPay = new TreeMap<>();
        int refundMoney = 0;
        for (RefundRow row : refundRows) {
            int payType = row.getPayType() == null ? 0 : row.getPayType();
            int[] agg = refundByPay.computeIfAbsent(payType, k -> new int[2]);
            agg[0]++;
            agg[1] += row.getMoney() == null ? 0 : row.getMoney();
            refundMoney += row.getMoney() == null ? 0 : row.getMoney();
        }

        int cashIncome = incomeByPay.getOrDefault(PAY_CASH, new int[2])[1];
        int cashRefund = refundByPay.getOrDefault(PAY_CASH, new int[2])[1];
        return ReconciliationView.builder()
                .date(date)
                .incomeCount(settlements.size())
                .incomeMoney(incomeMoney)
                .cardCount(cardCount)
                .incomePayTypes(toPayRows(incomeByPay))
                .rechargeCount(rechargeRows.size())
                .rechargeMoney(rechargeMoney)
                .recharges(rechargeRows)
                .refundCount(refundRows.size())
                .refundMoney(refundMoney)
                .refundPayTypes(toPayRows(refundByPay))
                .refunds(refundRows)
                .netMoney(incomeMoney - refundMoney)
                .cashNetMoney(cashIncome - cashRefund)
                .build();
    }

    private static List<ReconciliationView.PayRow> toPayRows(Map<Integer, int[]> byPay) {
        return byPay.entrySet().stream()
                .map(e -> ReconciliationView.PayRow.builder().payType(e.getKey()).payTypeText(payTypeText(e.getKey()))
                        .count(e.getValue()[0]).money(e.getValue()[1]).build())
                .toList();
    }
}
