package com.sinomed.vo;

import com.sinomed.entity.ItemEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Kiosk 浏览项：只透出展示字段，不带审计字段。
 */
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
@Schema(title = "Kiosk 服务项")
public class KioskItemView {
    @Schema(title = "卡项id", example = "3")
    private Long id;

    @Schema(title = "名称", example = "拔罐体验券")
    private String name;

    @Schema(title = "价格（元）", example = "99")
    private Integer price;

    @Schema(title = "封面图相对 url，可空", example = "/media/202610/xxx.png")
    private String cover;

    @Schema(title = "简介", example = "到店体验")
    private String description;

    @Schema(title = "服务时长（分钟），可空——供预约页展示，不编造", example = "40")
    private Integer durationMinutes;

    public static KioskItemView FromItemEntity(ItemEntity entity) {
        return KioskItemView.builder()
                .id(entity.getId())
                .name(entity.getName())
                .price(entity.getPrice())
                .cover(entity.getCover())
                .description(entity.getDescription())
                .durationMinutes(entity.getDurationMinutes())
                .build();
    }
}
