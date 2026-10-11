package com.sinomed.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.Date;

@Data
@Entity
@Table(name = "`orders`",indexes = {
        @Index(name = "idx_order_user_id",columnList = "user_id"),
        @Index(name = "idx_order_item_id",columnList = "item_id"),
        @Index(name = "idx_order_staff_id",columnList = "staff_id")
})
@EntityListeners(AuditingEntityListener.class)
public class OrderEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "user_id",nullable = false)
    private Long userId;

    @Column(name = "item_id",nullable = false)
    private Long itemId;

    @Column(name = "staff_id")
    private Long staffId;

    @Column(name = "status")
    private Integer status;

    @Column(name = "price")
    private Integer price;

    /** 整单备注（结算台琥珀备忘块，可空） */
    @Column(name = "remark", length = 200)
    private String remark;

    @CreatedDate
    @Column(name = "create_time")
    private Date createTime;

    @LastModifiedDate
    @Column(name = "update_time")
    private Date updateTime;

}
