package com.hbsisms.feature.sales;

import com.hbsisms.feature.payment.PaymentMethod;

/**
 * COD / thu khi giao: cho chuẩn bị khi chưa paid; bắt buộc thu đủ trước khi Đã giao.
 * {@code cash + shop_delivery} được normalize thành {@link PaymentMethod#COD}.
 */
public final class CollectOnDelivery {

    private CollectOnDelivery() {}

    /** Tiền mặt + shop tự giao → COD. */
    public static PaymentMethod normalize(PaymentMethod method, FulfillmentMethod fulfillment) {
        if (method == PaymentMethod.CASH && fulfillment == FulfillmentMethod.SHOP_DELIVERY) {
            return PaymentMethod.COD;
        }
        return method;
    }

    public static boolean matches(PaymentMethod method, FulfillmentMethod fulfillment) {
        if (method == PaymentMethod.COD) {
            return true;
        }
        // Đơn cũ chưa normalize vẫn coi là thu khi giao.
        return method == PaymentMethod.CASH && fulfillment == FulfillmentMethod.SHOP_DELIVERY;
    }

    public static boolean matches(SalesOrder order) {
        return matches(order.getPaymentMethod(), order.getFulfillmentMethod());
    }
}
