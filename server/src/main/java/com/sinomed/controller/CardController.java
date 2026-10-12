package com.sinomed.controller;

import com.sinomed.entity.CardUsageEntity;
import com.sinomed.entity.CustomerCardEntity;
import com.sinomed.repository.CustomerRepository;
import com.sinomed.repository.ItemRepository;
import com.sinomed.repository.StaffRepository;
import com.sinomed.service.CardService;
import com.sinomed.vo.CardUsageView;
import com.sinomed.vo.CardView;
import com.sinomed.vo.MessageView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 顾客持卡（次卡/疗程卡，需登录）：发卡、按顾客查询、停用/恢复、核销记录查询。
 * 结算抵扣由 /api/v1/settlement 的支付方式 5 触发（见 SettlementController），抵扣即落核销流水。
 */
@Tag(name = "次卡", description = "顾客持卡（次卡）API")
@RestController
@RequestMapping("/api/v1/card")
public class CardController {

    private final CardService cardService;
    private final CustomerRepository customerRepository;
    private final ItemRepository itemRepository;
    private final StaffRepository staffRepository;

    public CardController(CardService cardService,
                          CustomerRepository customerRepository,
                          ItemRepository itemRepository,
                          StaffRepository staffRepository) {
        this.cardService = cardService;
        this.customerRepository = customerRepository;
        this.itemRepository = itemRepository;
        this.staffRepository = staffRepository;
    }

    @Operation(summary = "发卡", description = "customerId + itemId + totalTimes（≥1）必填；sourceOrderId 可空（溯源购卡订单）",
            responses = {
                    @ApiResponse(responseCode = "200", description = "发卡后的持卡", content = @Content(
                            mediaType = "application/json", schema = @Schema(implementation = CardView.class))),
                    @ApiResponse(responseCode = "400", description = "顾客/卡项/订单不存在、总次数非法", content = @Content(
                            mediaType = "application/json", schema = @Schema(implementation = MessageView.class)))
            })
    @PostMapping("/")
    public CardView issue(@Valid @RequestBody CardView view) {
        CustomerCardEntity card = cardService.issue(view);
        return decorate(card);
    }

    @Operation(summary = "按顾客查持卡", description = "新卡在前；customerId 必填",
            responses = {
                    @ApiResponse(responseCode = "200", description = "持卡列表（可空）", content = @Content(
                            mediaType = "application/json", schema = @Schema(implementation = CardView.class)))
            })
    @GetMapping("/list")
    public List<CardView> listByCustomer(
            @Parameter(description = "顾客id") @NotNull @RequestParam Long customerId) {
        return cardService.listByCustomer(customerId).stream()
                .map(this::decorate)
                .collect(Collectors.toList());
    }

    @Operation(summary = "批量按顾客查持卡", description = "ids 逗号分隔；排班格位疗程标记等聚合场景一次拉齐，返回各顾客持卡（含卡项名）",
            responses = {
                    @ApiResponse(responseCode = "200", description = "持卡列表（可空）", content = @Content(
                            mediaType = "application/json", schema = @Schema(implementation = CardView.class)))
            })
    @GetMapping("/customers")
    public List<CardView> listByCustomerIds(
            @Parameter(description = "顾客id 集合，逗号分隔") @NotNull @RequestParam("ids") List<Long> ids) {
        return cardService.listByCustomerIds(ids).stream()
                .map(this::decorate)
                .collect(Collectors.toList());
    }

    @Operation(summary = "停用 / 恢复", description = "status：1 有效 / 0 停用",
            responses = {
                    @ApiResponse(responseCode = "200", description = "更新后的持卡", content = @Content(
                            mediaType = "application/json", schema = @Schema(implementation = CardView.class))),
                    @ApiResponse(responseCode = "400", description = "持卡不存在、状态非法", content = @Content(
                            mediaType = "application/json", schema = @Schema(implementation = MessageView.class)))
            })
    @PutMapping("/{id}/status")
    public CardView setStatus(@PathVariable @NotNull Long id, @RequestParam @NotNull Integer status) {
        return decorate(cardService.setStatus(id, status));
    }

    @Operation(summary = "按持卡查核销记录", description = "每次结算抵扣一条（第几次/时间/服务员工/订单）；新记录在前",
            responses = {
                    @ApiResponse(responseCode = "200", description = "核销记录列表（可空）", content = @Content(
                            mediaType = "application/json", schema = @Schema(implementation = CardUsageView.class))),
                    @ApiResponse(responseCode = "400", description = "持卡id缺失或不存在", content = @Content(
                            mediaType = "application/json", schema = @Schema(implementation = MessageView.class)))
            })
    @GetMapping("/{id}/usages")
    public List<CardUsageView> usages(
            @Parameter(description = "持卡id") @PathVariable @NotNull Long id) {
        return cardService.listUsages(id).stream()
                .map(this::decorateUsage)
                .collect(Collectors.toList());
    }

    /** 联出顾客名与卡项名 */
    private CardView decorate(CustomerCardEntity card) {
        CardView view = CardView.FromEntity(card);
        customerRepository.findById(card.getCustomerId())
                .ifPresent(c -> view.setCustomerName(c.getName()));
        itemRepository.findById(card.getItemId())
                .ifPresent(i -> view.setItemName(i.getName()));
        return view;
    }

    /** 联出服务员工名 */
    private CardUsageView decorateUsage(CardUsageEntity usage) {
        CardUsageView view = CardUsageView.FromEntity(usage);
        if (usage.getStaffId() != null) {
            staffRepository.findById(usage.getStaffId())
                    .ifPresent(s -> view.setStaffName(s.getName()));
        }
        return view;
    }
}
