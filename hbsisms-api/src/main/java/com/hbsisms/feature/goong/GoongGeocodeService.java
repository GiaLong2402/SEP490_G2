package com.hbsisms.feature.goong;

import com.fasterxml.jackson.databind.JsonNode;
import com.hbsisms.feature.goong.dto.GeoReverseResponse;
import java.math.BigDecimal;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Goong Geocode V2 — forward ({@code address}) and reverse ({@code latlng}).
 *
 * @see <a href="https://help.goong.io/kb/rest-api-v2/geocode-rest-api-v2/geocode-v2/">Goong Geocode V2</a>
 */
@Service
public class GoongGeocodeService {

    private static final Logger log = LoggerFactory.getLogger(GoongGeocodeService.class);

    private final GoongProperties properties;
    private final RestClient goongRestClient;

    public GoongGeocodeService(
            GoongProperties properties, @Qualifier("goongRestClient") RestClient goongRestClient) {
        this.properties = properties;
        this.goongRestClient = goongRestClient;
    }

    public boolean enabled() {
        return properties.enabled();
    }

    public GeoPoint shopLocation() {
        BigDecimal lat = properties.shopLatitude() != null
                ? properties.shopLatitude()
                : new BigDecimal("20.951248");
        BigDecimal lng = properties.shopLongitude() != null
                ? properties.shopLongitude()
                : new BigDecimal("107.080502");
        return new GeoPoint(lat, lng);
    }

    public BigDecimal deliveryRadiusKm() {
        return properties.deliveryRadiusKm() != null
                ? properties.deliveryRadiusKm()
                : BigDecimal.valueOf(8);
    }

    /** Best-effort geocode; empty when disabled, no hit, or HTTP error. */
    public Optional<GeoPoint> geocode(String address) {
        if (!enabled() || address == null || address.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode root = goongRestClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v2/geocode")
                            .queryParam("address", address.trim())
                            .queryParam("api_key", properties.apiKey().trim())
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode first = firstResult(root, "geocode", address);
            if (first == null) {
                return Optional.empty();
            }
            JsonNode location = first.path("geometry").path("location");
            BigDecimal lat = decimal(location, "lat");
            BigDecimal lng = decimal(location, "lng");
            if (lat == null || lng == null) {
                return Optional.empty();
            }
            return Optional.of(new GeoPoint(lat, lng));
        } catch (RestClientException ex) {
            log.warn("Goong geocode failed for address={}: {}", address, ex.getMessage());
            return Optional.empty();
        }
    }

    /** Reverse geocode pin → address parts for form fill. */
    public Optional<GeoReverseResponse> reverse(BigDecimal latitude, BigDecimal longitude) {
        if (!enabled() || latitude == null || longitude == null) {
            return Optional.empty();
        }
        String latlng = latitude.toPlainString() + "," + longitude.toPlainString();
        try {
            JsonNode root = goongRestClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v2/geocode")
                            .queryParam("latlng", latlng)
                            .queryParam("api_key", properties.apiKey().trim())
                            .queryParam("has_deprecated_administrative_unit", "true")
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode first = firstResult(root, "reverse", latlng);
            if (first == null) {
                return Optional.empty();
            }
            JsonNode compound = first.path("compound");
            JsonNode deprecated = first.path("deprecated_compound");
            String province = firstNonBlank(text(compound, "province"), text(deprecated, "province"));
            String ward = firstNonBlank(text(compound, "commune"), text(deprecated, "commune"));
            String district = text(deprecated, "district");
            String formatted = text(first, "formatted_address");
            String name = text(first, "name");
            String line1 = firstNonBlank(name, streetFromFormatted(formatted, ward, district, province));

            GeoPoint point = new GeoPoint(latitude, longitude);
            BigDecimal km = GeoDistance.kmBetween(shopLocation(), point);
            BigDecimal radius = deliveryRadiusKm();
            boolean inRadius = km != null && radius != null && km.compareTo(radius) <= 0;

            return Optional.of(new GeoReverseResponse(
                    latitude, longitude, km, inRadius, line1, ward, district, province, formatted));
        } catch (RestClientException ex) {
            log.warn("Goong reverse failed for latlng={}: {}", latlng, ex.getMessage());
            return Optional.empty();
        }
    }

    private JsonNode firstResult(JsonNode root, String op, String detail) {
        if (root == null) {
            return null;
        }
        String status = text(root, "status");
        if (status != null && !"OK".equalsIgnoreCase(status) && !"ok".equalsIgnoreCase(status)) {
            log.warn("Goong {} status={} for {}", op, status, detail);
            return null;
        }
        JsonNode results = root.get("results");
        if (results == null || !results.isArray() || results.isEmpty()) {
            return null;
        }
        return results.get(0);
    }

    /** Drop trailing admin parts from formatted address to keep street-ish line1. */
    private static String streetFromFormatted(String formatted, String ward, String district, String province) {
        if (formatted == null || formatted.isBlank()) {
            return null;
        }
        String result = formatted;
        for (String part : new String[] {ward, district, province}) {
            if (part == null || part.isBlank()) {
                continue;
            }
            result = result.replace(", " + part, "").replace(part, "").trim();
            if (result.endsWith(",")) {
                result = result.substring(0, result.length() - 1).trim();
            }
        }
        return result.isBlank() ? formatted : result;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode child = node.get(field);
        return child == null || child.isNull() ? null : child.asText();
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        JsonNode child = node.get(field);
        if (child == null || child.isNull()) {
            return null;
        }
        if (child.isNumber()) {
            return child.decimalValue();
        }
        try {
            return new BigDecimal(child.asText());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
