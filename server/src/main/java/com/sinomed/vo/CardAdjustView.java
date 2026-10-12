package com.sinomed.vo;

import com.sinomed.entity.CardAdjustEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * 持卡调整流水视图（赠次/转卡留痕）：兼作 adjust / transfer 请求体（各自取用字段，服务端校验）。
 */
@Data
@Schema(title = "持卡调整流水")
public class CardAdjustView {

    @Schema(title = "流水id", example = "1")
    private Long id;

    @Schema(title = "持卡id", example = "1")
    private Long cardId;

    @Schema(title = "类型：1 赠次 / 2 转卡", example = "1")
    private Integer type;

    @Schema(title = "赠加次数（1-99，转卡记 0）", example = "5")
    private Integer times;

    @Schema(title = "转卡目标顾客id（type=2 必填）", example = "2")
    private Long customerId;

    @Schema(title = "目标顾客名（联出展示）", example = "王女士")
    private String customerName;

    @Schema(title = "调整理由（必填 ≤100 字）", example = "老客回馈赠 5 次")
    private String reason;

    @Schema(title = "操作人员工id（服务端取当前登录账号，可不传）")
    private Long staffId;

    @Schema(title = "操作人姓名（联出展示）", example = "李店长")
    private String staffName;

    @Schema(title = "操作时间")
    private Date createTime;

    public static CardAdjustView FromEntity(CardAdjustEntity entity) {
        CardAdjustView view = new CardAdjustView();
        view.setId(entity.getId());
        view.setCardId(entity.getCardId());
        view.setType(entity.getType());
        view.setTimes(entity.getTimes());
        view.setCustomerId(entity.getCustomerId());
        view.setReason(entity.getReason());
        view.setStaffId(entity.getStaffId());
        view.setCreateTime(entity.getCreateTime());
        return view;
    }
}
