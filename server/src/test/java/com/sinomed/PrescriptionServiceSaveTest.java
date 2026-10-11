package com.sinomed;

import com.sinomed.entity.CustomerEntity;
import com.sinomed.repository.CustomerRepository;
import com.sinomed.repository.PrescriptionItemRepository;
import com.sinomed.repository.PrescriptionRepository;
import com.sinomed.service.PrescriptionService;
import com.sinomed.vo.PrescriptionItemView;
import com.sinomed.vo.PrescriptionView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 中药处方开方（PrescriptionService.save）回归：
 * 顾客必填、至少 1 味药、剂数 ≥ 1、逐味校验（药名非空/剂量 > 0）、校验失败整单回滚。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:sqlite:target/prescription-save-test-${random.uuid}.db"
})
class PrescriptionServiceSaveTest {

    @Autowired
    private PrescriptionService prescriptionService;

    @Autowired
    private PrescriptionRepository prescriptionRepository;

    @Autowired
    private PrescriptionItemRepository prescriptionItemRepository;

    @Autowired
    private CustomerRepository customerRepository;

    private static Long customerId;

    @BeforeEach
    void seedCustomer() {
        if (customerId == null) {
            CustomerEntity customer = new CustomerEntity();
            customer.setName("处方顾客");
            customer.setPhone("13900004001");
            customerId = customerRepository.save(customer).getId();
        }
    }

    private PrescriptionItemView herb(String name, Double weight, String special) {
        PrescriptionItemView view = new PrescriptionItemView();
        view.setHerb(name);
        view.setWeight(weight);
        view.setSpecial(special);
        return view;
    }

    @Test
    void savePersistsHerbsWithSortAndTrim() {
        PrescriptionView view = new PrescriptionView();
        view.setCustomerId(customerId);
        var danggui = herb("当归", 10.0, "后下");
        danggui.setProcess("麸炒");
        view.setHerbs(List.of(herb(" 黄芪 ", 30.0, null), danggui));
        view.setUsage("水煎服，日一剂");
        view.setTakeMethod("分早晚温服");
        var saved = prescriptionService.save(view);
        assertTrue(saved.getId() > 0);
        // 主单 + 药味逐条落库，药名 trim、sort 按顺序；炮制/服法随单存档
        var items = prescriptionItemRepository.findAll().stream()
                .filter(i -> i.getPrescriptionId().equals(saved.getId())).toList();
        assertEquals(2, items.size());
        assertEquals("黄芪", items.get(0).getHerb());
        assertEquals("后下", items.get(1).getSpecial());
        assertEquals("麸炒", items.get(1).getProcess());
        assertEquals("分早晚温服", saved.getTakeMethod());
    }

    @Test
    void dosesDefaultToSevenWhenAbsent() {
        PrescriptionView view = new PrescriptionView();
        view.setCustomerId(customerId);
        view.setHerbs(List.of(herb("甘草", 6.0, null)));
        var saved = prescriptionService.save(view);
        assertEquals(7, saved.getDoses());
    }

    @Test
    void missingCustomerRejected() {
        PrescriptionView view = new PrescriptionView();
        view.setHerbs(List.of(herb("甘草", 6.0, null)));
        var e = assertThrows(IllegalArgumentException.class, () -> prescriptionService.save(view));
        assertEquals("顾客id不能为空", e.getMessage());
    }

    @Test
    void emptyHerbsRejected() {
        PrescriptionView view = new PrescriptionView();
        view.setCustomerId(customerId);
        view.setHerbs(List.of());
        var e = assertThrows(IllegalArgumentException.class, () -> prescriptionService.save(view));
        assertEquals("处方至少要有 1 味药", e.getMessage());
    }

    @Test
    void zeroDosesRejected() {
        PrescriptionView view = new PrescriptionView();
        view.setCustomerId(customerId);
        view.setDoses(0);
        view.setHerbs(List.of(herb("甘草", 6.0, null)));
        var e = assertThrows(IllegalArgumentException.class, () -> prescriptionService.save(view));
        assertEquals("剂数必须 ≥ 1", e.getMessage());
    }

    @Test
    void blankHerbNameRejectedAndRolledBack() {
        long before = prescriptionRepository.count();
        PrescriptionView view = new PrescriptionView();
        view.setCustomerId(customerId);
        // 第 1 味合法、第 2 味药名为空：主单已先落，须整单回滚
        view.setHerbs(List.of(herb("黄芪", 30.0, null), herb("  ", 10.0, null)));
        var e = assertThrows(IllegalArgumentException.class, () -> prescriptionService.save(view));
        assertEquals("第 2 味药名不能为空", e.getMessage());
        assertEquals(before, prescriptionRepository.count());
    }

    @Test
    void nonPositiveWeightRejected() {
        PrescriptionView view = new PrescriptionView();
        view.setCustomerId(customerId);
        view.setHerbs(List.of(herb("甘草", 0.0, null)));
        var e = assertThrows(IllegalArgumentException.class, () -> prescriptionService.save(view));
        assertEquals("药材「甘草」剂量必须大于 0", e.getMessage());
    }
}
