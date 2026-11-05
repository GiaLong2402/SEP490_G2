package com.hbsisms.feature.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbsisms.feature.catalog.dto.ProductRequest;
import com.hbsisms.feature.catalog.dto.ProductResponse;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

/** Locks FE {@code ProductInput} / {@code ProductDetail} JSON names (isPublished, isActive, gallery). */
@JsonTest
class ProductDtoJacksonTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void request_readsIsPublishedAndIsActive() throws Exception {
        String json =
                """
                {
                  "code":"P-1",
                  "name":"Tôm",
                  "categoryId":5,
                  "description":null,
                  "storageNote":null,
                  "origin":"VN",
                  "imageUrl":"https://x/a.jpg",
                  "gallery":["https://x/g1.jpg"],
                  "isPublished":true,
                  "isActive":false
                }
                """;
        ProductRequest request = objectMapper.readValue(json, ProductRequest.class);
        assertThat(request.code()).isEqualTo("P-1");
        assertThat(request.categoryId()).isEqualTo(5L);
        assertThat(request.gallery()).containsExactly("https://x/g1.jpg");
        assertThat(request.isPublished()).isTrue();
        assertThat(request.isActive()).isFalse();
    }

    @Test
    void request_readsGalleryNull() throws Exception {
        String json =
                """
                {
                  "code":"P-1",
                  "name":"Tôm",
                  "categoryId":null,
                  "description":null,
                  "storageNote":null,
                  "origin":null,
                  "imageUrl":null,
                  "gallery":null,
                  "isPublished":false,
                  "isActive":true
                }
                """;
        ProductRequest request = objectMapper.readValue(json, ProductRequest.class);
        assertThat(request.gallery()).isNull();
    }

    @Test
    void response_writesIsPublishedAndIsActiveNotStripped() throws Exception {
        ProductResponse response = new ProductResponse(
                1L,
                "P-1",
                "Tôm",
                "tom",
                5L,
                null,
                null,
                null,
                null,
                List.of("https://x/g1.jpg"),
                true,
                false,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-02T00:00:00Z"),
                "Hải sản",
                List.of());

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));
        assertThat(json.has("isPublished")).isTrue();
        assertThat(json.get("isPublished").asBoolean()).isTrue();
        assertThat(json.has("isActive")).isTrue();
        assertThat(json.get("isActive").asBoolean()).isFalse();
        assertThat(json.has("published")).isFalse();
        assertThat(json.has("active")).isFalse();
        assertThat(json.get("gallery").isArray()).isTrue();
        assertThat(json.get("categoryName").asText()).isEqualTo("Hải sản");
        assertThat(json.get("variants").isArray()).isTrue();
    }
}
