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
