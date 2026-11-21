package com.hbsisms.feature.sales;

import com.hbsisms.common.text.Strings;
import com.hbsisms.feature.payment.PaymentMethod;
import com.hbsisms.feature.payment.PaymentStatus;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.data.jpa.domain.Specification;

/** Bộ lọc màn Giao hàng ({@code GET /deliveries}). */
final class DeliverySpecifications {

    private static final char LIKE_ESCAPE = '\\';

    /** Đơn đã bắt đầu / xong giao: vẫn hiện dù sau đó có hoàn tiền một phần (trả hàng) để còn xem lịch sử. */
    private static final Set<OrderState> DELIVERY_STAGE_STATES =
            EnumSet.of(OrderState.PARTIAL, OrderState.DELIVERED, OrderState.COMPLETED);

    /** Đơn đã được nhân viên xác nhận nhưng chưa giao: cần đã thanh toán đủ (hoặc COD — thu khi giao) mới hiện. */
    private static final Set<OrderState> CONFIRMED_STATES =
            EnumSet.of(OrderState.CONFIRMED, OrderState.PREPARING, OrderState.TO_SHIP);

    private DeliverySpecifications() {}

    static Specification<Delivery> search(
            String keyword,
            DeliveryState state,
            LocalDate date,
            String timeWindow,
            FulfillmentMethod fulfillmentMethod) {
        List<Specification<Delivery>> specs = new ArrayList<>();
        specs.add(readyForDelivery());
        String q = Strings.trimToNull(keyword);
        if (q != null) {
            specs.add(keywordSpec(q));
        }
        if (state != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("state"), state));
        }
        if (date != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("scheduledDate"), date));
        }
        String window = Strings.trimToNull(timeWindow);
        if (window != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("timeWindow"), window));
        }
        if (fulfillmentMethod != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("fulfillmentMethod"), fulfillmentMethod));
        }
        return Specification.allOf(specs);
    }

    /**
     * Chỉ đợt giao của đơn đã được xác nhận và đã thanh toán đủ:
     * (Đã xác nhận / Đang chuẩn bị / Chờ giao + (PAID hoặc COD)) HOẶC (Giao một phần / Đã giao / Hoàn tất).
     * <ul>
     *   <li>Chờ xử lý (kể cả đã trả PayOS) → ẩn, chờ nhân viên bấm Xác nhận bên Đơn hàng.</li>
     *   <li>COD (tiền mặt + shop giao) → hiện khi chưa trả; thu đủ mới bấm Đã giao được ({@link CollectOnDelivery}).</li>
     *   <li>Chờ thanh toán PayOS, chuyển thiếu → chưa PAID → ẩn.</li>
     *   <li>Trả sát giờ / trong 2 phút ân hạn → PAID; xác nhận xong thì hiện.</li>
     *   <li>Job hết hạn hủy đơn → CANCELLED → ẩn; tiền về muộn giữ lại đủ hàng → đơn mở lại (Chờ xử lý, PAID) → hiện sau khi xác nhận.</li>
     *   <li>Tiền về muộn nhưng thiếu hàng / khách hoặc cửa hàng đã hủy → CANCELLED (chờ hoàn) → ẩn.</li>
     *   <li>Đã giao rồi mới hoàn tiền một phần (trả hàng) → không còn PAID nhưng vẫn hiện để xem lịch sử.</li>
     * </ul>
     * Đơn đã hủy xem ở màn Đơn hàng.
     */
    private static Specification<Delivery> readyForDelivery() {
        return (root, query, cb) -> {
            var order = root.join("order", JoinType.INNER);
            return cb.or(
                    cb.and(
                            order.get("state").in(CONFIRMED_STATES),
                            cb.or(
                                    cb.equal(order.get("paymentStatus"), PaymentStatus.PAID),
                                    collectOnDelivery(order, cb))),
                    order.get("state").in(DELIVERY_STAGE_STATES));
        };
    }

    /** Cùng quy tắc {@link CollectOnDelivery#matches(PaymentMethod, FulfillmentMethod)}: COD, hoặc tiền mặt + shop giao (đơn cũ). */
    private static Predicate collectOnDelivery(Join<?, ?> order, CriteriaBuilder cb) {
        return cb.or(
                cb.equal(order.get("paymentMethod"), PaymentMethod.COD),
                cb.and(
                        cb.equal(order.get("paymentMethod"), PaymentMethod.CASH),
                        cb.equal(order.get("fulfillmentMethod"), FulfillmentMethod.SHOP_DELIVERY)));
    }

    /** Tìm theo mã đợt giao, mã đơn, tên khách (snapshot trên đơn hoặc tên khách hiện tại). */
    private static Specification<Delivery> keywordSpec(String keyword) {
        String pattern = "%" + Strings.escapeLike(keyword.toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> {
            var order = root.join("order", JoinType.INNER);
            var customer = order.join("customer", JoinType.LEFT);
            return cb.or(
                    cb.like(cb.lower(root.get("code")), pattern, LIKE_ESCAPE),
                    cb.like(cb.lower(order.get("code")), pattern, LIKE_ESCAPE),
                    cb.like(cb.lower(order.get("customerName")), pattern, LIKE_ESCAPE),
                    cb.like(cb.lower(customer.get("name")), pattern, LIKE_ESCAPE));
        };
    }
}
