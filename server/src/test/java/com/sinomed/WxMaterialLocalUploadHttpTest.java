package com.sinomed;

import com.sinomed.entity.WxAccountEntity;
import com.sinomed.entity.WxMaterialEntity;
import com.sinomed.entity.StaffEntity;
import com.sinomed.repository.WxAccountRepository;
import com.sinomed.repository.WxMaterialRepository;
import com.sinomed.repository.StaffRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 本地素材上传/删除回归（设计稿 §8 二期「素材上传与治理（永久台账+本仓存储）」本地半边）：
 * multipart 落 `material-dir` + `wx_materials` 台账（permanent、无微信 media_id）；类型/大小校验；
 * 删除仅限本地治理行（带微信 media_id 的远端行拒绝），本地文件一并删除。零外网、依赖真实凭据的
 * 微信侧上传建模为另一期工作，不在此例。
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
        "sinomed.official-account.enabled=true"
})
class WxMaterialLocalUploadHttpTest {

    private static final String DB_URL =
            "jdbc:sqlite:target/wx-material-local-upload-test-" + UUID.randomUUID() + ".db";
    private static final String MATERIAL_DIR_NAME = "target/wxmaterial-local-upload-" + UUID.randomUUID();
    private static final Path MATERIAL_DIR = Paths.get(MATERIAL_DIR_NAME);
    private static final byte[] PNG_BYTES =
            new byte[]{(byte) 0x1F, (byte) 0x8B, (byte) 0x08, (byte) 0x00}; // 无损内容，仅做字节往返

    @DynamicPropertySource
    static void pin(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DB_URL);
        registry.add("sinomed.official-account.material-dir", () -> MATERIAL_DIR_NAME);
        registry.add("sinomed.official-account.material-max-bytes", () -> "256");
    }

    @LocalServerPort
    private int port;

    private final RestTemplate rest = new RestTemplate();

    private String token;

    @Autowired
    private WxAccountRepository accountRepository;
    @Autowired
    private WxMaterialRepository materialRepository;
    @Autowired
    private StaffRepository staffRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private Long accountId;
    private String userToken;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @BeforeEach
    void seed() throws Exception {
        materialRepository.deleteAll();
        accountRepository.deleteAll();
        staffRepository.findAll().forEach(s -> {
            if ("wx-material-clerk".equals(s.getAccount())) {
                staffRepository.delete(s);
            }
        });
        if (Files.exists(MATERIAL_DIR)) {
            try (var walk = Files.walk(MATERIAL_DIR)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
        WxAccountEntity account = new WxAccountEntity();
        account.setAppId("wxOALU" + System.nanoTime());
        account.setAccountType("service");
        account.setVerified(1);
        accountId = accountRepository.save(account).getId();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        var resp = rest.postForEntity(url("/api/v1/user/login"),
                new HttpEntity<>(Map.of("username", "admin", "password", "123"), headers), Map.class);
        token = (String) resp.getBody().get("token");

        // 普通 USER（role 99）用于权限门禁：素材管理应 403（设计稿 §6.3 MANAGER+）
        StaffEntity clerk = new StaffEntity();
        clerk.setName("素材店员");
        clerk.setAccount("wx-material-clerk");
        clerk.setRole(99);
        clerk.setStatus(1);
        clerk.setPassword(passwordEncoder.encode("123"));
        staffRepository.save(clerk);
        var clerkResp = rest.postForEntity(url("/api/v1/user/login"),
                new HttpEntity<>(Map.of("username", "wx-material-clerk", "password", "123"), headers), Map.class);
        userToken = (String) clerkResp.getBody().get("token");
    }

    private HttpEntity<MultiValueMap<String, Object>> uploadEntity(byte[] bytes, String filename, String mime) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(token);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(MediaType.parseMediaType(mime));
        HttpEntity<ByteArrayResource> part = new HttpEntity<>(new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        }, partHeaders);
        body.add("file", part);
        return new HttpEntity<>(body, headers);
    }

    private Map<String, Object> upload(byte[] bytes, String filename, String mime) {
        return uploadTo(accountId, bytes, filename, mime);
    }

    private Map<String, Object> uploadTo(long account, byte[] bytes, String filename, String mime) {
        return rest.postForEntity(url("/api/v1/wx/materials/upload/" + account),
                uploadEntity(bytes, filename, mime), Map.class).getBody();
    }

    private void assertBadRequest(Runnable call, String message) {
        HttpClientErrorException e = assertThrows(HttpClientErrorException.class, call::run, message);
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode(), message + " 应 400：" + e.getResponseBodyAsString());
    }

    @Test
    void uploadsPngToPermanentLedgerAndDisk() {
        Map<String, Object> row = upload(PNG_BYTES, "doctor.png", "image/png");
        assertTrue(row.get("id") != null, "上传应返回台账 id：" + row);
        assertEquals("image", row.get("materialType"), "本地上传应为 image 台账（§4.5 枚举）：" + row);
        assertNull(row.get("mediaId"), "本地治理行不应有微信 media_id：" + row);
        assertEquals("doctor.png", row.get("name"));
        assertEquals(PNG_BYTES.length, ((Number) row.get("size")).intValue());
        assertTrue(((String) row.get("localPath")).endsWith(".png"), "扩展名应取文件名：" + row);

        Path onDisk = MATERIAL_DIR.resolve((String) row.get("localPath"));
        assertTrue(Files.exists(onDisk), "文件应落盘：" + onDisk);
        // 本地副本可经既有点端取回
        var bytes = rest.exchange(url("/api/v1/wx/materials/" + row.get("id")),
                HttpMethod.GET, new HttpEntity<>(authHeaders(token)), byte[].class).getBody();
        assertArrayEquals(PNG_BYTES, bytes, "读回字节应与上传逐字节一致");
    }

    private HttpHeaders authHeaders(String jwt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwt);
        return headers;
    }

    @Test
    void rejectsUnsupportedTypeEmptyOversizeAndMimeMismatch() {
        // 不支持类型：仅 png/jpg/jpeg/webp/gif/mp4（txt 扩展名）
        assertBadRequest(() -> upload(new byte[]{0x42}, "clip.txt", "text/plain"), "txt 应拒绝");
        // 空文件
        assertBadRequest(() -> upload(new byte[0], "empty.png", "image/png"), "空文件应拒绝");
        // 超上限（material-max-bytes=256）
        assertBadRequest(() -> upload(new byte[600], "big.png", "image/png"), "超限应拒绝");
        // MIME 与扩展名不符：evil.png 配 text/html 应拒绝（审核发现：绕过“仅媒体类型”契约）
        assertBadRequest(() -> upload(new byte[]{0x42}, "evil.png", "text/html"), "png+text/html 应拒绝");
        // 审核发现：MIME 子串绕过——`text/html; x=png` 曾因 contains("png") 通过，现剥参后基础类型精确匹配必须拒绝
        assertBadRequest(() -> upload(new byte[]{0x42}, "evil.png", "text/html; x=png"), "png+text/html;x=png 应拒绝");
        // 未知账号 400（文档 api.md:161 契约）
        assertBadRequest(() -> uploadTo(999999L, PNG_BYTES, "doctor.png", "image/png"), "未知账号应 400");
        assertTrue(materialRepository.findAll().isEmpty(), "全部拒绝用例不应给台账落行");
    }

    @Test
    void uploadsMp4WithVideoTypeAndRejectsMimeMismatch() {
        // 审核发现：新增媒体类型分支只测了 png——mp4 须走 video/mp4 校验且落 materialType=video（§4.5 枚举）
        Map<String, Object> row = upload(new byte[]{0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70}, "clip.mp4", "video/mp4");
        assertEquals("video", row.get("materialType"), "mp4 应落 video 台账：" + row);
        assertEquals("video/mp4", row.get("mimeType"), "mp4 入库 MIME 应为规范 video/mp4：" + row);
        assertTrue(((String) row.get("localPath")).endsWith(".mp4"), "mp4 扩展名应取文件名：" + row);

        // 类型映射写错（如落 image）或校验漏判（MIME 与扩展名不符放行）都会让既有 6 例全绿，此处锁定
        long videoRowsBefore = materialRepository.findAll().stream().filter(m -> "video".equals(m.getMaterialType())).count();
        assertBadRequest(() -> upload(new byte[]{0x42}, "evil.mp4", "video/quicktime"), "mp4+video/quicktime 应拒绝");
        assertBadRequest(() -> upload(new byte[]{0x42}, "clip.mp4", "application/octet-stream"), "mp4+octet-stream 应拒绝");
        long videoRowsAfter = materialRepository.findAll().stream().filter(m -> "video".equals(m.getMaterialType())).count();
        assertEquals(videoRowsBefore, videoRowsAfter, "拒绝用例不应新增 video 台账行");
    }

    @Test
    void uploadsParameterizedMimeWithCanonicalReplay() {
        // 审核发现：带参数的合法媒体类型（image/png; x=png）应接受，且入库/回放为规范值而非客户端原值
        Map<String, Object> row = upload(PNG_BYTES, "param.png", "image/png; x=png");
        assertEquals("image/png", row.get("mimeType"), "入库 MIME 应为规范 image/png：" + row);
        var resp = rest.exchange(url("/api/v1/wx/materials/" + row.get("id")),
                HttpMethod.GET, new HttpEntity<>(authHeaders(token)), byte[].class);
        assertArrayEquals(PNG_BYTES, resp.getBody(), "带参合法 MIME 上传仍应逐字节读回");
        String replayed = resp.getHeaders().getContentType() == null ? null
                : resp.getHeaders().getContentType().toString().split(";")[0].trim();
        assertEquals("image/png", replayed, "回放 Content-Type 应为规范 image/png 而非客户端原值");
    }

    @Test
    void deletesLocalMaterialAndRefusesLedgerRowWithWechatMediaId() {
        Map<String, Object> row = upload(PNG_BYTES, "delete-me.png", "image/png");
        long id = ((Number) row.get("id")).longValue();
        Path onDisk = MATERIAL_DIR.resolve((String) row.get("localPath"));

        // 远端行（带微信 media_id）拒绝本地删除
        WxMaterialEntity remote = new WxMaterialEntity();
        remote.setAccountId(accountId);
        remote.setMediaId("wx-media-1");
        remote.setMaterialType("temp");
        remote.setName("fans-image");
        remote.setUrl("https://wx.qq.com/pic.jpg");
        remote.setSize(16L);
        remote.setMimeType("image/jpeg");
        WxMaterialEntity savedRemote = materialRepository.save(remote);
        HttpClientErrorException e = assertThrows(HttpClientErrorException.class,
                () -> rest.exchange(url("/api/v1/wx/materials/local/" + savedRemote.getId()),
                        HttpMethod.DELETE, new HttpEntity<>(authHeaders(token)), Map.class));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode(), "远端行应拒绝：" + e.getResponseBodyAsString());

        // 本地行可删，文件一并清除
        String msg = rest.exchange(url("/api/v1/wx/materials/local/" + id),
                HttpMethod.DELETE, new HttpEntity<>(authHeaders(token)), Map.class)
                .getBody().get("message").toString();
        assertTrue(msg.contains("删除"), msg);
        assertFalse(Files.exists(onDisk), "删除应清掉本地文件：" + onDisk);
        assertFalse(materialRepository.findById(id).isPresent(), "台账行应删除");
    }

    @Test
    void unauthenticatedUploadIsRejected() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        HttpClientErrorException e = assertThrows(HttpClientErrorException.class,
                () -> rest.postForEntity(url("/api/v1/wx/materials/upload/" + accountId),
                        new HttpEntity<>(body, headers), Map.class));
        assertEquals(HttpStatus.UNAUTHORIZED, e.getStatusCode(), "未登录应 401");
    }

    @Test
    void userCannotUploadOrDeleteMaterials() {
        HttpHeaders readHeaders = new HttpHeaders();
        readHeaders.setBearerAuth(userToken);
        HttpClientErrorException pageErr = assertThrows(HttpClientErrorException.class,
                () -> rest.exchange(url("/api/v1/wx/materials/page?current=1&pageSize=10"),
                        HttpMethod.GET, new HttpEntity<>(readHeaders), Map.class));
        assertEquals(HttpStatus.FORBIDDEN, pageErr.getStatusCode(), "USER 分页应 403：" + pageErr.getResponseBodyAsString());

        HttpClientErrorException fileErr = assertThrows(HttpClientErrorException.class,
                () -> rest.exchange(url("/api/v1/wx/materials/1"),
                        HttpMethod.GET, new HttpEntity<>(readHeaders), Map.class));
        assertEquals(HttpStatus.FORBIDDEN, fileErr.getStatusCode(), "USER 取文件应 403：" + fileErr.getResponseBodyAsString());

        // 普通 USER 上传应 403（设计稿 §6.3 MANAGER+）
        HttpHeaders uploadHeaders = new HttpHeaders();
        uploadHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
        uploadHeaders.setBearerAuth(userToken);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(PNG_BYTES) {
            @Override
            public String getFilename() {
                return "u.png";
            }
        });
        HttpClientErrorException e = assertThrows(HttpClientErrorException.class,
                () -> rest.postForEntity(url("/api/v1/wx/materials/upload/" + accountId),
                        new HttpEntity<>(body, uploadHeaders), Map.class));
        assertEquals(HttpStatus.FORBIDDEN, e.getStatusCode(), "USER 上传应 403：" + e.getResponseBodyAsString());

        // 普通 USER 删除应 403
        HttpHeaders delHeaders = new HttpHeaders();
        delHeaders.setBearerAuth(userToken);
        HttpClientErrorException del = assertThrows(HttpClientErrorException.class,
                () -> rest.exchange(url("/api/v1/wx/materials/local/1"),
                        HttpMethod.DELETE, new HttpEntity<>(delHeaders), Map.class));
        assertEquals(HttpStatus.FORBIDDEN, del.getStatusCode(), "USER 删除应 403：" + del.getResponseBodyAsString());
        assertTrue(materialRepository.findAll().isEmpty(), "USER 越权不应落台账行");
    }

    @Test
    void deleteToleratesMissingLocalFile() throws Exception {
        // 审核发现：文件已丢失的台账行无法经删除端点清理——修复后应先删文件再删行
        Map<String, Object> row = upload(PNG_BYTES, "orphan.png", "image/png");
        long id = ((Number) row.get("id")).longValue();
        Path onDisk = MATERIAL_DIR.resolve((String) row.get("localPath"));
        Files.deleteIfExists(onDisk);
        assertFalse(Files.exists(onDisk), "前置：文件应已丢失");

        String msg = rest.exchange(url("/api/v1/wx/materials/local/" + id),
                HttpMethod.DELETE, new HttpEntity<>(authHeaders(token)), Map.class)
                .getBody().get("message").toString();
        assertTrue(msg.contains("删除"), msg);
        assertFalse(materialRepository.findById(id).isPresent(), "文件缺失也应清台账行");
    }

}
