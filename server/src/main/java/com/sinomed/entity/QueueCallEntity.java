package com.sinomed.entity;


import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.Date;

/**
 * 叫号记录：前台手动叫号（CallController POST）与预约到店自动叫号（接待 0→1 联动）两条来源，
 * 平板按游标 since 拉取（见 docs/design/tablet.md）。
 */
@Data
@Entity
@Table(name = "`queue_calls`", indexes = {
        @Index(name = "idx_queue_call_screen_id", columnList = "screen_id"),
        @Index(name = "idx_queue_call_called_at", columnList = "called_at")
})
@EntityListeners(AuditingEntityListener.class)
public class QueueCallEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    /** 定向屏 id（业务外键；为空 = 全部屏） */
    @Column(name = "screen_id")
    private Long screenId;

    /** 号码（如 08） */
    @Column(name = "number", nullable = false)
    private String number;

    /** 诊室名（如 第二诊室） */
    @Column(name = "room", nullable = false)
    private String room;

    /** 脱敏姓名（如 张*，隐私默认） */
    @Column(name = "patient_masked")
    private String patientMasked;

    /** 0 待叫、1 已叫 */
    @Column(name = "status", nullable = false)
    private Integer status;

    @Column(name = "called_at")
    private Date calledAt;

    @CreatedDate
    @Column(name = "create_time")
    private Date createTime;

    @LastModifiedDate
    @Column(name = "update_time")
    private Date updateTime;
}
