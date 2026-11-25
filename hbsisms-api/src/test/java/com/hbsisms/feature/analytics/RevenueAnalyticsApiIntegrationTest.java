package com.hbsisms.feature.analytics;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Doanh — IT analytics revenue (Inter 4): auth reports + shape.
 * Không Docker: Postgres từ {@code .env} / profile {@code it-external}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it-external")
class RevenueAnalyticsApiIntegrationTest {

    static {
        System.setProperty("user.timezone", "UTC");
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
    }

    private static final int SALES_ROLE_ID = 3;
    private static final AtomicInteger SEQ = new AtomicInteger(1);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Value("${app.seed.admin-password}")
    private String adminPassword;

    private String adminToken;
    private String salesToken;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = login("admin", adminPassword);
        String salesUser = "sales_rev_" + SEQ.getAndIncrement() + "_"
                + UUID.randomUUID().toString().substring(0, 6);
        createSalesUser(adminToken, salesUser);
        salesToken = login(salesUser, "Passw0rd!");
    }

    @Test
    void revenue_withReports_returnsSeriesShape() throws Exception {
        LocalDate to = LocalDate.now(AnalyticsDateSupport.VN);
        LocalDate from = to.minusDays(7);

        mockMvc.perform(get("/analytics/revenue")
                        .param("fromDate", from.toString())
                        .param("toDate", to.toString())
                        .param("groupBy", "day")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fromDate").value(from.toString()))
                .andExpect(jsonPath("$.toDate").value(to.toString()))
                .andExpect(jsonPath("$.groupBy").value("day"))
                .andExpect(jsonPath("$.revenue").exists())
                .andExpect(jsonPath("$.cogs").exists())
                .andExpect(jsonPath("$.profit").exists())
                .andExpect(jsonPath("$.marginPct").exists())
                .andExpect(jsonPath("$.orderCount").exists())
                .andExpect(jsonPath("$.series").isArray())
                .andExpect(jsonPath("$.previousFrom").exists())
                .andExpect(jsonPath("$.previousTo").exists())
                .andExpect(jsonPath("$.previousRevenue").exists())
                .andExpect(jsonPath("$.previousProfit").exists())
                .andExpect(jsonPath("$.previousOrderCount").exists());
    }

    @Test
    void revenue_withoutReports_forbidden() throws Exception {
        LocalDate to = LocalDate.now(AnalyticsDateSupport.VN);
        mockMvc.perform(get("/analytics/revenue")
                        .param("fromDate", to.toString())
                        .param("toDate", to.toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(salesToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void revenue_badGroupBy_badRequest() throws Exception {
        LocalDate to = LocalDate.now(AnalyticsDateSupport.VN);
        mockMvc.perform(get("/analytics/revenue")
                        .param("fromDate", to.toString())
                        .param("toDate", to.toString())
                        .param("groupBy", "week")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isBadRequest());
    }

    private String login(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("username", username, "password", password))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    private void createSalesUser(String adminToken, String username) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", username);
        body.put("fullName", "Sales Rev IT");
        body.put("email", null);
        body.put("phone", "09" + String.format("%08d", Math.floorMod(username.hashCode(), 100_000_000)));
        body.put("roleId", SALES_ROLE_ID);
        body.put("position", "Kinh doanh");
        body.put("employeeCode", null);
        body.put("isActive", true);
        body.put("password", "Passw0rd!");
        mockMvc.perform(post("/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
