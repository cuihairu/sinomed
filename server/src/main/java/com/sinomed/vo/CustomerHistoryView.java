package com.sinomed.vo;

import com.sinomed.entity.CustomerHistoryEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 顾客病史记录视图：type 0 过敏 / 1 既往，内容为自由文本。
 * 兼作新增病史的请求体，故需无参构造（Jackson 反序列化）。
 */
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
@Schema(title = "顾客病史记录")
public class CustomerHistoryView {
    @Schema(title = "记录id", example = "1")
    private Long id;

    @Schema(title = "顾客id", example = "3")
    private Long customerId;

    @Schema(title = "类型：0 过敏 / 1 既往 / 2 护理方案（美容业态 d 档案扩展）", example = "0")
    private Integer type;

    @Schema(title = "病史内容", example = "青霉素过敏")
    private String content;

    @Schema(title = "记录时间", example = "2026-10-10 10:00:00")
    private Date createTime;

    public static CustomerHistoryView FromEntity(CustomerHistoryEntity entity) {
        return CustomerHistoryView.builder()
                .id(entity.getId())
                .customerId(entity.getCustomerId())
                .type(entity.getType())
                .content(entity.getContent())
                .createTime(entity.getCreateTime())
                .build();
    }
}
