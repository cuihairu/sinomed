package com.sinomed.controller;

import com.sinomed.entity.CustomerEntity;
import com.sinomed.entity.CustomerHistoryEntity;
import com.sinomed.entity.CustomerTagEntity;
import com.sinomed.repository.CustomerTagRepository;
import com.sinomed.service.CustomerService;
import com.sinomed.service.StaffService;
import com.sinomed.util.PhoneValidationUtil;
import com.sinomed.vo.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Tag(name = "客人", description = "客人API")
@RestController
@RequestMapping("/api/v1/customer")
public class CustomerController {

    private final CustomerService customerService;
    private final CustomerTagRepository customerTagRepository;
    private final StaffService staffService;

    public CustomerController(CustomerService customerService,
                              CustomerTagRepository customerTagRepository,
                              StaffService staffService) {
        this.customerService = customerService;
        this.customerTagRepository = customerTagRepository;
        this.staffService = staffService;
    }


    @Operation(summary = "根据ID查询客人", description = "根据客人的id来查询客人信息",
            parameters = {
                    @Parameter(name = "id", description = "客人Id")
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "客人表", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CustomerView.class)
                    )),
                    @ApiResponse(responseCode = "400", description = "参数错误", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    )),
                    @ApiResponse(responseCode = "401", description = "没有权限", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    )),
                    @ApiResponse(responseCode = "500", description = "服务器参数", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ExceptionView.class)
                    ))
            })
    @GetMapping("/{id}")
    public CustomerView findUserById(@PathVariable("id") Long id) throws IllegalArgumentException {
        Optional<CustomerEntity> customerEntity = customerService.findById(id);
        if (customerEntity.isEmpty()) {
            throw new IllegalArgumentException("顾客信息不存在,请先建档");
        }
        return CustomerView.FromCustomerEntity(customerEntity.get());
    }

    @Operation(summary = "根据手机号码查询客人", description = "根据手机号码查询客人信息",
            parameters = {
                    @Parameter(name = "phone", description = "客人手机号码")
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "客人表", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CustomerView.class)
                    )),
                    @ApiResponse(responseCode = "400", description = "参数错误", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    )),
                    @ApiResponse(responseCode = "401", description = "没有权限", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    )),
                    @ApiResponse(responseCode = "500", description = "服务器参数", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ExceptionView.class)
                    ))
            })
    @GetMapping("/phone/{phone}")
    public CustomerView findUserByPhone(@PathVariable("phone") String phone) throws IllegalArgumentException {
        Optional<CustomerEntity> userEntity = customerService.findByPhone(phone);
        if (userEntity.isEmpty()) {
            throw new IllegalArgumentException("当前号码没有注册");
        }
        return CustomerView.FromCustomerEntity(userEntity.get());
    }

    @GetMapping("/page")
    public PageResp<CustomerView> findByPage(@Validated @NotNull @RequestParam int current, @Validated @NotNull @RequestParam int pageSize,@Validated @Nullable @RequestParam String name, @Validated @Nullable @RequestParam Integer age,@Validated @Nullable @RequestParam String phone,@Validated @Nullable @RequestParam String startTime,@Validated @Nullable @RequestParam String endTime){
        PageRequest page = PageRequest.of(current>0?current-1:0, pageSize>0?pageSize:1);
        Page<CustomerEntity> allByPage = customerService.findAllByPage(name,age,phone,startTime,endTime,page);
        List<CustomerView> data = new ArrayList<>();
        for (CustomerEntity customer : allByPage) {
            data.add(CustomerView.FromCustomerEntity(customer));
        }
        PageResp<CustomerView> customerViewPageResp = new PageResp<CustomerView>();
        customerViewPageResp.data = data;
        customerViewPageResp.pages = allByPage.getTotalPages();
        customerViewPageResp.total = allByPage.getTotalElements();
        customerViewPageResp.success = true;
        return customerViewPageResp;
    }

    @Operation(summary = "根据名字号码查询客人",
            description = "根据名字码查询客人信息",
            parameters = {
                    @Parameter(name = "name", description = "客人名字")
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "客人表", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CustomerView.class)
                    )),
                    @ApiResponse(responseCode = "400", description = "参数错误", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    )),
                    @ApiResponse(responseCode = "401", description = "没有权限", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    )),
                    @ApiResponse(responseCode = "500", description = "服务器参数", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ExceptionView.class)
                    ))
            })
    @GetMapping("/name/{name}")
    public List<CustomerView> findUserByName(@PathVariable("name") String name) throws IllegalArgumentException {
        List<CustomerEntity> userEntities = customerService.findByName(name);
        if (userEntities.isEmpty()) {
            throw new IllegalArgumentException("没有找到对应名字的客人");
        }
        List<CustomerView> ret = new ArrayList<>();
        for (CustomerEntity userEntity : userEntities) {
            ret.add(CustomerView.FromCustomerEntity(userEntity));
        }
        return ret;
    }

    @Operation(summary = "顾客病史列表", description = "过敏史/既往史记录，新记录在前；顾客不存在报 400")
    @GetMapping("/{id}/history")
    public List<CustomerHistoryView> listHistory(@PathVariable("id") Long id) throws IllegalArgumentException {
        List<CustomerHistoryView> ret = new ArrayList<>();
        for (CustomerHistoryEntity entity : customerService.listHistories(id)) {
            ret.add(CustomerHistoryView.FromEntity(entity));
        }
        return ret;
    }

    @Operation(summary = "新增病史", description = "type 0 过敏 / 1 既往；content 非空且 ≤200 字，顾客不存在或类型无效报 400",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = CustomerHistoryView.class)
            ), required = true))
    @PostMapping("/{id}/history")
    public CustomerHistoryView addHistory(@PathVariable("id") Long id, @Validated @RequestBody CustomerHistoryView view)
            throws IllegalArgumentException {
        return CustomerHistoryView.FromEntity(customerService.addHistory(id, view.getType(), view.getContent()));
    }

    @Operation(summary = "删除病史", description = "按病史记录 id 删除；演示环境口径，无留痕")
    @DeleteMapping("/history/{historyId}")
    public MessageView deleteHistory(@PathVariable("historyId") Long historyId) throws IllegalArgumentException {
        customerService.deleteHistory(historyId);
        return MessageView.builder().message("删除成功").build();
    }

    // ---------- 客群标签（美容业态 d，docs/design/salon-extension.md §2-d） ----------

    @Operation(summary = "顾客标签列表", description = "肤质/偏好等轻量标签，新加的在前；过敏史不在标签里（走病史 type 0）",
            responses = @ApiResponse(responseCode = "200", description = "标签数组", content = @Content(
                    mediaType = "application/json", schema = @Schema(implementation = CustomerTagView.class))))
    @GetMapping("/{id}/tag")
    public List<CustomerTagView> listTags(@PathVariable("id") Long id) throws IllegalArgumentException {
        customerService.findById(id).orElseThrow(() -> new IllegalArgumentException("顾客不存在：" + id));
        return customerTagRepository.findByCustomerIdOrderByIdDesc(id).stream()
                .map(CustomerTagView::FromEntity).toList();
    }

    @Operation(summary = "加顾客标签", description = "body { tag }：≤20 字；同顾客同标签唯一（重复 400）",
            responses = {
                    @ApiResponse(responseCode = "200", description = "标签", content = @Content(
                            mediaType = "application/json", schema = @Schema(implementation = CustomerTagView.class))),
                    @ApiResponse(responseCode = "400", description = "参数错误 / 重复", content = @Content(
                            mediaType = "application/json", schema = @Schema(implementation = MessageView.class)))
            })
    @PostMapping("/{id}/tag")
    public CustomerTagView addTag(@PathVariable("id") Long id, @RequestBody Map<String, Object> body)
            throws IllegalArgumentException {
        customerService.findById(id).orElseThrow(() -> new IllegalArgumentException("顾客不存在：" + id));
        String tag = body.get("tag") == null ? null : body.get("tag").toString().trim();
        if (tag == null || tag.isEmpty()) {
            throw new IllegalArgumentException("标签不能为空");
        }
        if (tag.length() > 20) {
            throw new IllegalArgumentException("标签限 20 字内");
        }
        customerTagRepository.findByCustomerIdAndTag(id, tag).ifPresent(t -> {
            throw new IllegalArgumentException("该顾客已有此标签：" + tag);
        });
        CustomerTagEntity entity = new CustomerTagEntity();
        entity.setCustomerId(id);
        entity.setTag(tag);
        return CustomerTagView.FromEntity(customerTagRepository.save(entity));
    }

    @Operation(summary = "删顾客标签", description = "按标签 id 删除",
            responses = @ApiResponse(responseCode = "200", description = "删除成功", content = @Content(
                    mediaType = "application/json", schema = @Schema(implementation = MessageView.class))))
    @DeleteMapping("/tag/{tagId}")
    public MessageView deleteTag(@PathVariable("tagId") Long tagId) {
        customerTagRepository.deleteById(tagId);
        return MessageView.builder().message("删除成功").build();
    }

    @Operation(summary = "美容敏感信息单独同意", description = "PIPL 口径（docs/design/salon-extension.md §4，随 d 同批）："
            + "body { consent: 1/0 }；记同意标志 + 时刻 + 经办员工（登录人），档案更新不抹掉",
            responses = {
                    @ApiResponse(responseCode = "200", description = "顾客", content = @Content(
                            mediaType = "application/json", schema = @Schema(implementation = CustomerView.class))),
                    @ApiResponse(responseCode = "400", description = "顾客不存在", content = @Content(
                            mediaType = "application/json", schema = @Schema(implementation = MessageView.class)))
            })
    @PutMapping("/{id}/consent")
    public CustomerView consent(@PathVariable("id") Long id, @RequestBody Map<String, Object> body)
            throws IllegalArgumentException {
        CustomerEntity customer = customerService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("顾客不存在：" + id));
        int consent = body.get("consent") != null && "1".equals(body.get("consent").toString()) ? 1 : 0;
        customer.setBeautyConsent(consent);
        customer.setBeautyConsentTime(new Date());
        customer.setBeautyConsentStaffId(currentStaffId());
        return CustomerView.FromCustomerEntity(customerService.save(customer));
    }

    /** 登录员工 id（同意记录经办人） */
    private Long currentStaffId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null) {
            return null;
        }
        return staffService.findByAccount(auth.getName()).map(s -> s.getId()).orElse(null);
    }

    @Operation(summary = "创建客人",
            description = "创建一个新客人",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = CustomerView.class)
            ), required = true),
            responses = {
                    @ApiResponse(responseCode = "200", description = "客人表", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CustomerView.class)
                    )),
                    @ApiResponse(responseCode = "400", description = "参数错误", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    )),
                    @ApiResponse(responseCode = "401", description = "没有权限", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    )),
                    @ApiResponse(responseCode = "500", description = "服务器参数", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ExceptionView.class)
                    ))
            })
    @PostMapping("/")
    public CustomerView creatCustomer(@Validated @RequestBody CustomerView customerView) throws IllegalArgumentException {
        if (customerView.getGender() == null) {
            throw new IllegalArgumentException("请输入选择客人的性别");
        }
        String phone = customerView.getPhone();
        if (phone == null || phone.isEmpty()) {
            CustomerEntity save = customerService.save(customerView.toCustomerEntity());
            return CustomerView.FromCustomerEntity(save);
        }
        if (customerView.getAge() == null){
            customerView.setAge(20);
        }
        if (customerView.getLevel() == null){
            customerView.setLevel(0);
        }
        if (customerView.getBirthday() == null){
            customerView.setBirthday(new Date());
        }
        phone = phone.trim();
        if (phone.startsWith("+86")){
            phone = phone.substring(3);
        }
        if (!PhoneValidationUtil.isPhone(phone)){
            throw new IllegalArgumentException("请输入正确的手机号码");
        }
        customerService.findByPhone(phone).ifPresent(staffEntity -> {
            throw new IllegalArgumentException("该手机号码已经绑定了顾客");
        });
        customerView.setPhone(phone);

        CustomerEntity save = customerService.save(customerView.toCustomerEntity());
        return CustomerView.FromCustomerEntity(save);
    }

    @Operation(summary = "更新客人",
            description = "根据用户的id来更新客人信息",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = TreatView.class)
            ), required = true),
            responses = {
                    @ApiResponse(responseCode = "200", description = "客人表", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CustomerView.class)
                    )),
                    @ApiResponse(responseCode = "400", description = "参数错误", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    )),
                    @ApiResponse(responseCode = "401", description = "没有权限", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    )),
                    @ApiResponse(responseCode = "500", description = "服务器参数", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ExceptionView.class)
                    ))
            })
    @PutMapping("/")
    public CustomerView updateUser(@Validated @RequestBody CustomerView customerView) throws IllegalArgumentException {
        Long id = customerView.getId();
        if (id == null || id < 0){
            throw new IllegalArgumentException("用户id错误，请传入正确的id");
        }
        CustomerEntity customerEntity = customerService.findById(id).orElseThrow(()->{
            throw new IllegalArgumentException("用户不存在");
        });
        customerView.updateCustomerEntity(customerEntity);
        CustomerEntity save = customerService.save(customerEntity);
        return CustomerView.FromCustomerEntity(save);
    }

    @Operation(summary = "删除客人", description = "根据用户的id来删除客人信息")
    @DeleteMapping("/{id}")
    public MessageView deleteUser(@PathVariable("id") Long id) {
        Optional<CustomerEntity> userEntity = customerService.findById(id);
        if (userEntity.isEmpty()) {
            return MessageView.builder().message("用户不存在").build();
        }
        customerService.deleteById(id);
        return MessageView.builder().message("删除成功").build();
    }

}
