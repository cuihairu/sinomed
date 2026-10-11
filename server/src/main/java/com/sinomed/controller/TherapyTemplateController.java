package com.sinomed.controller;

import com.sinomed.entity.TherapyTemplateEntity;
import com.sinomed.service.TherapyTemplateService;
import com.sinomed.vo.MessageView;
import com.sinomed.vo.PageResp;
import com.sinomed.vo.TherapyTemplateView;
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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 外治处方模板（需登录）：模板库 CRUD，接诊页「套用外治模板」取数入口。
 * 口径：模板名唯一（1–20 字）、方法必填（≤10 字）；模板只存建议值，
 * 套用后回填针灸处方区随单自由改（不写回模板）；停用（enabled=0）不在接诊页出现。
 */
@Tag(name = "外治处方模板", description = "外治处方模板API")
@RestController
@RequestMapping("/api/v1/therapy-template")
public class TherapyTemplateController {

    private final TherapyTemplateService templateService;

    public TherapyTemplateController(TherapyTemplateService templateService) {
        this.templateService = templateService;
    }

    @Operation(summary = "建模板", description = "name（1–20 字，唯一）/ method（≤10 字，如 耳穴压豆、穴位贴敷、艾灸）必填；"
            + "target（取穴/部位）/course（疗程/频次）/note（注意事项）各限 100 字可选；enabled（默认 1）",
            responses = {
                    @ApiResponse(responseCode = "200", description = "创建后的模板", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = TherapyTemplateView.class)
                    )),
                    @ApiResponse(responseCode = "400", description = "参数缺失 / 名称或方法越界 / 模板名重复", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    )),
                    @ApiResponse(responseCode = "401", description = "没有权限", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    ))
            })
    @PostMapping("/")
    public TherapyTemplateView create(@Validated @RequestBody TherapyTemplateView view) {
        return templateService.findById(templateService.save(view).getId());
    }

    @Operation(summary = "更新模板", description = "按 id 全量更新：id/name/method 必填；换名撞其他模板报 400",
            responses = {
                    @ApiResponse(responseCode = "200", description = "更新后的模板", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = TherapyTemplateView.class)
                    )),
                    @ApiResponse(responseCode = "400", description = "id 缺失 / 模板不存在 / 模板名重复", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    )),
                    @ApiResponse(responseCode = "401", description = "没有权限", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    ))
            })
    @PutMapping("/")
    public TherapyTemplateView update(@Validated @RequestBody TherapyTemplateView view) {
        return templateService.findById(templateService.update(view).getId());
    }

    @Operation(summary = "上架模板列表", description = "接诊页「套用外治模板」取数入口：enabled=1，按名称升序",
            responses = {
                    @ApiResponse(responseCode = "200", description = "上架模板", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = TherapyTemplateView.class)
                    )),
                    @ApiResponse(responseCode = "401", description = "没有权限", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    ))
            })
    @GetMapping("/enabled")
    public List<TherapyTemplateView> findEnabled() {
        return templateService.findEnabled();
    }

    @Operation(summary = "模板分页", description = "管理端列表：id 倒序；name 模糊 / method 精确过滤可选",
            responses = {
                    @ApiResponse(responseCode = "200", description = "分页模板", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = TherapyTemplateView.class)
                    )),
                    @ApiResponse(responseCode = "401", description = "没有权限", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    ))
            })
    @GetMapping("/page")
    public PageResp<TherapyTemplateView> findPage(
            @Parameter(description = "页码，1 起始") @Validated @NotNull @RequestParam int current,
            @Parameter(description = "每页条数") @Validated @NotNull @RequestParam int pageSize,
            @Parameter(description = "模板名模糊过滤，可空") @RequestParam(required = false) String name,
            @Parameter(description = "外治方法精确过滤，可空") @RequestParam(required = false) String method) {
        PageRequest pageRequest = PageRequest.of(current > 0 ? current - 1 : 0, pageSize > 0 ? pageSize : 10,
                Sort.by(Sort.Direction.DESC, "id"));
        Page<TherapyTemplateEntity> result = templateService.findPage(name, method, pageRequest);
        PageResp<TherapyTemplateView> resp = new PageResp<>();
        resp.data = result.map(TherapyTemplateView::FromEntity).getContent();
        resp.pages = result.getTotalPages();
        resp.total = result.getTotalElements();
        resp.success = true;
        return resp;
    }

    @Operation(summary = "模板详情", description = "不存在报 400",
            responses = {
                    @ApiResponse(responseCode = "200", description = "模板详情", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = TherapyTemplateView.class)
                    )),
                    @ApiResponse(responseCode = "400", description = "模板不存在", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    )),
                    @ApiResponse(responseCode = "401", description = "没有权限", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    ))
            })
    @GetMapping("/{id}")
    public TherapyTemplateView findById(@PathVariable Long id) {
        return templateService.findById(id);
    }

    @Operation(summary = "删除模板", description = "不存在报 400（演示环境口径，无留痕）",
            responses = {
                    @ApiResponse(responseCode = "200", description = "删除成功", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    )),
                    @ApiResponse(responseCode = "400", description = "模板不存在", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    )),
                    @ApiResponse(responseCode = "401", description = "没有权限", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    ))
            })
    @DeleteMapping("/{id}")
    public MessageView delete(@PathVariable Long id) {
        templateService.deleteById(id);
        MessageView messageView = new MessageView();
        messageView.setMessage("已删除");
        return messageView;
    }
}
