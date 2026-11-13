package com.hbsisms.feature.storefront;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbsisms.feature.catalog.ProductVariant;
import com.hbsisms.feature.catalog.ProductVariantRepository;
import com.hbsisms.feature.customer.Customer;
import com.hbsisms.feature.customer.CustomerRepository;
import com.hbsisms.feature.customer.CustomerType;
import com.hbsisms.feature.inventory.Location;
import com.hbsisms.feature.inventory.LocationRepository;
import com.hbsisms.feature.inventory.Lot;
import com.hbsisms.feature.inventory.LotRepository;
import com.hbsisms.feature.inventory.Stock;
import com.hbsisms.feature.inventory.StockRepository;
import com.hbsisms.feature.inventory.Warehouse;
import com.hbsisms.feature.inventory.WarehouseRepository;
import com.hbsisms.feature.payment.payos.PayosClient;
import com.hbsisms.feature.payment.payos.PayosPaymentLinkResult;
import com.hbsisms.feature.role.DefaultRoles;
import com.hbsisms.feature.role.RoleRepository;
import com.hbsisms.feature.sales.SalesOrderRepository;
import com.hbsisms.feature.user.AppUser;
import com.hbsisms.feature.user.UserRepository;
import com.hbsisms.feature.user.UserType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Duy — IT mua hàng Inter 2 #31: catalog → giỏ → checkout → /me/orders.
 * Profile {@code it-external} (Postgres từ .env). PayOS HTTP mock.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it-external")
class StorefrontPurchaseApiIntegrationTest {

    static {
        System.setProperty("user.timezone", "UTC");
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
    }

    private static final AtomicInteger SEQ = new AtomicInteger(1);

    @DynamicPropertySource
    static void testProps(DynamicPropertyRegistry registry) {
        // Ép stub dù .env có FIREBASE_AUTH_MODE=firebase — IT không gọi Firebase thật.
        registry.add("app.security.firebase.mode", () -> "stub");
        registry.add("app.payment.payos.mode", () -> "sandbox");
        registry.add("app.payment.payos.client-id", () -> "it-client");
        registry.add("app.payment.payos.api-key", () -> "it-api-key");
        registry.add("app.payment.payos.checksum-key", () -> "it-checksum-key");
        registry.add("app.payment.payos.return-url", () -> "http://localhost:5173/thanh-toan/ket-qua");
        registry.add("app.payment.payos.cancel-url", () -> "http://localhost:5173/thanh-toan/ket-qua");
    }

    @MockitoBean
    private PayosClient payosClient;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ProductVariantRepository productVariantRepository;
    @Autowired
    private SalesOrderRepository salesOrderRepository;
    @Autowired
    private WarehouseRepository warehouseRepository;
    @Autowired
    private LocationRepository locationRepository;
    @Autowired
    private LotRepository lotRepository;
    @Autowired
    private StockRepository stockRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private ProductVariant variant;

    @BeforeEach
    void setUp() {
        when(payosClient.createPaymentRequest(anyLong(), anyInt(), anyString(), anyLong()))
                .thenAnswer(inv -> new PayosPaymentLinkResult(
                        inv.getArgument(0),
                        inv.getArgument(1),
                        "https://pay.payos.vn/web/it-" + inv.getArgument(0),
                        "QR-IT",
                        "plink-it-" + inv.getArgument(0),
                        "PENDING"));

        variant = productVariantRepository
                .findBySkuIgnoreCase("TOM-SU-500")
                .or(() -> productVariantRepository.findBySkuIgnoreCase("TOM-SU-05"))
                .orElse(null);
        if (variant != null) {
            ensureAvailableStock(variant, 15);
        }
    }

    @Test
    void purchaseFlow_catalogCartCheckout_andListMine() throws Exception {
        if (variant == null) {
            return;
        }
        PortalCustomer a = registerCustomer("Mua Hang A");
        String session = UUID.randomUUID().toString();

        mockMvc.perform(get("/products").param("page", "1").param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());

        mockMvc.perform(post("/carts/current/items")
                        .header(CartService.SESSION_HEADER, session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cartBody(variant.getId(), 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.items[0].unitPrice").exists());

        String idem = "idem-flow-" + SEQ.getAndIncrement() + "-" + UUID.randomUUID();
        MvcResult checkout = mockMvc.perform(post("/checkout")
                        .header(HttpHeaders.AUTHORIZATION, bearer(a.token()))
                        .header("Idempotency-Key", idem)
                        .header(CartService.SESSION_HEADER, session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkoutBody(variant.getId(), 1))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.order.code").exists())
                .andExpect(jsonPath("$.payment.checkoutUrl").exists())
                .andReturn();

        JsonNode body = objectMapper.readTree(checkout.getResponse().getContentAsString());
        String code = body.get("order").get("code").asText();

        mockMvc.perform(get("/me/orders/" + code).header(HttpHeaders.AUTHORIZATION, bearer(a.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.state").value("PENDING"));

        mockMvc.perform(get("/me/orders")
                        .param("page", "1")
                        .param("pageSize", "20")
                        .header(HttpHeaders.AUTHORIZATION, bearer(a.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].code").value(code));
    }

    @Test
    void checkout_insufficientStock_returns409WithShortages() throws Exception {
        if (variant == null) {
            return;
        }
        PortalCustomer a = registerCustomer("Het Hang");
        String idem = "idem-short-" + SEQ.getAndIncrement() + "-" + UUID.randomUUID();

        mockMvc.perform(post("/checkout")
                        .header(HttpHeaders.AUTHORIZATION, bearer(a.token()))
                        .header("Idempotency-Key", idem)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkoutBody(variant.getId(), 9_999))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Một số sản phẩm không đủ hàng"))
                .andExpect(jsonPath("$.shortages[0].variantId").value(variant.getId()))
                .andExpect(jsonPath("$.shortages[0].requestedQty").value(9_999));
    }

    @Test
    void checkout_sameIdempotencyKey_returnsSameOrderOnlyOnce() throws Exception {
        if (variant == null) {
            return;
        }
        PortalCustomer a = registerCustomer("Idem Checkout");
        String idem = "idem-dup-" + SEQ.getAndIncrement() + "-" + UUID.randomUUID();
        Map<String, Object> body = checkoutBody(variant.getId(), 1);

        MvcResult first = mockMvc.perform(post("/checkout")
                        .header(HttpHeaders.AUTHORIZATION, bearer(a.token()))
                        .header("Idempotency-Key", idem)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode firstJson = objectMapper.readTree(first.getResponse().getContentAsString());
        String code = firstJson.get("order").get("code").asText();
        long orderId = firstJson.get("order").get("id").asLong();
        long ordersBefore = salesOrderRepository.count();

        mockMvc.perform(post("/checkout")
                        .header(HttpHeaders.AUTHORIZATION, bearer(a.token()))
                        .header("Idempotency-Key", idem)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.order.code").value(code))
                .andExpect(jsonPath("$.order.id").value(orderId));

        assertThat(salesOrderRepository.count()).isEqualTo(ordersBefore);
    }

    @Test
    void meOrders_foreignCode_returns404() throws Exception {
        if (variant == null) {
            return;
        }
        PortalCustomer owner = registerCustomer("Chu Don");
        PortalCustomer other = registerCustomer("Nguoi Khac");
        String idem = "idem-own-" + SEQ.getAndIncrement() + "-" + UUID.randomUUID();

        MvcResult created = mockMvc.perform(post("/checkout")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.token()))
                        .header("Idempotency-Key", idem)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkoutBody(variant.getId(), 1))))
                .andExpect(status().isCreated())
                .andReturn();

        String code = objectMapper
                .readTree(created.getResponse().getContentAsString())
                .get("order")
                .get("code")
                .asText();

        mockMvc.perform(get("/me/orders/" + code).header(HttpHeaders.AUTHORIZATION, bearer(other.token())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy đơn hàng"));
    }

    private void ensureAvailableStock(ProductVariant variant, int qty) {
        int n = SEQ.getAndIncrement();
        Warehouse warehouse = warehouseRepository.findByCodeIgnoreCase("KHO-HP").orElseGet(() -> {
            Warehouse created = new Warehouse();
            created.setCode("KHO-HP-IT-" + n);
            created.setName("Kho IT mua hang");
            created.setCapacity(500);
            created.setActive(true);
            return warehouseRepository.save(created);
        });
        Location location = locationRepository.findByWarehouse_IdOrderByCodeAsc(warehouse.getId()).stream()
                .findFirst()
                .orElseGet(() -> {
                    Location created = new Location();
                    created.setWarehouse(warehouse);
                    created.setCode("IT-A");
                    created.setZone("Z");
                    created.setCapacity(100);
                    return locationRepository.save(created);
                });

        Lot lot = new Lot();
        lot.setLotCode("LOT-BUY-IT-" + n + "-" + UUID.randomUUID().toString().substring(0, 8));
        lot.setVariant(variant);
        lot.setMfgDate(LocalDate.now().minusDays(1));
        lot.setExpDate(LocalDate.now().plusDays(40));
        lot.setReceivedAt(Instant.now());
        lot = lotRepository.save(lot);

        Stock stock = new Stock();
        stock.setVariant(variant);
        stock.setLot(lot);
        stock.setLocation(location);
        stock.setQtyAvailable(qty);
        stock.setQtyHeld(0);
        stock.setQtyQuarantine(0);
        stock.setQtyExpired(0);
        stockRepository.save(stock);
    }

    /** Tạo user customer + KH B2C rồi login mật khẩu (không phụ thuộc Firebase). */
    private PortalCustomer registerCustomer(String fullName) throws Exception {
        int n = SEQ.getAndIncrement();
        String suffix = n + UUID.randomUUID().toString().substring(0, 6);
        String username = "buy_it_" + suffix;
        String phone = "09" + String.format("%08d", 40_000_000 + (Math.abs(suffix.hashCode()) % 50_000_000));
        String password = "Passw0rd!";

        AppUser user = new AppUser();
        user.setUsername(username);
        user.setFullName(fullName);
        user.setPhone(phone);
        user.setEmail("buy_" + suffix + "@test.vn");
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setUserType(UserType.CUSTOMER);
        user.setActive(true);
        user.setRole(roleRepository
                .findByCode(DefaultRoles.CUSTOMER)
                .orElseThrow(() -> new IllegalStateException("Missing role customer")));
        user = userRepository.save(user);

        Customer customer = new Customer();
        customer.setCode("KH-IT-" + suffix);
        customer.setCustomerType(CustomerType.B2C);
        customer.setName(fullName);
        customer.setPhone(phone);
        customer.setCreditLimit(BigDecimal.ZERO);
        customer.setNetDays((short) 0);
        customer.setCurrentDebt(BigDecimal.ZERO);
        customer.setActive(true);
        customer.setAccountUser(user);
        customerRepository.save(customer);

        String body = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("username", username, "password", password))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return new PortalCustomer(objectMapper.readTree(body).get("token").asText());
    }

    private static Map<String, Object> cartBody(long variantId, int qty) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("variantId", variantId);
        map.put("qty", qty);
        return map;
    }

    private static Map<String, Object> checkoutBody(long variantId, int qtyKhay) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("fulfillmentMethod", "PICKUP");
        map.put("requestedDate", LocalDate.now().plusDays(1).toString());
        map.put("timeWindow", "08:00-10:00");
        map.put("paymentMethod", "PAYOS");
        map.put("lines", List.of(Map.of("variantId", variantId, "qtyKhay", qtyKhay)));
        return map;
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private record PortalCustomer(String token) {}
}
