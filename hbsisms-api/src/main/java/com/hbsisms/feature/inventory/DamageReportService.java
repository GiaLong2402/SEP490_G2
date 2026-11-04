package com.hbsisms.feature.inventory;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.common.text.Strings;
import com.hbsisms.common.web.PageRequests;
import com.hbsisms.common.web.PageResponse;
import com.hbsisms.feature.approval.ApprovalStatus;
import com.hbsisms.feature.approval.ApprovalTask;
import com.hbsisms.feature.approval.ApprovalTaskRepository;
import com.hbsisms.feature.approval.ApprovalType;
import com.hbsisms.feature.catalog.ProductVariant;
import com.hbsisms.feature.catalog.ProductVariantRepository;
import com.hbsisms.feature.inventory.dto.DamageReportRequest;
import com.hbsisms.feature.inventory.dto.DamageReportResponse;
import com.hbsisms.feature.user.AppUser;
import com.hbsisms.feature.user.UserRepository;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Task 37 — báo hỏng: tạo phiếu chờ duyệt; tồn chỉ trừ khi Chủ cửa hàng approve. */
@Service
@Transactional(readOnly = true)
public class DamageReportService {

    public static final String REF_TYPE = "damage_report";

    private static final Sort LIST_SORT = Sort.by(Sort.Direction.DESC, "reportedAt", "id");

    private final DamageReportRepository damageReportRepository;
    private final StockRepository stockRepository;
    private final ProductVariantRepository productVariantRepository;
    private final LotRepository lotRepository;
    private final LocationRepository locationRepository;
    private final WarehouseRepository warehouseRepository;
    private final UserRepository userRepository;
    private final ApprovalTaskRepository approvalTaskRepository;
    private final StockService stockService;

    public DamageReportService(
            DamageReportRepository damageReportRepository,
            StockRepository stockRepository,
            ProductVariantRepository productVariantRepository,
            LotRepository lotRepository,
            LocationRepository locationRepository,
            WarehouseRepository warehouseRepository,
            UserRepository userRepository,
            ApprovalTaskRepository approvalTaskRepository,
            StockService stockService) {
        this.damageReportRepository = damageReportRepository;
        this.stockRepository = stockRepository;
        this.productVariantRepository = productVariantRepository;
        this.lotRepository = lotRepository;
        this.locationRepository = locationRepository;
        this.warehouseRepository = warehouseRepository;
        this.userRepository = userRepository;
        this.approvalTaskRepository = approvalTaskRepository;
        this.stockService = stockService;
    }

    public PageResponse<DamageReportResponse> list(String q, ApprovalStatus status, int page, int pageSize) {
        boolean multiWarehouse = warehouseRepository.count() > 1;
        Specification<DamageReport> spec = (root, query, cb) -> {
            if (query != null && query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("variant", JoinType.LEFT).fetch("product", JoinType.LEFT);
                root.fetch("lot", JoinType.LEFT);
                var loc = root.fetch("location", JoinType.LEFT);
                loc.fetch("warehouse", JoinType.LEFT);
                root.fetch("reportedBy", JoinType.LEFT);
                root.fetch("reviewedBy", JoinType.LEFT);
                query.distinct(true);
            }
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            String needle = Strings.trimToNull(q);
            if (needle != null) {
                String pattern = "%" + needle.toLowerCase(Locale.ROOT) + "%";
                var variant = root.join("variant", JoinType.LEFT);
                var product = variant.join("product", JoinType.LEFT);
                var lot = root.join("lot", JoinType.LEFT);
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("code")), pattern),
                        cb.like(cb.lower(variant.get("sku")), pattern),
                        cb.like(cb.lower(product.get("name")), pattern),
                        cb.like(cb.lower(lot.get("lotCode")), pattern)));
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(Predicate[]::new));
        };
        return PageResponse.from(damageReportRepository
                .findAll(spec, PageRequests.of(page, pageSize, LIST_SORT))
                .map(report -> DamageReportResponse.from(report, multiWarehouse)));
    }

    @Transactional
    public DamageReportResponse create(DamageReportRequest request, Long actorUserId) {
        String reason = Strings.trimToNull(request.reason());
        if (reason == null) {
            throw ApiException.badRequest("Vui lòng nhập lý do");
        }

        ProductVariant variant = productVariantRepository
                .findWithProductById(request.variantId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy quy cách"));
        Lot lot = lotRepository
                .findById(request.lotId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy lô"));
        if (!lot.getVariant().getId().equals(variant.getId())) {
            throw ApiException.badRequest("Lô không thuộc quy cách đã chọn");
        }
        Location location = locationRepository
                .findById(request.locationId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy vị trí"));

        Stock stock = stockRepository
                .findByVariant_IdAndLot_IdAndLocation_Id(variant.getId(), lot.getId(), location.getId())
                .orElseThrow(() -> ApiException.badRequest("Lô không có hàng tại vị trí đã chọn"));
        int onHand = stock.getQtyAvailable() + stock.getQtyQuarantine() + stock.getQtyExpired();
        if (request.qty() > onHand) {
            throw ApiException.conflict("Vượt số lượng có thể báo hỏng (" + onHand + " khay)");
        }

        AppUser actor = actorUserId == null
                ? null
                : userRepository
                        .findById(actorUserId)
                        .orElseThrow(() -> ApiException.notFound("Không tìm thấy người dùng"));

        DamageReport report = new DamageReport();
        report.setCode(nextBhCode());
        report.setVariant(variant);
        report.setLot(lot);
        report.setLocation(location);
        report.setQty(request.qty());
        report.setReason(reason);
        report.setStatus(ApprovalStatus.PENDING);
        report.setReportedBy(actor);
        report.setReportedAt(Instant.now());
        report.setNote(Strings.trimToNull(request.note()));
        DamageReport saved = damageReportRepository.saveAndFlush(report);

        openApprovalTask(saved, actor);

        boolean multiWarehouse = warehouseRepository.count() > 1;
        return DamageReportResponse.from(
                damageReportRepository.findDetailedById(saved.getId()).orElse(saved), multiWarehouse);
    }

    /**
     * Called from {@link com.hbsisms.feature.approval.ApprovalService} when the owner decides a
     * damage_report task. Deducts stock only on approve.
     */
    @Transactional
    public void applyDecision(Long reportId, boolean approved, AppUser actor, String note) {
        DamageReport report = damageReportRepository
                .findDetailedById(reportId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy báo hỏng"));
        if (report.getStatus() != ApprovalStatus.PENDING) {
            throw ApiException.conflict("Báo hỏng không còn ở trạng thái chờ duyệt");
        }

        report.setStatus(approved ? ApprovalStatus.APPROVED : ApprovalStatus.REJECTED);
        report.setReviewedBy(actor);
        report.setReviewedAt(Instant.now());
        if (Strings.trimToNull(note) != null) {
            report.setNote(Strings.trimToNull(note));
        }
        damageReportRepository.save(report);

        if (approved) {
            if (report.getLocation() == null) {
                throw ApiException.conflict("Báo hỏng thiếu vị trí — không thể trừ tồn");
            }
            stockService.damage(StockMutation.of(
                    report.getVariant().getId(),
                    report.getLot().getId(),
                    report.getLocation().getId(),
                    report.getQty(),
                    REF_TYPE,
                    report.getId(),
                    report.getReason(),
                    actor == null ? null : actor.getId()));
        }
    }

    private void openApprovalTask(DamageReport report, AppUser requester) {
        approvalTaskRepository
                .findByRefTypeAndRefIdAndStatus(REF_TYPE, report.getId(), ApprovalStatus.PENDING)
                .ifPresent(existing -> {
                    throw ApiException.conflict("Đã có việc duyệt đang mở cho báo hỏng này");
                });

        ApprovalTask task = new ApprovalTask();
        task.setApprovalType(ApprovalType.DAMAGE_REPORT);
        task.setRefType(REF_TYPE);
        task.setRefId(report.getId());
        task.setTitle(report.getCode()
                + ": "
                + report.getQty()
                + " khay "
                + report.getVariant().getSku()
                + " — "
                + report.getReason());
        task.setStatus(ApprovalStatus.PENDING);
        task.setRequestedBy(requester);
        approvalTaskRepository.save(task);
    }

    private String nextBhCode() {
        int next = damageReportRepository
                .findMaxBhCode()
                .map(code -> {
                    String digits = code.replaceAll("\\D+", "");
                    try {
                        return Integer.parseInt(digits) + 1;
                    } catch (NumberFormatException ex) {
                        return 1;
                    }
                })
                .orElse(1);
        return "BH-" + String.format(Locale.ROOT, "%04d", next);
    }
}
