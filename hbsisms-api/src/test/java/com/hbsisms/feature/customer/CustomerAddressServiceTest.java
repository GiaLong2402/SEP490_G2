package com.hbsisms.feature.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.customer.dto.AddressRequest;
import com.hbsisms.feature.customer.dto.AddressResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CustomerAddressServiceTest {

    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private CustomerAddressRepository addressRepository;

    private CustomerAddressService service;
    private Customer customer;

    @BeforeEach
    void setUp() {
        service = new CustomerAddressService(customerRepository, addressRepository);
        customer = new Customer();
        ReflectionTestUtils.setField(customer, "id", 10L);
        customer.setCode("KH-B001");
        customer.setName("Nhà hàng Biển Đông");
        customer.setCustomerType(CustomerType.B2B);
    }

    @Test
    void list_returnsAddressesOrderedByRepository() {
        when(customerRepository.findById(10L)).thenReturn(Optional.of(customer));
        CustomerAddress defaultAddr = address(1L, "Địa chỉ mặc định", true);
        CustomerAddress other = address(2L, "Địa chỉ phụ", false);
        when(addressRepository.findByCustomerIdOrderByDefaultAddressDescIdAsc(10L))
                .thenReturn(List.of(defaultAddr, other));

        List<AddressResponse> rows = service.list(10L);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).isDefault()).isTrue();
        assertThat(rows.get(0).line1()).isEqualTo("Địa chỉ mặc định");
        assertThat(rows.get(1).isDefault()).isFalse();
    }

    @Test
    void list_rejectsUnknownCustomer() {
        when(customerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.list(99L))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void create_clearsPreviousDefaultWhenMarkingDefault() {
        when(customerRepository.findById(10L)).thenReturn(Optional.of(customer));
        when(addressRepository.save(any(CustomerAddress.class))).thenAnswer(inv -> {
            CustomerAddress a = inv.getArgument(0);
            ReflectionTestUtils.setField(a, "id", 5L);
            return a;
        });

        AddressResponse response = service.create(10L, request(true, new BigDecimal("3.5")));

        verify(addressRepository).clearDefaultForCustomer(10L);
        ArgumentCaptor<CustomerAddress> captor = ArgumentCaptor.forClass(CustomerAddress.class);
        verify(addressRepository).save(captor.capture());
        assertThat(captor.getValue().isDefaultAddress()).isTrue();
        assertThat(captor.getValue().isInDeliveryRadius()).isTrue();
        assertThat(response.id()).isEqualTo(5L);
        assertThat(response.inDeliveryRadius()).isTrue();
    }

    @Test
    void create_doesNotClearDefaultWhenNotDefault() {
        when(customerRepository.findById(10L)).thenReturn(Optional.of(customer));
        when(addressRepository.save(any(CustomerAddress.class))).thenAnswer(inv -> {
            CustomerAddress a = inv.getArgument(0);
            ReflectionTestUtils.setField(a, "id", 6L);
            return a;
        });

        service.create(10L, request(false, new BigDecimal("12")));

        verify(addressRepository, never()).clearDefaultForCustomer(any());
        ArgumentCaptor<CustomerAddress> captor = ArgumentCaptor.forClass(CustomerAddress.class);
        verify(addressRepository).save(captor.capture());
        assertThat(captor.getValue().isDefaultAddress()).isFalse();
        assertThat(captor.getValue().isInDeliveryRadius()).isFalse();
    }

    @Test
    void create_defaultsKindToShippingWhenNull() {
        when(customerRepository.findById(10L)).thenReturn(Optional.of(customer));
        when(addressRepository.save(any(CustomerAddress.class))).thenAnswer(inv -> {
            CustomerAddress a = inv.getArgument(0);
            ReflectionTestUtils.setField(a, "id", 7L);
            return a;
        });

        AddressRequest req = new AddressRequest(
                null, "An", "0901", "  12 Lạch Tray  ", "Máy Chai", "Ngô Quyền", "Hải Phòng", null, false);

        service.create(10L, req);

        ArgumentCaptor<CustomerAddress> captor = ArgumentCaptor.forClass(CustomerAddress.class);
        verify(addressRepository).save(captor.capture());
        assertThat(captor.getValue().getKind()).isEqualTo(AddressKind.SHIPPING);
        assertThat(captor.getValue().getLine1()).isEqualTo("12 Lạch Tray");
    }

    @Test
    void update_clearsDefaultOnlyWhenSwitchingToDefault() {
        when(customerRepository.findById(10L)).thenReturn(Optional.of(customer));
        CustomerAddress existing = address(3L, "Cũ", false);
        when(addressRepository.findByIdAndCustomerId(3L, 10L)).thenReturn(Optional.of(existing));
        when(addressRepository.save(existing)).thenReturn(existing);

        service.update(10L, 3L, request(true, new BigDecimal("2")));

        verify(addressRepository).clearDefaultForCustomer(10L);
        assertThat(existing.isDefaultAddress()).isTrue();
        assertThat(existing.isInDeliveryRadius()).isTrue();
    }

    @Test
    void update_skipsClearDefaultWhenAlreadyDefault() {
        when(customerRepository.findById(10L)).thenReturn(Optional.of(customer));
        CustomerAddress existing = address(3L, "Cũ", true);
        when(addressRepository.findByIdAndCustomerId(3L, 10L)).thenReturn(Optional.of(existing));
        when(addressRepository.save(existing)).thenReturn(existing);

        service.update(10L, 3L, request(true, new BigDecimal("1")));

        verify(addressRepository, never()).clearDefaultForCustomer(any());
    }

    @Test
    void update_rejectsUnknownAddress() {
        when(customerRepository.findById(10L)).thenReturn(Optional.of(customer));
        when(addressRepository.findByIdAndCustomerId(99L, 10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(10L, 99L, request(false, null)))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
        verify(addressRepository, never()).save(any());
    }

    @Test
    void delete_removesAddress() {
        when(customerRepository.findById(10L)).thenReturn(Optional.of(customer));
        CustomerAddress existing = address(3L, "Xóa", false);
        when(addressRepository.findByIdAndCustomerId(3L, 10L)).thenReturn(Optional.of(existing));

        service.delete(10L, 3L);

        verify(addressRepository).delete(existing);
    }

    @Test
    void delete_rejectsUnknownAddress() {
        when(customerRepository.findById(10L)).thenReturn(Optional.of(customer));
        when(addressRepository.findByIdAndCustomerId(99L, 10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(10L, 99L))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
        verify(addressRepository, never()).delete(any());
    }

    @Test
    void isInDeliveryRadius_boundaryAtEightKm() {
        assertThat(CustomerAddressService.isInDeliveryRadius(BigDecimal.ZERO)).isTrue();
        assertThat(CustomerAddressService.isInDeliveryRadius(new BigDecimal("8"))).isTrue();
        assertThat(CustomerAddressService.isInDeliveryRadius(new BigDecimal("8.01"))).isFalse();
        assertThat(CustomerAddressService.isInDeliveryRadius(null)).isFalse();
    }

    @Test
    void list_returnsEmptyWhenCustomerHasNoAddresses() {
        when(customerRepository.findById(10L)).thenReturn(Optional.of(customer));
        when(addressRepository.findByCustomerIdOrderByDefaultAddressDescIdAsc(10L)).thenReturn(List.of());

        assertThat(service.list(10L)).isEmpty();
    }

    @Test
    void create_rejectsUnknownCustomer() {
        when(customerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(99L, request(false, null)))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
        verify(addressRepository, never()).save(any());
    }

    @Test
    void update_rejectsUnknownCustomer() {
        when(customerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(99L, 1L, request(false, null)))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
        verify(addressRepository, never()).save(any());
    }

    @Test
    void delete_rejectsUnknownCustomer() {
        when(customerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(99L, 1L))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
        verify(addressRepository, never()).delete(any());
    }

    @Test
    void create_persistsBillingKindAndTrimsBlankOptionalFields() {
        when(customerRepository.findById(10L)).thenReturn(Optional.of(customer));
        when(addressRepository.save(any(CustomerAddress.class))).thenAnswer(inv -> {
            CustomerAddress a = inv.getArgument(0);
            ReflectionTestUtils.setField(a, "id", 8L);
            return a;
        });

        AddressRequest req = new AddressRequest(
                AddressKind.BILLING,
                "  ",
                "   ",
                "Số 1",
                "  ",
                null,
                "Hải Phòng",
                new BigDecimal("0"),
                false);

        service.create(10L, req);

        ArgumentCaptor<CustomerAddress> captor = ArgumentCaptor.forClass(CustomerAddress.class);
        verify(addressRepository).save(captor.capture());
        CustomerAddress saved = captor.getValue();
        assertThat(saved.getKind()).isEqualTo(AddressKind.BILLING);
        assertThat(saved.getRecipientName()).isNull();
        assertThat(saved.getPhone()).isNull();
        assertThat(saved.getWard()).isNull();
        assertThat(saved.getLatitude()).isNull();
        assertThat(saved.getLongitude()).isNull();
        assertThat(saved.isInDeliveryRadius()).isTrue();
    }

    @Test
    void update_canUnsetDefaultWithoutClearingOthers() {
        when(customerRepository.findById(10L)).thenReturn(Optional.of(customer));
        CustomerAddress existing = address(3L, "Cũ", true);
        when(addressRepository.findByIdAndCustomerId(3L, 10L)).thenReturn(Optional.of(existing));
        when(addressRepository.save(existing)).thenReturn(existing);

        service.update(10L, 3L, request(false, new BigDecimal("4")));

        verify(addressRepository, never()).clearDefaultForCustomer(any());
        assertThat(existing.isDefaultAddress()).isFalse();
        assertThat(existing.isInDeliveryRadius()).isTrue();
    }

    private static AddressRequest request(boolean isDefault, BigDecimal distanceKm) {
        return new AddressRequest(
                AddressKind.SHIPPING,
                "Nguyễn Văn A",
                "0901234567",
                "12 Lạch Tray",
                "Máy Chai",
                "Ngô Quyền",
                "Hải Phòng",
                distanceKm,
                isDefault);
    }

    private CustomerAddress address(long id, String line1, boolean isDefault) {
        CustomerAddress a = new CustomerAddress();
        ReflectionTestUtils.setField(a, "id", id);
        a.setCustomer(customer);
        a.setKind(AddressKind.SHIPPING);
        a.setLine1(line1);
        a.setDefaultAddress(isDefault);
        a.setInDeliveryRadius(true);
        return a;
    }
}
