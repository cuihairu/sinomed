package com.sinomed.repository;

import com.sinomed.entity.WxAutoReplyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface WxAutoReplyRepository extends JpaRepository<WxAutoReplyEntity, Long>,
        JpaSpecificationExecutor<WxAutoReplyEntity> {

    /** 排序交给 priority ASC（小者先试）；仅取启用规则 */
    List<WxAutoReplyEntity> findByAccountIdAndRuleTypeAndEnabledOrderByPriorityAsc(Long accountId, String ruleType, Integer enabled);

    List<WxAutoReplyEntity> findByAccountIdAndEnabledOrderByPriorityAsc(Long accountId, Integer enabled);
}
