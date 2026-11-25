package com.hbsisms.feature.dashboard;

import com.hbsisms.feature.approval.ApprovalStatus;
import com.hbsisms.feature.approval.ApprovalTask;
import com.hbsisms.feature.approval.ApprovalTaskRepository;
import com.hbsisms.feature.catalog.ProductVariant;
import com.hbsisms.feature.customer.Customer;
import com.hbsisms.feature.customer.CustomerRepository;
import com.hbsisms.feature.customer.CustomerType;
import com.hbsisms.feature.dashboard.dto.DashboardSummaryResponse;
import com.hbsisms.feature.dashboard.dto.DashboardSummaryResponse.ApprovalItem;
import com.hbsisms.feature.dashboard.dto.DashboardSummaryResponse.ApprovalsBlock;
import com.hbsisms.feature.dashboard.dto.DashboardSummaryResponse.DailyRevenue;
import com.hbsisms.feature.dashboard.dto.DashboardSummaryResponse.DebtBlock;
import com.hbsisms.feature.dashboard.dto.DashboardSummaryResponse.DeliveriesBlock;
import com.hbsisms.feature.dashboard.dto.DashboardSummaryResponse.DeliveryItem;
import com.hbsisms.feature.dashboard.dto.DashboardSummaryResponse.InventoryBlock;
import com.hbsisms.feature.dashboard.dto.DashboardSummaryResponse.LowStockItem;
import com.hbsisms.feature.dashboard.dto.DashboardSummaryResponse.NearExpiryItem;
import com.hbsisms.feature.dashboard.dto.DashboardSummaryResponse.OverdueCustomer;
import com.hbsisms.feature.dashboard.dto.DashboardSummaryResponse.RecentOrder;
import com.hbsisms.feature.dashboard.dto.DashboardSummaryResponse.SalesBlock;
import com.hbsisms.feature.inventory.GoodsReceiptLine;
import com.hbsisms.feature.inventory.GoodsReceiptLineRepository;
import com.hbsisms.feature.inventory.Lot;
import com.hbsisms.feature.inventory.Stock;
import com.hbsisms.feature.inventory.StockRepository;
import com.hbsisms.feature.payment.CustomerDebtLedger;
import com.hbsisms.feature.payment.CustomerDebtLedgerRepository;
import com.hbsisms.feature.role.DefaultRoles;
import com.hbsisms.feature.sales.Delivery;
import com.hbsisms.feature.sales.DeliveryRepository;
import com.hbsisms.feature.sales.DeliveryState;
import com.hbsisms.feature.sales.OrderState;
import com.hbsisms.feature.sales.SalesOrder;
import com.hbsisms.feature.sales.SalesOrderRepository;
import com.hbsisms.feature.system.AppConfigService;
import com.hbsisms.security.AuthUser;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Task 8 — KPI tổng quan: giá trị tồn (SL × giá vốn lô), SKU thấp, lô sắp/đã hết hạn, công nợ quá hạn.
 * Các khối sales / approvals / deliveries để trang Tổng quan FE không trống.
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Set<OrderState> PROCESS_STATES =
            EnumSet.of(OrderState.PENDING, OrderState.CONFIRMED, OrderState.PREPARING, OrderState.TO_SHIP);
    private static final Set<OrderState> EXCLUDED_RECENT =
            EnumSet.of(OrderState.CANCELLED, OrderState.EXPIRED);

    private final StockRepository stockRepository;
    private final GoodsReceiptLineRepository goodsReceiptLineRepository;
    private final AppConfigService appConfigService;
    private final CustomerRepository customerRepository;
    private final CustomerDebtLedgerRepository customerDebtLedgerRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final ApprovalTaskRepository approvalTaskRepository;
    private final DeliveryRepository deliveryRepository;
    private final Clock clock;

    public DashboardService(
            StockRepository stockRepository,
            GoodsReceiptLineRepository goodsReceiptLineRepository,
            AppConfigService appConfigService,
            CustomerRepository customerRepository,
            CustomerDebtLedgerRepository customerDebtLedgerRepository,
            SalesOrderRepository salesOrderRepository,
            ApprovalTaskRepository approvalTaskRepository,
            DeliveryRepository deliveryRepository,
            Clock clock) {
        this.stockRepository = stockRepository;
        this.goodsReceiptLineRepository = goodsReceiptLineRepository;
        this.appConfigService = appConfigService;
        this.customerRepository = customerRepository;
        this.customerDebtLedgerRepository = customerDebtLedgerRepository;
        this.salesOrderRepository = salesOrderRepository;
        this.approvalTaskRepository = approvalTaskRepository;
        this.deliveryRepository = deliveryRepository;
        this.clock = clock;
    }

    public DashboardSummaryResponse summary(AuthUser actor) {
        Set<String> perms = actor == null || actor.permissions() == null ? Set.of() : actor.permissions();
        String roleCode = actor == null ? null : actor.roleCode();
        boolean elevated = DefaultRoles.ADMIN.equals(roleCode) || DefaultRoles.OWNER.equals(roleCode);
        boolean canOrders = perms.contains("orders");
        boolean canInventory = perms.contains("inventory");
        // Công nợ tổng quan: Chủ/Admin (kể cả khi role khác lỡ có quyền debt).
        boolean canDebt = elevated && perms.contains("debt");
        boolean canApprove = perms.contains("approve");
        // Giá trị tồn / vốn: chỉ elevated; thủ kho vẫn thấy SL + HSD + tồn thấp.
        boolean showInventoryValue = elevated;

        return new DashboardSummaryResponse(
                canOrders ? salesBlock(elevated) : null,
                canDebt ? debtBlock() : null,
                canApprove ? approvalsBlock() : null,
                canInventory ? inventoryBlock(showInventoryValue) : null,
                canOrders || canInventory ? deliveriesBlock() : null);
    }

    private InventoryBlock inventoryBlock(boolean showInventoryValue) {
        int lowThreshold = appConfigService.lowStockThreshold();
        int expiryDays = appConfigService.expiryWarningDays();
        LocalDate today = LocalDate.now(clock.withZone(ZONE));

        List<Stock> stocks = stockRepository.findAllWithDetails();
        Map<String, BigDecimal> costByLot = showInventoryValue ? lotUnitCosts(stocks) : Map.of();

        int available = 0;
        BigDecimal inventoryValue = BigDecimal.ZERO;
        Map<Long, Integer> availableByVariant = new HashMap<>();
        Map<Long, NearLotAgg> nearByLot = new LinkedHashMap<>();
        Set<Long> expiredLotIds = new HashSet<>();

        for (Stock s : stocks) {
            Lot lot = s.getLot();
            ProductVariant variant = s.getVariant();
            available += s.getQtyAvailable();
            availableByVariant.merge(variant.getId(), s.getQtyAvailable(), Integer::sum);

            if (showInventoryValue) {
                BigDecimal unitCost = costByLot.getOrDefault(lot.getLotCode(), BigDecimal.ZERO);
                if (s.getQtyAvailable() > 0 && unitCost.signum() > 0) {
                    inventoryValue = inventoryValue.add(
                            unitCost.multiply(BigDecimal.valueOf(s.getQtyAvailable())));
                }
            }

            if (s.getQtyExpired() > 0) {
                expiredLotIds.add(lot.getId());
            }

            // Task 9 / FEFO: chỉ cảnh báo khi còn khả dụng và chạm ngưỡng HSD.
            if (s.getQtyAvailable() <= 0 || lot.getExpDate() == null) {
                continue;
            }
            int daysLeft = (int) ChronoUnit.DAYS.between(today, lot.getExpDate());
            if (daysLeft > expiryDays) {
                continue;
            }
            NearLotAgg agg = nearByLot.computeIfAbsent(
                    lot.getId(),
                    id -> new NearLotAgg(
                            lot.getLotCode(),
                            variant.getProduct().getName(),
                            daysLeft,
                            0));
            agg.qty += s.getQtyAvailable() + s.getQtyHeld();
        }

        List<NearExpiryItem> nearExpiry = nearByLot.values().stream()
                .sorted(Comparator.comparingInt((NearLotAgg a) -> a.daysLeft))
                .limit(5)
                .map(a -> new NearExpiryItem(a.lotCode, a.productName, a.daysLeft, a.qty))
                .toList();

        Map<Long, Stock> sampleByVariant = new HashMap<>();
        for (Stock s : stocks) {
            sampleByVariant.putIfAbsent(s.getVariant().getId(), s);
        }
        List<LowStockItem> lowStock = availableByVariant.entrySet().stream()
                .filter(e -> e.getValue() < lowThreshold)
                .sorted(Comparator.comparingInt(Map.Entry::getValue))
                .map(e -> {
                    Stock sample = sampleByVariant.get(e.getKey());
                    ProductVariant v = sample.getVariant();
                    return new LowStockItem(v.getSku(), v.getProduct().getName(), e.getValue());
                })
                .toList();

        return new InventoryBlock(
                available,
                showInventoryValue ? inventoryValue.setScale(2, RoundingMode.HALF_UP) : null,
                nearByLot.size(),
                expiredLotIds.size(),
                nearExpiry,
                lowStock);
    }

    private Map<String, BigDecimal> lotUnitCosts(List<Stock> stocks) {
        Set<String> codes = stocks.stream()
                .map(s -> s.getLot().getLotCode())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (codes.isEmpty()) {
            return Map.of();
        }
        Map<String, BigDecimal> map = new HashMap<>();
        for (GoodsReceiptLine line : goodsReceiptLineRepository.findCostLinesByLotCodes(codes)) {
            map.putIfAbsent(line.getLotCode(), line.getUnitPrice());
        }
        return map;
    }

    private DebtBlock debtBlock() {
        List<Customer> b2b = customerRepository.findByCustomerType(CustomerType.B2B);
        if (b2b.isEmpty()) {
            return new DebtBlock(BigDecimal.ZERO, BigDecimal.ZERO, List.of());
        }
        Map<Long, List<CustomerDebtLedger>> byCustomer = customerDebtLedgerRepository
                .findByCustomer_IdIn(b2b.stream().map(Customer::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(e -> e.getCustomer().getId()));

        LocalDate today = LocalDate.now(clock.withZone(ZONE));
        BigDecimal totalDebt = BigDecimal.ZERO;
        BigDecimal totalOverdue = BigDecimal.ZERO;
        List<OverdueCustomer> overdueCustomers = new ArrayList<>();

        for (Customer c : b2b) {
            BigDecimal debt = c.getCurrentDebt() == null ? BigDecimal.ZERO : c.getCurrentDebt();
            totalDebt = totalDebt.add(debt);
            AgingBuckets buckets = ageDebt(byCustomer.getOrDefault(c.getId(), List.of()), today);
            BigDecimal overdue = buckets.d1_30.add(buckets.d31_60).add(buckets.d60);
            totalOverdue = totalOverdue.add(overdue);
            if (overdue.signum() > 0) {
                overdueCustomers.add(new OverdueCustomer(c.getId(), c.getName(), overdue));
            }
        }

        overdueCustomers.sort(Comparator.comparing(OverdueCustomer::overdue).reversed());
        if (overdueCustomers.size() > 3) {
            overdueCustomers = new ArrayList<>(overdueCustomers.subList(0, 3));
        }
        return new DebtBlock(totalOverdue, totalDebt, overdueCustomers);
    }

    /** Phân bổ thanh toán vào charge đến hạn sớm nhất — khớp mock FE {@code debtAging}. */
    static AgingBuckets ageDebt(List<CustomerDebtLedger> entries, LocalDate today) {
        BigDecimal credit = BigDecimal.ZERO;
        for (CustomerDebtLedger e : entries) {
            if (e.getAmount() != null && e.getAmount().signum() < 0) {
                credit = credit.add(e.getAmount().negate());
            }
        }
        List<CustomerDebtLedger> charges = entries.stream()
                .filter(e -> e.getAmount() != null && e.getAmount().signum() > 0)
                .sorted(Comparator.comparing(
                        (CustomerDebtLedger e) -> e.getDueDate() == null ? LocalDate.MAX : e.getDueDate())
                        .thenComparing(CustomerDebtLedger::getId))
                .toList();

        AgingBuckets buckets = new AgingBuckets();
        for (CustomerDebtLedger charge : charges) {
            BigDecimal amount = charge.getAmount();
            BigDecimal applied = credit.min(amount);
            credit = credit.subtract(applied);
            BigDecimal open = amount.subtract(applied);
            if (open.signum() <= 0) {
                continue;
            }
            long overdueDays = 0;
            if (charge.getDueDate() != null) {
                overdueDays = ChronoUnit.DAYS.between(charge.getDueDate(), today);
            }
            if (overdueDays <= 0) {
                buckets.d0 = buckets.d0.add(open);
            } else if (overdueDays <= 30) {
                buckets.d1_30 = buckets.d1_30.add(open);
            } else if (overdueDays <= 60) {
                buckets.d31_60 = buckets.d31_60.add(open);
            } else {
                buckets.d60 = buckets.d60.add(open);
            }
        }
        return buckets;
    }

    /** {@code includeRevenue}: chỉ Chủ/Admin — sales chỉ thấy đơn cần xử lý + đơn mới. */
    private SalesBlock salesBlock(boolean includeRevenue) {
        LocalDate today = LocalDate.now(clock.withZone(ZONE));
        int ordersToProcess = (int) salesOrderRepository.countByStateIn(PROCESS_STATES);

        List<DailyRevenue> daily = List.of();
        BigDecimal revenueToday = BigDecimal.ZERO;
        BigDecimal revenueYesterday = BigDecimal.ZERO;
        if (includeRevenue) {
            revenueToday = revenueOn(today);
            revenueYesterday = revenueOn(today.minusDays(1));
            daily = new ArrayList<>(14);
            for (int i = 13; i >= 0; i--) {
                LocalDate day = today.minusDays(i);
                daily.add(new DailyRevenue(day.toString(), revenueOn(day)));
            }
        }

        List<RecentOrder> recent = salesOrderRepository
                .findRecentExcluding(EXCLUDED_RECENT, PageRequest.of(0, 6))
                .stream()
                .map(o -> new RecentOrder(
                        o.getId(),
                        o.getCode(),
                        displayCustomerName(o),
                        o.getTotal(),
                        o.getState()))
                .toList();

        return new SalesBlock(revenueToday, revenueYesterday, ordersToProcess, daily, recent);
    }

    private BigDecimal revenueOn(LocalDate day) {
        Instant from = day.atStartOfDay(ZONE).toInstant();
        Instant to = day.plusDays(1).atStartOfDay(ZONE).toInstant();
        BigDecimal sum = salesOrderRepository.sumRevenueBetween(from, to);
        return sum == null ? BigDecimal.ZERO : sum;
    }

    private ApprovalsBlock approvalsBlock() {
        List<ApprovalTask> pending =
                approvalTaskRepository.findByStatusOrderByCreatedAtDesc(ApprovalStatus.PENDING);
        List<ApprovalItem> items = pending.stream()
                .limit(4)
                .map(t -> new ApprovalItem(t.getId(), t.getTitle(), approvalLink(t)))
                .toList();
        return new ApprovalsBlock(pending.size(), items);
    }

    private static String approvalLink(ApprovalTask task) {
        if ("sales_order".equals(task.getRefType())) {
            return "/orders/" + task.getRefId();
        }
        return "/approvals";
    }

    private DeliveriesBlock deliveriesBlock() {
        LocalDate today = LocalDate.now(clock.withZone(ZONE));
        List<Delivery> todayList = deliveryRepository.findByScheduledDateOrderByIdAsc(today);
        List<DeliveryItem> items = todayList.stream()
                .filter(d -> d.getState() == DeliveryState.READY || d.getState() == DeliveryState.GOING)
                .limit(5)
                .map(d -> new DeliveryItem(
                        d.getId(),
                        d.getCode(),
                        displayCustomerName(d.getOrder()),
                        d.getTimeWindow(),
                        d.getState()))
                .toList();
        return new DeliveriesBlock(todayList.size(), items);
    }

    private static String displayCustomerName(SalesOrder order) {
        if (order == null) {
            return "Khách lẻ";
        }
        if (order.getCustomer() != null && order.getCustomer().getName() != null) {
            return order.getCustomer().getName();
        }
        if (order.getCustomerName() != null && !order.getCustomerName().isBlank()) {
            return order.getCustomerName();
        }
        return "Khách lẻ";
    }

    private static final class NearLotAgg {
        final String lotCode;
        final String productName;
        final int daysLeft;
        int qty;

        NearLotAgg(String lotCode, String productName, int daysLeft, int qty) {
            this.lotCode = lotCode;
            this.productName = productName;
            this.daysLeft = daysLeft;
            this.qty = qty;
        }
    }

    static final class AgingBuckets {
        BigDecimal d0 = BigDecimal.ZERO;
        BigDecimal d1_30 = BigDecimal.ZERO;
        BigDecimal d31_60 = BigDecimal.ZERO;
        BigDecimal d60 = BigDecimal.ZERO;
    }
}
