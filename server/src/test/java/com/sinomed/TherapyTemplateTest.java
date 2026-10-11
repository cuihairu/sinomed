package com.sinomed;

import com.sinomed.entity.TherapyTemplateEntity;
import com.sinomed.repository.TherapyTemplateRepository;
import com.sinomed.service.TherapyTemplateService;
import com.sinomed.vo.TherapyTemplateView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.junit.jupiter.api.function.Executable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 外治处方模板服务回归：建/查/分页过滤、上架列表、更新与唯一性、守卫。
 */
@SpringBootTest(properties = {"spring.datasource.url=jdbc:sqlite:target/therapy-template-test-${random.uuid}.db"})
@Transactional
class TherapyTemplateTest {

    @Autowired
    private TherapyTemplateService therapyTemplateService;

    @Autowired
    private TherapyTemplateRepository therapyTemplateRepository;

    private static final Pageable PAGE = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "id"));

    private TherapyTemplateView view(String name, String method) {
        return TherapyTemplateView.builder().name(name).method(method).target("神门、交感")
                .course("每周 2 次 × 4 周").note("每日揉按").enabled(1).build();
    }

    @Test
    void savePageFiltersAndEnabledList() {
        TherapyTemplateView saved = therapyTemplateService.findById(
                therapyTemplateService.save(view("失眠·耳穴压豆", "耳穴压豆")).getId());
        assertEquals("失眠·耳穴压豆", saved.getName());
        assertEquals("耳穴压豆", saved.getMethod());
        assertEquals("神门、交感", saved.getTarget());
        assertEquals(1, saved.getEnabled());
        assertNotNull(saved.getCreateTime(), "审计写入创建时间");

        therapyTemplateService.save(view("咳喘·穴位贴敷", "穴位贴敷"));
        TherapyTemplateEntity off = therapyTemplateService.save(TherapyTemplateView.builder()
                .name("肾阳虚·艾灸").method("艾灸").target("关元、气海").enabled(0).build());

        assertEquals(3, therapyTemplateService.findPage(null, null, PAGE).getTotalElements());
        // 名称模糊
        assertEquals(1, therapyTemplateService.findPage("失眠", null, PAGE).getTotalElements());
        // 方法精确
        assertEquals(1, therapyTemplateService.findPage(null, "艾灸", PAGE).getTotalElements());
        // 两个条件一起
        assertEquals(0, therapyTemplateService.findPage("失眠", "艾灸", PAGE).getTotalElements());

        List<TherapyTemplateView> enabled = therapyTemplateService.findEnabled();
        assertEquals(2, enabled.size(), "停用模板不进接诊页列表");
        assertTrue(enabled.stream().allMatch(t -> Integer.valueOf(1).equals(t.getEnabled())));
        assertTrue(enabled.stream().map(TherapyTemplateView::getName)
                .allMatch(n -> n.equals("失眠·耳穴压豆") || n.equals("咳喘·穴位贴敷")));

        TherapyTemplateView detail = therapyTemplateService.findById(off.getId());
        assertNull(detail.getCourse());
        assertNotNull(detail.getCreateTime());
    }

    @Test
    void updateKeepsSelfExemptUniqueName() {
        TherapyTemplateEntity first = therapyTemplateService.save(view("腰痛·留罐", "拔罐"));
        therapyTemplateService.save(view("失眠·耳穴压豆", "耳穴压豆"));

        // 沿用自身名称不算撞名
        TherapyTemplateView same = TherapyTemplateView.builder().id(first.getId()).name("腰痛·留罐")
                .method("拔罐").target("肾俞、大肠俞").enabled(1).build();
        assertEquals(first.getId(), therapyTemplateService.update(same).getId());

        // 换名撞其他模板被拒
        TherapyTemplateView clash = TherapyTemplateView.builder().id(first.getId()).name("失眠·耳穴压豆")
                .method("拔罐").enabled(1).build();
        assertThrows(IllegalArgumentException.class, () -> therapyTemplateService.update(clash));

        // 换名成功并回读
        TherapyTemplateView renamed = TherapyTemplateView.builder().id(first.getId()).name("腰痛·走罐")
                .method("走罐").target("肾俞、腰阳关").course("每周 2 次 × 3 周").note("罐斑紫暗属正常")
                .enabled(1).build();
        TherapyTemplateView updated = therapyTemplateService.findById(
                therapyTemplateService.update(renamed).getId());
        assertEquals("腰痛·走罐", updated.getName());
        assertEquals("走罐", updated.getMethod());
        assertEquals("罐斑紫暗属正常", updated.getNote());
        assertNotNull(updated.getUpdateTime());
    }

    @Test
    void guards() {
        long before = therapyTemplateRepository.count();
        check(() -> therapyTemplateService.save(view(null, "耳穴压豆")), "外治模板名不能为空");
        check(() -> therapyTemplateService.save(view("  ", "耳穴压豆")), "外治模板名不能为空");
        check(() -> therapyTemplateService.save(view("一".repeat(21), "耳穴压豆")), "外治模板名限 20 字内");
        check(() -> therapyTemplateService.save(view("失眠·耳穴压豆", null)), "外治方法必填");
        check(() -> therapyTemplateService.save(view("失眠·耳穴压豆", "   ")), "外治方法必填");
        check(() -> therapyTemplateService.save(view("失眠·耳穴压豆", "一二三四五六七八九十十一")), "外治方法限 10 字内");
        check(() -> therapyTemplateService.save(TherapyTemplateView.builder().name("失眠·耳穴压豆")
                .method("耳穴压豆").target("一".repeat(101)).build()), "取穴或部位限 100 字内");
        check(() -> therapyTemplateService.save(TherapyTemplateView.builder().name("失眠·耳穴压豆")
                .method("耳穴压豆").course("一".repeat(101)).build()), "疗程限 100 字内");
        check(() -> therapyTemplateService.save(TherapyTemplateView.builder().name("失眠·耳穴压豆")
                .method("耳穴压豆").note("一".repeat(101)).build()), "注意事项限 100 字内");

        // 重复名（更新豁免自身后，新建仍应被拒）
        therapyTemplateService.save(view("失眠·耳穴压豆", "耳穴压豆"));
        check(() -> therapyTemplateService.save(view("失眠·耳穴压豆", "耳穴压豆")), "外治模板名已存在");

        // 更新/删除不存在的模板
        check(() -> therapyTemplateService.update(TherapyTemplateView.builder().id(9999L)
                .name("不存在").method("拔罐").build()), "模板不存在");
        check(() -> therapyTemplateService.findById(9999L), "模板不存在");
        check(() -> therapyTemplateService.deleteById(9999L), "模板不存在");

        assertEquals(before + 1, therapyTemplateRepository.count(), "守卫抛错不落单，只落了那条合法新建");
    }

    private void check(Executable action, String message) {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, action);
        assertTrue(ex.getMessage().contains(message),
                "期望包含「" + message + "」，实际：[" + ex.getMessage() + "]");
    }
}
