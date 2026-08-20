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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Clock;
import java.time.LocalDate;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    @Autowired
    private Clock businessClock;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
                .andExpect(jsonPath("$.data.todaySalesOrders", is(1)))
                .andExpect(jsonPath("$.data.todayProductQuantity", is(2)))
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
    void duplicateCustomerSlotIsRejectedAndCancellationReleasesTheIdentity() throws Exception {
        MockHttpSession session = authenticatedSession();
        CsrfCredentials csrf = csrf(session);
        String missingCustomer = objectMapper.writeValueAsString(Map.of(
                "spotId", 2,
                "fishingDate", "2099-12-30",
                "timeSlot", "MORNING",
                "guests", 1));
        mockMvc.perform(post("/api/bookings")
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(missingCustomer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is(
                        "customerIdentityPresent: 散客预订必须填写顾客姓名和联系电话")));
        String request = objectMapper.writeValueAsString(Map.of(
                "contactName", "重复预订顾客",
                "contactPhone", "13900000001",
                "spotId", 2,
                "fishingDate", "2099-12-30",
                "timeSlot", "MORNING",
                "guests", 1,
                "notes", "集成测试"
        ));

        MvcResult created = mockMvc.perform(post("/api/bookings")
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.contactName", is("重复预订顾客")))
                .andExpect(jsonPath("$.data.contactPhone", is("13900000001")))
                .andExpect(jsonPath("$.data.status", is("CONFIRMED")))
                .andReturn();

        mockMvc.perform(post("/api/bookings")
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.data").value(nullValue()));

        mockMvc.perform(get("/api/bookings/availability")
                        .session(session)
                        .param("date", "2099-12-30")
                        .param("timeSlot", "MORNING")
                        .param("spotId", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].reservedCount", is(1)))
                .andExpect(jsonPath("$.data[0].availableCount", is(1)));

        long bookingId = objectMapper.readTree(created.getResponse().getContentAsByteArray())
                .at("/data/id").asLong();
        mockMvc.perform(post("/api/bookings/{id}/cancel", bookingId)
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/bookings/{id}/audits", bookingId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].action", is("CANCEL")))
                .andExpect(jsonPath("$.data[1].action", is("CREATE")));

        MvcResult rebooked = mockMvc.perform(post("/api/bookings")
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated())
                .andReturn();
        long rebookedId = objectMapper.readTree(rebooked.getResponse().getContentAsByteArray())
                .at("/data/id").asLong();
        mockMvc.perform(post("/api/bookings/{id}/cancel", rebookedId)
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token()))
                .andExpect(status().isOk());
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
        long orderId = objectMapper.readTree(created.getResponse().getContentAsByteArray())
                .at("/data/order/id").asLong();

        mockMvc.perform(get("/api/sales-orders/{id}", orderId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.order.id", is((int) orderId)))
                .andExpect(jsonPath("$.data.items.length()", is(1)))
                .andExpect(jsonPath("$.data.items[0].productId", is(3)))
                .andExpect(jsonPath("$.data.items[0].productName", is("瓶装饮用水")))
                .andExpect(jsonPath("$.data.items[0].quantity", is(2)))
                .andExpect(jsonPath("$.data.items[0].unitPrice", is(3.00)))
                .andExpect(jsonPath("$.data.items[0].lineAmount", is(6.00)));

        mockMvc.perform(get("/api/sales-orders/{id}", 999999).session(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", is("销售单不存在")));

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
                "contactName", "历史库存顾客",
                "contactPhone", "13900000002",
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
                "contactName", "非标准时段顾客",
                "contactPhone", "13900000003",
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
                "contactName", "并发抢位顾客",
                "contactPhone", "13900000004",
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
    void spotCapacityCannotBeReducedBelowExistingReservations() throws Exception {
        MockHttpSession session = authenticatedSession();
        CsrfCredentials csrf = csrf(session);
        String booking = objectMapper.writeValueAsString(Map.of(
                "contactName", "容量校验顾客",
                "contactPhone", "13900000005",
                "spotId", 1,
                "fishingDate", LocalDate.now(businessClock).toString(),
                "timeSlot", "MORNING",
                "guests", 1
        ));
        mockMvc.perform(post("/api/bookings")
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(booking))
                .andExpect(status().isCreated());

        String smallerSpot = objectMapper.writeValueAsString(Map.of(
                "zoneId", 1,
                "code", "E-01",
                "name", "东岸 1 号位",
                "mapX", 22,
                "mapY", 58,
                "capacity", 1,
                "status", "OPEN",
                "note", "集成测试缩容"
        ));
        mockMvc.perform(put("/api/spots/1")
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(smallerSpot))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("钓位容量不能低于现有有效预订人数（2 人）")));

        mockMvc.perform(get("/api/bookings/availability")
                        .session(session)
                        .param("date", LocalDate.now(businessClock).toString())
                        .param("timeSlot", "MORNING")
                        .param("spotId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].capacity", is(2)))
                .andExpect(jsonPath("$.data[0].reservedCount", is(2)))
                .andExpect(jsonPath("$.data[0].availableCount", is(0)))
                .andExpect(jsonPath("$.data[0].status", is("FULL")));
    }

    @Test
    @Order(9)
    void inactiveZoneBlocksHistoricalOpenSpotInventory() throws Exception {
        MockHttpSession session = authenticatedSession();
        CsrfCredentials csrf = csrf(session);
        String booking = objectMapper.writeValueAsString(Map.of(
                "contactName", "分区停用顾客",
                "contactPhone", "13900000006",
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

    @Test
    @Order(12)
    void cancelledAndFutureBookingsCannotBeLinkedToCatchRecords() throws Exception {
        MockHttpSession session = authenticatedSession();
        CsrfCredentials csrf = csrf(session);
        String today = LocalDate.now(businessClock).toString();
        String cancellableFutureDate = LocalDate.now(businessClock).plusDays(1).toString();
        String bookingRequest = objectMapper.writeValueAsString(Map.of(
                "contactName", "取消关联顾客",
                "contactPhone", "13900000007",
                "spotId", 4,
                "fishingDate", cancellableFutureDate,
                "timeSlot", "EVENING",
                "guests", 1
        ));
        MvcResult created = mockMvc.perform(post("/api/bookings")
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(bookingRequest))
                .andExpect(status().isCreated())
                .andReturn();
        long cancelledBookingId = objectMapper.readTree(created.getResponse().getContentAsByteArray())
                .at("/data/id").asLong();

        mockMvc.perform(post("/api/bookings/{id}/cancel", cancelledBookingId)
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token()))
                .andExpect(status().isOk());

        String cancelledCatch = objectMapper.writeValueAsString(Map.of(
                "bookingId", cancelledBookingId,
                "spotId", 4,
                "fishingDate", cancellableFutureDate,
                "species", "鲫鱼",
                "weight", 1.25,
                "quantity", 1,
                "status", "RECORDED",
                "expectedStatus", "RECORDED",
                "expectedVersion", 0
        ));
        mockMvc.perform(post("/api/catches")
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(cancelledCatch))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("已取消或无效的预订不能用于渔获登记")));

        String editableCatch = objectMapper.writeValueAsString(Map.of(
                "spotId", 4,
                "fishingDate", today,
                "species", "鲫鱼",
                "weight", 1.0,
                "quantity", 1,
                "status", "RECORDED"
        ));
        MvcResult editableCreated = mockMvc.perform(post("/api/catches")
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(editableCatch))
                .andExpect(status().isCreated())
                .andReturn();
        long editableCatchId = objectMapper.readTree(editableCreated.getResponse().getContentAsByteArray())
                .at("/data/id").asLong();

        mockMvc.perform(put("/api/catches/{id}", editableCatchId)
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(cancelledCatch))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("已取消或无效的预订不能用于渔获登记")));

        String futureDate = "2099-12-20";
        String futureBooking = objectMapper.writeValueAsString(Map.of(
                "contactName", "未来渔获顾客",
                "contactPhone", "13900000008",
                "spotId", 4,
                "fishingDate", futureDate,
                "timeSlot", "MORNING",
                "guests", 1
        ));
        MvcResult futureCreated = mockMvc.perform(post("/api/bookings")
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(futureBooking))
                .andExpect(status().isCreated())
                .andReturn();
        long futureBookingId = objectMapper.readTree(futureCreated.getResponse().getContentAsByteArray())
                .at("/data/id").asLong();

        String futureCatch = objectMapper.writeValueAsString(Map.of(
                "bookingId", futureBookingId,
                "spotId", 4,
                "fishingDate", futureDate,
                "species", "鲤鱼",
                "weight", 2.5,
                "quantity", 1,
                "status", "RECORDED"
        ));
        mockMvc.perform(post("/api/catches")
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(futureCatch))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("不能提前登记未来日期的渔获")));

        String unlinkedFutureCatch = objectMapper.writeValueAsString(Map.of(
                "spotId", 4,
                "fishingDate", futureDate,
                "species", "草鱼",
                "weight", 3.5,
                "quantity", 1,
                "status", "RECORDED"
        ));
        mockMvc.perform(post("/api/catches")
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(unlinkedFutureCatch))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("不能提前登记未来日期的渔获")));
    }

    @Test
    @Order(13)
    void bookingWithActiveCatchCannotBeMarkedNoShow() throws Exception {
        MockHttpSession session = authenticatedSession();
        CsrfCredentials csrf = csrf(session);
        String today = LocalDate.now(businessClock).toString();
        String bookingRequest = objectMapper.writeValueAsString(Map.of(
                "contactName", "渔获互斥顾客",
                "contactPhone", "13900000009",
                "spotId", 4,
                "fishingDate", today,
                "timeSlot", "AFTERNOON",
                "guests", 1
        ));
        MvcResult bookingCreated = mockMvc.perform(post("/api/bookings")
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(bookingRequest))
                .andExpect(status().isCreated())
                .andReturn();
        long bookingId = objectMapper.readTree(bookingCreated.getResponse().getContentAsByteArray())
                .at("/data/id").asLong();

        String catchRequest = objectMapper.writeValueAsString(Map.of(
                "bookingId", bookingId,
                "spotId", 4,
                "fishingDate", today,
                "species", "鲫鱼",
                "weight", 1.5,
                "quantity", 1,
                "status", "RECORDED"
        ));
        mockMvc.perform(post("/api/catches")
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(catchRequest))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/bookings/{id}/no-show", bookingId)
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("该预订已有有效渔获记录，只能完成结单，不能标记爽约")));
    }

    @Test
    @Order(14)
    void concurrentNoShowAndCatchCreationPreserveBookingInvariant() throws Exception {
        MockHttpSession setupSession = authenticatedSession();
        CsrfCredentials setupCsrf = csrf(setupSession);
        String today = LocalDate.now(businessClock).toString();
        String bookingRequest = objectMapper.writeValueAsString(Map.of(
                "contactName", "并发渔获顾客",
                "contactPhone", "13900000010",
                "spotId", 4,
                "fishingDate", today,
                "timeSlot", "MORNING",
                "guests", 1
        ));
        MvcResult bookingCreated = mockMvc.perform(post("/api/bookings")
                        .session(setupSession).cookie(setupCsrf.cookie())
                        .header(setupCsrf.headerName(), setupCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(bookingRequest))
                .andExpect(status().isCreated())
                .andReturn();
        long bookingId = objectMapper.readTree(bookingCreated.getResponse().getContentAsByteArray())
                .at("/data/id").asLong();
        String catchRequest = objectMapper.writeValueAsString(Map.of(
                "bookingId", bookingId,
                "spotId", 4,
                "fishingDate", today,
                "species", "鲤鱼",
                "weight", 2.0,
                "quantity", 1,
                "status", "RECORDED"
        ));

        MockHttpSession cancelSession = authenticatedSession();
        MockHttpSession catchSession = authenticatedSession();
        CsrfCredentials cancelCsrf = csrf(cancelSession);
        CsrfCredentials catchCsrf = csrf(catchSession);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var noShow = executor.submit(() -> {
                start.await();
                return mockMvc.perform(post("/api/bookings/{id}/no-show", bookingId)
                                .session(cancelSession).cookie(cancelCsrf.cookie())
                                .header(cancelCsrf.headerName(), cancelCsrf.token()))
                        .andReturn().getResponse().getStatus();
            });
            var catchCreation = executor.submit(() -> {
                start.await();
                return mockMvc.perform(post("/api/catches")
                                .session(catchSession).cookie(catchCsrf.cookie())
                                .header(catchCsrf.headerName(), catchCsrf.token())
                                .contentType(MediaType.APPLICATION_JSON).content(catchRequest))
                        .andReturn().getResponse().getStatus();
            });
            start.countDown();
            List<Integer> statuses = List.of(
                    noShow.get(10, TimeUnit.SECONDS),
                    catchCreation.get(10, TimeUnit.SECONDS)
            ).stream().sorted().toList();
            assertTrue(statuses.equals(List.of(200, 409)) || statuses.equals(List.of(201, 409)),
                    "爽约结单与登记只能有一个成功，实际状态：" + statuses);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @Order(15)
    void concurrentBookingAndCapacityShrinkPreserveCapacityInvariant() throws Exception {
        MockHttpSession setupSession = authenticatedSession();
        CsrfCredentials setupCsrf = csrf(setupSession);
        String spotRequest = objectMapper.writeValueAsString(Map.of(
                "zoneId", 3,
                "code", "RACE-01",
                "name", "并发验收钓位",
                "mapX", 55,
                "mapY", 75,
                "capacity", 2,
                "status", "OPEN",
                "note", "并发一致性测试"
        ));
        MvcResult spotCreated = mockMvc.perform(post("/api/spots")
                        .session(setupSession).cookie(setupCsrf.cookie())
                        .header(setupCsrf.headerName(), setupCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(spotRequest))
                .andExpect(status().isCreated())
                .andReturn();
        long spotId = objectMapper.readTree(spotCreated.getResponse().getContentAsByteArray())
                .at("/data/id").asLong();
        String futureDate = "2099-12-19";
        String bookingRequest = objectMapper.writeValueAsString(Map.of(
                "contactName", "并发缩容顾客",
                "contactPhone", "13900000011",
                "spotId", spotId,
                "fishingDate", futureDate,
                "timeSlot", "AFTERNOON",
                "guests", 2
        ));
        String shrinkRequest = objectMapper.writeValueAsString(Map.of(
                "zoneId", 3,
                "code", "RACE-01",
                "name", "并发验收钓位",
                "mapX", 55,
                "mapY", 75,
                "capacity", 1,
                "status", "OPEN",
                "note", "并发缩容测试"
        ));

        MockHttpSession bookingSession = authenticatedSession();
        MockHttpSession shrinkSession = authenticatedSession();
        CsrfCredentials bookingCsrf = csrf(bookingSession);
        CsrfCredentials shrinkCsrf = csrf(shrinkSession);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var booking = executor.submit(() -> {
                start.await();
                return mockMvc.perform(post("/api/bookings")
                                .session(bookingSession).cookie(bookingCsrf.cookie())
                                .header(bookingCsrf.headerName(), bookingCsrf.token())
                                .contentType(MediaType.APPLICATION_JSON).content(bookingRequest))
                        .andReturn().getResponse().getStatus();
            });
            var shrink = executor.submit(() -> {
                start.await();
                return mockMvc.perform(put("/api/spots/{id}", spotId)
                                .session(shrinkSession).cookie(shrinkCsrf.cookie())
                                .header(shrinkCsrf.headerName(), shrinkCsrf.token())
                                .contentType(MediaType.APPLICATION_JSON).content(shrinkRequest))
                        .andReturn().getResponse().getStatus();
            });
            start.countDown();
            List<Integer> statuses = List.of(
                    booking.get(10, TimeUnit.SECONDS),
                    shrink.get(10, TimeUnit.SECONDS)
            ).stream().sorted().toList();
            assertTrue(statuses.equals(List.of(200, 409)) || statuses.equals(List.of(201, 409)),
                    "预订与缩容只能有一个成功，实际状态：" + statuses);
        } finally {
            executor.shutdownNow();
        }

        MvcResult availability = mockMvc.perform(get("/api/bookings/availability")
                        .session(setupSession)
                        .param("date", futureDate)
                        .param("timeSlot", "AFTERNOON")
                        .param("spotId", String.valueOf(spotId)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode slot = objectMapper.readTree(availability.getResponse().getContentAsByteArray())
                .at("/data/0");
        assertTrue(slot.get("reservedCount").asInt() <= slot.get("capacity").asInt(),
                "并发操作后已预订人数不能超过钓位容量");
    }

    @Test
    @Order(16)
    void staleProductUpdateCannotOverwriteConcurrentSaleStock() throws Exception {
        MockHttpSession session = authenticatedSession();
        CsrfCredentials csrf = csrf(session);
        Integer originalStock = jdbcTemplate.queryForObject(
                "SELECT stock_quantity FROM product WHERE id = 2", Integer.class);
        Long originalVersion = jdbcTemplate.queryForObject(
                "SELECT version FROM product WHERE id = 2", Long.class);

        String sale = objectMapper.writeValueAsString(Map.of(
                "items", List.of(Map.of("productId", 2, "quantity", 1)),
                "paymentMethod", "CASH"));
        mockMvc.perform(post("/api/sales-orders")
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(sale))
                .andExpect(status().isCreated());

        String staleUpdate = objectMapper.writeValueAsString(Map.of(
                "sku", "LINE-001", "name", "尼龙主线 2.0", "category", "钓具",
                "price", 36, "stockQuantity", originalStock, "status", "ACTIVE",
                "version", originalVersion));
        mockMvc.perform(put("/api/products/2")
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(staleUpdate))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("商品信息或库存已发生变化，请刷新后重试"));

        assertEquals(originalStock - 1, jdbcTemplate.queryForObject(
                "SELECT stock_quantity FROM product WHERE id = 2", Integer.class));
        assertEquals(originalVersion + 1, jdbcTemplate.queryForObject(
                "SELECT version FROM product WHERE id = 2", Long.class));

        String missingVersion = objectMapper.writeValueAsString(Map.of(
                "sku", "LINE-001", "name", "尼龙主线 2.0", "category", "钓具",
                "price", 36, "stockQuantity", originalStock - 1, "status", "ACTIVE"));
        mockMvc.perform(put("/api/products/2")
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(missingVersion))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(17)
    void cancellingPendingSaleRestoresStockExactlyOnceAndBlocksConfirmation() throws Exception {
        MockHttpSession session = authenticatedSession();
        CsrfCredentials csrf = csrf(session);
        Integer originalStock = jdbcTemplate.queryForObject(
                "SELECT stock_quantity FROM product WHERE id = 3", Integer.class);
        Long originalVersion = jdbcTemplate.queryForObject(
                "SELECT version FROM product WHERE id = 3", Long.class);
        String sale = objectMapper.writeValueAsString(Map.of(
                "memberId", 2,
                "items", List.of(Map.of("productId", 3, "quantity", 3)),
                "paymentMethod", "WECHAT"));
        MvcResult created = mockMvc.perform(post("/api/sales-orders")
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(sale))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(created.getResponse().getContentAsByteArray());
        long orderId = body.at("/data/order/id").asLong();
        long paymentId = body.at("/data/payment/id").asLong();
        assertEquals(originalStock - 3, jdbcTemplate.queryForObject(
                "SELECT stock_quantity FROM product WHERE id = 3", Integer.class));

        mockMvc.perform(post("/api/sales-orders/{id}/cancel", orderId)
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("CANCELLED")))
                .andExpect(jsonPath("$.data.paymentStatus", is("CANCELLED")));
        assertEquals("CANCELLED", jdbcTemplate.queryForObject(
                "SELECT status FROM payment WHERE id = ?", String.class, paymentId));
        assertEquals(originalStock, jdbcTemplate.queryForObject(
                "SELECT stock_quantity FROM product WHERE id = 3", Integer.class));
        assertEquals(originalVersion + 2, jdbcTemplate.queryForObject(
                "SELECT version FROM product WHERE id = 3", Long.class));

        mockMvc.perform(post("/api/sales-orders/{id}/cancel", orderId)
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token()))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/payments/{id}/confirm", paymentId)
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict());
        assertEquals(originalStock, jdbcTemplate.queryForObject(
                "SELECT stock_quantity FROM product WHERE id = 3", Integer.class));
    }

    @Test
    @Order(18)
    void inactiveMemberAndPartialCoordinatesAreRejectedWithoutSideEffects() throws Exception {
        MockHttpSession session = authenticatedSession();
        CsrfCredentials csrf = csrf(session);
        String memberUpdate = objectMapper.writeValueAsString(Map.of(
                "memberNo", "M20260003", "name", "王远", "phone", "13800000003",
                "level", "SILVER", "points", 420, "status", "INACTIVE"));
        mockMvc.perform(put("/api/members/3")
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(memberUpdate))
                .andExpect(status().isOk());

        String booking = objectMapper.writeValueAsString(Map.of(
                "memberId", 3, "spotId", 2, "fishingDate", "2099-12-18",
                "timeSlot", "MORNING", "guests", 1));
        mockMvc.perform(post("/api/bookings")
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(booking))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("会员不存在或已停用，不能创建预订")));

        Integer stock = jdbcTemplate.queryForObject(
                "SELECT stock_quantity FROM product WHERE id = 1", Integer.class);
        String sale = objectMapper.writeValueAsString(Map.of(
                "memberId", 3,
                "items", List.of(Map.of("productId", 1, "quantity", 1))));
        mockMvc.perform(post("/api/sales-orders")
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(sale))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("会员不存在或已停用，不能创建销售单")));
        assertEquals(stock, jdbcTemplate.queryForObject(
                "SELECT stock_quantity FROM product WHERE id = 1", Integer.class));

        Long before = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM fishing_spot", Long.class);
        String invalidSpot = objectMapper.writeValueAsString(Map.of(
                "zoneId", 1, "code", "PAIR-01", "name", "坐标成对测试",
                "mapX", 12, "capacity", 1, "status", "OPEN"));
        mockMvc.perform(post("/api/spots")
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(invalidSpot))
                .andExpect(status().isBadRequest());
        assertEquals(before, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM fishing_spot", Long.class));
    }

    @Test
    @Order(19)
    void reverseOrderedProductListsCompleteWithoutDeadlock() throws Exception {
        MockHttpSession firstSession = authenticatedSession();
        MockHttpSession secondSession = authenticatedSession();
        CsrfCredentials firstCsrf = csrf(firstSession);
        CsrfCredentials secondCsrf = csrf(secondSession);
        String firstRequest = objectMapper.writeValueAsString(Map.of(
                "items", List.of(
                        Map.of("productId", 1, "quantity", 1),
                        Map.of("productId", 2, "quantity", 1))));
        String secondRequest = objectMapper.writeValueAsString(Map.of(
                "items", List.of(
                        Map.of("productId", 2, "quantity", 1),
                        Map.of("productId", 1, "quantity", 1))));
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> {
                start.await();
                return mockMvc.perform(post("/api/sales-orders")
                                .session(firstSession).cookie(firstCsrf.cookie())
                                .header(firstCsrf.headerName(), firstCsrf.token())
                                .contentType(MediaType.APPLICATION_JSON).content(firstRequest))
                        .andReturn().getResponse().getStatus();
            });
            var second = executor.submit(() -> {
                start.await();
                return mockMvc.perform(post("/api/sales-orders")
                                .session(secondSession).cookie(secondCsrf.cookie())
                                .header(secondCsrf.headerName(), secondCsrf.token())
                                .contentType(MediaType.APPLICATION_JSON).content(secondRequest))
                        .andReturn().getResponse().getStatus();
            });
            start.countDown();
            assertEquals(List.of(201, 201), List.of(
                    first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @Order(20)
    void concurrentPaymentConfirmationAndCancellationHaveExactlyOneWinner() throws Exception {
        MockHttpSession setupSession = authenticatedSession();
        CsrfCredentials setupCsrf = csrf(setupSession);
        Integer originalStock = jdbcTemplate.queryForObject(
                "SELECT stock_quantity FROM product WHERE id = 1", Integer.class);
        String sale = objectMapper.writeValueAsString(Map.of(
                "items", List.of(Map.of("productId", 1, "quantity", 1))));
        MvcResult created = mockMvc.perform(post("/api/sales-orders")
                        .session(setupSession).cookie(setupCsrf.cookie())
                        .header(setupCsrf.headerName(), setupCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(sale))
                .andExpect(status().isCreated()).andReturn();
        JsonNode body = objectMapper.readTree(created.getResponse().getContentAsByteArray());
        long orderId = body.at("/data/order/id").asLong();
        long paymentId = body.at("/data/payment/id").asLong();

        MockHttpSession confirmSession = authenticatedSession();
        MockHttpSession cancelSession = authenticatedSession();
        CsrfCredentials confirmCsrf = csrf(confirmSession);
        CsrfCredentials cancelCsrf = csrf(cancelSession);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var confirm = executor.submit(() -> {
                start.await();
                return mockMvc.perform(post("/api/payments/{id}/confirm", paymentId)
                                .session(confirmSession).cookie(confirmCsrf.cookie())
                                .header(confirmCsrf.headerName(), confirmCsrf.token())
                                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                        .andReturn().getResponse().getStatus();
            });
            var cancel = executor.submit(() -> {
                start.await();
                return mockMvc.perform(post("/api/sales-orders/{id}/cancel", orderId)
                                .session(cancelSession).cookie(cancelCsrf.cookie())
                                .header(cancelCsrf.headerName(), cancelCsrf.token()))
                        .andReturn().getResponse().getStatus();
            });
            start.countDown();
            List<Integer> statuses = List.of(
                    confirm.get(10, TimeUnit.SECONDS), cancel.get(10, TimeUnit.SECONDS))
                    .stream().sorted().toList();
            assertEquals(List.of(200, 409), statuses);
        } finally {
            executor.shutdownNow();
        }

        String orderStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM sales_order WHERE id = ?", String.class, orderId);
        String paymentStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM payment WHERE id = ?", String.class, paymentId);
        Integer finalStock = jdbcTemplate.queryForObject(
                "SELECT stock_quantity FROM product WHERE id = 1", Integer.class);
        if ("COMPLETED".equals(orderStatus)) {
            assertEquals("PAID", paymentStatus);
            assertEquals(originalStock - 1, finalStock);
        } else {
            assertEquals("CANCELLED", orderStatus);
            assertEquals("CANCELLED", paymentStatus);
            assertEquals(originalStock, finalStock);
        }
    }

    @Test
    @Order(21)
    void expandingSpotCapacityUpdatesExistingFutureInventory() throws Exception {
        MockHttpSession session = authenticatedSession();
        CsrfCredentials csrf = csrf(session);
        String spotRequest = objectMapper.writeValueAsString(Map.of(
                "zoneId", 3,
                "code", "EXPAND-01",
                "name", "扩容验收钓位",
                "mapX", 65,
                "mapY", 75,
                "capacity", 1,
                "status", "OPEN",
                "note", "扩容库存同步测试"));
        MvcResult spotCreated = mockMvc.perform(post("/api/spots")
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(spotRequest))
                .andExpect(status().isCreated())
                .andReturn();
        long spotId = objectMapper.readTree(spotCreated.getResponse().getContentAsByteArray())
                .at("/data/id").asLong();
        String futureDate = "2099-12-17";
        String bookingRequest = objectMapper.writeValueAsString(Map.of(
                "contactName", "扩容顾客甲",
                "contactPhone", "13900000012",
                "spotId", spotId,
                "fishingDate", futureDate,
                "timeSlot", "MORNING",
                "guests", 1));
        String secondBookingRequest = objectMapper.writeValueAsString(Map.of(
                "contactName", "扩容顾客乙",
                "contactPhone", "13900000015",
                "spotId", spotId,
                "fishingDate", futureDate,
                "timeSlot", "MORNING",
                "guests", 1));
        mockMvc.perform(post("/api/bookings")
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(bookingRequest))
                .andExpect(status().isCreated());

        String expandedSpot = objectMapper.writeValueAsString(Map.of(
                "zoneId", 3,
                "code", "EXPAND-01",
                "name", "扩容验收钓位",
                "mapX", 65,
                "mapY", 75,
                "capacity", 2,
                "status", "OPEN",
                "note", "扩容后可继续预订"));
        mockMvc.perform(put("/api/spots/{id}", spotId)
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(expandedSpot))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.capacity", is(2)));

        mockMvc.perform(get("/api/bookings/availability")
                        .session(session)
                        .param("date", futureDate)
                        .param("timeSlot", "MORNING")
                        .param("spotId", String.valueOf(spotId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].capacity", is(2)))
                .andExpect(jsonPath("$.data[0].reservedCount", is(1)))
                .andExpect(jsonPath("$.data[0].availableCount", is(1)));

        mockMvc.perform(post("/api/bookings")
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(secondBookingRequest))
                .andExpect(status().isCreated());

        assertEquals(2, jdbcTemplate.queryForObject("""
                SELECT capacity FROM fishing_slot_inventory
                WHERE spot_id = ? AND fishing_date = ? AND time_slot = 'MORNING'
                """, Integer.class, spotId, LocalDate.parse(futureDate)));
        assertEquals(2, jdbcTemplate.queryForObject("""
                SELECT reserved_count FROM fishing_slot_inventory
                WHERE spot_id = ? AND fishing_date = ? AND time_slot = 'MORNING'
                """, Integer.class, spotId, LocalDate.parse(futureDate)));
    }

    @Test
    @Order(22)
    void adminSlotConfigurationDrivesBookingPriceAndPaymentLifecycle() throws Exception {
        MockHttpSession session = authenticatedSession();
        CsrfCredentials csrf = csrf(session);
        Long zoneId = jdbcTemplate.queryForObject(
                "SELECT id FROM fishing_zone WHERE status = 'ACTIVE' ORDER BY id LIMIT 1", Long.class);
        String spotRequest = objectMapper.writeValueAsString(Map.of(
                "zoneId", zoneId,
                "code", "REPORT-SLOT-01",
                "name", "开题合同验收钓位",
                "mapX", 41,
                "mapY", 59,
                "capacity", 3,
                "defaultPrice", 65,
                "status", "OPEN",
                "note", "收费与时段配置验收"));
        MvcResult spotCreated = mockMvc.perform(post("/api/spots")
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(spotRequest))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.defaultPrice", is(65.0)))
                .andReturn();
        long spotId = objectMapper.readTree(spotCreated.getResponse().getContentAsByteArray())
                .at("/data/id").asLong();
        String fishingDate = LocalDate.now(businessClock).plusDays(40).toString();
        String slot = objectMapper.writeValueAsString(Map.of(
                "fishingDate", fishingDate,
                "timeSlot", "MORNING",
                "capacity", 2,
                "price", 80,
                "status", "AVAILABLE",
                "expectedVersion", 0));
        mockMvc.perform(put("/api/bookings/slots/{spotId}", spotId)
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(slot))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.capacity", is(2)))
                .andExpect(jsonPath("$.data.price", is(80.0)))
                .andExpect(jsonPath("$.data.status", is("AVAILABLE")));

        String bookingRequest = objectMapper.writeValueAsString(Map.of(
                "contactName", "收费验收顾客",
                "contactPhone", "13900000013",
                "spotId", spotId,
                "fishingDate", fishingDate,
                "timeSlot", "MORNING",
                "guests", 2));
        MvcResult bookingCreated = mockMvc.perform(post("/api/bookings")
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(bookingRequest))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.amount", is(160.0)))
                .andExpect(jsonPath("$.data.contactName", is("收费验收顾客")))
                .andExpect(jsonPath("$.data.paymentStatus", is("PENDING")))
                .andReturn();
        long bookingId = objectMapper.readTree(bookingCreated.getResponse().getContentAsByteArray())
                .at("/data/id").asLong();
        Long paymentId = jdbcTemplate.queryForObject("""
                SELECT id FROM payment WHERE business_type = 'BOOKING' AND business_id = ?
                """, Long.class, bookingId);
        mockMvc.perform(post("/api/payments/{id}/confirm", paymentId)
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"method\":\"CASH\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.businessType", is("BOOKING")))
                .andExpect(jsonPath("$.data.status", is("PAID")));
        assertEquals("PAID", jdbcTemplate.queryForObject(
                "SELECT payment_status FROM booking WHERE id = ?", String.class, bookingId));
        mockMvc.perform(get("/api/bookings/{id}/audits", bookingId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].action", is("PAYMENT_CONFIRM")))
                .andExpect(jsonPath("$.data[0].afterPaymentStatus", is("PAID")))
                .andExpect(jsonPath("$.data[1].action", is("CREATE")));

        String afternoonSlot = objectMapper.writeValueAsString(Map.of(
                "fishingDate", fishingDate,
                "timeSlot", "AFTERNOON",
                "capacity", 3,
                "price", 50,
                "status", "AVAILABLE",
                "expectedVersion", 0));
        mockMvc.perform(put("/api/bookings/slots/{spotId}", spotId)
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(afternoonSlot))
                .andExpect(status().isOk());
        String cancellableRequest = objectMapper.writeValueAsString(Map.of(
                "contactName", "取消审计顾客",
                "contactPhone", "13900000014",
                "spotId", spotId,
                "fishingDate", fishingDate,
                "timeSlot", "AFTERNOON",
                "guests", 1));
        MvcResult cancellable = mockMvc.perform(post("/api/bookings")
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(cancellableRequest))
                .andExpect(status().isCreated()).andReturn();
        long cancellableId = objectMapper.readTree(cancellable.getResponse().getContentAsByteArray())
                .at("/data/id").asLong();
        mockMvc.perform(post("/api/bookings/{id}/cancel", cancellableId)
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus", is("CANCELLED")));
        assertEquals("CANCELLED", jdbcTemplate.queryForObject("""
                SELECT status FROM payment WHERE business_type = 'BOOKING' AND business_id = ?
                """, String.class, cancellableId));
        mockMvc.perform(get("/api/bookings/{id}/audits", cancellableId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].action", is("CANCEL")))
                .andExpect(jsonPath("$.data[1].action", is("CREATE")));
    }

    @Test
    @Order(23)
    void memberActivityAuditAndCatchFiltersCloseTheReportContract() throws Exception {
        MockHttpSession session = authenticatedSession();
        CsrfCredentials csrf = csrf(session);
        String memberRequest = objectMapper.writeValueAsString(Map.of(
                "memberNo", "M-REPORT-01",
                "name", "开题验收会员",
                "phone", "13900009991",
                "level", "NORMAL",
                "points", 10,
                "status", "ACTIVE"));
        MvcResult memberCreated = mockMvc.perform(post("/api/members")
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(memberRequest))
                .andExpect(status().isCreated()).andReturn();
        long memberId = objectMapper.readTree(memberCreated.getResponse().getContentAsByteArray())
                .at("/data/id").asLong();
        String memberUpdate = objectMapper.writeValueAsString(Map.of(
                "memberNo", "M-REPORT-01",
                "name", "开题验收会员",
                "phone", "13900009991",
                "level", "SILVER",
                "points", 30,
                "status", "ACTIVE"));
        mockMvc.perform(put("/api/members/{id}", memberId)
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(memberUpdate))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.level", is("SILVER")));

        Long spotId = jdbcTemplate.queryForObject("""
                SELECT s.id FROM fishing_spot s
                JOIN fishing_zone z ON z.id = s.zone_id
                WHERE s.status = 'OPEN' AND z.status = 'ACTIVE'
                ORDER BY s.id DESC LIMIT 1
                """, Long.class);
        Long zoneId = jdbcTemplate.queryForObject(
                "SELECT zone_id FROM fishing_spot WHERE id = ?", Long.class, spotId);
        String catchDate = LocalDate.now(businessClock).toString();
        String catchRequest = objectMapper.writeValueAsString(Map.of(
                "memberId", memberId,
                "spotId", spotId,
                "fishingDate", catchDate,
                "timeSlot", "AFTERNOON",
                "species", "合同验收鲫鱼",
                "weight", 2.5,
                "quantity", 3,
                "notes", "组合筛选验收"));
        mockMvc.perform(post("/api/catches")
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(catchRequest))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.timeSlot", is("AFTERNOON")))
                .andExpect(jsonPath("$.data.zoneId", is(zoneId.intValue())));

        String futureDate = LocalDate.now(businessClock).plusDays(50).toString();
        String bookingRequest = objectMapper.writeValueAsString(Map.of(
                "memberId", memberId,
                "spotId", spotId,
                "fishingDate", futureDate,
                "timeSlot", "EVENING",
                "guests", 1));
        mockMvc.perform(post("/api/bookings")
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(bookingRequest))
                .andExpect(status().isCreated());
        Long productId = jdbcTemplate.queryForObject("""
                SELECT id FROM product WHERE status = 'ACTIVE' AND stock_quantity > 0 ORDER BY id LIMIT 1
                """, Long.class);
        String saleRequest = objectMapper.writeValueAsString(Map.of(
                "memberId", memberId,
                "items", List.of(Map.of("productId", productId, "quantity", 1))));
        mockMvc.perform(post("/api/sales-orders")
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(saleRequest))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/catches")
                        .session(session)
                        .param("date", catchDate)
                        .param("zoneId", String.valueOf(zoneId))
                        .param("species", "合同验收鲫鱼")
                        .param("memberId", String.valueOf(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].timeSlot", is("AFTERNOON")));
        mockMvc.perform(get("/api/members/{id}/activity", memberId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.member.id", is((int) memberId)))
                .andExpect(jsonPath("$.data.bookings.length()", is(1)))
                .andExpect(jsonPath("$.data.catches.length()", is(1)))
                .andExpect(jsonPath("$.data.salesOrders.length()", is(1)));
        mockMvc.perform(get("/api/members/{id}/audits", memberId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].action", is("UPDATE")))
                .andExpect(jsonPath("$.data[1].action", is("CREATE")));
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
