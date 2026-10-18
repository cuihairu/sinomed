package com.sinomed.repository;

import com.sinomed.entity.WxAutoReplyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WxAutoReplyRepository extends JpaRepository<WxAutoReplyEntity, Long> {

    /** 排序交给 priority ASC（小者先试）；仅取启用规则 */
    List<WxAutoReplyEntity> findByAccountIdAndRuleTypeAndEnabledOrderByPriorityAsc(Long accountId, String ruleType, Integer enabled);

    List<WxAutoReplyEntity> findByAccountIdAndEnabledOrderByPriorityAsc(Long accountId, Integer enabled);
}
