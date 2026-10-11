package com.sinomed;

import com.sinomed.entity.CustomerEntity;
import com.sinomed.entity.ItemEntity;
import com.sinomed.entity.OrderEntity;
import com.sinomed.entity.RechargeEntity;
import com.sinomed.entity.RefundLogEntity;
import com.sinomed.repository.CustomerRepository;
import com.sinomed.repository.ItemRepository;
import com.sinomed.repository.OrderRepository;
import com.sinomed.repository.RechargeRepository;
import com.sinomed.repository.RefundLogRepository;
import com.sinomed.service.CardService;
import com.sinomed.service.SettlementService;
import com.sinomed.vo.CardView;
import com.sinomed.vo.ReconciliationView;
import com.sinomed.vo.SettlementView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 退费与日对账（refund 原路退回 + reconciliation 日聚合）回归：
 * 储值退款补余额（正数流水 note=退单）、次卡退款作废核销回补余次、
 * 微信/现金仅落退款记录、重复退款 400、订单回退后可重收、
 * 日对账三段聚合与现金净额、退款补回流水不计入充值。库路径随机文件避免污染。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:sqlite:target/refund-test-${random.uuid}.db"
})
class RefundReconciliationTest {

    @Autowired
    private SettlementService settlementService;

    @Autowired
    private CardService cardService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private RechargeRepository rechargeRepository;

    @Autowired
    private RefundLogRepository refundLogRepository;

    @Autowired
    private com.sinomed.repository.CardUsageRepository usageRepository;

    private static Long itemId;

    /** 结算结果：结算单 id + 订单 id */
    private record Settled(Long settlementId, Long orderId) {}

    @BeforeEach
    void seed() {
        if (itemId == null) {
            ItemEntity item = new ItemEntity();
            item.setName("退费测试项目");
            item.setDescription("");
            item.setPrice(88);
            item.setEnabled(1);
            item.setSort(0);
            itemId = itemRepository.save(item).getId();
        }
    }

    private Long newCustomer(String phone) {
        CustomerEntity customer = new CustomerEntity();
        customer.setName("退费顾客");
        customer.setPhone(phone);
        return customerRepository.save(customer).getId();
    }

    private Settled settle(Long customer, int price, int payType) {
        OrderEntity order = new OrderEntity();
        order.setUserId(customer);
        order.setItemId(itemId);
        order.setStatus(0);
        order.setPrice(price);
        Long orderId = orderRepository.save(order).getId();
        SettlementView view = SettlementView.builder().orderId(orderId).payType(payType).build();
        return new Settled(settlementService.settle(view).getId(), orderId);
    }

    private RechargeEntity recharge(Long customer, int money) {
        RechargeEntity entity = new RechargeEntity();
        entity.setUserId(customer);
        entity.setMoney(money);
        return entity;
    }

    @Test
    void storedValueRefundRestoresBalanceAndReopensOrder() {
        Long customer = newCustomer("13911001001");
        rechargeRepository.save(recharge(customer, 100));

        Settled settled = settle(customer, 30, 1);
        assertEquals(70, rechargeRepository.sumMoneyByUserId(customer), "储值支付应扣 30");

        RefundLogEntity refund = settlementService.refund(settled.settlementId(), 66L, "收错款");

        assertEquals(100, rechargeRepository.sumMoneyByUserId(customer), "退款应补回余额");
        assertEquals(30, refund.getMoney());
        assertEquals(1, refund.getPayType());
        assertEquals(66L, refund.getStaffId());
        assertEquals("收错款", refund.getReason());
        // 原路退回流水带「退单 #结算id」note（正数流水共两笔：手动充值 100 + 退款补回 30）
        List<RechargeEntity> backs = rechargeRepository.findAll().stream()
                .filter(r -> customer.equals(r.getUserId()) && r.getMoney() != null && r.getMoney() > 0)
                .toList();
        assertEquals(2, backs.size());
        List<RechargeEntity> byNote = backs.stream()
                .filter(r -> ("退单 #" + settled.settlementId()).equals(r.getNote()))
                .toList();
        assertEquals(1, byNote.size(), "应恰好一条退单补回流水");
        assertEquals(30, byNote.get(0).getMoney());
        // 订单回退 2 → 1，可重新收款
        OrderEntity order = orderRepository.findById(settled.orderId()).orElseThrow();
        assertEquals(1, order.getStatus(), "订单应回退到已接单");
        settlementService.settle(SettlementView.builder().orderId(order.getId()).payType(4).build());
        assertEquals(2, orderRepository.findById(order.getId()).orElseThrow().getStatus(), "重收后应再完结");
    }

    @Test
    void cardRefundVoidsUsageAndRestoresTimes() {
        Long customer = newCustomer("13911001002");
        Long cardId = cardService.issue(CardView.builder()
                .customerId(customer).itemId(itemId).totalTimes(10).build()).getId();

        Settled settled = settle(customer, 0, 5);
        assertEquals(9, cardService.listByCustomer(customer).get(0).getRemainingTimes(), "次卡结算应扣 1 次");
        assertEquals(1, usageRepository.findByOrderId(settled.orderId()).size());

        RefundLogEntity refund = settlementService.refund(settled.settlementId(), null, "顾客取消");

        assertEquals(0, refund.getMoney(), "次卡退款金额记 0");
        assertEquals(10, cardService.listByCustomer(customer).get(0).getRemainingTimes(), "余次应回补");
        assertEquals(0, usageRepository.findByOrderId(settled.orderId()).size(), "核销流水应作废");
        assertNull(refund.getStaffId());
    }

    @Test
    void wechatRefundOnlyWritesLogAndDuplicateRefundRejected() {
        Long customer = newCustomer("13911001003");
        Settled settled = settle(customer, 88, 2);

        assertEquals(0, rechargeRepository.sumMoneyByUserId(customer), "微信支付不动储值");
        settlementService.refund(settled.settlementId(), 66L, "多收了");
        assertEquals(0, rechargeRepository.sumMoneyByUserId(customer), "微信退款也不动储值（演示口径仅落记录）");
        assertEquals(1, refundLogRepository.findBySettlementIdIn(List.of(settled.settlementId())).size());

        IllegalArgumentException dup = assertThrows(IllegalArgumentException.class,
                () -> settlementService.refund(settled.settlementId(), 66L, "再退一次"));
        assertTrue(dup.getMessage().contains("该结算已退款"), "重复退款应报已退款：" + dup.getMessage());
    }

    @Test
    void refundGuards() {
        Long customer = newCustomer("13911001004");
        // 结算不存在
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> settlementService.refund(99999L, null, "理由")).getMessage().contains("结算单不存在"));
        // 理由必填与超长
        Settled settled = settle(customer, 88, 4);
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> settlementService.refund(settled.settlementId(), null, "  ")).getMessage().contains("退款理由不能为空"));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> settlementService.refund(settled.settlementId(), null, "一".repeat(101))).getMessage().contains("退款理由限 100 字内"));

        // 未完结订单不能退：先正常退一次，删掉流水后订单已是 1（未完结）
        settlementService.refund(settled.settlementId(), null, "正常退");
        refundLogRepository.deleteAll(refundLogRepository
                .findBySettlementIdIn(List.of(settled.settlementId())));
        IllegalArgumentException guard = assertThrows(IllegalArgumentException.class,
                () -> settlementService.refund(settled.settlementId(), null, "再退"));
        assertTrue(guard.getMessage().contains("订单未完结"), "订单未完结应拦截：" + guard.getMessage());
    }

    @Test
    void reconciliationAggregatesAndExcludesRefundTopUp() {
        Long customer = newCustomer("13911001005");
        String today = LocalDate.now().toString();
        // 同库跑多个用例：先取基线，断言只看本用例增量
        ReconciliationView before = settlementService.reconciliation(today);

        rechargeRepository.save(recharge(customer, 100)); // 充值 ¥100（单列不计营收）
        Settled stored = settle(customer, 30, 1); // 储值收 ¥30
        settle(customer, 50, 2); // 微信收 ¥50
        Settled cash = settle(customer, 20, 4); // 现金收 ¥20
        settlementService.refund(cash.settlementId(), 66L, "现金退演示"); // 现金退 ¥20
        settlementService.refund(stored.settlementId(), 66L, "储值退演示"); // 储值退 ¥30（补回流水带退单 note）

        ReconciliationView report = settlementService.reconciliation(today);

        assertEquals(today, report.getDate());
        assertEquals(3, report.getIncomeCount() - before.getIncomeCount(), "本用例结算 3 单");
        assertEquals(100, report.getIncomeMoney() - before.getIncomeMoney(), "实收 = 30+50+20");
        assertEquals(1, report.getRechargeCount() - before.getRechargeCount(), "退款补回流水不计入充值笔数");
        assertEquals(100, report.getRechargeMoney() - before.getRechargeMoney(), "充值金额只算真实充值");
        assertTrue(report.getRecharges().stream().noneMatch(r -> r.getNote() != null && r.getNote().startsWith("退单")),
                "充值明细不含退款补回流水");
        assertEquals(2, report.getRefundCount() - before.getRefundCount(), "退款 2 笔");
        assertEquals(50, report.getRefundMoney() - before.getRefundMoney(), "退款 = 20+30");
        assertEquals(50, report.getNetMoney() - before.getNetMoney(), "净额增量 = 实收 100 - 退款 50");
        assertEquals(0, report.getCashNetMoney() - before.getCashNetMoney(), "现金净额增量 = 现金收 20 - 现金退 20");
        // 退款明细按新记录在前，且能按理由定位到本用例两笔
        assertTrue(report.getRefunds().size() >= before.getRefunds().size() + 2);
        assertTrue(report.getRefunds().stream().anyMatch(r -> "现金退演示".equals(r.getReason())
                && Integer.valueOf(20).equals(r.getMoney()) && Integer.valueOf(4).equals(r.getPayType())));
        assertTrue(report.getRefunds().stream().anyMatch(r -> "储值退演示".equals(r.getReason())
                && Integer.valueOf(30).equals(r.getMoney()) && Integer.valueOf(1).equals(r.getPayType())));
        assertNotNull(report.getRefunds().get(0).getId(), "退款明细应非空");
    }

    @Test
    void reconciliationDateGuards() {
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> settlementService.reconciliation("abc")).getMessage().contains("对账日期格式无效"));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> settlementService.reconciliation("2026-13-40")).getMessage().contains("对账日期无效"));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> settlementService.reconciliation(null)).getMessage().contains("对账日期格式无效"));
    }
}
