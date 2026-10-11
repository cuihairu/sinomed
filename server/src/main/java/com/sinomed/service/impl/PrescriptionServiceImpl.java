package com.sinomed.service.impl;

import com.sinomed.entity.CustomerEntity;
import com.sinomed.entity.DispenseLogEntity;
import com.sinomed.entity.PrescriptionEntity;
import com.sinomed.entity.PrescriptionItemEntity;
import com.sinomed.repository.CustomerRepository;
import com.sinomed.repository.DispenseLogRepository;
import com.sinomed.repository.PrescriptionItemRepository;
import com.sinomed.repository.PrescriptionRepository;
import com.sinomed.service.PrescriptionService;
import com.sinomed.vo.PrescriptionItemView;
import com.sinomed.vo.PrescriptionView;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class PrescriptionServiceImpl implements PrescriptionService {

    private final PrescriptionRepository prescriptionRepository;
    private final PrescriptionItemRepository prescriptionItemRepository;
    private final DispenseLogRepository dispenseLogRepository;
    private final CustomerRepository customerRepository;

    /**
     * 开方：顾客必填、至少 1 味药、剂数 ≥ 1、剂量 > 0；创建/更新时间由审计维护。
     * 代煎：view.decoction=true 时袋数=剂数、落「待煎」，否则「无需代煎」。
     * 膏方：prescriptionType=1 时落「待制作」并记收膏方式，否则「非膏方」。
     */
    @Override
    @Transactional
    public PrescriptionEntity save(PrescriptionView view) {
        if (view.getCustomerId() == null) {
            throw new IllegalArgumentException("顾客id不能为空");
        }
        List<PrescriptionItemView> herbs = view.getHerbs();
        if (herbs == null || herbs.isEmpty()) {
            throw new IllegalArgumentException("处方至少要有 1 味药");
        }
        int doses = view.getDoses() == null ? 7 : view.getDoses();
        if (doses < 1) {
            throw new IllegalArgumentException("剂数必须 ≥ 1");
        }
        boolean decoction = Boolean.TRUE.equals(view.getDecoction());
        boolean paste = Integer.valueOf(1).equals(view.getPrescriptionType());

        PrescriptionEntity prescription = new PrescriptionEntity();
        prescription.setTreatId(view.getTreatId());
        prescription.setCustomerId(view.getCustomerId());
        prescription.setStaffId(view.getStaffId());
        prescription.setDoses(doses);
        prescription.setUsage(view.getUsage());
        prescription.setRemark(view.getRemark());
        prescription.setDecoctionStatus(decoction ? 1 : 0);
        prescription.setDecoctionBags(decoction ? doses : null);
        prescription.setPrescriptionType(paste ? 1 : 0);
        prescription.setPasteStatus(paste ? 1 : 0);
        prescription.setCraft(paste ? view.getCraft() : null);
        prescription.setDispenseStatus(0);
        prescription = prescriptionRepository.save(prescription);

        for (int i = 0; i < herbs.size(); i++) {
            PrescriptionItemView herb = herbs.get(i);
            if (herb.getHerb() == null || herb.getHerb().isBlank()) {
                throw new IllegalArgumentException("第 " + (i + 1) + " 味药名不能为空");
            }
            if (herb.getWeight() == null || herb.getWeight() <= 0) {
                throw new IllegalArgumentException("药材「" + herb.getHerb() + "」剂量必须大于 0");
            }
            PrescriptionItemEntity item = new PrescriptionItemEntity();
            item.setPrescriptionId(prescription.getId());
            item.setHerb(herb.getHerb().trim());
            item.setWeight(herb.getWeight());
            item.setSpecial(herb.getSpecial());
            item.setSort(i);
            prescriptionItemRepository.save(item);
        }
        return prescription;
    }

    /**
     * 处方详情（含按 sort 排好的药味）
     */
    @Override
    public PrescriptionView findById(Long id) {
        PrescriptionEntity prescription = prescriptionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("处方不存在：" + id));
        PrescriptionView view = PrescriptionView.FromPrescriptionEntity(prescription);
        view.setHerbs(prescriptionItemRepository.findByPrescriptionIdOrderBySortAsc(id).stream()
                .map(PrescriptionItemView::FromItemEntity).toList());
        return view;
    }

    /**
     * 处方分页；customerId 为空查全部
     */
    @Override
    public Page<PrescriptionEntity> findPage(Long customerId, Pageable pageable) {
        if (customerId == null) {
            return prescriptionRepository.findAll(pageable);
        }
        return prescriptionRepository.findByCustomerId(customerId, pageable);
    }

    /**
     * 删除处方（连同药味，一个事务）
     */
    @Override
    @Transactional
    public void deleteById(Long id) {
        prescriptionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("处方不存在：" + id));
        prescriptionItemRepository.deleteByPrescriptionId(id);
        prescriptionRepository.deleteById(id);
    }

    /**
     * 代煎流转：只允许 待煎(1)→可取(2)→已取(3) 顺序推进；
     * 未选代煎(0)不可流转，加急/回退不做（演示口径）。
     */
    @Override
    @Transactional
    public PrescriptionEntity setDecoctionStatus(Long id, Integer status) {
        if (status == null || status != 2 && status != 3) {
            throw new IllegalArgumentException("代煎状态无效：只允许 2 可取 / 3 已取");
        }
        PrescriptionEntity prescription = prescriptionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("处方不存在：" + id));
        Integer current = prescription.getDecoctionStatus();
        if (current == null || current == 0) {
            throw new IllegalArgumentException("该方未选代煎，无需领取流转");
        }
        int expected = status == 2 ? 1 : 2;
        if (current != expected) {
            throw new IllegalArgumentException("代煎状态流转无效：只能 待煎→可取→已取，当前为「" + decoctionText(current) + "」");
        }
        prescription.setDecoctionStatus(status);
        if (status == 3) {
            markAutoDispensed(prescription, "代煎领取自动发药");
        }
        log.info("代煎流转：处方 {} → {}", id, decoctionText(status));
        return prescriptionRepository.save(prescription);
    }

    /**
     * 膏方领取流转：只允许 待制作(1)→可取(2)→已取(3) 顺序推进；
     * 非膏方(0)不可流转，回退/跳跃不做（演示口径，加急/取药窗口同代煎）。
     */
    @Override
    @Transactional
    public PrescriptionEntity setPasteStatus(Long id, Integer status) {
        if (status == null || status != 2 && status != 3) {
            throw new IllegalArgumentException("膏方状态无效：只允许 2 可取 / 3 已取");
        }
        PrescriptionEntity prescription = prescriptionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("处方不存在：" + id));
        Integer current = prescription.getPasteStatus();
        if (current == null || current == 0) {
            throw new IllegalArgumentException("该方不是膏方，无需领取流转");
        }
        int expected = status == 2 ? 1 : 2;
        if (current != expected) {
            throw new IllegalArgumentException("膏方状态流转无效：只能 待制作→可取→已取，当前为「" + pasteText(current) + "」");
        }
        prescription.setPasteStatus(status);
        if (status == 3) {
            markAutoDispensed(prescription, "膏方领取自动发药");
        }
        log.info("膏方流转：处方 {} → {}", id, pasteText(status));
        return prescriptionRepository.save(prescription);
    }

    /**
     * 药房发药：待发药(0)→已发药(1) 单向，落发药流水。
     * 操作人 staffId 可空（自动带出的路径不传）；note ≤100 字可空。
     */
    @Override
    @Transactional
    public PrescriptionEntity dispense(Long id, Long staffId, String note) {
        PrescriptionEntity prescription = prescriptionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("处方不存在：" + id));
        if (prescription.getDispenseStatus() != null && prescription.getDispenseStatus() == 1) {
            throw new IllegalArgumentException("处方已发药：" + id);
        }
        if (note != null && note.trim().length() > 100) {
            throw new IllegalArgumentException("备注限 100 字内");
        }
        prescription.setDispenseStatus(1);
        prescription.setDispenseTime(new Date());
        prescription.setDispenseBy(staffId);
        DispenseLogEntity entry = new DispenseLogEntity();
        entry.setPrescriptionId(id);
        entry.setAction(1);
        entry.setStaffId(staffId);
        entry.setNote(note == null ? null : note.trim());
        dispenseLogRepository.save(entry);
        PrescriptionEntity saved = prescriptionRepository.save(prescription);
        log.info("发药：处方 {}", id);
        return saved;
    }

    /**
     * 药房工作台分页：dispenseStatus 可空（0 待发药 / 1 已发药）；
     * customerKeyword 按顾客姓名/手机模糊，可空；id 倒序。
     */
    @Override
    public Page<PrescriptionEntity> findDispensePage(Integer dispenseStatus, String customerKeyword,
                                                      Pageable pageable) {
        List<Long> customerIds = (customerKeyword != null && !customerKeyword.isBlank())
                ? customerRepository.findAll((root, query, cb) -> cb.or(
                                cb.like(root.get("name"), "%" + customerKeyword.trim() + "%"),
                                cb.like(root.get("phone"), "%" + customerKeyword.trim() + "%")))
                        .stream().map(CustomerEntity::getId).toList()
                : null;
        boolean hasKeyword = customerIds != null;
        if (hasKeyword && customerIds.isEmpty()) {
            return Page.empty(pageable);
        }
        Specification<PrescriptionEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (dispenseStatus != null) {
                predicates.add(cb.equal(root.get("dispenseStatus"), dispenseStatus));
            }
            if (hasKeyword) {
                predicates.add(root.get("customerId").in(customerIds));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return prescriptionRepository.findAll(spec, pageable);
    }

    /**
     * 代煎/膏方「已取」自动带出发药：只补一次，流水 note 写明来源，操作人留空。
     */
    private void markAutoDispensed(PrescriptionEntity prescription, String note) {
        if (prescription.getDispenseStatus() != null && prescription.getDispenseStatus() == 1) {
            return;
        }
        prescription.setDispenseStatus(1);
        prescription.setDispenseTime(new Date());
        prescription.setDispenseBy(null);
        DispenseLogEntity entry = new DispenseLogEntity();
        entry.setPrescriptionId(prescription.getId());
        entry.setAction(1);
        entry.setNote(note);
        dispenseLogRepository.save(entry);
    }

    private static String decoctionText(int status) {
        return switch (status) {
            case 0 -> "无需代煎";
            case 1 -> "待煎";
            case 2 -> "可取";
            default -> "已取";
        };
    }

    private static String pasteText(int status) {
        return switch (status) {
            case 0 -> "非膏方";
            case 1 -> "待制作";
            case 2 -> "可取";
            default -> "已取";
        };
    }
}
