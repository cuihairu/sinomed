package com.sinomed.service.impl;

import com.sinomed.entity.WxMessageLogEntity;
import com.sinomed.repository.WxMessageLogRepository;
import com.sinomed.util.DateUtil;
import com.sinomed.util.DbUniqueUtil;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.LinkedList;
import java.util.List;

/**
 * 公众号消息流水（设计稿第一期交付物 4）：入站/出站/拒绝三类落库，
 * 入站带 MsgId/事件 dedupe_key 供 DB 排重（并发双投递由唯一索引兜底）；
 * 管理端只读分页按账号/粉丝/方向/类型/结果/时间窗过滤（AND 语义）。
 */
@Component
public class WxMessageLogService {

    private final WxMessageLogRepository messageLogRepository;

    public WxMessageLogService(WxMessageLogRepository messageLogRepository) {
        this.messageLogRepository = messageLogRepository;
    }

    public void logReject(long accountId, String result, String msgType, String fromUser, String toUser) {
        WxMessageLogEntity log = new WxMessageLogEntity();
        log.setAccountId(accountId);
        log.setDirection("reject");
        log.setMsgType(msgType);
        log.setFromUser(fromUser);
        log.setToUser(toUser);
        log.setResult(result);
        messageLogRepository.save(log);
    }

    public void logMessage(long accountId, String direction, String msgType, String fromUser, String toUser,
                           String content, String msgId, String dedupeKey) {
        WxMessageLogEntity log = new WxMessageLogEntity();
        log.setAccountId(accountId);
        log.setDirection(direction);
        log.setMsgType(msgType);
        log.setFromUser(fromUser);
        log.setToUser(toUser);
        // 流水留痕截断：超长文本只存前 500 字（审计价值在「说了什么类别」，不是全文备份）
        log.setContent(content != null && content.length() > 500 ? content.substring(0, 500) : content);
        log.setMsgId(msgId != null ? parseLongOrNull(msgId) : null);
        log.setDedupeKey(dedupeKey);
        log.setResult("ok");
        try {
            messageLogRepository.saveAndFlush(log);
        } catch (DataAccessException e) {
            // 并发同 dedupe_key 双投递：唯一索引兜底排重——另一请求已落库，本请求作重放处理，
            // 不向上抛（否则 500 触发微信重试放大）。调用方在 existsByDedupeKey 后的窗口由这里补住。
            if (dedupeKey != null && isUniqueConstraint(e)) {
                logReject(accountId, "duplicate", msgType, fromUser, toUser);
            } else {
                throw e;
            }
        }
    }

    /** 唯一约束冲突判定：Hibernate 随方言/驱动把 SQLITE_CONSTRAINT_UNIQUE 包成 DataAccessException 子类，逐层找约束痕迹 */
    private boolean isUniqueConstraint(DataAccessException e) {
        return DbUniqueUtil.isUniqueViolation(e);
    }

    private static Long parseLongOrNull(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 管理端只读流水列表（设计稿第一期交付物 4）：按账号/粉丝/方向/类型/结果/时间窗过滤（AND 语义），
     * 排序由 Pageable 携带（默认 createTime、id 倒序=最新在前）。不依赖模块开关——
     * disabled 期间的 reject 审计也要可查（不静默）。不做对话式 UI。
     */
    public Page<WxMessageLogEntity> pageLogs(Long accountId, String fromUser, String direction, String msgType,
                                             String result, String startTime, String endTime, Pageable pageable) {
        if (accountId == null && isBlank(fromUser) && isBlank(direction) && isBlank(msgType)
                && isBlank(result) && isBlank(startTime) && isBlank(endTime)) {
            return messageLogRepository.findAll(pageable);
        }
        Specification<WxMessageLogEntity> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new LinkedList<>();
            if (accountId != null) {
                predicates.add(criteriaBuilder.equal(root.get("accountId").as(Long.class), accountId));
            }
            if (!isBlank(fromUser)) {
                predicates.add(criteriaBuilder.equal(root.get("fromUser").as(String.class), fromUser.trim()));
            }
            if (!isBlank(direction)) {
                predicates.add(criteriaBuilder.equal(root.get("direction").as(String.class), direction.trim()));
            }
            if (!isBlank(msgType)) {
                predicates.add(criteriaBuilder.equal(root.get("msgType").as(String.class), msgType.trim()));
            }
            if (!isBlank(result)) {
                predicates.add(criteriaBuilder.equal(root.get("result").as(String.class), result.trim()));
            }
            // createTime 落库为毫秒整数，需按 Date 比较；字符串词法比较在 SQLite 永不命中
            Date start = DateUtil.parseParam(startTime);
            if (start != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.<Date>get("createTime"), start));
            }
            Date end = DateUtil.parseParam(endTime);
            if (end != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.<Date>get("createTime"), end));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
        return messageLogRepository.findAll(spec, pageable);
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
