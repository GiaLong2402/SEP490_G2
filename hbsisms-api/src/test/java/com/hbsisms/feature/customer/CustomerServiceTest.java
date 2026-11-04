package com.hbsisms.feature.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.common.web.PageResponse;
import com.hbsisms.feature.approval.ApprovalTaskRepository;
import com.hbsisms.feature.catalog.CustomerPriceRepository;
import com.hbsisms.feature.customer.dto.CustomerDetailResponse;
import com.hbsisms.feature.customer.dto.CustomerListItemResponse;
import com.hbsisms.feature.customer.dto.CustomerRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private CustomerAddressRepository addressRepository;
    @Mock
    private CustomerPriceRepository customerPriceRepository;
    @Mock
    private ApprovalTaskRepository approvalTaskRepository;

    private CustomerService customerService;

    @BeforeEach
    void setUp() {
        customerService = new CustomerService(
                customerRepository, addressRepository, customerPriceRepository, approvalTaskRepository);
    }

    @Test
    void searchMapsPageAndOrderCountZero() {
        Customer customer = sample("KH-001", CustomerType.B2C, "Nguyễn Văn An");
        when(customerRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(invocation -> {
                    Pageable pageable = invocation.getArgument(1);
                    return new PageImpl<>(List.of(customer), pageable, 1);
                });

        PageResponse<CustomerListItemResponse> page = customerService.search("An", CustomerType.B2C, true, 1, 15);

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).code()).isEqualTo("KH-001");
        assertThat(page.items().get(0).customerType()).isEqualTo(CustomerType.B2C);
        assertThat(page.items().get(0).orderCount()).isZero();
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.pageSize()).isEqualTo(15);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(customerRepository).findAll(any(Specification.class), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("name")).isNotNull();
    }

    @Test
    void getDetailReturnsAddressesAndEmptyRecentOrders() {
        Customer customer = sample("KH-B001", CustomerType.B2B, "Nhà hàng Biển Đông");
        ReflectionTestUtils.setField(customer, "id", 10L);
        when(customerRepository.findWithAccountById(10L)).thenReturn(Optional.of(customer));
        when(addressRepository.findByCustomerIdOrderByDefaultAddressDescIdAsc(10L)).thenReturn(List.of());
        when(customerPriceRepository.findByCustomerIdOrderByIdAsc(10L)).thenReturn(List.of());

        CustomerDetailResponse detail = customerService.getDetail(10L);

        assertThat(detail.code()).isEqualTo("KH-B001");
        assertThat(detail.customerType()).isEqualTo(CustomerType.B2B);
        assertThat(detail.addresses()).isEmpty();
        assertThat(detail.prices()).isEmpty();
        assertThat(detail.recentOrders()).isEmpty();
    }

    @Test
    void getDetailNotFound() {
        when(customerRepository.findWithAccountById(eq(99L))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.getDetail(99L))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void createB2cAssignsCodeAndZeroDebt() {
        when(customerRepository.findCodesByPrefix("KH-")).thenReturn(List.of("KH-001"));
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> {
            Customer c = invocation.getArgument(0);
            ReflectionTestUtils.setField(c, "id", 42L);
            return c;
        });
        when(addressRepository.findByCustomerIdOrderByDefaultAddressDescIdAsc(42L)).thenReturn(List.of());
        when(customerPriceRepository.findByCustomerIdOrderByIdAsc(42L)).thenReturn(List.of());

        CustomerDetailResponse detail = customerService.create(b2cRequest("Phạm Thị Dung", "0912000111", true));

        ArgumentCaptor<Customer> captor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(captor.capture());
        assertThat(captor.getValue().getCode()).isEqualTo("KH-002");
        assertThat(captor.getValue().getCurrentDebt()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(captor.getValue().getCustomerType()).isEqualTo(CustomerType.B2C);
        assertThat(detail.id()).isEqualTo(42L);
        assertThat(detail.code()).isEqualTo("KH-002");
    }

    @Test
    void createB2bKeepsCreditTerms() {
        when(customerRepository.findCodesByPrefix("KH-B")).thenReturn(List.of());
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> {
            Customer c = invocation.getArgument(0);
            ReflectionTestUtils.setField(c, "id", 7L);
            return c;
        });
        when(addressRepository.findByCustomerIdOrderByDefaultAddressDescIdAsc(7L)).thenReturn(List.of());
        when(customerPriceRepository.findByCustomerIdOrderByIdAsc(7L)).thenReturn(List.of());

        CustomerDetailResponse detail = customerService.create(b2bRequest(
                "Nhà hàng Cảng Xanh",
                "0933000222",
                "0200555666",
                new BigDecimal("10000000"),
                (short) 15));

        ArgumentCaptor<Customer> captor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(captor.capture());
        assertThat(captor.getValue().getCode()).isEqualTo("KH-B001");
        assertThat(captor.getValue().getCreditLimit()).isEqualByComparingTo("10000000");
        assertThat(captor.getValue().getNetDays()).isEqualTo((short) 15);
        assertThat(captor.getValue().getTaxCode()).isEqualTo("0200555666");
        assertThat(detail.code()).isEqualTo("KH-B001");
    }

    @Test
    void createB2cRejectsCreditLimit() {
        assertThatThrownBy(() -> customerService.create(new CustomerRequest(
                        CustomerType.B2C,
                        "Test",
                        null,
                        "0912000111",
                        null,
                        null,
                        null,
                        new BigDecimal("1000"),
                        (short) 0,
                        true,
                        null)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void updateB2bToB2cWithDebtConflicts() {
        Customer existing = sample("KH-B001", CustomerType.B2B, "Nợ");
        ReflectionTestUtils.setField(existing, "id", 5L);
        existing.setCurrentDebt(new BigDecimal("3500000"));
        when(customerRepository.findWithAccountById(5L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> customerService.update(5L, b2cRequest("Nợ", "0912000111", true)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void createRejectsInvalidPhone() {
        assertThatThrownBy(() -> customerService.create(b2cRequest("A", "123", true)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void createB2bRejectsInvalidTaxCode() {
        assertThatThrownBy(() -> customerService.create(b2bRequest(
                        "DN", "0933000222", "ABC", BigDecimal.ZERO, (short) 0)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void setActiveLocksCustomer() {
        Customer existing = sample("KH-001", CustomerType.B2C, "An");
        ReflectionTestUtils.setField(existing, "id", 3L);
        when(customerRepository.findWithAccountById(3L)).thenReturn(Optional.of(existing));
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));
        when(addressRepository.findByCustomerIdOrderByDefaultAddressDescIdAsc(3L)).thenReturn(List.of());
        when(customerPriceRepository.findByCustomerIdOrderByIdAsc(3L)).thenReturn(List.of());

        CustomerDetailResponse detail = customerService.setActive(3L, false);

        assertThat(detail.isActive()).isFalse();
        ArgumentCaptor<Customer> captor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(captor.capture());
        assertThat(captor.getValue().isActive()).isFalse();
    }

    @Test
    void setActiveUnlocksCustomer() {
        Customer existing = sample("KH-001", CustomerType.B2C, "An");
        ReflectionTestUtils.setField(existing, "id", 3L);
        existing.setActive(false);
        when(customerRepository.findWithAccountById(3L)).thenReturn(Optional.of(existing));
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));
        when(addressRepository.findByCustomerIdOrderByDefaultAddressDescIdAsc(3L)).thenReturn(List.of());
        when(customerPriceRepository.findByCustomerIdOrderByIdAsc(3L)).thenReturn(List.of());

        CustomerDetailResponse detail = customerService.setActive(3L, true);

        assertThat(detail.isActive()).isTrue();
    }

    @Test
    void setActiveNotFound() {
        when(customerRepository.findWithAccountById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.setActive(99L, false))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    private static CustomerRequest b2cRequest(String name, String phone, boolean active) {
        return new CustomerRequest(
                CustomerType.B2C, name, null, phone, null, null, null, BigDecimal.ZERO, (short) 0, active, null);
    }

    private static CustomerRequest b2bRequest(
            String name, String phone, String taxCode, BigDecimal creditLimit, short netDays) {
        return new CustomerRequest(
                CustomerType.B2B, name, null, phone, taxCode, "Anh Tuấn", "0933000223", creditLimit, netDays, true, null);
    }

    private static Customer sample(String code, CustomerType type, String name) {
        Customer c = new Customer();
        ReflectionTestUtils.setField(c, "id", 1L);
        c.setCode(code);
        c.setCustomerType(type);
        c.setName(name);
        c.setPhone("0912345678");
        c.setCreditLimit(BigDecimal.ZERO);
        c.setNetDays((short) 0);
        c.setCurrentDebt(BigDecimal.ZERO);
        c.setActive(true);
        return c;
    }
}
