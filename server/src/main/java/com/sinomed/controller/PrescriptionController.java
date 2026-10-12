package com.sinomed.controller;

import com.sinomed.entity.PrescriptionEntity;
import com.sinomed.repository.CustomerRepository;
import com.sinomed.repository.PrescriptionItemRepository;
import com.sinomed.repository.StaffRepository;
import com.sinomed.service.AllergyService;
import com.sinomed.service.CompatibilityService;
import com.sinomed.service.DoseService;
import com.sinomed.service.PrescriptionService;
import com.sinomed.service.PricingService;
import com.sinomed.service.StaffService;
import com.sinomed.vo.CompatibilityCheckView;
import com.sinomed.vo.CompatibilityResultView;
import com.sinomed.vo.AllergyCheckView;
import com.sinomed.vo.AllergyResultView;
import com.sinomed.vo.DoseCheckView;
import com.sinomed.vo.DoseResultView;
import com.sinomed.vo.DispenseView;
import com.sinomed.vo.MessageView;
import com.sinomed.vo.PageResp;
import com.sinomed.vo.PrescriptionItemView;
import com.sinomed.vo.PrescriptionView;
import com.sinomed.vo.PricingView;
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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 中药处方（需登录）：处方笺开方、查询、删除、配伍审方与实时计价、代煎流转、膏方领取流转。
 * 口径：药材名为自由文本；配伍审方（十八反/十九畏）提示不拦截；
 * 计价按药材字典（/api/v1/herb）实时算、无快照；代煎袋数=剂数、状态待煎→可取→已取、
 * 服务费=袋数×3 元实时算（提示口径，收费仍以卡项订单结算为准）；库存仍为规划功能。
 */
@Tag(name = "处方", description = "中药处方API")
@RestController
@RequestMapping("/api/v1/prescription")
public class PrescriptionController {

    private final PrescriptionService prescriptionService;
    private final PrescriptionItemRepository prescriptionItemRepository;
    private final CustomerRepository customerRepository;
    private final StaffRepository staffRepository;
    private final CompatibilityService compatibilityService;
    private final PricingService pricingService;
    private final AllergyService allergyService;
    private final DoseService doseService;
    private final StaffService staffService;

    public PrescriptionController(PrescriptionService prescriptionService,
                                  PrescriptionItemRepository prescriptionItemRepository,
                                  CustomerRepository customerRepository,
                                  StaffRepository staffRepository,
                                  CompatibilityService compatibilityService,
                                  PricingService pricingService,
                                  AllergyService allergyService,
                                  DoseService doseService,
                                  StaffService staffService) {
        this.prescriptionService = prescriptionService;
        this.prescriptionItemRepository = prescriptionItemRepository;
        this.customerRepository = customerRepository;
        this.staffRepository = staffRepository;
        this.compatibilityService = compatibilityService;
        this.pricingService = pricingService;
        this.allergyService = allergyService;
        this.doseService = doseService;
        this.staffService = staffService;
    }

    @Operation(summary = "开方", description = "customerId + herbs（至少 1 味：herb 药名 + weight 剂量克，special 特殊煎法/process 炮制可选）必填；"
            + "treatId/staffId/reviewStaffId（药师审核）/checkStaffId（调配复核）/doses（默认 7）/usage（煎法）/takeMethod（服法）/remark 可选；"
            + "decoction=true 时代煎，袋数=剂数、落「待煎」，deliveryType 0 自取 / 1 同城配送（不代煎忽略）；"
            + "prescriptionType=1 时膏方（落「待制作」，craft 记收膏方式）",
            responses = {
                    @ApiResponse(responseCode = "200", description = "创建后的处方（含药味）", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PrescriptionView.class)
                    )),
                    @ApiResponse(responseCode = "400", description = "参数缺失 / 药味为空 / 药名或剂量非法 / 顾客不存在", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    )),
                    @ApiResponse(responseCode = "401", description = "没有权限", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    ))
            })
    @PostMapping("/")
    public PrescriptionView create(@Validated @RequestBody PrescriptionView view) {
        if (view.getCustomerId() == null) {
            throw new IllegalArgumentException("需要顾客 id");
        }
        customerRepository.findById(view.getCustomerId())
                .orElseThrow(() -> new IllegalArgumentException("顾客不存在：" + view.getCustomerId()));
        if (view.getStaffId() != null) {
            staffRepository.findById(view.getStaffId())
                    .orElseThrow(() -> new IllegalArgumentException("员工不存在：" + view.getStaffId()));
        }
        for (Long sid : Arrays.asList(view.getReviewStaffId(), view.getCheckStaffId())) {
            if (sid != null) {
                staffRepository.findById(sid)
                        .orElseThrow(() -> new IllegalArgumentException("员工不存在：" + sid));
            }
        }
        PrescriptionEntity saved = prescriptionService.save(view);
        // 重查一遍带出审计时间与药味，回包完整（含实时计价）
        PrescriptionView result = prescriptionService.findById(saved.getId());
        result.setPricing(pricingService.price(result.getHerbs(), result.getDoses()));
        return result;
    }

    @Operation(summary = "配伍审方", description = "按经典十八反（禁忌）/十九畏（慎用）比对药材名；自由文本药名按别名包含匹配"
            + "（如「法半夏」命中「半夏」）。findings 为空即未发现配伍禁忌；提示不拦截，是否照用由医师判断",
            responses = {
                    @ApiResponse(responseCode = "200", description = "审方结果", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CompatibilityResultView.class)
                    )),
                    @ApiResponse(responseCode = "400", description = "药材名单为空", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    )),
                    @ApiResponse(responseCode = "401", description = "没有权限", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    ))
            })
    @PostMapping("/compatibility")
    public CompatibilityResultView checkCompatibility(@RequestBody CompatibilityCheckView view) {
        List<String> herbs = view == null ? null : view.getHerbs();
        return compatibilityService.check(herbs);
    }

    @Operation(summary = "过敏审方", description = "按顾客过敏史（customer_histories type=0）比对当前药味：过敏史原文包含药名"
            + "（≥2 字）即命中（如「阿胶、蜂蜜过敏」命中「阿胶」）。findings 为空即过敏史未提到当前药味；"
            + "提示不拦截，是否照用由医师判断",
            responses = {
                    @ApiResponse(responseCode = "200", description = "审方结果", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AllergyResultView.class)
                    )),
                    @ApiResponse(responseCode = "400", description = "顾客不存在或药材名单为空", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    )),
                    @ApiResponse(responseCode = "401", description = "没有权限", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    ))
            })
    @PostMapping("/allergy-check")
    public AllergyResultView checkAllergy(@RequestBody AllergyCheckView view) {
        return allergyService.check(view == null ? null : view.getCustomerId(),
                view == null ? null : view.getHerbs());
    }

    @Operation(summary = "剂量审方", description = "按药材字典单剂克数上限（herbs.dose_max，可空=不预警）比对当前药味单剂克数："
            + "weight 严格大于上限才命中（等值不提示），字典未收录药名进 unmatched 无参照，未设上限不预警。"
            + "findings 为空即未发现超上限；提示不拦截，是否照用由医师判断",
            responses = {
                    @ApiResponse(responseCode = "200", description = "剂量审方结果", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = DoseResultView.class)
                    )),
                    @ApiResponse(responseCode = "400", description = "药材名单为空", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    )),
                    @ApiResponse(responseCode = "401", description = "没有权限", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    ))
            })
    @PostMapping("/dose-check")
    public DoseResultView checkDose(@RequestBody DoseCheckView view) {
        return doseService.check(view == null ? null : view.getHerbs());
    }

    @Operation(summary = "处方试算", description = "不开方只算钱：body 传 herbs（herb/weight）+ doses，按药材字典实时计价返回；"
            + "未收录药名计入 unknownHerbs 不计费",
            responses = {
                    @ApiResponse(responseCode = "200", description = "计价结果", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PricingView.class)
                    ))
            })
    @PostMapping("/price")
    public PricingView price(@RequestBody PrescriptionView view) {
        return pricingService.price(view == null ? null : view.getHerbs(), view == null ? null : view.getDoses());
    }

    @Operation(summary = "处方分页", description = "管理端列表：联出顾客名、医师名与药味（herbs）；customerId 可选过滤，id 倒序",
            responses = {
                    @ApiResponse(responseCode = "200", description = "分页处方", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PrescriptionView.class)
                    )),
                    @ApiResponse(responseCode = "401", description = "没有权限", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    ))
            })
    @GetMapping("/page")
    public PageResp<PrescriptionView> findPage(
            @Parameter(description = "页码，1 起始") @Validated @NotNull @RequestParam int current,
            @Parameter(description = "每页条数") @Validated @NotNull @RequestParam int pageSize,
            @Parameter(description = "顾客id过滤，可空") @RequestParam(required = false) Long customerId) {
        PageRequest pageRequest = PageRequest.of(current > 0 ? current - 1 : 0, pageSize > 0 ? pageSize : 10,
                Sort.by(Sort.Direction.DESC, "id"));
        Page<PrescriptionEntity> result = prescriptionService.findPage(customerId, pageRequest);

        Map<Long, String> customerNames = customerRepository.findAllById(
                        result.map(PrescriptionEntity::getCustomerId).toSet()).stream()
                .collect(Collectors.toMap(c -> c.getId(), c -> c.getName()));
        Map<Long, String> staffNames = staffRepository.findAllById(
                        result.map(PrescriptionEntity::getStaffId).filter(s -> s != null).toSet()).stream()
                .collect(Collectors.toMap(s -> s.getId(), s -> s.getName()));
        Map<Long, List<PrescriptionItemView>> herbs = prescriptionItemRepository
                .findByPrescriptionIdInOrderBySortAsc(result.map(PrescriptionEntity::getId).getContent())
                .stream().collect(Collectors.groupingBy(item -> item.getPrescriptionId(),
                        Collectors.mapping(PrescriptionItemView::FromItemEntity, Collectors.toList())));

        List<PrescriptionView> data = result.map(prescription -> {
            PrescriptionView view = PrescriptionView.FromPrescriptionEntity(prescription);
            view.setCustomerName(customerNames.get(prescription.getCustomerId()));
            view.setStaffName(staffNames.get(prescription.getStaffId()));
            view.setHerbs(herbs.get(prescription.getId()));
            view.setPricing(pricingService.price(view.getHerbs(), view.getDoses()));
            return view;
        }).getContent();
        PageResp<PrescriptionView> resp = new PageResp<>();
        resp.data = data;
        resp.pages = result.getTotalPages();
        resp.total = result.getTotalElements();
        resp.success = true;
        return resp;
    }

    @Operation(summary = "处方详情", description = "按 id 查处方（含药味）",
            responses = {
                    @ApiResponse(responseCode = "200", description = "处方详情", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PrescriptionView.class)
                    )),
                    @ApiResponse(responseCode = "400", description = "处方不存在", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    )),
                    @ApiResponse(responseCode = "401", description = "没有权限", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    ))
            })
    @GetMapping("/{id}")
    public PrescriptionView findById(@PathVariable Long id) {
        PrescriptionView view = prescriptionService.findById(id);
        view.setPricing(pricingService.price(view.getHerbs(), view.getDoses()));
        return view;
    }

    @Operation(summary = "代煎流转", description = "代煎处方领取流转：待煎(1)→可取(2)→已取(3) 顺序推进；"
            + "未选代煎(0)或回退/跳跃均报 400。回包为流转后的完整处方视图（含药味与计价）",
            responses = {
                    @ApiResponse(responseCode = "200", description = "流转后的处方", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PrescriptionView.class)
                    )),
                    @ApiResponse(responseCode = "400", description = "处方不存在 / 状态无效 / 流转不合法", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    )),
                    @ApiResponse(responseCode = "401", description = "没有权限", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    ))
            })
    @PutMapping("/{id}/decoction")
    public PrescriptionView setDecoctionStatus(
            @PathVariable Long id,
            @Parameter(description = "目标状态：2 可取 / 3 已取") @RequestParam Integer status) {
        prescriptionService.setDecoctionStatus(id, status);
        PrescriptionView view = prescriptionService.findById(id);
        view.setPricing(pricingService.price(view.getHerbs(), view.getDoses()));
        return view;
    }

    @Operation(summary = "膏方领取流转", description = "膏方处方领取流转：待制作(1)→可取(2)→已取(3) 顺序推进；"
            + "非膏方(0)或回退/跳跃均报 400。回包为流转后的完整处方视图（含药味与计价）",
            responses = {
                    @ApiResponse(responseCode = "200", description = "流转后的处方", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PrescriptionView.class)
                    )),
                    @ApiResponse(responseCode = "400", description = "处方不存在 / 非膏方 / 状态无效 / 流转不合法", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    )),
                    @ApiResponse(responseCode = "401", description = "没有权限", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    ))
            })
    @PutMapping("/{id}/paste")
    public PrescriptionView setPasteStatus(
            @PathVariable Long id,
            @Parameter(description = "目标状态：2 可取 / 3 已取") @RequestParam Integer status) {
        prescriptionService.setPasteStatus(id, status);
        PrescriptionView view = prescriptionService.findById(id);
        view.setPricing(pricingService.price(view.getHerbs(), view.getDoses()));
        return view;
    }

    @Operation(summary = "药房发药", description = "待发药(0)→已发药(1) 单向：确认「药交出去了」，落发药流水；"
            + "操作人取当前登录账号；note ≤100 字可选。已发药再发报 400（不开放撤销，与代煎/膏方领取单向口径一致）；"
            + "代煎/膏方走「已取」领取时自动置已发药，无需重复点发药",
            responses = {
                    @ApiResponse(responseCode = "200", description = "药房工作台行（联顾客/类型/状态）", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = DispenseView.class)
                    )),
                    @ApiResponse(responseCode = "400", description = "处方不存在 / 已发药 / 备注超长", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    )),
                    @ApiResponse(responseCode = "401", description = "没有权限", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    ))
            })
    @PostMapping("/{id}/dispense")
    public DispenseView dispense(@PathVariable Long id,
                                 @RequestBody(required = false) Map<String, Object> body) {
        String note = body == null || body.get("note") == null ? null : body.get("note").toString();
        PrescriptionEntity saved = prescriptionService.dispense(id, currentStaffId(), note);
        return toDispenseView(saved);
    }

    @Operation(summary = "药房工作台分页", description = "dispenseStatus 可空（0 待发药 / 1 已发药）；"
            + "customerKeyword 按顾客姓名/手机模糊，可空；id 倒序，联顾客、代煎/膏方状态与药味数",
            responses = {
                    @ApiResponse(responseCode = "200", description = "药房工作台分页", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = DispenseView.class)
                    )),
                    @ApiResponse(responseCode = "401", description = "没有权限", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    ))
            })
    @GetMapping("/dispense/page")
    public PageResp<DispenseView> findDispensePage(
            @Parameter(description = "页码，1 起始") @Validated @NotNull @RequestParam int current,
            @Parameter(description = "每页条数") @Validated @NotNull @RequestParam int pageSize,
            @Parameter(description = "发药状态过滤：0 待发药 / 1 已发药，可空") @RequestParam(required = false) Integer dispenseStatus,
            @Parameter(description = "顾客姓名/手机模糊，可空") @RequestParam(required = false) String customerKeyword) {
        PageRequest pageRequest = PageRequest.of(current > 0 ? current - 1 : 0, pageSize > 0 ? pageSize : 10,
                Sort.by(Sort.Direction.DESC, "id"));
        Page<PrescriptionEntity> result = prescriptionService.findDispensePage(dispenseStatus, customerKeyword, pageRequest);
        PageResp<DispenseView> resp = new PageResp<>();
        resp.data = result.map(this::toDispenseView).getContent();
        resp.pages = result.getTotalPages();
        resp.total = result.getTotalElements();
        resp.success = true;
        return resp;
    }

    /** 发药操作人：取当前登录账号对应的员工（查无留空，不阻断发药） */
    private Long currentStaffId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null) {
            return null;
        }
        return staffService.findByAccount(auth.getName()).map(s -> s.getId()).orElse(null);
    }

    /** 工作台行组装：联顾客姓名手机、代煎/膏方状态文案与药味数 */
    private DispenseView toDispenseView(PrescriptionEntity prescription) {
        return customerRepository.findById(prescription.getCustomerId())
                .map(customer -> DispenseView.builder()
                        .id(prescription.getId())
                        .customerName(customer.getName())
                        .customerPhone(customer.getPhone())
                        .prescriptionType(prescription.getPrescriptionType())
                        .doses(prescription.getDoses())
                        .itemCount((int) prescriptionItemRepository.countByPrescriptionId(prescription.getId()))
                        .decoctionText(decoctionText(prescription.getDecoctionStatus()))
                        .pasteText(pasteText(prescription.getPasteStatus()))
                        .dispenseStatus(prescription.getDispenseStatus())
                        .dispenseTime(prescription.getDispenseTime())
                        .createTime(prescription.getCreateTime())
                        .build())
                .orElseGet(() -> DispenseView.builder().id(prescription.getId())
                        .prescriptionType(prescription.getPrescriptionType())
                        .doses(prescription.getDoses())
                        .itemCount((int) prescriptionItemRepository.countByPrescriptionId(prescription.getId()))
                        .decoctionText(decoctionText(prescription.getDecoctionStatus()))
                        .pasteText(pasteText(prescription.getPasteStatus()))
                        .dispenseStatus(prescription.getDispenseStatus())
                        .dispenseTime(prescription.getDispenseTime())
                        .createTime(prescription.getCreateTime())
                        .build());
    }

    private static String decoctionText(Integer status) {
        return switch (status == null ? 0 : status) {
            case 0 -> "无需代煎";
            case 1 -> "待煎";
            case 2 -> "可取";
            default -> "已取";
        };
    }

    private static String pasteText(Integer status) {
        return switch (status == null ? 0 : status) {
            case 0 -> "非膏方";
            case 1 -> "待制作";
            case 2 -> "可取";
            default -> "已取";
        };
    }

    @Operation(summary = "删除处方", description = "连同药味一并删除（演示环境口径，无留痕）",
            responses = {
                    @ApiResponse(responseCode = "200", description = "删除成功", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    )),
                    @ApiResponse(responseCode = "400", description = "处方不存在", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    )),
                    @ApiResponse(responseCode = "401", description = "没有权限", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    ))
            })
    @DeleteMapping("/{id}")
    public MessageView deleteById(@PathVariable Long id) {
        prescriptionService.deleteById(id);
        return MessageView.builder().message("已删除处方 " + id).build();
    }
}
