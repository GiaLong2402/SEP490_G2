package com.hbsisms.feature.sales;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.inventory.LedgerEntryType;
import com.hbsisms.feature.inventory.Stock;
import com.hbsisms.feature.inventory.StockLedger;
import com.hbsisms.feature.inventory.StockLedgerRepository;
import com.hbsisms.feature.inventory.StockMutation;
import com.hbsisms.feature.inventory.StockRepository;
import com.hbsisms.feature.inventory.StockService;
import com.hbsisms.feature.payment.PaymentStatus;
import com.hbsisms.feature.system.AppConfigService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FEFO hold / release / OUT cho đơn bán. Ledger {@code refType=sales_order}, {@code refId=orderId}.
 */
@Service
public class OrderAllocationService {

    public static final String REF_TYPE = "sales_order";

    private final StockRepository stockRepository;
    private final StockLedgerRepository stockLedgerRepository;
    private final StockService stockService;
    private final SalesOrderRepository salesOrderRepository;
    private final SalesOrderLineRepository salesOrderLineRepository;
    private final AppConfigService appConfigService;
    private final Clock clock;

    public OrderAllocationService(
            StockRepository stockRepository,
            StockLedgerRepository stockLedgerRepository,
            StockService stockService,
            SalesOrderRepository salesOrderRepository,
            SalesOrderLineRepository salesOrderLineRepository,
            AppConfigService appConfigService,
            Clock clock) {
        this.stockRepository = stockRepository;
        this.stockLedgerRepository = stockLedgerRepository;
        this.stockService = stockService;
        this.salesOrderRepository = salesOrderRepository;
        this.salesOrderLineRepository = salesOrderLineRepository;
        this.appConfigService = appConfigService;
        this.clock = clock;
    }

    /**
     * Giữ hàng FEFO theo từng dòng đơn. Mặc định không cho thiếu
     * ({@code allowOutstanding=false}).
     */
    @Transactional
    public int holdForOrder(SalesOrder order, Long actorId) {
        return holdForOrder(order, actorId, false);
    }

    /**
     * Giữ hàng FEFO theo từng dòng đơn.
     *
     * @param allowOutstanding {@code true} chỉ khi tạo từ yêu cầu đã khả thi — ghi
     *     {@code outstandingQty} cho phần thiếu; {@code false} thì thiếu hàng → nhả hold + 409
     */
    @Transactional
    public int holdForOrder(SalesOrder order, Long actorId, boolean allowOutstanding) {
        Objects.requireNonNull(order.getId(), "order id");
        // Idempotent: đã có HOLD ròng thì không giữ thêm lần nữa.
        Map<HoldKey, Integer> existing = netHeldByLot(order.getId());
        if (!existing.isEmpty()) {
            return existing.values().stream().mapToInt(Integer::intValue).sum();
        }

        LocalDate today = LocalDate.now(clock);
        int totalHeld = 0;
        int totalNeeded = 0;

        for (SalesOrderLine line : order.getLines()) {
            int need = line.getQtyKhay();
            totalNeeded += need;
            int remaining = need;
            List<Stock> candidates =
                    stockRepository.findFefoCandidates(line.getVariant().getId(), today);
            for (Stock stock : candidates) {
                if (remaining <= 0) {
                    break;
                }
                // Re-read available after prior holds in this tx (clearAutomatically on adjust).
                Stock fresh = stockRepository
                        .findById(stock.getId())
                        .orElse(stock);
                int take = Math.min(remaining, fresh.getQtyAvailable());
                if (take <= 0) {
                    continue;
                }
                try {
                    stockService.hold(StockMutation.of(
                            fresh.getVariant().getId(),
                            fresh.getLot().getId(),
                            fresh.getLocation().getId(),
                            take,
                            REF_TYPE,
                            order.getId(),
                            "Giữ hàng FEFO đơn " + order.getCode(),
                            actorId));
                } catch (ApiException ex) {
                    // Tranh chấp lô cuối — bỏ qua dòng này, thử candidate tiếp.
                    if (ex.getStatus().value() == 409) {
                        continue;
                    }
                    throw ex;
                }
                remaining -= take;
                totalHeld += take;
            }
            line.setOutstandingQty(allowOutstanding ? remaining : 0);
        }

        applyAllocationStatus(order, totalHeld, totalNeeded);

        if (!allowOutstanding && totalHeld < totalNeeded) {
            releaseForOrder(order, actorId, "Không đủ hàng — hủy hold");
            for (SalesOrderLine line : order.getLines()) {
                line.setOutstandingQty(0);
            }
            throw ApiException.conflict(
                    "Không đủ hàng khả dụng để giữ đủ số lượng — không thể xác nhận đơn");
        }

        applyHoldExpiry(order);
        // clearAutomatically trên StockRepository.adjust* làm detach order/lines — bắt buộc save.
        salesOrderRepository.save(order);
        return totalHeld;
    }

    /** Giải phóng toàn bộ HOLD còn lại của đơn (hủy / hết hạn). */
    @Transactional
    public int releaseForOrder(SalesOrder order, Long actorId, String note) {
        Objects.requireNonNull(order.getId(), "order id");
        Map<HoldKey, Integer> net = netHeldByLot(order.getId());
        int released = 0;
        for (Map.Entry<HoldKey, Integer> entry : net.entrySet()) {
            int qty = entry.getValue();
            if (qty <= 0) {
                continue;
            }
            HoldKey key = entry.getKey();
            stockService.release(StockMutation.of(
                    key.variantId(),
                    key.lotId(),
                    key.locationId(),
                    qty,
                    REF_TYPE,
                    order.getId(),
                    note != null ? note : "Giải phóng hold đơn " + order.getCode(),
                    actorId));
            released += qty;
        }
        for (SalesOrderLine line : order.getLines()) {
            line.setOutstandingQty(line.getQtyKhay());
        }
        order.setAllocationStatus(AllocationStatus.NONE);
        order.setHoldExpiresAt(null);
        salesOrderRepository.save(order);
        return released;
    }

    /**
     * Xuất kho OUT đúng lô đã HOLD. Idempotent: gọi lại khi đã OUT hết thì không trừ thêm.
     *
     * @return số khay OUT lần này (0 nếu đã hoàn tất trước đó)
     */
    @Transactional
    public int issueOutHeldForOrder(SalesOrder order, Long actorId) {
        Objects.requireNonNull(order.getId(), "order id");
        Map<HoldKey, Integer> net = netHeldByLot(order.getId());
        int issued = 0;
        for (Map.Entry<HoldKey, Integer> entry : net.entrySet()) {
            int qty = entry.getValue();
            if (qty <= 0) {
                continue;
            }
            HoldKey key = entry.getKey();
            stockService.issueHeldOut(StockMutation.of(
                    key.variantId(),
                    key.lotId(),
                    key.locationId(),
                    qty,
                    REF_TYPE,
                    order.getId(),
                    "Xuất kho đơn quầy " + order.getCode(),
                    actorId));
            issued += qty;
        }
        if (issued > 0 || net.isEmpty()) {
            // Đã bàn giao: clear outstanding phần đã hold; FULL nếu không còn outstanding.
            int outstanding = salesOrderLineRepository.sumOutstandingByOrderId(order.getId());
            order.setAllocationStatus(
                    outstanding > 0 ? AllocationStatus.HELD : AllocationStatus.FULL);
            order.setHoldExpiresAt(null);
            salesOrderRepository.save(order);
        }
        return issued;
    }

    /** Job / check: giải phóng hold đơn quá {@code hold_expires_at}. */
    @Transactional
    public int releaseExpiredHolds() {
        Instant now = Instant.now(clock);
        List<SalesOrder> expired = salesOrderRepository.findExpiredHolds(now);
        int count = 0;
        for (SalesOrder order : expired) {
            // Chỉ release khi chưa thu đủ — đơn Paid giữ hold đến lúc xuất.
            if (order.getPaymentStatus() == PaymentStatus.PAID
                    || order.getPaymentStatus() == PaymentStatus.DEBT) {
                order.setHoldExpiresAt(null);
                continue;
            }
            SalesOrder detailed = salesOrderRepository
                    .findWithDetailsById(order.getId())
                    .orElse(order);
            releaseForOrder(detailed, null, "Hết hạn giữ hàng (" + appConfigService.paymentHoldMinutes() + " phút)");
            count++;
        }
        return count;
    }

    /**
     * Sau nhập thành phẩm: ưu tiên đơn Paid còn outstanding, hold thêm theo FEFO.
     *
     * @return số khay đã hold thêm
     */
    @Transactional
    public int allocateOutstandingAfterReceipt(Long actorId) {
        List<SalesOrderLine> lines = salesOrderLineRepository.findOutstandingPaidLines();
        int total = 0;
        Long lastOrderId = null;
        SalesOrder current = null;
        for (SalesOrderLine line : lines) {
            SalesOrder order = line.getOrder();
            if (!Objects.equals(lastOrderId, order.getId())) {
                if (current != null) {
                    refreshAllocationStatus(current);
                    salesOrderRepository.save(current);
                }
                current = salesOrderRepository
                        .findWithDetailsById(order.getId())
                        .orElse(order);
                lastOrderId = order.getId();
            }
            int need = line.getOutstandingQty();
            if (need <= 0) {
                continue;
            }
            int held = holdAdditionalForLine(current, line, need, actorId);
            line.setOutstandingQty(need - held);
            total += held;
        }
        if (current != null) {
            refreshAllocationStatus(current);
            salesOrderRepository.save(current);
        }
        return total;
    }

    private int holdAdditionalForLine(SalesOrder order, SalesOrderLine line, int need, Long actorId) {
        LocalDate today = LocalDate.now(clock);
        int remaining = need;
        int held = 0;
        for (Stock stock : stockRepository.findFefoCandidates(line.getVariant().getId(), today)) {
            if (remaining <= 0) {
                break;
            }
            Stock fresh = stockRepository.findById(stock.getId()).orElse(stock);
            int take = Math.min(remaining, fresh.getQtyAvailable());
            if (take <= 0) {
                continue;
            }
            try {
                stockService.hold(StockMutation.of(
                        fresh.getVariant().getId(),
                        fresh.getLot().getId(),
                        fresh.getLocation().getId(),
                        take,
                        REF_TYPE,
                        order.getId(),
                        "Phân bổ sau nhập TP đơn " + order.getCode(),
                        actorId));
            } catch (ApiException ex) {
                if (ex.getStatus().value() == 409) {
                    continue;
                }
                throw ex;
            }
            remaining -= take;
            held += take;
        }
        return held;
    }

    private void refreshAllocationStatus(SalesOrder order) {
        int outstanding = salesOrderLineRepository.sumOutstandingByOrderId(order.getId());
        int held = netHeldByLot(order.getId()).values().stream().mapToInt(Integer::intValue).sum();
        int needed = order.getLines().stream().mapToInt(SalesOrderLine::getQtyKhay).sum();
        applyAllocationStatus(order, held, needed);
        if (outstanding == 0 && held >= needed) {
            order.setAllocationStatus(AllocationStatus.FULL);
        }
    }

    Map<HoldKey, Integer> netHeldByLot(long orderId) {
        List<StockLedger> entries =
                stockLedgerRepository.findByRefTypeAndRefIdForAllocation(REF_TYPE, orderId);
        Map<HoldKey, Integer> net = new LinkedHashMap<>();
        for (StockLedger entry : entries) {
            if (entry.getLot() == null || entry.getLocation() == null || entry.getVariant() == null) {
                continue;
            }
            HoldKey key = new HoldKey(
                    entry.getVariant().getId(),
                    entry.getLot().getId(),
                    entry.getLocation().getId());
            int abs = Math.abs(entry.getQtyDelta());
            int delta =
                    switch (entry.getEntryType()) {
                        case HOLD -> abs;
                        case RELEASE, OUT -> -abs;
                        default -> 0;
                    };
            if (delta == 0) {
                continue;
            }
            net.merge(key, delta, Integer::sum);
        }
        net.entrySet().removeIf(e -> e.getValue() == null || e.getValue() <= 0);
        return net;
    }

    private void applyAllocationStatus(SalesOrder order, int held, int needed) {
        if (held <= 0) {
            order.setAllocationStatus(needed > 0 ? AllocationStatus.INCOMING : AllocationStatus.NONE);
        } else if (held >= needed) {
            order.setAllocationStatus(AllocationStatus.FULL);
        } else {
            order.setAllocationStatus(AllocationStatus.HELD);
        }
    }

    private void applyHoldExpiry(SalesOrder order) {
        // Online + chưa Paid/Debt → hết hạn giữ theo app_config.
        boolean online = order.getChannel() == SaleChannel.WEB || order.getChannel() == SaleChannel.APP;
        boolean unpaid = order.getPaymentStatus() == PaymentStatus.UNPAID
                || order.getPaymentStatus() == PaymentStatus.WAITING;
        if (online && unpaid && order.getAllocationStatus() != AllocationStatus.NONE) {
            int minutes = appConfigService.paymentHoldMinutes();
            order.setHoldExpiresAt(Instant.now(clock).plusSeconds(minutes * 60L));
        } else {
            order.setHoldExpiresAt(null);
        }
    }

    record HoldKey(long variantId, long lotId, long locationId) {}
}
