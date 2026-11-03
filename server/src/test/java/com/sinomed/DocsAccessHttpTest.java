package com.sinomed;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * C11 回归：API 文档面（Swagger/Knife4j）与 Actuator 的 permitAll 按 prod profile 收紧。
 * 默认（无 prod）profile 下 Swagger UI / doc.html / v3 api-docs 不被安全层拦截（非 401）；
 * 注：/v3/api-docs 本身因 springdoc 2.3.0 与 Spring 的既有 NoSuchMethodError 会 500，
 * 本测试只断言安全层放行（不因 401 拦截），不把既存 springdoc 兼容 bug 混入 C11。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.datasource.url=jdbc:sqlite:target/docs-access-http-test-${random.uuid}.db"})
class DocsAccessHttpTest {

    @LocalServerPort
    private int port;

    private final RestTemplate rest = new RestTemplate();

    @Test
    void docsEndpointsNotBlockedBySecurityWithoutProdProfile() {
        assertNotEquals(HttpStatus.UNAUTHORIZED.value(), status("/swagger-ui/index.html").value(),
                "非 prod 下 swagger-ui 不应被安全层 401 拦截");
        assertNotEquals(HttpStatus.UNAUTHORIZED.value(), status("/doc.html").value(),
                "非 prod 下 doc.html 不应被安全层 401 拦截");
        assertNotEquals(HttpStatus.UNAUTHORIZED.value(), status("/v3/api-docs").value(),
                "非 prod 下 v3/api-docs 不应被安全层 401 拦截");
    }

    private HttpStatusCode status(String path) {
        try {
            ResponseEntity<String> r = rest.getForEntity("http://localhost:" + port + path, String.class);
            return r.getStatusCode();
        } catch (HttpStatusCodeException e) {
            return e.getStatusCode();
        }
    }
}
