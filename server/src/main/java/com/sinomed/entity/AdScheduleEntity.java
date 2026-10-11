package com.sinomed.entity;


import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.Date;

/**
 * 排期：把素材挂到屏上，限定生效星期与时段；无命中时平板兜底播该屏全部启用素材。
 */
@Data
@Entity
@Table(name = "`ad_schedules`", indexes = {
        @Index(name = "idx_ad_schedule_screen_id", columnList = "screen_id"),
        @Index(name = "idx_ad_schedule_material_id", columnList = "material_id")
})
@EntityListeners(AuditingEntityListener.class)
public class AdScheduleEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    /** 屏 id（业务外键） */
    @Column(name = "screen_id", nullable = false)
    private Long screenId;

    /** 素材 id（业务外键） */
    @Column(name = "material_id", nullable = false)
    private Long materialId;

    /** 排期名（屏幕状态展示用，如「10 月常规排期」；可空） */
    @Column(name = "name")
    private String name;

    /** 生效星期，逗号分隔，1=周一 … 7=周日；空 = 每天 */
    @Column(name = "weekdays")
    private String weekdays;

    /** 生效时段起点 HH:mm；空 = 全天 */
    @Column(name = "start_time")
    private String startTime;

    /** 生效时段终点 HH:mm；空 = 全天 */
    @Column(name = "end_time")
    private String endTime;

    /** 1 启用、0 停用 */
    @Column(name = "enabled", nullable = false)
    private Integer enabled;

    @CreatedDate
    @Column(name = "create_time")
    private Date createTime;

    @LastModifiedDate
    @Column(name = "update_time")
    private Date updateTime;
}
