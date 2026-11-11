package com.hbsisms.feature.storefront;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbsisms.feature.catalog.ProductVariantRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Guest cart IT — cần Postgres (profile it-external) + variant publish trong DB (seed TOM-SU-500).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it-external")
class CartApiIntegrationTest {

    static {
        System.setProperty("user.timezone", "UTC");
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ProductVariantRepository productVariantRepository;

    private String session;
    private Long variantId;

    @BeforeEach
    void setUp() {
        session = UUID.randomUUID().toString();
        variantId = productVariantRepository.findBySkuIgnoreCase("TOM-SU-500").map(v -> v.getId()).orElse(null);
    }

    @Test
    void guestCart_addUpdateRemove_withPriceResolver() throws Exception {
        if (variantId == null) {
            return; // seed missing in this DB — skip quietly
        }

        mockMvc.perform(get("/carts/current").header(CartService.SESSION_HEADER, session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());

        String afterAdd = mockMvc.perform(post("/carts/current/items")
                        .header(CartService.SESSION_HEADER, session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body(variantId, 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.items[0].unitPrice").exists())
                .andExpect(jsonPath("$.items[0].priceBasis").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long itemId = objectMapper.readTree(afterAdd).get("items").get(0).get("id").asLong();

        mockMvc.perform(put("/carts/current/items/" + itemId)
                        .header(CartService.SESSION_HEADER, session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("qty", 10))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].qty").value(10))
                .andExpect(jsonPath("$.items[0].unitPrice").value(175000.0));

        mockMvc.perform(delete("/carts/current/items/" + itemId).header(CartService.SESSION_HEADER, session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void guestCart_qtyZero_returns400() throws Exception {
        if (variantId == null) {
            return;
        }
        mockMvc.perform(post("/carts/current/items")
                        .header(CartService.SESSION_HEADER, session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body(variantId, 0))))
                .andExpect(status().isBadRequest());
    }

    private static Map<String, Object> body(Long variantId, int qty) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("variantId", variantId);
        map.put("qty", qty);
        return map;
    }
}
