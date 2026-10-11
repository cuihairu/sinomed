package com.sinomed.service.impl;

import com.sinomed.entity.AdMaterialEntity;
import com.sinomed.entity.AdScheduleEntity;
import com.sinomed.entity.AdScreenEntity;
import com.sinomed.repository.AdMaterialRepository;
import com.sinomed.repository.AdScheduleRepository;
import com.sinomed.repository.AdScreenRepository;
import com.sinomed.service.AdsService;
import com.sinomed.vo.MediaUploadView;
import com.sinomed.vo.PlaylistView;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class AdsServiceImpl implements AdsService {
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");
    private static final Map<String, Integer> IMAGE_EXT = Map.of("png", 1, "jpg", 1, "jpeg", 1, "webp", 1, "gif", 1);
    private static final Map<String, Integer> VIDEO_EXT = Map.of("mp4", 2, "webm", 2, "mov", 2, "m4v", 2);

    private final AdMaterialRepository materialRepository;
    private final AdScreenRepository screenRepository;
    private final AdScheduleRepository scheduleRepository;

    /** 媒体落盘目录（本地相对路径；生产由 Nginx /media/ 段托管同目录） */
    @Value("${sinomed.ads.upload-dir:data/ads}")
    private String uploadDir;

    /** 对外媒体前缀，与 WebMvcConfig 的静态映射一致 */
    @Value("${sinomed.ads.url-prefix:/media}")
    private String urlPrefix;

    public AdsServiceImpl(AdMaterialRepository materialRepository,
                          AdScreenRepository screenRepository,
                          AdScheduleRepository scheduleRepository) {
        this.materialRepository = materialRepository;
        this.screenRepository = screenRepository;
        this.scheduleRepository = scheduleRepository;
    }

    // ---------- 素材 ----------

    @Override
    public Page<AdMaterialEntity> findMaterials(AdMaterialEntity example, Pageable pageable) {
        return materialRepository.findAll(Example.of(example), pageable);
    }

    @Override
    public Optional<AdMaterialEntity> findMaterialById(Long id) {
        return materialRepository.findById(id);
    }

    @Override
    public AdMaterialEntity saveMaterial(AdMaterialEntity entity) {
        if (entity.getEnabled() == null) entity.setEnabled(1);
        if (entity.getSort() == null) entity.setSort(0);
        preserveMaterialAudit(entity);
        return materialRepository.save(entity);
    }

    @Override
    public void deleteMaterialById(Long id) {
        materialRepository.deleteById(id);
    }

    @Override
    public MediaUploadView uploadMaterial(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件为空");
        }
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String ext = original.contains(".")
                ? original.substring(original.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT)
                : "";
        if (!IMAGE_EXT.containsKey(ext) && !VIDEO_EXT.containsKey(ext)) {
            throw new IllegalArgumentException("不支持的文件类型：" + ext + "（仅图片 png/jpg/webp/gif，视频 mp4/webm/mov）");
        }
        String relative = Paths.get(
                LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM")),
                UUID.randomUUID() + "." + ext
        ).toString().replace('\\', '/');
        Path target = Paths.get(uploadDir, relative);
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
        } catch (IOException e) {
            throw new IllegalStateException("媒体落盘失败：" + e.getMessage(), e);
        }
        log.info("ads media uploaded: {} -> {} ({} bytes)", original, relative, file.getSize());
        return MediaUploadView.builder()
                .url(urlPrefix + "/" + relative)
                .originalName(original)
                .size(file.getSize())
                .build();
    }

    // ---------- 屏 ----------

    @Override
    public Page<AdScreenEntity> findScreens(AdScreenEntity example, Pageable pageable) {
        return screenRepository.findAll(Example.of(example), pageable);
    }

    @Override
    public Optional<AdScreenEntity> findScreenById(Long id) {
        return screenRepository.findById(id);
    }

    @Override
    public AdScreenEntity saveScreen(AdScreenEntity entity) {
        if (entity.getEnabled() == null) entity.setEnabled(1);
        screenRepository.findByCode(entity.getCode())
                .filter(other -> !other.getId().equals(entity.getId()))
                .ifPresent(other -> {
                    throw new IllegalArgumentException("屏标识已存在：" + entity.getCode());
                });
        preserveScreenAudit(entity);
        return screenRepository.save(entity);
    }

    @Override
    public void deleteScreenById(Long id) {
        screenRepository.deleteById(id);
    }

    // ---------- 排期 ----------

    @Override
    public Page<AdScheduleEntity> findSchedules(AdScheduleEntity example, Pageable pageable) {
        return scheduleRepository.findAll(Example.of(example), pageable);
    }

    @Override
    public Optional<AdScheduleEntity> findScheduleById(Long id) {
        return scheduleRepository.findById(id);
    }

    @Override
    public AdScheduleEntity saveSchedule(AdScheduleEntity entity) {
        if (entity.getEnabled() == null) entity.setEnabled(1);
        screenRepository.findById(entity.getScreenId())
                .orElseThrow(() -> new IllegalArgumentException("屏不存在：" + entity.getScreenId()));
        materialRepository.findById(entity.getMaterialId())
                .orElseThrow(() -> new IllegalArgumentException("素材不存在：" + entity.getMaterialId()));
        preserveScheduleAudit(entity);
        return scheduleRepository.save(entity);
    }

    @Override
    public void deleteScheduleById(Long id) {
        scheduleRepository.deleteById(id);
    }

    // ---------- 下发 ----------

    @Override
    public PlaylistView playlist(String screenCode) {
        if (screenCode == null || screenCode.isBlank()) {
            throw new IllegalArgumentException("缺少 screen 参数");
        }
        AdScreenEntity screen = screenRepository.findByCodeAndEnabled(screenCode, 1)
                .orElseThrow(() -> new IllegalArgumentException("屏不存在或已停用：" + screenCode));

        // 心跳
        screen.setLastSeenAt(new Date());
        screenRepository.save(screen);

        // 命中排期 → 素材；无命中 → 该屏全部启用素材兜底
        LocalDate today = LocalDate.now();
        String weekday = String.valueOf(today.getDayOfWeek().getValue());
        String now = LocalTime.now().format(HHMM);
        List<AdScheduleEntity> hits = scheduleRepository.findByScreenIdAndEnabled(screen.getId(), 1).stream()
                .filter(s -> matchesWeekdays(s.getWeekdays(), weekday))
                .filter(s -> matchesTimeRange(s.getStartTime(), s.getEndTime(), now))
                .toList();

        List<AdMaterialEntity> materials;
        if (hits.isEmpty()) {
            materials = materialRepository.findByEnabledOrderBySortAscIdAsc(1);
        } else {
            Set<Long> materialIds = new LinkedHashSet<>();
            hits.forEach(s -> materialIds.add(s.getMaterialId()));
            materials = materialRepository.findByIdInAndEnabledOrderBySortAscIdAsc(new ArrayList<>(materialIds), 1);
        }

        List<PlaylistView.PlaylistItem> items = materials.stream().map(m ->
                PlaylistView.PlaylistItem.builder()
                        .materialId(m.getId())
                        .name(m.getName())
                        .kicker(m.getKicker())
                        .title(m.getTitle())
                        .copy(m.getCopy())
                        .price(m.getPrice())
                        .type(m.getType())
                        .url(m.getUrl())
                        .durationMs(m.getDurationMs())
                        .sort(m.getSort())
                        .build()
        ).toList();

        return PlaylistView.builder()
                .version(contentVersion())
                .screenId(screen.getId())
                .code(screen.getCode())
                .lastSeenAt(screen.getLastSeenAt())
                .items(items)
                .build();
    }

    /**
     * 内容戳：素材/排期最近一次变更时间（毫秒），无数据为 0。
     * 平板比对 version 决定是否重拉媒体；屏改名不参与（不影响播放内容）。
     */
    private String contentVersion() {
        long materialVersion = materialRepository.findTopByOrderByUpdateTimeDesc()
                .map(m -> m.getUpdateTime() == null ? 0L : m.getUpdateTime().getTime())
                .orElse(0L);
        long scheduleVersion = scheduleRepository.findTopByOrderByUpdateTimeDesc()
                .map(s -> s.getUpdateTime() == null ? 0L : s.getUpdateTime().getTime())
                .orElse(0L);
        return String.valueOf(Math.max(materialVersion, scheduleVersion));
    }

    private boolean matchesWeekdays(String weekdays, String weekday) {
        if (weekdays == null || weekdays.isBlank()) {
            return true;
        }
        for (String token : weekdays.split(",")) {
            if (token.trim().equals(weekday)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesTimeRange(String startTime, String endTime, String now) {
        int nowMinute = toMinute(now, 0);
        int start = toMinute(startTime, 0);          // 空 = 00:00
        int end = toMinute(endTime, 24 * 60);        // 空 = 24:00
        return nowMinute >= start && nowMinute <= end;
    }

    private int toMinute(String hhmm, int fallback) {
        if (hhmm == null || hhmm.isBlank()) {
            return fallback;
        }
        String[] parts = hhmm.trim().split(":");
        try {
            int hour = Integer.parseInt(parts[0]);
            int minute = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            return hour * 60 + minute;
        } catch (NumberFormatException e) {
            log.warn("非法时间段：{}", hhmm);
            return fallback;
        }
    }

    // 与 ItemServiceImpl 一致：更新时保留原审计时间
    private void preserveMaterialAudit(AdMaterialEntity entity) {
        if (entity.getId() != null) {
            materialRepository.findById(entity.getId()).ifPresent(existing -> {
                entity.setCreateTime(existing.getCreateTime());
                entity.setUpdateTime(existing.getUpdateTime());
            });
        }
    }

    private void preserveScreenAudit(AdScreenEntity entity) {
        if (entity.getId() != null) {
            screenRepository.findById(entity.getId()).ifPresent(existing -> {
                entity.setCreateTime(existing.getCreateTime());
                entity.setUpdateTime(existing.getUpdateTime());
                entity.setLastSeenAt(existing.getLastSeenAt());
            });
        }
    }

    private void preserveScheduleAudit(AdScheduleEntity entity) {
        if (entity.getId() != null) {
            scheduleRepository.findById(entity.getId()).ifPresent(existing -> {
                entity.setCreateTime(existing.getCreateTime());
                entity.setUpdateTime(existing.getUpdateTime());
            });
        }
    }
}
