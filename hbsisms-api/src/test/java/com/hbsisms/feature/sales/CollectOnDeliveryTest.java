package com.hbsisms.feature.sales;

import static org.assertj.core.api.Assertions.assertThat;

import com.hbsisms.feature.payment.PaymentMethod;
import org.junit.jupiter.api.Test;

class CollectOnDeliveryTest {

    @Test
    void normalize_cashShopDelivery_toCod() {
        assertThat(CollectOnDelivery.normalize(PaymentMethod.CASH, FulfillmentMethod.SHOP_DELIVERY))
                .isEqualTo(PaymentMethod.COD);
    }

    @Test
    void normalize_cashPickup_staysCash() {
        assertThat(CollectOnDelivery.normalize(PaymentMethod.CASH, FulfillmentMethod.PICKUP))
                .isEqualTo(PaymentMethod.CASH);
    }

    @Test
    void normalize_codPickup_toCash() {
        assertThat(CollectOnDelivery.normalize(PaymentMethod.COD, FulfillmentMethod.PICKUP))
                .isEqualTo(PaymentMethod.CASH);
    }

    @Test
    void normalize_codShopDelivery_staysCod() {
        assertThat(CollectOnDelivery.normalize(PaymentMethod.COD, FulfillmentMethod.SHOP_DELIVERY))
                .isEqualTo(PaymentMethod.COD);
    }

    @Test
    void matches_codOrLegacyCashShopDelivery() {
        assertThat(CollectOnDelivery.matches(PaymentMethod.COD, FulfillmentMethod.PARTNER_DELIVERY)).isTrue();
        assertThat(CollectOnDelivery.matches(PaymentMethod.COD, FulfillmentMethod.SHOP_DELIVERY)).isTrue();
        assertThat(CollectOnDelivery.matches(PaymentMethod.COD, FulfillmentMethod.PICKUP)).isFalse();
        assertThat(CollectOnDelivery.matches(PaymentMethod.CASH, FulfillmentMethod.SHOP_DELIVERY)).isTrue();
        assertThat(CollectOnDelivery.matches(PaymentMethod.CASH, FulfillmentMethod.PICKUP)).isFalse();
        assertThat(CollectOnDelivery.matches(PaymentMethod.BANK_TRANSFER, FulfillmentMethod.SHOP_DELIVERY))
                .isFalse();
    }
}
