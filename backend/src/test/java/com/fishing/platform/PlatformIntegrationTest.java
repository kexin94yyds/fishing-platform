package com.fishing.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PlatformIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @Order(1)
    void loginAuthorizationSessionFixationAndPostLoginCsrfWork() throws Exception {
        mockMvc.perform(get("/api/dashboard/summary"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.timestamp").isString());

        MockHttpSession session = new MockHttpSession();
        String anonymousSessionId = session.getId();
        login(session);
        assertNotEquals(anonymousSessionId, session.getId(),
                "认证成功时应更换已有 session id，防止 session fixation");

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username", is("admin")))
                .andExpect(jsonPath("$.data.role", is("ADMIN")));

        mockMvc.perform(get("/api/dashboard/summary").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.visitorsToday", is(186)))
                .andExpect(jsonPath("$.data.trafficTrend", hasSize(7)))
                .andExpect(jsonPath("$.data.bookingMix[0].name", is("CONFIRMED")))
                .andExpect(jsonPath("$.data.recentBookings[0].bookingNo", is("BK-SEED-002")));

        CsrfCredentials csrf = csrf(session);
        mockMvc.perform(post("/api/bookings")
                        .session(session)
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.data").value(nullValue()));

        mockMvc.perform(get("/api/bookings")
                        .session(session)
                        .param("date", "bad"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.timestamp").isString());

        mockMvc.perform(get("/api/products/abc").session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    @Order(2)
    void duplicateBookingIsRejectedByAtomicSlotInventoryUpdate() throws Exception {
        MockHttpSession session = authenticatedSession();
        CsrfCredentials csrf = csrf(session);
        String request = objectMapper.writeValueAsString(Map.of(
                "spotId", 2,
                "fishingDate", "2099-12-30",
                "timeSlot", "MORNING",
                "guests", 2,
                "notes", "集成测试"
        ));

        mockMvc.perform(post("/api/bookings")
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.status", is("CONFIRMED")));

        mockMvc.perform(post("/api/bookings")
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    @Order(3)
    void salesOrderRollsBackWhenStockIsInsufficient() throws Exception {
        MockHttpSession session = authenticatedSession();
        CsrfCredentials csrf = csrf(session);
        String request = objectMapper.writeValueAsString(Map.of(
                "memberId", 1,
                "items", List.of(
                        Map.of("productId", 1, "quantity", 1),
                        Map.of("productId", 4, "quantity", 999)
                ),
                "paymentMethod", "CASH"
        ));

        mockMvc.perform(post("/api/sales-orders")
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("商品“4.5 米综合竿”库存不足"))
                .andExpect(jsonPath("$.data").value(nullValue()));

        mockMvc.perform(get("/api/products").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].stockQuantity", is(78)))
                .andExpect(jsonPath("$.data[3].stockQuantity", is(8)));
    }

    @Test
    @Order(4)
    void confirmingPaymentAdvancesSalesOrderStatus() throws Exception {
        MockHttpSession session = authenticatedSession();
        CsrfCredentials csrf = csrf(session);
        String request = objectMapper.writeValueAsString(Map.of(
                "memberId", 2,
                "items", List.of(Map.of("productId", 3, "quantity", 2)),
                "paymentMethod", "WECHAT"
        ));

        MvcResult created = mockMvc.perform(post("/api/sales-orders")
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.order.status", is("PENDING_PAYMENT")))
                .andExpect(jsonPath("$.data.payment.status", is("PENDING")))
                .andReturn();
        long paymentId = objectMapper.readTree(created.getResponse().getContentAsByteArray())
                .at("/data/payment/id").asLong();

        CsrfCredentials refreshedCsrf = csrf(session);
        mockMvc.perform(post("/api/payments/{id}/confirm", paymentId)
                        .session(session).cookie(refreshedCsrf.cookie())
                        .header(refreshedCsrf.headerName(), refreshedCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"WECHAT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("PAID")))
                .andExpect(jsonPath("$.data.confirmedAt").isString());

        mockMvc.perform(get("/api/sales-orders").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status", is("COMPLETED")))
                .andExpect(jsonPath("$.data[0].paymentStatus", is("PAID")));
    }

    @Test
    @Order(5)
    void historicalAvailableInventoryCannotBypassClosedSpot() throws Exception {
        MockHttpSession session = authenticatedSession();
        CsrfCredentials csrf = csrf(session);
        String booking = objectMapper.writeValueAsString(Map.of(
                "spotId", 3,
                "fishingDate", "2099-12-29",
                "timeSlot", "EVENING",
                "guests", 1
        ));
        MvcResult created = mockMvc.perform(post("/api/bookings")
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(booking))
                .andExpect(status().isCreated())
                .andReturn();
        long bookingId = objectMapper.readTree(created.getResponse().getContentAsByteArray())
                .at("/data/id").asLong();

        mockMvc.perform(post("/api/bookings/{id}/cancel", bookingId)
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("CANCELLED")));

        String closedSpot = objectMapper.writeValueAsString(Map.of(
                "zoneId", 2,
                "code", "N-01",
                "name", "竞技 1 号位",
                "mapX", 67,
                "mapY", 28,
                "capacity", 1,
                "status", "CLOSED",
                "note", "集成测试临时关闭"
        ));
        mockMvc.perform(put("/api/spots/3")
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(closedSpot))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("CLOSED")));

        mockMvc.perform(post("/api/bookings")
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(booking))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    @Order(6)
    void nonCanonicalTimeSlotIsRejectedBeforeInventoryCreation() throws Exception {
        MockHttpSession session = authenticatedSession();
        CsrfCredentials csrf = csrf(session);
        String request = objectMapper.writeValueAsString(Map.of(
                "spotId", 1,
                "fishingDate", "2099-12-28",
                "timeSlot", "morning",
                "guests", 1
        ));

        mockMvc.perform(post("/api/bookings")
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message").value("timeSlot: 必须是 MORNING、AFTERNOON 或 EVENING"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    @Order(7)
    void concurrentBookingAllowsOnlyOneWinner() throws Exception {
        MockHttpSession firstSession = authenticatedSession();
        MockHttpSession secondSession = authenticatedSession();
        CsrfCredentials firstCsrf = csrf(firstSession);
        CsrfCredentials secondCsrf = csrf(secondSession);
        String booking = objectMapper.writeValueAsString(Map.of(
                "spotId", 4,
                "fishingDate", "2099-12-28",
                "timeSlot", "EVENING",
                "guests", 4
        ));

        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> {
                start.await();
                return mockMvc.perform(post("/api/bookings")
                                .session(firstSession).cookie(firstCsrf.cookie())
                                .header(firstCsrf.headerName(), firstCsrf.token())
                                .contentType(MediaType.APPLICATION_JSON).content(booking))
                        .andReturn().getResponse().getStatus();
            });
            var second = executor.submit(() -> {
                start.await();
                return mockMvc.perform(post("/api/bookings")
                                .session(secondSession).cookie(secondCsrf.cookie())
                                .header(secondCsrf.headerName(), secondCsrf.token())
                                .contentType(MediaType.APPLICATION_JSON).content(booking))
                        .andReturn().getResponse().getStatus();
            });
            start.countDown();
            List<Integer> statuses = List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS)
            ).stream().sorted().toList();
            assertEquals(List.of(201, 409), statuses);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @Order(8)
    void shrinkingSpotCapacityImmediatelyConstrainsHistoricalInventory() throws Exception {
        MockHttpSession session = authenticatedSession();
        CsrfCredentials csrf = csrf(session);
        String booking = objectMapper.writeValueAsString(Map.of(
                "spotId", 2,
                "fishingDate", "2099-12-26",
                "timeSlot", "AFTERNOON",
                "guests", 1
        ));
        mockMvc.perform(post("/api/bookings")
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(booking))
                .andExpect(status().isCreated());

        String smallerSpot = objectMapper.writeValueAsString(Map.of(
                "zoneId", 1,
                "code", "E-02",
                "name", "东岸 2 号位",
                "mapX", 31,
                "mapY", 66,
                "capacity", 1,
                "status", "OPEN",
                "note", "集成测试缩容"
        ));
        mockMvc.perform(put("/api/spots/2")
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(smallerSpot))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.capacity", is(1)));

        mockMvc.perform(get("/api/bookings/availability")
                        .session(session)
                        .param("date", "2099-12-26")
                        .param("timeSlot", "AFTERNOON")
                        .param("spotId", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].capacity", is(1)))
                .andExpect(jsonPath("$.data[0].availableCount", is(0)))
                .andExpect(jsonPath("$.data[0].status", is("FULL")));

        mockMvc.perform(post("/api/bookings")
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(booking))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    @Order(9)
    void inactiveZoneBlocksHistoricalOpenSpotInventory() throws Exception {
        MockHttpSession session = authenticatedSession();
        CsrfCredentials csrf = csrf(session);
        String booking = objectMapper.writeValueAsString(Map.of(
                "spotId", 1,
                "fishingDate", "2099-12-27",
                "timeSlot", "MORNING",
                "guests", 2
        ));
        MvcResult created = mockMvc.perform(post("/api/bookings")
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(booking))
                .andExpect(status().isCreated())
                .andReturn();
        long bookingId = objectMapper.readTree(created.getResponse().getContentAsByteArray())
                .at("/data/id").asLong();

        mockMvc.perform(post("/api/bookings/{id}/cancel", bookingId)
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token()))
                .andExpect(status().isOk());

        String inactiveZone = objectMapper.writeValueAsString(Map.of(
                "code", "EAST",
                "name", "东岸生态区",
                "description", "集成测试停用",
                "status", "INACTIVE"
        ));
        mockMvc.perform(put("/api/zones/1")
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(inactiveZone))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("INACTIVE")));

        mockMvc.perform(post("/api/bookings")
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(booking))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.data").value(nullValue()));

        mockMvc.perform(get("/api/bookings/availability")
                        .session(session)
                        .param("date", "2099-12-27")
                        .param("timeSlot", "MORNING")
                        .param("spotId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status", is("CLOSED")));
    }

    @Test
    @Order(10)
    void seededReadModelsAndUnknownRouteUseStableEnvelope() throws Exception {
        MockHttpSession session = authenticatedSession();
        for (String endpoint : List.of(
                "/api/zones",
                "/api/spots/map",
                "/api/catches",
                "/api/members",
                "/api/payments",
                "/api/analytics/traffic?days=7"
        )) {
            mockMvc.perform(get(endpoint).session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data").exists())
                    .andExpect(jsonPath("$.timestamp").isString());
        }

        mockMvc.perform(get("/api/does-not-exist").session(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.timestamp").isString());
    }

    @Test
    @Order(11)
    void linkedBookingIsAuthoritativeForCatchAssociation() throws Exception {
        MockHttpSession session = authenticatedSession();
        CsrfCredentials csrf = csrf(session);
        String request = objectMapper.writeValueAsString(Map.of(
                "bookingId", 1,
                "spotId", 2,
                "memberId", 2,
                "fishingDate", "2099-12-25",
                "species", "鲫鱼",
                "weight", 1.25,
                "quantity", 2,
                "status", "RECORDED"
        ));

        mockMvc.perform(post("/api/catches")
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.bookingId", is(1)))
                .andExpect(jsonPath("$.data.bookingNo", is("BK-SEED-001")))
                .andExpect(jsonPath("$.data.spotId", is(1)))
                .andExpect(jsonPath("$.data.memberId", is(1)));
    }

    private MockHttpSession authenticatedSession() throws Exception {
        MockHttpSession session = new MockHttpSession();
        login(session);
        return session;
    }

    private void login(MockHttpSession session) throws Exception {
        CsrfCredentials csrf = csrf(session);
        mockMvc.perform(post("/api/auth/login")
                        .session(session)
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.username", is("admin")));
    }

    private CsrfCredentials csrf(MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.headerName", is("X-XSRF-TOKEN")))
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsByteArray()).get("data");
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(cookie, "CSRF endpoint should issue XSRF-TOKEN cookie");
        return new CsrfCredentials(data.get("headerName").asText(), data.get("token").asText(), cookie);
    }

    private record CsrfCredentials(String headerName, String token, Cookie cookie) {
    }
}
