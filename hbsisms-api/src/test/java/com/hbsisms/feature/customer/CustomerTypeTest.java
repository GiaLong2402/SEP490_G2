package com.hbsisms.feature.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CustomerTypeTest {

    @Test
    void codeIsLowercase() {
        assertThat(CustomerType.B2C.code()).isEqualTo("b2c");
        assertThat(CustomerType.B2B.code()).isEqualTo("b2b");
    }

    @Test
    void fromCodeAcceptsLowercase() {
        assertThat(CustomerType.fromCode("b2c")).isEqualTo(CustomerType.B2C);
        assertThat(CustomerType.fromCode("B2B")).isEqualTo(CustomerType.B2B);
    }

    @Test
    void fromCodeRejectsBlankAndUnknown() {
        assertThatThrownBy(() -> CustomerType.fromCode(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CustomerType.fromCode(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CustomerType.fromCode("retail")).isInstanceOf(IllegalArgumentException.class);
    }
}
