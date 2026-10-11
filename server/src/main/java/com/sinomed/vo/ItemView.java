package com.sinomed.vo;


import com.sinomed.entity.ItemEntity;
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
@Schema(title = "商品信息")
public class ItemView {
    @Schema(title = "商品id",description = "商品id",example = "1234567890")
    private Long id;

    @NotNull
    @Schema(title = "商品名字",description = "商品名字，不可以重复",example = "中医诊断",requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @NotNull
    @Schema(title = "商品价格",description = "商品价格，必填，没有写0",example = "1000",requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer price;

    @NotNull
    @Schema(title = "商品描述",description = "商品描述，一个简介",example = "妙手回春",requiredMode = Schema.RequiredMode.REQUIRED)
    private String description;

    @Schema(title = "上架状态",description = "1 上架、0 下架（Kiosk 只展示上架项），缺省 1",example = "1")
    private Integer enabled;

    @Schema(title = "封面图",description = "相对 url，走 /media 静态托管，可空",example = "/media/202610/xxx.png")
    private String cover;

    @Schema(title = "展示顺序",description = "Kiosk 展示顺序，小者在前，缺省 0",example = "0")
    private Integer sort;

    @Schema(title = "单次时长",description = "分钟，可空；展示用「N 分钟」，不参与结算",example = "40")
    private Integer durationMinutes;

    @Schema(title = "商品创建时间",description = "商品的创建时间",example = "2021-01-01 12:00:00")
    private Date createTime;

    @Schema(title = "商品更新时间",description = "商品更新时间",example = "2023-01-01 12:00:00")
    private Date updateTime;

    public ItemEntity ToItemEntity(){
        ItemEntity itemEntity = new ItemEntity();
        itemEntity.setId(id);
        itemEntity.setName(name);
        itemEntity.setPrice(price);
        itemEntity.setDescription(description);
        itemEntity.setEnabled(enabled != null ? enabled : 1);
        itemEntity.setCover(cover);
        itemEntity.setSort(sort != null ? sort : 0);
        itemEntity.setDurationMinutes(durationMinutes);
        return itemEntity;
    }

    public static ItemView FromItemEntity(ItemEntity itemEntity){
        return ItemView.builder()
                .id(itemEntity.getId())
                .name(itemEntity.getName())
                .price(itemEntity.getPrice())
                .description(itemEntity.getDescription())
                .enabled(itemEntity.getEnabled())
                .cover(itemEntity.getCover())
                .sort(itemEntity.getSort())
                .durationMinutes(itemEntity.getDurationMinutes())
                .createTime(itemEntity.getCreateTime())
                .updateTime(itemEntity.getUpdateTime())
                .build();
    }
}
