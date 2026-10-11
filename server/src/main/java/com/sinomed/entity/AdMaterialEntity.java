package com.sinomed.entity;


import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.Date;

/**
 * 广告素材（图片/视频），由管理端维护，平板按排期拉取。
 */
@Data
@Entity
@Table(name = "`ad_materials`", indexes = {
        @Index(name = "idx_ad_material_enabled", columnList = "enabled")
})
@EntityListeners(AuditingEntityListener.class)
public class AdMaterialEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    /** 眉题（叠加层顶部小字，如「冬病夏治」；可空=无叠加层） */
    @Column(name = "kicker")
    private String kicker;

    /** 大标题（叠加层主文案；可空） */
    @Column(name = "title")
    private String title;

    /** 文案副题（叠加层说明行；可空） */
    @Column(name = "copy")
    private String copy;

    /** 价格行（如「¥880 / 10 次」，自由文本；可空） */
    @Column(name = "price")
    private String price;

    /** 1 图片、2 视频 */
    @Column(name = "type", nullable = false)
    private Integer type;

    /** 媒体相对路径（上传后返回，Nginx/静态映射对外） */
    @Column(name = "url", nullable = false)
    private String url;

    /** 轮播停留时长（毫秒），视频可取实际时长 */
    @Column(name = "duration_ms", nullable = false)
    private Integer durationMs;

    /** 1 启用、0 停用 */
    @Column(name = "enabled", nullable = false)
    private Integer enabled;

    /** 轮播顺序，小者在前 */
    @Column(name = "sort", nullable = false)
    private Integer sort;

    @CreatedDate
    @Column(name = "create_time")
    private Date createTime;

    @LastModifiedDate
    @Column(name = "update_time")
    private Date updateTime;
}
