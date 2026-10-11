package com.sinomed;

import com.sinomed.entity.CustomerEntity;
import com.sinomed.entity.ItemEntity;
import com.sinomed.entity.StaffEntity;
import com.sinomed.repository.CustomerRepository;
import com.sinomed.repository.ItemRepository;
import com.sinomed.repository.OrderItemRepository;
import com.sinomed.repository.StaffRepository;
import com.sinomed.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 前台建单（OrderService.create）回归：卡项存在/上架校验、价格快照、status=0、建单人落库。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:sqlite:target/order-create-test-${random.uuid}.db"
})
class OrderServiceCreateTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private StaffRepository staffRepository;

    private static Long customerId;
    private static Long staffId;
    private static Long itemId;
    private static Long disabledItemId;

    @BeforeEach
    void seedData() {
        if (itemId == null) {
            CustomerEntity customer = new CustomerEntity();
            customer.setName("建档顾客");
            customer.setPhone("13900003001");
            customerId = customerRepository.save(customer).getId();

            StaffEntity staff = new StaffEntity();
            staff.setName("建单员工");
            staff.setAccount("order-test-staff");
            staff.setStatus(1);
            staffId = staffRepository.save(staff).getId();

            ItemEntity item = new ItemEntity();
            item.setName("前台建单卡项");
            item.setDescription("");
            item.setPrice(288);
            item.setEnabled(1);
            item.setSort(0);
            itemId = itemRepository.save(item).getId();

            ItemEntity disabled = new ItemEntity();
            disabled.setName("前台建单下架项");
            disabled.setDescription("");
            disabled.setPrice(66);
            disabled.setEnabled(0);
            disabled.setSort(0);
            disabledItemId = itemRepository.save(disabled).getId();
        }
    }

    @Test
    void createRecordsPriceSnapshotStatusAndStaff() {
        var order = orderService.create(customerId, itemId, staffId);
        assertEquals(0, order.getStatus());
        assertEquals(288, order.getPrice());
        assertEquals(customerId, order.getUserId());
        assertEquals(itemId, order.getItemId());
        assertEquals(staffId, order.getStaffId());
    }

    @Test
    void createWithoutStaffKeepsStaffNull() {
        var order = orderService.create(customerId, itemId, null);
        assertEquals(0, order.getStatus());
        assertEquals(null, order.getStaffId());
    }

    @Test
    void nonexistentItemRejected() {
        var e = assertThrows(IllegalArgumentException.class,
                () -> orderService.create(customerId, 99999L, null));
        assertTrue(e.getMessage().startsWith("卡项不存在"));
    }

    @Test
    void disabledItemRejected() {
        var e = assertThrows(IllegalArgumentException.class,
                () -> orderService.create(customerId, disabledItemId, null));
        assertTrue(e.getMessage().startsWith("卡项已下架"));
    }

    @Test
    void createWritesDetailLineWithNameSnapshot() {
        var order = orderService.create(customerId, itemId, null);
        var lines = orderItemRepository.findByOrderIdOrderByIdAsc(order.getId());
        assertEquals(1, lines.size());
        assertEquals("前台建单卡项", lines.get(0).getName());
        assertEquals(288, lines.get(0).getPrice());
        assertEquals(1, lines.get(0).getQty());
        assertEquals(288, lines.get(0).getSubtotal());
    }

    @Test
    void createWithLinesSumsTotalsAndSnapshotsNames() {
        var order = orderService.createWithLines(customerId,
                List.of(new OrderService.Line(itemId, 1), new OrderService.Line(itemId, 2)), staffId);
        assertEquals(0, order.getStatus());
        assertEquals(itemId, order.getItemId()); // 首行卡项
        assertEquals(288 * 3, order.getPrice()); // 合计 = 各行小计和
        var lines = orderItemRepository.findByOrderIdOrderByIdAsc(order.getId());
        assertEquals(2, lines.size());
        assertEquals(1, lines.get(0).getQty());
        assertEquals(288, lines.get(0).getSubtotal());
        assertEquals(2, lines.get(1).getQty());
        assertEquals(576, lines.get(1).getSubtotal());
        assertEquals("前台建单卡项", lines.get(1).getName());
    }

    @Test
    void createWithLinesRejectsDisabledItemAndBadQty() {
        var e1 = assertThrows(IllegalArgumentException.class, () -> orderService.createWithLines(customerId,
                List.of(new OrderService.Line(itemId, 1), new OrderService.Line(disabledItemId, 1)), null));
        assertTrue(e1.getMessage().startsWith("卡项已下架"));
        var e2 = assertThrows(IllegalArgumentException.class,
                () -> orderService.createWithLines(customerId, List.of(new OrderService.Line(itemId, 0)), null));
        assertEquals("明细行数量须 ≥1", e2.getMessage());
        var e3 = assertThrows(IllegalArgumentException.class,
                () -> orderService.createWithLines(customerId, List.of(), null));
        assertEquals("明细行不能为空", e3.getMessage());
    }

    @Test
    void remarkUpdateTrimsAndClears() {
        var order = orderService.create(customerId, itemId, null);
        var saved = orderService.updateRemark(order.getId(), "  代煎 7 袋，先试饮  ");
        assertEquals("代煎 7 袋，先试饮", saved.getRemark());
        var cleared = orderService.updateRemark(order.getId(), "  ");
        assertEquals(null, cleared.getRemark());
        var e = assertThrows(IllegalArgumentException.class,
                () -> orderService.updateRemark(order.getId(), "长".repeat(201)));
        assertEquals("备注最长 200 字", e.getMessage());
    }

    @Test
    void findPageOrdersByCreateTimeDescThenIdDesc() {
        var first = orderService.create(customerId, itemId, null).getId();
        var second = orderService.create(customerId, itemId, null).getId();
        var page = orderService.findPage(null, null, org.springframework.data.domain.PageRequest.of(0, 10));
        var ids = page.map(var -> var.getId()).getContent();
        assertTrue(ids.indexOf(second) < ids.indexOf(first), "同秒建单按 id 倒序，新单在前");
    }
}
