package com.sinomed;

import com.sinomed.entity.CustomerEntity;
import com.sinomed.entity.DispenseLogEntity;
import com.sinomed.entity.PrescriptionEntity;
import com.sinomed.repository.CustomerRepository;
import com.sinomed.repository.DispenseLogRepository;
import com.sinomed.repository.PrescriptionRepository;
import com.sinomed.service.PrescriptionService;
import com.sinomed.vo.PrescriptionItemView;
import com.sinomed.vo.PrescriptionView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 药房发药（dispense 单向流转 + findDispensePage 工作台）回归：
 * 待发药(0)→已发药(1) 单向、重复发药拒绝、落发药流水；
 * 代煎/膏方走「已取」自动置已发药（流水 note 写来源、操作人空）；
 * 工作台按状态/顾客姓名手机过滤。库路径随机文件避免污染。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:sqlite:target/dispense-test-${random.uuid}.db"
})
class DispenseTest {

    @Autowired
    private PrescriptionService prescriptionService;

    @Autowired
    private PrescriptionRepository prescriptionRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private DispenseLogRepository dispenseLogRepository;

    private static Long customerId;

    @BeforeEach
    void seedCustomer() {
        if (customerId == null) {
            CustomerEntity customer = new CustomerEntity();
            customer.setName("发药顾客");
            customer.setPhone("13900007001");
            customerId = customerRepository.save(customer).getId();
        }
    }

    private Long create(boolean decoction, boolean paste) {
        PrescriptionItemView herb = new PrescriptionItemView();
        herb.setHerb("当归");
        herb.setWeight(9.0);
        PrescriptionView view = PrescriptionView.builder()
                .customerId(customerId)
                .doses(7)
                .decoction(decoction)
                .prescriptionType(paste ? 1 : 0)
                .craft(paste ? "炼蜜" : null)
                .herbs(List.of(herb))
                .build();
        return prescriptionService.save(view).getId();
    }

    private List<DispenseLogEntity> logs(Long prescriptionId) {
        return dispenseLogRepository.findByPrescriptionIdOrderByIdDesc(prescriptionId);
    }

    @Test
    void dispenseMarksDoneAndWritesLog() {
        Long id = create(false, false);
        assertEquals(0, prescriptionRepository.findById(id).orElseThrow().getDispenseStatus());

        PrescriptionEntity saved = prescriptionService.dispense(id, 66L, "前台自取");
        assertEquals(1, saved.getDispenseStatus());
        assertNotNull(saved.getDispenseTime());
        assertEquals(66L, saved.getDispenseBy());

        List<DispenseLogEntity> entries = logs(id);
        assertEquals(1, entries.size());
        assertEquals(1, entries.get(0).getAction());
        assertEquals(66L, entries.get(0).getStaffId());
        assertEquals("前台自取", entries.get(0).getNote());
    }

    @Test
    void dispenseRejectsDuplicateUnknownAndLongNote() {
        Long id = create(false, false);
        prescriptionService.dispense(id, 66L, null);
        var dup = assertThrows(IllegalArgumentException.class, () -> prescriptionService.dispense(id, 66L, "再发一次"));
        assertTrue(dup.getMessage().contains("已发药"));
        assertEquals(1, logs(id).size(), "重复发药不写第二条流水");

        var missing = assertThrows(IllegalArgumentException.class, () -> prescriptionService.dispense(99999L, null, null));
        assertEquals("处方不存在：99999", missing.getMessage());

        var tooLong = assertThrows(IllegalArgumentException.class,
                () -> prescriptionService.dispense(create(false, false), 66L, "一".repeat(101)));
        assertTrue(tooLong.getMessage().contains("备注限 100 字内"));
    }

    @Test
    void decoctionTakenAutoDispenses() {
        Long id = create(true, false);
        prescriptionService.setDecoctionStatus(id, 2);
        assertEquals(0, prescriptionRepository.findById(id).orElseThrow().getDispenseStatus(), "可取仍待发药");
        prescriptionService.setDecoctionStatus(id, 3);

        PrescriptionEntity done = prescriptionRepository.findById(id).orElseThrow();
        assertEquals(1, done.getDispenseStatus());
        assertEquals(3, done.getDecoctionStatus());
        assertNull(done.getDispenseBy(), "自动发药操作人留空");

        List<DispenseLogEntity> entries = logs(id);
        assertEquals(1, entries.size());
        assertEquals("代煎领取自动发药", entries.get(0).getNote());
        assertNull(entries.get(0).getStaffId());
    }

    @Test
    void pasteTakenAutoDispensesOnceOnly() {
        Long id = create(false, true);
        prescriptionService.setPasteStatus(id, 2);
        prescriptionService.setPasteStatus(id, 3);
        assertEquals(1, prescriptionRepository.findById(id).orElseThrow().getDispenseStatus());

        List<DispenseLogEntity> entries = logs(id);
        assertEquals(1, entries.size(), "「已取」只补一次自动发药流水");
        assertEquals("膏方领取自动发药", entries.get(0).getNote());

        var again = assertThrows(IllegalArgumentException.class, () -> prescriptionService.setPasteStatus(id, 3));
        assertTrue(again.getMessage().contains("流转无效"));
        assertEquals(1, logs(id).size(), "失败流转不再补流水");
    }

    @Test
    void dispensePageFiltersStatusAndCustomer() {
        Long manual = create(false, false);
        Long auto = create(true, false);
        prescriptionService.dispense(manual, 66L, null);
        prescriptionService.setDecoctionStatus(auto, 2);
        prescriptionService.setDecoctionStatus(auto, 3);
        Long waiting = create(false, false);

        PageRequest page = PageRequest.of(0, 10);
        Page<PrescriptionEntity> done = prescriptionService.findDispensePage(1, null, page);
        assertTrue(done.getContent().stream().anyMatch(p -> p.getId().equals(manual)));
        assertTrue(done.getContent().stream().anyMatch(p -> p.getId().equals(auto)));
        assertTrue(done.getContent().stream().noneMatch(p -> p.getId().equals(waiting)));

        Page<PrescriptionEntity> todo = prescriptionService.findDispensePage(0, null, page);
        assertTrue(todo.getContent().stream().anyMatch(p -> p.getId().equals(waiting)));
        assertTrue(todo.getContent().stream().noneMatch(p -> p.getId().equals(manual)));

        Page<PrescriptionEntity> byName = prescriptionService.findDispensePage(null, "发药顾客", page);
        assertTrue(byName.getContent().stream().anyMatch(p -> p.getId().equals(waiting)));
        Page<PrescriptionEntity> byPhone = prescriptionService.findDispensePage(null, "13900007001", page);
        assertTrue(byPhone.getContent().stream().anyMatch(p -> p.getId().equals(waiting)));
        Page<PrescriptionEntity> miss = prescriptionService.findDispensePage(null, "查无此人", page);
        assertEquals(0, miss.getTotalElements(), "关键字无命中返回空页");
    }
}
