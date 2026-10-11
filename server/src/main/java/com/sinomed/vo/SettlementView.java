package com.sinomed.vo;


import com.sinomed.entity.SettlementEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 结算单视图：字段与 settlements 表对齐；customerName/orderId 关联信息为列表联出。
 * 支付方式：1 储值 / 2 微信 / 3 支付宝 / 4 现金 / 5 次卡抵扣（实收 0）
 * （演示口径：微信/支付宝仅记录方式，不拉起真实收银台）。
 */
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
@Schema(title = "结算单")
public class SettlementView {
    @Schema(title = "结算单id", example = "3")
    private Long id;

    @Schema(title = "订单id", example = "12")
    private Long orderId;

    @Schema(title = "顾客id", example = "8")
    private Long customerId;

    @Schema(title = "顾客名（列表联出）", example = "王女士")
    private String customerName;

    @Schema(title = "支付方式：1 储值 / 2 微信 / 3 支付宝 / 4 现金 / 5 次卡抵扣（实收 0）", example = "1")
    private Integer payType;

    @Schema(title = "实收金额（元）", example = "480")
    private Integer money;

    @Schema(title = "结算时间", example = "2026-10-09 11:30:00")
    private Date createTime;

    @Schema(title = "是否已退款（列表联出，null 视为未退）", example = "false")
    private Boolean refunded;

    public static SettlementView FromSettlementEntity(SettlementEntity entity) {
        return SettlementView.builder()
                .id(entity.getId())
                .orderId(entity.getOrderId())
                .customerId(entity.getUserId())
                .payType(entity.getPayType())
                .money(entity.getMoney())
                .createTime(entity.getCreateTime())
                .build();
    }
}
