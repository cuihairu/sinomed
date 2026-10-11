package com.sinomed.entity;

import jakarta.persistence.*;
import lombok.Data;

/**
 * 处方药味：药材名为自由文本（MVP 无药材字典/库存，配伍审方与计价为规划功能）。
 */
@Data
@Entity
@Table(name = "`prescription_items`", indexes = {
        @Index(name = "idx_prescription_item_pid", columnList = "prescription_id")
})
public class PrescriptionItemEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "prescription_id", nullable = false)
    private Long prescriptionId;

    /** 药材名（如「黄芪」） */
    @Column(name = "herb", nullable = false)
    private String herb;

    /** 单剂剂量（克，可为小数如 4.5） */
    @Column(name = "weight", nullable = false)
    private Double weight;

    /** 特殊煎法：先煎/后下/包煎/烊化/冲服等，可空 */
    @Column(name = "special")
    private String special;

    /** 炮制：麸炒/蜜炙/切段生用等，可空 */
    @Column(name = "process")
    private String process;

    /** 展示顺序，小者在前 */
    @Column(name = "sort", nullable = false)
    private Integer sort;
}
