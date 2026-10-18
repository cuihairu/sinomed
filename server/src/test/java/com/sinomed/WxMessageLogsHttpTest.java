package com.sinomed;

import com.sinomed.entity.WxMessageLogEntity;
import com.sinomed.repository.WxMessageLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 管理端消息流水只读列表 HTTP 回归（设计稿第一期交付物 4：按粉丝/时间/类型过滤，不做对话式 UI）。
 * 只读端点不依赖模块开关——disabled 期间的 reject 审计也要可查（不静默）。走真实 HTTP + JWT。
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:sqlite:target/wx-oa-logs-test-${random.uuid}.db"
})
class WxMessageLogsHttpTest {

    @LocalServerPort
    private int port;

    private final RestTemplate rest = new RestTemplate();

    @Autowired
    private WxMessageLogRepository messageLogRepository;

    private String token;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @BeforeEach
    void seed() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        var resp = rest.postForEntity(url("/api/v1/user/login"),
                new HttpEntity<>(Map.of("username", "admin", "password", "123"), headers), Map.class);
        token = (String) resp.getBody().get("token");

        messageLogRepository.deleteAll();
        log("in", "text", "oFAN1", "营业时间", 9001L, "ok");
        log("out", "text", "oFAN1", "9:00-18:00", null, "ok");
        log("in", "image", "oFAN2", "[图片]", 9002L, "ok");
        log("reject", "none", "oFAN3", "bad_signature", null, "bad_signature");
    }

    private void log(String direction, String msgType, String fromUser, String content, Long msgId, String result) {
        WxMessageLogEntity row = new WxMessageLogEntity();
        row.setAccountId(1L);
        row.setDirection(direction);
        row.setMsgType(msgType);
        row.setFromUser(fromUser);
        row.setToUser("gh_test");
        row.setContent(content);
        row.setMsgId(msgId);
        row.setDedupeKey(direction + ":" + msgId + ":" + fromUser + System.nanoTime());
        row.setResult(result);
        messageLogRepository.save(row);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> page(String query) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return rest.exchange(url("/api/v1/wx/message-logs" + query), HttpMethod.GET,
                new HttpEntity<>(headers), Map.class).getBody();
    }

    @SuppressWarnings("unchecked")
    private java.util.List<Map<String, Object>> rows(Map<String, Object> resp) {
        return (java.util.List<Map<String, Object>>) resp.get("data");
    }

    private long totalOf(Map<String, Object> resp) {
        // JSON 数字经 Jackson 还原为 Integer，与 PageResp.total 的 Long 不同型——断言走 Number 统一口径
        return ((Number) resp.get("total")).longValue();
    }

    @Test
    void pagesAllLogsNewestFirstByDefault() {
        Map<String, Object> resp = page("?current=1&pageSize=10");
        assertEquals(true, resp.get("success"));
        assertEquals(4L, totalOf(resp), "无过滤应返回全量：" + resp);
        assertEquals("reject", rows(resp).get(0).get("direction"), "默认按插入倒序（最新在前）");
        assertEquals("in", rows(resp).get(3).get("direction"), "最早一条应排末尾");
    }

    @Test
    void filtersByFanDirectionAndType() {
        Map<String, Object> byFan = page("?current=1&pageSize=10&fromUser=oFAN1");
        assertEquals(2L, totalOf(byFan), "按粉丝过滤应只留该粉丝的收发两条：" + byFan);

        Map<String, Object> byDirection = page("?current=1&pageSize=10&direction=in");
        assertEquals(2L, totalOf(byDirection), "按方向过滤应只留入站两条：" + byDirection);

        Map<String, Object> byType = page("?current=1&pageSize=10&msgType=image");
        assertEquals(1L, totalOf(byType), "按类型过滤应只留图片一条：" + byType);

        Map<String, Object> combined = page("?current=1&pageSize=10&fromUser=oFAN2&direction=in&msgType=image");
        assertEquals(1L, totalOf(combined), "组合过滤应按 AND 语义命中：" + combined);
        assertEquals("oFAN2", rows(combined).get(0).get("fromUser"));
    }

    @Test
    void timeRangeNarrowsWindow() {
        java.time.format.DateTimeFormatter fmt =
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        // 种库发生在「现在」之前若干毫秒——起点必须留余量，卡秒会误排除同秒早段的行
        String coveringStart = now.minusSeconds(30).format(fmt);
        String coveringEnd = now.plusSeconds(30).format(fmt);
        Map<String, Object> covering = page("?current=1&pageSize=10&startTime=" + coveringStart + "&endTime=" + coveringEnd);
        assertEquals(4L, totalOf(covering), "覆盖窗口应包含全部（createTime 由审计写为保存时刻）：" + covering);

        // 未来/过去窗口验证谓词方向确实生效（漏接过滤会得 4，方向反了会得 0）
        String future = now.plusHours(1).format(fmt);
        Map<String, Object> afterFuture = page("?current=1&pageSize=10&startTime=" + future);
        assertEquals(0L, totalOf(afterFuture), "起点在未来应全被排除：" + afterFuture);

        String past = now.minusHours(1).format(fmt);
        Map<String, Object> beforePast = page("?current=1&pageSize=10&endTime=" + past);
        assertEquals(0L, totalOf(beforePast), "终点在过去应全被排除：" + beforePast);

        // 时间窗与类型过滤 AND 组合：窗内图片消息恰一条
        Map<String, Object> windowAndType = page("?current=1&pageSize=10&startTime=" + coveringStart
                + "&endTime=" + coveringEnd + "&msgType=image");
        assertEquals(1L, totalOf(windowAndType), "时间窗应与类型过滤按 AND 组合：" + windowAndType);
    }

    @Test
    void noMatchReturnsEmptyShape() {
        Map<String, Object> resp = page("?current=1&pageSize=10&fromUser=oGHOST");
        assertEquals(0L, totalOf(resp), "无命中 total 应为 0（不得 null）：" + resp);
        assertEquals(0, ((Number) resp.get("pages")).intValue(), "无命中 pages 应为 0（不得 null）");
        assertTrue(rows(resp).isEmpty(), "无命中 data 应为空数组");
    }

    @Test
    void requiresAuthentication() {
        HttpClientErrorException ex = assertThrows(HttpClientErrorException.class,
                () -> rest.getForEntity(url("/api/v1/wx/message-logs?current=1&pageSize=10"), Map.class));
        assertEquals(401, ex.getStatusCode().value(), "管理端只读列表不得免登录（免登录仅回调端点）");
    }
}
