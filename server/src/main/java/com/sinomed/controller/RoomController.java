package com.sinomed.controller;

import com.sinomed.entity.RoomEntity;
import com.sinomed.repository.RoomRepository;
import com.sinomed.vo.MessageView;
import com.sinomed.vo.RoomView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Sort;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 工位/房间字典 CRUD（美容业态 b，docs/design/salon-extension.md §2-b）。
 * 纯字典表无业务规则，控制器直接走仓库；预约侧消费见 AppointmentController / AppointmentServiceImpl。
 */
@Tag(name = "工位", description = "工位API")
@RestController
@RequestMapping("/api/v1/room")
@Validated
public class RoomController {

    private final RoomRepository roomRepository;

    public RoomController(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    @Operation(summary = "工位列表", description = "全量按 id 升序；停用工位保留展示（历史预约仍引用）",
            responses = {
                    @ApiResponse(responseCode = "200", description = "工位数组", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = RoomView.class)
                    ))
            })
    @GetMapping("/")
    public List<RoomView> list() {
        return roomRepository.findAll(Sort.by("id")).stream()
                .map(RoomView::FromRoomEntity).toList();
    }

    @Operation(summary = "工位详情", description = "按 id 取工位",
            responses = {
                    @ApiResponse(responseCode = "200", description = "工位", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = RoomView.class)
                    )),
                    @ApiResponse(responseCode = "400", description = "工位不存在", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    ))
            })
    @GetMapping("/{id}")
    public RoomView get(@PathVariable Long id) {
        return roomRepository.findById(id).map(RoomView::FromRoomEntity)
                .orElseThrow(() -> new IllegalArgumentException("工位不存在：" + id));
    }

    @Operation(summary = "新建工位", description = "name 必填且唯一；enabled 缺省 1 在用",
            responses = {
                    @ApiResponse(responseCode = "200", description = "创建后的工位", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = RoomView.class)
                    )),
                    @ApiResponse(responseCode = "400", description = "参数错误", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    ))
            })
    @PostMapping("/")
    public RoomView create(@Validated @RequestBody RoomView roomView) {
        roomRepository.findByName(roomView.getName()).ifPresent(r -> {
            throw new IllegalArgumentException("工位名已存在：" + roomView.getName());
        });
        return RoomView.FromRoomEntity(roomRepository.save(roomView.ToRoomEntity()));
    }

    @Operation(summary = "更新工位", description = "id 必填；改名/停用（enabled=0）皆走此接口；停用不影响历史预约",
            responses = {
                    @ApiResponse(responseCode = "200", description = "更新后的工位", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = RoomView.class)
                    )),
                    @ApiResponse(responseCode = "400", description = "工位不存在或参数错误", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    ))
            })
    @PutMapping("/")
    public RoomView update(@Validated @RequestBody RoomView roomView) {
        if (roomView.getId() == null) {
            throw new IllegalArgumentException("需要工位 id");
        }
        RoomEntity room = roomRepository.findById(roomView.getId())
                .orElseThrow(() -> new IllegalArgumentException("工位不存在：" + roomView.getId()));
        room.setName(roomView.getName() != null ? roomView.getName() : room.getName());
        room.setEnabled(roomView.getEnabled() != null ? roomView.getEnabled() : room.getEnabled());
        return RoomView.FromRoomEntity(roomRepository.save(room));
    }

    @Operation(summary = "删除工位", description = "硬删；历史预约的 room_name 联表查不到时显示为空",
            responses = {
                    @ApiResponse(responseCode = "200", description = "删除成功", content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageView.class)
                    ))
            })
    @DeleteMapping("/{id}")
    public MessageView delete(@PathVariable Long id) {
        roomRepository.deleteById(id);
        return MessageView.builder().message("删除成功").build();
    }
}
