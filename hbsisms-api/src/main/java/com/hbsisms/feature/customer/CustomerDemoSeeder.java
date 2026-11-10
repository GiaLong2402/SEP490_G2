package com.hbsisms.feature.customer;

import java.math.BigDecimal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds demo customers (Hải Phòng seafood) when the table is empty so list/detail UI can be tested
 * before full Customer CRUD lands.
 */
@Slf4j
@Component
@Order(60)
public class CustomerDemoSeeder implements ApplicationRunner {

    private final CustomerRepository customerRepository;
    private final CustomerAddressRepository addressRepository;

    public CustomerDemoSeeder(CustomerRepository customerRepository, CustomerAddressRepository addressRepository) {
        this.customerRepository = customerRepository;
        this.addressRepository = addressRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (customerRepository.count() > 0) {
            return;
        }

        Customer an = saveB2c("KH-001", "Nguyễn Văn An", "0912345678", "Khách lẻ Cát Bà", true);
        saveB2c("KH-002", "Trần Thị Bình", "0987654321", "Khách lẻ Đồ Sơn", true);
        Customer bienDong = saveB2b(
                "KH-B001",
                "Nhà hàng Biển Đông",
                "0933111222",
                "0200123456",
                "Nguyễn Thị Hoa",
                "0933111223",
                new BigDecimal("20000000"),
                (short) 15,
                new BigDecimal("3500000"),
                "Công nợ đang dùng — demo Meter hạn mức");
        saveB2b(
                "KH-B002",
                "KT Hải Sản Minh Phát",
                "0909555666",
                "0200987654",
                "Phạm Văn Minh",
                "0909555667",
                new BigDecimal("50000000"),
                (short) 30,
                BigDecimal.ZERO,
                "B2B sạch hạn mức");
        saveB2c("KH-003", "Lê Văn Cường", "0977000111", "Khách ngừng — demo chip Ngừng", false);

        // Địa chỉ cho B2B Biển Đông — tab Địa chỉ trên detail không trống
        saveAddress(
                bienDong,
                AddressKind.SHIPPING,
                "Nguyễn Thị Hoa",
                "0933111222",
                "12 Lạch Tray",
                "Máy Tơ",
                "Ngô Quyền",
                "Hải Phòng",
                new BigDecimal("3.50"),
                true);
        saveAddress(
                bienDong,
                AddressKind.BILLING,
                "Kế toán Biển Đông",
                "0933111223",
                "12 Lạch Tray",
                "Máy Tơ",
                "Ngô Quyền",
                "Hải Phòng",
                new BigDecimal("3.50"),
                false);

        // Địa chỉ demo portal KH-001 (Inter 2 #26) — trong bán kính giao 8km
        saveAddress(
                an,
                AddressKind.SHIPPING,
                "Nguyễn Văn An",
                "0912345678",
                "45 đường Cát Bà",
                null,
                "Cát Hải",
                "Hải Phòng",
                new BigDecimal("5.00"),
                true);
        saveAddress(
                an,
                AddressKind.SHIPPING,
                "Nguyễn Văn An",
                "0912345678",
                "12 Lạch Tray",
                "Máy Tơ",
                "Ngô Quyền",
                "Hải Phòng",
                new BigDecimal("3.50"),
                false);

        log.info("Seeded demo customers (KH-001..KH-003, KH-B001..KH-B002)");
    }

    private Customer saveB2c(String code, String name, String phone, String note, boolean active) {
        Customer c = new Customer();
        c.setCode(code);
        c.setCustomerType(CustomerType.B2C);
        c.setName(name);
        c.setPhone(phone);
        c.setCreditLimit(BigDecimal.ZERO);
        c.setNetDays((short) 0);
        c.setCurrentDebt(BigDecimal.ZERO);
        c.setActive(active);
        c.setNote(note);
        return customerRepository.save(c);
    }

    private Customer saveB2b(
            String code,
            String name,
            String phone,
            String taxCode,
            String contactName,
            String contactPhone,
            BigDecimal creditLimit,
            short netDays,
            BigDecimal currentDebt,
            String note) {
        Customer c = new Customer();
        c.setCode(code);
        c.setCustomerType(CustomerType.B2B);
        c.setName(name);
        c.setPhone(phone);
        c.setTaxCode(taxCode);
        c.setContactName(contactName);
        c.setContactPhone(contactPhone);
        c.setCreditLimit(creditLimit);
        c.setNetDays(netDays);
        c.setCurrentDebt(currentDebt);
        c.setActive(true);
        c.setNote(note);
        return customerRepository.save(c);
    }

    private void saveAddress(
            Customer customer,
            AddressKind kind,
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
        a.setKind(kind);
        a.setRecipientName(recipientName);
        a.setPhone(phone);
        a.setLine1(line1);
        a.setWard(ward);
        a.setDistrict(district);
        a.setProvince(province);
        a.setDistanceKm(distanceKm);
        a.setInDeliveryRadius(distanceKm != null && distanceKm.compareTo(new BigDecimal("8")) <= 0);
        a.setDefaultAddress(isDefault);
        addressRepository.save(a);
    }
}
