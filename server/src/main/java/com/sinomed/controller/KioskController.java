package com.sinomed.controller;

import com.sinomed.entity.AppointmentEntity;
import com.sinomed.entity.CardLookupLogEntity;
import com.sinomed.entity.CustomerCardEntity;
import com.sinomed.entity.CustomerEntity;
import com.sinomed.entity.ItemEntity;
import com.sinomed.entity.OrderEntity;
import com.sinomed.entity.SmsCodeEntity;
import com.sinomed.repository.AppointmentRepository;
import com.sinomed.repository.CardLookupLogRepository;
import com.sinomed.repository.CustomerCardRepository;
import com.sinomed.repository.CustomerRepository;
import com.sinomed.repository.ItemRepository;
import com.sinomed.repository.SmsCodeRepository;
import com.sinomed.service.AppointmentService;
import com.sinomed.service.OrderService;
import com.sinomed.service.WechatClient;
import com.sinomed.vo.ExceptionView;
import com.sinomed.vo.KioskAppointmentRequest;
import com.sinomed.vo.KioskAppointmentResult;
import com.sinomed.vo.KioskCardLookupRequest;
import com.sinomed.vo.KioskCardLookupResult;
import com.sinomed.vo.KioskOpenidSessionRequest;
import com.sinomed.vo.KioskOpenidSessionResult;
import com.sinomed.vo.KioskItemView;
import com.sinomed.vo.KioskOrderRequest;
import com.sinomed.vo.KioskOrderResult;
import com.sinomed.vo.MessageView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 顾客选服务 Kiosk（免登录，内网设备约定，见 docs/design/kiosk.md）：
 * 浏览纯只读；下单与自助约期是两个写入口，写范围限定一条顾客档案（按手机号幂等）+ 待接待订单/预约。
 * 自助约期口径见 docs/design/app-booking.md（频控 1 条/日/手机号、整点 09:00–17:00、时长固定 60 分钟）。
 *
 * <p>手机查卡（card-lookup）是第三个写入口：不做裸手机号查卡——按 sinomed.phone-lookup.mode
 * （off/openid_only/sms_verified，默认 off）走身份绑定，openid 与短信两条路径都落审计
 * （card_lookup_logs：查了谁的卡/谁查的/时间），口径见 2026-10-14 裁定单。
 */
@Tag(name = "顾客选服务 Kiosk", description = "到店自助浏览卡项与下单（免登录）")
@RestController
@RequestMapping("/api/v1/kiosk")
public class KioskController {

    /** 自助约期可约时段（整点起始，含 17:00 对齐排班网格最后一格） */
    private static final int BOOKABLE_HOUR_FIRST = 9;
    private static final int BOOKABLE_HOUR_LAST = 17;

    /** 短信验证码有效期（分钟） */
    private static final int SMS_CODE_TTL_MINUTES = 5;

    private final ItemRepository itemRepository;
    private final CustomerRepository customerRepository;
    private final OrderService orderService;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentService appointmentService;
    private final CustomerCardRepository customerCardRepository;
    private final CardLookupLogRepository cardLookupLogRepository;
    private final SmsCodeRepository smsCodeRepository;
    private final WechatClient wechatClient;

    /** 手机查卡模式：off（默认，关闭）/ openid_only / sms_verified，后台可改 */
    @Value("${sinomed.phone-lookup.mode:off}")
    private String phoneLookupMode;

    public KioskController(ItemRepository itemRepository,
                           CustomerRepository customerRepository,
                           OrderService orderService,
                           AppointmentRepository appointmentRepository,
                           AppointmentService appointmentService,
                           CustomerCardRepository customerCardRepository,
                           CardLookupLogRepository cardLookupLogRepository,
                           SmsCodeRepository smsCodeRepository,
                           WechatClient wechatClient) {
        this.itemRepository = itemRepository;
        this.customerRepository = customerRepository;
        this.orderService = orderService;
        this.appointmentRepository = appointmentRepository;
        this.appointmentService = appointmentService;
        this.customerCardRepository = customerCardRepository;
        this.cardLookupLogRepository = cardLookupLogRepository;
        this.smsCodeRepository = smsCodeRepository;
        this.wechatClient = wechatClient;
    }

    @Operation(summary = "浏览上架卡项", description = "Kiosk 首页网格：enabled=1 的卡项按 sort、name 升序，只含展示字段",
            responses = {
                    @ApiResponse(responseCode = "200", description = "上架卡项列表", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = KioskItemView.class)
                    ))
            })
    @GetMapping("/items")
    public List<KioskItemView> items() {
        return itemRepository.findByEnabledOrderBySortAscNameAsc(1).stream()
                .map(KioskItemView::FromItemEntity)
                .toList();
    }

    @Operation(summary = "下单", description = "按手机号找/建顾客（幂等），每个卡项 id 落一条待接待订单（status=0、价格取卡项现价快照）；不做支付",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = KioskOrderRequest.class)
            ), required = true),
            responses = {
                    @ApiResponse(responseCode = "200", description = "下单回执（单号与合计）", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = KioskOrderResult.class)
                    )),
                    @ApiResponse(responseCode = "400", description = "手机号格式错误 / 卡项不存在或已下架", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    )),
                    @ApiResponse(responseCode = "500", description = "服务器参数", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ExceptionView.class)
                    ))
            })
    @PostMapping("/orders")
    public KioskOrderResult order(@Validated @RequestBody KioskOrderRequest request) {
        if (!request.getPhone().matches("^1\\d{10}$")) {
            throw new IllegalArgumentException("手机号格式不对：应为 1 开头的 11 位数字");
        }
        // 卡项先全部校验，避免校验到一半建档
        List<ItemEntity> items = new ArrayList<>();
        for (Long itemId : request.getItemIds()) {
            ItemEntity item = itemRepository.findById(itemId)
                    .orElseThrow(() -> new IllegalArgumentException("卡项不存在：" + itemId));
            if (item.getEnabled() == null || item.getEnabled() != 1) {
                throw new IllegalArgumentException("卡项已下架：" + item.getName());
            }
            items.add(item);
        }

        CustomerEntity customer = findOrCreateCustomer(request.getPhone(), request.getName());

        List<KioskOrderResult.Line> lines = new ArrayList<>();
        int totalFee = 0;
        for (ItemEntity item : items) {
            // 统一走前台建单（含明细行落库与价格快照），Kiosk 单无建单员工
            OrderEntity saved = orderService.create(customer.getId(), item.getId(), null);
            totalFee += item.getPrice() == null ? 0 : item.getPrice();
            lines.add(KioskOrderResult.Line.builder()
                    .id(saved.getId())
                    .itemId(item.getId())
                    .itemName(item.getName())
                    .price(item.getPrice())
                    .build());
        }
        return KioskOrderResult.builder()
                .customerId(customer.getId())
                .customerName(customer.getName())
                .orders(lines)
                .totalFee(totalFee)
                .build();
    }

    @Operation(summary = "自助约期", description = "小程序/自助机顾客免登录约期：按手机号找/建顾客（幂等），落一条待到店预约（duration=60、status=0、remark「小程序自助」）；"
                    + "频控同手机号当日 status IN (0,1) 限 1 条；时段限 09:00–17:00 整点且晚于当前时刻；每时段限约 1 位（未指派医师空组互斥，冲突报 400）；不做支付",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = KioskAppointmentRequest.class)
            ), required = true),
            responses = {
                    @ApiResponse(responseCode = "200", description = "约期回执", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = KioskAppointmentResult.class)
                    )),
                    @ApiResponse(responseCode = "400", description = "手机号/时间格式或越界 / 当日已有预约（频控）/ 卡项不存在或已下架", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    )),
                    @ApiResponse(responseCode = "500", description = "服务器参数", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ExceptionView.class)
                    ))
            })
    @PostMapping("/appointments")
    public KioskAppointmentResult appointment(@Validated @RequestBody KioskAppointmentRequest request) {
        if (!request.getPhone().matches("^1\\d{10}$")) {
            throw new IllegalArgumentException("手机号格式不对：应为 1 开头的 11 位数字");
        }
        LocalDateTime start;
        try {
            start = LocalDateTime.parse(request.getStartTime(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("时间格式不对：应为 yyyy-MM-dd HH:mm");
        }
        if (start.getMinute() != 0 || start.getSecond() != 0) {
            throw new IllegalArgumentException("预约时段须为整点");
        }
        if (start.getHour() < BOOKABLE_HOUR_FIRST || start.getHour() > BOOKABLE_HOUR_LAST) {
            throw new IllegalArgumentException("可约时段为每日 09:00–17:00 整点");
        }
        if (!start.isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("预约时段须晚于当前时刻");
        }
        // 卡项先校验，避免校验到一半建档（同下单口径）
        ItemEntity item = null;
        if (request.getItemId() != null) {
            item = itemRepository.findById(request.getItemId())
                    .orElseThrow(() -> new IllegalArgumentException("卡项不存在：" + request.getItemId()));
            if (item.getEnabled() == null || item.getEnabled() != 1) {
                throw new IllegalArgumentException("卡项已下架：" + item.getName());
            }
        }

        CustomerEntity customer = findOrCreateCustomer(request.getPhone(), request.getName());

        // 频控：同手机号当日 status IN (0,1) 限 1 条
        Date dayStart = Date.from(start.toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date dayEnd = Date.from(start.toLocalDate().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
        long activeCount = appointmentRepository.countByCustomerIdAndStartTimeBetweenAndStatusIn(
                customer.getId(), dayStart, dayEnd, List.of(0, 1));
        if (activeCount > 0) {
            throw new IllegalArgumentException("当日已有预约，请到店或致电改约");
        }

        // 每时段限约 1 位：自助约期未指派医师（staffId 空，到店分配），空组互斥
        appointmentService.ensureSlotFree(
                Date.from(start.atZone(ZoneId.systemDefault()).toInstant()), null);

        AppointmentEntity appointment = new AppointmentEntity();
        appointment.setCustomerId(customer.getId());
        appointment.setItemId(item == null ? null : item.getId());
        appointment.setStaffId(null); // 到店分配
        appointment.setStartTime(Date.from(start.atZone(ZoneId.systemDefault()).toInstant()));
        appointment.setDuration(60);
        appointment.setStatus(0);
        appointment.setRemark("小程序自助");
        AppointmentEntity saved = appointmentRepository.save(appointment);
        return KioskAppointmentResult.builder()
                .customerId(customer.getId())
                .customerName(customer.getName())
                .appointmentId(saved.getId())
                .startTime(request.getStartTime())
                .itemName(item == null ? null : item.getName())
                .status(0)
                .build();
    }

    @Operation(summary = "获取短信验证码", description = "sms_verified 模式下发 6 位验证码（5 分钟有效、一次性）；off/openid_only 模式拒绝。"
                    + "本系统无真实短信网关，发码只落库与日志（演示环境向店员索取）",
            responses = {
                    @ApiResponse(responseCode = "200", description = "发码回执", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    )),
                    @ApiResponse(responseCode = "400", description = "模式不允许 / 手机号格式错误", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    ))
            })
    @PostMapping("/sms-code")
    public MessageView smsCode(@RequestBody KioskCardLookupRequest request) {
        requireSmsMode(request.getPhone(), null);
        SmsCodeEntity code = new SmsCodeEntity();
        code.setPhone(request.getPhone());
        code.setCode(String.valueOf(100000 + ThreadLocalRandom.current().nextInt(900000)));
        code.setUsed(0);
        code.setExpiresAt(new Date(System.currentTimeMillis() + SMS_CODE_TTL_MINUTES * 60_000L));
        smsCodeRepository.save(code);
        // 无短信网关：演示环境的送达方式（测试用 SmsCodeRepository 直接取码）
        org.slf4j.LoggerFactory.getLogger(KioskController.class)
                .info("SMS_CODE_DEMO phone={} code={}", request.getPhone(), code.getCode());
        return new MessageView("验证码已发送（5 分钟内有效，演示环境请向店员索取）");
    }

    @Operation(summary = "小程序登录换 openid", description = "openid_only 模式专用：wx.login 的 code 经微信 jscode2session 换 openid，"
                    + "供查卡绑定路径使用（凭据未配置时明确拒绝，不静默）。off/sms_verified 模式拒绝；"
                    + "session_key 属敏感凭据不出服务端。每次尝试（含拒绝）落 card_lookup_logs 审计",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = KioskOpenidSessionRequest.class)
            ), required = true),
            responses = {
                    @ApiResponse(responseCode = "200", description = "openid 回执", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = KioskOpenidSessionResult.class)
                    )),
                    @ApiResponse(responseCode = "400", description = "模式不允许 / 缺 code / 凭据未配置 / 微信返回错误", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    ))
            })
    @PostMapping("/openid-session")
    public KioskOpenidSessionResult openidSession(@RequestBody KioskOpenidSessionRequest request) {
        String mode = phoneLookupMode;
        if ("off".equals(mode)) {
            audit(null, null, mode, null, "disabled");
            throw new IllegalArgumentException("在线查卡已关闭，请到店前台或致电门店查询");
        }
        if (!"openid_only".equals(mode)) {
            audit(null, null, mode, null, "disabled");
            throw new IllegalArgumentException("当前查卡方式不支持微信登录，请走手机号验证码查询");
        }
        String code = request.getCode();
        if (code == null || code.isBlank()) {
            audit(null, null, mode, null, "missing_code");
            throw new IllegalArgumentException("需要小程序登录 code");
        }
        try {
            String openid = wechatClient.codeToOpenid(code.trim());
            return KioskOpenidSessionResult.builder().openid(openid).build();
        } catch (IllegalArgumentException e) {
            audit(null, null, mode, null, "code_exchange_failed");
            throw e;
        }
    }

    @Operation(summary = "手机查卡", description = "按 sinomed.phone-lookup.mode 走身份绑定后返回持卡：off 全拒；"
                    + "openid_only 需 openid 且已绑定；sms_verified 需手机号+验证码（双因子）。"
                    + "每次尝试（含拒绝）落 card_lookup_logs 审计。不做裸手机号查卡（2026-10-14 裁定）",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = KioskCardLookupRequest.class)
            ), required = true),
            responses = {
                    @ApiResponse(responseCode = "200", description = "持卡列表", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = KioskCardLookupResult.class)
                    )),
                    @ApiResponse(responseCode = "400", description = "模式关闭 / 缺身份 / 未绑定 / 验证码错误", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    ))
            })
    @PostMapping("/card-lookup")
    public KioskCardLookupResult cardLookup(@RequestBody KioskCardLookupRequest request) {
        String mode = phoneLookupMode;
        if ("off".equals(mode)) {
            audit(null, null, mode, null, "disabled");
            throw new IllegalArgumentException("在线查卡已关闭，请到店前台或致电门店查询");
        }
        if ("openid_only".equals(mode)) {
            return lookupByOpenid(request, mode);
        }
        if ("sms_verified".equals(mode)) {
            return lookupBySms(request, mode);
        }
        audit(null, null, mode, null, "disabled");
        throw new IllegalArgumentException("在线查卡已关闭，请到店前台或致电门店查询");
    }

    /** openid 路径：登录态天然带 openid，按 openid 查绑定顾客，查无即拒绝（不回退手机号） */
    private KioskCardLookupResult lookupByOpenid(KioskCardLookupRequest request, String mode) {
        String openid = request.getOpenid();
        if (openid == null || openid.isBlank()) {
            audit(null, null, mode, null, "missing_openid");
            throw new IllegalArgumentException("需要 openid（登录态）才能查卡");
        }
        CustomerEntity customer = customerRepository.findByOpenid(openid).orElse(null);
        if (customer == null) {
            audit(null, openid, mode, null, "no_binding");
            throw new IllegalArgumentException("该 openid 未绑定顾客档案，请到前台登记绑定");
        }
        audit(customer.getPhone(), openid, mode, customer.getId(), "ok");
        return buildResult(customer);
    }

    /** 短信路径：手机号+验证码双因子，验证码一次性，通过后按手机号查顾客（查无即拒绝） */
    private KioskCardLookupResult lookupBySms(KioskCardLookupRequest request, String mode) {
        String phone = request.getPhone();
        String submitted = request.getSmsCode();
        if (phone == null || phone.isBlank() || submitted == null || submitted.isBlank()) {
            audit(phone, phone, mode, null, "missing_code");
            throw new IllegalArgumentException("需要手机号与短信验证码才能查卡");
        }
        SmsCodeEntity issued = smsCodeRepository.findFirstByPhoneOrderByIdDesc(phone).orElse(null);
        if (issued == null) {
            audit(phone, phone, mode, null, "bad_code");
            throw new IllegalArgumentException("验证码不存在，请先获取验证码");
        }
        if (issued.getUsed() != null && issued.getUsed() == 1) {
            audit(phone, phone, mode, null, "used_code");
            throw new IllegalArgumentException("验证码已使用，请重新获取");
        }
        if (issued.getExpiresAt() != null && issued.getExpiresAt().before(new Date())) {
            audit(phone, phone, mode, null, "expired_code");
            throw new IllegalArgumentException("验证码已过期，请重新获取");
        }
        if (!issued.getCode().equals(submitted.trim())) {
            audit(phone, phone, mode, null, "bad_code");
            throw new IllegalArgumentException("验证码不对，请核对后重试");
        }
        issued.setUsed(1);
        smsCodeRepository.save(issued);

        CustomerEntity customer = customerRepository.findByPhone(phone).orElse(null);
        if (customer == null) {
            audit(phone, phone, mode, null, "no_customer");
            throw new IllegalArgumentException("该手机号无顾客档案，请到前台建档");
        }
        audit(phone, phone, mode, customer.getId(), "ok");
        return buildResult(customer);
    }

    /** 校验手机号格式并确认模式为 sms_verified（取码与查卡同一口径） */
    private void requireSmsMode(String phone, String operator) {
        String mode = phoneLookupMode;
        if ("off".equals(mode)) {
            audit(phone, operator, mode, null, "disabled");
            throw new IllegalArgumentException("在线查卡已关闭，请到店前台或致电门店查询");
        }
        if (!"sms_verified".equals(mode)) {
            audit(phone, operator, mode, null, "disabled");
            throw new IllegalArgumentException("当前查卡方式不支持短信验证码，请走 openid 登录态查询");
        }
        if (phone == null || !phone.matches("^1\\d{10}$")) {
            throw new IllegalArgumentException("手机号格式不对：应为 1 开头的 11 位数字");
        }
    }

    /** 持卡列表 + 卡项名快照（新卡在前，与顾客档案持卡栏同口径） */
    private KioskCardLookupResult buildResult(CustomerEntity customer) {
        List<KioskCardLookupResult.CardLine> lines = new ArrayList<>();
        for (CustomerCardEntity card : customerCardRepository.findByCustomerIdOrderByIdDesc(customer.getId())) {
            ItemEntity item = itemRepository.findById(card.getItemId()).orElse(null);
            lines.add(KioskCardLookupResult.CardLine.builder()
                    .cardId(card.getId())
                    .itemId(card.getItemId())
                    .itemName(item == null ? null : item.getName())
                    .totalTimes(card.getTotalTimes())
                    .remainingTimes(card.getRemainingTimes())
                    .status(card.getStatus())
                    .validUntil(card.getValidUntil())
                    .build());
        }
        return KioskCardLookupResult.builder()
                .customerId(customer.getId())
                .customerName(customer.getName())
                .cards(lines)
                .build();
    }

    /** 审计留痕：查了谁的卡（phone/customerId）、谁查的（operator）、模式、结果、时间（2026-10-14 裁定：两条路径都落） */
    private void audit(String phone, String operator, String mode, Long customerId, String result) {
        CardLookupLogEntity log = new CardLookupLogEntity();
        log.setPhone(phone);
        log.setOperator(operator);
        log.setMode(mode);
        log.setCustomerId(customerId);
        log.setResult(result);
        cardLookupLogRepository.save(log);
    }

    /** 按手机号找/建顾客（幂等），称呼缺省「到店客人」——下单与自助约期同一口径 */
    private CustomerEntity findOrCreateCustomer(String phone, String name) {
        return customerRepository.findByPhone(phone)
                .orElseGet(() -> {
                    CustomerEntity created = new CustomerEntity();
                    created.setPhone(phone);
                    created.setName(name == null || name.isBlank() ? "到店客人" : name.trim());
                    return customerRepository.save(created);
                });
    }
}
