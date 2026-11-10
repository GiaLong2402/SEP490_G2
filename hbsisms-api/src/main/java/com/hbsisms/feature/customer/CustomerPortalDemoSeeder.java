package com.hbsisms.feature.customer;

import com.hbsisms.feature.auth.AuthIdentity;
import com.hbsisms.feature.auth.AuthIdentityRepository;
import com.hbsisms.feature.catalog.ProductVariant;
import com.hbsisms.feature.catalog.ProductVariantRepository;
import com.hbsisms.feature.payment.PayDirection;
import com.hbsisms.feature.payment.PayTxnStatus;
import com.hbsisms.feature.payment.Payment;
import com.hbsisms.feature.payment.PaymentMethod;
import com.hbsisms.feature.payment.PaymentRepository;
import com.hbsisms.feature.payment.PaymentStatus;
import com.hbsisms.feature.role.DefaultRoles;
import com.hbsisms.feature.role.RoleRepository;
import com.hbsisms.feature.sales.AllocationStatus;
import com.hbsisms.feature.sales.Delivery;
import com.hbsisms.feature.sales.DeliveryRepository;
import com.hbsisms.feature.sales.DeliveryState;
import com.hbsisms.feature.sales.FulfillmentMethod;
import com.hbsisms.feature.sales.OrderEvent;
import com.hbsisms.feature.sales.OrderEventRepository;
import com.hbsisms.feature.sales.OrderState;
import com.hbsisms.feature.sales.OrderType;
import com.hbsisms.feature.sales.PriceBasis;
import com.hbsisms.feature.sales.RequestStatus;
import com.hbsisms.feature.sales.SaleChannel;
import com.hbsisms.feature.sales.SalesOrder;
import com.hbsisms.feature.sales.SalesOrderLine;
import com.hbsisms.feature.sales.SalesOrderRepository;
import com.hbsisms.feature.sales.SalesRequest;
import com.hbsisms.feature.sales.SalesRequestRepository;
import com.hbsisms.feature.sales.SellUnit;
import com.hbsisms.feature.user.AppUser;
import com.hbsisms.feature.user.UserRepository;
import com.hbsisms.feature.user.UserType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inter 2 portal — KH-001 + địa chỉ; seed yêu cầu ({@code YCM-DEMO-*}) và đơn chờ TT ({@code DH-DEMO-*}).
 * Idempotent: chạy được trên DB đã seed sẵn.
 */
@Slf4j
@Component
@Order(61)
public class CustomerPortalDemoSeeder implements ApplicationRunner {

    static final String DEMO_PHONE = "0912345678";
    static final String DEMO_CUSTOMER_CODE = "KH-001";
    static final String DEMO_FIREBASE_UID = "uid-demo-kh001";
    static final String DEMO_REQUEST_CODE = "YCM-DEMO-001";
    static final String DEMO_ORDER_CODE = "DH-DEMO-001";
    static final String DEMO_ORDER_SKU = "TOM-SU-500";

    private final CustomerRepository customerRepository;
    private final CustomerAddressRepository addressRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AuthIdentityRepository authIdentityRepository;
    private final SalesRequestRepository salesRequestRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final DeliveryRepository deliveryRepository;
    private final PaymentRepository paymentRepository;
    private final OrderEventRepository orderEventRepository;
    private final ProductVariantRepository productVariantRepository;

    public CustomerPortalDemoSeeder(
            CustomerRepository customerRepository,
            CustomerAddressRepository addressRepository,
            UserRepository userRepository,
            RoleRepository roleRepository,
            AuthIdentityRepository authIdentityRepository,
            SalesRequestRepository salesRequestRepository,
            SalesOrderRepository salesOrderRepository,
            DeliveryRepository deliveryRepository,
            PaymentRepository paymentRepository,
            OrderEventRepository orderEventRepository,
            ProductVariantRepository productVariantRepository) {
        this.customerRepository = customerRepository;
        this.addressRepository = addressRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.authIdentityRepository = authIdentityRepository;
        this.salesRequestRepository = salesRequestRepository;
        this.salesOrderRepository = salesOrderRepository;
        this.deliveryRepository = deliveryRepository;
        this.paymentRepository = paymentRepository;
        this.orderEventRepository = orderEventRepository;
        this.productVariantRepository = productVariantRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Customer customer = customerRepository
                .findByCodeIgnoreCase(DEMO_CUSTOMER_CODE)
                .or(() -> customerRepository.findFirstByPhoneOrderByIdAsc(DEMO_PHONE))
                .orElse(null);
        if (customer == null) {
            log.info("Skip portal demo seed: customer {} not found", DEMO_CUSTOMER_CODE);
            return;
        }

        AppUser user = ensurePortalUser(customer);
        if (customer.getAccountUser() == null || !customer.getAccountUser().getId().equals(user.getId())) {
            customer.setAccountUser(user);
            customerRepository.save(customer);
        }
        ensureAuthIdentity(user);
        ensureAddresses(customer);
        ensureDemoSalesRequest(customer, DEMO_REQUEST_CODE);
        ensureDemoOrder(customer, DEMO_ORDER_CODE);
        // Account portal khác (vd. Nguyễn Văn Mười đăng ký OTP) cũng có yêu cầu + đơn demo.
        for (Customer linked : customerRepository.findAll()) {
            if (linked.getAccountUser() == null) {
                continue;
            }
            ensureDemoSalesRequest(linked, "YCM-DEMO-C" + linked.getId());
            ensureDemoOrder(linked, "DH-DEMO-C" + linked.getId());
        }

        log.info("Portal demo ready: customer {} phone {} userId={}", customer.getCode(), DEMO_PHONE, user.getId());
    }

    private void ensureDemoOrder(Customer customer, String code) {
        if (salesOrderRepository.findByCode(code).isPresent()) {
            return;
        }
        ProductVariant variant = productVariantRepository.findBySkuIgnoreCase(DEMO_ORDER_SKU).orElse(null);
        if (variant == null) {
            log.warn("Skip order seed {}: variant {} missing", code, DEMO_ORDER_SKU);
            return;
        }

        CustomerAddress ship = addressRepository
                .findByCustomerIdOrderByDefaultAddressDescIdAsc(customer.getId())
                .stream()
                .findFirst()
                .orElse(null);

        Instant now = Instant.now();
        Instant holdExpires = now.plusSeconds(15 * 60L);
        int qty = 2;
        BigDecimal unitPrice = variant.getListPrice() != null ? variant.getListPrice() : new BigDecimal("120000");
        BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(qty));
        BigDecimal shipping = new BigDecimal("20000");
        BigDecimal total = lineTotal.add(shipping);

        SalesOrder order = new SalesOrder();
        order.setCode(code);
        order.setCustomer(customer);
        order.setOrderType(OrderType.ONLINE_B2C);
        order.setChannel(SaleChannel.WEB);
        order.setPaymentMethod(PaymentMethod.PAYOS);
        order.setPaymentTermDays((short) 0);
        order.setPaymentStatus(PaymentStatus.WAITING);
        order.setAllocationStatus(AllocationStatus.NONE);
        order.setFulfillmentMethod(FulfillmentMethod.SHOP_DELIVERY);
        order.setShipAddress(ship);
        order.setState(OrderState.PENDING);
        order.setSubtotal(lineTotal);
        order.setDiscountTotal(BigDecimal.ZERO);
        order.setShippingFee(shipping);
        order.setTotal(total);
        order.setOverLimit(false);
        order.setOverLimitAmount(BigDecimal.ZERO);
        order.setPlacedBy(customer.getAccountUser());
        order.setPlacedAt(now);
        order.setHoldExpiresAt(holdExpires);
        order.setNote("Demo portal — chờ thanh toán PayOS stub");

        SalesOrderLine line = new SalesOrderLine();
        line.setVariant(variant);
        line.setUnit(SellUnit.PACK);
        line.setQty(qty);
        line.setQtyKhay(qty);
        line.setUnitPrice(unitPrice);
        line.setDiscountPct(BigDecimal.ZERO);
        line.setLineTotal(lineTotal);
        line.setPriceBasis(PriceBasis.LIST);
        line.setOutstandingQty(0);
        order.addLine(line);
        salesOrderRepository.save(order);

        Delivery delivery = new Delivery();
        delivery.setCode("DL-" + code);
        delivery.setOrder(order);
        delivery.setLegLabel("Trọn đơn");
        delivery.setScheduledDate(LocalDate.now().plusDays(1));
        delivery.setTimeWindow("10:00-12:00");
        delivery.setFulfillmentMethod(FulfillmentMethod.SHOP_DELIVERY);
        delivery.setAddress(ship);
        if (ship != null) {
            delivery.setInRadius(ship.isInDeliveryRadius());
            delivery.setDistanceKm(ship.getDistanceKm());
        } else {
            delivery.setInRadius(true);
        }
        delivery.setState(DeliveryState.READY);
        delivery.setItems(List.of());
        deliveryRepository.save(delivery);

        Payment payment = new Payment();
        payment.setCode("PAY-" + code);
        payment.setOrder(order);
        payment.setCustomer(customer);
        payment.setMethod(PaymentMethod.PAYOS);
        payment.setDirection(PayDirection.IN);
        payment.setAmount(total);
        payment.setStatus(PayTxnStatus.PENDING);
        payment.setGateway("payos");
        long payosCode = paymentRepository.maxPayosOrderCode() + 1;
        payment.setPayosOrderCode(payosCode);
        payment.setCheckoutUrl("http://localhost:5173/thanh-toan/ket-qua?orderCode=" + payosCode);
        payment.setPaidAt(now);
        payment.setNote("Stub PayOS — demo /me/orders");
        paymentRepository.save(payment);

        OrderEvent event = new OrderEvent();
        event.setOrder(order);
        event.setEventType("created");
        event.setDescription("Tạo đơn demo — chờ thanh toán");
        event.setCreatedBy(customer.getAccountUser());
        orderEventRepository.save(event);

        log.info("Seeded portal order {} for customer {}", code, customer.getCode());
    }

    private void ensureDemoSalesRequest(Customer customer, String code) {
        if (salesRequestRepository.findByCode(code).isPresent()) {
            return;
        }
        SalesRequest row = new SalesRequest();
        row.setCode(code);
        row.setCustomer(customer);
        row.setCustomerName(customer.getName());
        row.setChannel(SaleChannel.WEB);
        row.setSummary("Chả Cá · Khay 500 g × 3 khay (còn 1) — demo vượt tồn");
        row.setStatus(RequestStatus.NEW);
        row.setReceivedAt(Instant.now());
        row.setNeededDate(LocalDate.now().plusDays(2));
        row.setRequestedQty(3);
        row.setAvailableQty(1);
        salesRequestRepository.save(row);
        log.info("Seeded portal sales request {} for customer {}", code, customer.getCode());
    }

    private AppUser ensurePortalUser(Customer customer) {
        return userRepository.findByPhone(DEMO_PHONE).orElseGet(() -> {
            AppUser user = new AppUser();
            user.setFullName(customer.getName() != null ? customer.getName() : "Nguyễn Văn An");
            user.setPhone(DEMO_PHONE);
            user.setEmail("an.nguyen@example.com");
            user.setPhoneVerifiedAt(Instant.now());
            user.setUserType(UserType.CUSTOMER);
            user.setActive(true);
            user.setRole(roleRepository
                    .findByCode(DefaultRoles.CUSTOMER)
                    .orElseThrow(() -> new IllegalStateException("Missing role customer")));
            return userRepository.save(user);
        });
    }

    private void ensureAuthIdentity(AppUser user) {
        if (authIdentityRepository.existsByUser_IdAndProvider(user.getId(), AuthIdentity.PROVIDER_FIREBASE)) {
            return;
        }
        if (authIdentityRepository
                .findByProviderAndProviderUid(AuthIdentity.PROVIDER_FIREBASE, DEMO_FIREBASE_UID)
                .isPresent()) {
            return;
        }
        AuthIdentity identity = new AuthIdentity();
        identity.setUser(user);
        identity.setProvider(AuthIdentity.PROVIDER_FIREBASE);
        identity.setProviderUid(DEMO_FIREBASE_UID);
        identity.setSignInMethod("phone");
        identity.setPhone(DEMO_PHONE);
        authIdentityRepository.save(identity);
    }

    private void ensureAddresses(Customer customer) {
        List<CustomerAddress> existing =
                addressRepository.findByCustomerIdOrderByDefaultAddressDescIdAsc(customer.getId());
        if (existing.isEmpty()) {
            saveAddress(
                    customer,
                    "Nguyễn Văn An",
                    DEMO_PHONE,
                    "45 đường Cát Bà",
                    null,
                    "Cát Hải",
                    "Hải Phòng",
                    new BigDecimal("5.00"),
                    true);
            saveAddress(
                    customer,
                    "Nguyễn Văn An",
                    DEMO_PHONE,
                    "12 Lạch Tray",
                    "Máy Tơ",
                    "Ngô Quyền",
                    "Hải Phòng",
                    new BigDecimal("3.50"),
                    false);
            return;
        }

        // DB cũ: 1 địa chỉ Cát Bà distance 28 → chỉnh trong bán kính + thêm địa chỉ 2 nếu thiếu
        CustomerAddress first = existing.get(0);
        if ("45 đường Cát Bà".equals(first.getLine1())) {
            first.setDistanceKm(new BigDecimal("5.00"));
            first.setInDeliveryRadius(true);
            first.setDefaultAddress(true);
            addressRepository.save(first);
        }
        boolean hasLachTray = existing.stream().anyMatch(a -> "12 Lạch Tray".equals(a.getLine1()));
        if (!hasLachTray) {
            addressRepository.clearDefaultForCustomer(customer.getId());
            first.setDefaultAddress(true);
            addressRepository.save(first);
            saveAddress(
                    customer,
                    "Nguyễn Văn An",
                    DEMO_PHONE,
                    "12 Lạch Tray",
                    "Máy Tơ",
                    "Ngô Quyền",
                    "Hải Phòng",
                    new BigDecimal("3.50"),
                    false);
        }
    }

    private void saveAddress(
            Customer customer,
            String recipientName,
            String phone,
            String line1,
            String ward,
            String district,
            String province,
            BigDecimal distanceKm,
            boolean isDefault) {
        CustomerAddress a = new CustomerAddress();
        a.setCustomer(customer);
        a.setKind(AddressKind.SHIPPING);
        a.setRecipientName(recipientName);
        a.setPhone(phone);
        a.setLine1(line1);
        a.setWard(ward);
        a.setDistrict(district);
        a.setProvince(province);
        a.setDistanceKm(distanceKm);
        a.setInDeliveryRadius(distanceKm.compareTo(new BigDecimal("8")) <= 0);
        a.setDefaultAddress(isDefault);
        addressRepository.save(a);
    }
}
