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
 * Address APIs for a customer (staff {@code /customers/{id}/addresses} and B2C {@code /me/addresses}).
 * Delivery radius matches FE mock default {@code delivery_radius_km = 8} until AppConfig is available.
 */
@Service
@Transactional(readOnly = true)
public class CustomerAddressService {

    /** Same default as FE mock {@code configInt('delivery_radius_km', 8)}. */
    static final BigDecimal DELIVERY_RADIUS_KM = BigDecimal.valueOf(8);

    static final String CANNOT_DELETE_DEFAULT =
            "Không xóa được địa chỉ mặc định. Hãy chọn địa chỉ mặc định khác trước.";

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
        boolean first = addressRepository.countByCustomerId(customerId) == 0;
        boolean makeDefault = first || Boolean.TRUE.equals(request.isDefault());
        if (makeDefault) {
            addressRepository.clearDefaultForCustomer(customerId);
        }
        CustomerAddress address = new CustomerAddress();
        address.setCustomer(customer);
        apply(address, request, makeDefault);
        return AddressResponse.from(addressRepository.save(address));
    }

    @Transactional
    public AddressResponse update(Long customerId, Long addressId, AddressRequest request) {
        requireCustomer(customerId);
        CustomerAddress address = requireAddress(customerId, addressId);
        boolean makeDefault = Boolean.TRUE.equals(request.isDefault());
        if (makeDefault && !address.isDefaultAddress()) {
            addressRepository.clearDefaultForCustomer(customerId);
        }
        // Cannot unset default via isDefault=false alone — keep current default until another is chosen.
        boolean defaultFlag = makeDefault || address.isDefaultAddress();
        apply(address, request, defaultFlag);
        return AddressResponse.from(addressRepository.save(address));
    }

    @Transactional
    public void delete(Long customerId, Long addressId) {
        requireCustomer(customerId);
        CustomerAddress address = requireAddress(customerId, addressId);
        long total = addressRepository.countByCustomerId(customerId);
        if (address.isDefaultAddress() && total > 1) {
            throw ApiException.conflict(CANNOT_DELETE_DEFAULT);
        }
        addressRepository.delete(address);
    }

    private void apply(CustomerAddress address, AddressRequest request, boolean defaultAddress) {
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
        address.setDefaultAddress(defaultAddress);
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
