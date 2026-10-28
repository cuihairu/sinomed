package com.sinomed;

import com.sinomed.entity.WxAccountEntity;
import com.sinomed.entity.WxMaterialEntity;
import com.sinomed.repository.WxAccountRepository;
import com.sinomed.repository.WxMaterialRepository;
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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    private Long accountId;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @BeforeEach
    void seed() throws Exception {
        materialRepository.deleteAll();
        accountRepository.deleteAll();
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
    }

    private HttpEntity<MultiValueMap<String, Object>> uploadEntity(byte[] bytes, String filename, String mime) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(token);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        });
        return new HttpEntity<>(body, headers);
    }

    private Map<String, Object> upload(byte[] bytes, String filename, String mime) {
        return rest.postForEntity(url("/api/v1/wx/materials/upload/" + accountId),
                uploadEntity(bytes, filename, mime), Map.class).getBody();
    }

    @Test
    void uploadsPngToPermanentLedgerAndDisk() {
        Map<String, Object> row = upload(PNG_BYTES, "doctor.png", "image/png");
        assertTrue(row.get("id") != null, "上传应返回台账 id：" + row);
        assertEquals("permanent", row.get("materialType"), "本地上传应为 permanent 台账：" + row);
        assertNull(row.get("mediaId"), "本地治理行不应有微信 media_id：" + row);
        assertEquals("doctor.png", row.get("name"));
        assertEquals(PNG_BYTES.length, ((Number) row.get("size")).intValue());
        assertTrue(((String) row.get("localPath")).endsWith(".png"), "扩展名应取文件名：" + row);

        Path onDisk = MATERIAL_DIR.resolve((String) row.get("localPath"));
        assertTrue(Files.exists(onDisk), "文件应落盘：" + onDisk);
        // 本地副本可经既有点端取回
        var bytes = rest.exchange(url("/api/v1/wx/materials/" + row.get("id")),
                HttpMethod.GET, new HttpEntity<>(authHeaders(token)), byte[].class).getBody();
        assertEquals(PNG_BYTES.length, bytes.length, "读回字节应与上传一致");
    }

    private HttpHeaders authHeaders(String jwt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwt);
        return headers;
    }

    @Test
    void rejectsUnsupportedTypeEmptyAndOversize() {
        // 不支持类型：仅 png/jpg/jpeg/webp/gif/mp4
        assertThrows(HttpClientErrorException.class,
                () -> upload(new byte[]{0x42}, "clip.txt", "text/plain"),
                "txt 应拒绝");
        // 空文件
        assertThrows(HttpClientErrorException.class,
                () -> upload(new byte[0], "empty.png", "image/png"),
                "空文件应拒绝");
        // 超上限（material-max-bytes=256）
        assertThrows(HttpClientErrorException.class,
                () -> upload(new byte[600], "big.png", "image/png"),
                "超限应拒绝");
        assertTrue(materialRepository.findAll().isEmpty(), "全部拒绝用例不应给台账落行");
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
}
