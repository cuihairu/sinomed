package com.sinomed.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.Date;

@Data
@Entity
@Table(name = "`recharges`",indexes = {
        @Index(name = "idx_recharge_user_id",columnList = "user_id")
})
@EntityListeners(AuditingEntityListener.class)
public class RechargeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "user_id",nullable = false)
    private Long userId;

    @Column(name = "money")
    private Integer money;

    /** 备注（≤100 字，可空）：储值退款补回的流水写「退单 #结算id」，日对账据此不计入充值 */
    @Column(name = "note")
    private String note;

    @CreatedDate
    @Column(name = "create_time")
    private Date createTime;

    @LastModifiedDate
    @Column(name = "update_time")
    private Date updateTime;
}
