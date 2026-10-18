package com.sinomed.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.DynamicInsert;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.Date;

/**
 * 公众号自动回复规则（wx_auto_replies，设计稿 §4.4）：subscribe 欢迎语 / keyword 关键词 /
 * fallback 兜底；第一期规则表手工维护（无 UI）。同账号同 rule_type=subscribe/fallback
 * 各最多一条生效（服务层约束，不加 DB 唯一约束——避免草稿态冲突）。
 */
@Data
@Entity
@DynamicInsert
@Table(name = "wx_auto_replies", indexes = {
        @Index(name = "idx_wx_reply_rule", columnList = "account_id, rule_type, enabled")
})
@EntityListeners(AuditingEntityListener.class)
public class WxAutoReplyEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    /** subscribe（欢迎语）/ keyword（关键词）/ fallback（兜底） */
    @Column(name = "rule_type", nullable = false)
    private String ruleType;

    /** 关键词（rule_type=keyword 时非空） */
    @Column(name = "keyword")
    private String keyword;

    /** exact 全等 / contains 包含 */
    @Column(name = "match_type", columnDefinition = "varchar(16) default 'exact'")
    private String matchType;

    /** 第一期仅 text */
    @Column(name = "reply_type", columnDefinition = "varchar(16) default 'text'")
    private String replyType;

    /** 文本内容；素材类存 media_id（第二期） */
    @Column(name = "content", nullable = false)
    private String content;

    /** 匹配优先级，小者先试 */
    @Column(name = "priority", nullable = false, columnDefinition = "integer default 0")
    private Integer priority;

    /** 1 启用 / 0 停用 */
    @Column(name = "enabled", nullable = false, columnDefinition = "integer default 1")
    private Integer enabled;

    @CreatedDate
    @Column(name = "create_time")
    private Date createTime;

    @LastModifiedDate
    @Column(name = "update_time")
    private Date updateTime;
}
