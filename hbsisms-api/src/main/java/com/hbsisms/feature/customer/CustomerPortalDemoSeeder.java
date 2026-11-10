package com.hbsisms.feature.customer;

import com.hbsisms.feature.auth.AuthIdentity;
import com.hbsisms.feature.auth.AuthIdentityRepository;
import com.hbsisms.feature.role.DefaultRoles;
import com.hbsisms.feature.role.RoleRepository;
import com.hbsisms.feature.user.AppUser;
import com.hbsisms.feature.user.UserRepository;
import com.hbsisms.feature.user.UserType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inter 2 #26 — đảm bảo KH-001 (0912345678) có tài khoản portal customer + 2 địa chỉ Hải Phòng để demo.
 * Idempotent: chạy được trên DB đã seed sẵn.
 */
@Slf4j
@Component
@Order(61)
public class CustomerPortalDemoSeeder implements ApplicationRunner {

    static final String DEMO_PHONE = "0912345678";
    static final String DEMO_CUSTOMER_CODE = "KH-001";
    static final String DEMO_FIREBASE_UID = "uid-demo-kh001";

    private final CustomerRepository customerRepository;
    private final CustomerAddressRepository addressRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AuthIdentityRepository authIdentityRepository;

    public CustomerPortalDemoSeeder(
            CustomerRepository customerRepository,
            CustomerAddressRepository addressRepository,
            UserRepository userRepository,
            RoleRepository roleRepository,
            AuthIdentityRepository authIdentityRepository) {
        this.customerRepository = customerRepository;
        this.addressRepository = addressRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.authIdentityRepository = authIdentityRepository;
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

        log.info("Portal demo ready: customer {} phone {} userId={}", customer.getCode(), DEMO_PHONE, user.getId());
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
