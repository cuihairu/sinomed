package com.sinomed.seed;

import com.sinomed.entity.DispenseLogEntity;
import com.sinomed.entity.PrescriptionEntity;
import com.sinomed.entity.PrescriptionItemEntity;
import com.sinomed.repository.DispenseLogRepository;
import com.sinomed.repository.PrescriptionItemRepository;
import com.sinomed.repository.PrescriptionRepository;
import com.sinomed.util.DateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 药房域种子（LAY-002 拆分）：中药处方（汤剂代煎 + 膏方）、药房发药演示
 * （最早一张处方置「已发药」，其余留给药房工作台走流转）。按自然日/膏方有无判重。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PharmacySeedService {

    private final PrescriptionRepository prescriptionRepository;
    private final PrescriptionItemRepository prescriptionItemRepository;
    private final DispenseLogRepository dispenseLogRepository;
    private final SeedSupport support;

    /** 中药处方演示数据：王慕清昨日一张逍遥散化裁（药材名为自由文本，虚构演示） */
    public void seedPrescriptions(Map<String, Long> customers) {
        record HerbSeed(String herb, double weight, String special) {}
        Long customerId = customers.get("13900000001");
        Long staffId = support.staffByAccount("shen");
        if (customerId == null) {
            return;
        }
        // 汤剂（代煎）：已有昨日处方即跳过
        if (!prescriptionOnDayExists(customerId, -1)) {
            List<HerbSeed> herbs = List.of(
                    new HerbSeed("柴胡", 12, null),
                    new HerbSeed("白芍", 15, null),
                    new HerbSeed("当归", 10, null),
                    new HerbSeed("茯苓", 15, null),
                    new HerbSeed("白术", 12, null),
                    new HerbSeed("薄荷", 6, "后下"),
                    new HerbSeed("炙甘草", 6, null)
            );
            PrescriptionEntity prescription = new PrescriptionEntity();
            prescription.setCustomerId(customerId);
            prescription.setStaffId(staffId);
            prescription.setDoses(7);
            prescription.setPrescriptionType(0);
            prescription.setPasteStatus(0);
            prescription.setDispenseStatus(0); // 列 NOT NULL 需显式置 0（待发药）
            // 昨日开方今日可取（0 无需/1 待煎/2 可取/3 已取），留给演示走「已取」流转
            prescription.setDecoctionStatus(2);
            prescription.setDecoctionBags(7);
            prescription.setUsage("水煎服，日一剂，早晚温服；代煎 7 袋");
            prescription.setRemark("复诊请带近期睡眠记录");
            prescription = prescriptionRepository.save(prescription);
            for (int i = 0; i < herbs.size(); i++) {
                HerbSeed herb = herbs.get(i);
                PrescriptionItemEntity item = new PrescriptionItemEntity();
                item.setPrescriptionId(prescription.getId());
                item.setHerb(herb.herb());
                item.setWeight(herb.weight());
                item.setSpecial(herb.special());
                item.setSort(i);
                prescriptionItemRepository.save(item);
            }
            support.backdateRow("prescriptions", prescription.getId(), support.at(-1, 10, 30)); // 回写昨日开方时间线
        }
        // 膏方（炼蜜收膏）：演示膏方领取流转，已有膏方即跳过
        if (customersHasPaste(customerId)) {
            return;
        }
        List<HerbSeed> pasteHerbs = List.of(
                new HerbSeed("熟地黄", 60, null),
                new HerbSeed("党参", 30, null),
                new HerbSeed("白芍", 30, null),
                new HerbSeed("茯苓", 30, null),
                new HerbSeed("陈皮", 30, null),
                new HerbSeed("砂仁", 10, "后下"),
                new HerbSeed("阿胶", 20, "烊化"),
                new HerbSeed("龟板胶", 20, "烊化"),
                new HerbSeed("鹿角胶", 10, "烊化")
        );
        PrescriptionEntity paste = new PrescriptionEntity();
        paste.setCustomerId(customerId);
        paste.setStaffId(staffId);
        paste.setDoses(30);
        paste.setPrescriptionType(1);
        paste.setPasteStatus(2); // 三日可取，留给演示走「已取」流转
        paste.setDecoctionStatus(0); // 膏方按料计，不走代煎（列 NOT NULL 需显式置 0）
        paste.setDispenseStatus(0); // 列 NOT NULL 需显式置 0（待发药）
        paste.setCraft("炼蜜");
        paste.setUsage("温水冲服，日 2 次，每次 15g；一料服约 30 日");
        paste.setRemark("空腹服用，忌生冷；服期间停用峻补之品");
        paste = prescriptionRepository.save(paste);
        for (int i = 0; i < pasteHerbs.size(); i++) {
            HerbSeed herb = pasteHerbs.get(i);
            PrescriptionItemEntity item = new PrescriptionItemEntity();
            item.setPrescriptionId(paste.getId());
            item.setHerb(herb.herb());
            item.setWeight(herb.weight());
            item.setSpecial(herb.special());
            item.setSort(i);
            prescriptionItemRepository.save(item);
        }
        support.backdateRow("prescriptions", paste.getId(), support.at(-3, 10, 0)); // 回写三日制膏时间线
        log.info("种子·处方：检查完成");
    }

    /** 药房发药演示：最早一张处方置「已发药」（今日 11:00，操作人=开方医师），其余留给药房工作台走流转 */
    public void seedDispense() {
        if (dispenseLogRepository.count() > 0) {
            return;
        }
        PrescriptionEntity first = prescriptionRepository.findAll(Sort.by(Sort.Direction.ASC, "id"))
                .stream().findFirst().orElse(null);
        if (first == null) {
            return;
        }
        first.setDispenseStatus(1);
        first.setDispenseTime(support.at(0, 11, 0));
        first.setDispenseBy(first.getStaffId());
        prescriptionRepository.save(first);
        DispenseLogEntity entry = new DispenseLogEntity();
        entry.setPrescriptionId(first.getId());
        entry.setAction(1);
        entry.setStaffId(first.getStaffId());
        dispenseLogRepository.save(entry);
        support.backdateRow("dispense_logs", entry.getId(), support.at(0, 11, 0));
        log.info("种子·药房发药：检查完成");
    }

    /** 该顾客是否已有膏方（种子幂等键） */
    private boolean customersHasPaste(Long customerId) {
        return prescriptionRepository.findAll().stream()
                .anyMatch(p -> customerId.equals(p.getCustomerId())
                        && Integer.valueOf(1).equals(p.getPrescriptionType()));
    }

    /** 该顾客在 offset 天当日是否已有处方（按自然日判重） */
    private boolean prescriptionOnDayExists(Long customerId, int dayOffset) {
        return prescriptionRepository.findAll().stream()
                .anyMatch(p -> customerId.equals(p.getCustomerId())
                        && p.getCreateTime() != null
                        && DateUtil.isSomeDay(p.getCreateTime(), support.at(dayOffset, 12, 0)));
    }
}
