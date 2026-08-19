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
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:fishing_booking_lifecycle;MODE=MySQL;"
                + "DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class BookingLifecycleIntegrationTest {

    private static final AtomicLong BOOKING_SEQUENCE = new AtomicLong();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock businessClock;

    private final AtomicInteger sequence = new AtomicInteger();

    @Test
    void yesterdayConfirmedBookingCannotBeCancelled() throws Exception {
        MockHttpSession session = authenticatedSession();
        CsrfCredentials csrf = csrf(session);
        long bookingId = insertConfirmedBooking(yesterday(), 2L);

        mockMvc.perform(post("/api/bookings/{id}/cancel", bookingId)
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("垂钓日当天或之前不能取消，请由管理员结单")));

        assertEquals("CONFIRMED", bookingStatus(bookingId));
    }

    @Test
    void yesterdayBookingsCanBeCompletedOrMarkedNoShowButActiveCatchBlocksNoShow() throws Exception {
        MockHttpSession session = authenticatedSession();
        CsrfCredentials csrf = csrf(session);
        long noShowBookingId = insertConfirmedBooking(yesterday(), 2L);
        long caughtBookingId = insertConfirmedBooking(yesterday(), 2L);
        insertActiveCatch(caughtBookingId, yesterday(), 2L);

        mockMvc.perform(post("/api/bookings/{id}/no-show", noShowBookingId)
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("NO_SHOW")));

        mockMvc.perform(post("/api/bookings/{id}/no-show", caughtBookingId)
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("该预订已有有效渔获记录，只能完成结单，不能标记爽约")));

        mockMvc.perform(post("/api/bookings/{id}/complete", caughtBookingId)
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("COMPLETED")));
    }

    @Test
    void futureBookingsCannotBeSettled() throws Exception {
        MockHttpSession session = authenticatedSession();
        CsrfCredentials csrf = csrf(session);
        long completeBookingId = insertConfirmedBooking(today().plusDays(1), 2L);
        long noShowBookingId = insertConfirmedBooking(today().plusDays(1), 2L);

        mockMvc.perform(post("/api/bookings/{id}/complete", completeBookingId)
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("未来预约不能结单")));
        mockMvc.perform(post("/api/bookings/{id}/no-show", noShowBookingId)
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("未来预约不能结单")));
    }

    @Test
    void concurrentSettlementHasExactlyOneWinner() throws Exception {
        long bookingId = insertConfirmedBooking(today(), 2L);
        MockHttpSession completeSession = authenticatedSession();
        MockHttpSession noShowSession = authenticatedSession();
        CsrfCredentials completeCsrf = csrf(completeSession);
        CsrfCredentials noShowCsrf = csrf(noShowSession);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var complete = executor.submit(() -> {
                start.await();
                return mockMvc.perform(post("/api/bookings/{id}/complete", bookingId)
                                .session(completeSession).cookie(completeCsrf.cookie())
                                .header(completeCsrf.headerName(), completeCsrf.token()))
                        .andReturn().getResponse().getStatus();
            });
            var noShow = executor.submit(() -> {
                start.await();
                return mockMvc.perform(post("/api/bookings/{id}/no-show", bookingId)
                                .session(noShowSession).cookie(noShowCsrf.cookie())
                                .header(noShowCsrf.headerName(), noShowCsrf.token()))
                        .andReturn().getResponse().getStatus();
            });
            start.countDown();
            List<Integer> statuses = List.of(
                    complete.get(10, TimeUnit.SECONDS), noShow.get(10, TimeUnit.SECONDS))
                    .stream().sorted().toList();
            assertEquals(List.of(200, 409), statuses);
        } finally {
            executor.shutdownNow();
        }
        String finalStatus = bookingStatus(bookingId);
        org.junit.jupiter.api.Assertions.assertTrue(
                "COMPLETED".equals(finalStatus) || "NO_SHOW".equals(finalStatus));
    }

    @Test
    void resourceShutdownRejectsFutureConfirmedBookings() throws Exception {
        MockHttpSession session = authenticatedSession();
        CsrfCredentials csrf = csrf(session);
        insertConfirmedBooking(today().plusDays(1), 2L);

        String closedSpot = objectMapper.writeValueAsString(Map.of(
                "zoneId", 1,
                "code", "E-02",
                "name", "东岸 2 号位",
                "mapX", 31,
                "mapY", 66,
                "capacity", 2,
                "status", "CLOSED",
                "note", "生命周期保护测试"));
        mockMvc.perform(put("/api/spots/2")
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(closedSpot))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("该钓位存在未来已确认预约，不能停用或调整所属分区")));

        String inactiveZone = objectMapper.writeValueAsString(Map.of(
                "code", "EAST",
                "name", "东岸生态区",
                "description", "生命周期保护测试",
                "status", "INACTIVE"));
        mockMvc.perform(put("/api/zones/1")
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(inactiveZone))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("该分区存在未来已确认预约，不能停用")));

        assertEquals("OPEN", jdbcTemplate.queryForObject(
                "SELECT status FROM fishing_spot WHERE id = 2", String.class));
        assertEquals("ACTIVE", jdbcTemplate.queryForObject(
                "SELECT status FROM fishing_zone WHERE id = 1", String.class));
    }

    @Test
    void historicalStatisticsKeepCompletedBookingsAndExcludeNoShows() throws Exception {
        MockHttpSession session = authenticatedSession();
        CsrfCredentials csrf = csrf(session);
        LocalDate yesterday = yesterday();
        int bookingCountBefore = bookingCountFor(yesterday, session);
        long completeBookingId = insertConfirmedBooking(yesterday, 2L);
        long noShowBookingId = insertConfirmedBooking(yesterday, 2L);

        assertEquals(bookingCountBefore + 2, bookingCountFor(yesterday, session));

        mockMvc.perform(post("/api/bookings/{id}/complete", completeBookingId)
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/bookings/{id}/no-show", noShowBookingId)
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token()))
                .andExpect(status().isOk());

        assertEquals(bookingCountBefore + 1, bookingCountFor(yesterday, session));
    }

    private long insertConfirmedBooking(LocalDate fishingDate, long spotId) {
        String bookingNo = "BK-LIFECYCLE-" + BOOKING_SEQUENCE.incrementAndGet();
        jdbcTemplate.update("""
                INSERT INTO booking
                    (booking_no, spot_id, user_id, fishing_date, time_slot, guests, amount, status, notes)
                VALUES (?, ?, 1, ?, 'MORNING', 1, 0, 'CONFIRMED', '预约生命周期集成测试')
                """, bookingNo, spotId, fishingDate);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM booking WHERE booking_no = ?", Long.class, bookingNo);
        assertNotNull(id);
        return id;
    }

    private void insertActiveCatch(long bookingId, LocalDate fishingDate, long spotId) {
        jdbcTemplate.update("""
                INSERT INTO catch_record
                    (catch_no, booking_id, spot_id, fishing_date, species, weight, quantity, status)
                VALUES (?, ?, ?, ?, '鲫鱼', ?, 1, 'RECORDED')
                """, "CR-LIFECYCLE-" + sequence.incrementAndGet(), bookingId, spotId,
                fishingDate, BigDecimal.ONE);
    }

    private int bookingCountFor(LocalDate date, MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/analytics/traffic")
                        .session(session)
                        .param("days", "2"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode series = objectMapper.readTree(result.getResponse().getContentAsByteArray())
                .at("/data/series");
        for (JsonNode point : series) {
            if (date.toString().equals(point.get("statDate").asText())) {
                return point.get("bookingCount").asInt();
            }
        }
        throw new AssertionError("客流统计中缺少日期 " + date);
    }

    private String bookingStatus(long bookingId) {
        return jdbcTemplate.queryForObject("SELECT status FROM booking WHERE id = ?", String.class, bookingId);
    }

    private LocalDate today() {
        return LocalDate.now(businessClock);
    }

    private LocalDate yesterday() {
        return today().minusDays(1);
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
