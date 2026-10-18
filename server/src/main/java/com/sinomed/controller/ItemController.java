package com.sinomed.controller;

import com.sinomed.entity.ItemEntity;
import com.sinomed.entity.StaffEntity;
import com.sinomed.service.ItemService;
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
import org.springframework.data.domain.Sort;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@Tag(name = "商品", description = "商品API")
@RestController
@RequestMapping("/api/v1/item")
public class ItemController {

    private final ItemService itemService;

    public ItemController(ItemService itemService) {
        this.itemService = itemService;
    }

    @Operation(summary = "创建新商品", description = "创建一个新商品",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ItemView.class)
            ), required = true),
            responses = {
                    @ApiResponse(responseCode = "200", description = "商品表", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ItemView.class)
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
    public ItemView createItem(@Validated @RequestBody ItemView itemView) {
        ItemEntity save = itemService.save(itemView.ToItemEntity());
        return ItemView.FromItemEntity(save);
    }

    @Operation(summary = "更新新商品信息", description = "更新一个商品信息",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ItemView.class)
            ), required = true),
            responses = {
                    @ApiResponse(responseCode = "200", description = "商品表", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ItemView.class)
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
    public ItemView updateItem(@Validated @RequestBody ItemView itemView) {
        if (itemView.getId() == null) {
            throw new IllegalArgumentException("商品Id不能为空");
        }
        itemService.findById(itemView.getId()).orElseThrow(() -> new IllegalArgumentException("商品不存在"));
        ItemEntity save = itemService.save(itemView.ToItemEntity());
        return ItemView.FromItemEntity(save);
    }

    @Operation(summary = "获取所有商品信息", description = "获取所有商品信息"
            , responses = {
            @ApiResponse(responseCode = "200", description = "商品表", content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ItemView.class)
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

    @GetMapping("/page")
    public PageResp<ItemView> findByPage(@Validated @NotNull @RequestParam int current, @Validated @NotNull @RequestParam int pageSize, @Validated @Nullable @RequestParam String name, @Validated @Nullable @RequestParam Integer price, @Validated @Nullable @RequestParam Integer category){
        // 排序字段小者在前（原型 admin-items 口径），同序号 id 倒序稳定分页
        PageRequest page = PageRequest.of(current>0?current-1:0, pageSize>0?pageSize:1,
                Sort.by(Sort.Direction.ASC, "sort").and(Sort.by(Sort.Direction.DESC, "id")));
        // 过滤（Specification，同 StaffController 口径）：name 包含（不区分大小写）、price/category 精确，null 即不过滤
        Page<ItemEntity> allByPage = itemService.findAllByPage(name, price, category, page);
        List<ItemView> data = new ArrayList<>();
        for (ItemEntity item : allByPage) {
            data.add(ItemView.FromItemEntity(item));
        }
        PageResp<ItemView> staffViewPageResp = new PageResp<ItemView>();
        staffViewPageResp.data = data;
        staffViewPageResp.pages = allByPage.getTotalPages();
        staffViewPageResp.total = allByPage.getTotalElements();
        staffViewPageResp.success = true;
        return staffViewPageResp;
    }
    @Operation(summary = "根据商品Id查询商品信息", description = "查询一个商品信息", parameters = {
            @Parameter(name = "id", description = "商品Id")
    }, responses = {
            @ApiResponse(responseCode = "200", description = "商品表", content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ItemView.class)
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
    public ItemView findItemById(@PathVariable Long id) {
        ItemEntity itemEntity = itemService.findById(id).orElseThrow(() -> new IllegalArgumentException("商品不存在"));
        return ItemView.FromItemEntity(itemEntity);
    }

    @Operation(summary = "根据商品名字查询商品信息", description = "查询一个商品信息", parameters = {
            @Parameter(name = "name", description = "商品名字")
    }, responses = {
            @ApiResponse(responseCode = "200", description = "商品表", content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ItemView.class)
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
    public ItemView findItemByName(@PathVariable String name) {
        ItemEntity itemEntity = itemService.findByName(name).orElseThrow(() -> new IllegalArgumentException("商品不存在"));
        return ItemView.FromItemEntity(itemEntity);
    }

    @Operation(summary = "删除商品信息", description = "删除一个商品信息",
            responses = {
                    @ApiResponse(responseCode = "200", description = "商品表", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ItemView.class)
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
    @DeleteMapping("/{id}")
    public ItemView deleteItem(@PathVariable @NotNull Long id) {
        ItemEntity itemEntity = itemService.findById(id).orElseThrow(() -> new IllegalArgumentException("商品不存在"));
        itemService.deleteById(id);
        return ItemView.FromItemEntity(itemEntity);
    }
}
