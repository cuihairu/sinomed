package com.sinomed.vo;

import com.sinomed.entity.HerbEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 药材字典条目：处方计价比价依据；价格单位为每克多少分。
 */
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
@Schema(title = "药材字典条目")
public class HerbView {
    @Schema(title = "药材id", example = "1")
    private Long id;

    @NotBlank
    @Schema(title = "药材名（唯一，与处方药名精确比对）", example = "甘草", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @NotNull
    @Schema(title = "每克价格（分）", example = "5", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer price;

    @Schema(title = "库存下限（克，可空=不预警；余额低于下限时余额接口标 warnStock）", example = "500")
    private Integer stockMin;

    @Schema(title = "库存上限（克，可空=不预警；余额高于上限时余额接口标 warnStock）", example = "5000")
    private Integer stockMax;

    @Schema(title = "单剂克数上限（克/剂，可空=不预警；开方剂量审方超线提示）", example = "10")
    private Double doseMax;

    @Schema(title = "收录时间", example = "2026-10-10 10:00:00")
    private Date createTime;

    public static HerbView FromEntity(HerbEntity entity) {
        return HerbView.builder()
                .id(entity.getId())
                .name(entity.getName())
                .price(entity.getPrice())
                .stockMin(entity.getStockMin())
                .stockMax(entity.getStockMax())
                .doseMax(entity.getDoseMax())
                .createTime(entity.getCreateTime())
                .build();
    }
}
