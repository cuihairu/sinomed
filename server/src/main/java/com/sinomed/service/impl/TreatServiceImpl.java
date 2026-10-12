package com.sinomed.service.impl;

import com.sinomed.entity.CustomerEntity;
import com.sinomed.entity.TreatEntity;
import com.sinomed.repository.TreatRepository;
import com.sinomed.service.TreatService;
import com.sinomed.util.DateUtil;
import com.sinomed.vo.CustomerVisitsVo;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TreatServiceImpl implements TreatService {

    /** 按顾客批量聚合就诊次数：treats 表按 cid 计数，无记录的顾客不在返回里 */
    @Override
    public List<CustomerVisitsVo> countVisitsByCustomerIds(List<Long> customerIds) {
        if (customerIds == null || customerIds.isEmpty()) {
            return List.of();
        }
        return treatRepository.countVisitsByCustomerIds(customerIds).stream()
                .map(v -> CustomerVisitsVo.builder()
                        .customerId(v.getCustomerId())
                        .visits(v.getVisits())
                        .build())
                .collect(Collectors.toList());
    }

    private final TreatRepository treatRepository;
    public TreatServiceImpl(TreatRepository treatRepository){
        this.treatRepository = treatRepository;
    }
    /**
     * @param id
     */
    @Override
    public void deleteById(Long id) {
        treatRepository.deleteById(id);
    }

    /**
     * @param id
     * @return
     */
    @Override
    @Cacheable("TreatEntity")
    public Optional<TreatEntity> findById(Long id) {
        return treatRepository.findById(id);
    }

    @Override
    @Transactional
    @CacheEvict(value = "TreatEntity", key = "#view.id")
    public TreatEntity update(com.sinomed.vo.TreatView view) {
        if (view.getId() == null) {
            throw new IllegalArgumentException("接诊单id不能为空");
        }
        TreatEntity treat = view.ToTreatEntity();
        validAcupuncture(treat);
        treatRepository.findById(view.getId())
                .orElseThrow(() -> new IllegalArgumentException("接诊单不存在：" + view.getId()));
        return treatRepository.save(treat);
    }

    @Override
    public List<TreatEntity> findByCustomerId(Long customerId) {
        TreatEntity treatEntity = new TreatEntity();
        treatEntity.setCustomerId(customerId);
        return treatRepository.findAll(Example.of(treatEntity));
    }

    /**
     * @param
     * @return
     */
    @Override
    public Page<TreatEntity> findByPage(TreatEntity treatEntity, Pageable pageable) {
        return treatRepository.findAll(Example.of(treatEntity),pageable);
    }

    @Override
    public Page<TreatEntity> findAllByPage(List<Long> customerIds, String startTime, String endTime, Pageable pageable){
        Specification<TreatEntity> specification = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new LinkedList<>();
            // createTime 落库为毫秒整数，需按 Date 比较；字符串词法比较在 SQLite 永不命中
            Date start = DateUtil.parseParam(startTime);
            if (start != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.<Date>get("createTime"), start));
            }
            Date end = DateUtil.parseParam(endTime);
            if (end != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.<Date>get("createTime"), end));
            }

            if (customerIds != null && !customerIds.isEmpty()){
                CriteriaBuilder.In<Long> inClause = criteriaBuilder.in(root.get("customerId"));
                for (Long cid : customerIds){
                    inClause.value(cid);
                }
                predicates.add(inClause);
            }
            Predicate[] array = new Predicate[predicates.size()];
            return criteriaBuilder.and(predicates.toArray(array));
        };


        return treatRepository.findAll(specification,pageable);
    }

    /**
     * 保存治疗
     *
     * @param treatEntity
     */
    @Override
    public TreatEntity save(TreatEntity treatEntity) {
        validAcupuncture(treatEntity);
        // 时间不更新
        if (treatEntity.getId() != null){
            Optional<TreatEntity> byId = treatRepository.findById(treatEntity.getId());
            if (!byId.isEmpty()){
                treatEntity.setCreateTime(byId.get().getCreateTime());
                treatEntity.setUpdateTime(byId.get().getUpdateTime());
            }
        }
        return treatRepository.save(treatEntity);
    }

    /** 针灸处方字段校验：针法/手法 ≤30 字、灸法 ≤60 字、留针 1-240 分钟（可空）、疗程 ≤100 字；空白文本按空处理 */
    private void validAcupuncture(TreatEntity treat) {
        if (treat == null) {
            return;
        }
        treat.setAcuMethod(trimOrNull(treat.getAcuMethod()));
        if (treat.getAcuMethod() != null && treat.getAcuMethod().length() > 30) {
            throw new IllegalArgumentException("针法限 30 字内");
        }
        if (treat.getRetentionMinutes() != null
                && (treat.getRetentionMinutes() < 1 || treat.getRetentionMinutes() > 240)) {
            throw new IllegalArgumentException("留针时长无效：" + treat.getRetentionMinutes() + "（应为 1-240 分钟）");
        }
        treat.setManipulation(trimOrNull(treat.getManipulation()));
        if (treat.getManipulation() != null && treat.getManipulation().length() > 30) {
            throw new IllegalArgumentException("手法限 30 字内");
        }
        treat.setMoxaMethod(trimOrNull(treat.getMoxaMethod()));
        if (treat.getMoxaMethod() != null && treat.getMoxaMethod().length() > 60) {
            throw new IllegalArgumentException("灸法限 60 字内");
        }
        treat.setAcuCourse(trimOrNull(treat.getAcuCourse()));
        if (treat.getAcuCourse() != null && treat.getAcuCourse().length() > 100) {
            throw new IllegalArgumentException("疗程限 100 字内");
        }
    }

    /** 去除首尾空白；全空白返回 null */
    private String trimOrNull(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * @return
     */
    @Override
    public List<TreatEntity> findAll() {
        return treatRepository.findAll();
    }
}
