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

import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:fishing_authorization;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class AuthorizationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void operatorCanRunDailyWorkButCannotChangeAdminConfigurationOrConfirmPayment() throws Exception {
        MockHttpSession session = registerOperator();
        CsrfCredentials csrf = csrf(session);

        for (String path : List.of(
                "/api/dashboard/summary",
                "/api/zones",
                "/api/spots",
                "/api/bookings",
                "/api/catches",
                "/api/members",
                "/api/products",
                "/api/sales-orders",
                "/api/payments",
                "/api/analytics/traffic?days=7")) {
            mockMvc.perform(get(path).session(session))
                    .andExpect(status().isOk());
        }

        for (String path : List.of("/api/bookings", "/api/catches", "/api/members", "/api/sales-orders")) {
            mockMvc.perform(post(path)
                            .session(session).cookie(csrf.cookie())
                            .header(csrf.headerName(), csrf.token())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
        for (String path : List.of("/api/catches/1", "/api/members/1")) {
            mockMvc.perform(put(path)
                            .session(session).cookie(csrf.cookie())
                            .header(csrf.headerName(), csrf.token())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        long zoneCount = count("fishing_zone");
        long spotCount = count("fishing_spot");
        long productCount = count("product");
        String paymentStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM payment WHERE id = 1", String.class);

        assertForbidden(post("/api/zones"), session, csrf);
        assertForbidden(put("/api/zones/1"), session, csrf);
        assertForbidden(post("/api/spots"), session, csrf);
        assertForbidden(put("/api/spots/1"), session, csrf);
        assertForbidden(post("/api/products"), session, csrf);
        assertForbidden(put("/api/products/1"), session, csrf);
        assertForbidden(post("/api/payments/999999/confirm"), session, csrf);

        assertEquals(zoneCount, count("fishing_zone"));
        assertEquals(spotCount, count("fishing_spot"));
        assertEquals(productCount, count("product"));
        assertEquals(paymentStatus, jdbcTemplate.queryForObject(
                "SELECT status FROM payment WHERE id = 1", String.class));
    }

    private void assertForbidden(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                                 MockHttpSession session,
                                 CsrfCredentials csrf) throws Exception {
        mockMvc.perform(request
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success", is(false)));
    }

    private MockHttpSession registerOperator() throws Exception {
        MockHttpSession session = new MockHttpSession();
        CsrfCredentials csrf = csrf(session);
        mockMvc.perform(post("/api/auth/register")
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "rbac_operator",
                                  "displayName": "权限测试运营员",
                                  "password": "Operator123"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.role", is("OPERATOR")));
        return session;
    }

    private long count(String table) {
        Long value = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        return value == null ? 0 : value;
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
