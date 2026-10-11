package com.sinomed.controller;

import com.sinomed.entity.AppointmentEntity;
import com.sinomed.entity.CustomerEntity;
import com.sinomed.entity.ItemEntity;
import com.sinomed.entity.OrderEntity;
import com.sinomed.repository.AppointmentRepository;
import com.sinomed.repository.CustomerRepository;
import com.sinomed.repository.ItemRepository;
import com.sinomed.service.AppointmentService;
import com.sinomed.service.OrderService;
import com.sinomed.vo.ExceptionView;
import com.sinomed.vo.KioskAppointmentRequest;
import com.sinomed.vo.KioskAppointmentResult;
import com.sinomed.vo.KioskItemView;
import com.sinomed.vo.KioskOrderRequest;
import com.sinomed.vo.KioskOrderResult;
import com.sinomed.vo.MessageView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 顾客选服务 Kiosk（免登录，内网设备约定，见 docs/design/kiosk.md）：
 * 浏览纯只读；下单与自助约期是两个写入口，写范围限定一条顾客档案（按手机号幂等）+ 待接待订单/预约。
 * 自助约期口径见 docs/design/app-booking.md（频控 1 条/日/手机号、整点 09:00–17:00、时长固定 60 分钟）。
 */
@Tag(name = "顾客选服务 Kiosk", description = "到店自助浏览卡项与下单（免登录）")
@RestController
@RequestMapping("/api/v1/kiosk")
public class KioskController {

    /** 自助约期可约时段（整点起始，含 17:00 对齐排班网格最后一格） */
    private static final int BOOKABLE_HOUR_FIRST = 9;
    private static final int BOOKABLE_HOUR_LAST = 17;

    private final ItemRepository itemRepository;
    private final CustomerRepository customerRepository;
    private final OrderService orderService;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentService appointmentService;

    public KioskController(ItemRepository itemRepository,
                           CustomerRepository customerRepository,
                           OrderService orderService,
                           AppointmentRepository appointmentRepository,
                           AppointmentService appointmentService) {
        this.itemRepository = itemRepository;
        this.customerRepository = customerRepository;
        this.orderService = orderService;
        this.appointmentRepository = appointmentRepository;
        this.appointmentService = appointmentService;
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
