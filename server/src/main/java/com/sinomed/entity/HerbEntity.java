package com.sinomed.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.Date;

/**
 * 药材字典：处方计价的比价依据。药材名为处方自由文本的规范名（唯一），
 * 价格为每克单价（分），计价时按「分/克 × 单剂克数」取整累加。
 */
@Data
@Entity
@Table(name = "`herbs`")
@EntityListeners(AuditingEntityListener.class)
public class HerbEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    /** 每克价格（分）；处方计价 = Σ round(price × 单剂克数) × 剂数 */
    @Column(name = "price", nullable = false)
    private Integer price;

    /** 库存下限（克，可空=不预警）；库存余额低于下限时余额接口标 warnStock */
    @Column(name = "stock_min")
    private Integer stockMin;

    /** 库存上限（克，可空=不预警）；库存余额高于上限时余额接口标 warnStock */
    @Column(name = "stock_max")
    private Integer stockMax;

    @CreatedDate
    @Column(name = "create_time")
    private Date createTime;

    @LastModifiedDate
    @Column(name = "update_time")
    private Date updateTime;
}
