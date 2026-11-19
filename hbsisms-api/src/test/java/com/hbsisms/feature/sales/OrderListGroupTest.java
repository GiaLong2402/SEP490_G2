package com.hbsisms.feature.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OrderListGroupTest {

    @Test
    void fromCode_acceptsTabKeys() {
        assertThat(OrderListGroup.fromCode("awaiting_payment")).isEqualTo(OrderListGroup.AWAITING_PAYMENT);
        assertThat(OrderListGroup.fromCode("READY")).isEqualTo(OrderListGroup.READY);
        assertThat(OrderListGroup.fromCode("delivery_failed")).isEqualTo(OrderListGroup.DELIVERY_FAILED);
        assertThat(OrderListGroup.fromCode(null)).isNull();
        assertThat(OrderListGroup.fromCode("  ")).isNull();
    }

    @Test
    void fromCode_rejectsUnknown() {
        assertThatThrownBy(() -> OrderListGroup.fromCode("foo"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Nhóm đơn không hợp lệ");
    }
}
