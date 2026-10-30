package com.sinomed;

import com.sinomed.entity.WxAccountEntity;
import com.sinomed.entity.WxMaterialEntity;
import com.sinomed.repository.WxAccountRepository;
import com.sinomed.repository.WxMaterialRepository;
import com.sinomed.repository.StaffRepository;
import com.sinomed.service.WxMpServiceProvider;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 公众号永久素材远端上传回归（设计稿 §8 二期出网半边；卡 B 验收口径）：
 * 经 WxJava SDK 调 cgi-bin/material/add_material，成功把微信 media_id/url 回写 wx_materials；
 * 微信侧 token 由 SDK 自动获取（cgi-bin/token）；真实出网压凭据，本机走 wechat-api-base-url
 * 指向 JDK HttpServer 本地桩（D-OA4/WxMenuPushHttpTest 先例，零外网）。
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
        "sinomed.official-account.enabled=true"
})
class WxMaterialRemoteUploadHttpTest {

    private static final String DB_URL =
            "jdbc:sqlite:target/wx-material-remote-upload-test-" + UUID.randomUUID() + ".db";
    private static final String MATERIAL_DIR_NAME = "target/wxmaterial-remote-upload-" + UUID.randomUUID();
    private static final Path MATERIAL_DIR = Paths.get(MATERIAL_DIR_NAME);
    private static final byte[] PNG_BYTES =
            new byte[]{(byte) 0x1F, (byte) 0x8B, (byte) 0x08, (byte) 0x00};

    static volatile String tokenPayload = "{\"access_token\":\"TOKEN_REMOTE\",\"expires_in\":7200}";
    static volatile String uploadReply = "{\"media_id\":\"MEDIA_REMOTE_1\",\"url\":\"https://mmbiz.qpic.cn/a.jpg\"}";
    static final AtomicInteger tokenHits = new AtomicInteger();
    static final AtomicInteger uploadHits = new AtomicInteger();
    static volatile String lastUploadQuery;
    static volatile String lastUploadBody;

    private static final HttpServer STUB = newStub();

    private static HttpServer newStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", exchange -> {
                String path = exchange.getRequestURI().getPath();
                String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                boolean isToken = path.endsWith("/cgi-bin/token");
                if (isToken) {
                    tokenHits.incrementAndGet();
                } else {
                    uploadHits.incrementAndGet();
                    lastUploadQuery = exchange.getRequestURI().getRawQuery();
                    lastUploadBody = requestBody;
                }
                byte[] out = (isToken ? tokenPayload : uploadReply).getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, out.length);
                exchange.getResponseBody().write(out);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException("微信桩起不来", e);
        }
    }

    @DynamicPropertySource
    static void pin(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DB_URL);
        registry.add("sinomed.official-account.material-dir", () -> MATERIAL_DIR_NAME);
        registry.add("sinomed.official-account.material-max-bytes", () -> "4096");
        registry.add("sinomed.official-account.credential-key", () -> "test-cred-key");
        registry.add("sinomed.official-account.wechat-api-base-url", () -> "http://localhost:" + STUB.getAddress().getPort());
    }

    @AfterAll
    static void stopStub() {
        STUB.stop(0);
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
    @Autowired
    private WxMpServiceProvider wxMpServiceProvider;

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
            if ("wx-remote-clerk".equals(s.getAccount())) {
                staffRepository.delete(s);
            }
        });
        if (Files.exists(MATERIAL_DIR)) {
            try (var walk = Files.walk(MATERIAL_DIR)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
        WxAccountEntity account = new WxAccountEntity();
        account.setAppId("wxOAREM" + System.nanoTime());
        account.setAccountType("service");
        account.setVerified(1);
        account.setAppSecret("REMOTE-SECRET");
        accountId = accountRepository.save(account).getId();
        wxMpServiceProvider.evict(accountId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        var resp = rest.postForEntity(url("/api/v1/user/login"),
                new HttpEntity<>(Map.of("username", "admin", "password", "123"), headers), Map.class);
        token = (String) resp.getBody().get("token");

        var clerk = new com.sinomed.entity.StaffEntity();
        clerk.setName("远端素材店员");
        clerk.setAccount("wx-remote-clerk");
        clerk.setRole(99);
        clerk.setStatus(1);
        clerk.setPassword(passwordEncoder.encode("123"));
        staffRepository.save(clerk);
        var clerkResp = rest.postForEntity(url("/api/v1/user/login"),
                new HttpEntity<>(Map.of("username", "wx-remote-clerk", "password", "123"), headers), Map.class);
        userToken = (String) clerkResp.getBody().get("token");

        tokenHits.set(0);
        uploadHits.set(0);
        lastUploadQuery = null;
        lastUploadBody = null;
        tokenPayload = "{\"access_token\":\"TOKEN_REMOTE\",\"expires_in\":7200}";
        uploadReply = "{\"media_id\":\"MEDIA_REMOTE_1\",\"url\":\"https://mmbiz.qpic.cn/a.jpg\"}";
    }

    private HttpEntity<MultiValueMap<String, Object>> uploadEntity(byte[] bytes, String filename, String mime,
                                                                   String jwt) {
        return uploadEntity(bytes, filename, mime, jwt, null, null);
    }

    private HttpEntity<MultiValueMap<String, Object>> uploadEntity(byte[] bytes, String filename, String mime,
                                                                   String jwt, String videoTitle, String videoIntroduction) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(jwt);
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
        if (videoTitle != null) {
            body.add("videoTitle", videoTitle);
        }
        if (videoIntroduction != null) {
            body.add("videoIntroduction", videoIntroduction);
        }
        return new HttpEntity<>(body, headers);
    }

    private Map<String, Object> uploadRemote(byte[] bytes, String filename, String mime) {
        return uploadRemote(bytes, filename, mime, null, null);
    }

    private Map<String, Object> uploadRemote(byte[] bytes, String filename, String mime,
                                             String videoTitle, String videoIntroduction) {
        return rest.postForEntity(url("/api/v1/wx/materials/remote/" + accountId),
                uploadEntity(bytes, filename, mime, token, videoTitle, videoIntroduction), Map.class).getBody();
    }

    private void assertBadRequest(Runnable call, String message) {
        HttpClientErrorException e = assertThrows(HttpClientErrorException.class, call::run, message);
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode(), message + " 应 400：" + e.getResponseBodyAsString());
    }

    @Test
    void uploadsPermanentMaterialAndWritesBackMediaId() {
        Map<String, Object> row = uploadRemote(PNG_BYTES, "doctor.png", "image/png");
        assertNotNull(row.get("id"), "上传应返回台账 id：" + row);
        assertEquals("MEDIA_REMOTE_1", row.get("mediaId"), "微信 media_id 应回写台账：" + row);
        assertEquals("https://mmbiz.qpic.cn/a.jpg", row.get("url"), "微信 url 应回写：" + row);
        assertEquals("image", row.get("materialType"));
        assertEquals(PNG_BYTES.length, ((Number) row.get("size")).intValue());

        assertEquals(1, tokenHits.get(), "SDK 应取一次 access_token");
        assertEquals(1, uploadHits.get(), "应调一次 material/add_material");
        assertNotNull(lastUploadQuery);
        assertTrue(lastUploadQuery.contains("type=image"), "add_material 应带 type=image：" + lastUploadQuery);
        assertTrue(lastUploadQuery.contains("access_token=TOKEN_REMOTE"), "token 应拼入查询串：" + lastUploadQuery);

        // 本地副本仍在（远端上传失败时才会清理）
        WxMaterialEntity saved = materialRepository.findById(((Number) row.get("id")).longValue()).orElseThrow();
        assertTrue(Files.exists(MATERIAL_DIR.resolve(saved.getLocalPath())), "本地副本应保留");
    }

    @Test
    void uploadsMp4WithVideoTypeAndDescription() {
        Map<String, Object> row = uploadRemote(new byte[]{0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70},
                "clip.mp4", "video/mp4", "手术演示", "术前培训");
        assertEquals("video", row.get("materialType"));
        assertEquals("MEDIA_REMOTE_1", row.get("mediaId"));
        assertTrue(lastUploadQuery.contains("type=video"), "mp4 应带 type=video：" + lastUploadQuery);
        assertNotNull(lastUploadBody, "mp4 上传应捕获微信请求体");
        assertTrue(lastUploadBody.contains("\"title\":\"手术演示\""),
                "description 应透传 videoTitle：" + lastUploadBody);
        assertTrue(lastUploadBody.contains("\"introduction\":\"术前培训\""),
                "description 应透传 videoIntroduction：" + lastUploadBody);
    }

    @Test
    void rejectsMissingAppSecretAndUnknownAccount() {
        WxAccountEntity noSecret = new WxAccountEntity();
        noSecret.setAppId("wxOAREM-NOSECRET" + System.nanoTime());
        noSecret.setAccountType("service");
        noSecret.setVerified(1);
        Long noSecretId = accountRepository.save(noSecret).getId();

        HttpClientErrorException e = assertThrows(HttpClientErrorException.class,
                () -> rest.postForEntity(url("/api/v1/wx/materials/remote/" + noSecretId),
                        uploadEntity(PNG_BYTES, "a.png", "image/png", token), Map.class));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode(), "未配 AppSecret 应 400：" + e.getResponseBodyAsString());

        assertBadRequest(() -> rest.postForEntity(url("/api/v1/wx/materials/remote/999999"),
                uploadEntity(PNG_BYTES, "a.png", "image/png", token), Map.class), "未知账号应 400");

        assertEquals(0, uploadHits.get(), "拒绝用例不应触达微信桩");
        assertTrue(materialRepository.findAll().isEmpty(), "拒绝用例不应落台账行");
    }

    @Test
    void rejectsUnsupportedTypeAndMimeMismatch() {
        assertBadRequest(() -> uploadRemote(new byte[]{0x42}, "clip.txt", "text/plain"), "txt 应拒绝");
        assertBadRequest(() -> uploadRemote(new byte[]{0x42}, "evil.png", "text/html"), "MIME 不符应拒绝");
        assertBadRequest(() -> uploadRemote(new byte[10000], "big.png", "image/png"), "超限应拒绝");
        assertEquals(0, uploadHits.get(), "本地校验失败不应触达微信桩");
        assertTrue(materialRepository.findAll().isEmpty(), "校验失败不应落台账行");
    }

    @Test
    void wechatErrorIsPassedThroughAndCleansUpOrphan() throws Exception {
        uploadReply = "{\"errcode\":40001,\"errmsg\":\"invalid credential\"}";
        HttpClientErrorException e = assertThrows(HttpClientErrorException.class,
                () -> uploadRemote(PNG_BYTES, "doctor.png", "image/png"));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode(), "微信 errcode 非 0 应 400：" + e.getResponseBodyAsString());
        assertTrue(e.getResponseBodyAsString().contains("微信永久素材上传失败"), "错误应透传：" + e.getResponseBodyAsString());
        assertTrue(materialRepository.findAll().isEmpty(), "上传失败应清理孤儿台账行");
        try (var walk = Files.walk(MATERIAL_DIR)) {
            assertFalse(walk.anyMatch(p -> !p.equals(MATERIAL_DIR) && Files.isRegularFile(p)), "上传失败应清理本地文件");
        }
    }

    @Test
    void userCannotUploadRemoteMaterial() {
        HttpClientErrorException e = assertThrows(HttpClientErrorException.class,
                () -> rest.postForEntity(url("/api/v1/wx/materials/remote/" + accountId),
                        uploadEntity(PNG_BYTES, "u.png", "image/png", userToken), Map.class));
        assertEquals(HttpStatus.FORBIDDEN, e.getStatusCode(), "USER 应 403：" + e.getResponseBodyAsString());
        assertTrue(materialRepository.findAll().isEmpty(), "越权不应落台账行");
    }

    @Test
    void unauthenticatedUploadRemoteIsRejected() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        HttpClientErrorException e = assertThrows(HttpClientErrorException.class,
                () -> rest.postForEntity(url("/api/v1/wx/materials/remote/" + accountId),
                        new HttpEntity<>(body, headers), Map.class));
        assertEquals(HttpStatus.UNAUTHORIZED, e.getStatusCode(), "未登录应 401");
    }
}
