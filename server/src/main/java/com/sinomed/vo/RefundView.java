package com.sinomed.vo;

import com.sinomed.entity.RefundLogEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 退款单视图：字段与 refund_logs 表对齐；customerName/payTypeText/staffName 为联出展示。
 */
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
@Schema(title = "退款单")
public class RefundView {
    @Schema(title = "退款流水id", example = "1")
    private Long id;

    @Schema(title = "被退的结算单id", example = "5")
    private Long settlementId;

    @Schema(title = "订单id", example = "12")
    private Long orderId;

    @Schema(title = "顾客id", example = "8")
    private Long customerId;

    @Schema(title = "顾客名（联出）", example = "王女士")
    private String customerName;

    @Schema(title = "退款金额（元，= 原结算实收；次卡为 0）", example = "480")
    private Integer money;

    @Schema(title = "原支付方式：1 储值 / 2 微信 / 3 支付宝 / 4 现金 / 5 次卡抵扣", example = "4")
    private Integer payType;

    @Schema(title = "支付方式文案", example = "现金")
    private String payTypeText;

    @Schema(title = "退款理由（≤100 字）", example = "收错款")
    private String reason;

    @Schema(title = "操作人 staff id", example = "2")
    private Long staffId;

    @Schema(title = "操作人名（联出）", example = "沈知远")
    private String staffName;

    @Schema(title = "退款时间", example = "2026-10-11 12:05:00")
    private Date createTime;

    public static RefundView from(RefundLogEntity entity, String payTypeText) {
        return RefundView.builder()
                .id(entity.getId())
                .settlementId(entity.getSettlementId())
                .orderId(entity.getOrderId())
                .money(entity.getMoney())
                .payType(entity.getPayType())
                .payTypeText(payTypeText)
                .reason(entity.getReason())
                .staffId(entity.getStaffId())
                .createTime(entity.getCreateTime())
                .build();
    }
}
