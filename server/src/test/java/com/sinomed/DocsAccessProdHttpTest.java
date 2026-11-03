package com.sinomed;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C11 回归（prod profile）：API 文档面（Swagger/Knife4j）与 Actuator 不再 permitAll——
 * 未登录访问 /swagger-ui/index.html、/doc.html、/v3/api-docs 应 401（anyRequest 需认证），
 * 生产面不再暴露。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:sqlite:target/docs-access-prod-http-test-${random.uuid}.db",
        "spring.profiles.active=prod"
})
class DocsAccessProdHttpTest {

    @LocalServerPort
    private int port;

    private final RestTemplate rest = new RestTemplate();

    @Test
    void docsEndpointsRequireAuthUnderProd() {
        assertUnauthorized("/swagger-ui/index.html");
        assertUnauthorized("/doc.html");
        assertUnauthorized("/v3/api-docs");
    }

    private void assertUnauthorized(String path) {
        try {
            rest.getForEntity("http://localhost:" + port + path, String.class);
            throw new AssertionError("prod 下 " + path + " 未登录应 401，却可达");
        } catch (HttpClientErrorException e) {
            assertTrue(e.getStatusCode().value() == 401, "prod 下 " + path + " 应 401，实际 " + e.getStatusCode());
        }
    }
}
