package com.sinomed.vo;


import com.sinomed.entity.AdMaterialEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
@Schema(title = "广告素材")
public class AdMaterialView {
    @Schema(title = "素材id", example = "1")
    private Long id;

    @NotNull
    @Schema(title = "素材名", requiredMode = Schema.RequiredMode.REQUIRED, example = "冬季三九贴活动")
    private String name;

    @Schema(title = "分类（素材子标题用，可空）", example = "门店活动")
    private String category;

    @Schema(title = "眉题（叠加层顶部小字，可空）", example = "冬病夏治")
    private String kicker;

    @Schema(title = "大标题（叠加层主文案，可空）", example = "三伏艾灸，借阳祛寒正当时")
    private String title;

    @Schema(title = "文案副题（叠加层说明行，可空）", example = "督脉灸配伍中脘、足三里，适合手脚怕冷的顾客。")
    private String copy;

    @Schema(title = "价格行（自由文本，可空）", example = "¥880 / 10 次")
    private String price;

    @NotNull
    @Schema(title = "类型", description = "1 图片、2 视频", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer type;

    @NotNull
    @Schema(title = "媒体地址", description = "上传接口返回的相对路径", requiredMode = Schema.RequiredMode.REQUIRED, example = "/media/202610/abc.png")
    private String url;

    @NotNull
    @Schema(title = "停留时长(毫秒)", description = "图片轮播停留；视频可取实际时长", requiredMode = Schema.RequiredMode.REQUIRED, example = "8000")
    private Integer durationMs;

    @NotNull
    @Schema(title = "是否启用", description = "1 启用、0 停用", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer enabled;

    @NotNull
    @Schema(title = "轮播顺序", description = "小者在前", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
    private Integer sort;

    @Schema(title = "创建时间", example = "2026-10-01 12:00:00")
    private Date createTime;

    @Schema(title = "更新时间", example = "2026-10-07 09:00:00")
    private Date updateTime;

    public AdMaterialEntity ToAdMaterialEntity() {
        AdMaterialEntity entity = new AdMaterialEntity();
        entity.setId(id);
        entity.setName(name);
        entity.setCategory(category);
        entity.setKicker(kicker);
        entity.setTitle(title);
        entity.setCopy(copy);
        entity.setPrice(price);
        entity.setType(type);
        entity.setUrl(url);
        entity.setDurationMs(durationMs);
        entity.setEnabled(enabled);
        entity.setSort(sort);
        return entity;
    }

    public static AdMaterialView FromAdMaterialEntity(AdMaterialEntity entity) {
        return AdMaterialView.builder()
                .id(entity.getId())
                .name(entity.getName())
                .category(entity.getCategory())
                .kicker(entity.getKicker())
                .title(entity.getTitle())
                .copy(entity.getCopy())
                .price(entity.getPrice())
                .type(entity.getType())
                .url(entity.getUrl())
                .durationMs(entity.getDurationMs())
                .enabled(entity.getEnabled())
                .sort(entity.getSort())
                .createTime(entity.getCreateTime())
                .updateTime(entity.getUpdateTime())
                .build();
    }
}
