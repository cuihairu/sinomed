package com.sinomed;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 到店扫码收款码出图 HTTP 回归：缺图 404（页面如实提示未配置，不做假码）→ 目录放图即 200 出字节
 * （png/jpg 按扩展名给 Content-Type）；方法白名单外 404；未登录 401。
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:sqlite:target/qr-http-test-${random.uuid}.db"})
class PayQrCodeHttpTest {

    /** 收款码目录：static + UUID 固定一次。${random.uuid} 占位符每次解析都会生成新值，
     *  controller 与测试的两个 @Value 会拿到两个不同目录——必须经 DynamicPropertySource 供给同一值 */
    private static final Path QR_DIR = Path.of("target", "qr-test-" + UUID.randomUUID());

    @DynamicPropertySource
    static void qrProps(DynamicPropertyRegistry registry) {
        registry.add("sinomed.pay.qrcodes-dir", () -> QR_DIR.toString());
    }

    @LocalServerPort
    private int port;

    private final RestTemplate rest = new RestTemplate();

    private String token() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        var resp = rest.postForEntity(url("/api/v1/user/login"),
                new HttpEntity<>(Map.of("username", "admin", "password", "123"), headers), Map.class);
        return (String) resp.getBody().get("token");
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private ResponseEntity<byte[]> get(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return rest.exchange(url(path), HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
    }

    private int statusOf(String path, String token) {
        try {
            return get(path, token).getStatusCode().value();
        } catch (HttpStatusCodeException e) {
            return e.getStatusCode().value();
        }
    }

    @Test
    void missingImageServes404AndUnknownMethodRejected() {
        String token = token();
        // 目录为空：微信 / 支付宝都 404（页面据此如实提示「未配置」，不做假码）
        assertEquals(404, statusOf("/api/v1/pay-qrcode/wechat", token));
        assertEquals(404, statusOf("/api/v1/pay-qrcode/alipay", token));
        // 白名单外（现金/次卡无码）一律 404，哪怕目录里被塞了同名文件
        assertEquals(404, statusOf("/api/v1/pay-qrcode/cash", token));
    }

    @Test
    void droppedImageServedBytesAndContentType() throws Exception {
        String token = token();
        Path dir = QR_DIR;
        Files.createDirectories(dir);
        // 微信 png：按扩展名给 image/png，字节原样回
        byte[] png = new byte[] {-119, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 13};
        Files.write(dir.resolve("wechat.png"), png);
        ResponseEntity<byte[]> resp = get("/api/v1/pay-qrcode/wechat", token);
        assertEquals(200, resp.getStatusCode().value());
        assertEquals(MediaType.IMAGE_PNG, resp.getHeaders().getContentType());
        assertArrayEquals(png, resp.getBody());
        // 支付宝 jpg：image/jpeg 同口径
        byte[] jpg = new byte[] {-1, -40, -1, -32, 0, 16};
        Files.write(dir.resolve("alipay.jpg"), jpg);
        ResponseEntity<byte[]> resp2 = get("/api/v1/pay-qrcode/alipay", token);
        assertEquals(200, resp2.getStatusCode().value());
        assertEquals(MediaType.IMAGE_JPEG, resp2.getHeaders().getContentType());
        assertArrayEquals(jpg, resp2.getBody());
    }

    @Test
    void unauthenticatedRejected() {
        assertTrue(statusOf("/api/v1/pay-qrcode/wechat", null) == 401
                || statusOf("/api/v1/pay-qrcode/wechat", null) == 403,
                "收款码是商户资产，未登录不得出图");
    }
}
