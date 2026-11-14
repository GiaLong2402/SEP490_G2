package com.hbsisms.feature.goong;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.goong.dto.GeoLocateResponse;
import com.hbsisms.feature.goong.dto.GeoReverseResponse;
import com.hbsisms.feature.goong.dto.GeoShopResponse;
import java.math.BigDecimal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Authenticated helpers for map UI (shop, forward geocode, reverse geocode). */
@RestController
@RequestMapping("/geo")
public class GeoController {

    private final GoongGeocodeService goongGeocodeService;

    public GeoController(GoongGeocodeService goongGeocodeService) {
        this.goongGeocodeService = goongGeocodeService;
    }

    @GetMapping("/shop")
    public GeoShopResponse shop() {
        GeoPoint shop = goongGeocodeService.shopLocation();
        return new GeoShopResponse(shop.latitude(), shop.longitude(), goongGeocodeService.deliveryRadiusKm());
    }

    @GetMapping("/geocode")
    public GeoLocateResponse geocode(@RequestParam String address) {
        if (!StringUtils.hasText(address)) {
            throw ApiException.badRequest("Địa chỉ là bắt buộc");
        }
        requireGoong();
        GeoPoint point = goongGeocodeService
                .geocode(address.trim())
                .orElseThrow(() -> ApiException.badRequest("Không tìm thấy tọa độ cho địa chỉ này"));
        BigDecimal km = GeoDistance.kmBetween(goongGeocodeService.shopLocation(), point);
        BigDecimal radius = goongGeocodeService.deliveryRadiusKm();
        boolean inRadius = km != null && radius != null && km.compareTo(radius) <= 0;
        return new GeoLocateResponse(point.latitude(), point.longitude(), km, inRadius);
    }

    @GetMapping("/reverse")
    public GeoReverseResponse reverse(
            @RequestParam BigDecimal lat, @RequestParam BigDecimal lng) {
        requireGoong();
        return goongGeocodeService
                .reverse(lat, lng)
                .orElseThrow(() -> ApiException.badRequest("Không đọc được địa chỉ từ vị trí này"));
    }

    private void requireGoong() {
        if (!goongGeocodeService.enabled()) {
            throw ApiException.badRequest("Goong chưa được cấu hình (GOONG_API_KEY)");
        }
    }
}
