package com.hbsisms.feature.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hbsisms.feature.approval.ApprovalStatus;
import com.hbsisms.feature.approval.ApprovalTask;
import com.hbsisms.feature.approval.ApprovalTaskRepository;
import com.hbsisms.feature.approval.ApprovalType;
import com.hbsisms.feature.catalog.Product;
import com.hbsisms.feature.dashboard.dto.DashboardTasksResponse;
import com.hbsisms.feature.dashboard.dto.DashboardTasksResponse.RawLow;
import com.hbsisms.feature.dashboard.dto.DashboardTasksResponse.TaskGroup;
import com.hbsisms.feature.inventory.GoodsReceipt;
import com.hbsisms.feature.inventory.GoodsReceiptRepository;
import com.hbsisms.feature.inventory.InventoryQueryService;
import com.hbsisms.feature.inventory.Location;
import com.hbsisms.feature.inventory.RawMaterial;
import com.hbsisms.feature.inventory.RawMaterialRepository;
import com.hbsisms.feature.inventory.ReceiptStatus;
import com.hbsisms.feature.inventory.ReceiptType;
import com.hbsisms.feature.inventory.dto.InventoryAlertsResponse;
import com.hbsisms.feature.inventory.dto.LowStockAlertResponse;
import com.hbsisms.feature.production.MaterialIssue;
import com.hbsisms.feature.production.MaterialIssueRepository;
import com.hbsisms.feature.production.MaterialIssueStatus;
import com.hbsisms.feature.production.ProductionOrder;
import com.hbsisms.feature.production.ProductionOrderRepository;
import com.hbsisms.feature.production.ProductionOrderStatus;
import com.hbsisms.security.AuthUser;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DashboardTaskServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 25);
    private static final Instant NOW = TODAY.atTime(10, 0).atZone(ZONE).toInstant();

    @Mock
    private InventoryQueryService inventoryQueryService;
    @Mock
    private RawMaterialRepository rawMaterialRepository;
    @Mock
    private ApprovalTaskRepository approvalTaskRepository;
    @Mock
    private GoodsReceiptRepository goodsReceiptRepository;
    @Mock
    private MaterialIssueRepository materialIssueRepository;
    @Mock
    private ProductionOrderRepository productionOrderRepository;

    private DashboardTaskService service;

    @BeforeEach
    void setUp() {
        service = new DashboardTaskService(
                inventoryQueryService,
                rawMaterialRepository,
                approvalTaskRepository,
                goodsReceiptRepository,
                materialIssueRepository,
                productionOrderRepository,
                Clock.fixed(NOW, ZONE));
    }

    @Test
    void warehouseRole_seesWarehouseGroupsOnly_withUrgentFlags() {
        stubNoLowStock();
        GoodsReceipt raw = receipt(1L, "PN-0001", ReceiptType.RAW, "Chợ Long Biên", NOW.minus(Duration.ofHours(3)));
        raw.setExpectedDate(TODAY.minusDays(1));
        GoodsReceipt finished = receipt(2L, "PN-0002", ReceiptType.FINISHED, "LSX-2026-0019", NOW.minus(Duration.ofHours(30)));
        finished.setProductionOrderId(19L);
        stubReceipts(List.of(raw), List.of(finished));
        ProductionOrder order = order(20L, "LSX-2026-0020", "Mực lá", ProductionOrderStatus.APPROVED, TODAY.plusDays(1));
        when(materialIssueRepository.findByStatusOrderByCreatedAtAsc(MaterialIssueStatus.PENDING))
                .thenReturn(List.of(issue("PX-0020", order)));

        DashboardTasksResponse res = service.tasks(user("warehouse", "inventory"));

        assertThat(res.lowStock()).isNotNull();
        assertThat(res.pending().groups()).extracting(TaskGroup::key)
                .containsExactly("raw_receipt", "material_issue", "finished_receipt");
        assertThat(res.pending().total()).isEqualTo(3);
        assertThat(res.pending().urgent()).isEqualTo(2);

        TaskGroup rawGroup = res.pending().groups().get(0);
        assertThat(rawGroup.link()).isEqualTo("/receipts?receiptType=raw&status=pending");
        assertThat(rawGroup.items().get(0).urgent()).as("quá ngày dự kiến").isTrue();
        assertThat(rawGroup.items().get(0).title()).isEqualTo("Chợ Long Biên");
        assertThat(rawGroup.items().get(0).link()).isEqualTo("/receipts?q=PN-0001");

        TaskGroup issueGroup = res.pending().groups().get(1);
        assertThat(issueGroup.items().get(0).urgent()).as("hạn lệnh còn ngày mai").isFalse();
        assertThat(issueGroup.items().get(0).link()).isEqualTo("/production-orders/20");
        assertThat(issueGroup.items().get(0).title()).isEqualTo("Xuất NVL cho LSX-2026-0020 · Mực lá");

        TaskGroup finishedGroup = res.pending().groups().get(2);
        assertThat(finishedGroup.items().get(0).urgent()).as("chờ cất kho quá 24 giờ").isTrue();
        assertThat(finishedGroup.items().get(0).link()).isEqualTo("/production-orders/19");

        verifyNoInteractions(approvalTaskRepository);
        verify(productionOrderRepository, never()).search(any(), any(), any());
    }

    @Test
    void owner_seesApprovalsSplitByType_andDraftProductionOrders() {
        stubNoLowStock();
        stubReceipts(List.of(), List.of());
        when(materialIssueRepository.findByStatusOrderByCreatedAtAsc(MaterialIssueStatus.PENDING)).thenReturn(List.of());

        ApprovalTask damage = approval(ApprovalType.DAMAGE_REPORT, "damage_report", 5L, "Báo hỏng 3 khay", NOW.minus(Duration.ofDays(2)));
        ApprovalTask price = approval(ApprovalType.PRICE_LIST, "customer_price", 6L, "Giá riêng Nhà hàng A", NOW.minus(Duration.ofHours(1)));
        ApprovalTask overLimit = approval(ApprovalType.OVER_LIMIT_ORDER, "sales_order", 9L, "Đơn vượt hạn mức", NOW.minus(Duration.ofHours(2)));
        ApprovalTask stocktake = approval(ApprovalType.STOCKTAKE, "stocktake", 4L, "Kiểm kê tháng 11", NOW.minus(Duration.ofHours(5)));
        when(approvalTaskRepository.findByStatusOrderByCreatedAtDesc(ApprovalStatus.PENDING))
                .thenReturn(List.of(price, overLimit, stocktake, damage));

        ProductionOrder draft = order(30L, "LSX-2026-0030", "Chả mực", ProductionOrderStatus.DRAFT, TODAY);
        when(productionOrderRepository.search(isNull(), eq(ProductionOrderStatus.DRAFT), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(draft)));

        DashboardTasksResponse res = service.tasks(user("owner", "inventory", "approve"));

        assertThat(res.pending().groups()).extracting(TaskGroup::key)
                .containsExactly("damage_report", "customer_price", "other_approval", "production_approval");
        assertThat(res.pending().total()).isEqualTo(5);

        TaskGroup damageGroup = res.pending().groups().get(0);
        assertThat(damageGroup.link()).isEqualTo("/approvals?approvalType=damage_report");
        assertThat(damageGroup.urgentCount()).as("treo quá 24 giờ").isEqualTo(1);

        TaskGroup priceGroup = res.pending().groups().get(1);
        assertThat(priceGroup.link()).isEqualTo("/approvals?approvalType=price_list");
        assertThat(priceGroup.urgentCount()).isZero();

        TaskGroup other = res.pending().groups().get(2);
        assertThat(other.items()).extracting(i -> i.link())
                .as("cũ nhất trước, link mở thẳng đơn / phiếu kiểm kê")
                .containsExactly("/stocktakes/4", "/orders/9");

        TaskGroup production = res.pending().groups().get(3);
        assertThat(production.link()).isEqualTo("/production-orders?status=draft");
        assertThat(production.items().get(0).urgent()).as("hạn hoàn thành là hôm nay").isTrue();
        assertThat(production.items().get(0).title()).isEqualTo("Chả mực");
    }

    @Test
    void roleWithoutInventoryOrApprove_getsNothing() {
        DashboardTasksResponse res = service.tasks(user("sales", "orders"));

        assertThat(res.lowStock()).isNull();
        assertThat(res.pending()).isNull();
        verifyNoInteractions(inventoryQueryService, rawMaterialRepository, approvalTaskRepository,
                goodsReceiptRepository, materialIssueRepository, productionOrderRepository);
    }

    @Test
    void approverWithoutInventory_seesApprovalsButNoLowStock() {
        when(approvalTaskRepository.findByStatusOrderByCreatedAtDesc(ApprovalStatus.PENDING)).thenReturn(List.of());

        DashboardTasksResponse res = service.tasks(user("owner", "approve"));

        assertThat(res.lowStock()).isNull();
        assertThat(res.pending()).isNotNull();
        assertThat(res.pending().total()).isZero();
        assertThat(res.pending().groups()).isEmpty();
        verifyNoInteractions(goodsReceiptRepository, materialIssueRepository, productionOrderRepository);
    }

    @Test
    void lowStock_finishedFromInventoryAlerts_rawOnlyActiveBelowReorder_mostShortFirst() {
        when(inventoryQueryService.alerts()).thenReturn(new InventoryAlertsResponse(
                40, 10, 3,
                List.of(new LowStockAlertResponse("SKU-B", "Chả mực", 6), new LowStockAlertResponse("SKU-A", "Mực lá", 2)),
                List.of()));
        RawMaterial a = raw(1L, "NVL-A", "Mực mo", "2", "10");
        RawMaterial b = raw(2L, "NVL-B", "Cá thu", "8", "10");
        RawMaterial noLevel = raw(3L, "NVL-C", "Muối", "0", null);
        RawMaterial inactive = raw(4L, "NVL-D", "Tôm", "1", "10");
        inactive.setActive(false);
        RawMaterial equal = raw(5L, "NVL-E", "Hành", "10", "10");
        when(rawMaterialRepository.findAllByOrderByCodeAsc()).thenReturn(List.of(b, a, noLevel, inactive, equal));

        DashboardTasksResponse res = service.tasks(user("sales", "inventory"));

        assertThat(res.lowStock().threshold()).isEqualTo(10);
        assertThat(res.lowStock().finished()).extracting(f -> f.sku()).containsExactly("SKU-A", "SKU-B");
        assertThat(res.lowStock().raw()).extracting(RawLow::code).containsExactly("NVL-A", "NVL-B");
        RawLow first = res.lowStock().raw().get(0);
        assertThat(first.locationCode()).isEqualTo("LANH-01");
        assertThat(first.reorderLevel()).isEqualByComparingTo("10");
        assertThat(res.pending()).as("sales không xử lý kho / duyệt").isNull();
    }

    @Test
    void group_previewsFiveOldest_butCountsAll() {
        stubNoLowStock();
        List<GoodsReceipt> raws = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            raws.add(receipt((long) i, "PN-000" + i, ReceiptType.RAW, "Nguồn " + i, NOW.minus(Duration.ofHours(10 - i))));
        }
        stubReceipts(raws, List.of());
        when(materialIssueRepository.findByStatusOrderByCreatedAtAsc(MaterialIssueStatus.PENDING)).thenReturn(List.of());

        TaskGroup group = service.tasks(user("warehouse", "inventory")).pending().groups().get(0);

        assertThat(group.count()).isEqualTo(7);
        assertThat(group.items()).hasSize(DashboardTaskService.PREVIEW);
        assertThat(group.items().get(0).code()).isEqualTo("PN-0001");
        assertThat(group.urgentCount()).as("không có ngày dự kiến thì không gấp").isZero();
    }

    @Test
    void materialIssueOfCancelledOrder_isIgnored() {
        stubNoLowStock();
        stubReceipts(List.of(), List.of());
        ProductionOrder cancelled = order(40L, "LSX-2026-0040", "Mực lá", ProductionOrderStatus.CANCELLED, TODAY);
        when(materialIssueRepository.findByStatusOrderByCreatedAtAsc(MaterialIssueStatus.PENDING))
                .thenReturn(List.of(issue("PX-0040", cancelled)));

        DashboardTasksResponse res = service.tasks(user("warehouse", "inventory"));

        assertThat(res.pending().groups()).isEmpty();
        assertThat(res.pending().total()).isZero();
    }

    @Test
    void isBelowReorder_requiresPositiveLevelAndStrictlyBelow() {
        assertThat(DashboardTaskService.isBelowReorder(raw(1L, "A", "A", "9.5", "10"))).isTrue();
        assertThat(DashboardTaskService.isBelowReorder(raw(2L, "B", "B", "10", "10"))).isFalse();
        assertThat(DashboardTaskService.isBelowReorder(raw(3L, "C", "C", "0", "0"))).isFalse();
        assertThat(DashboardTaskService.isBelowReorder(raw(4L, "D", "D", "0", null))).isFalse();
    }

    // ---------------------------------------------------------------- fixtures

    private void stubNoLowStock() {
        when(inventoryQueryService.alerts()).thenReturn(new InventoryAlertsResponse(0, 10, 3, List.of(), List.of()));
        when(rawMaterialRepository.findAllByOrderByCodeAsc()).thenReturn(List.of());
    }

    /** Service gọi phiếu NVL trước rồi tới phiếu thành phẩm. */
    @SuppressWarnings("unchecked")
    private void stubReceipts(List<GoodsReceipt> raw, List<GoodsReceipt> finished) {
        when(goodsReceiptRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(raw, finished);
    }

    private static AuthUser user(String role, String... perms) {
        return new AuthUser(1L, "u", role, Set.of(perms));
    }

    private static GoodsReceipt receipt(Long id, String code, ReceiptType type, String source, Instant receivedAt) {
        GoodsReceipt r = new GoodsReceipt();
        ReflectionTestUtils.setField(r, "id", id);
        r.setCode(code);
        r.setReceiptType(type);
        r.setStatus(ReceiptStatus.PENDING);
        r.setSource(source);
        r.setReceivedAt(receivedAt);
        return r;
    }

    private static ProductionOrder order(Long id, String code, String productName, ProductionOrderStatus status, LocalDate due) {
        Product product = new Product();
        product.setName(productName);
        ProductionOrder o = new ProductionOrder();
        ReflectionTestUtils.setField(o, "id", id);
        ReflectionTestUtils.setField(o, "createdAt", NOW.minus(Duration.ofHours(4)));
        o.setCode(code);
        o.setProduct(product);
        o.setStatus(status);
        o.setPlannedDueDate(due);
        return o;
    }

    private static MaterialIssue issue(String code, ProductionOrder order) {
        MaterialIssue i = new MaterialIssue();
        ReflectionTestUtils.setField(i, "createdAt", NOW.minus(Duration.ofHours(2)));
        i.setCode(code);
        i.setProductionOrder(order);
        i.setStatus(MaterialIssueStatus.PENDING);
        return i;
    }

    private static ApprovalTask approval(ApprovalType type, String refType, Long refId, String title, Instant createdAt) {
        ApprovalTask t = new ApprovalTask();
        t.setApprovalType(type);
        t.setRefType(refType);
        t.setRefId(refId);
        t.setTitle(title);
        t.setCreatedAt(createdAt);
        return t;
    }

    private static RawMaterial raw(Long id, String code, String name, String onHand, String reorder) {
        Location zone = new Location();
        zone.setCode("LANH-01");
        RawMaterial m = new RawMaterial();
        ReflectionTestUtils.setField(m, "id", id);
        m.setCode(code);
        m.setName(name);
        m.setUnit("kg");
        m.setQtyOnHand(new BigDecimal(onHand));
        m.setReorderLevel(reorder == null ? null : new BigDecimal(reorder));
        m.setStorageLocation(zone);
        return m;
    }
}
