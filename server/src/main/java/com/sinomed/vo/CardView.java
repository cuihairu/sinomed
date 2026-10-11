package com.sinomed.vo;

import com.sinomed.entity.CustomerCardEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 顾客持卡（次卡）视图：itemName/customerName 为列表联出展示字段。
 */
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
@Schema(title = "顾客持卡")
public class CardView {
    @Schema(title = "持卡id", example = "1")
    private Long id;

    @NotNull
    @Schema(title = "顾客id", example = "5", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long customerId;

    @Schema(title = "顾客名（列表联出）", example = "王女士")
    private String customerName;

    @NotNull
    @Schema(title = "卡项id", example = "3", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long itemId;

    @Schema(title = "卡项名（列表联出）", example = "经络推拿（10 次卡）")
    private String itemName;

    @NotNull
    @Schema(title = "总次数", example = "10", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer totalTimes;

    @Schema(title = "剩余次数", example = "7")
    private Integer remainingTimes;

    @Schema(title = "状态：1 有效 / 0 停用", example = "1")
    private Integer status;

    @Schema(title = "发卡来源订单id（可空）", example = "12")
    private Long sourceOrderId;

    @Schema(title = "有效期至（可空=长期有效），过期卡不参与抵扣", example = "2026-10-09")
    private Date validUntil;

    @Schema(title = "发卡时间", example = "2026-10-10 10:00:00")
    private Date createTime;

    public static CardView FromEntity(CustomerCardEntity entity) {
        return CardView.builder()
                .id(entity.getId())
                .customerId(entity.getCustomerId())
                .itemId(entity.getItemId())
                .totalTimes(entity.getTotalTimes())
                .remainingTimes(entity.getRemainingTimes())
                .status(entity.getStatus())
                .sourceOrderId(entity.getSourceOrderId())
                .validUntil(entity.getValidUntil())
                .createTime(entity.getCreateTime())
                .build();
    }
}
