package com.sinomed.entity;


import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.DynamicInsert;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.Date;

@Data
@Entity
@DynamicInsert
@Table(name = "`items`",indexes = {
        @Index(name = "idx_item_name",columnList = "name")
})
@EntityListeners(AuditingEntityListener.class)
public class ItemEntity {
    // @DynamicInsert：insert 省略 null 列、落库走列默认（enabled→1、sort→0、category→0）。
    //  直建实体的种子路径（测试夹具、DemoSeedRunner）不必逐字段 set；
    //  与「不写字段初始化器」配套——初始化器会把默认值带进 QBE 查询条件，列默认只影响落库。
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "name",nullable = false,unique = true)
    private String name;

    @Column(name = "price",nullable = false)
    private Integer price;

    @Lob
    @Column(name = "description",nullable = false,columnDefinition = "TEXT")
    private String description;

    /** 1 上架、0 下架（Kiosk 只展示上架项；列默认 1，存量行加列即为上架）。
     *  注意不写字段初始化器——卡项分页查询用 Example 匹配，初始化器会把默认值带进查询条件 */
    @Column(name = "enabled", nullable = false, columnDefinition = "integer default 1")
    private Integer enabled;

    /** 封面图相对 url（可空，走 /media 静态托管） */
    @Column(name = "cover")
    private String cover;

    /** Kiosk 展示顺序，小者在前 */
    @Column(name = "sort", nullable = false, columnDefinition = "integer default 0")
    private Integer sort;

    /** 单次服务时长（分钟，可空；展示用「N 分钟」，不参与结算） */
    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    /** 类目：0 医馆服务（缺省，存量行加列即为 0）/ 1 皮肤管理 / 2 身体护理 / 3 中医美容
     *  （美容业态 a·项目类型扩展，docs/design/salon-extension.md）。
     *  同 enabled 不写字段初始化器——卡项分页查询用 Example 匹配，初始化器会把默认值带进查询条件 */
    @Column(name = "category", nullable = false, columnDefinition = "integer default 0")
    private Integer category;

    @CreatedDate
    @Column(name = "create_time")
    private Date createTime;

    @LastModifiedDate
    @Column(name = "update_time")
    private Date updateTime;
}
