package com.sinomed.vo;


import com.sinomed.entity.PrescriptionItemEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 处方药味视图：与 prescription_items 表对齐（MVP 无药材字典，药材名为自由文本）。
 */
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
@Schema(title = "处方药味")
public class PrescriptionItemView {
    @Schema(title = "药味id", example = "12")
    private Long id;

    @Schema(title = "药材名", example = "黄芪")
    private String herb;

    @Schema(title = "单剂剂量（克）", example = "15")
    private Double weight;

    @Schema(title = "特殊煎法：先煎/后下/包煎/烊化/冲服等，可空", example = "先煎")
    private String special;

    @Schema(title = "炮制：麸炒/蜜炙/切段生用等，可空", example = "麸炒")
    private String process;

    @Schema(title = "展示顺序", example = "1")
    private Integer sort;

    public static PrescriptionItemView FromItemEntity(PrescriptionItemEntity entity) {
        return PrescriptionItemView.builder()
                .id(entity.getId())
                .herb(entity.getHerb())
                .weight(entity.getWeight())
                .special(entity.getSpecial())
                .process(entity.getProcess())
                .sort(entity.getSort())
                .build();
    }

    /** 模板药味 → 处方药味视图（两表同构：herb/weight/special/sort；模板无炮制列） */
    public static PrescriptionItemView FromTemplateItemEntity(com.sinomed.entity.PrescriptionTemplateItemEntity entity) {
        return PrescriptionItemView.builder()
                .id(entity.getId())
                .herb(entity.getHerb())
                .weight(entity.getWeight())
                .special(entity.getSpecial())
                .sort(entity.getSort())
                .build();
    }

    /** 方剂药味 → 处方药味视图（两表同构：herb/weight/special/sort；方剂无炮制列） */
    public static PrescriptionItemView FromFormulaItemEntity(com.sinomed.entity.FormulaItemEntity entity) {
        return PrescriptionItemView.builder()
                .id(entity.getId())
                .herb(entity.getHerb())
                .weight(entity.getWeight())
                .special(entity.getSpecial())
                .sort(entity.getSort())
                .build();
    }
}
