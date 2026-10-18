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
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.Date;

/**
 * 顾客病史记录（过敏史/既往史）：逐条 append，新记录在前。
 * type 0 过敏 / 1 既往 / 2 护理方案（美容业态 d 档案扩展，不复用外治处方模板）；
 * 内容为自由文本（如「青霉素过敏」「高血压 8 年，规律服药」「每周一次补水护理，共 8 次」）。
 */
@Data
@Entity
@Table(name = "`customer_histories`", indexes = {
        @Index(name = "idx_customer_history_customer", columnList = "customer_id")
})
@EntityListeners(AuditingEntityListener.class)
public class CustomerHistoryEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    /** 0 过敏史 / 1 既往史 / 2 护理方案 */
    @Column(name = "type", nullable = false)
    private Integer type;

    /** 病史内容（自由文本，≤200 字） */
    @Column(name = "content", nullable = false)
    private String content;

    @CreatedDate
    @Column(name = "create_time")
    private Date createTime;

    @LastModifiedDate
    @Column(name = "update_time")
    private Date updateTime;
}
