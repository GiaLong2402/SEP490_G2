package com.hbsisms.feature.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbsisms.feature.catalog.dto.TierInput;
import com.hbsisms.feature.catalog.dto.VariantOptionResponse;
import com.hbsisms.feature.catalog.dto.VariantRequest;
import com.hbsisms.feature.catalog.dto.VariantWithTiersResponse;
import com.hbsisms.feature.catalog.dto.VolumeTierResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

/** Locks FE {@code VariantInput} / {@code VariantWithTiers} / {@code VariantOption} JSON names. */
@JsonTest
class VariantDtoJacksonTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void request_readsTiersAndIsActive() throws Exception {
        String json =
                """
                {
                  "sku":"SKU-1",
                  "packLabel":"Khay 0,5kg",
                  "unitWeightG":500,
                  "listPrice":100000.00,
                  "packPerCase":10,
                  "barcode":null,
                  "isActive":true,
                  "tiers":[
                    {"minQtyKhay":10,"unitPrice":90000},
                    {"minQtyKhay":20,"unitPrice":80000}
                  ]
                }
                """;
        VariantRequest request = objectMapper.readValue(json, VariantRequest.class);
        assertThat(request.sku()).isEqualTo("SKU-1");
        assertThat(request.listPrice()).isEqualByComparingTo("100000.00");
        assertThat(request.packPerCase()).isEqualTo(10);
        assertThat(request.isActive()).isTrue();
        assertThat(request.tiers()).hasSize(2);
        assertThat(request.tiers().get(0)).isEqualTo(new TierInput(10, new BigDecimal("90000")));
    }

    @Test
    void request_readsEmptyTiers() throws Exception {
        String json =
                """
                {
                  "sku":"SKU-1",
                  "packLabel":"Khay",
                  "unitWeightG":null,
                  "listPrice":1,
                  "packPerCase":1,
                  "barcode":null,
                  "isActive":false,
                  "tiers":[]
                }
                """;
        VariantRequest request = objectMapper.readValue(json, VariantRequest.class);
        assertThat(request.unitWeightG()).isNull();
        assertThat(request.tiers()).isEmpty();
        assertThat(request.isActive()).isFalse();
    }

    @Test
    void withTiersResponse_writesIsActiveAndAvailable() throws Exception {
        VariantWithTiersResponse response = new VariantWithTiersResponse(
                9L,
                1L,
                "SKU-1",
                "Khay 0,5kg",
                500,
                new BigDecimal("100000.00"),
                10,
                null,
                true,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-02T00:00:00Z"),
                List.of(new VolumeTierResponse(3L, 9L, 10, new BigDecimal("90000.00"))),
                42L);

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));
        assertThat(json.get("isActive").asBoolean()).isTrue();
        assertThat(json.has("active")).isFalse();
        assertThat(json.get("available").asLong()).isEqualTo(42L);
        assertThat(json.get("packPerCase").asInt()).isEqualTo(10);
        assertThat(json.get("tiers").get(0).get("minQtyKhay").asInt()).isEqualTo(10);
        assertThat(json.get("tiers").get(0).get("unitPrice").decimalValue())
                .isEqualByComparingTo("90000.00");
        assertThat(json.get("tiers").get(0).get("variantId").asLong()).isEqualTo(9L);
    }

    @Test
    void optionResponse_matchesFeVariantOption() throws Exception {
        VariantOptionResponse response =
                new VariantOptionResponse(1L, "SKU-1", "Khay", "Tôm sú", new BigDecimal("180000"), (short) 10);
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));
        assertThat(json.get("sku").asText()).isEqualTo("SKU-1");
        assertThat(json.get("productName").asText()).isEqualTo("Tôm sú");
        assertThat(json.get("listPrice").decimalValue()).isEqualByComparingTo("180000");
        assertThat(json.get("packPerCase").asInt()).isEqualTo(10);
        assertThat(json.has("isActive")).isFalse();
    }
}
