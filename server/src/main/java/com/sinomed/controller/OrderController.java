package com.sinomed.controller;

import com.sinomed.entity.CustomerEntity;
import com.sinomed.entity.ItemEntity;
import com.sinomed.entity.OrderEntity;
import com.sinomed.entity.OrderItemEntity;
import com.sinomed.repository.CustomerRepository;
import com.sinomed.repository.ItemRepository;
import com.sinomed.repository.OrderItemRepository;
import com.sinomed.repository.SettlementRepository;
import com.sinomed.repository.StaffRepository;
import com.sinomed.service.OrderService;
import com.sinomed.vo.ExceptionView;
import com.sinomed.vo.MessageView;
import com.sinomed.vo.OrderItemView;
import com.sinomed.vo.OrderSummaryView;
import com.sinomed.vo.OrderView;
import com.sinomed.vo.PageResp;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 订单管理（需登录）：Kiosk 单在此接待。此前的空壳接口已替换为真实实现，字段与 orders 表对齐。
 * 多品项/数量走 order_item 明细行（items），存量单无行时按 itemId+price 回退一行数量 1。
 */
@Tag(name = "订单", description = "订单API")
@RestController
@RequestMapping("/api/v1/order")
public class OrderController {

    private final OrderService orderService;
    private final CustomerRepository customerRepository;
    private final ItemRepository itemRepository;
    private final OrderItemRepository orderItemRepository;
    private final SettlementRepository settlementRepository;
    private final StaffRepository staffRepository;

    public OrderController(OrderService orderService,
                           CustomerRepository customerRepository,
                           ItemRepository itemRepository,
                           OrderItemRepository orderItemRepository,
                           SettlementRepository settlementRepository,
                           StaffRepository staffRepository) {
        this.orderService = orderService;
        this.customerRepository = customerRepository;
        this.itemRepository = itemRepository;
        this.orderItemRepository = orderItemRepository;
        this.settlementRepository = settlementRepository;
        this.staffRepository = staffRepository;
    }

    @Operation(summary = "前台建单", description = "前台/馆长替顾客下单：customerId 必填，staffId 可选记录建单人；"
            + "单品传 itemId（多品项传 items[{itemId,qty}]，qty≥1）；价格取卡项现价快照并落明细行，"
            + "落 status=0 待接待，之后照常走接单/完成/取消与结算",
            responses = {
                    @ApiResponse(responseCode = "200", description = "创建后的订单（联顾客与卡项名）", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = OrderView.class)
                    )),
                    @ApiResponse(responseCode = "400", description = "参数缺失 / 顾客或卡项不存在 / 卡项已下架", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    )),
                    @ApiResponse(responseCode = "401", description = "没有权限", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    ))
            })
    @PostMapping("/")
    public OrderView create(@Validated @RequestBody OrderView view) {
        if (view.getCustomerId() == null) {
            throw new IllegalArgumentException("需要顾客 id");
        }
        CustomerEntity customer = customerRepository.findById(view.getCustomerId())
                .orElseThrow(() -> new IllegalArgumentException("顾客不存在：" + view.getCustomerId()));
        if (view.getStaffId() != null) {
            staffRepository.findById(view.getStaffId())
                    .orElseThrow(() -> new IllegalArgumentException("员工不存在：" + view.getStaffId()));
        }
        boolean multi = view.getItems() != null && !view.getItems().isEmpty();
        OrderEntity saved = multi
                ? orderService.createWithLines(view.getCustomerId(), toLines(view.getItems()), view.getStaffId())
                : orderService.create(view.getCustomerId(), requireItemId(view), view.getStaffId());
        OrderView result = OrderView.FromOrderEntity(saved);
        result.setCustomerName(customer.getName());
        result.setCustomerPhone(customer.getPhone());
        result.setRemark(saved.getRemark());
        hydrateItems(List.of(result), List.of(saved));
        return result;
    }

    /** 建单请求里的明细行（itemId+qty）转服务入参 */
    private List<OrderService.Line> toLines(List<OrderItemView> items) {
        List<OrderService.Line> lines = new ArrayList<>();
        for (OrderItemView item : items) {
            lines.add(new OrderService.Line(item.getItemId(), item.getQty()));
        }
        return lines;
    }

    private Long requireItemId(OrderView view) {
        if (view.getItemId() == null) {
            throw new IllegalArgumentException("需要卡项 id（或传 items 明细行）");
        }
        return view.getItemId();
    }

    /** 批量联明细行；存量单无行时按 itemId+price 回退一行数量 1 */
    private void hydrateItems(List<OrderView> views, List<OrderEntity> orders) {
        Map<Long, List<OrderItemEntity>> byOrder = orderItemRepository.findByOrderIdIn(
                        orders.stream().map(OrderEntity::getId).collect(Collectors.toSet())).stream()
                .collect(Collectors.groupingBy(OrderItemEntity::getOrderId));
        Map<Long, ItemEntity> itemNames = itemRepository.findAllById(
                        orders.stream().map(OrderEntity::getItemId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(ItemEntity::getId, Function.identity()));
        for (int i = 0; i < views.size(); i++) {
            OrderEntity order = orders.get(i);
            OrderView view = views.get(i);
            List<OrderItemEntity> rows = byOrder.getOrDefault(order.getId(), Collections.emptyList());
            if (!rows.isEmpty()) {
                view.setItems(rows.stream().map(OrderItemView::fromEntity).collect(Collectors.toList()));
            } else if (order.getItemId() != null) {
                ItemEntity item = itemNames.get(order.getItemId());
                view.setItems(List.of(OrderItemView.builder()
                        .itemId(order.getItemId())
                        .itemName(item != null ? item.getName() : null)
                        .price(order.getPrice())
                        .qty(1)
                        .subtotal(order.getPrice())
                        .build()));
            }
            if (view.getItemName() == null && order.getItemId() != null) {
                ItemEntity item = itemNames.get(order.getItemId());
                if (item != null) {
                    view.setItemName(item.getName());
                }
            }
        }
    }

    @Operation(summary = "整单备注", description = "结算台琥珀备忘块：≤200 字，空串/null 清空；返回更新后的订单",
            responses = {
                    @ApiResponse(responseCode = "200", description = "更新后的订单", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = OrderView.class)
                    )),
                    @ApiResponse(responseCode = "400", description = "订单不存在 / 备注超 200 字", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    )),
                    @ApiResponse(responseCode = "401", description = "没有权限", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    ))
            })
    @PutMapping("/remark")
    public OrderView updateRemark(@Validated @RequestBody OrderView view) {
        if (view.getId() == null) {
            throw new IllegalArgumentException("需要订单 id");
        }
        OrderEntity saved = orderService.updateRemark(view.getId(), view.getRemark());
        OrderView result = OrderView.FromOrderEntity(saved);
        result.setRemark(saved.getRemark());
        hydrateItems(List.of(result), List.of(saved));
        return result;
    }

    @Operation(summary = "订单分页", description = "管理端列表：联出顾客名/手机号、卡项名与结算支付方式；status 可选过滤（0 已下单、1 已确认、2 已完成、9 已取消）；pending=true 只看待结算（结算台队列）",
            responses = {
                    @ApiResponse(responseCode = "200", description = "分页订单", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = OrderView.class)
                    )),
                    @ApiResponse(responseCode = "401", description = "没有权限", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    ))
            })
    @GetMapping("/page")
    public PageResp<OrderView> findPage(
            @Parameter(description = "页码，1 起始") @Validated @NotNull @RequestParam int current,
            @Parameter(description = "每页条数") @Validated @NotNull @RequestParam int pageSize,
            @Parameter(description = "状态过滤，可空") @RequestParam(required = false) Integer status,
            @Parameter(description = "只看待结算（状态 0/1），可空") @RequestParam(required = false) Boolean pending) {
        PageRequest pageRequest = PageRequest.of(current > 0 ? current - 1 : 0, pageSize > 0 ? pageSize : 10);
        Page<OrderEntity> result = orderService.findPage(status, pending, pageRequest);

        Map<Long, CustomerEntity> customers = customerRepository.findAllById(
                        result.map(OrderEntity::getUserId).toSet()).stream()
                .collect(Collectors.toMap(CustomerEntity::getId, Function.identity()));
        Map<Long, ItemEntity> items = itemRepository.findAllById(
                        result.map(OrderEntity::getItemId).toSet()).stream()
                .collect(Collectors.toMap(ItemEntity::getId, Function.identity()));
        Map<Long, Integer> payTypes = settlementRepository.findByOrderIdIn(
                        result.map(OrderEntity::getId).toSet()).stream()
                .collect(Collectors.toMap(s -> s.getOrderId(), s -> s.getPayType()));

        List<OrderView> data = result.map(order -> {
            OrderView view = OrderView.FromOrderEntity(order);
            CustomerEntity customer = customers.get(order.getUserId());
            if (customer != null) {
                view.setCustomerName(customer.getName());
                view.setCustomerPhone(customer.getPhone());
            }
            ItemEntity item = items.get(order.getItemId());
            if (item != null) {
                view.setItemName(item.getName());
            }
            view.setPayType(payTypes.get(order.getId()));
            return view;
        }).getContent();
        hydrateItems(data, result.getContent());
        PageResp<OrderView> resp = new PageResp<>();
        resp.data = data;
        resp.pages = result.getTotalPages();
        resp.total = result.getTotalElements();
        resp.success = true;
        return resp;
    }

    @Operation(summary = "按顾客汇总消费", description = "顾客档案「累计消费」统计卡数据源：累计消费=已完成（status=2）订单价格合计，orders=完成单数",
            responses = {
                    @ApiResponse(responseCode = "200", description = "消费汇总", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = OrderSummaryView.class)
                    )),
                    @ApiResponse(responseCode = "401", description = "没有权限", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    ))
            })
    @GetMapping("/summary")
    public OrderSummaryView summary(
            @Parameter(description = "顾客id") @Validated @NotNull @RequestParam Long customerId) {
        return orderService.summaryByCustomer(customerId);
    }

    @Operation(summary = "订单详情", description = "按 id 查单条（含联出的顾客与卡项名）",
            responses = {
                    @ApiResponse(responseCode = "200", description = "订单详情", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = OrderView.class)
                    )),
                    @ApiResponse(responseCode = "400", description = "订单不存在", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    ))
            })
    @GetMapping("/{id}")
    public OrderView findById(@PathVariable Long id) {
        OrderEntity order = orderService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("订单不存在：" + id));
        OrderView view = OrderView.FromOrderEntity(order);
        customerRepository.findById(order.getUserId()).ifPresent(c -> {
            view.setCustomerName(c.getName());
            view.setCustomerPhone(c.getPhone());
        });
        itemRepository.findById(order.getItemId()).ifPresent(i -> view.setItemName(i.getName()));
        hydrateItems(List.of(view), List.of(order));
        return view;
    }

    @Operation(summary = "状态流转", description = "只允许 0→1（接单）、1→2（完成）与 0/1→9（取消）",
            responses = {
                    @ApiResponse(responseCode = "200", description = "流转后的订单", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = OrderView.class)
                    )),
                    @ApiResponse(responseCode = "400", description = "非法流转 / 订单不存在", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    ))
            })
    @PutMapping("/status")
    public OrderView updateStatus(@Validated @RequestBody OrderView view) {
        if (view.getId() == null || view.getStatus() == null) {
            throw new IllegalArgumentException("需要订单 id 与目标 status");
        }
        return OrderView.FromOrderEntity(orderService.updateStatus(view.getId(), view.getStatus()));
    }

    @Operation(summary = "删除订单", description = "仅已取消（status=9）的订单可删",
            responses = {
                    @ApiResponse(responseCode = "200", description = "删除成功", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    )),
                    @ApiResponse(responseCode = "400", description = "订单不存在或未取消", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    ))
            })
    @DeleteMapping("/{id}")
    public MessageView delete(@PathVariable Long id) {
        OrderEntity order = orderService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("订单不存在：" + id));
        if (order.getStatus() == null || order.getStatus() != 9) {
            throw new IllegalArgumentException("只有已取消的订单可删");
        }
        orderService.deleteById(id);
        return MessageView.builder().message("删除成功").build();
    }
}
