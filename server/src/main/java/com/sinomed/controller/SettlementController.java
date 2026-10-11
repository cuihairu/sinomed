package com.sinomed.controller;

import com.sinomed.entity.SettlementEntity;
import com.sinomed.repository.CustomerRepository;
import com.sinomed.repository.RefundLogRepository;
import com.sinomed.repository.SettlementRepository;
import com.sinomed.service.SettlementService;
import com.sinomed.service.StaffService;
import com.sinomed.vo.MessageView;
import com.sinomed.vo.PageResp;
import com.sinomed.vo.ReconciliationView;
import com.sinomed.vo.RefundView;
import com.sinomed.vo.SettlementMonthReportView;
import com.sinomed.vo.SettlementView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 收费结算（需登录）：结算台对待结算订单（状态 0 已下单 / 1 已确认）收款。
 * 储值支付校验余额并扣减流水；微信/支付宝为演示口径，只记录支付方式。
 * 退单原路退回（一单只退一次），日对账按自然日聚合收款/充值/退款。
 */
@Tag(name = "收费", description = "结算API")
@RestController
@RequestMapping("/api/v1/settlement")
public class SettlementController {

    private final SettlementService settlementService;
    private final CustomerRepository customerRepository;
    private final RefundLogRepository refundLogRepository;
    private final SettlementRepository settlementRepository;
    private final StaffService staffService;

    public SettlementController(SettlementService settlementService,
                                CustomerRepository customerRepository,
                                RefundLogRepository refundLogRepository,
                                SettlementRepository settlementRepository,
                                StaffService staffService) {
        this.settlementService = settlementService;
        this.customerRepository = customerRepository;
        this.refundLogRepository = refundLogRepository;
        this.settlementRepository = settlementRepository;
        this.staffService = staffService;
    }

    @Operation(summary = "收款结算", description = "orderId+payType（1 储值 / 2 微信 / 3 支付宝 / 4 现金）必填；"
            + "订单状态 0/1 → 2 已完成，一单一结算；储值支付余额不足报 400",
            responses = {
                    @ApiResponse(responseCode = "200", description = "结算单", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SettlementView.class)
                    )),
                    @ApiResponse(responseCode = "400", description = "参数缺失 / 订单不存在或已完结 / 已结算过 / 储值余额不足", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    )),
                    @ApiResponse(responseCode = "401", description = "没有权限", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    ))
            })
    @PostMapping("/")
    public SettlementView create(@Validated @RequestBody SettlementView view) {
        SettlementEntity saved = settlementService.settle(view);
        SettlementView result = SettlementView.FromSettlementEntity(saved);
        customerRepository.findById(saved.getUserId())
                .ifPresent(customer -> result.setCustomerName(customer.getName()));
        return result;
    }

    @Operation(summary = "结算单分页", description = "管理端「最近结算」列表：联出顾客名，时间倒序",
            responses = {
                    @ApiResponse(responseCode = "200", description = "分页结算单", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SettlementView.class)
                    )),
                    @ApiResponse(responseCode = "401", description = "没有权限", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    ))
            })
    @GetMapping("/page")
    public PageResp<SettlementView> findPage(
            @Parameter(description = "页码，1 起始") @Validated @NotNull @RequestParam int current,
            @Parameter(description = "每页条数") @Validated @NotNull @RequestParam int pageSize) {
        PageRequest pageRequest = PageRequest.of(current > 0 ? current - 1 : 0, pageSize > 0 ? pageSize : 10,
                Sort.by(Sort.Direction.DESC, "id"));
        Page<SettlementEntity> result = settlementService.findPage(pageRequest);

        Map<Long, String> names = customerRepository.findAllById(
                        result.map(SettlementEntity::getUserId).toSet()).stream()
                .collect(Collectors.toMap(customer -> customer.getId(), customer -> customer.getName()));

        Set<Long> refundedIds = refundLogRepository.findBySettlementIdIn(
                        result.map(SettlementEntity::getId).toSet()).stream()
                .map(r -> r.getSettlementId())
                .collect(Collectors.toSet());

        List<SettlementView> data = result.map(settlement -> {
            SettlementView view = SettlementView.FromSettlementEntity(settlement);
            view.setCustomerName(names.get(settlement.getUserId()));
            view.setRefunded(refundedIds.contains(settlement.getId()));
            return view;
        }).getContent();
        PageResp<SettlementView> resp = new PageResp<>();
        resp.data = data;
        resp.pages = result.getTotalPages();
        resp.total = result.getTotalElements();
        resp.success = true;
        return resp;
    }

    @Operation(summary = "月度收费报表", description = "month=yyyy-MM 必填；按结算时间聚合当月结算单："
            + "逐日单数/实收、支付方式构成、次卡核销单数（实收 0）",
            responses = {
                    @ApiResponse(responseCode = "200", description = "月度报表", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SettlementMonthReportView.class)
                    )),
                    @ApiResponse(responseCode = "400", description = "月份格式无效", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    )),
                    @ApiResponse(responseCode = "401", description = "没有权限", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    ))
            })
    @GetMapping("/report/month")
    public SettlementMonthReportView monthReport(
            @Parameter(description = "报表月份 yyyy-MM") @Validated @NotNull @RequestParam String month) {
        return settlementService.monthReport(month);
    }

    @Operation(summary = "退单", description = "reason 必填 ≤100 字；一笔结算只能退一次（已退款报 400）；"
            + "退款 = 退整单、按原 payType 原路退回（储值补正数流水、次卡作废核销回补余次、"
            + "微信/支付宝/现金仅落退款记录）；订单 2 已完成 → 1 已接单可重收",
            responses = {
                    @ApiResponse(responseCode = "200", description = "退款单", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = RefundView.class)
                    )),
                    @ApiResponse(responseCode = "400", description = "理由空白或超长 / 结算不存在 / 该结算已退款 / 订单未完结", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    )),
                    @ApiResponse(responseCode = "401", description = "没有权限", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    ))
            })
    @PostMapping("/{id}/refund")
    public RefundView refund(@PathVariable Long id,
                             @RequestBody(required = false) Map<String, Object> body) {
        String reason = body == null || body.get("reason") == null ? null : body.get("reason").toString();
        var saved = settlementService.refund(id, currentStaffId(), reason);
        RefundView view = RefundView.from(saved, payTypeText(saved.getPayType() == null ? 0 : saved.getPayType()));
        joinRefundNames(view);
        return view;
    }

    /** 退款单联出顾客名与操作人名 */
    private void joinRefundNames(RefundView view) {
        settlementRepository.findById(view.getSettlementId()).ifPresent(s -> {
            view.setCustomerId(s.getUserId());
            customerRepository.findById(s.getUserId()).ifPresent(c -> view.setCustomerName(c.getName()));
        });
        if (view.getStaffId() != null) {
            staffService.findById(view.getStaffId()).ifPresent(s -> view.setStaffName(s.getName()));
        }
    }

    @Operation(summary = "日对账单", description = "date=yyyy-MM-dd 必填（格式非法报 400）；按自然日聚合"
            + "收款（单数/实收/支付方式构成/次卡核销单列）、充值（笔数/金额，单列不计入营收，"
            + "退款补回的流水不计）、退款（笔数/金额/按原支付方式构成）；净额=实收-退款，现金净额供钱箱核对",
            responses = {
                    @ApiResponse(responseCode = "200", description = "日对账单", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ReconciliationView.class)
                    )),
                    @ApiResponse(responseCode = "400", description = "日期缺失或格式非法", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    )),
                    @ApiResponse(responseCode = "401", description = "没有权限", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    ))
            })
    @GetMapping("/reconciliation")
    public ReconciliationView reconciliation(
            @Parameter(description = "对账日期 yyyy-MM-dd") @Validated @NotNull @RequestParam String date) {
        ReconciliationView report = settlementService.reconciliation(date);
        joinNames(report);
        return report;
    }

    /** 对账明细联出顾客名与操作人名（日单量小，逐行查询足够） */
    private void joinNames(ReconciliationView report) {
        if (report.getRecharges() != null) {
            report.getRecharges().forEach(r -> customerRepository.findById(r.getCustomerId())
                    .ifPresent(c -> r.setCustomerName(c.getName())));
        }
        if (report.getRefunds() != null) {
            report.getRefunds().forEach(r -> {
                customerRepository.findById(r.getCustomerId()).ifPresent(c -> r.setCustomerName(c.getName()));
                if (r.getStaffId() != null) {
                    staffService.findById(r.getStaffId()).ifPresent(s -> r.setStaffName(s.getName()));
                }
            });
        }
    }

    private static String payTypeText(int payType) {
        return switch (payType) {
            case 1 -> "储值";
            case 2 -> "微信";
            case 3 -> "支付宝";
            case 4 -> "现金";
            case 5 -> "次卡抵扣";
            default -> "未知(" + payType + ")";
        };
    }

    /** 退款操作人：取当前登录账号对应的员工（查无留空，不阻断退款） */
    private Long currentStaffId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null) {
            return null;
        }
        return staffService.findByAccount(auth.getName()).map(s -> s.getId()).orElse(null);
    }
}
