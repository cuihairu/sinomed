package com.sinomed;

import com.sinomed.entity.HerbEntity;
import com.sinomed.repository.HerbRepository;
import com.sinomed.service.DoseService;
import com.sinomed.vo.DoseResultView;
import com.sinomed.vo.PrescriptionItemView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 剂量审方（提示不拦截）：单剂克数严格大于药材字典 dose_max 才命中，等值不提示；
 * 未设上限不参与比对，未收录药名进 unmatched 无参照；名单为空报 400。
 */
@SpringBootTest(properties = {"spring.datasource.url=jdbc:sqlite:target/dose-test-${random.uuid}.db"})
@Transactional
class PrescriptionDoseTest {

    @Autowired
    private DoseService doseService;

    @Autowired
    private HerbRepository herbRepository;

    private void addHerb(String name, Double doseMax) {
        HerbEntity herb = new HerbEntity();
        herb.setName(name);
        herb.setPrice(100);
        herb.setDoseMax(doseMax);
        herbRepository.save(herb);
    }

    private PrescriptionItemView item(String herb, double weight) {
        return PrescriptionItemView.builder().herb(herb).weight(weight).build();
    }

    @Test
    void weightAboveLimitHitsAtLimitDoesNot() {
        addHerb("附子", 10.0);
        addHerb("干姜", 6.0);

        DoseResultView result = doseService.check(
                List.of(item("附子", 15), item("附子", 10), item("干姜", 6)));
        assertEquals(3, result.getChecked());
        assertEquals(3, result.getLimited(), "三味都设了上限");
        assertEquals(1, result.getFindings().size(), "只有超上限那味命中：" + result.getFindings());
        DoseResultView.Finding hit = result.getFindings().get(0);
        assertEquals("附子", hit.getHerb());
        assertEquals(15.0, hit.getWeight());
        assertEquals(10.0, hit.getLimit());
    }

    @Test
    void missingLimitSkippedAndUnknownHerbReported() {
        addHerb("黄芪", 10.0);
        // 未设上限：超多少也不预警（可空=不预警）
        addHerb("熟地黄", null);

        DoseResultView result = doseService.check(
                List.of(item("黄芪", 30), item("熟地黄", 99), item("神秘药材", 50)));
        assertEquals(3, result.getChecked());
        assertEquals(1, result.getLimited(), "只黄芪设了上限");
        assertEquals(1, result.getFindings().size(), "只黄芪命中：" + result.getFindings());
        assertEquals(1, result.getUnmatched().size(), "未收录药名进 unmatched");
        assertEquals("神秘药材", result.getUnmatched().get(0));
    }

    @Test
    void blankAndNullHerbsRejected() {
        try {
            doseService.check(Arrays.asList(item("  ", 10), (PrescriptionItemView) null));
            throw new AssertionError("空白药味应报 400");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("药材名单不能为空"), "应提示名单为空：" + e.getMessage());
        }
        try {
            doseService.check(null);
            throw new AssertionError("null 名单应报 400");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("药材名单不能为空"), "应提示名单为空：" + e.getMessage());
        }
    }
}
