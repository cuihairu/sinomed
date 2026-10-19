package com.sinomed;

import com.sinomed.entity.WxAccountEntity;
import com.sinomed.entity.WxMaterialEntity;
import com.sinomed.repository.WxAccountRepository;
import com.sinomed.repository.WxMaterialRepository;
import com.sinomed.repository.WxMessageLogRepository;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.net.InetSocketAddress;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 图片消息素材拉取 HTTP 回归（设计稿 §4.5 第一期口径：收到图片消息下载 PicUrl 到 local_path 落
 * wx_materials 台账，type=temp，供管理端查看）。下载源用 JDK 内置 HttpServer 起本地站，零外网；
 * 下载失败/超限不得放大——回调仍 200 走兜底（微信 5s 约束），台账不落行。
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
        "sinomed.official-account.enabled=true",
        "sinomed.official-account.token=TESTTOKEN"
})
class WxMaterialCaptureHttpTest {

    // random.uuid 每次解析都是新值（测试 @Value 与服务 @Value 会各得一个 uuid），此处静态固定一次
    private static final String DB_URL = "jdbc:sqlite:target/wx-material-capture-test-" + UUID.randomUUID() + ".db";
    private static final String MATERIAL_DIR = "target/wx-material-capture-files-" + UUID.randomUUID();

    @DynamicPropertySource
    static void pinPaths(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DB_URL);
        // 64B 上限让「超限跳过」可在毫秒级构造
        registry.add("sinomed.official-account.material-dir", () -> MATERIAL_DIR);
        registry.add("sinomed.official-account.material-max-bytes", () -> "64");
    }

    @LocalServerPort
    private int port;

    @Value("${sinomed.official-account.material-dir}")
    private String materialDir;

    private final RestTemplate rest = new RestTemplate();

    @Autowired
    private WxAccountRepository accountRepository;
    @Autowired
    private WxMaterialRepository materialRepository;
    @Autowired
    private WxMessageLogRepository messageLogRepository;

    private long accountId;

    private static final byte[] TINY_JPEG = {(byte) 0xFF, (byte) 0xD8, 0x00, 0x01, 0x02, 0x03};

    private HttpServer mediaServer;
    private final AtomicInteger mediaHits = new AtomicInteger();

    @BeforeEach
    void startMediaServer() throws Exception {
        mediaServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        mediaServer.createContext("/", exchange -> {
            mediaHits.incrementAndGet();
            byte[] body = exchange.getRequestURI().getPath().contains("/big")
                    ? new byte[100] : TINY_JPEG;
            exchange.getResponseHeaders().set("Content-Type", "image/jpeg");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        mediaServer.start();
    }

    @AfterEach
    void stopMediaServer() {
        if (mediaServer != null) {
            mediaServer.stop(0);
        }
    }

    private String mediaUrl(String path) {
        return "http://localhost:" + mediaServer.getAddress().getPort() + path;
    }

    private String sign(String timestamp, String nonce) throws Exception {
        String joined = Arrays.stream(new String[]{"TESTTOKEN", timestamp, nonce}).sorted()
                .collect(Collectors.joining());
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest(joined.getBytes(StandardCharsets.UTF_8))) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private String postImage(String fromUser, long msgId, String mediaId, String picUrl) throws Exception {
        String xml = "<xml><ToUserName><![CDATA[gh_test]]></ToUserName>"
                + "<FromUserName><![CDATA[" + fromUser + "]]></FromUserName>"
                + "<CreateTime>1760000100</CreateTime><MsgType><![CDATA[image]]></MsgType>"
                + "<PicUrl><![CDATA[" + picUrl + "]]></PicUrl>"
                + "<MediaId><![CDATA[" + mediaId + "]]></MediaId>"
                + "<MsgId>" + msgId + "</MsgId></xml>";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "xml", StandardCharsets.UTF_8));
        return rest.exchange("http://localhost:" + port + "/api/v1/wx/callback/" + accountId
                        + "?signature=" + sign("1700000000", "n" + msgId) + "&timestamp=1700000000&nonce=n" + msgId,
                HttpMethod.POST, new HttpEntity<>(xml, headers), String.class).getBody();
    }

    @BeforeEach
    void seed() throws Exception {
        materialRepository.deleteAll();
        messageLogRepository.deleteAll();
        accountRepository.deleteAll();
        // 目录跨用例共享（同一 context），清空后「超限不落盘」断言才与执行顺序无关
        Path dir = Paths.get(MATERIAL_DIR);
        if (Files.exists(dir)) {
            Files.walk(dir).sorted(Comparator.reverseOrder())
                    .map(Path::toFile).forEach(File::delete);
        }
        WxAccountEntity account = new WxAccountEntity();
        account.setAppId("wxOAMAT" + System.nanoTime());
        account.setAccountType("service");
        account.setVerified(1);
        accountId = accountRepository.save(account).getId();
    }

    @Test
    void imageMessageDownloadsPicUrlAndRecordsMaterial() throws Exception {
        String reply = postImage("oFANIMG", 71001L, "MEDIA_IMG_1", mediaUrl("/pic.jpg"));
        assertEquals("success", reply, "无规则配置应回 success：" + reply);
        assertEquals(1, mediaHits.get(), "PicUrl 应被下载一次");

        List<WxMaterialEntity> rows = materialRepository.findAll();
        assertEquals(1, rows.size(), "素材台账应落一行：" + rows);
        WxMaterialEntity row = rows.get(0);
        assertEquals("MEDIA_IMG_1", row.getMediaId());
        assertEquals("temp", row.getMaterialType(), "消息内素材按临时素材类型登记");
        assertEquals(TINY_JPEG.length, row.getSize());
        assertEquals("image/jpeg", row.getMimeType());
        assertTrue(row.getUrl().endsWith("/pic.jpg"), "PicUrl 应留台账");
        assertTrue(row.getLocalPath() != null && !row.getLocalPath().isBlank(), "local_path 应落相对路径");

        Path onDisk = Paths.get(materialDir, row.getLocalPath());
        assertTrue(Files.exists(onDisk), "文件应落盘：" + onDisk);
        assertTrue(Arrays.equals(TINY_JPEG, Files.readAllBytes(onDisk)), "落盘内容应与源一致");

        assertEquals(1, messageLogRepository.findAll().stream()
                .filter(l -> "in".equals(l.getDirection()) && "image".equals(l.getMsgType())).count(),
                "入站流水照常落");
    }

    @Test
    void duplicateMediaIdSkipsRedownload() throws Exception {
        postImage("oFANIMG", 71002L, "MEDIA_IMG_2", mediaUrl("/pic.jpg"));
        // 同 MediaId 重推（换 MsgId 绕开消息排重，模拟微信 5s 超时重试新消息号）
        postImage("oFANIMG", 71003L, "MEDIA_IMG_2", mediaUrl("/pic.jpg"));
        assertEquals(1, mediaHits.get(), "同 (account, media_id) 不得重复下载");
        assertEquals(1, materialRepository.count(), "台账不重复落行");
    }

    @Test
    void downloadFailureKeepsCallbackGreen() throws Exception {
        // 指向必然拒连的端口：下载失败不得影响回调（仍 200 + success，不落台账）
        String reply = postImage("oFANIMG", 71004L, "MEDIA_IMG_3", "http://localhost:1/pic.jpg");
        assertEquals("success", reply, "下载失败回调仍应正常响应：" + reply);
        assertEquals(0, materialRepository.count(), "失败不得落台账行");
        assertEquals(1, messageLogRepository.findAll().stream()
                .filter(l -> "in".equals(l.getDirection())).count(), "入站流水照常落");
    }

    @Test
    void oversizedDownloadSkippedNotFatal() throws Exception {
        String reply = postImage("oFANIMG", 71005L, "MEDIA_IMG_4", mediaUrl("/big.jpg"));
        assertEquals("success", reply, "超限素材回调仍应正常：" + reply);
        assertEquals(0, materialRepository.count(), "超限素材不落台账（上限 64B，源 100B）");
        assertFalse(Files.exists(Paths.get(materialDir)), "超限素材不得落盘");
    }
}
