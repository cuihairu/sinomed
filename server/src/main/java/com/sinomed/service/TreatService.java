package com.sinomed.service;

import com.sinomed.entity.TreatEntity;
import com.sinomed.vo.CustomerVisitsVo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface TreatService {
    /**
     * 根据治疗的id查询治疗
     * */
    Optional<TreatEntity> findById(Long id);
    List<TreatEntity>findByCustomerId(Long customerId);

    Page<TreatEntity> findByPage(TreatEntity treatEntity, Pageable pageable);
    Page<TreatEntity> findAllByPage(List<Long> customerIds, String startTime, String endTime, Pageable pageable);
    /**
     * 保存治疗
     * */
    TreatEntity save(TreatEntity treatEntity);

    /**
     * 按 id 全量更新接诊单：id 必填且须存在，字段以请求体为准；更新后驱逐 findById 缓存
     */
    TreatEntity update(com.sinomed.vo.TreatView view);
    /**
     * 根据治疗的id删除治疗
     * */
    void deleteById(Long id);
    /**
     * 根据治疗的id查询治疗
     * */
    List<TreatEntity> findAll();

    /**
     * 按顾客批量查就诊次数（排班页「首诊/复诊」角标）；无诊疗记录的顾客不在返回里
     * */
    List<CustomerVisitsVo> countVisitsByCustomerIds(List<Long> customerIds);
}
