package com.sinomed;

import com.sinomed.entity.StaffEntity;
import com.sinomed.repository.StaffRepository;
import com.sinomed.service.StaffService;
import com.sinomed.vo.StaffView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 名医等级：医生标签 + 排序权重（等级 1-3，仅影响列表排序与标识徽章）。
 * 加价口径：名医挂号费溢价上限 20%（配置项 famous-doctor-surge-cap-percent，缺省 20）。
 */
@SpringBootTest(properties = {"spring.datasource.url=jdbc:sqlite:target/famous-doctor-test-${random.uuid}.db"})
@Transactional
class FamousDoctorTest {

    @Autowired
    private StaffService staffService;

    @Autowired
    private StaffRepository staffRepository;

    @Value("${sinomed.famous-doctor-surge-cap-percent:20}")
    private int surgeCapPercent;

    private Long newStaff(String name, int famousLevel) {
        StaffEntity staff = new StaffEntity();
        staff.setName(name);
        staff.setAccount("famous-" + System.nanoTime());
        staff.setPassword("x");
        staff.setStatus(1);
        staff.setTitle("医师");
        staff.setFamousLevel(famousLevel);
        return staffRepository.save(staff).getId();
    }

    @Test
    void famousLevelCrud() {
        Long id = newStaff("华佗", 3);
        StaffEntity found = staffService.findById(id).orElseThrow();
        assertEquals(3, found.getFamousLevel(), "名医等级应落库");

        // 更新等级
        StaffView view = StaffView.fromStaffEntity(found);
        view.setFamousLevel(2);
        StaffEntity updated = staffService.save(view.toStaffEntity());
        assertEquals(2, updated.getFamousLevel(), "更新名医等级应生效");
    }

    @Test
    void famousLevelGuards() {
        Long id = newStaff("张仲景", 0);
        StaffEntity found = staffService.findById(id).orElseThrow();

        // 越界等级应拒绝
        StaffView tooHigh = StaffView.fromStaffEntity(found);
        tooHigh.setFamousLevel(4);
        try {
            staffService.save(tooHigh.toStaffEntity());
            throw new AssertionError("等级 4 应被拒绝");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("名医等级"), "越界等级应报 400：" + e.getMessage());
        }

        // 负数等级应拒绝
        StaffView negative = StaffView.fromStaffEntity(found);
        negative.setFamousLevel(-1);
        try {
            staffService.save(negative.toStaffEntity());
            throw new AssertionError("负数等级应被拒绝");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("名医等级"), "负数等级应报 400：" + e.getMessage());
        }
    }

    @Test
    void surgeCapConfigDefault() {
        assertEquals(20, surgeCapPercent, "溢价上限缺省 20%");
    }

    @Test
    void famousDoctorSortsFirst() {
        Long normal = newStaff("普通医师", 0);
        Long famous1 = newStaff("一级名医", 1);
        Long famous3 = newStaff("三级名医", 3);

        // 医师列表应按 famousLevel 降序（名医在前）
        List<StaffEntity> all = staffService.findAll((StaffEntity) null,
                org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.DESC, "famousLevel"));
        List<Long> ids = all.stream().map(StaffEntity::getId).toList();

        // 名医排在普通医师之前
        int famous3Idx = ids.indexOf(famous3);
        int famous1Idx = ids.indexOf(famous1);
        int normalIdx = ids.indexOf(normal);
        assertTrue(famous3Idx < normalIdx, "三级名医应排在普通医师之前");
        assertTrue(famous1Idx < normalIdx, "一级名医应排在普通医师之前");
        assertTrue(famous3Idx < famous1Idx, "三级名医应排在一级名医之前");
    }
}
