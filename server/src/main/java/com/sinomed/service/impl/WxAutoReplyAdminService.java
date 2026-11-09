package com.sinomed.service.impl;

import com.sinomed.entity.WxAutoReplyEntity;
import com.sinomed.repository.WxAccountRepository;
import com.sinomed.repository.WxAutoReplyRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * 公众号自动回复规则管理端 CRUD（设计稿第二期「管理端规则编辑」）：
 * 校验枚举值、keyword 型必填关键词、subscribe/fallback 同账号同时最多一条生效。
 * 与流水查询同口径：不依赖模块开关（模块关着也能编辑规则，先建后激活）。
 */
@Component
public class WxAutoReplyAdminService {

    private final WxAccountRepository accountRepository;
    private final WxAutoReplyRepository autoReplyRepository;

    /** 规则类型与匹配方式合法值（设计稿 §4.4：subscribe/keyword/fallback × exact/contains） */
    private static final Set<String> RULE_TYPES = Set.of("subscribe", "keyword", "fallback");
    private static final Set<String> MATCH_TYPES = Set.of("exact", "contains");

    public WxAutoReplyAdminService(WxAccountRepository accountRepository,
                                   WxAutoReplyRepository autoReplyRepository) {
        this.accountRepository = accountRepository;
        this.autoReplyRepository = autoReplyRepository;
    }

    /**
     * 管理端规则保存（设计稿第二期「管理端规则编辑」，创建与更新共用）：校验枚举值、
     * keyword 型必填关键词、非 keyword 型强制清空关键词（不污染匹配）、matchType/replyType 缺省、
     * subscribe/fallback 同账号同时最多一条生效（实体注释口径的服务层约束，不加 DB 唯一——避免草稿态冲突）。
     * 与 pageLogs 同口径：不依赖模块开关（模块关着也能编辑规则，先建后激活）。
     */
    public WxAutoReplyEntity saveAutoReply(WxAutoReplyEntity incoming) {
        if (incoming.getAccountId() == null || accountRepository.findById(incoming.getAccountId()).isEmpty()) {
            throw new IllegalArgumentException("公众号账号不存在");
        }
        String ruleType = incoming.getRuleType() == null ? "" : incoming.getRuleType().trim();
        if (!RULE_TYPES.contains(ruleType)) {
            throw new IllegalArgumentException("ruleType 必须是 subscribe/keyword/fallback");
        }
        String matchType = isBlank(incoming.getMatchType()) ? "exact" : incoming.getMatchType().trim();
        if (!MATCH_TYPES.contains(matchType)) {
            throw new IllegalArgumentException("matchType 必须是 exact/contains");
        }
        String replyType = isBlank(incoming.getReplyType()) ? "text" : incoming.getReplyType().trim();
        if (!"text".equals(replyType)) {
            throw new IllegalArgumentException("第一期仅支持 text 回复（素材类第二期）");
        }
        if (isBlank(incoming.getContent())) {
            throw new IllegalArgumentException("回复内容不能为空");
        }
        String keyword = null;
        if ("keyword".equals(ruleType)) {
            if (isBlank(incoming.getKeyword())) {
                throw new IllegalArgumentException("keyword 规则必须填写关键词");
            }
            keyword = incoming.getKeyword().trim();
        }

        WxAutoReplyEntity entity;
        if (incoming.getId() == null) {
            entity = new WxAutoReplyEntity();
        } else {
            entity = autoReplyRepository.findById(incoming.getId())
                    .orElseThrow(() -> new IllegalArgumentException("规则不存在"));
        }
        entity.setAccountId(incoming.getAccountId());
        entity.setRuleType(ruleType);
        entity.setKeyword(keyword);
        entity.setMatchType(matchType);
        entity.setReplyType(replyType);
        entity.setContent(incoming.getContent().trim());
        entity.setPriority(incoming.getPriority() == null ? 0 : incoming.getPriority());
        entity.setEnabled(incoming.getEnabled() == null ? 1 : incoming.getEnabled());

        if (entity.getEnabled() == 1 && ("subscribe".equals(ruleType) || "fallback".equals(ruleType))) {
            String typeLabel = "subscribe".equals(ruleType) ? "欢迎语" : "兜底";
            autoReplyRepository.findByAccountIdAndRuleTypeAndEnabledOrderByPriorityAsc(
                            entity.getAccountId(), ruleType, 1)
                    .stream().filter(other -> !other.getId().equals(entity.getId()))
                    .findAny()
                    .ifPresent(other -> {
                        throw new IllegalArgumentException("该账号已有一条启用的" + typeLabel + "规则，请先停用旧规则");
                    });
        }
        return autoReplyRepository.save(entity);
    }

    /** 管理端规则分页（AND 过滤），默认 priority ASC、id ASC（小者先试，同优先级按创建序） */
    public Page<WxAutoReplyEntity> pageAutoReplies(Long accountId, String ruleType, Integer enabled,
                                                   Pageable pageable) {
        if (accountId == null && isBlank(ruleType) && enabled == null) {
            return autoReplyRepository.findAll(pageable);
        }
        Specification<WxAutoReplyEntity> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new LinkedList<>();
            if (accountId != null) {
                predicates.add(criteriaBuilder.equal(root.get("accountId").as(Long.class), accountId));
            }
            if (!isBlank(ruleType)) {
                predicates.add(criteriaBuilder.equal(root.get("ruleType").as(String.class), ruleType.trim()));
            }
            if (enabled != null) {
                predicates.add(criteriaBuilder.equal(root.get("enabled").as(Integer.class), enabled));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
        return autoReplyRepository.findAll(spec, pageable);
    }

    public void deleteAutoReply(Long id) {
        autoReplyRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("规则不存在"));
        autoReplyRepository.deleteById(id);
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
