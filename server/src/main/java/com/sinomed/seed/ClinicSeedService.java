package com.sinomed.seed;

import com.sinomed.entity.HerbEntity;
import com.sinomed.entity.HerbStockLogEntity;
import com.sinomed.entity.ItemEntity;
import com.sinomed.entity.StaffEntity;
import com.sinomed.entity.StaffShiftEntity;
import com.sinomed.entity.TherapyTemplateEntity;
import com.sinomed.repository.HerbRepository;
import com.sinomed.repository.HerbStockLogRepository;
import com.sinomed.repository.ItemRepository;
import com.sinomed.repository.StaffRepository;
import com.sinomed.repository.StaffShiftRepository;
import com.sinomed.repository.TherapyTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 门店运营基础种子（LAY-002 拆分）：员工账号、卡项（服务项目）、员工班表、
 * 饮片库存台账、外治处方模板。逐表按自然键判重，已有数据跳过。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClinicSeedService {

    private final StaffRepository staffRepository;
    private final ItemRepository itemRepository;
    private final StaffShiftRepository shiftRepository;
    private final HerbRepository herbRepository;
    private final HerbStockLogRepository herbStockLogRepository;
    private final TherapyTemplateRepository therapyTemplateRepository;
    private final PasswordEncoder passwordEncoder;
    private final SeedSupport support;

    /** 员工：馆长 + 中医师 + 前台，密码统一为演示账号 123（bcrypt） */
    public void seedStaff() {
        record StaffSeed(String name, String account, int role, int gender, int age,
                         String phone, String address, int birthYear, int birthMonth, int birthDay) {}
        List<StaffSeed> seeds = List.of(
                new StaffSeed("顾景明", "gu", 0, 1, 45, "13800000001", "南京市秦淮区", 1981, 4, 18),
                new StaffSeed("沈知远", "shen", 99, 1, 38, "13800000002", "南京市秦淮区", 1988, 9, 3),
                new StaffSeed("苏文若", "su", 99, 0, 26, "13800000003", "南京市秦淮区", 2000, 2, 11)
        );
        for (StaffSeed s : seeds) {
            if (staffExists(s.account())) {
                continue;
            }
            StaffEntity staff = StaffEntity.getInstanceWithDefault(s.account());
            staff.setName(s.name());
            staff.setRole(s.role());
            staff.setGender(s.gender());
            staff.setAge(s.age());
            staff.setPhone(s.phone());
            staff.setAddress(s.address());
            staff.setBirthday(Date.from(LocalDate.of(s.birthYear(), s.birthMonth(), s.birthDay())
                    .atStartOfDay(ZoneId.systemDefault()).toInstant()));
            staff.setPassword(passwordEncoder.encode("123"));
            staffRepository.save(staff);
        }
        log.info("种子·员工：检查完成");
    }

    /** 卡项（服务项目）：单次体验与次卡/季卡组合 */
    public void seedItems() {
        record ItemSeed(String name, int price, String description) {}
        List<ItemSeed> seeds = List.of(
                new ItemSeed("中医体质辨识（单次）", 198,
                        "四诊合参体质辨识一次，含辨识报告与调理建议。"),
                new ItemSeed("经络推拿（10 次卡）", 680,
                        "经络推拿十次卡，有效期一年，可指定技师。"),
                new ItemSeed("艾灸温阳调理（5 次卡）", 480,
                        "艾灸温阳调理五次卡，含灸后温阳茶饮。"),
                new ItemSeed("四季膏方调理（季卡）", 1280,
                        "四季膏方调理季度服务，含辨证开方与两次复诊调整。")
        );
        int sort = 0;
        for (ItemSeed s : seeds) {
            if (itemExists(s.name())) {
                continue;
            }
            ItemEntity item = new ItemEntity();
            item.setName(s.name());
            item.setPrice(s.price());
            item.setDescription(s.description());
            // enabled/sort 为 NOT NULL 列（ItemView 默认 enabled=1/sort=0），种子直插实体需显式带上
            item.setEnabled(1);
            item.setSort(++sort);
            itemRepository.save(item);
        }
        log.info("种子·卡项：检查完成");
    }

    /** 员工班表：馆长/中医师/前台各一组周期班次（按周循环，同员工同星期唯一；考勤仍以打卡为准） */
    public void seedShifts() {
        if (shiftRepository.count() > 0) {
            return;
        }
        Long shen = support.staffByAccount("shen");
        Long su = support.staffByAccount("su");
        Long gu = support.staffByAccount("gu");
        record ShiftSeed(Long staffId, int weekday, String start, String end) {}
        List<ShiftSeed> seeds = new ArrayList<>();
        // 沈知远（中医师）：周一 ~ 周五 09:00-18:00
        for (int weekday = 1; weekday <= 5; weekday++) {
            seeds.add(new ShiftSeed(shen, weekday, "09:00", "18:00"));
        }
        // 苏文若（前台）：周一 ~ 周六 09:30-18:30
        for (int weekday = 1; weekday <= 6; weekday++) {
            seeds.add(new ShiftSeed(su, weekday, "09:30", "18:30"));
        }
        // 顾景明（馆长）：周三 ~ 周日 10:00-19:00
        for (int weekday = 3; weekday <= 7; weekday++) {
            seeds.add(new ShiftSeed(gu, weekday, "10:00", "19:00"));
        }
        for (ShiftSeed s : seeds) {
            if (s.staffId() == null) {
                continue;
            }
            StaffShiftEntity shift = new StaffShiftEntity();
            shift.setStaffId(s.staffId());
            shift.setWeekday(s.weekday());
            shift.setStart(s.start());
            shift.setEnd(s.end());
            StaffShiftEntity saved = shiftRepository.save(shift);
            support.backdateRow("staff_shifts", saved.getId(), support.at(-16, 9, 0));
        }
        log.info("种子·员工班表：检查完成");
    }

    /** 饮片库存：常用药材入库（带批次效期/供货方）+ 一笔出库演示；台账口径，不涉采购单据与结算 */
    public void seedHerbStock() {
        if (herbStockLogRepository.count() > 0) {
            return;
        }
        record StockSeed(String herb, int type, int quantity, Integer expiryInDays, String supplier, String note) {}
        List<StockSeed> seeds = List.of(
                new StockSeed("黄芪", 1, 5000, 120, "亳州药市", null),
                new StockSeed("当归", 1, 3000, 90, "亳州药市", null),
                new StockSeed("茯苓", 1, 4000, 200, "安徽亳州", null),
                new StockSeed("白术", 1, 2500, 25, "浙江磐安", "近期到期"),
                new StockSeed("甘草", 1, 6000, 300, "内蒙古", null),
                new StockSeed("甘草", 0, 800, null, null, "门诊领用"));
        for (StockSeed s : seeds) {
            HerbEntity herb = herbRepository.findByName(s.herb());
            if (herb == null) {
                continue;
            }
            HerbStockLogEntity entity = new HerbStockLogEntity();
            entity.setHerbId(herb.getId());
            entity.setType(s.type());
            entity.setQuantity(s.quantity());
            entity.setExpiry(s.expiryInDays() == null ? null : support.at(s.expiryInDays(), 12, 0));
            entity.setSupplier(s.supplier());
            entity.setNote(s.note());
            HerbStockLogEntity saved = herbStockLogRepository.save(entity);
            support.backdateRow("herb_stock_logs", saved.getId(), support.at(-15, 9, 0));
        }
        log.info("种子·饮片库存：检查完成");
    }

    /** 外治处方模板：耳穴压豆/穴位贴敷/艾灸/拔罐常用组合各一；模板只存建议值，接诊页套用后随单自由改 */
    public void seedTherapyTemplates() {
        if (therapyTemplateRepository.count() > 0) {
            return;
        }
        record TplSeed(String name, String method, String target, String course, String note) {}
        List<TplSeed> seeds = List.of(
                new TplSeed("失眠·耳穴压豆", "耳穴压豆", "神门、交感、皮质下、心", "每周 2 次 × 4 周，留豆 3 天一换", "每日睡前自行揉按 1 分钟，胶布过敏停用"),
                new TplSeed("咳喘·穴位贴敷", "穴位贴敷", "肺俞、定喘、膻中", "贴敷 4~6 小时，隔日一换 × 7 次", "皮肤发红属正常，灼热起疱即取下"),
                new TplSeed("肾阳虚·温灸关元", "艾灸", "关元、气海、命门", "每日 1 次，每次 20 分钟 × 2 周", "灸后避风保暖，口干舌燥减量"),
                new TplSeed("腰痛·留罐", "拔罐", "肾俞、大肠俞、委中", "每周 2 次 × 3 周，留罐 10 分钟", "罐斑紫暗属正常，皮肤破损不拔"));
        for (TplSeed s : seeds) {
            TherapyTemplateEntity entity = new TherapyTemplateEntity();
            entity.setName(s.name());
            entity.setMethod(s.method());
            entity.setTarget(s.target());
            entity.setCourse(s.course());
            entity.setNote(s.note());
            entity.setEnabled(1);
            TherapyTemplateEntity saved = therapyTemplateRepository.save(entity);
            support.backdateRow("therapy_templates", saved.getId(), support.at(-14, 10, 0));
        }
        log.info("种子·外治模板：检查完成");
    }

    private boolean staffExists(String account) {
        return staffRepository.findAll().stream().anyMatch(s -> account.equals(s.getAccount()));
    }

    private boolean itemExists(String name) {
        return itemRepository.findAll().stream().anyMatch(i -> name.equals(i.getName()));
    }
}
