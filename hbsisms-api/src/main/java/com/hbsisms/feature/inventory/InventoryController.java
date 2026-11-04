package com.hbsisms.feature.inventory;

import com.hbsisms.common.web.PageRequests;
import com.hbsisms.common.web.PageResponse;
import com.hbsisms.feature.inventory.dto.AdjustStockRequest;
import com.hbsisms.feature.inventory.dto.InventorySummaryResponse;
import com.hbsisms.feature.inventory.dto.LedgerRowResponse;
import com.hbsisms.feature.inventory.dto.LocationOptionResponse;
import com.hbsisms.feature.inventory.dto.LotStockRowResponse;
import com.hbsisms.feature.inventory.dto.MoveStockRequest;
import com.hbsisms.feature.inventory.dto.ReceiveStockRequest;
import com.hbsisms.feature.inventory.dto.VariantStockRowResponse;
import com.hbsisms.feature.system.AuditLogService;
import com.hbsisms.security.AuthUser;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasAuthority('inventory')")
public class InventoryController {

    private final InventoryQueryService inventoryQueryService;
    private final StockService stockService;
    private final InventoryReceiveService inventoryReceiveService;
    private final AuditLogService auditLogService;

    public InventoryController(
            InventoryQueryService inventoryQueryService,
            StockService stockService,
            InventoryReceiveService inventoryReceiveService,
            AuditLogService auditLogService) {
        this.inventoryQueryService = inventoryQueryService;
        this.stockService = stockService;
        this.inventoryReceiveService = inventoryReceiveService;
        this.auditLogService = auditLogService;
    }

    @GetMapping("/inventory/summary")
    public InventorySummaryResponse summary() {
        return inventoryQueryService.summary();
    }

    @GetMapping("/inventory/variants")
    public PageResponse<VariantStockRowResponse> variants(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long locationId,
            @RequestParam(required = false, defaultValue = "false") boolean lowOnly,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "" + PageRequests.DEFAULT_PAGE_SIZE) int pageSize) {
        return inventoryQueryService.variants(q, locationId, lowOnly, page, pageSize);
    }

    @GetMapping("/inventory/lots")
    public PageResponse<LotStockRowResponse> lots(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long locationId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "" + PageRequests.DEFAULT_PAGE_SIZE) int pageSize) {
        return inventoryQueryService.lots(q, locationId, page, pageSize);
    }

    @GetMapping("/inventory/near-expiry")
    public PageResponse<LotStockRowResponse> nearExpiry(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "" + PageRequests.DEFAULT_PAGE_SIZE) int pageSize) {
        return inventoryQueryService.nearExpiry(q, page, pageSize);
    }

    @GetMapping("/inventory/ledger")
    public PageResponse<LedgerRowResponse> ledger(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String entryType,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "" + PageRequests.DEFAULT_PAGE_SIZE) int pageSize) {
        return inventoryQueryService.ledger(q, entryType, from, to, page, pageSize);
    }

    @GetMapping("/locations")
    public List<LocationOptionResponse> locations() {
        return inventoryQueryService.activeLocations();
    }

    @PostMapping("/inventory/moves")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void move(
            @Valid @RequestBody MoveStockRequest request, @AuthenticationPrincipal AuthUser actor) {
        stockService.moveByStockId(
                request.stockId(), request.toLocationId(), request.qty(), actor == null ? null : actor.id());
    }

    @PostMapping("/inventory/adjusts")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void adjust(
            @Valid @RequestBody AdjustStockRequest request, @AuthenticationPrincipal AuthUser actor) {
        Long actorId = actor == null ? null : actor.id();
        stockService.adjustByStockId(request.stockId(), request.qtyDelta(), request.note(), actorId);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("qtyDelta", request.qtyDelta());
        detail.put("note", request.note());
        auditLogService.record(actorId, "adjust", "stock", request.stockId(), detail);
    }

    @PostMapping("/inventory/receives")
    @ResponseStatus(HttpStatus.CREATED)
    public void receive(
            @Valid @RequestBody ReceiveStockRequest request, @AuthenticationPrincipal AuthUser actor) {
        inventoryReceiveService.receive(request, actor == null ? null : actor.id());
    }
}
