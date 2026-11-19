package com.hbsisms.feature.sales;

import com.hbsisms.feature.catalog.ProductVariant;
import com.hbsisms.feature.catalog.ProductVariantRepository;
import com.hbsisms.feature.customer.Customer;
import com.hbsisms.feature.customer.CustomerRepository;
import com.hbsisms.feature.customer.CustomerType;
import com.hbsisms.feature.inventory.Stock;
import com.hbsisms.feature.inventory.StockRepository;
import com.hbsisms.feature.payment.CustomerDebtLedger;
import com.hbsisms.feature.payment.CustomerDebtLedgerRepository;
import com.hbsisms.feature.payment.DebtEntryType;
import com.hbsisms.feature.payment.PaymentMethod;
import com.hbsisms.feature.payment.PaymentStatus;
import com.hbsisms.feature.user.AppUser;
import com.hbsisms.feature.user.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Demo task #31 nhánh công nợ: đơn {@code CREDIT_NET} đã giao + nợ đã ghi + phiếu trả
 * {@code RECEIVED} → FE bấm «Hoàn tiền» → giảm {@code currentDebt} (ADJUST), không tạo phiếu REFUND.
 * Chỉ seed <strong>một lần</strong> ({@code SO-DEMO-DEBT-01}): đã có đơn demo (kể cả đã hoàn)
 * thì <strong>không</strong> tạo slot mới — tránh mỗi lần restart lại cộng nợ.
 */
@Component
@Order(71)
public class CreditNetReturnRefundSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CreditNetReturnRefundSeeder.class);
    private static final String ORDER_PREFIX = "SO-DEMO-DEBT-";
    private static final String RETURN_PREFIX = "TH-DEMO-DEBT-";
    private static final String CUSTOMER_CODE = "KH-B001";
    private static final int QTY_KHAY = 2;
    private static final int MAX_SLOTS = 3;

    private final SalesOrderRepository salesOrderRepository;
    private final SalesReturnRepository salesReturnRepository;
    private final CustomerRepository customerRepository;
    private final CustomerDebtLedgerRepository customerDebtLedgerRepository;
    private final StockRepository stockRepository;
    private final ProductVariantRepository productVariantRepository;
    private final UserRepository userRepository;
    private final OrderEventRepository orderEventRepository;
    private final Clock clock;

    public CreditNetReturnRefundSeeder(
            SalesOrderRepository salesOrderRepository,
            SalesReturnRepository salesReturnRepository,
            CustomerRepository customerRepository,
            CustomerDebtLedgerRepository customerDebtLedgerRepository,
            StockRepository stockRepository,
            ProductVariantRepository productVariantRepository,
            UserRepository userRepository,
            OrderEventRepository orderEventRepository,
            Clock clock) {
        this.salesOrderRepository = salesOrderRepository;
        this.salesReturnRepository = salesReturnRepository;
        this.customerRepository = customerRepository;
        this.customerDebtLedgerRepository = customerDebtLedgerRepository;
        this.stockRepository = stockRepository;
        this.productVariantRepository = productVariantRepository;
        this.userRepository = userRepository;
        this.orderEventRepository = orderEventRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // Đã có phiếu RECEIVED → chỉ báo sẵn sàng, không cộng nợ thêm.
        for (int i = 1; i <= MAX_SLOTS; i++) {
            String orderCode = ORDER_PREFIX + String.format("%02d", i);
            SalesOrder existing = salesOrderRepository.findByCode(orderCode).orElse(null);
            if (existing == null) {
                continue;
            }
            SalesReturn open = salesReturnRepository.findByOrder_IdOrderByCreatedAtDesc(existing.getId()).stream()
                    .filter(r -> r.getStatus() == ReturnStatus.RECEIVED)
                    .findFirst()
                    .orElse(null);
            if (open != null) {
                log.info(
                        "Demo công nợ #31 sẵn sàng: phiếu {} (received) · đơn {} — Trả hàng → Hoàn tiền (không seed thêm)",
                        open.getCode(),
                        orderCode);
                return;
            }
        }

        // Đã từng seed (kể cả đã hoàn) → dừng hẳn, không tạo SO-DEMO-DEBT-02… mỗi lần restart.
        for (int i = 1; i <= MAX_SLOTS; i++) {
            String orderCode = ORDER_PREFIX + String.format("%02d", i);
            if (salesOrderRepository.findByCode(orderCode).isPresent()) {
                log.info(
                        "Demo công nợ #31: đã có {} — bỏ qua seed mới (tránh cộng nợ lại khi restart)",
                        orderCode);
                return;
            }
        }

        // Lần đầu DB trống demo → tạo đúng 1 slot.
        String orderCode = ORDER_PREFIX + "01";
        String returnCode = RETURN_PREFIX + "01";
        if (!ensureSlot(orderCode, returnCode)) {
            log.warn("Demo công nợ #31: không tạo được {}", orderCode);
        }
    }

    /** @return true nếu đã có (hoặc vừa tạo) phiếu RECEIVED sẵn sàng Hoàn tiền */
    private boolean ensureSlot(String orderCode, String returnCode) {
        SalesOrder existing = salesOrderRepository.findByCode(orderCode).orElse(null);
        if (existing != null) {
            List<SalesReturn> returns = salesReturnRepository.findByOrder_IdOrderByCreatedAtDesc(existing.getId());
            SalesReturn open = returns.stream()
                    .filter(r -> r.getStatus() == ReturnStatus.RECEIVED)
                    .findFirst()
                    .orElse(null);
            if (open != null) {
                log.info(
                        "Demo công nợ #31 sẵn sàng: phiếu {} (received) · đơn {} · khách {} — Trả hàng → Hoàn tiền → xem Nợ trên KH",
                        open.getCode(),
                        orderCode,
                        existing.getCustomer() == null ? "?" : existing.getCustomer().getCode());
                return true;
            }
            if (returns.stream().anyMatch(r -> r.getStatus() == ReturnStatus.REFUNDED)) {
                log.info("Demo công nợ {}: đã REFUNDED — không tạo phiếu/cộng nợ mới", orderCode);
                return false;
            }
            SalesOrder detailed = salesOrderRepository
                    .findWithDetailsById(existing.getId())
                    .orElse(existing);
            seedReceivedReturn(detailed, returnCode, detailed.getTotal());
            return true;
        }

        Customer customer = resolveB2bCustomer();
        if (customer == null) {
            log.warn("Skip demo công nợ {}: chưa có khách B2B", orderCode);
            return false;
        }

        ProductVariant variant = resolveVariant();
        if (variant == null) {
            log.warn("Skip demo công nợ {}: chưa có SKU", orderCode);
            return false;
        }

        AppUser actor = userRepository.findAll().stream().findFirst().orElse(null);
        Instant now = Instant.now(clock);
        BigDecimal unitPrice =
                variant.getListPrice() != null ? variant.getListPrice() : new BigDecimal("150000.00");
        BigDecimal lineTotal =
                unitPrice.multiply(BigDecimal.valueOf(QTY_KHAY)).setScale(2, RoundingMode.HALF_UP);
        short netDays = customer.getNetDays() > 0 ? customer.getNetDays() : (short) 15;

        SalesOrder order = new SalesOrder();
        order.setCode(orderCode);
        order.setCustomer(customer);
        order.setCustomerName(customer.getName());
        order.setOrderType(OrderType.B2B);
        order.setChannel(SaleChannel.PHONE);
        order.setPaymentMethod(PaymentMethod.CREDIT_NET);
        order.setPaymentTermDays(netDays);
        order.setPaymentStatus(PaymentStatus.DEBT);
        order.setAllocationStatus(AllocationStatus.FULL);
        order.setFulfillmentMethod(FulfillmentMethod.PICKUP);
        order.setState(OrderState.DELIVERED);
        order.setSubtotal(lineTotal);
        order.setDiscountTotal(BigDecimal.ZERO.setScale(2));
        order.setShippingFee(BigDecimal.ZERO.setScale(2));
        order.setTotal(lineTotal);
        order.setOverLimit(false);
        order.setOverLimitAmount(BigDecimal.ZERO.setScale(2));
        order.setPlacedBy(actor);
        order.setPlacedAt(now);
        order.setHoldExpiresAt(null);
        order.setNote("Demo task #31 — đơn công nợ, phiếu trả đã nhận hàng, chờ Hoàn tiền (giảm nợ)");

        SalesOrderLine line = new SalesOrderLine();
        line.setVariant(variant);
        line.setUnit(SellUnit.PACK);
        line.setQty(QTY_KHAY);
        line.setQtyKhay(QTY_KHAY);
        line.setUnitPrice(unitPrice);
        line.setDiscountPct(BigDecimal.ZERO);
        line.setLineTotal(lineTotal);
        line.setPriceBasis(PriceBasis.LIST);
        line.setOutstandingQty(0);
        order.addLine(line);
        order = salesOrderRepository.saveAndFlush(order);

        BigDecimal debtBefore = customer.getCurrentDebt() == null
                ? BigDecimal.ZERO.setScale(2)
                : customer.getCurrentDebt().setScale(2, RoundingMode.HALF_UP);
        customer.setCurrentDebt(debtBefore.add(lineTotal));
        CustomerDebtLedger charge = new CustomerDebtLedger();
        charge.setCustomer(customer);
        charge.setOrder(order);
        charge.setEntryType(DebtEntryType.CHARGE);
        charge.setAmount(lineTotal);
        charge.setBalanceAfter(customer.getCurrentDebt());
        charge.setDueDate(LocalDate.now(clock).plusDays(netDays));
        charge.setNote("Demo CHARGE đơn " + orderCode);
        customerDebtLedgerRepository.save(charge);
        customerRepository.save(customer);

        OrderEvent event = new OrderEvent();
        event.setOrder(order);
        event.setEventType("debt");
        event.setDescription("Demo: ghi nợ " + lineTotal.toPlainString() + " ₫ — chờ trả hàng hoàn (giảm nợ)");
        event.setCreatedBy(actor);
        orderEventRepository.save(event);

        seedReceivedReturn(order, returnCode, lineTotal);
        log.info(
                "Demo công nợ #31 sẵn sàng: phiếu {} · đơn {} · nợ KH {} = {} ₫ (+{}) — Trả hàng → Hoàn tiền",
                returnCode,
                orderCode,
                customer.getCode(),
                customer.getCurrentDebt().toPlainString(),
                lineTotal.toPlainString());
        return true;
    }

    private void seedReceivedReturn(SalesOrder order, String returnCode, BigDecimal amount) {
        if (salesReturnRepository.findByOrder_IdOrderByCreatedAtDesc(order.getId()).stream()
                .anyMatch(r -> returnCode.equals(r.getCode()) || r.getStatus() == ReturnStatus.RECEIVED)) {
            return;
        }
        AppUser actor = userRepository.findAll().stream().findFirst().orElse(null);
        SalesOrderLine line = order.getLines().isEmpty() ? null : order.getLines().get(0);
        Long variantId = line == null || line.getVariant() == null ? null : line.getVariant().getId();
        int qty = line == null ? QTY_KHAY : line.getQtyKhay();

        SalesReturn row = new SalesReturn();
        row.setCode(returnCode);
        row.setOrder(order);
        row.setCustomer(order.getCustomer());
        row.setReason("Demo #31 — hàng lỗi, đã nhận về kho, chờ hoàn (giảm công nợ)");
        row.setAmount(amount.setScale(2, RoundingMode.HALF_UP));
        row.setItems(List.of(new ReturnItem(variantId, null, qty, amount)));
        row.setStatus(ReturnStatus.RECEIVED);
        row.setCreatedBy(actor);
        row.setInspectedBy(actor);
        row.setInspectedAt(Instant.now(clock));
        salesReturnRepository.save(row);
    }

    private Customer resolveB2bCustomer() {
        return customerRepository
                .findByCodeIgnoreCase(CUSTOMER_CODE)
                .filter(c -> c.getCustomerType() == CustomerType.B2B)
                .or(() -> customerRepository.findAll().stream()
                        .filter(c -> c.getCustomerType() == CustomerType.B2B && c.isActive())
                        .findFirst())
                .orElse(null);
    }

    private ProductVariant resolveVariant() {
        Stock pick = stockRepository.findAllWithDetails().stream().findFirst().orElse(null);
        if (pick != null && pick.getVariant() != null) {
            return pick.getVariant();
        }
        return productVariantRepository.findAll().stream().findFirst().orElse(null);
    }
}
