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

import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:fishing_user_booking;MODE=MySQL;"
                + "DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class UserBookingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private Clock businessClock;

    @Test
    void userCanOnlyUseTheDedicatedUserApi() throws Exception {
        SessionCredentials user = login("angler", "angler123");

        mockMvc.perform(get("/api/auth/me").session(user.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role", is("USER")));
        mockMvc.perform(get("/api/user/bookings").session(user.session()))
                .andExpect(status().isOk());

        for (String path : new String[]{
                "/api/dashboard/summary", "/api/zones", "/api/spots", "/api/bookings",
                "/api/catches", "/api/members", "/api/products", "/api/payments",
                "/api/analytics/traffic?days=7", "/api/admin/accounts"}) {
            mockMvc.perform(get(path).session(user.session()))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(post("/api/bookings")
                        .session(user.session()).cookie(user.csrf().cookie())
                        .header(user.csrf().headerName(), user.csrf().token())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());

        SessionCredentials admin = login("admin", "admin123");
        mockMvc.perform(get("/api/user/bookings").session(admin.session()))
                .andExpect(status().isForbidden());
    }

    @Test
    void userBookingUsesServerIdentityPricePaymentAndCancellation() throws Exception {
        jdbcTemplate.update("UPDATE fishing_spot SET default_price = 88.00 WHERE id = 2");
        SessionCredentials user = login("angler", "angler123");
        LocalDate fishingDate = LocalDate.now(businessClock).plusDays(2);

        MvcResult createdResult = mockMvc.perform(post("/api/user/bookings")
                        .session(user.session()).cookie(user.csrf().cookie())
                        .header(user.csrf().headerName(), user.csrf().token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "contactPhone", "13800009999",
                                "contactName", "伪造姓名",
                                "spotId", 2,
                                "fishingDate", fishingDate.toString(),
                                "timeSlot", "MORNING",
                                "guests", 2,
                                "amount", 0,
                                "notes", "用户端预约"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.contactName", is("湖畔钓友")))
                .andExpect(jsonPath("$.data.amount", is(176.0)))
                .andExpect(jsonPath("$.data.paymentStatus", is("PENDING")))
                .andReturn();

        long bookingId = body(createdResult).at("/data/id").asLong();
        String bookingNo = body(createdResult).at("/data/bookingNo").asText();
        long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE username = 'angler'", Long.class);
        assertEquals(userId, jdbcTemplate.queryForObject(
                "SELECT user_id FROM booking WHERE id = ?", Long.class, bookingId));
        assertEquals("U:" + userId, jdbcTemplate.queryForObject(
                "SELECT active_customer_key FROM booking WHERE id = ?", String.class, bookingId));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payment WHERE business_type = 'BOOKING' AND business_id = ? AND status = 'PENDING'",
                Integer.class, bookingId));

        mockMvc.perform(post("/api/user/bookings")
                        .session(user.session()).cookie(user.csrf().cookie())
                        .header(user.csrf().headerName(), user.csrf().token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "contactPhone", "13900007777",
                                "spotId", 1,
                                "fishingDate", fishingDate.toString(),
                                "timeSlot", "MORNING",
                                "guests", 1))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("该顾客在所选日期和时段已有有效预订")));

        mockMvc.perform(get("/api/user/bookings").session(user.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].bookingNo", hasItem(bookingNo)));

        SessionCredentials admin = login("admin", "admin123");
        mockMvc.perform(post("/api/bookings")
                        .session(admin.session()).cookie(admin.csrf().cookie())
                        .header(admin.csrf().headerName(), admin.csrf().token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "contactName", "现场重复登记",
                                "contactPhone", "13800009999",
                                "spotId", 1,
                                "fishingDate", fishingDate.toString(),
                                "timeSlot", "MORNING",
                                "guests", 1))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("该联系电话在所选日期和时段已有有效预订")));
        mockMvc.perform(get("/api/bookings").session(admin.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].bookingNo", hasItem(bookingNo)));
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM booking
                WHERE fishing_date = ? AND time_slot = 'MORNING' AND status = 'CONFIRMED'
                """, Integer.class, fishingDate));

        mockMvc.perform(post("/api/user/bookings/{id}/cancel", bookingId)
                        .session(user.session()).cookie(user.csrf().cookie())
                        .header(user.csrf().headerName(), user.csrf().token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("CANCELLED")))
                .andExpect(jsonPath("$.data.paymentStatus", is("CANCELLED")));

        assertEquals(0, jdbcTemplate.queryForObject("""
                SELECT reserved_count FROM fishing_slot_inventory
                WHERE spot_id = 2 AND fishing_date = ? AND time_slot = 'MORNING'
                """, Integer.class, fishingDate));
        assertEquals("CANCELLED", jdbcTemplate.queryForObject(
                "SELECT status FROM payment WHERE business_type = 'BOOKING' AND business_id = ?",
                String.class, bookingId));
    }

    @Test
    void ownershipAndRoleBoundaryCannotBeBypassed() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO app_user (username, password_hash, display_name, role, enabled)
                VALUES ('angler_two', ?, '另一位钓友', 'USER', TRUE)
                """, passwordEncoder.encode("AnglerTwo123"));
        jdbcTemplate.update("""
                INSERT INTO app_user (username, password_hash, display_name, role, enabled)
                VALUES ('operator', ?, '运营人员', 'OPERATOR', TRUE)
                """, passwordEncoder.encode("Operator123"));
        jdbcTemplate.update("UPDATE fishing_spot SET default_price = 50.00 WHERE id = 3");

        SessionCredentials firstUser = login("angler", "angler123");
        LocalDate fishingDate = LocalDate.now(businessClock).plusDays(3);
        MvcResult created = mockMvc.perform(post("/api/user/bookings")
                        .session(firstUser.session()).cookie(firstUser.csrf().cookie())
                        .header(firstUser.csrf().headerName(), firstUser.csrf().token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "contactPhone", "13800008888",
                                "spotId", 3,
                                "fishingDate", fishingDate.toString(),
                                "timeSlot", "AFTERNOON",
                                "guests", 1))))
                .andExpect(status().isCreated())
                .andReturn();
        long bookingId = body(created).at("/data/id").asLong();
        String bookingNo = body(created).at("/data/bookingNo").asText();

        SessionCredentials secondUser = login("angler_two", "AnglerTwo123");
        mockMvc.perform(get("/api/user/bookings").session(secondUser.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].bookingNo", not(hasItem(bookingNo))));
        mockMvc.perform(post("/api/user/bookings/{id}/cancel", bookingId)
                        .session(secondUser.session()).cookie(secondUser.csrf().cookie())
                        .header(secondUser.csrf().headerName(), secondUser.csrf().token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", is("预订不存在")));

        SessionCredentials admin = login("admin", "admin123");
        long userVersion = jdbcTemplate.queryForObject(
                "SELECT version FROM app_user WHERE username = 'angler'", Long.class);
        mockMvc.perform(put("/api/admin/accounts/{id}", userId("angler"))
                        .session(admin.session()).cookie(admin.csrf().cookie())
                        .header(admin.csrf().headerName(), admin.csrf().token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "displayName", "湖畔钓友",
                                "role", "OPERATOR",
                                "enabled", true,
                                "expectedVersion", userVersion))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("钓友用户与工作人员不能互相转换角色")));

        long operatorVersion = jdbcTemplate.queryForObject(
                "SELECT version FROM app_user WHERE username = 'operator'", Long.class);
        mockMvc.perform(put("/api/admin/accounts/{id}", userId("operator"))
                        .session(admin.session()).cookie(admin.csrf().cookie())
                        .header(admin.csrf().headerName(), admin.csrf().token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "displayName", "运营人员",
                                "role", "USER",
                                "enabled", true,
                                "expectedVersion", operatorVersion))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("钓友用户与工作人员不能互相转换角色")));
    }

    private long userId(String username) {
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE username = ?", Long.class, username);
        assertNotNull(id);
        return id;
    }

    private SessionCredentials login(String username, String password) throws Exception {
        MockHttpSession session = new MockHttpSession();
        CsrfCredentials csrf = csrf(session);
        mockMvc.perform(post("/api/auth/login")
                        .session(session).cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", password))))
                .andExpect(status().isOk());
        return new SessionCredentials(session, csrf(session));
    }

    private CsrfCredentials csrf(MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf").session(session))
                .andExpect(status().isOk()).andReturn();
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

    private record CsrfCredentials(String headerName, String token, Cookie cookie) {
    }

    private record SessionCredentials(MockHttpSession session, CsrfCredentials csrf) {
    }
}
