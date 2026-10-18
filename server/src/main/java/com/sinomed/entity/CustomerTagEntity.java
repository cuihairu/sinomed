package com.sinomed.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.Date;

/**
 * 客群标签（美容业态 d，docs/design/salon-extension.md §2-d）：肤质/偏好等轻量标签，
 * 同顾客同标签唯一（人工去重由唯一性兜底）。过敏史不在此表——复用 customer_histories type 0；
 * 护理方案走 customer_histories type 2，不建并行档案。
 */
@Data
@Entity
@Table(name = "`customer_tags`", uniqueConstraints = {
        @UniqueConstraint(name = "uk_customer_tag_customer_tag", columnNames = {"customer_id", "tag"})
}, indexes = {
        @Index(name = "idx_customer_tag_customer", columnList = "customer_id")
})
@EntityListeners(AuditingEntityListener.class)
public class CustomerTagEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    /** 标签（≤20 字，如「肤质·油性」「偏好·力度大」） */
    @Column(name = "tag", nullable = false)
    private String tag;

    @CreatedDate
    @Column(name = "create_time")
    private Date createTime;

    @LastModifiedDate
    @Column(name = "update_time")
    private Date updateTime;
}
