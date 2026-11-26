package com.hbsisms.feature.dashboard;

import com.hbsisms.feature.approval.ApprovalStatus;
import com.hbsisms.feature.approval.ApprovalTask;
import com.hbsisms.feature.approval.ApprovalTaskRepository;
import com.hbsisms.feature.approval.ApprovalType;
import com.hbsisms.feature.dashboard.dto.DashboardTasksResponse;
import com.hbsisms.feature.dashboard.dto.DashboardTasksResponse.FinishedLow;
import com.hbsisms.feature.dashboard.dto.DashboardTasksResponse.LowStockBlock;
import com.hbsisms.feature.dashboard.dto.DashboardTasksResponse.PendingBlock;
import com.hbsisms.feature.dashboard.dto.DashboardTasksResponse.RawLow;
import com.hbsisms.feature.dashboard.dto.DashboardTasksResponse.TaskGroup;
import com.hbsisms.feature.dashboard.dto.DashboardTasksResponse.TaskItem;
import com.hbsisms.feature.inventory.GoodsReceipt;
import com.hbsisms.feature.inventory.GoodsReceiptRepository;
import com.hbsisms.feature.inventory.InventoryQueryService;
import com.hbsisms.feature.inventory.RawMaterial;
import com.hbsisms.feature.inventory.RawMaterialRepository;
import com.hbsisms.feature.inventory.ReceiptStatus;
import com.hbsisms.feature.inventory.ReceiptType;
import com.hbsisms.feature.inventory.dto.InventoryAlertsResponse;
import com.hbsisms.feature.production.MaterialIssue;
import com.hbsisms.feature.production.MaterialIssueRepository;
import com.hbsisms.feature.production.MaterialIssueStatus;
import com.hbsisms.feature.production.ProductionOrder;
import com.hbsisms.feature.production.ProductionOrderRepository;
import com.hbsisms.feature.production.ProductionOrderStatus;
import com.hbsisms.feature.role.DefaultRoles;
import com.hbsisms.security.AuthUser;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inter 4 dòng 10 — trang Tổng quan: hàng chạm ngưỡng tồn tối thiểu và các đầu việc đang treo.
 * Mỗi người chỉ thấy nhóm việc mình được phép xử lý (giống quyền ở API thao tác tương ứng).
 */
@Service
@Transactional(readOnly = true)
public class DashboardTaskService {

    static final int PREVIEW = 5;
    /** Việc chờ duyệt / hàng chờ cất kho treo quá mốc này thì gắn "Gấp". */
    static final Duration STALE_AFTER = Duration.ofHours(24);

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int MAX_DRAFT_ORDERS = 500;

    private final InventoryQueryService inventoryQueryService;
    private final RawMaterialRepository rawMaterialRepository;
    private final ApprovalTaskRepository approvalTaskRepository;
    private final GoodsReceiptRepository goodsReceiptRepository;
    private final MaterialIssueRepository materialIssueRepository;
    private final ProductionOrderRepository productionOrderRepository;
    private final Clock clock;

    public DashboardTaskService(
            InventoryQueryService inventoryQueryService,
            RawMaterialRepository rawMaterialRepository,
            ApprovalTaskRepository approvalTaskRepository,
            GoodsReceiptRepository goodsReceiptRepository,
            MaterialIssueRepository materialIssueRepository,
            ProductionOrderRepository productionOrderRepository,
            Clock clock) {
        this.inventoryQueryService = inventoryQueryService;
        this.rawMaterialRepository = rawMaterialRepository;
        this.approvalTaskRepository = approvalTaskRepository;
        this.goodsReceiptRepository = goodsReceiptRepository;
        this.materialIssueRepository = materialIssueRepository;
        this.productionOrderRepository = productionOrderRepository;
        this.clock = clock;
    }

    public DashboardTasksResponse tasks(AuthUser actor) {
        Set<String> perms = actor == null || actor.permissions() == null ? Set.of() : actor.permissions();
        String roleCode = actor == null ? null : actor.roleCode();
        boolean elevated = DefaultRoles.ADMIN.equals(roleCode) || DefaultRoles.OWNER.equals(roleCode);
        boolean inventory = perms.contains("inventory");
        boolean approve = perms.contains("approve");
        // Giống @roles.warehouseOps(): xác nhận nhập NVL, cất kho TP, xuất NVL.
        boolean warehouseOps = inventory && (elevated || DefaultRoles.WAREHOUSE.equals(roleCode));
        // Giống API duyệt lệnh SX: inventory + Chủ/Admin.
        boolean approveProduction = inventory && elevated;

        Instant now = Instant.now(clock);
        LocalDate today = LocalDate.now(clock.withZone(ZONE));

        List<TaskGroup> groups = new ArrayList<>();
        if (approve) {
            groups.addAll(approvalGroups(now));
        }
        if (approveProduction) {
            addIfAny(groups, draftProductionOrders(today));
        }
        if (warehouseOps) {
            addIfAny(groups, pendingRawReceipts(today));
            addIfAny(groups, pendingMaterialIssues(today));
            addIfAny(groups, pendingFinishedReceipts(now));
        }

        boolean seesAnyGroup = approve || approveProduction || warehouseOps;
        PendingBlock pending = seesAnyGroup
                ? new PendingBlock(
                        groups.stream().mapToInt(TaskGroup::count).sum(),
                        groups.stream().mapToInt(TaskGroup::urgentCount).sum(),
                        groups)
                : null;
        return new DashboardTasksResponse(inventory ? lowStock() : null, pending);
    }

    // ---------------------------------------------------------------- tồn thấp

    private LowStockBlock lowStock() {
        InventoryAlertsResponse alerts = inventoryQueryService.alerts();
        List<FinishedLow> finished = alerts.lowStock().stream()
                .sorted(Comparator.comparingInt(a -> a.available()))
                .map(a -> new FinishedLow(a.sku(), a.productName(), a.available()))
                .toList();

        List<RawLow> raw = rawMaterialRepository.findAllByOrderByCodeAsc().stream()
                .filter(m -> m.isActive() && isBelowReorder(m))
                .sorted(Comparator.comparing(DashboardTaskService::shortageRatio))
                .map(m -> new RawLow(
                        m.getId(),
                        m.getCode(),
                        m.getName(),
                        m.getUnit(),
                        m.getQtyOnHand(),
                        m.getReorderLevel(),
                        m.getStorageLocation() == null ? null : m.getStorageLocation().getCode()))
                .toList();
        return new LowStockBlock(alerts.lowStockThreshold(), finished, raw);
    }

    /** Cùng điều kiện "Cần đặt thêm" ở màn nguyên liệu: có đặt mức và tồn dưới mức. */
    static boolean isBelowReorder(RawMaterial m) {
        BigDecimal reorder = m.getReorderLevel();
        BigDecimal onHand = m.getQtyOnHand() == null ? BigDecimal.ZERO : m.getQtyOnHand();
        return reorder != null && reorder.signum() > 0 && onHand.compareTo(reorder) < 0;
    }

    /** Tồn / mức đặt lại — càng nhỏ càng thiếu nhiều, xếp lên đầu. */
    private static double shortageRatio(RawMaterial m) {
        BigDecimal onHand = m.getQtyOnHand() == null ? BigDecimal.ZERO : m.getQtyOnHand();
        return onHand.doubleValue() / m.getReorderLevel().doubleValue();
    }

    // ---------------------------------------------------------------- việc chờ duyệt

    private List<TaskGroup> approvalGroups(Instant now) {
        List<ApprovalTask> pending = approvalTaskRepository.findByStatusOrderByCreatedAtDesc(ApprovalStatus.PENDING)
                .stream()
                .sorted(Comparator.comparing(ApprovalTask::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        List<ApprovalTask> damage = pending.stream()
                .filter(t -> t.getApprovalType() == ApprovalType.DAMAGE_REPORT).toList();
        List<ApprovalTask> price = pending.stream()
                .filter(t -> t.getApprovalType() == ApprovalType.PRICE_LIST).toList();
        List<ApprovalTask> other = pending.stream()
                .filter(t -> t.getApprovalType() != ApprovalType.DAMAGE_REPORT
                        && t.getApprovalType() != ApprovalType.PRICE_LIST)
                .toList();

        Function<ApprovalTask, TaskItem> toItem = t -> new TaskItem(
                null, t.getTitle(), t.getCreatedAt(), null, isStale(t.getCreatedAt(), now), approvalLink(t));
        List<TaskGroup> groups = new ArrayList<>();
        addIfAny(groups, group("damage_report", "Phiếu báo hỏng chờ duyệt",
                "/approvals?approvalType=" + ApprovalType.DAMAGE_REPORT.code(), damage, toItem));
        addIfAny(groups, group("customer_price", "Giá riêng B2B chờ duyệt",
                "/approvals?approvalType=" + ApprovalType.PRICE_LIST.code(), price, toItem));
        addIfAny(groups, group("other_approval", "Yêu cầu duyệt khác", "/approvals", other, toItem));
        return groups;
    }

    static String approvalLink(ApprovalTask task) {
        if ("sales_order".equals(task.getRefType()) && task.getRefId() != null) {
            return "/orders/" + task.getRefId();
        }
        if ("stocktake".equals(task.getRefType()) && task.getRefId() != null) {
            return "/stocktakes/" + task.getRefId();
        }
        if (task.getApprovalType() != null) {
            return "/approvals?approvalType=" + task.getApprovalType().code();
        }
        return "/approvals";
    }

    // ---------------------------------------------------------------- sản xuất

    private TaskGroup draftProductionOrders(LocalDate today) {
        List<ProductionOrder> drafts = productionOrderRepository
                .search(null, ProductionOrderStatus.DRAFT,
                        PageRequest.of(0, MAX_DRAFT_ORDERS, Sort.by(Sort.Direction.ASC, "createdAt")))
                .getContent();
        return group("production_approval", "Lệnh sản xuất chờ duyệt",
                "/production-orders?status=" + ProductionOrderStatus.DRAFT.code(), drafts,
                o -> new TaskItem(
                        o.getCode(),
                        productName(o),
                        o.getCreatedAt(),
                        o.getPlannedDueDate(),
                        isDue(o.getPlannedDueDate(), today),
                        "/production-orders/" + o.getId()));
    }

    private TaskGroup pendingMaterialIssues(LocalDate today) {
        List<MaterialIssue> issues = materialIssueRepository.findByStatusOrderByCreatedAtAsc(MaterialIssueStatus.PENDING)
                .stream()
                .filter(i -> i.getProductionOrder() != null
                        && i.getProductionOrder().getStatus() != ProductionOrderStatus.CANCELLED)
                .toList();
        return group("material_issue", "Phiếu xuất NVL chờ xuất",
                "/production-orders?status=" + ProductionOrderStatus.APPROVED.code(), issues,
                i -> {
                    ProductionOrder o = i.getProductionOrder();
                    return new TaskItem(
                            i.getCode(),
                            "Xuất NVL cho " + o.getCode() + " · " + productName(o),
                            i.getCreatedAt(),
                            o.getPlannedDueDate(),
                            isDue(o.getPlannedDueDate(), today),
                            "/production-orders/" + o.getId());
                });
    }

    // ---------------------------------------------------------------- phiếu nhập

    private TaskGroup pendingRawReceipts(LocalDate today) {
        List<GoodsReceipt> receipts = pendingReceipts(ReceiptType.RAW);
        return group("raw_receipt", "Phiếu nhập NVL chờ nhập kho",
                "/receipts?receiptType=" + ReceiptType.RAW.code() + "&status=" + ReceiptStatus.PENDING.code(), receipts,
                r -> new TaskItem(
                        r.getCode(),
                        r.getSource() == null || r.getSource().isBlank() ? "Phiếu nhập nguyên liệu" : r.getSource(),
                        r.getReceivedAt(),
                        r.getExpectedDate(),
                        r.getExpectedDate() != null && r.getExpectedDate().isBefore(today),
                        "/receipts?q=" + r.getCode()));
    }

    private TaskGroup pendingFinishedReceipts(Instant now) {
        List<GoodsReceipt> receipts = pendingReceipts(ReceiptType.FINISHED);
        return group("finished_receipt", "Thành phẩm chờ cất kho",
                "/receipts?receiptType=" + ReceiptType.FINISHED.code() + "&status=" + ReceiptStatus.PENDING.code(),
                receipts,
                r -> new TaskItem(
                        r.getCode(),
                        r.getSource() == null || r.getSource().isBlank()
                                ? "Nhập thành phẩm"
                                : "Nhập thành phẩm từ " + r.getSource(),
                        r.getReceivedAt(),
                        null,
                        isStale(r.getReceivedAt(), now),
                        r.getProductionOrderId() != null
                                ? "/production-orders/" + r.getProductionOrderId()
                                : "/receipts?q=" + r.getCode()));
    }

    private List<GoodsReceipt> pendingReceipts(ReceiptType type) {
        return goodsReceiptRepository.findAll(
                (root, query, cb) -> cb.and(
                        cb.equal(root.get("receiptType"), type),
                        cb.equal(root.get("status"), ReceiptStatus.PENDING)),
                Sort.by(Sort.Direction.ASC, "receivedAt"));
    }

    // ---------------------------------------------------------------- helpers

    private static <T> TaskGroup group(
            String key, String label, String link, List<T> rows, Function<T, TaskItem> toItem) {
        List<TaskItem> all = rows.stream().map(toItem).toList();
        int urgent = (int) all.stream().filter(TaskItem::urgent).count();
        return new TaskGroup(key, label, all.size(), urgent, link, all.stream().limit(PREVIEW).toList());
    }

    private static void addIfAny(List<TaskGroup> groups, TaskGroup group) {
        if (group.count() > 0) {
            groups.add(group);
        }
    }

    private static boolean isStale(Instant createdAt, Instant now) {
        return createdAt != null && createdAt.plus(STALE_AFTER).isBefore(now);
    }

    /** Lệnh đã tới / quá hạn hoàn thành mà còn treo. */
    private static boolean isDue(LocalDate dueDate, LocalDate today) {
        return dueDate != null && !dueDate.isAfter(today);
    }

    private static String productName(ProductionOrder o) {
        return o.getProduct() == null ? o.getCode() : o.getProduct().getName();
    }
}
