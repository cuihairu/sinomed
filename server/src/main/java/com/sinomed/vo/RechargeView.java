package com.sinomed.vo;


import com.sinomed.entity.RechargeEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 储值流水视图：字段与 recharges 表对齐；customerName 是管理端列表联出的展示字段。
 * money 可为负：充值为正，储值支付扣减为负（余额 = 流水合计）。
 */
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
@Schema(title = "储值流水")
public class RechargeView {
    @Schema(title = "流水id", example = "3")
    private Long id;

    @Schema(title = "顾客id", example = "8")
    private Long customerId;

    @Schema(title = "顾客名（列表联出）", example = "王女士")
    private String customerName;

    @Schema(title = "金额（元，充值为正、储值支付为负）", example = "500")
    private Integer money;

    @Schema(title = "备注（可空，储值退款补回为「退单 #结算id」）", example = "退单 #4")
    private String note;

    @Schema(title = "发生时间", example = "2026-10-07 10:00:00")
    private Date createTime;

    public static RechargeView FromRechargeEntity(RechargeEntity entity) {
        return RechargeView.builder()
                .id(entity.getId())
                .customerId(entity.getUserId())
                .money(entity.getMoney())
                .note(entity.getNote())
                .createTime(entity.getCreateTime())
                .build();
    }
}
