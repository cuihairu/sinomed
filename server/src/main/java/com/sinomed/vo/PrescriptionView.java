package com.sinomed.vo;


import com.sinomed.entity.PrescriptionEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

/**
 * 处方笺视图：字段与 prescriptions 表对齐；customerName/staffName/herbs 为列表与详情联出的展示字段，
 * pricing 为按药材字典实时算出的计价（不落库、无快照）；
 * decoction/decoctionFeeFen 为代煎领取口径（decoction 仅开方入参，费率实时算不落库）；
 * prescriptionType/pasteStatus/craft 为膏方口径（膏方开方即落「待制作」，工艺记录收膏方式）。
 */
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
@Schema(title = "中药处方笺")
public class PrescriptionView {

    /** 代煎服务费（分/袋）：袋数=剂数，费率实时计算不落库 */
    public static final int DECOCTION_FEE_FEN_PER_BAG = 300;
    @Schema(title = "处方id", example = "3")
    private Long id;

    @Schema(title = "接诊单id（可空）", example = "8")
    private Long treatId;

    @Schema(title = "顾客id", example = "5")
    private Long customerId;

    @Schema(title = "顾客名（列表联出）", example = "王女士")
    private String customerName;

    @Schema(title = "开方医师id（可空）", example = "2")
    private Long staffId;

    @Schema(title = "开方医师名（列表联出）", example = "沈中医师")
    private String staffName;

    @Schema(title = "剂数（几付）", example = "7")
    private Integer doses;

    @Schema(title = "用法：煎服法/频次/代煎说明等", example = "水煎服，日一剂，早晚温服")
    private String usage;

    @Schema(title = "备注", example = "代煎 7 袋")
    private String remark;

    @Schema(title = "药味列表（详情联出）")
    private List<PrescriptionItemView> herbs;

    @Schema(title = "计价（按药材字典实时算，未收录药名不计费）")
    private PricingView pricing;

    @Schema(title = "开方入参：是否代煎（袋数=剂数，落库为待煎）", example = "true")
    private Boolean decoction;

    @Schema(title = "代煎状态：0 无需代煎 / 1 待煎 / 2 可取 / 3 已取", example = "1")
    private Integer decoctionStatus;

    @Schema(title = "代煎袋数（=剂数；无需代煎为空）", example = "7")
    private Integer decoctionBags;

    @Schema(title = "代煎服务费（分，袋数×300，实时算不落库；无需代煎为空）", example = "2100")
    private Integer decoctionFeeFen;

    @Schema(title = "处方类型：0 汤剂 / 1 膏方（开方入参，默认 0）", example = "1")
    private Integer prescriptionType;

    @Schema(title = "膏方领取状态：0 非膏方 / 1 待制作 / 2 可取 / 3 已取", example = "1")
    private Integer pasteStatus;

    @Schema(title = "收膏方式（仅膏方，可空）", example = "炼蜜")
    private String craft;

    @Schema(title = "发药状态：0 待发药 / 1 已发药（代煎/膏方「已取」自动置 1）", example = "0")
    private Integer dispenseStatus;

    @Schema(title = "发药时间（待发药为空）", example = "2026-10-11 11:00:00")
    private Date dispenseTime;

    @Schema(title = "开方时间", example = "2026-10-09 11:00:00")
    private Date createTime;

    @Schema(title = "药师审核人 staff id（签名行，可空）", example = "3")
    private Long reviewStaffId;

    @Schema(title = "调配 / 复核人 staff id（签名行，可空）", example = "4")
    private Long checkStaffId;

    @Schema(title = "领取方式：0 自取 / 1 同城配送（可空=自取）", example = "0")
    private Integer deliveryType;

    /** 代煎服务费 = 袋数 × 费率（无需代煎返回 null） */
    public static Integer decoctionFeeFen(Integer bags) {
        return bags == null ? null : bags * DECOCTION_FEE_FEN_PER_BAG;
    }

    public static PrescriptionView FromPrescriptionEntity(PrescriptionEntity entity) {
        return PrescriptionView.builder()
                .id(entity.getId())
                .treatId(entity.getTreatId())
                .customerId(entity.getCustomerId())
                .staffId(entity.getStaffId())
                .doses(entity.getDoses())
                .usage(entity.getUsage())
                .remark(entity.getRemark())
                .decoctionStatus(entity.getDecoctionStatus())
                .decoctionBags(entity.getDecoctionBags())
                .decoctionFeeFen(decoctionFeeFen(entity.getDecoctionBags()))
                .prescriptionType(entity.getPrescriptionType())
                .pasteStatus(entity.getPasteStatus())
                .craft(entity.getCraft())
                .dispenseStatus(entity.getDispenseStatus())
                .dispenseTime(entity.getDispenseTime())
                .createTime(entity.getCreateTime())
                .reviewStaffId(entity.getReviewStaffId())
                .checkStaffId(entity.getCheckStaffId())
                .deliveryType(entity.getDeliveryType())
                .build();
    }
}
