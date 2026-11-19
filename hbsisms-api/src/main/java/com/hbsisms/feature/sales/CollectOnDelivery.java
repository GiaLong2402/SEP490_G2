package com.hbsisms.feature.sales;

import com.hbsisms.feature.payment.PaymentMethod;

/**
 * COD / thu khi giao: cho chuẩn bị khi chưa paid; bắt buộc thu đủ trước khi Đã giao.
 * <ul>
 *   <li>{@code cash + shop_delivery} → {@link PaymentMethod#COD}
 *   <li>{@code COD + pickup} → {@link PaymentMethod#CASH} (không có chuyến giao)
 * </ul>
 */
public final class CollectOnDelivery {

    private CollectOnDelivery() {}

    /** Chuẩn hóa phương thức thanh toán theo hình thức giao. */
    public static PaymentMethod normalize(PaymentMethod method, FulfillmentMethod fulfillment) {
        if (method == PaymentMethod.CASH && fulfillment == FulfillmentMethod.SHOP_DELIVERY) {
            return PaymentMethod.COD;
        }
        if (method == PaymentMethod.COD && fulfillment == FulfillmentMethod.PICKUP) {
            return PaymentMethod.CASH;
        }
        return method;
    }

    public static boolean matches(PaymentMethod method, FulfillmentMethod fulfillment) {
        if (method == PaymentMethod.COD) {
            // COD + đến lấy không còn là thu khi giao.
            return fulfillment != FulfillmentMethod.PICKUP;
        }
        // Đơn cũ chưa normalize vẫn coi là thu khi giao.
        return method == PaymentMethod.CASH && fulfillment == FulfillmentMethod.SHOP_DELIVERY;
    }

    public static boolean matches(SalesOrder order) {
        return matches(order.getPaymentMethod(), order.getFulfillmentMethod());
    }
}
