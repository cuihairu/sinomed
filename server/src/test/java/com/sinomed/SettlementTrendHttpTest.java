package com.sinomed;

import com.sinomed.entity.CustomerEntity;
import com.sinomed.entity.SettlementEntity;
import com.sinomed.entity.TreatEntity;
import com.sinomed.repository.CustomerRepository;
import com.sinomed.repository.SettlementRepository;
import com.sinomed.repository.TreatRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 经营趋势 HTTP 回归：GET /api/v1/settlement/report/trend 近 N 月逐月聚合
 * （实收/单数/次卡核销/接诊量，无数据月零填充升序）；月数越界 400，未登录 401。
 * 结算/接诊时间经原生 SQL 回写历史日期（审计只写当下，同 SettlementReportTest）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.datasource.url=jdbc:sqlite:target/settle-trend-http-test-${random.uuid}.db"})
class SettlementTrendHttpTest {

    @LocalServerPort
    private int port;

    @Autowired
    private SettlementRepository settlementRepository;

    @Autowired
    private TreatRepository treatRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

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

    private HttpEntity<Void> authGet(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
    }

    private int statusOf(String path, String token) {
        try {
            return rest.exchange(url(path), HttpMethod.GET, authGet(token), Map.class)
                    .getStatusCode().value();
        } catch (HttpStatusCodeException e) {
            return e.getStatusCode().value();
        }
    }

    /** 建结算单并把结算时间回写到指定日期（审计只写当下，走原生 SQL 回写） */
    private void newSettlement(long orderId, int payType, int money, LocalDate day) {
        SettlementEntity settlement = new SettlementEntity();
        settlement.setOrderId(orderId);
        settlement.setUserId(orderId);
        settlement.setPayType(payType);
        settlement.setMoney(money);
        settlementRepository.saveAndFlush(settlement);
        java.util.Date when = java.util.Date.from(day.atTime(12, 0).atZone(java.time.ZoneId.systemDefault()).toInstant());
        jdbcTemplate.update("UPDATE settlements SET create_time = ?, update_time = ? WHERE id = ?",
                new Timestamp(when.getTime()), new Timestamp(when.getTime()), settlement.getId());
    }

    /** 建接诊单并把时间回写到指定日期 */
    private void newTreat(long customerId, LocalDate day) {
        TreatEntity treat = new TreatEntity();
        treat.setCustomerId(customerId);
        treatRepository.saveAndFlush(treat);
        java.util.Date when = java.util.Date.from(day.atTime(10, 0).atZone(java.time.ZoneId.systemDefault()).toInstant());
        jdbcTemplate.update("UPDATE treats SET create_time = ?, update_time = ? WHERE id = ?",
                new Timestamp(when.getTime()), new Timestamp(when.getTime()), treat.getId());
        entityManager.clear(); // 让后续查询从库里重读回写后的时间
    }

    @Test
    void trendAggregatesMoneyCountsAndTreats() {
        String token = token();
        CustomerEntity customer = new CustomerEntity();
        customer.setName("趋势顾客");
        customer.setPhone("13700033301");
        long customerId = customerRepository.save(customer).getId();

        LocalDate prev = LocalDate.now().minusMonths(1);
        LocalDate old = LocalDate.now().minusMonths(3);
        newSettlement(900001, 4, 100, prev.withDayOfMonth(10)); // 现金
        newSettlement(900002, 5, 250, prev.withDayOfMonth(20)); // 次卡核销：单数计入、实收 250（回写后按落库值聚合）
        newSettlement(900003, 4, 50, old.withDayOfMonth(5));
        newTreat(customerId, prev.withDayOfMonth(12));
        newTreat(customerId, prev.withDayOfMonth(22));
        newTreat(customerId, LocalDate.now());

        Map<?, ?> trend = rest.exchange(url("/api/v1/settlement/report/trend?months=6"),
                HttpMethod.GET, authGet(token), Map.class).getBody();
        assertEquals(6, ((Number) trend.get("months")).intValue());
        List<?> rows = (List<?>) trend.get("rows");
        assertEquals(6, rows.size(), "6 个月应出 6 行零填充");
        assertEquals(LocalDate.now().minusMonths(5).toString().substring(0, 7),
                ((Map<?, ?>) rows.get(0)).get("month"), "首行应为最早月");

        String prevKey = prev.toString().substring(0, 7);
        Map<?, ?> prevRow = rows.stream().map(r -> (Map<?, ?>) r)
                .filter(r -> prevKey.equals(r.get("month"))).findFirst().orElseThrow();
        assertEquals(350, ((Number) prevRow.get("money")).intValue(), "上月实收=100+250");
        assertEquals(2, ((Number) prevRow.get("count")).intValue(), "上月单数");
        assertEquals(1, ((Number) prevRow.get("cardCount")).intValue(), "次卡核销单数");
        assertEquals(2, ((Number) prevRow.get("treatCount")).intValue(), "上月接诊量");

        String oldKey = old.toString().substring(0, 7);
        Map<?, ?> oldRow = rows.stream().map(r -> (Map<?, ?>) r)
                .filter(r -> oldKey.equals(r.get("month"))).findFirst().orElseThrow();
        assertEquals(50, ((Number) oldRow.get("money")).intValue());
        assertEquals(0, ((Number) oldRow.get("treatCount")).intValue());

        String curKey = LocalDate.now().toString().substring(0, 7);
        Map<?, ?> curRow = rows.stream().map(r -> (Map<?, ?>) r)
                .filter(r -> curKey.equals(r.get("month"))).findFirst().orElseThrow();
        assertEquals(1, ((Number) curRow.get("treatCount")).intValue(), "当月接诊量");
        assertEquals(0, ((Number) curRow.get("money")).intValue(), "当月无结算");
    }

    @Test
    void trendGuardsRejectInvalidMonthsAndAnonymous() {
        String token = token();
        assertEquals(400, statusOf("/api/v1/settlement/report/trend?months=0", token));
        assertEquals(400, statusOf("/api/v1/settlement/report/trend?months=25", token));
        // 缺省月数（不传 months）
        assertEquals(200, statusOf("/api/v1/settlement/report/trend", token));
        boolean threw = false;
        try {
            rest.exchange(url("/api/v1/settlement/report/trend"), HttpMethod.GET, HttpEntity.EMPTY, Map.class);
        } catch (Exception e) {
            threw = true;
        }
        assertTrue(threw, "未登录应 401");
    }
}
