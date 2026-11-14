package com.hbsisms.feature.payment.payos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import com.hbsisms.feature.payment.PayTxnStatus;
import com.hbsisms.feature.payment.Payment;
import com.hbsisms.feature.payment.PaymentReconcileFlag;
import com.hbsisms.feature.payment.PaymentRepository;
import com.hbsisms.feature.payment.PaymentStatus;
import com.hbsisms.feature.role.DefaultRoles;
import com.hbsisms.feature.role.RoleRepository;
import com.hbsisms.feature.sales.AllocationStatus;
import com.hbsisms.feature.sales.OrderEvent;
import com.hbsisms.feature.sales.OrderEventRepository;
import com.hbsisms.feature.sales.OrderState;
import com.hbsisms.feature.sales.SalesOrder;
import com.hbsisms.feature.sales.SalesOrderRepository;
import com.hbsisms.feature.system.AppConfigService;
import com.hbsisms.feature.system.Notification;
import com.hbsisms.feature.system.NotificationRepository;
import com.hbsisms.feature.user.AppUser;
import com.hbsisms.feature.user.UserRepository;
import com.hbsisms.feature.user.UserType;
import com.hbsisms.security.TokenService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * Long — IT thanh toán PayOS (Inter 2 · task "Test thanh toán: Unit + Integration").
 *
 * <p>Gọi API thật qua MockMvc trên DB online (profile {@code it-external}); PayOS HTTP được mock
 * ({@link PayosClient}), webhook ký thật bằng checksum key giả. {@code @Transactional}: mọi dữ liệu test
 * (user, KH, lô, tồn, đơn, payment, event, notification) tự rollback sau mỗi test — không để rác DB chung.
 *
 * <p>Token cấp thẳng bằng {@link TokenService}: {@code /auth/login} chạy ngoài transaction
 * ({@code NOT_SUPPORTED}) nên không thấy user test chưa commit. Kiểm tra JWT vẫn đi qua filter thật.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it-external")
@Transactional
class PayosPaymentApiIntegrationTest {

    static {
        System.setProperty("user.timezone", "UTC");
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
    }

    private static final String CHECKSUM = "it-checksum-key";
    private static final AtomicInteger SEQ = new AtomicInteger(1);

    @DynamicPropertySource
    static void testProps(DynamicPropertyRegistry registry) {
        registry.add("app.security.firebase.mode", () -> "stub");
        registry.add("app.payment.payos.mode", () -> "sandbox");
        registry.add("app.payment.payos.client-id", () -> "it-client");
        registry.add("app.payment.payos.api-key", () -> "it-api-key");
        registry.add("app.payment.payos.checksum-key", () -> CHECKSUM);
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
    private OrderEventRepository orderEventRepository;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private NotificationRepository notificationRepository;
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
    @Autowired
    private AppConfigService appConfigService;
    @Autowired
    private TokenService tokenService;
    @PersistenceContext
    private EntityManager entityManager;

    private ProductVariant variant;
    private AppUser owner;

    @BeforeEach
    void setUp() {
        reset(payosClient);
        when(payosClient.createPaymentRequest(anyLong(), anyInt(), anyString(), anyLong()))
                .thenAnswer(inv -> new PayosPaymentLinkResult(
                        inv.getArgument(0),
                        inv.getArgument(1),
                        "https://pay.payos.vn/web/it-" + inv.getArgument(0),
                        "QR-IT",
                        "plink-it-" + inv.getArgument(0),
                        "PENDING"));
        // Mặc định PayOS "không trả lời" → job hoãn các đơn khác trong DB chung (đằng nào cũng rollback)
        when(payosClient.getPaymentRequest(anyLong())).thenReturn(Optional.empty());

        variant = productVariantRepository
                .findBySkuIgnoreCase("TOM-SU-500")
                .or(() -> productVariantRepository.findBySkuIgnoreCase("TOM-SU-05"))
                .orElse(null);
        assumeTrue(variant != null, "Cần variant demo TOM-SU-500 / TOM-SU-05");
        ensureAvailableStock(variant, 15);
        owner = createOwner();
    }

    // ---------------------------------------------------------------- tạo link

    @Test
    void checkout_createsPendingPayos_andRelinkReusesLink() throws Exception {
        String token = registerCustomer("PayOS Link");
        Checkout c = checkout(token);

        Payment payment = payment(c.payosOrderCode());
        assertThat(payment.getStatus()).isEqualTo(PayTxnStatus.PENDING);
        assertThat(payment.getCheckoutUrl()).isEqualTo("https://pay.payos.vn/web/it-" + c.payosOrderCode());
        assertThat(payment.getAmount()).isEqualByComparingTo(c.amount());

        // "Thanh toán lại" khi link còn hạn → trả đúng link cũ, không tạo link mới trên PayOS
        perform(post("/payments/payos/" + c.payosOrderCode() + "/link").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkoutUrl").value(payment.getCheckoutUrl()));
        verify(payosClient, times(1)).createPaymentRequest(anyLong(), anyInt(), anyString(), anyLong());

        // Link PayOS sống tới giờ giữ hàng + ân hạn
        SalesOrder order = order(c.orderId());
        long graceSeconds = appConfigService.paymentGraceMinutes() * 60L;
        org.mockito.ArgumentCaptor<Long> expiredAt = org.mockito.ArgumentCaptor.forClass(Long.class);
        verify(payosClient).createPaymentRequest(eq(c.payosOrderCode()), anyInt(), anyString(), expiredAt.capture());
        // DB làm tròn thời gian tới micro giây → cho phép lệch 1 giây khi đọc lại
        assertThat(expiredAt.getValue())
                .isCloseTo(order.getHoldExpiresAt().plusSeconds(graceSeconds).getEpochSecond(),
                        org.assertj.core.data.Offset.offset(1L));
    }

    @Test
    void relink_foreignCustomer404_unauthenticatedBlocked() throws Exception {
        Checkout c = checkout(registerCustomer("Chu Don"));
        String other = registerCustomer("Nguoi Khac");

        perform(post("/payments/payos/" + c.payosOrderCode() + "/link").header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isNotFound());
        perform(get("/payments/payos/return").param("orderCode", String.valueOf(c.payosOrderCode()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isNotFound());

        int unauth = perform(get("/payments/payos/return").param("orderCode", String.valueOf(c.payosOrderCode())))
                .andReturn().getResponse().getStatus();
        assertThat(unauth).isIn(401, 403);
    }

    // ---------------------------------------------------------------- webhook

    @Test
    void webhook_valid_marksPaid_andResendIsIdempotent() throws Exception {
        Checkout c = checkout(registerCustomer("Webhook OK"));
        Map<String, Object> data = webhookData(c.payosOrderCode(), c.amount(), "FT-IT-OK");

        postWebhook(data, sign(data)).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        postWebhook(data, sign(data)).andExpect(status().isOk()); // PayOS gửi lại đúng tin đó

        Payment payment = payment(c.payosOrderCode());
        assertThat(payment.getStatus()).isEqualTo(PayTxnStatus.SUCCESS);
        assertThat(payment.getReference()).isEqualTo("FT-IT-OK");
        assertThat(payment.getRawPayload()).containsKey("webhook");
        SalesOrder order = order(c.orderId());
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(order.getHoldExpiresAt()).isNull();
        assertThat(eventTypes(c.orderId()).stream().filter("paid"::equals).count()).isEqualTo(1);
    }

    @Test
    void webhook_badSignature_returns401_noChange() throws Exception {
        Checkout c = checkout(registerCustomer("Sai Chu Ky"));
        Map<String, Object> data = webhookData(c.payosOrderCode(), c.amount(), "FT-IT-BAD");

        postWebhook(data, "0".repeat(64)).andExpect(status().isUnauthorized());

        assertThat(payment(c.payosOrderCode()).getStatus()).isEqualTo(PayTxnStatus.PENDING);
        assertThat(order(c.orderId()).getPaymentStatus()).isNotEqualTo(PaymentStatus.PAID);
    }

    @Test
    void webhook_tamperedAmount_returns401() throws Exception {
        // Kẻ xấu sửa số tiền nhưng giữ chữ ký cũ → chữ ký không khớp
        Checkout c = checkout(registerCustomer("Sua Tien"));
        Map<String, Object> data = webhookData(c.payosOrderCode(), c.amount(), "FT-IT-TAMPER");
        String sig = sign(data);
        data.put("amount", c.amount().intValueExact() * 10);

        postWebhook(data, sig).andExpect(status().isUnauthorized());

        assertThat(payment(c.payosOrderCode()).getStatus()).isEqualTo(PayTxnStatus.PENDING);
        assertThat(payment(c.payosOrderCode()).getAmount()).isEqualByComparingTo(c.amount());
    }

    @Test
    void webhook_unknownOrderOrFailCode_acksNoChange() throws Exception {
        Checkout c = checkout(registerCustomer("Ma La"));

        Map<String, Object> unknown = webhookData(9_000_000_000L + SEQ.getAndIncrement(), c.amount(), "FT-IT-UNK");
        postWebhook(unknown, sign(unknown)).andExpect(status().isOk());

        Map<String, Object> failed = webhookData(c.payosOrderCode(), c.amount(), "FT-IT-FAIL");
        failed.put("code", "01");
        failed.put("desc", "Giao dịch thất bại");
        postWebhook(failed, sign(failed)).andExpect(status().isOk());

        assertThat(payment(c.payosOrderCode()).getStatus()).isEqualTo(PayTxnStatus.PENDING);
        assertThat(eventTypes(c.orderId())).doesNotContain("paid");
    }

    @Test
    void webhook_lessAmount_flagsShort_notPaid() throws Exception {
        Checkout c = checkout(registerCustomer("Chuyen Thieu"));
        BigDecimal less = c.amount().subtract(BigDecimal.valueOf(1000));
        Map<String, Object> data = webhookData(c.payosOrderCode(), less, "FT-IT-SHORT");

        postWebhook(data, sign(data)).andExpect(status().isOk());
        postWebhook(data, sign(data)).andExpect(status().isOk()); // gửi lại → không báo Chủ lần 2

        Payment payment = payment(c.payosOrderCode());
        assertThat(payment.getStatus()).isEqualTo(PayTxnStatus.PENDING);
        assertThat(payment.getReconcileFlag()).isEqualTo(PaymentReconcileFlag.SHORT);
        assertThat(payment.getNote()).contains("lệch số tiền");
        assertThat(order(c.orderId()).getPaymentStatus()).isNotEqualTo(PaymentStatus.PAID);
        assertThat(ownerNotifications("payment_mismatch")).hasSize(1);
    }

    @Test
    void webhook_moreAmount_paidAndFlagsOver() throws Exception {
        Checkout c = checkout(registerCustomer("Chuyen Thua"));
        BigDecimal more = c.amount().add(BigDecimal.valueOf(8000));
        Map<String, Object> data = webhookData(c.payosOrderCode(), more, "FT-IT-OVER");

        postWebhook(data, sign(data)).andExpect(status().isOk());

        Payment payment = payment(c.payosOrderCode());
        assertThat(payment.getStatus()).isEqualTo(PayTxnStatus.SUCCESS);
        assertThat(payment.getAmount()).isEqualByComparingTo(more); // ghi đúng tiền thực nhận
        assertThat(payment.getReconcileFlag()).isEqualTo(PaymentReconcileFlag.OVER);
        assertThat(payment.getNote()).contains("thừa 8.000đ");
        assertThat(order(c.orderId()).getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(ownerNotifications("payment_overpaid")).hasSize(1);

        // Trang Thanh toán nội bộ trả excessAmount cho form hoàn tiền
        perform(get("/payments").param("q", c.orderCode()).header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].reconcileFlag").value("over"))
                .andExpect(jsonPath("$.items[0].excessAmount").value(8000));
    }

    // ---------------------------------------------------------------- khách quay về

    @Test
    void return_payosPaid_marksPaid_andUrlStatusIgnored() throws Exception {
        String token = registerCustomer("Quay Ve");
        Checkout c = checkout(token);

        // PayOS chưa có tiền: URL có gắn status=PAID cũng không được tin
        when(payosClient.getPaymentRequest(c.payosOrderCode()))
                .thenReturn(Optional.of(new PayosPaymentInfoResult(c.payosOrderCode(), c.amount().intValueExact(), "PENDING", "plink")));
        perform(get("/payments/payos/return")
                        .param("orderCode", String.valueOf(c.payosOrderCode()))
                        .param("status", "PAID")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
        assertThat(payment(c.payosOrderCode()).getStatus()).isEqualTo(PayTxnStatus.PENDING);

        // PayOS báo đã trả → hệ thống ghi nhận
        when(payosClient.getPaymentRequest(c.payosOrderCode()))
                .thenReturn(Optional.of(new PayosPaymentInfoResult(c.payosOrderCode(), c.amount().intValueExact(), "PAID", "plink")));
        perform(get("/payments/payos/return")
                        .param("orderCode", String.valueOf(c.payosOrderCode()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
        assertThat(payment(c.payosOrderCode()).getStatus()).isEqualTo(PayTxnStatus.SUCCESS);
        assertThat(order(c.orderId()).getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void return_paidThenOverAmount_viaReturn() throws Exception {
        // Đường web hỏi PayOS (nhanh hơn webhook) cũng nhận ra chuyển thừa nhờ amountPaid
        String token = registerCustomer("Thua Qua Return");
        Checkout c = checkout(token);
        int paid = c.amount().intValueExact() + 8000;
        when(payosClient.getPaymentRequest(c.payosOrderCode()))
                .thenReturn(Optional.of(new PayosPaymentInfoResult(
                        c.payosOrderCode(), c.amount().intValueExact(), "PAID", "plink", paid)));

        perform(get("/payments/payos/return")
                        .param("orderCode", String.valueOf(c.payosOrderCode()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));

        Payment payment = payment(c.payosOrderCode());
        assertThat(payment.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(paid));
        assertThat(payment.getReconcileFlag()).isEqualTo(PaymentReconcileFlag.OVER);

        // Webhook đến sau với cùng số tiền → không báo Chủ lần 2, chỉ lưu bằng chứng
        Map<String, Object> data = webhookData(c.payosOrderCode(), BigDecimal.valueOf(paid), "FT-IT-LATER");
        postWebhook(data, sign(data)).andExpect(status().isOk());
        assertThat(payment(c.payosOrderCode()).getReference()).isEqualTo("FT-IT-LATER");
        assertThat(ownerNotifications("payment_overpaid")).hasSize(1);
    }

    // ---------------------------------------------------------------- hết hạn + webhook muộn

    @Test
    void holdExpire_thenLateWebhook_reholdsAndPaid() throws Exception {
        Checkout c = checkout(registerCustomer("Het Han Cuu Duoc"));
        expireBeyondGrace(c);
        when(payosClient.getPaymentRequest(c.payosOrderCode()))
                .thenReturn(Optional.of(new PayosPaymentInfoResult(c.payosOrderCode(), c.amount().intValueExact(), "PENDING", "plink")));

        runHoldExpireJob();

        SalesOrder expired = order(c.orderId());
        assertThat(expired.getState()).isEqualTo(OrderState.CANCELLED);
        assertThat(eventTypes(c.orderId())).contains("expired");
        assertThat(payment(c.payosOrderCode()).getStatus()).isEqualTo(PayTxnStatus.CANCELLED);
        verify(payosClient).cancelPaymentRequest(anyString(), anyString());

        // Tiền về sau 15 phút (+ ân hạn) — kho vẫn đủ → giữ lại hàng, đơn chạy tiếp
        Map<String, Object> data = webhookData(c.payosOrderCode(), c.amount(), "FT-IT-LATE");
        postWebhook(data, sign(data)).andExpect(status().isOk());

        SalesOrder rescued = order(c.orderId());
        assertThat(rescued.getState()).isNotEqualTo(OrderState.CANCELLED);
        assertThat(rescued.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(rescued.getAllocationStatus()).isEqualTo(AllocationStatus.FULL);
        assertThat(eventTypes(c.orderId())).contains("late_paid_reheld");
        assertThat(payment(c.payosOrderCode()).getStatus()).isEqualTo(PayTxnStatus.SUCCESS);
    }

    @Test
    void holdExpire_withinGrace_notCancelled() throws Exception {
        // Quá 15 phút 30 giây nhưng còn trong ân hạn → job chưa đụng tới đơn
        Checkout c = checkout(registerCustomer("Con An Han"));
        SalesOrder order = order(c.orderId());
        order.setHoldExpiresAt(Instant.now().minusSeconds(30));
        salesOrderRepository.saveAndFlush(order);

        runHoldExpireJob();

        assertThat(order(c.orderId()).getState()).isNotEqualTo(OrderState.CANCELLED);
        assertThat(payment(c.payosOrderCode()).getStatus()).isEqualTo(PayTxnStatus.PENDING);
        verify(payosClient, never()).getPaymentRequest(c.payosOrderCode());
    }

    @Test
    void holdExpire_lateWebhook_noStock_needsRefund() throws Exception {
        Checkout c = checkout(registerCustomer("Het Han Het Hang"));
        expireBeyondGrace(c);
        when(payosClient.getPaymentRequest(c.payosOrderCode()))
                .thenReturn(Optional.of(new PayosPaymentInfoResult(c.payosOrderCode(), c.amount().intValueExact(), "PENDING", "plink")));
        runHoldExpireJob();
        assertThat(order(c.orderId()).getState()).isEqualTo(OrderState.CANCELLED);

        // Trong lúc chờ, hàng đã bán hết (chỉ trong transaction test, sẽ rollback)
        for (Stock stock : stockRepository.findAll()) {
            if (stock.getVariant() != null && variant.getId().equals(stock.getVariant().getId())) {
                stock.setQtyAvailable(0);
                stockRepository.save(stock);
            }
        }
        stockRepository.flush();

        Map<String, Object> data = webhookData(c.payosOrderCode(), c.amount(), "FT-IT-NOSTOCK");
        postWebhook(data, sign(data)).andExpect(status().isOk());

        SalesOrder order = order(c.orderId());
        assertThat(order.getState()).isEqualTo(OrderState.CANCELLED);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.REFUND_PENDING);
        assertThat(eventTypes(c.orderId())).contains("needs_refund");
        Payment payment = payment(c.payosOrderCode());
        assertThat(payment.getStatus()).isEqualTo(PayTxnStatus.SUCCESS);
        assertThat(payment.getRawPayload()).containsEntry("needsRefund", true);
        assertThat(ownerNotifications("payment_refund")).hasSize(1);
    }

    @Test
    void customerCancel_locksLink_thenLateWebhook_needsRefund() throws Exception {
        String token = registerCustomer("Khach Huy");
        Checkout c = checkout(token);

        perform(post("/me/orders/" + c.orderCode() + "/cancel").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
        verify(payosClient).cancelPaymentRequest(anyString(), eq("Khách hủy đơn"));
        assertThat(payment(c.payosOrderCode()).getStatus()).isEqualTo(PayTxnStatus.CANCELLED);

        // Khách vẫn lỡ chuyển (VD đã quét QR từ trước) → không cứu đơn, chờ hoàn tiền
        Map<String, Object> data = webhookData(c.payosOrderCode(), c.amount(), "FT-IT-AFTERCANCEL");
        postWebhook(data, sign(data)).andExpect(status().isOk());

        SalesOrder order = order(c.orderId());
        assertThat(order.getState()).isEqualTo(OrderState.CANCELLED);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.REFUND_PENDING);
        assertThat(eventTypes(c.orderId())).doesNotContain("late_paid_reheld");
        assertThat(ownerNotifications("payment_refund")).hasSize(1);
    }

    @Test
    void staffCancel_locksPayosLink() throws Exception {
        Checkout c = checkout(registerCustomer("Cua Hang Huy"));

        perform(post("/orders/" + c.orderId() + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "Hết hàng đột xuất"))))
                .andExpect(status().isNoContent());

        verify(payosClient).cancelPaymentRequest(anyString(), eq("Cửa hàng hủy đơn"));
        Payment payment = payment(c.payosOrderCode());
        assertThat(payment.getStatus()).isEqualTo(PayTxnStatus.CANCELLED);
        assertThat(payment.getCheckoutUrl()).isNull();
        assertThat(order(c.orderId()).getState()).isEqualTo(OrderState.CANCELLED);
    }

    // ---------------------------------------------------------------- hoàn tiền + đối soát

    @Test
    void refund_evidenceRules_partialThenSort() throws Exception {
        Checkout c = checkout(registerCustomer("Hoan Phan Thua"));
        BigDecimal more = c.amount().add(BigDecimal.valueOf(8000));
        Map<String, Object> data = webhookData(c.payosOrderCode(), more, "FT-IT-REFUND");
        postWebhook(data, sign(data)).andExpect(status().isOk());
        Payment source = payment(c.payosOrderCode());
        String admin = adminToken();

        // Chuyển khoản thiếu mã giao dịch → 400; tiền mặt thiếu ghi chú → 400
        postRefund(admin, c.orderId(), source.getId(), "bank_transfer", 8000, null, null).andExpect(status().isBadRequest());
        postRefund(admin, c.orderId(), source.getId(), "cash", 8000, null, null).andExpect(status().isBadRequest());

        // Hoàn đúng phần thừa → 201, đơn vẫn PAID
        postRefund(admin, c.orderId(), source.getId(), "bank_transfer", 8000, "FT-IT-HOAN", "Hoàn phần chuyển thừa")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.direction").value("refund"));
        assertThat(order(c.orderId()).getPaymentStatus()).isEqualTo(PaymentStatus.PAID);

        // Hoàn vượt số còn lại → 409
        postRefund(admin, c.orderId(), source.getId(), "bank_transfer", c.amount().intValueExact() + 1, "FT-IT-X", null)
                .andExpect(status().isConflict());

        // Sắp xếp: mặc định mới nhất trước (phiếu hoàn lên đầu); sort=asc cũ nhất trước
        perform(get("/payments").param("q", c.orderCode()).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].direction").value("refund"));
        perform(get("/payments").param("q", c.orderCode()).param("sort", "asc")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].direction").value("in"));
    }

    @Test
    void payments_reconcileFlagPatchAndFilter() throws Exception {
        Checkout c = checkout(registerCustomer("Doi Soat Tay"));
        Map<String, Object> data = webhookData(c.payosOrderCode(), c.amount(), "FT-IT-RECON");
        postWebhook(data, sign(data)).andExpect(status().isOk());
        Payment payment = payment(c.payosOrderCode());
        String admin = adminToken();

        // Chủ gắn tay "Trùng" → lọc theo nhãn ra đúng giao dịch; nhãn sai → 400
        perform(patch("/payments/" + payment.getId() + "/reconcile")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"flag\":\"duplicate\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reconcileFlag").value("duplicate"));
        perform(get("/payments").param("q", c.orderCode()).param("reconcileFlag", "duplicate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1));
        perform(patch("/payments/" + payment.getId() + "/reconcile")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"flag\":\"abc\"}"))
                .andExpect(status().isBadRequest());

        // Khách hàng không vào được trang Thanh toán nội bộ
        int customerStatus = perform(get("/payments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(registerCustomer("Khach To Mo"))))
                .andReturn().getResponse().getStatus();
        assertThat(customerStatus).isIn(401, 403);
    }

    // ================================================================= helpers

    /**
     * Gọi API như một request riêng: ghi xuống DB và xóa bộ nhớ đệm Hibernate trước/sau, để mỗi request đọc lại
     * dữ liệu mới (giống thực tế) dù tất cả nằm trong một transaction test sẽ rollback.
     */
    private ResultActions perform(RequestBuilder request) throws Exception {
        entityManager.flush();
        entityManager.clear();
        ResultActions result = mockMvc.perform(request);
        entityManager.flush();
        entityManager.clear();
        return result;
    }

    private record Checkout(long orderId, String orderCode, long payosOrderCode, BigDecimal amount) {}

    private Checkout checkout(String token) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("fulfillmentMethod", "PICKUP");
        body.put("requestedDate", LocalDate.now().plusDays(1).toString());
        body.put("timeWindow", "08:00-10:00");
        body.put("paymentMethod", "PAYOS");
        body.put("lines", List.of(Map.of("variantId", variant.getId(), "qtyKhay", 1)));

        String json = perform(post("/checkout")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .header("Idempotency-Key", "idem-payos-it-" + SEQ.getAndIncrement() + "-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode root = objectMapper.readTree(json);
        return new Checkout(
                root.get("order").get("id").asLong(),
                root.get("order").get("code").asText(),
                root.get("payment").get("payosOrderCode").asLong(),
                new BigDecimal(root.get("payment").get("amount").asText()));
    }

    private Map<String, Object> webhookData(long orderCode, BigDecimal amount, String reference) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderCode", orderCode);
        data.put("amount", amount.intValueExact());
        data.put("description", "CSIT SO" + orderCode);
        data.put("accountNumber", "0332619391");
        data.put("reference", reference);
        data.put("transactionDateTime", "2026-11-14 05:37:00");
        data.put("currency", "VND");
        data.put("paymentLinkId", "plink-it-" + orderCode);
        data.put("code", "00");
        data.put("desc", "success");
        data.put("counterAccountBankId", "");
        data.put("counterAccountBankName", "");
        data.put("counterAccountName", "");
        data.put("counterAccountNumber", "");
        data.put("virtualAccountName", "");
        data.put("virtualAccountNumber", "");
        return data;
    }

    private static String sign(Map<String, Object> data) {
        return PayosSignature.signDataObject(data, CHECKSUM);
    }

    private ResultActions postWebhook(Map<String, Object> data, String signature) throws Exception {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("code", "00");
        envelope.put("desc", "success");
        envelope.put("success", true);
        envelope.put("data", data);
        envelope.put("signature", signature);
        return perform(post("/payments/payos/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(envelope)));
    }

    private ResultActions postRefund(
            String token, long orderId, Long sourceId, String method, int amount, String reference, String note)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderId", orderId);
        body.put("sourcePaymentId", sourceId);
        body.put("method", method);
        body.put("amount", amount);
        body.put("reference", reference);
        body.put("note", note);
        return perform(post("/payments/refund")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    /** Lùi giờ giữ hàng quá 15 phút + ân hạn để job xử lý đơn này. */
    private void expireBeyondGrace(Checkout c) {
        SalesOrder order = order(c.orderId());
        long graceSeconds = appConfigService.paymentGraceMinutes() * 60L;
        order.setHoldExpiresAt(Instant.now().minusSeconds(graceSeconds + 60));
        salesOrderRepository.saveAndFlush(order);
    }

    private void runHoldExpireJob() throws Exception {
        perform(post("/sales/hold-expire").header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
                .andExpect(status().isOk());
    }

    private Payment payment(long payosOrderCode) {
        return paymentRepository.findWithOrderCustomerByPayosOrderCode(payosOrderCode).orElseThrow();
    }

    private SalesOrder order(long orderId) {
        return salesOrderRepository.findById(orderId).orElseThrow();
    }

    private List<String> eventTypes(long orderId) {
        return orderEventRepository.findByOrder_IdOrderByCreatedAtAscIdAsc(orderId).stream()
                .map(OrderEvent::getEventType)
                .toList();
    }

    private List<Notification> ownerNotifications(String type) {
        return notificationRepository.findAll().stream()
                .filter(n -> n.getUser() != null && owner.getId().equals(n.getUser().getId()))
                .filter(n -> type.equals(n.getType()))
                .toList();
    }

    /** Quản trị viên seed sẵn trong DB (quyền orders / debt / admin). */
    private String adminToken() {
        AppUser admin = userRepository.findByUsername("admin").orElseThrow();
        return tokenService.issue(admin).value();
    }

    /** Chủ cửa hàng riêng của test — để kiểm tra thông báo chắc chắn (rollback sau test). */
    private AppUser createOwner() {
        String suffix = SEQ.getAndIncrement() + UUID.randomUUID().toString().substring(0, 6);
        AppUser user = new AppUser();
        user.setUsername("owner_payos_it_" + suffix);
        user.setFullName("Chu IT " + suffix);
        user.setPhone("08" + String.format("%08d", Math.abs(suffix.hashCode()) % 100_000_000));
        user.setEmail("owner_" + suffix + "@test.vn");
        user.setPasswordHash(passwordEncoder.encode("Passw0rd!"));
        user.setUserType(UserType.STAFF);
        user.setActive(true);
        user.setRole(roleRepository.findByCode(DefaultRoles.OWNER).orElseThrow());
        return userRepository.saveAndFlush(user);
    }

    /** Tạo user customer + KH B2C rồi đăng nhập mật khẩu (không phụ thuộc Firebase). */
    private String registerCustomer(String fullName) {
        String suffix = SEQ.getAndIncrement() + UUID.randomUUID().toString().substring(0, 6);
        String username = "payos_it_" + suffix;
        String phone = "09" + String.format("%08d", 40_000_000 + (Math.abs(suffix.hashCode()) % 50_000_000));
        String password = "Passw0rd!";

        AppUser user = new AppUser();
        user.setUsername(username);
        user.setFullName(fullName);
        user.setPhone(phone);
        user.setEmail("payos_" + suffix + "@test.vn");
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setUserType(UserType.CUSTOMER);
        user.setActive(true);
        user.setRole(roleRepository.findByCode(DefaultRoles.CUSTOMER).orElseThrow());
        user = userRepository.save(user);

        Customer customer = new Customer();
        customer.setCode("KH-PIT-" + suffix);
        customer.setCustomerType(CustomerType.B2C);
        customer.setName(fullName);
        customer.setPhone(phone);
        customer.setCreditLimit(BigDecimal.ZERO);
        customer.setNetDays((short) 0);
        customer.setCurrentDebt(BigDecimal.ZERO);
        customer.setActive(true);
        customer.setAccountUser(user);
        customerRepository.saveAndFlush(customer);

        return tokenService.issue(user).value();
    }

    private void ensureAvailableStock(ProductVariant v, int qty) {
        int n = SEQ.getAndIncrement();
        Warehouse warehouse = warehouseRepository.findByCodeIgnoreCase("KHO-HP").orElseGet(() -> {
            Warehouse created = new Warehouse();
            created.setCode("KHO-HP-PIT-" + n);
            created.setName("Kho IT PayOS");
            created.setCapacity(500);
            created.setActive(true);
            return warehouseRepository.save(created);
        });
        Location location = locationRepository.findByWarehouse_IdOrderByCodeAsc(warehouse.getId()).stream()
                .findFirst()
                .orElseGet(() -> {
                    Location created = new Location();
                    created.setWarehouse(warehouse);
                    created.setCode("PIT-A");
                    created.setZone("Z");
                    created.setCapacity(100);
                    return locationRepository.save(created);
                });

        Lot lot = new Lot();
        lot.setLotCode("LOT-PAYOS-IT-" + n + "-" + UUID.randomUUID().toString().substring(0, 8));
        lot.setVariant(v);
        lot.setMfgDate(LocalDate.now().minusDays(1));
        lot.setExpDate(LocalDate.now().plusDays(40));
        lot.setReceivedAt(Instant.now());
        lot = lotRepository.save(lot);

        Stock stock = new Stock();
        stock.setVariant(v);
        stock.setLot(lot);
        stock.setLocation(location);
        stock.setQtyAvailable(qty);
        stock.setQtyHeld(0);
        stock.setQtyQuarantine(0);
        stock.setQtyExpired(0);
        stockRepository.saveAndFlush(stock);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
