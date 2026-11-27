package com.hbsisms.feature.sales;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.catalog.Product;
import com.hbsisms.feature.catalog.ProductVariant;
import com.hbsisms.feature.customer.Customer;
import com.hbsisms.feature.payment.PaymentStatus;
import com.hbsisms.feature.production.BomExplosionService;
import com.hbsisms.feature.production.ProductionOrder;
import com.hbsisms.feature.production.ProductionOrderRepository;
import com.hbsisms.feature.production.ProductionOrderStatus;
import com.hbsisms.feature.system.BusinessCodeService;
import com.hbsisms.feature.system.NotificationService;
import com.hbsisms.feature.user.AppUser;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sau khi thu đủ cọc: giữ TP có sẵn + tạo lệnh SX nháp phần thiếu → {@link OrderState#IN_PRODUCTION}.
 * Khi SX/nhập kho xong: chuyển {@link OrderState#AWAITING_SETTLEMENT}.
 */
@Service
public class PreorderFulfillmentService {

    private static final Logger log = LoggerFactory.getLogger(PreorderFulfillmentService.class);

    private final SalesOrderRepository salesOrderRepository;
    private final OrderEventRepository orderEventRepository;
    private final OrderAllocationService orderAllocationService;
    private final ProductionOrderRepository productionOrderRepository;
    private final BomExplosionService bomExplosionService;
    private final BusinessCodeService businessCodeService;
    private final NotificationService notificationService;
    private final Clock clock;

    public PreorderFulfillmentService(
            SalesOrderRepository salesOrderRepository,
            OrderEventRepository orderEventRepository,
            OrderAllocationService orderAllocationService,
            ProductionOrderRepository productionOrderRepository,
            BomExplosionService bomExplosionService,
            BusinessCodeService businessCodeService,
            NotificationService notificationService,
            Clock clock) {
        this.salesOrderRepository = salesOrderRepository;
        this.orderEventRepository = orderEventRepository;
        this.orderAllocationService = orderAllocationService;
        this.productionOrderRepository = productionOrderRepository;
        this.bomExplosionService = bomExplosionService;
        this.businessCodeService = businessCodeService;
        this.notificationService = notificationService;
        this.clock = clock;
    }

    /**
     * Gọi sau khi phiếu thu cọc SUCCESS. Idempotent nếu đã {@link PaymentStatus#DEPOSITED}
     * và đã sang sản xuất.
     */
    @Transactional
    public void onDepositReceived(Long orderId, AppUser actor) {
        SalesOrder order = salesOrderRepository
                .findWithDetailsById(orderId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy đơn hàng"));
        if (!order.isPreorder()) {
            return;
        }
        if (order.getState() == OrderState.CANCELLED || order.getState() == OrderState.EXPIRED) {
            throw ApiException.conflict("Đơn đã hủy hoặc hết hạn");
        }
        if (order.getState() == OrderState.IN_PRODUCTION
                || order.getState() == OrderState.AWAITING_SETTLEMENT
                || order.getState() == OrderState.PREPARING
                || order.getState() == OrderState.TO_SHIP
                || order.getState() == OrderState.DELIVERED
                || order.getState() == OrderState.COMPLETED) {
            return;
        }
        if (order.getPaymentStatus() != PaymentStatus.DEPOSITED
                && order.getPaymentStatus() != PaymentStatus.PAID) {
            throw ApiException.conflict("Chưa thu đủ tiền cọc");
        }

        Long actorId = actor == null ? null : actor.getId();
        int held = orderAllocationService.holdForOrder(order, actorId, true);
        SalesOrder fresh = salesOrderRepository
                .findWithDetailsById(orderId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy đơn"));

        int drafts = createProductionDraftsForShortage(fresh, actor);
        fresh.setState(OrderState.IN_PRODUCTION);
        fresh.setDepositDueAt(null);
        salesOrderRepository.save(fresh);

        OrderEvent event = new OrderEvent();
        event.setOrder(fresh);
        event.setEventType("deposit_confirmed");
        event.setDescription(
                "Đã cọc — giữ " + held + " khay, tạo " + drafts + " lệnh SX nháp; chuyển Đang sản xuất");
        event.setCreatedBy(actor);
        orderEventRepository.save(event);

        // Nếu không thiếu hàng (đã giữ đủ) → chờ tất toán luôn.
        if (drafts == 0 && fresh.getAllocationStatus() == AllocationStatus.FULL) {
            advanceToSettlement(fresh.getId(), actor);
        }
    }

    /** Chuyển đơn đặt trước sang Chờ tất toán khi phần thiếu đã được đáp ứng. */
    @Transactional
    public void tryAdvanceToSettlement(Long orderId) {
        advanceToSettlement(orderId, null);
    }

    private void advanceToSettlement(Long orderId, AppUser actor) {
        SalesOrder order = salesOrderRepository
                .findWithDetailsById(orderId)
                .orElse(null);
        if (order == null || !order.isPreorder()) {
            return;
        }
        if (order.getState() != OrderState.IN_PRODUCTION) {
            return;
        }
        // Chỉ chuyển khi đã giữ đủ (không còn outstanding) — nhập kho / SX đều phải đủ số khay.
        boolean stillShort = order.getLines().stream().anyMatch(l -> l.getOutstandingQty() > 0);
        if (stillShort) {
            return;
        }

        // Hủy lệnh SX nháp còn lại (đã nhập kho đủ, không cần SX nữa).
        for (ProductionOrder po : productionOrderRepository.findBySalesOrder_Id(orderId)) {
            if (po.getStatus() == ProductionOrderStatus.DRAFT) {
                po.setStatus(ProductionOrderStatus.CANCELLED);
                po.setNote((po.getNote() == null ? "" : po.getNote() + " — ")
                        + "Hủy vì đã nhập kho đủ hàng cho đơn");
                productionOrderRepository.save(po);
            }
        }

        order.setState(OrderState.AWAITING_SETTLEMENT);
        salesOrderRepository.save(order);

        OrderEvent event = new OrderEvent();
        event.setOrder(order);
        event.setEventType("awaiting_settlement");
        event.setDescription("Hàng sẵn sàng — chờ tất toán phần còn lại");
        event.setCreatedBy(actor);
        orderEventRepository.save(event);

        notifyCustomerSettlementReady(order);
    }

    /** Chuông portal: đủ hàng → khách PayOS tất toán phần còn lại. */
    private void notifyCustomerSettlementReady(SalesOrder order) {
        Customer customer = order.getCustomer();
        if (customer == null || customer.getAccountUser() == null) {
            return;
        }
        BigDecimal paid = order.getPaidAmount() == null ? BigDecimal.ZERO : order.getPaidAmount();
        BigDecimal total = order.getTotal() == null ? BigDecimal.ZERO : order.getTotal();
        BigDecimal remaining = total.subtract(paid);
        if (remaining.signum() < 0) {
            remaining = BigDecimal.ZERO;
        }
        String amountText = NumberFormat.getInstance(Locale.forLanguageTag("vi-VN"))
                .format(remaining.setScale(0, RoundingMode.HALF_UP));
        String code = order.getCode() == null ? "" : order.getCode();
        notificationService.notifyUser(
                customer.getAccountUser(),
                "settlement-ready",
                "Hàng đã sẵn sàng — tất toán phần còn lại",
                "Đơn #"
                        + code
                        + " đã đủ hàng. Vui lòng thanh toán phần còn lại "
                        + amountText
                        + "đ qua PayOS.",
                "/thanh-toan/don/" + code);
    }

    private int createProductionDraftsForShortage(SalesOrder order, AppUser actor) {
        // Gom phần thiếu theo loại khay (quy cách): mỗi loại khay một lệnh — BOM 1 kg nhân theo cân nặng khay,
        // nhập kho đúng khay khách đặt.
        Map<Long, BigDecimal> shortageByVariant = new LinkedHashMap<>();
        Map<Long, ProductVariant> variantById = new HashMap<>();
        for (SalesOrderLine line : order.getLines()) {
            int shortQty = line.getOutstandingQty();
            if (shortQty <= 0) {
                continue;
            }
            ProductVariant variant = line.getVariant();
            if (variant == null || variant.getProduct() == null) {
                continue;
            }
            variantById.put(variant.getId(), variant);
            shortageByVariant.merge(
                    variant.getId(),
                    BigDecimal.valueOf(shortQty).setScale(4, RoundingMode.HALF_UP),
                    BigDecimal::add);
        }

        List<ProductionOrder> existing = productionOrderRepository.findBySalesOrder_Id(order.getId());
        int created = 0;
        for (Map.Entry<Long, BigDecimal> entry : shortageByVariant.entrySet()) {
            if (entry.getValue().signum() <= 0) {
                continue;
            }
            ProductVariant variant = variantById.get(entry.getKey());
            Product product = variant.getProduct();
            // Tránh tạo trùng nếu đã có lệnh gắn đơn cho khay này (chưa hủy). Lệnh cũ chưa ghi khay → coi như trùng.
            boolean exists = existing.stream()
                    .anyMatch(po -> po.getStatus() != ProductionOrderStatus.CANCELLED
                            && po.getProduct() != null
                            && product.getId().equals(po.getProduct().getId())
                            && (po.getFinishedVariant() == null
                                    || variant.getId().equals(po.getFinishedVariant().getId())));
            if (exists) {
                continue;
            }
            try {
                BomExplosionService.ExplosionResult explosion =
                        bomExplosionService.explode(product.getId(), entry.getValue(), variant);
                ProductionOrder po = new ProductionOrder();
                po.setCode(businessCodeService.nextProductionOrderCode());
                po.setProduct(product);
                po.setFinishedVariant(variant);
                po.setPlannedQty(entry.getValue());
                po.setNote("Tự tạo từ đơn đặt trước " + order.getCode());
                po.setEstimatedMaterialCost(explosion.estimatedMaterialCost());
                po.setStatus(ProductionOrderStatus.DRAFT);
                po.setCreatedBy(actor);
                po.setSalesOrder(order);
                po.replaceMaterialLines(explosion.lines());
                productionOrderRepository.save(po);
                created++;
            } catch (RuntimeException ex) {
                log.warn(
                        "Không tạo được lệnh SX cho quy cách {} từ đơn {}: {}",
                        variant.getSku(),
                        order.getCode(),
                        ex.getMessage());
            }
        }
        return created;
    }

    /** Job: hủy đơn đặt trước quá hạn cọc. */
    @Transactional
    public int expireOverdueDeposits() {
        Instant now = Instant.now(clock);
        List<SalesOrder> overdue = salesOrderRepository.findOverduePreorderDeposits(now);
        int count = 0;
        for (SalesOrder order : overdue) {
            try {
                orderAllocationService.releaseForOrder(order, null, "Hết hạn trả cọc");
                order.setState(OrderState.CANCELLED);
                order.setPaymentStatus(PaymentStatus.UNPAID);
                order.setHoldExpiresAt(null);
                order.setDepositDueAt(null);
                salesOrderRepository.save(order);

                OrderEvent event = new OrderEvent();
                event.setOrder(order);
                event.setEventType("deposit_expired");
                event.setDescription("Hủy đơn đặt trước — quá hạn trả cọc");
                orderEventRepository.save(event);
                count++;
            } catch (RuntimeException ex) {
                log.warn("expire deposit order {}: {}", order.getCode(), ex.getMessage());
            }
        }
        return count;
    }
}
