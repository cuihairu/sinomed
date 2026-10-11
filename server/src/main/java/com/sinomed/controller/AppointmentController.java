package com.sinomed.controller;

import com.sinomed.entity.AppointmentEntity;
import com.sinomed.entity.CustomerEntity;
import com.sinomed.entity.ItemEntity;
import com.sinomed.entity.StaffEntity;
import com.sinomed.repository.AppointmentRepository;
import com.sinomed.repository.CustomerRepository;
import com.sinomed.repository.ItemRepository;
import com.sinomed.repository.StaffRepository;
import com.sinomed.service.AppointmentService;
import com.sinomed.vo.AppointmentView;
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
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 预约管理（需登录）：前台/馆长建约、到店接待转接诊。
 */
@Tag(name = "预约", description = "预约API")
@RestController
@RequestMapping("/api/v1/appointment")
public class AppointmentController {

    private final AppointmentService appointmentService;
    private final AppointmentRepository appointmentRepository;
    private final CustomerRepository customerRepository;
    private final ItemRepository itemRepository;
    private final StaffRepository staffRepository;

    public AppointmentController(AppointmentService appointmentService,
                                 AppointmentRepository appointmentRepository,
                                 CustomerRepository customerRepository,
                                 ItemRepository itemRepository,
                                 StaffRepository staffRepository) {
        this.appointmentService = appointmentService;
        this.appointmentRepository = appointmentRepository;
        this.customerRepository = customerRepository;
        this.itemRepository = itemRepository;
        this.staffRepository = staffRepository;
    }


    @Operation(summary = "新建预约", description = "顾客+时段必填，卡项/员工/备注可选；新预约一律 status=0 待到店；每时段限约 1 位（同医师同日同小时已有待到店/已接待预约报 400）",
            responses = {
                    @ApiResponse(responseCode = "200", description = "创建后的预约", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AppointmentView.class)
                    )),
                    @ApiResponse(responseCode = "400", description = "参数缺失 / 顾客或卡项不存在", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    )),
                    @ApiResponse(responseCode = "401", description = "没有权限", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    ))
            })
    @PostMapping("/")
    public AppointmentView create(@Validated @RequestBody AppointmentView view) {
        if (view.getCustomerId() == null) {
            throw new IllegalArgumentException("需要顾客 id");
        }
        if (view.getStartTime() == null) {
            throw new IllegalArgumentException("需要预约时段 startTime");
        }
        customerRepository.findById(view.getCustomerId())
                .orElseThrow(() -> new IllegalArgumentException("顾客不存在：" + view.getCustomerId()));
        if (view.getItemId() != null) {
            itemRepository.findById(view.getItemId())
                    .orElseThrow(() -> new IllegalArgumentException("卡项不存在：" + view.getItemId()));
        }
        if (view.getStaffId() != null) {
            staffRepository.findById(view.getStaffId())
                    .orElseThrow(() -> new IllegalArgumentException("员工不存在：" + view.getStaffId()));
        }
        return AppointmentView.FromAppointmentEntity(appointmentService.save(view));
    }

    @Operation(summary = "预约分页", description = "管理端列表：联出顾客名/手机号、卡项名；status 可选过滤（0 待到店、1 已接待、8 过号、9 已取消），date 可选按日过滤",
            responses = {
                    @ApiResponse(responseCode = "200", description = "分页预约", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AppointmentView.class)
                    )),
                    @ApiResponse(responseCode = "401", description = "没有权限", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    ))
            })
    @GetMapping("/page")
    public PageResp<AppointmentView> findPage(
            @Parameter(description = "页码，1 起始") @Validated @NotNull @RequestParam int current,
            @Parameter(description = "每页条数") @Validated @NotNull @RequestParam int pageSize,
            @Parameter(description = "状态过滤，可空") @RequestParam(required = false) Integer status,
            @Parameter(description = "按日过滤（yyyy-MM-dd，可空）") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        PageRequest pageRequest = PageRequest.of(current > 0 ? current - 1 : 0, pageSize > 0 ? pageSize : 10);
        Page<AppointmentEntity> result;
        if (date != null) {
            Date start = Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
            Date end = Date.from(date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
            result = status == null
                    ? appointmentRepository.findByStartTimeBetween(start, end, pageRequest)
                    : appointmentRepository.findByStartTimeBetweenAndStatus(start, end, status, pageRequest);
        } else {
            result = appointmentService.findPage(status, pageRequest);
        }

        Map<Long, CustomerEntity> customers = customerRepository.findAllById(
                        result.map(AppointmentEntity::getCustomerId).toSet()).stream()
                .collect(Collectors.toMap(CustomerEntity::getId, Function.identity()));
        Map<Long, ItemEntity> items = itemRepository.findAllById(
                        result.map(AppointmentEntity::getItemId).toSet()).stream()
                .collect(Collectors.toMap(ItemEntity::getId, Function.identity()));

        PageResp<AppointmentView> resp = new PageResp<>();
        resp.data = result.map(this::toView).getContent();
        resp.pages = result.getTotalPages();
        resp.total = result.getTotalElements();
        resp.success = true;
        return resp;
    }

    @Operation(summary = "状态流转", description = "只允许 0→1（到店接待，随后从接诊页建单）、0→8（过号）与 0→9（取消）；过号/取消为终态",
            responses = {
                    @ApiResponse(responseCode = "200", description = "流转后的预约", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AppointmentView.class)
                    )),
                    @ApiResponse(responseCode = "400", description = "非法流转 / 预约不存在", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    ))
            })
    @PutMapping("/status")
    public AppointmentView updateStatus(@Validated @RequestBody AppointmentView view) {
        if (view.getId() == null || view.getStatus() == null) {
            throw new IllegalArgumentException("需要预约 id 与目标 status");
        }
        return AppointmentView.FromAppointmentEntity(appointmentService.updateStatus(view.getId(), view.getStatus()));
    }

    @Operation(summary = "删除预约", description = "仅已取消（status=9）的预约可删",
            responses = {
                    @ApiResponse(responseCode = "200", description = "删除成功", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    )),
                    @ApiResponse(responseCode = "400", description = "预约不存在或未取消", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    ))
            })
    @DeleteMapping("/{id}")
    public MessageView delete(@PathVariable Long id) {
        AppointmentEntity appointment = appointmentService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("预约不存在：" + id));
        if (appointment.getStatus() == null || appointment.getStatus() != 9) {
            throw new IllegalArgumentException("只有已取消的预约可删");
        }
        appointmentService.deleteById(id);
        return MessageView.builder().message("删除成功").build();
    }

    /** 单条视图 + 联出展示字段 */
    private AppointmentView toView(AppointmentEntity appointment) {
        AppointmentView view = AppointmentView.FromAppointmentEntity(appointment);
        customerRepository.findById(appointment.getCustomerId()).ifPresent(c -> {
            view.setCustomerName(c.getName());
            view.setCustomerPhone(c.getPhone());
        });
        if (appointment.getItemId() != null) {
            itemRepository.findById(appointment.getItemId()).ifPresent(i -> view.setItemName(i.getName()));
        }
        if (appointment.getStaffId() != null) {
            staffRepository.findById(appointment.getStaffId()).ifPresent(s -> view.setStaffName(s.getName()));
        }
        return view;
    }
}
