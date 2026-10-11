package com.sinomed.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.Date;

/**
 * 预约：顾客→卡项→时段一条待接待预约。
 * 状态：0 待到店、1 已接待（转接诊后置）、8 过号、9 已取消。
 */
@Data
@Entity
@Table(name = "`appointments`",indexes = {
        @Index(name = "idx_appointment_customer_id",columnList = "customer_id"),
        @Index(name = "idx_appointment_item_id",columnList = "item_id"),
        @Index(name = "idx_appointment_start_time",columnList = "start_time")
})
@EntityListeners(AuditingEntityListener.class)
public class AppointmentEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "customer_id",nullable = false)
    private Long customerId;

    @Column(name = "item_id")
    private Long itemId;

    @Column(name = "staff_id")
    private Long staffId;

    /** 预约时段开始时刻 */
    @Column(name = "start_time",nullable = false)
    private Date startTime;

    /** 时长（分钟） */
    @Column(name = "duration")
    private Integer duration;

    /** 0 待到店、1 已接待、8 过号、9 已取消 */
    @Column(name = "status")
    private Integer status;

    @Column(name = "remark")
    private String remark;

    @CreatedDate
    @Column(name = "create_time")
    private Date createTime;

    @LastModifiedDate
    @Column(name = "update_time")
    private Date updateTime;

}
