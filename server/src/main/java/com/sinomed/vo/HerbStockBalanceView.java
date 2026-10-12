package com.sinomed.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 药材库存余额视图：当前克数与最早未消耗批次效期（FEFO 口径）。
 */
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
@Schema(title = "药材库存余额")
public class HerbStockBalanceView {

    @Schema(title = "药材 id")
    private Long herbId;

    @Schema(title = "药材名")
    private String name;

    @Schema(title = "当前库存（克，可为 0 或负数兜底展示）")
    private Integer stock;

    @Schema(title = "最早未消耗批次效期 yyyy-MM-dd（无批次为空）")
    private String nextExpiry;

    @Schema(title = "距最早未消耗效期的天数（负数表示已过期）")
    private Long expiryInDays;

    @Schema(title = "是否近期到期（expiryWithinDays 内）或已过期")
    private Boolean warnExpiry;

    @Schema(title = "库存下限（克，可空=不预警）")
    private Integer stockMin;

    @Schema(title = "库存上限（克，可空=不预警）")
    private Integer stockMax;

    @Schema(title = "是否触库存线（低于下限或高于上限）")
    private Boolean warnStock;
}
