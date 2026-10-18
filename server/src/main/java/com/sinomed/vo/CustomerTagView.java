package com.sinomed.vo;


import com.sinomed.entity.CustomerTagEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Date;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
@Schema(title = "顾客标签")
public class CustomerTagView {
    @Schema(title = "标签id", example = "1")
    private Long id;

    @Schema(title = "顾客id", example = "3")
    private Long customerId;

    @Schema(title = "标签（≤20 字，如「肤质·油性」）", example = "肤质·油性")
    private String tag;

    @Schema(title = "创建时间", example = "2026-10-18 21:00:00")
    private Date createTime;

    public static CustomerTagView FromEntity(CustomerTagEntity entity) {
        return CustomerTagView.builder()
                .id(entity.getId())
                .customerId(entity.getCustomerId())
                .tag(entity.getTag())
                .createTime(entity.getCreateTime())
                .build();
    }
}
