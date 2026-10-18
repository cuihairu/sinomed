package com.sinomed.repository;

import com.sinomed.entity.CommissionRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CommissionRuleRepository extends JpaRepository<CommissionRuleEntity,Long>{

    /** 记账匹配：项目 × 岗位（一岗一比例） */
    Optional<CommissionRuleEntity> findByItemIdAndTitle(Long itemId, String title);

    /** 规则列表按卡项过滤 */
    List<CommissionRuleEntity> findByItemIdOrderByIdDesc(Long itemId);
}
