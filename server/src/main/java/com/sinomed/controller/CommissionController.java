package com.sinomed.controller;

import com.sinomed.entity.CommissionRecordEntity;
import com.sinomed.entity.CommissionRuleEntity;
import com.sinomed.entity.StaffEntity;
import com.sinomed.repository.CommissionRecordRepository;
import com.sinomed.repository.CommissionRuleRepository;
import com.sinomed.repository.ItemRepository;
import com.sinomed.repository.StaffRepository;
import com.sinomed.vo.CommissionRecordView;
import com.sinomed.vo.CommissionRuleView;
import com.sinomed.vo.MessageView;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 提成规则与记录（美容业态 c，docs/design/salon-extension.md §2-c）。
 * 规则：P0 固定比例「项目×岗位」（staffs.title），一岗一比例；
 * 记录：结算自动生成（SettlementServiceImpl 落钩），此处只读 + 汇总。
 */
@Tag(name = "提成", description = "提成规则与记录API")
@RestController
@RequestMapping("/api/v1/commission")
@Validated
public class CommissionController {

    private final CommissionRuleRepository ruleRepository;
    private final CommissionRecordRepository recordRepository;
    private final StaffRepository staffRepository;
    private final ItemRepository itemRepository;

    public CommissionController(CommissionRuleRepository ruleRepository,
                                CommissionRecordRepository recordRepository,
                                StaffRepository staffRepository,
                                ItemRepository itemRepository) {
        this.ruleRepository = ruleRepository;
        this.recordRepository = recordRepository;
        this.staffRepository = staffRepository;
        this.itemRepository = itemRepository;
    }

    // ---------- 规则 ----------

    @Operation(summary = "规则列表", description = "全量按 id 倒序；itemId 可选按卡项过滤",
            responses = @ApiResponse(responseCode = "200", description = "规则数组", content = @Content(
                    mediaType = "application/json", schema = @Schema(implementation = CommissionRuleView.class))))
    @GetMapping("/rule")
    public List<CommissionRuleView> listRules(
            @Parameter(description = "按卡项过滤，可空") @RequestParam(required = false) Long itemId) {
        List<CommissionRuleEntity> rules = itemId == null
                ? ruleRepository.findAll(org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.DESC, "id"))
                : ruleRepository.findByItemIdOrderByIdDesc(itemId);
        return rules.stream().map(CommissionRuleView::FromCommissionRuleEntity).toList();
    }

    @Operation(summary = "新建规则", description = "itemId + title + ratio 必填；同项目同岗位唯一（一岗一比例）；比例 0–100",
            responses = {
                    @ApiResponse(responseCode = "200", description = "创建后的规则", content = @Content(
                            mediaType = "application/json", schema = @Schema(implementation = CommissionRuleView.class))),
                    @ApiResponse(responseCode = "400", description = "参数错误 / 重复", content = @Content(
                            mediaType = "application/json", schema = @Schema(implementation = MessageView.class)))
            })
    @PostMapping("/rule")
    public CommissionRuleView createRule(@Validated @RequestBody CommissionRuleView view) {
        validateRule(view, true);
        ruleRepository.findByItemIdAndTitle(view.getItemId(), view.getTitle().trim()).ifPresent(r -> {
            throw new IllegalArgumentException("该卡项已配此岗位规则：" + view.getTitle());
        });
        return CommissionRuleView.FromCommissionRuleEntity(ruleRepository.save(view.ToCommissionRuleEntity()));
    }

    @Operation(summary = "更新规则", description = "id 必填，可改 title/ratio（itemId 建议删了重建，避免换项目口径漂移）",
            responses = {
                    @ApiResponse(responseCode = "200", description = "更新后的规则", content = @Content(
                            mediaType = "application/json", schema = @Schema(implementation = CommissionRuleView.class))),
                    @ApiResponse(responseCode = "400", description = "规则不存在 / 参数错误", content = @Content(
                            mediaType = "application/json", schema = @Schema(implementation = MessageView.class)))
            })
    @PutMapping("/rule")
    public CommissionRuleView updateRule(@Validated @RequestBody CommissionRuleView view) {
        if (view.getId() == null) {
            throw new IllegalArgumentException("需要规则 id");
        }
        validateRule(view, false);
        CommissionRuleEntity rule = ruleRepository.findById(view.getId())
                .orElseThrow(() -> new IllegalArgumentException("规则不存在：" + view.getId()));
        if (view.getTitle() != null) {
            String title = view.getTitle().trim();
            if (!title.equals(rule.getTitle())) {
                ruleRepository.findByItemIdAndTitle(rule.getItemId(), title).ifPresent(other -> {
                    if (!other.getId().equals(rule.getId())) {
                        throw new IllegalArgumentException("该卡项已配此岗位规则：" + title);
                    }
                });
                rule.setTitle(title);
            }
        }
        if (view.getRatio() != null) {
            rule.setRatio(view.getRatio());
        }
        return CommissionRuleView.FromCommissionRuleEntity(ruleRepository.save(rule));
    }

    @Operation(summary = "删除规则", description = "硬删；已生成的提成记录不受影响（快照在记录里）",
            responses = @ApiResponse(responseCode = "200", description = "删除成功", content = @Content(
                    mediaType = "application/json", schema = @Schema(implementation = MessageView.class))))
    @DeleteMapping("/rule/{id}")
    public MessageView deleteRule(@PathVariable Long id) {
        ruleRepository.deleteById(id);
        return MessageView.builder().message("删除成功").build();
    }

    private void validateRule(CommissionRuleView view, boolean requireItem) {
        if (requireItem && view.getItemId() == null) {
            throw new IllegalArgumentException("需要卡项 id");
        }
        if (requireItem) {
            itemRepository.findById(view.getItemId())
                    .orElseThrow(() -> new IllegalArgumentException("卡项不存在：" + view.getItemId()));
        }
        if (view.getTitle() == null || view.getTitle().isBlank()) {
            throw new IllegalArgumentException("需要岗位 title");
        }
        if (view.getRatio() == null || view.getRatio() < 0 || view.getRatio() > 100) {
            throw new IllegalArgumentException("比例须为 0–100 的整数百分比");
        }
    }

    // ---------- 记录 ----------

    @Operation(summary = "记录分页", description = "orderId/staffId 可选过滤，按 id 倒序；联出员工名",
            responses = @ApiResponse(responseCode = "200", description = "分页记录", content = @Content(
                    mediaType = "application/json", schema = @Schema(implementation = CommissionRecordView.class))))
    @GetMapping("/record/page")
    public PageResp<CommissionRecordView> recordPage(
            @Parameter(description = "页码，1 起始") @Validated @NotNull @RequestParam int current,
            @Parameter(description = "每页条数") @Validated @NotNull @RequestParam int pageSize,
            @Parameter(description = "按订单过滤，可空，可与 staffId 叠加（AND）") @RequestParam(required = false) Long orderId,
            @Parameter(description = "按员工过滤，可空，可与 orderId 叠加（AND）") @RequestParam(required = false) Long staffId) {
        PageRequest pageRequest = PageRequest.of(current > 0 ? current - 1 : 0, pageSize > 0 ? pageSize : 10,
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "id"));
        Page<CommissionRecordEntity> result = orderId != null && staffId != null
                ? recordRepository.findByOrderIdAndStaffId(orderId, staffId, pageRequest)
                : orderId != null
                        ? recordRepository.findByOrderId(orderId, pageRequest)
                        : staffId != null
                                ? recordRepository.findByStaffId(staffId, pageRequest)
                                : recordRepository.findAll(pageRequest);
        Map<Long, StaffEntity> staffs = staffRepository.findAllById(
                        result.map(CommissionRecordEntity::getStaffId).toSet()).stream()
                .collect(Collectors.toMap(StaffEntity::getId, Function.identity()));
        PageResp<CommissionRecordView> resp = new PageResp<>();
        resp.data = result.map(r -> {
            CommissionRecordView view = CommissionRecordView.FromCommissionRecordEntity(r);
            StaffEntity staff = staffs.get(r.getStaffId());
            if (staff != null) {
                view.setStaffName(staff.getName());
            }
            return view;
        }).getContent();
        resp.pages = result.getTotalPages();
        resp.total = result.getTotalElements();
        resp.success = true;
        return resp;
    }

    @Operation(summary = "按员工合计", description = "提成汇总（工资基数）：staffId/起止可空（start 含、end 不含）；净额=正向+冲销",
            responses = @ApiResponse(responseCode = "200", description = "员工合计数组"))
    @GetMapping("/record/summary")
    public List<Map<String, Object>> recordSummary(
            @Parameter(description = "按员工过滤，可空") @RequestParam(required = false) Long staffId,
            @Parameter(description = "起始时刻（含），可空") @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME)
            java.util.Date start,
            @Parameter(description = "截止时刻（不含），可空") @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME)
            java.util.Date end) {
        List<Map<String, Object>> summary = new ArrayList<>();
        List<Object[]> rows = recordRepository.summarize(staffId, start, end);
        Map<Long, StaffEntity> staffs = staffRepository.findAllById(
                        rows.stream().map(row -> (Long) row[0]).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(StaffEntity::getId, Function.identity()));
        for (Object[] row : rows) {
            Map<String, Object> line = new HashMap<>();
            line.put("staffId", row[0]);
            line.put("total", row[1]);
            StaffEntity staff = staffs.get((Long) row[0]);
            if (staff != null) {
                line.put("staffName", staff.getName());
            }
            summary.add(line);
        }
        return summary;
    }
}
