package com.sinomed.vo;


import com.sinomed.entity.CommissionRecordEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Date;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
@Schema(title = "提成记录")
public class CommissionRecordView {
    @Schema(title = "记录id", example = "1")
    private Long id;

    @Schema(title = "订单id", example = "12")
    private Long orderId;

    @Schema(title = "结算单id", example = "7")
    private Long settlementId;

    @Schema(title = "订单明细行id", example = "20")
    private Long orderItemId;

    @Schema(title = "员工id", example = "1")
    private Long staffId;

    @Schema(title = "员工名（列表联出）", example = "李技师")
    private String staffName;

    @Schema(title = "卡项id", example = "3")
    private Long itemId;

    @Schema(title = "卡项名快照", example = "皮肤管理")
    private String itemName;

    @Schema(title = "行小计快照（元）", example = "400")
    private Integer subtotal;

    @Schema(title = "比例快照（百分比）", example = "20")
    private Integer ratio;

    @Schema(title = "提成金额（元，退单冲销为负）", example = "80")
    private Integer amount;

    @Schema(title = "备注（退单冲销记「退单冲销 #结算id」）")
    private String remark;

    @Schema(title = "创建时间", example = "2026-10-18 21:00:00")
    private Date createTime;

    public static CommissionRecordView FromCommissionRecordEntity(CommissionRecordEntity record){
        return CommissionRecordView.builder()
                .id(record.getId())
                .orderId(record.getOrderId())
                .settlementId(record.getSettlementId())
                .orderItemId(record.getOrderItemId())
                .staffId(record.getStaffId())
                .itemId(record.getItemId())
                .itemName(record.getItemName())
                .subtotal(record.getSubtotal())
                .ratio(record.getRatio())
                .amount(record.getAmount())
                .remark(record.getRemark())
                .createTime(record.getCreateTime())
                .build();
    }
}
