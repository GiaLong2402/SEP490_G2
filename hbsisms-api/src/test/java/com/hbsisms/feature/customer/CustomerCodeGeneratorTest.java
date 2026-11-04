package com.hbsisms.feature.customer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class CustomerCodeGeneratorTest {

    @Test
    void nextB2cStartsAt001() {
        assertThat(CustomerCodeGenerator.nextCode(CustomerType.B2C, List.of())).isEqualTo("KH-001");
    }

    @Test
    void nextB2cIgnoresKhoiAndB2bCodes() {
        assertThat(CustomerCodeGenerator.nextCode(
                        CustomerType.B2C, List.of("KH-002", "KH-B001", "KH-KHOI-TEST-001", "KH-010")))
                .isEqualTo("KH-011");
    }

    @Test
    void nextB2bUsesKhBPrefix() {
        assertThat(CustomerCodeGenerator.nextCode(CustomerType.B2B, List.of("KH-B001", "KH-B003")))
                .isEqualTo("KH-B004");
    }
}
