package com.sinomed;

import com.sinomed.entity.CustomerCardEntity;
import com.sinomed.entity.CustomerEntity;
import com.sinomed.entity.ItemEntity;
import com.sinomed.repository.CustomerCardRepository;
import com.sinomed.repository.CustomerRepository;
import com.sinomed.repository.ItemRepository;
import com.sinomed.service.CardService;
import com.sinomed.vo.CardView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.List;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 次卡（CardService）回归：发卡校验、按顾客查询、停用/恢复、
 * 抵扣「早发的先扣」且跳过停用与余 0 卡。库路径随机文件避免污染；
 * 抵扣类用例各用新顾客，避免方法执行顺序耦合。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:sqlite:target/card-service-test-${random.uuid}.db"
})
class CardServiceTest {

    @Autowired
    private CardService cardService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private CustomerCardRepository cardRepository;

    private static Long itemId;

    @BeforeEach
    void seed() {
        if (itemId == null) {
            ItemEntity item = new ItemEntity();
            item.setName("测试次卡项");
            item.setDescription("");
            item.setPrice(680);
            item.setEnabled(1);
            item.setSort(0);
            itemId = itemRepository.save(item).getId();
        }
    }

    private Long newCustomer(String phone) {
        CustomerEntity customer = new CustomerEntity();
        customer.setName("持卡顾客");
        customer.setPhone(phone);
        return customerRepository.save(customer).getId();
    }

    private CardView view(Long customer, Long item, Integer total) {
        return CardView.builder().customerId(customer).itemId(item).totalTimes(total).build();
    }

    @Test
    void issueCreatesCardWithFullRemaining() {
        Long customer = newCustomer("13900005011");
        var card = cardService.issue(view(customer, itemId, 10));
        assertTrue(card.getId() > 0);
        assertEquals(10, card.getTotalTimes());
        assertEquals(10, card.getRemainingTimes());
        assertEquals(1, card.getStatus());
    }

    @Test
    void issueRejectsBadReferencesAndTimes() {
        Long customer = newCustomer("13900005012");
        var e1 = assertThrows(IllegalArgumentException.class, () -> cardService.issue(view(99999L, itemId, 5)));
        assertEquals("顾客不存在：99999", e1.getMessage());
        var e2 = assertThrows(IllegalArgumentException.class, () -> cardService.issue(view(customer, 99999L, 5)));
        assertEquals("卡项不存在：99999", e2.getMessage());
        var e3 = assertThrows(IllegalArgumentException.class, () -> cardService.issue(view(customer, itemId, 0)));
        assertEquals("总次数必须 ≥ 1", e3.getMessage());
        var e4 = assertThrows(IllegalArgumentException.class, () -> cardService.issue(CardView.builder()
                .customerId(customer).itemId(itemId).totalTimes(5).sourceOrderId(5L).build()));
        assertEquals("订单不存在：5", e4.getMessage());
    }

    @Test
    void listByCustomerReturnsOnlyThatCustomer() {
        Long mine = newCustomer("13900005013");
        Long other = newCustomer("13900005014");
        cardService.issue(view(mine, itemId, 10));
        cardService.issue(view(other, itemId, 5));

        var mineCards = cardService.listByCustomer(mine);
        assertEquals(1, mineCards.size());
        assertEquals(10, mineCards.get(0).getTotalTimes());
        assertEquals(1, cardService.listByCustomer(other).size());
    }

    @Test
    void setStatusToggles() {
        Long customer = newCustomer("13900005015");
        var card = cardService.issue(view(customer, itemId, 10));
        assertEquals(0, cardService.setStatus(card.getId(), 0).getStatus());
        assertEquals(1, cardService.setStatus(card.getId(), 1).getStatus());
        var e = assertThrows(IllegalArgumentException.class, () -> cardService.setStatus(card.getId(), 2));
        assertEquals("状态无效：1 有效 / 0 停用", e.getMessage());
    }

    @Test
    void deductTakesOldestActiveCardFirst() {
        Long customer = newCustomer("13900005016");
        var older = cardService.issue(view(customer, itemId, 10));
        var newer = cardService.issue(view(customer, itemId, 10));
        var deducted = cardService.deduct(customer, itemId, null);
        assertEquals(older.getId(), deducted.getId());
        assertEquals(9, deducted.getRemainingTimes());
        // 再扣仍是最老那张（余 9 > 0）
        assertEquals(older.getId(), cardService.deduct(customer, itemId, null).getId());
        assertEquals(8, cardRepository.findById(older.getId()).orElseThrow().getRemainingTimes());
        assertEquals(10, cardRepository.findById(newer.getId()).orElseThrow().getRemainingTimes());
    }

    @Test
    void deductSkipsDisabledAndEmptyCards() {
        Long customer = newCustomer("13900005017");
        var disabled = cardService.issue(view(customer, itemId, 10));
        cardService.setStatus(disabled.getId(), 0);
        var single = cardService.issue(view(customer, itemId, 1));
        cardService.deduct(customer, itemId, null); // 把余 1 扣光
        assertEquals(0, cardRepository.findById(single.getId()).orElseThrow().getRemainingTimes());
        var e = assertThrows(IllegalArgumentException.class, () -> cardService.deduct(customer, itemId, null));
        assertTrue(e.getMessage().contains("无可抵扣次卡"));
    }

    @Test
    void deductRejectsNoCardAtAll() {
        Long customer = newCustomer("13900005018");
        var e = assertThrows(IllegalArgumentException.class, () -> cardService.deduct(customer, itemId, null));
        assertTrue(e.getMessage().contains("无可抵扣次卡"));
    }

    @Test
    void issueStoresValidUntilAndDeductSkipsExpired() {
        Long customer = newCustomer("13900005019");
        var expired = cardService.issue(CardView.builder()
                .customerId(customer).itemId(itemId).totalTimes(10)
                .validUntil(Date.from(Instant.now().minus(1, ChronoUnit.DAYS))).build());
        // 有效期落库读回：已过（参与抵扣判定用 before(now)，只断言方向不掐精度）
        assertTrue(expired.getValidUntil() != null && expired.getValidUntil().before(new Date()));
        var valid = cardService.issue(view(customer, itemId, 10));

        // 过期卡跳过，扣到未过期那张
        var deducted = cardService.deduct(customer, itemId, null);
        assertEquals(valid.getId(), deducted.getId());
        assertEquals(10, cardRepository.findById(expired.getId()).orElseThrow().getRemainingTimes());

        // 只剩过期卡时无可抵扣
        var only = newCustomer("13900005020");
        cardService.issue(CardView.builder()
                .customerId(only).itemId(itemId).totalTimes(5)
                .validUntil(Date.from(Instant.now().minus(1, ChronoUnit.DAYS))).build());
        var e = assertThrows(IllegalArgumentException.class, () -> cardService.deduct(only, itemId, null));
        assertTrue(e.getMessage().contains("无可抵扣次卡"));
    }

    @Test
    void listByCustomerIdsReturnsCardsAcrossCustomers() {
        var mine = newCustomer("13900005023");
        var other = newCustomer("13900005024");
        cardService.issue(CardView.builder().customerId(mine).itemId(itemId).totalTimes(10).build());
        cardService.issue(CardView.builder().customerId(other).itemId(itemId).totalTimes(5).build());

        List<CustomerCardEntity> found = cardService.listByCustomerIds(List.of(mine, other));
        assertEquals(2, found.size());
        assertTrue(found.stream().anyMatch((c) -> c.getCustomerId().equals(mine)));
        assertTrue(found.stream().anyMatch((c) -> c.getCustomerId().equals(other)));
        assertTrue(cardService.listByCustomerIds(List.of()).isEmpty(), "空入参返回空列表");
    }
}
