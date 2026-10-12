package com.sinomed.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 剂量审方入参：按药材字典单剂克数上限比对当前药味（提示不拦截）。
 */
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
@Schema(title = "剂量审方入参")
public class DoseCheckView {

    @Schema(title = "药味清单（必填）：herb 药名 + weight 单剂克数",
            example = "[{\"herb\":\"附子\",\"weight\":30}]")
    private List<PrescriptionItemView> herbs;
}
