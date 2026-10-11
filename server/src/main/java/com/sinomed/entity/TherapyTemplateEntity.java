package com.sinomed.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.Date;

/**
 * 外治处方模板：耳穴/贴敷/艾灸/拔罐/推拿等外治法的常用组合（方法 + 取穴部位 + 疗程）。
 * 口径：模板名唯一；模板只存建议值，接诊页套用后回填针灸处方区随单自由改（不写回模板）。
 */
@Data
@Entity
@Table(name = "`therapy_templates`", indexes = {
        @Index(name = "idx_therapy_tpl_name", columnList = "name"),
        @Index(name = "idx_therapy_tpl_method", columnList = "method")
})
@EntityListeners(AuditingEntityListener.class)
public class TherapyTemplateEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    /** 模板名（如「失眠·耳穴压豆」），唯一，1–20 字 */
    @Column(name = "name", nullable = false)
    private String name;

    /** 外治方法（如 耳穴压豆、穴位贴敷、艾灸），≤10 字 */
    @Column(name = "method", nullable = false)
    private String method;

    /** 建议取穴/部位（如 神门、交感、皮质下；或 关元、气海），≤100 字，可空 */
    @Column(name = "target")
    private String target;

    /** 建议疗程/频次（如 每周 2 次 × 4 周；贴敷 3 天一换），≤100 字，可空 */
    @Column(name = "course")
    private String course;

    /** 注意事项（如 揉按频次、过敏停用），≤100 字，可空 */
    @Column(name = "note")
    private String note;

    /** 上架：0 停用 / 1 启用（停用不在接诊页出现） */
    @Column(name = "enabled", nullable = false)
    private Integer enabled;

    @CreatedDate
    @Column(name = "create_time")
    private Date createTime;

    @LastModifiedDate
    @Column(name = "update_time")
    private Date updateTime;
}
