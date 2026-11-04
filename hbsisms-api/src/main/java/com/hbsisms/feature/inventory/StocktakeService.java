package com.hbsisms.feature.inventory;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.common.text.Strings;
import com.hbsisms.common.web.PageRequests;
import com.hbsisms.common.web.PageResponse;
import com.hbsisms.feature.approval.ApprovalStatus;
import com.hbsisms.feature.approval.ApprovalTask;
import com.hbsisms.feature.approval.ApprovalTaskRepository;
import com.hbsisms.feature.approval.ApprovalType;
import com.hbsisms.feature.inventory.dto.StocktakeCompleteResponse;
import com.hbsisms.feature.inventory.dto.StocktakeCountItem;
import com.hbsisms.feature.inventory.dto.StocktakeDetailResponse;
import com.hbsisms.feature.inventory.dto.StocktakeInput;
import com.hbsisms.feature.inventory.dto.StocktakeLineResponse;
import com.hbsisms.feature.inventory.dto.StocktakeListItemResponse;
import com.hbsisms.feature.inventory.dto.StocktakeSaveCountsRequest;
import com.hbsisms.feature.user.AppUser;
import com.hbsisms.feature.user.UserRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class StocktakeService {

    public static final String REF_TYPE = "stocktake";

    private static final Sort LIST_SORT = Sort.by(Sort.Direction.DESC, "id");

    private final StocktakeRepository stocktakeRepository;
    private final StocktakeLineRepository stocktakeLineRepository;
    private final StockRepository stockRepository;
    private final LocationRepository locationRepository;
    private final WarehouseRepository warehouseRepository;
    private final ApprovalTaskRepository approvalTaskRepository;
    private final StockService stockService;
    private final UserRepository userRepository;

    public StocktakeService(
            StocktakeRepository stocktakeRepository,
            StocktakeLineRepository stocktakeLineRepository,
            StockRepository stockRepository,
            LocationRepository locationRepository,
            WarehouseRepository warehouseRepository,
            ApprovalTaskRepository approvalTaskRepository,
            StockService stockService,
            UserRepository userRepository) {
        this.stocktakeRepository = stocktakeRepository;
        this.stocktakeLineRepository = stocktakeLineRepository;
        this.stockRepository = stockRepository;
        this.locationRepository = locationRepository;
        this.warehouseRepository = warehouseRepository;
        this.approvalTaskRepository = approvalTaskRepository;
        this.stockService = stockService;
        this.userRepository = userRepository;
    }

    public PageResponse<StocktakeListItemResponse> list(String q, String status, int page, int pageSize) {
        StocktakeStatus statusFilter = parseStatus(status);
        Specification<Stocktake> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (statusFilter != null) {
                predicates.add(cb.equal(root.get("status"), statusFilter));
            }
            String needle = Strings.trimToNull(q);
            if (needle != null) {
                String pattern = "%" + needle.toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("code")), pattern),
                        cb.like(cb.lower(root.get("scope")), pattern)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return PageResponse.from(stocktakeRepository
                .findAll(spec, PageRequests.of(page, pageSize, LIST_SORT))
                .map(this::toListItem));
    }

    public StocktakeDetailResponse get(Long id) {
        Stocktake stocktake = requireWithActors(id);
        return toDetail(stocktake);
    }

    @Transactional
    public StocktakeDetailResponse create(StocktakeInput input, Long actorId) {
        AppUser actor = requireUser(actorId);
        String zone = Strings.trimToNull(input == null ? null : input.zone());
        Set<Long> locationIds = locationRepository.findAllWithWarehouse().stream()
                .filter(l -> l.getWarehouse() != null && l.getWarehouse().isActive())
                .filter(l -> zone == null || zone.equals(l.getZone()))
                .map(Location::getId)
                .collect(Collectors.toCollection(HashSet::new));
        if (locationIds.isEmpty()) {
            throw ApiException.conflict(
                    zone == null ? "Chưa có vị trí kho đang dùng" : "Khu vực này không có vị trí");
        }

        List<Stock> stocks = stockRepository.findAllWithDetails().stream()
                .filter(s -> locationIds.contains(s.getLocation().getId()))
                .filter(s -> s.getQtyAvailable() + s.getQtyHeld() > 0)
                .toList();
        if (stocks.isEmpty()) {
            throw ApiException.conflict("Khu vực này không có hàng để kiểm kê");
        }

        Stocktake stocktake = new Stocktake();
        stocktake.setCode(nextKkCode());
        stocktake.setScope(zone == null ? "Toàn kho" : zone);
        stocktake.setStatus(StocktakeStatus.COUNTING);
        stocktake.setCreatedBy(actor);
        stocktake.setCountedBy(actor);
        stocktake.setStartedAt(Instant.now());
        stocktake.setNote(Strings.trimToNull(input == null ? null : input.note()));
        stocktake = stocktakeRepository.saveAndFlush(stocktake);

        for (Stock stock : stocks) {
            StocktakeLine line = new StocktakeLine();
            line.setVariant(stock.getVariant());
            line.setLot(stock.getLot());
            line.setLocation(stock.getLocation());
            line.setSystemQty(stock.getQtyAvailable() + stock.getQtyHeld());
            line.setCountedQty(null);
            stocktake.addLine(line);
        }
        stocktakeRepository.save(stocktake);
        return toDetail(requireWithActors(stocktake.getId()));
    }

    @Transactional
    public void saveCounts(Long id, StocktakeSaveCountsRequest request, Long actorId) {
        Stocktake stocktake = requireWithActors(id);
        assertCountingEditable(stocktake);
        AppUser actor = requireUser(actorId);

        Map<Long, StocktakeLine> byId = stocktakeLineRepository.findByStocktake_Id(id).stream()
                .collect(Collectors.toMap(StocktakeLine::getId, l -> l));
        if (request.counts() != null) {
            for (StocktakeCountItem item : request.counts()) {
                StocktakeLine line = byId.get(item.lineId());
                if (line == null) {
                    continue;
                }
                if (item.countedQty() != null && item.countedQty() < 0) {
                    throw ApiException.badRequest("Số đếm không được âm");
                }
                line.setCountedQty(item.countedQty());
            }
        }
        stocktake.setCountedBy(actor);
        stocktakeRepository.save(stocktake);
    }

    @Transactional
    public StocktakeCompleteResponse complete(Long id, Long actorId) {
        Stocktake stocktake = requireWithActors(id);
        assertCountingEditable(stocktake);
        List<StocktakeLine> lines = stocktakeLineRepository.findByStocktake_Id(id);
        if (lines.stream().anyMatch(l -> l.getCountedQty() == null)) {
            throw ApiException.conflict("Còn dòng chưa đếm");
        }

        List<StocktakeLine> diffs = lines.stream()
                .filter(l -> !Objects.equals(l.getCountedQty(), l.getSystemQty()))
                .toList();
        if (diffs.isEmpty()) {
            stocktake.setStatus(StocktakeStatus.DONE);
            stocktake.setClosedAt(Instant.now());
            stocktake.setCountedBy(requireUser(actorId));
            stocktakeRepository.save(stocktake);
            return new StocktakeCompleteResponse(false);
        }

        openApproval(stocktake, diffs, actorId);
        return new StocktakeCompleteResponse(true);
    }

    @Transactional
    public void cancel(Long id, Long actorId) {
        Stocktake stocktake = requireWithActors(id);
        assertCountingEditable(stocktake);
        stocktake.setStatus(StocktakeStatus.CANCELLED);
        stocktake.setClosedAt(Instant.now());
        stocktake.setCountedBy(requireUser(actorId));
        stocktakeRepository.save(stocktake);
    }

    /** Called from ApprovalService when owner decides a stocktake task. */
    @Transactional
    public void applyApprovalDecision(Long stocktakeId, boolean approved, Long actorId) {
        Stocktake stocktake = requireWithActors(stocktakeId);
        if (stocktake.getStatus() != StocktakeStatus.COUNTING) {
            throw ApiException.conflict("Phiếu kiểm kê không còn ở trạng thái đang đếm");
        }
        if (!approved) {
            // Reject: keep COUNTING so staff can recount; pending approval is cleared by task status.
            return;
        }

        List<StocktakeLine> lines = stocktakeLineRepository.findByStocktake_Id(stocktakeId);
        for (StocktakeLine line : lines) {
            if (line.getCountedQty() == null) {
                continue;
            }
            int diff = line.getCountedQty() - line.getSystemQty();
            if (diff == 0 || line.getLot() == null || line.getLocation() == null) {
                continue;
            }
            Stock stock = stockRepository
                    .findByVariant_IdAndLot_IdAndLocation_Id(
                            line.getVariant().getId(), line.getLot().getId(), line.getLocation().getId())
                    .orElseThrow(() -> ApiException.notFound("Không tìm thấy dòng tồn để điều chỉnh"));
            stockService.adjustByStockId(
                    stock.getId(),
                    diff,
                    "Điều chỉnh theo kiểm kê " + stocktake.getCode(),
                    actorId);
        }
        stocktake.setStatus(StocktakeStatus.DONE);
        stocktake.setClosedAt(Instant.now());
        stocktakeRepository.save(stocktake);
    }

    private void openApproval(Stocktake stocktake, List<StocktakeLine> diffs, Long actorId) {
        approvalTaskRepository
                .findByRefTypeAndRefIdAndStatus(REF_TYPE, stocktake.getId(), ApprovalStatus.PENDING)
                .ifPresent(existing -> {
                    throw ApiException.conflict("Phiếu đã gửi duyệt");
                });

        AppUser requester = requireUser(actorId);
        int diffTotal = diffs.stream().mapToInt(l -> l.getCountedQty() - l.getSystemQty()).sum();
        ApprovalTask task = new ApprovalTask();
        task.setApprovalType(ApprovalType.STOCKTAKE);
        task.setRefType(REF_TYPE);
        task.setRefId(stocktake.getId());
        task.setTitle(stocktake.getCode()
                + ": chênh lệch "
                + diffs.size()
                + " dòng, tổng "
                + diffTotal
                + " khay");
        task.setStatus(ApprovalStatus.PENDING);
        task.setRequestedBy(requester);
        approvalTaskRepository.save(task);
    }

    private void assertCountingEditable(Stocktake stocktake) {
        if (stocktake.getStatus() != StocktakeStatus.COUNTING) {
            throw ApiException.conflict("Phiếu không còn ở trạng thái đang đếm");
        }
        if (hasPendingApproval(stocktake.getId())) {
            throw ApiException.conflict("Phiếu đang chờ duyệt, không thể sửa số đếm");
        }
    }

    private boolean hasPendingApproval(Long stocktakeId) {
        return approvalTaskRepository
                .findByRefTypeAndRefIdAndStatus(REF_TYPE, stocktakeId, ApprovalStatus.PENDING)
                .isPresent();
    }

    private StocktakeListItemResponse toListItem(Stocktake stocktake) {
        List<StocktakeLine> lines = stocktakeLineRepository.findByStocktake_Id(stocktake.getId());
        return StocktakeListItemResponse.from(stocktake, lines, hasPendingApproval(stocktake.getId()));
    }

    private StocktakeDetailResponse toDetail(Stocktake stocktake) {
        boolean multiWarehouse = warehouseRepository.count() > 1;
        List<StocktakeLine> lines = stocktakeLineRepository.findDetailedByStocktakeId(stocktake.getId());
        StocktakeListItemResponse header =
                StocktakeListItemResponse.from(stocktake, lines, hasPendingApproval(stocktake.getId()));
        List<StocktakeLineResponse> lineResponses =
                lines.stream().map(l -> StocktakeLineResponse.from(l, multiWarehouse)).toList();
        return StocktakeDetailResponse.from(header, lineResponses);
    }

    private Stocktake requireWithActors(Long id) {
        return stocktakeRepository
                .findWithActorsById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy phiếu kiểm kê"));
    }

    private AppUser requireUser(Long actorId) {
        if (actorId == null) {
            throw ApiException.forbidden("Cần đăng nhập");
        }
        return userRepository
                .findById(actorId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy người dùng"));
    }

    private String nextKkCode() {
        int next = stocktakeRepository
                .findMaxKkCode()
                .map(code -> {
                    try {
                        return Integer.parseInt(code.substring(3));
                    } catch (NumberFormatException | StringIndexOutOfBoundsException ex) {
                        return 0;
                    }
                })
                .orElse(0)
                + 1;
        return "KK-" + String.format("%04d", next);
    }

    private static StocktakeStatus parseStatus(String raw) {
        String value = Strings.trimToNull(raw);
        if (value == null) {
            return null;
        }
        try {
            return StocktakeStatus.fromCode(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
