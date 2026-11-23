package com.hbsisms.feature.customer;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.common.text.Strings;
import com.hbsisms.feature.customer.dto.AddressRequest;
import com.hbsisms.feature.customer.dto.AddressResponse;
import com.hbsisms.feature.goong.GeoDistance;
import com.hbsisms.feature.goong.GeoPoint;
import com.hbsisms.feature.goong.GoongGeocodeService;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Address APIs for a customer (staff {@code /customers/{id}/addresses} and B2C {@code /me/addresses}).
 * When Goong is configured, geocodes the address and computes distance to the shop; otherwise
 * uses {@code distanceKm} from the request (FE manual / mock).
 */
@Service
@Transactional(readOnly = true)
public class CustomerAddressService {

    /** Fallback when {@code app.goong.delivery-radius-km} is unset. */
    static final BigDecimal DELIVERY_RADIUS_KM = BigDecimal.valueOf(8);

    static final String CANNOT_DELETE_DEFAULT =
            "Không xóa được địa chỉ mặc định. Hãy chọn địa chỉ mặc định khác trước.";

    private final CustomerRepository customerRepository;
    private final CustomerAddressRepository addressRepository;
    private final GoongGeocodeService goongGeocodeService;

    public CustomerAddressService(
            CustomerRepository customerRepository,
            CustomerAddressRepository addressRepository,
            GoongGeocodeService goongGeocodeService) {
        this.customerRepository = customerRepository;
        this.addressRepository = addressRepository;
        this.goongGeocodeService = goongGeocodeService;
    }

    public List<AddressResponse> list(Long customerId) {
        requireCustomer(customerId);
        return addressRepository.findByCustomerIdOrderByDefaultAddressDescIdAsc(customerId).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Nếu đã có pin (lat/lng) thì luôn tính lại km theo shop hiện tại — tránh khoảng cách
     * cũ lưu khi shop còn đặt ở tọa độ mặc định Hải Phòng.
     */
    AddressResponse toResponse(CustomerAddress address) {
        if (address.getLatitude() != null && address.getLongitude() != null) {
            GeoPoint dest = new GeoPoint(address.getLatitude(), address.getLongitude());
            BigDecimal km = GeoDistance.kmBetween(goongGeocodeService.shopLocation(), dest);
            BigDecimal radius = goongGeocodeService.deliveryRadiusKm() != null
                    ? goongGeocodeService.deliveryRadiusKm()
                    : DELIVERY_RADIUS_KM;
            return AddressResponse.from(address, km, isInDeliveryRadius(km, radius));
        }
        return AddressResponse.from(address);
    }

    /** Cập nhật lại distanceKm / inDeliveryRadius trên entity khi đã có pin. */
    public boolean refreshDistanceFromShop(CustomerAddress address) {
        if (address.getLatitude() == null || address.getLongitude() == null) {
            return false;
        }
        GeoPoint dest = new GeoPoint(address.getLatitude(), address.getLongitude());
        BigDecimal km = GeoDistance.kmBetween(goongGeocodeService.shopLocation(), dest);
        BigDecimal radius = goongGeocodeService.deliveryRadiusKm() != null
                ? goongGeocodeService.deliveryRadiusKm()
                : DELIVERY_RADIUS_KM;
        boolean inRadius = isInDeliveryRadius(km, radius);
        boolean changed = address.getDistanceKm() == null
                || address.getDistanceKm().compareTo(km) != 0
                || address.isInDeliveryRadius() != inRadius;
        if (!changed) {
            return false;
        }
        address.setDistanceKm(km);
        address.setInDeliveryRadius(inRadius);
        return true;
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
        return toResponse(addressRepository.save(address));
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
        return toResponse(addressRepository.save(address));
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
        address.setDefaultAddress(defaultAddress);
        resolveLocation(address, request.latitude(), request.longitude(), request.distanceKm());
    }

    /**
     * Prefer map pin (lat/lng) → haversine. Else Goong forward-geocode. Else manual
     * {@code distanceKm}.
     */
    private void resolveLocation(
            CustomerAddress address,
            BigDecimal requestLat,
            BigDecimal requestLng,
            BigDecimal requestedDistanceKm) {
        BigDecimal radius = goongGeocodeService.deliveryRadiusKm() != null
                ? goongGeocodeService.deliveryRadiusKm()
                : DELIVERY_RADIUS_KM;

        if (requestLat != null && requestLng != null) {
            GeoPoint dest = new GeoPoint(requestLat, requestLng);
            BigDecimal km = GeoDistance.kmBetween(goongGeocodeService.shopLocation(), dest);
            address.setLatitude(dest.latitude());
            address.setLongitude(dest.longitude());
            address.setDistanceKm(km);
            address.setInDeliveryRadius(isInDeliveryRadius(km, radius));
            return;
        }

        if (goongGeocodeService.enabled()) {
            String query = buildGeocodeQuery(address);
            var point = goongGeocodeService.geocode(query);
            if (point.isPresent()) {
                GeoPoint dest = point.get();
                BigDecimal km = GeoDistance.kmBetween(goongGeocodeService.shopLocation(), dest);
                address.setLatitude(dest.latitude());
                address.setLongitude(dest.longitude());
                address.setDistanceKm(km);
                address.setInDeliveryRadius(isInDeliveryRadius(km, radius));
                return;
            }
        }

        address.setLatitude(null);
        address.setLongitude(null);
        address.setDistanceKm(requestedDistanceKm);
        address.setInDeliveryRadius(isInDeliveryRadius(requestedDistanceKm, radius));
    }

    static String buildGeocodeQuery(CustomerAddress address) {
        return Stream.of(
                        address.getLine1(),
                        address.getWard(),
                        address.getDistrict(),
                        address.getProvince())
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .collect(Collectors.joining(", "));
    }

    static boolean isInDeliveryRadius(BigDecimal distanceKm) {
        return isInDeliveryRadius(distanceKm, DELIVERY_RADIUS_KM);
    }

    static boolean isInDeliveryRadius(BigDecimal distanceKm, BigDecimal radiusKm) {
        BigDecimal radius = radiusKm != null ? radiusKm : DELIVERY_RADIUS_KM;
        return distanceKm != null && distanceKm.compareTo(radius) <= 0;
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
