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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:fishing_statistics;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class StatisticsConsistencyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void dashboardCardsAndTrafficTodayUseTheSameLiveFacts() throws Exception {
        MockHttpSession session = authenticatedSession();
        JsonNode dashboard = data(getJson("/api/dashboard/summary", session));
        JsonNode analytics = data(getJson("/api/analytics/traffic?days=1", session));
        JsonNode today = analytics.get("series").get(0);
        JsonNode lastTrendPoint = dashboard.get("trafficTrend")
                .get(dashboard.get("trafficTrend").size() - 1);

        assertEquals(today.get("visits").asLong(), dashboard.get("visitorsToday").asLong());
        assertEquals(today.get("visits").asLong(), lastTrendPoint.get("value").asLong());
        assertEquals(today.get("bookingCount").asLong(), dashboard.get("todayBookings").asLong());
        assertEquals(0, today.get("revenue").decimalValue()
                .compareTo(dashboard.get("todayRevenue").decimalValue()));

        assertEquals(2, today.get("bookingCount").asLong(), "预约数应来自未取消的预约事实");
        assertEquals(3, today.get("newMembers").asLong(), "新会员数应来自会员创建事实");
        assertEquals(0, BigDecimal.ZERO.compareTo(today.get("revenue").decimalValue()),
                "未确认到账前今日营收应为零");
    }

    @Test
    void openSpotMetricExcludesSpotsInsideInactiveZones() throws Exception {
        MockHttpSession session = authenticatedSession();
        jdbcTemplate.update("UPDATE fishing_zone SET status = 'INACTIVE' WHERE id = 2");
        try {
            Long expected = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM fishing_spot s
                    JOIN fishing_zone z ON z.id = s.zone_id
                    WHERE s.status = 'OPEN' AND z.status = 'ACTIVE'
                    """, Long.class);
            JsonNode dashboard = data(getJson("/api/dashboard/summary", session));
            assertEquals(expected, dashboard.get("openSpots").asLong());
        } finally {
            jdbcTemplate.update("UPDATE fishing_zone SET status = 'ACTIVE' WHERE id = 2");
        }
    }

    @Test
    void demoDatabaseSessionUsesShanghaiCalendarAtUtcBoundary() {
        Integer timezoneHour = jdbcTemplate.queryForObject(
                "SELECT EXTRACT(TIMEZONE_HOUR FROM CURRENT_TIMESTAMP)", Integer.class);
        LocalDate shanghaiDate = jdbcTemplate.queryForObject(
                "SELECT CAST(TIMESTAMP WITH TIME ZONE '2026-08-07 16:30:00+00:00' AS DATE)",
                LocalDate.class);

        assertEquals(8, timezoneHour);
        assertEquals(LocalDate.of(2026, 8, 8), shanghaiDate);
    }

    private JsonNode getJson(String path, MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(get(path).session(session))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private JsonNode data(JsonNode response) {
        return response.get("data");
    }

    private MockHttpSession authenticatedSession() throws Exception {
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

    private CsrfCredentials csrf(MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf").session(session))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsByteArray()).get("data");
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(cookie);
        return new CsrfCredentials(data.get("headerName").asText(), data.get("token").asText(), cookie);
    }

    private record CsrfCredentials(String headerName, String token, Cookie cookie) {
    }
}
