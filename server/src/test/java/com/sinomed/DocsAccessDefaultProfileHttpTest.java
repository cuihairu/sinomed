package com.sinomed;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * C11 回归：prod 通过 spring.profiles.default 激活时收紧不得静默失效。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:sqlite:target/docs-access-default-prod-http-test-${random.uuid}.db",
        "spring.profiles.default=prod"
})
class DocsAccessDefaultProfileHttpTest {

    @LocalServerPort
    private int port;

    private final RestTemplate rest = new RestTemplate();

    @Test
    void docsEndpointsRequireAuthWhenProdIsDefaultProfile() {
        assertUnauthorized("/swagger-ui/index.html");
        assertUnauthorized("/doc.html");
        assertUnauthorized("/v3/api-docs");
    }

    private void assertUnauthorized(String path) {
        try {
            ResponseEntity<String> response = rest.getForEntity("http://localhost:" + port + path, String.class);
            assertEquals(HttpStatus.UNAUTHORIZED.value(), response.getStatusCode().value(),
                    "prod default profile 下 " + path + " 未登录应 401");
        } catch (HttpStatusCodeException e) {
            HttpStatusCode status = e.getStatusCode();
            assertEquals(HttpStatus.UNAUTHORIZED.value(), status.value(),
                    "prod default profile 下 " + path + " 应 401，实际 " + status);
        }
    }
}
