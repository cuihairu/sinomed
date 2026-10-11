package com.sinomed.service;

import com.sinomed.entity.PrescriptionEntity;
import com.sinomed.vo.PrescriptionView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PrescriptionService {
    /**
     * 开方：落 prescriptions 主单 + prescription_items 药味（一个事务）
     */
    PrescriptionEntity save(PrescriptionView view);

    /**
     * 处方详情（含药味）
     */
    PrescriptionView findById(Long id);

    /**
     * 处方分页；customerId 为空查全部
     */
    Page<PrescriptionEntity> findPage(Long customerId, Pageable pageable);

    /**
     * 删除处方（连同药味）
     */
    void deleteById(Long id);

    /**
     * 代煎流转：待煎(1)→可取(2)→已取(3) 顺序推进，其余拒绝
     */
    PrescriptionEntity setDecoctionStatus(Long id, Integer status);

    /**
     * 膏方领取流转：待制作(1)→可取(2)→已取(3) 顺序推进，非膏方(0)与回退/跳跃拒绝
     */
    PrescriptionEntity setPasteStatus(Long id, Integer status);

    /**
     * 药房发药：待发药(0)→已发药(1) 单向，落发药流水（note ≤100 字可空）。
     * 操作人 staffId 可空（自动带出的路径不传）。
     */
    PrescriptionEntity dispense(Long id, Long staffId, String note);

    /**
     * 药房工作台分页：dispenseStatus 可空（0 待发药 / 1 已发药）；
     * customerKeyword 按顾客姓名/手机模糊，可空；id 倒序
     */
    Page<PrescriptionEntity> findDispensePage(Integer dispenseStatus, String customerKeyword, Pageable pageable);
}
