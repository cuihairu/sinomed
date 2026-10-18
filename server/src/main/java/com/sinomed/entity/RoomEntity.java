package com.sinomed.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.DynamicInsert;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.Date;

/**
 * 工位/房间：预约的资源维度（美容业态 b，docs/design/salon-extension.md §2-b）。
 * 医馆预约 roomId 可空（不占工位维）；美容项目预约占工位，冲突检测技师+工位双维。
 * 与叫号展示文本 QueueCallEntity.room 区分——本实体是可预约资源。
 */
@Data
@Entity
@DynamicInsert
@Table(name = "`rooms`",indexes = {
        @Index(name = "idx_room_name",columnList = "name")
})
@EntityListeners(AuditingEntityListener.class)
public class RoomEntity {
    // @DynamicInsert：insert 省略 null 列、落库走列默认（enabled→1）。
    //  同 ItemEntity 纪律：不写字段初始化器，列默认只影响落库。
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "name",nullable = false,unique = true)
    private String name;

    /** 1 在用、0 停用（列默认 1；预约表单只列在用工位） */
    @Column(name = "enabled", nullable = false, columnDefinition = "integer default 1")
    private Integer enabled;

    @CreatedDate
    @Column(name = "create_time")
    private Date createTime;

    @LastModifiedDate
    @Column(name = "update_time")
    private Date updateTime;
}
