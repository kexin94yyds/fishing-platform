package com.fishing.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:fishing_traffic_daily;MODE=MySQL;"
                + "DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class TrafficDailyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock businessClock;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final AtomicInteger sequence = new AtomicInteger();

    @Test
    void operatorCanReadButCannotWriteTrafficDailyEntries() throws Exception {
        MockHttpSession operator = registerOperator();
        CsrfCredentials csrf = csrf(operator);

        mockMvc.perform(get("/api/traffic-daily").session(operator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        mockMvc.perform(put("/api/traffic-daily/{date}", today())
                        .session(operator).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(trafficJson(21, 17, "运营员尝试", 0L)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCreatesUpdatesAndRejectsStaleTrafficDailyWrites() throws Exception {
        MockHttpSession admin = authenticatedAdmin();
        CsrfCredentials csrf = csrf(admin);
        LocalDate date = today().minusDays(14);
        jdbcTemplate.update("DELETE FROM traffic_daily WHERE stat_date = ?", date);

        mockMvc.perform(put("/api/traffic-daily/{date}", date)
                        .session(admin).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(trafficJson(37, 28, "主入口人工计数", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.statDate", is(date.toString())))
                .andExpect(jsonPath("$.data.visits", is(37)))
                .andExpect(jsonPath("$.data.uniqueVisitors", is(28)))
                .andExpect(jsonPath("$.data.notes", is("主入口人工计数")))
                .andExpect(jsonPath("$.data.version", is(0)))
                .andExpect(jsonPath("$.data.createdBy", is(1)))
                .andExpect(jsonPath("$.data.updatedBy", is(1)));

        mockMvc.perform(put("/api/traffic-daily/{date}", date)
                        .session(admin).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(trafficJson(99, 90, "不能冒充新建覆盖版本零", null)))
                .andExpect(status().isConflict());
        assertEquals(37, jdbcTemplate.queryForObject(
                "SELECT visits FROM traffic_daily WHERE stat_date = ?", Integer.class, date));

        mockMvc.perform(put("/api/traffic-daily/{date}", date)
                        .session(admin).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(trafficJson(42, 31, "复核后更新", 0L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version", is(1)))
                .andExpect(jsonPath("$.data.visits", is(42)));

        JsonNode recentEntries = responseData(mockMvc.perform(get("/api/traffic-daily")
                        .session(admin).param("days", "30"))
                .andExpect(status().isOk()).andReturn());
        JsonNode retrieved = pointFor(recentEntries, date);
        assertEquals(1, retrieved.get("version").asInt());
        assertEquals("复核后更新", retrieved.get("notes").asText());

        mockMvc.perform(put("/api/traffic-daily/{date}", date)
                        .session(admin).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(trafficJson(99, 90, "陈旧覆盖", 0L)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("客流日汇总已被其他操作修改，请刷新后重试")));

        assertEquals(42, jdbcTemplate.queryForObject(
                "SELECT visits FROM traffic_daily WHERE stat_date = ?", Integer.class, date));
        assertEquals(31, jdbcTemplate.queryForObject(
                "SELECT unique_visitors FROM traffic_daily WHERE stat_date = ?", Integer.class, date));
    }

    @Test
    void rejectsFutureDateAndInvalidUniqueVisitorRange() throws Exception {
        MockHttpSession admin = authenticatedAdmin();
        CsrfCredentials csrf = csrf(admin);

        mockMvc.perform(put("/api/traffic-daily/{date}", today().plusDays(1))
                        .session(admin).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(trafficJson(10, 8, null, 0L)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("不能录入未来日期的客流数据")));

        mockMvc.perform(put("/api/traffic-daily/{date}", today().minusDays(15))
                        .session(admin).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(trafficJson(8, 9, null, 0L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("独立访客数不能大于到访人数")));
    }

    @Test
    void analyticsUsesManualVisitsButKeepsOtherMetricsAsLiveBusinessFacts() throws Exception {
        MockHttpSession admin = authenticatedAdmin();
        CsrfCredentials csrf = csrf(admin);
        LocalDate date = today().minusDays(1);
        jdbcTemplate.update("DELETE FROM traffic_daily WHERE stat_date = ?", date);
        insertLiveFacts(date);

        mockMvc.perform(put("/api/traffic-daily/{date}", date)
                        .session(admin).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(trafficJson(73, 59, "闸机导出", null)))
                .andExpect(status().isOk());

        JsonNode series = responseData(mockMvc.perform(get("/api/analytics/traffic")
                        .session(admin).param("days", "2"))
                .andExpect(status().isOk()).andReturn()).get("series");
        JsonNode point = pointFor(series, date);
        assertEquals(73, point.get("visits").asInt());
        assertEquals(59, point.get("uniqueVisitors").asInt());
        assertEquals(1, point.get("newMembers").asInt());
        assertEquals(1, point.get("bookingCount").asInt());
        assertEquals(1, point.get("salesOrderCount").asInt());
        assertEquals(3, point.get("productQuantity").asInt());
        assertEquals(0, BigDecimal.valueOf(45.50).compareTo(point.get("revenue").decimalValue()));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT new_members FROM traffic_daily WHERE stat_date = ?", Integer.class, date));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT booking_count FROM traffic_daily WHERE stat_date = ?", Integer.class, date));
        assertEquals(0, BigDecimal.ZERO.compareTo(jdbcTemplate.queryForObject(
                "SELECT revenue FROM traffic_daily WHERE stat_date = ?", BigDecimal.class, date)));
    }

    private void insertLiveFacts(LocalDate date) {
        int index = sequence.incrementAndGet();
        jdbcTemplate.update("""
                INSERT INTO member (member_no, name, phone, level, points, status, created_at)
                VALUES (?, ?, ?, 'NORMAL', 0, 'ACTIVE', ?)
                """, "M-TRAFFIC-" + index, "客流事实会员", "1370000" + String.format("%04d", index),
                date.atTime(9, 0));
        jdbcTemplate.update("""
                INSERT INTO booking
                    (booking_no, spot_id, user_id, fishing_date, time_slot, guests, amount, status, notes)
                VALUES (?, 1, 1, ?, 'MORNING', 1, 0, 'COMPLETED', '客流事实预约')
                """, "BK-TRAFFIC-" + index, date);
        String orderNo = "SO-TRAFFIC-" + index;
        jdbcTemplate.update("""
                INSERT INTO sales_order
                    (order_no, total_amount, status, payment_status, created_by, created_at)
                VALUES (?, 45.50, 'COMPLETED', 'PAID', 1, ?)
                """, orderNo, date.atTime(10, 0));
        Long orderId = jdbcTemplate.queryForObject(
                "SELECT id FROM sales_order WHERE order_no = ?", Long.class, orderNo);
        jdbcTemplate.update("""
                INSERT INTO sales_order_item
                    (order_id, product_id, product_name, quantity, unit_price, line_amount)
                VALUES (?, 1, '客流事实商品', 3, 15.00, 45.00)
                """, orderId);
        jdbcTemplate.update("""
                INSERT INTO payment
                    (payment_no, business_type, business_id, amount, method, status, confirmed_at)
                VALUES (?, 'SALES_ORDER', ?, ?, 'CASH', 'PAID', ?)
                """, "PAY-TRAFFIC-" + index, orderId, new BigDecimal("45.50"), date.atTime(10, 0));
    }

    private JsonNode pointFor(JsonNode series, LocalDate date) {
        for (JsonNode point : series) {
            if (date.toString().equals(point.get("statDate").asText())) return point;
        }
        throw new AssertionError("客流统计中缺少日期 " + date);
    }

    private String trafficJson(int visits, int uniqueVisitors, String notes, Long expectedVersion) throws Exception {
        var payload = new java.util.LinkedHashMap<String, Object>();
        payload.put("visits", visits);
        payload.put("uniqueVisitors", uniqueVisitors);
        if (notes != null) payload.put("notes", notes);
        if (expectedVersion != null) payload.put("expectedVersion", expectedVersion);
        return objectMapper.writeValueAsString(payload);
    }

    private LocalDate today() {
        return LocalDate.now(businessClock);
    }

    private JsonNode responseData(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray()).get("data");
    }

    private MockHttpSession authenticatedAdmin() throws Exception {
        MockHttpSession session = new MockHttpSession();
        CsrfCredentials csrf = csrf(session);
        mockMvc.perform(post("/api/auth/login")
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk());
        return session;
    }

    private MockHttpSession registerOperator() throws Exception {
        String username = "traffic_operator_" + sequence.incrementAndGet();
        String password = "Operator123";
        jdbcTemplate.update("""
                INSERT INTO app_user (username, password_hash, display_name, role, enabled)
                VALUES (?, ?, '客流运营员', 'OPERATOR', TRUE)
                """, username, passwordEncoder.encode(password));
        MockHttpSession session = new MockHttpSession();
        CsrfCredentials csrf = csrf(session);
        mockMvc.perform(post("/api/auth/login")
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role", is("OPERATOR")));
        return session;
    }

    private CsrfCredentials csrf(MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf").session(session))
                .andExpect(status().isOk()).andReturn();
        JsonNode data = responseData(result);
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(cookie);
        return new CsrfCredentials(data.get("headerName").asText(), data.get("token").asText(), cookie);
    }

    private record CsrfCredentials(String headerName, String token, Cookie cookie) {
    }
}
