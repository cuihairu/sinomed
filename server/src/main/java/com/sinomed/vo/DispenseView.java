package com.sinomed.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 药房工作台行视图：处方 + 顾客 + 代煎/膏方状态 + 发药状态。
 */
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
@Schema(title = "药房工作台行")
public class DispenseView {
    @Schema(title = "处方id", example = "12")
    private Long id;

    @Schema(title = "顾客姓名", example = "张三")
    private String customerName;

    @Schema(title = "顾客手机", example = "13900000001")
    private String customerPhone;

    @Schema(title = "处方类型：0 汤剂 / 1 膏方", example = "0")
    private Integer prescriptionType;

    @Schema(title = "剂数", example = "7")
    private Integer doses;

    @Schema(title = "药味数", example = "3")
    private Integer itemCount;

    @Schema(title = "代煎状态文案", example = "可取")
    private String decoctionText;

    @Schema(title = "膏方状态文案", example = "待制作")
    private String pasteText;

    @Schema(title = "发药状态：0 待发药 / 1 已发药", example = "0")
    private Integer dispenseStatus;

    @Schema(title = "发药时间", example = "2026-10-11 11:00:00")
    private Date dispenseTime;

    @Schema(title = "开方时间", example = "2026-10-10 10:30:00")
    private Date createTime;
}
