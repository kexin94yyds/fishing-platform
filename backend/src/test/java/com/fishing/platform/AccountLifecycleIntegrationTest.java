package com.fishing.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:fishing_account_lifecycle;MODE=MySQL;"
                + "DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class AccountLifecycleIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired PasswordEncoder passwordEncoder;

    @BeforeEach
    void resetAccounts() {
        jdbcTemplate.update("DELETE FROM account_audit_log");
        jdbcTemplate.update("DELETE FROM app_user WHERE id <> 1");
        jdbcTemplate.update("""
                UPDATE app_user
                SET password_hash = ?, display_name = '运营管理员', role = 'ADMIN', enabled = TRUE,
                    version = 0, session_version = 0
                WHERE id = 1
                """, passwordEncoder.encode("admin123"));
        jdbcTemplate.update("""
                INSERT INTO app_user (username, password_hash, display_name, role, enabled)
                VALUES ('operator', ?, '运营人员', 'OPERATOR', TRUE)
                """, passwordEncoder.encode("operator123"));
    }

    @Test
    void adminCanCreateUpdateResetAndAuditWithoutPasswordDisclosure() throws Exception {
        SessionCredentials admin = login("admin", "admin123");
        MvcResult created = mockMvc.perform(post("/api/admin/accounts")
                        .session(admin.session()).cookie(admin.csrf().cookie())
                        .header(admin.csrf().headerName(), admin.csrf().token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "managed_operator", "displayName", "受管运营员",
                                "password", "Managed123", "role", "OPERATOR"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.version", is(0)))
                .andReturn();
        JsonNode account = body(created).at("/data");
        long id = account.get("id").asLong();

        mockMvc.perform(put("/api/admin/accounts/{id}", id)
                        .session(admin.session()).cookie(admin.csrf().cookie())
                        .header(admin.csrf().headerName(), admin.csrf().token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("displayName", "受管管理员", "role", "ADMIN",
                                "enabled", true, "expectedVersion", 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role", is("ADMIN")))
                .andExpect(jsonPath("$.data.version", is(1)));

        mockMvc.perform(post("/api/admin/accounts/{id}/reset-password", id)
                        .session(admin.session()).cookie(admin.csrf().cookie())
                        .header(admin.csrf().headerName(), admin.csrf().token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("newPassword", "Managed456", "expectedVersion", 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version", is(2)));

        mockMvc.perform(get("/api/admin/account-audits").session(admin.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", is(3)))
                .andExpect(jsonPath("$.data[0].action", is("RESET_PASSWORD")));
        assertEquals(0L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM account_audit_log WHERE actor_username LIKE '%Managed%'", Long.class));
    }

    @Test
    void selfLifecycleGuardsAndOperatorRbacAreEnforced() throws Exception {
        SessionCredentials admin = login("admin", "admin123");
        long adminId = body(mockMvc.perform(get("/api/auth/me").session(admin.session())).andReturn())
                .at("/data/id").asLong();

        mockMvc.perform(put("/api/admin/accounts/{id}", adminId)
                        .session(admin.session()).cookie(admin.csrf().cookie())
                        .header(admin.csrf().headerName(), admin.csrf().token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("displayName", "运营管理员", "role", "OPERATOR",
                                "enabled", true, "expectedVersion", 0))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/accounts/{id}/reset-password", adminId)
                        .session(admin.session()).cookie(admin.csrf().cookie())
                        .header(admin.csrf().headerName(), admin.csrf().token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("newPassword", "AdminNew123", "expectedVersion", 0))))
                .andExpect(status().isForbidden());

        SessionCredentials operator = login("operator", "operator123");
        mockMvc.perform(get("/api/admin/accounts").session(operator.session()))
                .andExpect(status().isForbidden());
    }

    @Test
    void disableReenableAndPasswordResetDoNotReviveOldSessions() throws Exception {
        String username = "session_operator";
        jdbcTemplate.update("""
                INSERT INTO app_user (username, password_hash, display_name, role, enabled)
                VALUES (?, ?, '会话运营员', 'OPERATOR', TRUE)
                """, username, passwordEncoder.encode("Session123"));
        long id = jdbcTemplate.queryForObject("SELECT id FROM app_user WHERE username = ?", Long.class, username);
        SessionCredentials oldSession = login(username, "Session123");
        SessionCredentials admin = login("admin", "admin123");

        updateAccount(admin, id, "会话运营员", "OPERATOR", false, 0, 200);
        mockMvc.perform(get("/api/dashboard/summary").session(oldSession.session()))
                .andExpect(status().isUnauthorized());
        updateAccount(admin, id, "会话运营员", "OPERATOR", true, 1, 200);
        mockMvc.perform(get("/api/dashboard/summary").session(oldSession.session()))
                .andExpect(status().isUnauthorized());

        SessionCredentials current = login(username, "Session123");
        mockMvc.perform(post("/api/admin/accounts/{id}/reset-password", id)
                        .session(admin.session()).cookie(admin.csrf().cookie())
                        .header(admin.csrf().headerName(), admin.csrf().token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("newPassword", "Session456", "expectedVersion", 2))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/dashboard/summary").session(current.session()))
                .andExpect(status().isUnauthorized());
        login(username, "Session456");
    }

    @Test
    void concurrentAdminRemovalKeepsAtLeastOneEnabledAdmin() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO app_user (username, password_hash, display_name, role, enabled)
                VALUES ('admin_two', ?, '第二管理员', 'ADMIN', TRUE)
                """, passwordEncoder.encode("AdminTwo123"));
        long firstId = jdbcTemplate.queryForObject("SELECT id FROM app_user WHERE username='admin'", Long.class);
        long secondId = jdbcTemplate.queryForObject("SELECT id FROM app_user WHERE username='admin_two'", Long.class);
        SessionCredentials first = login("admin", "admin123");
        SessionCredentials second = login("admin_two", "AdminTwo123");
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var one = executor.submit(() -> { start.await(); return updateAccount(first, secondId, "第二管理员", "OPERATOR", true, 0, null); });
            var two = executor.submit(() -> { start.await(); return updateAccount(second, firstId, "运营管理员", "OPERATOR", true, 0, null); });
            start.countDown();
            assertEquals(List.of(200, 403), List.of(one.get(10, TimeUnit.SECONDS), two.get(10, TimeUnit.SECONDS))
                    .stream().sorted().toList());
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM app_user WHERE role='ADMIN' AND enabled=TRUE", Integer.class));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void staleConcurrentAccountUpdatesAllowOnlyOneWinner() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO app_user (username, password_hash, display_name, role, enabled)
                VALUES ('version_target', ?, '版本目标', 'OPERATOR', TRUE)
                """, passwordEncoder.encode("Version123"));
        long targetId = jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE username='version_target'", Long.class);
        SessionCredentials first = login("admin", "admin123");
        SessionCredentials second = login("admin", "admin123");
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var one = executor.submit(() -> { start.await(); return updateAccount(first, targetId, "第一版本", "OPERATOR", true, 0, null); });
            var two = executor.submit(() -> { start.await(); return updateAccount(second, targetId, "第二版本", "OPERATOR", true, 0, null); });
            start.countDown();
            assertEquals(List.of(200, 409), List.of(one.get(10, TimeUnit.SECONDS), two.get(10, TimeUnit.SECONDS))
                    .stream().sorted().toList());
            assertEquals(1L, jdbcTemplate.queryForObject(
                    "SELECT version FROM app_user WHERE id = ?", Long.class, targetId));
            assertEquals(1L, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM account_audit_log WHERE target_user_id = ?", Long.class, targetId));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void changingOwnPasswordInvalidatesEveryOldSession() throws Exception {
        SessionCredentials first = login("operator", "operator123");
        SessionCredentials second = login("operator", "operator123");

        mockMvc.perform(post("/api/auth/change-password")
                        .session(first.session()).cookie(first.csrf().cookie())
                        .header(first.csrf().headerName(), first.csrf().token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentPassword", "wrong123", "newPassword", "Operator456"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("当前密码不正确")));

        mockMvc.perform(post("/api/auth/change-password")
                        .session(first.session()).cookie(first.csrf().cookie())
                        .header(first.csrf().headerName(), first.csrf().token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentPassword", "operator123", "newPassword", "Operator456"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/dashboard/summary").session(second.session()))
                .andExpect(status().isUnauthorized());

        // A browser still presents the now-stale session immediately after changing its own
        // password. The public CSRF endpoint must discard it and establish a usable new session
        // so that the very next login attempt succeeds without a spurious 401/retry.
        MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf").session(first.session()))
                .andExpect(status().isOk())
                .andReturn();
        assertTrue(first.session().isInvalid());
        JsonNode csrfData = body(csrfResult).get("data");
        Cookie csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(csrfCookie);
        mockMvc.perform(post("/api/auth/login")
                        .cookie(csrfCookie)
                        .header(csrfData.get("headerName").asText(), csrfData.get("token").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "operator", "password", "Operator456"))))
                .andExpect(status().isOk());
    }

    private int updateAccount(SessionCredentials credentials, long id, String displayName, String role,
                              boolean enabled, long version, Integer expected) throws Exception {
        int status = mockMvc.perform(put("/api/admin/accounts/{id}", id)
                        .session(credentials.session()).cookie(credentials.csrf().cookie())
                        .header(credentials.csrf().headerName(), credentials.csrf().token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("displayName", displayName, "role", role,
                                "enabled", enabled, "expectedVersion", version))))
                .andReturn().getResponse().getStatus();
        if (expected != null) assertEquals(expected, status);
        return status;
    }

    private SessionCredentials login(String username, String password) throws Exception {
        MockHttpSession session = new MockHttpSession();
        CsrfCredentials csrf = csrf(session);
        mockMvc.perform(post("/api/auth/login").session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", password))))
                .andExpect(status().isOk());
        return new SessionCredentials(session, csrf(session));
    }

    private CsrfCredentials csrf(MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf").session(session)).andExpect(status().isOk()).andReturn();
        JsonNode data = body(result).get("data");
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(cookie);
        return new CsrfCredentials(data.get("headerName").asText(), data.get("token").asText(), cookie);
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private record CsrfCredentials(String headerName, String token, Cookie cookie) {}
    private record SessionCredentials(MockHttpSession session, CsrfCredentials csrf) {}
}
