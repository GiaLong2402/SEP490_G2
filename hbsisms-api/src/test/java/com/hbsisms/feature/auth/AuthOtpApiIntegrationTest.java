package com.hbsisms.feature.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbsisms.feature.customer.Customer;
import com.hbsisms.feature.customer.CustomerRepository;
import com.hbsisms.feature.customer.CustomerType;
import com.hbsisms.feature.user.AppUser;
import com.hbsisms.feature.user.UserRepository;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Khôi — IT đăng nhập / đăng ký OTP (Inter 2 task 5). Stub verifier, không gọi Firebase thật.
 * Requires Docker (Testcontainers Postgres).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class AuthOtpApiIntegrationTest {

    private static final String ADMIN_PASSWORD = "Admin@12345";
    private static final int WAREHOUSE_ROLE_ID = 4;
    private static final AtomicInteger SEQ = new AtomicInteger(1);

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private UserRepository userRepository;

    private String adminToken;

    @BeforeEach
    void loginAsAdmin() throws Exception {
        adminToken = passwordLogin("admin", ADMIN_PASSWORD);
    }

    @Test
    void otpLogin_provisionedStaffPhone_returns200() throws Exception {
        String phone = nextPhone();
        createWarehouse(uniqueName(), phone, true);

        postJson("/auth/login/otp", Map.of("idToken", stubToken("uid-wh-ok", phone)), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.roleCode").value("warehouse"));
    }

    @Test
    void otpLogin_unknownStaffPhone_returns403() throws Exception {
        postJson("/auth/login/otp", Map.of("idToken", stubToken("uid-ghost", nextPhone())), null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(AuthService.ACCOUNT_NOT_PROVISIONED));
    }

    @Test
    void otpRegister_newCustomer_returns201WithCustomerRole() throws Exception {
        String phone = nextPhone();

        postJson(
                        "/auth/register/otp",
                        Map.of(
                                "idToken", stubToken("uid-cust-new", phone),
                                "fullName", "Khách OTP Mới",
                                "email", "otp" + SEQ.get() + "@test.vn"),
                        null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.roleCode").value("customer"))
                .andExpect(jsonPath("$.user.fullName").value("Khách OTP Mới"));

        assertThat(customerRepository.findFirstByPhoneAndAccountUserIsNullOrderByIdAsc(phone)).isEmpty();
        AppUser user = userRepository.findByPhone(phone).orElseThrow();
        assertThat(user.getUserType().name()).isEqualTo("CUSTOMER");
    }

    @Test
    void otpRegister_linksWalkInCustomerByPhone() throws Exception {
        String phone = nextPhone();
        Customer walkIn = new Customer();
        walkIn.setCode("KH-T" + String.format("%03d", SEQ.getAndIncrement() % 1000));
        walkIn.setCustomerType(CustomerType.B2C);
        walkIn.setName("Khách quầy");
        walkIn.setPhone(phone);
        walkIn.setCreditLimit(BigDecimal.ZERO);
        walkIn.setNetDays((short) 0);
        walkIn.setCurrentDebt(BigDecimal.ZERO);
        walkIn.setActive(true);
        walkIn = customerRepository.save(walkIn);
        Long customerId = walkIn.getId();

        postJson(
                        "/auth/register/otp",
                        Map.of(
                                "idToken", stubToken("uid-link-" + customerId, phone),
                                "fullName", "Khách quầy"),
                        null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.roleCode").value("customer"));

        Customer linked = customerRepository.findWithAccountById(customerId).orElseThrow();
        assertThat(linked.getAccountUser()).isNotNull();
        assertThat(linked.getAccountUser().getPhone()).isEqualTo(phone);
    }

    @Test
    void otpLogin_lockedStaff_returns403() throws Exception {
        String phone = nextPhone();
        String username = uniqueName();
        long id = createWarehouse(username, phone, true);
        Map<String, Object> body = userBody(username, phone, false, null);
        putJson("/admin/users/" + id, body, adminToken).andExpect(status().isOk());

        postJson("/auth/login/otp", Map.of("idToken", stubToken("uid-locked", phone)), null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(AuthService.ACCOUNT_LOCKED));
    }

    @Test
    void otpLogin_thenLogout_revokesToken() throws Exception {
        String phone = nextPhone();
        createWarehouse(uniqueName(), phone, true);

        String token = objectMapper
                .readTree(postJson("/auth/login/otp", Map.of("idToken", stubToken("uid-logout", phone)), null)
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("token")
                .asText();

        postJson("/auth/logout", null, token).andExpect(status().isNoContent());

        mockMvc.perform(get("/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void passwordLogin_admin_stillWorks() throws Exception {
        postJson("/auth/login", Map.of("username", "admin", "password", ADMIN_PASSWORD), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.roleCode").value("admin"))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void otpRegister_duplicatePhone_returns409() throws Exception {
        String phone = nextPhone();
        postJson(
                        "/auth/register/otp",
                        Map.of("idToken", stubToken("uid-dup-1", phone), "fullName", "Lần 1"),
                        null)
                .andExpect(status().isCreated());

        postJson(
                        "/auth/register/otp",
                        Map.of("idToken", stubToken("uid-dup-2", phone), "fullName", "Lần 2"),
                        null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(AuthService.PHONE_ALREADY_REGISTERED));
    }

    private long createWarehouse(String username, String phone, boolean active) throws Exception {
        String body = postJson("/admin/users", userBody(username, phone, active, "Passw0rd!"), adminToken)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private static Map<String, Object> userBody(String username, String phone, boolean active, String password) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", username);
        body.put("fullName", "NV Kho OTP");
        body.put("email", null);
        body.put("phone", phone);
        body.put("roleId", WAREHOUSE_ROLE_ID);
        body.put("position", "Kho");
        body.put("employeeCode", null);
        body.put("isActive", active);
        body.put("password", password);
        return body;
    }

    private String passwordLogin(String username, String password) throws Exception {
        String body = postJson("/auth/login", Map.of("username", username, "password", password), null)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    private ResultActions postJson(String path, Object body, String token) throws Exception {
        var request = post(path).contentType(MediaType.APPLICATION_JSON);
        if (body != null) {
            request.content(objectMapper.writeValueAsString(body));
        }
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return mockMvc.perform(request);
    }

    private ResultActions putJson(String path, Object body, String token) throws Exception {
        return mockMvc.perform(put(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private static String stubToken(String uid, String phone) {
        return "stub." + uid + "." + phone;
    }

    private static String nextPhone() {
        int n = SEQ.getAndIncrement();
        return "09" + String.format("%08d", 10_000_000 + (n % 80_000_000));
    }

    private static String uniqueName() {
        return "w" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}
