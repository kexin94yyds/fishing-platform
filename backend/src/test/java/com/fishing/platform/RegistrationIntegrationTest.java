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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "fishing.registration.enabled=true")
@AutoConfigureMockMvc
class RegistrationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void demoProfileAdvertisesPublicRegistration() throws Exception {
        mockMvc.perform(get("/api/auth/registration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.enabled", is(true)));
    }

    @Test
    void registerRequiresCsrfEvenThoughEndpointIsPublic() throws Exception {
        String username = "csrf_guarded_user";
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(username, "CSRF 验证员", "Guarded@123", null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.timestamp").isString());

        assertEquals(0, userCount(username));
    }

    @Test
    void successfulRegisterCreatesBcryptOperatorAndLogsInAutomatically() throws Exception {
        String username = "operator_demo";
        String rawPassword = "Operator123";
        MockHttpSession session = new MockHttpSession();
        String anonymousSessionId = session.getId();
        CsrfCredentials beforeCsrf = csrf(session);

        MvcResult registered = mockMvc.perform(post("/api/auth/register")
                        .session(session)
                        .cookie(beforeCsrf.cookie())
                        .header(beforeCsrf.headerName(), beforeCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(username, "新运营员", rawPassword, "ADMIN")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.username", is(username)))
                .andExpect(jsonPath("$.data.displayName", is("新运营员")))
                .andExpect(jsonPath("$.data.role", is("OPERATOR")))
                .andReturn();

        assertNotEquals(anonymousSessionId, session.getId(),
                "注册自动登录必须执行 session fixation 防护");
        Cookie clearedCsrf = registered.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(clearedCsrf);
        assertEquals(0, clearedCsrf.getMaxAge(), "认证成功后应清除旧 CSRF Cookie");

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username", is(username)))
                .andExpect(jsonPath("$.data.role", is("OPERATOR")));

        CsrfCredentials afterCsrf = csrf(session);
        assertNotEquals(beforeCsrf.token(), afterCsrf.token(),
                "注册自动登录后重新获取的 CSRF token 应完成轮换");
        mockMvc.perform(post("/api/bookings")
                        .session(session)
                        .cookie(afterCsrf.cookie())
                        .header(afterCsrf.headerName(), afterCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)));

        Map<String, Object> stored = jdbcTemplate.queryForMap(
                "SELECT password_hash, role, enabled FROM app_user WHERE username = ?", username);
        String passwordHash = stored.get("password_hash").toString();
        assertNotEquals(rawPassword, passwordHash);
        assertTrue(passwordHash.startsWith("$2"));
        assertTrue(passwordEncoder.matches(rawPassword, passwordHash));
        assertEquals("OPERATOR", stored.get("role"));
        assertEquals(Boolean.TRUE, stored.get("enabled"));
    }

    @Test
    void duplicateUsernameReturnsConflict() throws Exception {
        String username = "duplicate_user";
        registerSuccessfully(new MockHttpSession(), username, "首个运营员", "Duplicate123");

        MockHttpSession secondSession = new MockHttpSession();
        CsrfCredentials csrf = csrf(secondSession);
        mockMvc.perform(post("/api/auth/register")
                        .session(secondSession)
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(username, "重复运营员", "Duplicate123", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", is("用户名已存在")))
                .andExpect(jsonPath("$.data").value(nullValue()));

        assertEquals(1, userCount(username));
    }

    @Test
    void weakPasswordIsRejectedWithoutCreatingAccount() throws Exception {
        String username = "weak_operator";
        MockHttpSession session = new MockHttpSession();
        CsrfCredentials csrf = csrf(session);

        mockMvc.perform(post("/api/auth/register")
                        .session(session)
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(username, "弱密码用户", "password", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("password")))
                .andExpect(jsonPath("$.data").value(nullValue()));

        assertEquals(0, userCount(username));
    }

    @Test
    void concurrentRegistrationCreatesExactlyOneAccount() throws Exception {
        String username = "race_operator";
        MockHttpSession firstSession = new MockHttpSession();
        MockHttpSession secondSession = new MockHttpSession();
        CsrfCredentials firstCsrf = csrf(firstSession);
        CsrfCredentials secondCsrf = csrf(secondSession);
        String body = registerJson(username, "并发运营员", "Racepass123", "ADMIN");

        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> {
                start.await();
                return mockMvc.perform(post("/api/auth/register")
                                .session(firstSession)
                                .cookie(firstCsrf.cookie())
                                .header(firstCsrf.headerName(), firstCsrf.token())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                        .andReturn().getResponse().getStatus();
            });
            var second = executor.submit(() -> {
                start.await();
                return mockMvc.perform(post("/api/auth/register")
                                .session(secondSession)
                                .cookie(secondCsrf.cookie())
                                .header(secondCsrf.headerName(), secondCsrf.token())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
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

        assertEquals(1, userCount(username));
        assertEquals("OPERATOR", jdbcTemplate.queryForObject(
                "SELECT role FROM app_user WHERE username = ?", String.class, username));
    }

    @Test
    void databaseDefaultRoleCannotImplicitlyGrantAdmin() {
        String username = "default_role_probe";
        jdbcTemplate.update("""
                INSERT INTO app_user
                    (username, password_hash, display_name, enabled)
                VALUES
                    (?, ?, ?, TRUE)
                """, username, passwordEncoder.encode("Default123"), "默认角色探针");

        assertEquals("OPERATOR", jdbcTemplate.queryForObject(
                "SELECT role FROM app_user WHERE username = ?", String.class, username));
    }

    private void registerSuccessfully(MockHttpSession session,
                                      String username,
                                      String displayName,
                                      String password) throws Exception {
        CsrfCredentials csrf = csrf(session);
        mockMvc.perform(post("/api/auth/register")
                        .session(session)
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(username, displayName, password, null)))
                .andExpect(status().isCreated());
    }

    private String registerJson(String username,
                                String displayName,
                                String password,
                                String injectedRole) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", username);
        body.put("displayName", displayName);
        body.put("password", password);
        if (injectedRole != null) {
            body.put("role", injectedRole);
        }
        return objectMapper.writeValueAsString(body);
    }

    private int userCount(String username) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM app_user WHERE username = ?", Integer.class, username);
        return count == null ? 0 : count;
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
