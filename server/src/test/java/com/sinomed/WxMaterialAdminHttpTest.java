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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 管理端素材台账 HTTP 回归（设计稿 §4.5「供管理端查看」补齐：粉丝发来的图片此前落盘后无处可见）。
 * 台账分页（账号/类型/时间窗 AND 过滤）+ 本地副本取图（含路径穿越防护）+ 未登录 401。
 * 与 rules/logs 同口径：只读、不依赖模块开关，需登录（安全模式取图不可匿名）。
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:sqlite:target/wx-material-admin-test-${random.uuid}.db",
        "sinomed.official-account.enabled=true",
        "sinomed.official-account.material-dir=target/wxmaterials-admin-test"
})
class WxMaterialAdminHttpTest {

    private static final Path MATERIAL_DIR = Paths.get("target/wxmaterials-admin-test");
    private static final byte[] PNG_BYTES =
            new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    @LocalServerPort
    private int port;

    private final RestTemplate rest = new RestTemplate();

    private String token;

    @Autowired
    private WxAccountRepository accountRepository;
    @Autowired
    private WxMaterialRepository materialRepository;

    private Long accountA;
    private Long accountB;

    private Long matLocal;
    private Long matRemoteOnly;
    private Long matNoFile;
    private Long matTraversal;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private HttpHeaders auth() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    @BeforeEach
    void seed() throws IOException {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        var resp = rest.postForEntity(url("/api/v1/user/login"),
                new HttpEntity<>(Map.of("username", "admin", "password", "123"), headers), Map.class);
        token = (String) resp.getBody().get("token");

        accountRepository.deleteAll();
        materialRepository.deleteAll();
        Files.createDirectories(MATERIAL_DIR.resolve("202610"));
        Files.write(MATERIAL_DIR.resolve("202610/img1.png"), PNG_BYTES);

        accountA = accountRepository.save(newAccount("wxMAT1", "素材测试号A")).getId();
        accountB = accountRepository.save(newAccount("wxMAT2", "素材测试号B")).getId();

        matLocal = materialRepository.save(material(accountA, "m1", "temp", "粉丝图片",
                "202610/img1.png", (long) PNG_BYTES.length, "image/png")).getId();
        matRemoteOnly = materialRepository.save(material(accountA, "m2", "permanent", "仅远端",
                null, 2048L, "image/jpeg")).getId();
        matNoFile = materialRepository.save(material(accountA, "m3", "temp", "下载失败留痕",
                "202610/missing.png", null, "image/png")).getId();
        matTraversal = materialRepository.save(material(accountB, "m4", "temp", "越权探测",
                "../../../etc/passwd", 16L, "text/plain")).getId();
    }

    private WxAccountEntity newAccount(String appId, String name) {
        WxAccountEntity account = new WxAccountEntity();
        account.setAppId(appId);
        account.setAccountName(name);
        account.setAccountType("service");
        account.setVerified(1);
        return account;
    }

    private WxMaterialEntity material(Long accountId, String mediaId, String type, String name,
                                      String localPath, Long size, String mimeType) {
        WxMaterialEntity m = new WxMaterialEntity();
        m.setAccountId(accountId);
        m.setMediaId(mediaId);
        m.setMaterialType(type);
        m.setName(name);
        m.setUrl("https://wx.qq.com/wxoc/" + mediaId);
        m.setLocalPath(localPath);
        m.setSize(size);
        m.setMimeType(mimeType);
        return m;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> page(String query) {
        ResponseEntity<Map> resp = rest.exchange(url("/api/v1/wx/materials/page" + query),
                HttpMethod.GET, new HttpEntity<>(auth()), Map.class);
        return resp.getBody();
    }

    @Test
    void pageListsNewestFirstWithAndFilters() {
        Map<String, Object> all = page("?current=1&pageSize=50");
        assertTrue(Boolean.TRUE.equals(all.get("success")), "成功标记应为 true：" + all);
        assertEquals(4, ((Number) all.get("total")).intValue(), "应列出全部台账行：" + all);

        var rows = (java.util.List<Map<String, Object>>) all.get("data");
        assertEquals(4, rows.size(), "分页 size 应等于总数：" + rows);
        long firstId = ((Number) rows.get(0).get("id")).longValue();
        long lastId = ((Number) rows.get(rows.size() - 1).get("id")).longValue();
        assertTrue(firstId > lastId, "应最新在前（createTime 倒序，id 倒序兜底）：" + firstId + " -> " + lastId);

        // 台账列齐（含本地副本标记），不含任何密钥列
        Map<String, Object> row = rows.stream().filter(r -> matLocal.equals(((Number) r.get("id")).longValue()))
                .findFirst().orElseThrow();
        assertEquals("m1", row.get("mediaId"));
        assertEquals("temp", row.get("materialType"));
        assertEquals("202610/img1.png", row.get("localPath"));
        assertEquals(PNG_BYTES.length, ((Number) row.get("size")).longValue());
        assertEquals("image/png", row.get("mimeType"));
        assertFalse(row.containsKey("appSecret") || row.containsKey("token") || row.containsKey("aesKey"),
                "台账不得下发密钥列：" + row);

        // AND 语义：账号 + 类型叠加；账号维度互斥
        assertEquals(2, ((Number) page("?current=1&pageSize=50&accountId=" + accountA + "&materialType=temp")
                .get("total")).intValue(), "账号+类型 AND 过滤应命中 2 行");
        assertEquals(1, ((Number) page("?current=1&pageSize=50&accountId=" + accountB)
                .get("total")).intValue(), "账号过滤应互斥");
    }

    @Test
    void servesLocalMaterialWithStoredMimeType() {
        ResponseEntity<byte[]> resp = rest.exchange(url("/api/v1/wx/materials/" + matLocal),
                HttpMethod.GET, new HttpEntity<>(auth()), byte[].class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals("image/png", resp.getHeaders().getContentType().toString());
        assertTrue(java.util.Arrays.equals(PNG_BYTES, resp.getBody()), "应原样返回本地副本字节");
    }

    @Test
    void missingCopyUnknownAndUnreadableMaterialAreRejected() {
        // 无本地副本、本地文件丢失、未知 id：统一 400（ResponseExceptionAdvice 口径），不给 200 空内容
        for (Long id : new Long[]{matRemoteOnly, matNoFile, 999999L}) {
            HttpClientErrorException e = assertThrows(HttpClientErrorException.class,
                    () -> rest.exchange(url("/api/v1/wx/materials/" + id), HttpMethod.GET,
                            new HttpEntity<>(auth()), byte[].class),
                    "应拒绝：" + id);
            assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode(), "应 400：" + id);
        }
    }

    @Test
    void traversalLocalPathIsNotServed() {
        // 台账里被写入 ../ 路径时不得逃出素材根目录（/etc/passwd 存在，正好验证不是"不存在所以没中"）
        HttpClientErrorException e = assertThrows(HttpClientErrorException.class,
                () -> rest.exchange(url("/api/v1/wx/materials/" + matTraversal), HttpMethod.GET,
                        new HttpEntity<>(auth()), byte[].class),
                "越权路径必须拒绝");
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
        assertFalse(e.getResponseBodyAsString().contains("root:x:"), "绝不得透出目录外文件内容");
    }

    @Test
    void unauthenticatedIsRejected() {
        for (String path : new String[]{"/api/v1/wx/materials/page?current=1&pageSize=10",
                "/api/v1/wx/materials/" + matLocal}) {
            HttpClientErrorException e = assertThrows(HttpClientErrorException.class,
                    () -> rest.exchange(url(path), HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), Map.class),
                    "未登录应拒绝：" + path);
            assertTrue(e.getStatusCode() == HttpStatus.UNAUTHORIZED || e.getStatusCode() == HttpStatus.FORBIDDEN,
                    "应 401/403：" + path + " => " + e.getStatusCode());
        }
    }
}
