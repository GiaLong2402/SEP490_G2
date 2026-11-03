package com.hbsisms.feature.customer;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.common.text.Strings;
import com.hbsisms.feature.customer.dto.AddressRequest;
import com.hbsisms.feature.customer.dto.AddressResponse;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Address APIs for a customer (task 19). Delivery radius matches FE mock default
 * {@code delivery_radius_km = 8} until AppConfig is available.
 */
@Service
@Transactional(readOnly = true)
public class CustomerAddressService {

    /** Same default as FE mock {@code configInt('delivery_radius_km', 8)}. */
    static final BigDecimal DELIVERY_RADIUS_KM = BigDecimal.valueOf(8);

    private final CustomerRepository customerRepository;
    private final CustomerAddressRepository addressRepository;

    public CustomerAddressService(
            CustomerRepository customerRepository, CustomerAddressRepository addressRepository) {
        this.customerRepository = customerRepository;
        this.addressRepository = addressRepository;
    }

    public List<AddressResponse> list(Long customerId) {
        requireCustomer(customerId);
        return addressRepository.findByCustomerIdOrderByDefaultAddressDescIdAsc(customerId).stream()
                .map(AddressResponse::from)
                .toList();
    }

    @Transactional
    public AddressResponse create(Long customerId, AddressRequest request) {
        Customer customer = requireCustomer(customerId);
        if (Boolean.TRUE.equals(request.isDefault())) {
            addressRepository.clearDefaultForCustomer(customerId);
        }
        CustomerAddress address = new CustomerAddress();
        address.setCustomer(customer);
        apply(address, request);
        return AddressResponse.from(addressRepository.save(address));
    }

    @Transactional
    public AddressResponse update(Long customerId, Long addressId, AddressRequest request) {
        requireCustomer(customerId);
        CustomerAddress address = requireAddress(customerId, addressId);
        if (Boolean.TRUE.equals(request.isDefault()) && !address.isDefaultAddress()) {
            addressRepository.clearDefaultForCustomer(customerId);
        }
        apply(address, request);
        return AddressResponse.from(addressRepository.save(address));
    }

    @Transactional
    public void delete(Long customerId, Long addressId) {
        requireCustomer(customerId);
        CustomerAddress address = requireAddress(customerId, addressId);
        addressRepository.delete(address);
    }

    private void apply(CustomerAddress address, AddressRequest request) {
        address.setKind(request.kind() == null ? AddressKind.SHIPPING : request.kind());
        address.setRecipientName(Strings.trimToNull(request.recipientName()));
        address.setPhone(Strings.trimToNull(request.phone()));
        address.setLine1(request.line1().trim());
        address.setWard(Strings.trimToNull(request.ward()));
        address.setDistrict(Strings.trimToNull(request.district()));
        address.setProvince(Strings.trimToNull(request.province()));
        // Map / geocoding chưa có — giữ null như FE mock.
        address.setLatitude(null);
        address.setLongitude(null);
        address.setDistanceKm(request.distanceKm());
        address.setInDeliveryRadius(isInDeliveryRadius(request.distanceKm()));
        address.setDefaultAddress(Boolean.TRUE.equals(request.isDefault()));
    }

    static boolean isInDeliveryRadius(BigDecimal distanceKm) {
        return distanceKm != null && distanceKm.compareTo(DELIVERY_RADIUS_KM) <= 0;
    }

    private Customer requireCustomer(Long customerId) {
        return customerRepository
                .findById(customerId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy khách hàng"));
    }

    private CustomerAddress requireAddress(Long customerId, Long addressId) {
        return addressRepository
                .findByIdAndCustomerId(addressId, customerId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy địa chỉ"));
    }
}
