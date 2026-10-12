package com.sinomed.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 顾客就诊次数（排班页「首诊/复诊」角标依据）：visits ≥ 1 即有既往诊疗记录=复诊，0/未收录=首诊。
 */
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
@Schema(title = "顾客就诊次数")
public class CustomerVisitsVo {

    @Schema(title = "顾客id", example = "1")
    private Long customerId;

    @Schema(title = "诊疗记录条数（treats 表按顾客计数）", example = "3")
    private Long visits;
}
