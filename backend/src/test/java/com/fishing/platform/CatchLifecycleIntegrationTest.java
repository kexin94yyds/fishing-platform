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

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:fishing_catch_lifecycle;MODE=MySQL;"
                + "DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CatchLifecycleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock businessClock;

    @Test
    @Order(1)
    void creationAlwaysStartsRecordedEvenWhenClientSendsStatusFields() throws Exception {
        MockHttpSession session = authenticatedSession();
        CsrfCredentials csrf = csrf(session);
        String request = objectMapper.writeValueAsString(Map.of(
                "spotId", 1,
                "fishingDate", today(),
                "species", "鲫鱼",
                "weight", 1.2,
                "quantity", 1,
                "status", "VOID",
                "expectedStatus", "VOID",
                "expectedVersion", 999));

        mockMvc.perform(post("/api/catches")
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status", is("RECORDED")));
    }

    @Test
    @Order(2)
    void standaloneCatchRejectsInactiveMemberClosedSpotAndInactiveZone() throws Exception {
        MockHttpSession session = authenticatedSession();
        CsrfCredentials csrf = csrf(session);

        jdbcTemplate.update("UPDATE member SET status = 'INACTIVE' WHERE id = 3");
        performCreate(session, csrf, 1, 3L)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("会员不存在或已停用，不能登记独立渔获")));
        jdbcTemplate.update("UPDATE member SET status = 'ACTIVE' WHERE id = 3");

        jdbcTemplate.update("UPDATE fishing_spot SET status = 'CLOSED' WHERE id = 3");
        performCreate(session, csrf, 3, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("钓位未开放，不能登记独立渔获")));
        jdbcTemplate.update("UPDATE fishing_spot SET status = 'OPEN' WHERE id = 3");

        jdbcTemplate.update("UPDATE fishing_zone SET status = 'INACTIVE' WHERE id = 2");
        performCreate(session, csrf, 3, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("钓位所属分区已停用，不能登记独立渔获")));
        jdbcTemplate.update("UPDATE fishing_zone SET status = 'ACTIVE' WHERE id = 2");
    }

    @Test
    @Order(3)
    void catchStatusMovesForwardOnlyAndVoidKeepsItsAssociation() throws Exception {
        MockHttpSession session = authenticatedSession();
        CsrfCredentials csrf = csrf(session);
        long catchId = createCatch(session, csrf, 1);

        performUpdate(session, csrf, catchId, 1, "RECORDED", 0, "VERIFIED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("VERIFIED")))
                .andExpect(jsonPath("$.data.version", is(1)));
        performUpdate(session, csrf, catchId, 1, "VERIFIED", 1, "RECORDED")
                .andExpect(status().isConflict());
        performUpdate(session, csrf, catchId, 1, "VERIFIED", 1, "VOID")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("VOID")))
                .andExpect(jsonPath("$.data.version", is(2)));
        performUpdate(session, csrf, catchId, 1, "VOID", 2, "RECORDED")
                .andExpect(status().isConflict());
        performUpdate(session, csrf, catchId, 2, "VOID", 2, "VOID")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is(
                        "作废仅允许修改状态和渔获内容，关联预订、会员、钓位及日期必须保持不变")));
        performUpdate(session, csrf, catchId, 1, "VOID", 2, "VOID")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.spotId", is(1)))
                .andExpect(jsonPath("$.data.status", is("VOID")))
                .andExpect(jsonPath("$.data.version", is(3)));
    }

    @Test
    @Order(4)
    void concurrentVerificationAndVoidUseExpectedStatusSoOnlyOneWins() throws Exception {
        MockHttpSession setupSession = authenticatedSession();
        CsrfCredentials setupCsrf = csrf(setupSession);
        long catchId = createCatch(setupSession, setupCsrf, 1);

        MockHttpSession verifySession = authenticatedSession();
        MockHttpSession voidSession = authenticatedSession();
        CsrfCredentials verifyCsrf = csrf(verifySession);
        CsrfCredentials voidCsrf = csrf(voidSession);
        String verifyRequest = updateJson(1, "RECORDED", 0, "VERIFIED", "鲤鱼");
        String voidRequest = updateJson(1, "RECORDED", 0, "VOID", "鲤鱼");
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var verification = executor.submit(() -> {
                start.await();
                return mockMvc.perform(put("/api/catches/{id}", catchId)
                                .session(verifySession).cookie(verifyCsrf.cookie())
                                .header(verifyCsrf.headerName(), verifyCsrf.token())
                                .contentType(MediaType.APPLICATION_JSON).content(verifyRequest))
                        .andReturn().getResponse().getStatus();
            });
            var voiding = executor.submit(() -> {
                start.await();
                return mockMvc.perform(put("/api/catches/{id}", catchId)
                                .session(voidSession).cookie(voidCsrf.cookie())
                                .header(voidCsrf.headerName(), voidCsrf.token())
                                .contentType(MediaType.APPLICATION_JSON).content(voidRequest))
                        .andReturn().getResponse().getStatus();
            });
            start.countDown();
            assertEquals(List.of(200, 409), List.of(
                    verification.get(10, TimeUnit.SECONDS), voiding.get(10, TimeUnit.SECONDS))
                    .stream().sorted().toList());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @Order(5)
    void staleSameStatusEditCannotOverwriteEarlierContentUpdate() throws Exception {
        MockHttpSession session = authenticatedSession();
        CsrfCredentials csrf = csrf(session);
        long catchId = createCatch(session, csrf, 1);

        mockMvc.perform(put("/api/catches/{id}", catchId)
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson(1, "RECORDED", 0, "RECORDED", "第一版鱼种")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version", is(1)));

        mockMvc.perform(put("/api/catches/{id}", catchId)
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson(1, "RECORDED", 0, "RECORDED", "陈旧覆盖")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("渔获记录已被其他操作修改，请刷新后重试")));

        mockMvc.perform(get("/api/catches").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + catchId + ")].species", is(List.of("第一版鱼种"))));
    }

    private org.springframework.test.web.servlet.ResultActions performCreate(
            MockHttpSession session, CsrfCredentials csrf, long spotId, Long memberId) throws Exception {
        var request = new java.util.LinkedHashMap<String, Object>();
        request.put("spotId", spotId);
        if (memberId != null) request.put("memberId", memberId);
        request.put("fishingDate", today());
        request.put("species", "鲤鱼");
        request.put("weight", 1.5);
        request.put("quantity", 1);
        return mockMvc.perform(post("/api/catches")
                .session(session).cookie(csrf.cookie())
                .header(csrf.headerName(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private long createCatch(MockHttpSession session, CsrfCredentials csrf, long spotId) throws Exception {
        MvcResult result = performCreate(session, csrf, spotId, null)
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray()).at("/data/id").asLong();
    }

    private org.springframework.test.web.servlet.ResultActions performUpdate(
            MockHttpSession session, CsrfCredentials csrf, long catchId, long spotId,
            String expectedStatus, long expectedVersion, String targetStatus) throws Exception {
        return mockMvc.perform(put("/api/catches/{id}", catchId)
                .session(session).cookie(csrf.cookie())
                .header(csrf.headerName(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateJson(spotId, expectedStatus, expectedVersion, targetStatus, "鲤鱼")));
    }

    private String updateJson(long spotId, String expectedStatus, long expectedVersion,
                              String targetStatus, String species) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "spotId", spotId,
                "fishingDate", today(),
                "species", species,
                "weight", 2.0,
                "quantity", 2,
                "status", targetStatus,
                "expectedStatus", expectedStatus,
                "expectedVersion", expectedVersion));
    }

    private String today() {
        return LocalDate.now(businessClock).toString();
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
                .andExpect(status().isOk()).andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsByteArray()).get("data");
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(cookie);
        return new CsrfCredentials(data.get("headerName").asText(), data.get("token").asText(), cookie);
    }

    private record CsrfCredentials(String headerName, String token, Cookie cookie) {
    }
}
