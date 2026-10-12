package com.sinomed;

import com.sinomed.entity.HerbEntity;
import com.sinomed.repository.HerbRepository;
import com.sinomed.service.HerbStockService;
import com.sinomed.vo.HerbStockBalanceView;
import com.sinomed.vo.HerbStockLogView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 饮片出入库：入库→出库→余额（FEFO 消耗批次）、近期到期预警、守卫、分页过滤与删除。
 * 效期以「今天 ±N 天」生成，避免用例依赖运行日期。
 */
@SpringBootTest(properties = {"spring.datasource.url=jdbc:sqlite:target/herb-stock-test-${random.uuid}.db"})
@Transactional
class HerbStockTest {

    @Autowired
    private HerbStockService herbStockService;

    @Autowired
    private HerbRepository herbRepository;

    private Long newHerb(String name) {
        HerbEntity herb = new HerbEntity();
        herb.setName(name);
        herb.setPrice(100);
        return herbRepository.save(herb).getId();
    }

    private String plusDays(int days) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, days);
        return new SimpleDateFormat("yyyy-MM-dd").format(cal.getTime());
    }

    private HerbStockLogView view(Long herbId, int type, int quantity, String expiry) {
        return HerbStockLogView.builder().herbId(herbId).type(type).quantity(quantity).expiry(expiry).build();
    }

    private HerbStockLogView in(Long herbId, int quantity, String expiry) {
        return herbStockService.addLog(view(herbId, 1, quantity, expiry));
    }

    private HerbStockLogView out(Long herbId, int quantity) {
        return herbStockService.addLog(view(herbId, 0, quantity, null));
    }

    @Test
    void balanceCountsInMinusOutAndFefoConsumesEarliestExpiry() {
        Long herbId = newHerb("黄芪");
        String near = plusDays(60);
        String far = plusDays(120);
        in(herbId, 1000, far);
        in(herbId, 500, near);
        out(herbId, 400);

        HerbStockBalanceView row = herbStockService.balance(30).get(0);
        assertEquals(herbId, row.getHerbId());
        assertEquals("黄芪", row.getName());
        assertEquals(1100, row.getStock(), "入库合计 - 出库合计");
        assertEquals(near, row.getNextExpiry(), "出库按效期先进先出消耗最早批次");
        assertEquals(60L, row.getExpiryInDays());
        assertFalse(row.getWarnExpiry(), "60 天 > 30 天不预警");

        assertTrue(herbStockService.balance(60).get(0).getWarnExpiry(), "60 天到期在 60 天阈值内预警");
        assertFalse(herbStockService.balance(59).get(0).getWarnExpiry());
    }

    @Test
    void balanceWarnsOnStockLimits() {
        HerbEntity herb = new HerbEntity();
        herb.setName("白芷");
        herb.setPrice(100);
        herb.setStockMin(500);
        herb.setStockMax(5000);
        Long herbId = herbRepository.save(herb).getId();

        in(herbId, 300, null);
        HerbStockBalanceView low = herbStockService.balance(30).get(0);
        assertTrue(low.getWarnStock(), "300 克低于下限 500 预警");
        assertEquals(500, low.getStockMin());
        assertEquals(5000, low.getStockMax());

        out(herbId, 300);
        in(herbId, 6000, null);
        assertTrue(herbStockService.balance(30).get(0).getWarnStock(), "6000 克高于上限 5000 预警");
    }

    @Test
    void guards() {
        Long herbId = newHerb("当归");
        in(herbId, 100, plusDays(90));
        // 出库超库存
        assertEquals("出库数量超过当前库存：100 克",
                assertThrows(IllegalArgumentException.class, () -> herbStockService.addLog(view(herbId, 0, 101, null)))
                        .getMessage());
        // 其余守卫
        record Case(HerbStockLogView input, String expect) {}
        for (Case c : List.of(
                new Case(view(9999L, 1, 10, null), "药材不存在"),
                new Case(view(herbId, 2, 10, null), "流水类型无效"),
                new Case(view(herbId, 1, 0, null), "数量须为正整数（克）"),
                new Case(view(herbId, 1, -5, null), "数量须为正整数（克）"),
                new Case(view(herbId, 1, 10, "2026/01/01"), "效期格式无效"),
                new Case(view(herbId, 1, 10, "2026-13-40"), "效期格式无效"),
                new Case(view(herbId, 1, 10, plusDays(-1)), "效期已过期"),
                new Case(HerbStockLogView.builder().herbId(herbId).type(1).quantity(10)
                        .supplier("x".repeat(51)).build(), "供货方限 50 字内"),
                new Case(HerbStockLogView.builder().herbId(herbId).type(1).quantity(10)
                        .note("x".repeat(101)).build(), "备注限 100 字内"),
                new Case(null, "药材必填")
        )) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> herbStockService.addLog(c.input()), c.expect());
            assertTrue(e.getMessage().contains(c.expect()), e.getMessage());
        }
        // 失败登记不落单
        assertEquals(1, herbStockService.findPage(null, null,
                        PageRequest.of(0, 50, Sort.by(Sort.Direction.ASC, "id"))).getTotalElements(),
                "守卫抛错不应落流水");
    }

    @Test
    void pageFiltersJoinHerbNameAndDeleteRecomputesBalance() {
        Long herbA = newHerb("白术");
        Long herbB = newHerb("茯苓");
        in(herbA, 100, plusDays(90));
        out(herbA, 30);
        in(herbB, 200, plusDays(180));

        var all = herbStockService.findPage(null, null,
                PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "id")));
        assertEquals(3, all.getTotalElements());
        assertEquals("茯苓", all.getContent().get(0).getHerbName(), "id 倒序 + 联药材名");

        assertEquals(2, herbStockService.findPage(herbA, null,
                PageRequest.of(0, 50)).getTotalElements(), "按药材过滤");
        assertEquals(1, herbStockService.findPage(null, 0,
                PageRequest.of(0, 50)).getTotalElements(), "按类型过滤");
        assertEquals(0, herbStockService.findPage(herbB, 0,
                PageRequest.of(0, 50)).getTotalElements(), "无出库的药材为空页");

        Long deleted = herbStockService.findPage(herbB, null, PageRequest.of(0, 50)).getContent().get(0).getId();
        HerbStockLogView removed = herbStockService.deleteById(deleted);
        assertEquals("茯苓", removed.getHerbName());
        assertEquals(2, herbStockService.findPage(null, null, PageRequest.of(0, 50)).getTotalElements());
        // 删光流水的药材不成行；白术仍在且余额重算为 70 克
        List<HerbStockBalanceView> remaining = herbStockService.balance(30);
        assertEquals(1, remaining.size());
        assertEquals("白术", remaining.get(0).getName());
        assertEquals(70, remaining.get(0).getStock());

        assertEquals("流水不存在",
                assertThrows(IllegalArgumentException.class, () -> herbStockService.deleteById(9999L)).getMessage());
    }
}
