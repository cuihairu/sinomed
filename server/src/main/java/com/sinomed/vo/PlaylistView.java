package com.sinomed.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
@Schema(title = "平板下发播放列表")
public class PlaylistView {
    @Schema(title = "内容戳", description = "素材/排期最近变更时间戳；平板比对后才重拉媒体", example = "1791302400000")
    private String version;

    @Schema(title = "屏id", example = "1")
    private Long screenId;

    @Schema(title = "屏标识", example = "PAD-01")
    private String code;

    @Schema(title = "最近心跳", description = "服务端记录的心跳时间")
    private Date lastSeenAt;

    @Schema(title = "播放序列")
    private List<PlaylistItem> items;

    @Builder
    @Data
    @Schema(title = "播放项")
    public static class PlaylistItem {
        @Schema(title = "素材id", example = "3")
        private Long materialId;

        @Schema(title = "素材名", example = "冬季三九贴活动")
        private String name;

        @Schema(title = "眉题（叠加层顶部小字，可空）", example = "冬病夏治")
        private String kicker;

        @Schema(title = "大标题（叠加层主文案，可空）", example = "三伏艾灸，借阳祛寒正当时")
        private String title;

        @Schema(title = "文案副题（叠加层说明行，可空）")
        private String copy;

        @Schema(title = "价格行（自由文本，可空）", example = "¥880 / 10 次")
        private String price;

        @Schema(title = "类型", description = "1 图片、2 视频", example = "1")
        private Integer type;

        @Schema(title = "媒体地址", example = "/media/202610/abc.png")
        private String url;

        @Schema(title = "停留时长(毫秒)", example = "8000")
        private Integer durationMs;

        @Schema(title = "轮播顺序", example = "10")
        private Integer sort;
    }
}
