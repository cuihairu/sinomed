package com.sinomed.vo;


import com.sinomed.entity.RoomEntity;
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
@Schema(title = "工位信息")
public class RoomView {
    @Schema(title = "工位id",description = "工位id",example = "1234567890")
    private Long id;

    @NotNull
    @Schema(title = "工位名字",description = "工位名字，不可以重复",example = "美容一号工位",requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Schema(title = "在用状态",description = "1 在用、0 停用，缺省 1",example = "1")
    private Integer enabled;

    @Schema(title = "工位创建时间",description = "工位的创建时间",example = "2021-01-01 12:00:00")
    private Date createTime;

    @Schema(title = "工位更新时间",description = "工位的更新时间",example = "2023-01-01 12:00:00")
    private Date updateTime;

    public RoomEntity ToRoomEntity(){
        RoomEntity roomEntity = new RoomEntity();
        roomEntity.setId(id);
        roomEntity.setName(name);
        roomEntity.setEnabled(enabled != null ? enabled : 1);
        return roomEntity;
    }

    public static RoomView FromRoomEntity(RoomEntity roomEntity){
        return RoomView.builder()
                .id(roomEntity.getId())
                .name(roomEntity.getName())
                .enabled(roomEntity.getEnabled())
                .createTime(roomEntity.getCreateTime())
                .updateTime(roomEntity.getUpdateTime())
                .build();
    }
}
