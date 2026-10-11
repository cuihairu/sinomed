package com.sinomed.vo;

import com.sinomed.entity.OrderItemEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 订单明细行视图：订单接口 items 字段的行结构（名称/单价为下单时刻快照）。
 * 建单请求里只需 itemId + qty，其余为服务端落库后回显。
 */
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
@Schema(title = "订单明细行")
public class OrderItemView {
    @Schema(title = "卡项id", example = "3")
    private Long itemId;

    @Schema(title = "卡项名（快照）", example = "艾灸调理")
    private String itemName;

    @Schema(title = "单价快照（元）", example = "100")
    private Integer price;

    @Schema(title = "数量（≥1）", example = "2")
    private Integer qty;

    @Schema(title = "小计（元）", example = "200")
    private Integer subtotal;

    public static OrderItemView fromEntity(OrderItemEntity e) {
        return OrderItemView.builder()
                .itemId(e.getItemId())
                .itemName(e.getName())
                .price(e.getPrice())
                .qty(e.getQty())
                .subtotal(e.getSubtotal())
                .build();
    }
}
