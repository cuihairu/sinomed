package com.sinomed.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.Date;

@Data
@Entity
@Table(name = "`prescriptions`", indexes = {
        @Index(name = "idx_prescription_customer_id", columnList = "customer_id"),
        @Index(name = "idx_prescription_treat_id", columnList = "treat_id")
})
@EntityListeners(AuditingEntityListener.class)
public class PrescriptionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    /** 关联接诊单（可空：随到随开） */
    @Column(name = "treat_id")
    private Long treatId;

    /** 顾客 id */
    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    /** 开方医师 id（可空） */
    @Column(name = "staff_id")
    private Long staffId;

    /** 剂数（几付） */
    @Column(name = "doses", nullable = false)
    private Integer doses;

    /** 用法：煎服法/频次/代煎说明等自由文本 */
    @Column(name = "`usage`")
    private String usage;

    @Column(name = "remark")
    private String remark;

    /** 代煎状态：0 无需代煎 / 1 待煎 / 2 可取 / 3 已取 */
    @Column(name = "decoction_status", nullable = false)
    private Integer decoctionStatus;

    /** 代煎袋数（=剂数；无需代煎为空） */
    @Column(name = "decoction_bags")
    private Integer decoctionBags;

    /** 处方类型：0 汤剂 / 1 膏方（列默认 0） */
    @Column(name = "prescription_type", nullable = false, columnDefinition = "integer default 0")
    private Integer prescriptionType;

    /** 膏方领取状态：0 非膏方 / 1 待制作 / 2 可取 / 3 已取（列默认 0） */
    @Column(name = "paste_status", nullable = false, columnDefinition = "integer default 0")
    private Integer pasteStatus;

    /** 收膏方式（炼蜜 / 清膏 / 糖膏等，仅膏方；可空） */
    @Column(name = "craft")
    private String craft;

    /** 发药状态：0 待发药 / 1 已发药（列默认 0；代煎/膏方「已取」自动置 1） */
    @Column(name = "dispense_status", nullable = false, columnDefinition = "integer default 0")
    private Integer dispenseStatus;

    /** 发药时间（待发药为空） */
    @Column(name = "dispense_time")
    private Date dispenseTime;

    /** 发药操作人 staff id（自动带出为空，流水 note 注明来源；可空） */
    @Column(name = "dispense_by")
    private Long dispenseBy;

    @CreatedDate
    @Column(name = "create_time")
    private Date createTime;

    @LastModifiedDate
    @Column(name = "update_time")
    private Date updateTime;
}
