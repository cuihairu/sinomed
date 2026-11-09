package com.sinomed.service.impl;

import com.sinomed.entity.WxAccountEntity;
import com.sinomed.entity.WxMaterialEntity;
import com.sinomed.repository.WxAccountRepository;
import com.sinomed.repository.WxMaterialRepository;
import com.sinomed.service.WxMpServiceProvider;
import com.sinomed.util.DateUtil;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.common.error.WxErrorException;
import me.chanjar.weixin.mp.bean.material.WxMpMaterial;
import me.chanjar.weixin.mp.bean.material.WxMpMaterialUploadResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 公众号素材（设计稿 §4.5/§8）：粉丝图片消息拉取落台账（temp）、本地治理上传（永久台账+本仓存储）、
 * 远端永久素材上传（WxJava SDK，cgi-bin/material/add_material）与本地副本读取/删除。
 * 落盘根目录 material-dir（相对路径入库、绝对寻址靠它拼），下载/落盘失败不放大回调（微信 5s 窗口）。
 */
@Slf4j
@Component
public class WxMaterialAdminService {

    private final WxAccountRepository accountRepository;
    private final WxMaterialRepository materialRepository;
    private final WxMpServiceProvider wxMpServiceProvider;

    /** 素材落盘根目录（env WX_OA_MATERIAL_DIR），相对路径入库、绝对寻址靠它拼 */
    private final String materialDir;

    /** 单素材下载上限（字节，env WX_OA_MATERIAL_MAX_BYTES），超限跳过不放大 */
    private final long materialMaxBytes;

    private static final Set<String> LOCAL_MATERIAL_EXTS =
            Set.of("png", "jpg", "jpeg", "webp", "gif", "mp4");

    /** 素材下载客户端：连接/单请求均 3s 上限，护住微信回调 5s 窗口 */
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3)).build();

    public WxMaterialAdminService(WxAccountRepository accountRepository,
                                  WxMaterialRepository materialRepository,
                                  WxMpServiceProvider wxMpServiceProvider,
                                  @org.springframework.beans.factory.annotation.Value("${sinomed.official-account.material-dir:data/wxmaterials}") String materialDir,
                                  @org.springframework.beans.factory.annotation.Value("${sinomed.official-account.material-max-bytes:10485760}") long materialMaxBytes) {
        this.accountRepository = accountRepository;
        this.materialRepository = materialRepository;
        this.wxMpServiceProvider = wxMpServiceProvider;
        this.materialDir = materialDir;
        this.materialMaxBytes = materialMaxBytes;
    }

    /**
     * 管理端素材台账只读分页（设计稿 §4.5「供管理端查看」，粉丝发图此前落盘后无处可见）：
     * 按账号/类型/时间窗 AND 过滤（同 pageLogs 口径），排序由 Pageable 携带（默认最新在前）。
     * 不依赖模块开关——素材是已落库的台账数据，模块关闭期间也要可查（不静默）。
     */
    public Page<WxMaterialEntity> pageMaterials(Long accountId, String materialType,
                                                String startTime, String endTime, Pageable pageable) {
        if (accountId == null && isBlank(materialType) && isBlank(startTime) && isBlank(endTime)) {
            return materialRepository.findAll(pageable);
        }
        Specification<WxMaterialEntity> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new LinkedList<>();
            if (accountId != null) {
                predicates.add(criteriaBuilder.equal(root.get("accountId").as(Long.class), accountId));
            }
            if (!isBlank(materialType)) {
                predicates.add(criteriaBuilder.equal(root.get("materialType").as(String.class), materialType.trim()));
            }
            Date start = DateUtil.parseParam(startTime);
            if (start != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.<Date>get("createTime"), start));
            }
            Date end = DateUtil.parseParam(endTime);
            if (end != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.<Date>get("createTime"), end));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
        return materialRepository.findAll(spec, pageable);
    }

    /** 素材本地副本（管理端取图）：path 已解析并校验落在素材根目录内 */
    public record MaterialFile(Path path, String mimeType) {
    }

    /**
     * 管理端取素材本地文件：localPath 相对素材根目录解析，normalize 后必须仍在根内（防 ../ 穿越），
     * 无本地副本/文件缺失/非法路径一律 IllegalArgumentException（400 口径，管理端无 404 特例）。
     */
    public MaterialFile loadMaterialFile(long id) {
        WxMaterialEntity material = materialRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("素材不存在"));
        if (isBlank(material.getLocalPath())) {
            throw new IllegalArgumentException("该素材无本地副本");
        }
        Path target = resolveLocalPath(material.getLocalPath());
        if (target == null || !Files.isRegularFile(target)) {
            throw new IllegalArgumentException("素材文件不存在或路径不合法");
        }
        String mime = isBlank(material.getMimeType()) ? "application/octet-stream" : material.getMimeType().trim();
        try {
            org.springframework.http.MediaType.parseMediaType(mime);
        } catch (Exception e) {
            mime = "application/octet-stream"; // 台账 mime 来自下载响应头，坏值兜底不让取图 500
        }
        return new MaterialFile(target, mime);
    }

    /** localPath 相对素材根目录解析并校验在根内；路径非法/越界返回 null（缺失文件不在此判，调用方定口径） */
    private Path resolveLocalPath(String localPath) {
        if (isBlank(localPath)) {
            return null;
        }
        Path root = Paths.get(materialDir).toAbsolutePath().normalize();
        Path target = root.resolve(localPath).normalize();
        return target.startsWith(root) ? target : null;
    }

    /**
     * 本地素材上传（设计稿 §8 二期「素材上传与治理（永久台账+本仓存储）」的本地半边）：
     * 落 `wx_materials.material_type=image/video`（§4.5 标准枚举），`local_path` 沿素材根目录、`media_id` 留空
     * （本地治理行与微信远端台账互不冲突）；真实上传到微信属二期出网半边，压正式凭据后另接。
     */
    public WxMaterialEntity uploadLocalMaterial(long accountId, MultipartFile file) {
        WxAccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("公众号账号不存在"));
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件为空");
        }
        if (file.getSize() > materialMaxBytes) {
            throw new IllegalArgumentException("文件超过大小上限：" + materialMaxBytes + " 字节");
        }
        String originalName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().trim();
        String extension = extensionFor(originalName, file.getContentType());
        if (!LOCAL_MATERIAL_EXTS.contains(extension)) {
            throw new IllegalArgumentException("仅支持 png/jpg/jpeg/webp/gif/mp4 素材");
        }
        // 客户端 MIME 必须与扩展名的规范媒体类型一致：先剥掉 `;` 后的参数，再对基础媒体类型精确比较
        // （禁止 `contains` 子串匹配——`text/html; x=png` 会因含 "png" 绕过）；入库记规范值，回放 Content-Type 恒定
        String baseMime = (file.getContentType() == null ? "" : file.getContentType())
                .split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        String canonicalMime = switch (extension) {
            case "png" -> baseMime.equals("image/png") ? "image/png" : null;
            case "jpg", "jpeg" -> (baseMime.equals("image/jpeg") || baseMime.equals("image/jpg")) ? "image/jpeg" : null;
            case "gif" -> baseMime.equals("image/gif") ? "image/gif" : null;
            case "webp" -> baseMime.equals("image/webp") ? "image/webp" : null;
            case "mp4" -> baseMime.equals("video/mp4") ? "video/mp4" : null;
            default -> null;
        };
        if (canonicalMime == null) {
            throw new IllegalArgumentException("文件媒体类型与扩展名不符：" + file.getContentType());
        }
        String relative = Paths.get(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM")),
                UUID.randomUUID() + "." + extension).toString().replace('\\', '/');
        Path target = Paths.get(materialDir, relative);
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target.toAbsolutePath());
            WxMaterialEntity material = new WxMaterialEntity();
            material.setAccountId(account.getId());
            material.setMaterialType("mp4".equals(extension) ? "video" : "image");
            material.setName(originalName.isBlank() ? "本地素材" : originalName);
            material.setLocalPath(relative);
            material.setSize(file.getSize());
            material.setMimeType(canonicalMime);
            try {
                return materialRepository.saveAndFlush(material);
            } catch (RuntimeException e) {
                // 落盘后 save 失败：清掉刚写的孤儿文件（防脏盘），再上抛——与远端上传路径同口径
                try {
                    Files.deleteIfExists(target);
                } catch (IOException cleanup) {
                    log.warn("素材 save 失败后清理孤儿文件失败 account={} path={}: {}",
                            account.getId(), relative, cleanup.getMessage());
                }
                throw e;
            }
        } catch (IOException e) {
            throw new IllegalStateException("素材落盘失败：" + e.getMessage(), e);
        }
    }

    /** 删除本地治理素材；有微信 media_id 的远端台账行不走本地删除（防误删远端审计）。 */
    public void deleteLocalMaterial(long id) {
        WxMaterialEntity material = materialRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("素材不存在"));
        if (!isBlank(material.getMediaId())) {
            throw new IllegalArgumentException("微信素材台账不可本地删除");
        }
        if (!isBlank(material.getLocalPath())) {
            try {
                // 文件已丢失时仍清台账行（否则该行永远无法经本端点清理）；缺失按已删处理
                Path target = resolveLocalPath(material.getLocalPath());
                if (target == null) {
                    throw new IllegalArgumentException("素材路径不合法");
                }
                Files.deleteIfExists(target);
            } catch (IOException e) {
                throw new IllegalStateException("素材文件删除失败：" + e.getMessage(), e);
            }
        }
        materialRepository.deleteById(id);
    }

    /**
     * 远端上传永久素材（设计稿 §8 二期「素材上传与治理」出网半边；卡 B 验收口径）：
     * 走 WxJava SDK `WxMpMaterialService.materialFileUpload`（cgi-bin/material/add_material），
     * 出网基址经 WxMpServiceProvider 注入 WxMpHostConfig，测试指本地桩（D-OA4 先例）。
     * 上传成功把 media_id/url 回写台账行；微信错误或 IO 失败删除本地孤儿文件+台账行并透传 400。
     */
    public WxMaterialEntity uploadRemoteMaterial(long accountId, MultipartFile file,
                                                 String videoTitle, String videoIntroduction) {
        WxAccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("公众号账号不存在"));
        if (isBlank(account.getAppSecret())) {
            throw new IllegalArgumentException("该账号未配置 AppSecret，无法上传微信永久素材");
        }

        WxMaterialEntity material;
        Path localFile;
        try {
            material = uploadLocalMaterial(accountId, file);
            localFile = Paths.get(materialDir, material.getLocalPath());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("素材本地暂存失败：" + e.getMessage(), e);
        }

        String mediaType = "video".equals(material.getMaterialType()) ? "video" : "image";
        WxMpMaterial remote = new WxMpMaterial();
        remote.setName(file.getOriginalFilename());
        remote.setFile(localFile.toFile());
        remote.setVideoTitle(videoTitle);
        remote.setVideoIntroduction(videoIntroduction);
        try {
            WxMpMaterialUploadResult result = wxMpServiceProvider.forAccount(account)
                    .getMaterialService().materialFileUpload(mediaType, remote);
            if (result == null || isBlank(result.getMediaId())) {
                throw new IllegalArgumentException("微信永久素材上传失败（errcode=" +
                        (result == null ? null : result.getErrCode()) + " errmsg=" +
                        (result == null ? null : result.getErrMsg()) + "）");
            }
            material.setMediaId(result.getMediaId());
            material.setUrl(result.getUrl());
            return materialRepository.save(material);
        } catch (WxErrorException e) {
            cleanupRemoteUploadOrphan(material, localFile);
            throw new IllegalArgumentException("微信永久素材上传失败：" + e.getMessage(), e);
        } catch (RuntimeException e) {
            WxMaterialEntity managed = materialRepository.findById(material.getId()).orElse(material);
            cleanupRemoteUploadOrphan(managed, localFile);
            throw e;
        }
    }

    private void cleanupRemoteUploadOrphan(WxMaterialEntity saved, Path localFile) {
        materialRepository.delete(saved);
        try {
            Files.deleteIfExists(localFile);
        } catch (IOException ignored) {
            log.warn("remote material upload cleanup file delete failed path={}", localFile, ignored);
        }
    }

    /**
     * 图片消息素材拉取（设计稿 §4.5 第一期口径：下载 PicUrl 到 local_path 落 wx_materials 台账）。
     * 同 (account, media_id) 已有台账则跳过重下（微信超时重推同图）；下载失败、非 200、超上限一律
     * 只记 warn 不落行——回调仍按无素材路径走兜底（微信 5s 约束不得放大异常）。
     * 下载在回调线程内同步做，单次上限 3s；超时被微信重发时由 MsgId 排重兜底（不重复拉取）。
     */
    public void captureMaterial(long accountId, String mediaId, String picUrl) {
        if (isBlank(picUrl)) {
            return;
        }
        if (!isBlank(mediaId)
                && materialRepository.findByAccountIdAndMediaId(accountId, mediaId).isPresent()) {
            return; // 已入台账（含此前成功下载），不重复下载
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(picUrl))
                    .timeout(Duration.ofSeconds(3)).GET().build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                log.warn("wx material download http {} account={} media={}",
                        response.statusCode(), accountId, mediaId);
                return;
            }
            byte[] body = response.body();
            if (body.length > materialMaxBytes) {
                log.warn("wx material exceeds {} bytes, skip account={} media={}",
                        materialMaxBytes, accountId, mediaId);
                return;
            }
            String mime = response.headers().firstValue("Content-Type").orElse("");
            String relative = Paths.get(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM")),
                    UUID.randomUUID() + "." + extensionFor(picUrl, mime))
                    .toString().replace('\\', '/');
            Path target = Paths.get(materialDir, relative);
            Files.createDirectories(target.getParent());
            Files.write(target, body);

            WxMaterialEntity material = new WxMaterialEntity();
            material.setAccountId(accountId);
            material.setMediaId(mediaId);
            material.setMaterialType("temp");
            material.setUrl(picUrl);
            material.setLocalPath(relative);
            material.setSize((long) body.length);
            // 与上传路径同口径规范化（剥参数+小写），远端 Content-Type 不再原样入台账
            material.setMimeType(canonicalizeMimeType(mime));
            try {
                materialRepository.saveAndFlush(material);
            } catch (RuntimeException e) {
                // 落盘后 save 失败：清掉刚写的孤儿文件（防脏盘），再上抛——与本地/远端上传路径同口径
                try {
                    Files.deleteIfExists(target);
                } catch (IOException cleanup) {
                    log.warn("wx 素材拉取 save 失败后清理孤儿文件失败 account={} path={}: {}",
                            accountId, relative, cleanup.getMessage());
                }
                throw e;
            }
        } catch (Exception e) {
            log.warn("wx material download failed account={} media={}: {}", accountId, mediaId, e.getMessage());
        }
    }

    /** 落盘扩展名：优先 Content-Type，其次 URL 路径后缀，兜底 bin */
    private static String extensionFor(String url, String mime) {
        String lower = mime == null ? "" : mime.toLowerCase(Locale.ROOT);
        if (lower.contains("jpeg") || lower.contains("jpg")) {
            return "jpg";
        }
        if (lower.contains("png")) {
            return "png";
        }
        if (lower.contains("gif")) {
            return "gif";
        }
        if (lower.contains("webp")) {
            return "webp";
        }
        int slash = url.lastIndexOf('/');
        int dot = url.lastIndexOf('.');
        if (dot > slash && dot < url.length() - 1) {
            String ext = url.substring(dot + 1).toLowerCase(Locale.ROOT);
            if (ext.matches("[a-z0-9]{1,5}")) {
                return ext;
            }
        }
        return "bin";
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    /** 规范化媒体类型：剥 `;` 参数+小写，白名单（png/jpeg/gif/webp/mp4）内回规范值，白名单外回 null */
    private static String canonicalizeMimeType(String rawMime) {
        String base = (rawMime == null ? "" : rawMime).split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        return switch (base) {
            case "image/png" -> "image/png";
            case "image/jpeg", "image/jpg" -> "image/jpeg";
            case "image/gif" -> "image/gif";
            case "image/webp" -> "image/webp";
            case "video/mp4" -> "video/mp4";
            default -> null;
        };
    }
}
