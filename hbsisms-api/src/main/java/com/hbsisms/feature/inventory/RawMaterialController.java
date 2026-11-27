package com.hbsisms.feature.inventory;

import com.hbsisms.feature.costing.CostingService;
import com.hbsisms.feature.inventory.dto.RawMaterialAvgCostResponse;
import com.hbsisms.feature.inventory.dto.RawMaterialLotResponse;
import com.hbsisms.feature.inventory.dto.RawMaterialRequest;
import com.hbsisms.feature.inventory.dto.RawMaterialResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/raw-materials")
@PreAuthorize("hasAuthority('inventory')")
public class RawMaterialController {

    private final RawMaterialService rawMaterialService;
    private final RawMaterialStockService rawMaterialStockService;
    private final CostingService costingService;

    public RawMaterialController(
            RawMaterialService rawMaterialService,
            RawMaterialStockService rawMaterialStockService,
            CostingService costingService) {
        this.rawMaterialService = rawMaterialService;
        this.rawMaterialStockService = rawMaterialStockService;
        this.costingService = costingService;
    }

    @GetMapping
    public List<RawMaterialResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean activeOnly,
            @RequestParam(required = false) Boolean lowStock,
            @RequestParam(required = false) Long storageLocationId) {
        return rawMaterialService.list(q, activeOnly, lowStock, storageLocationId);
    }

    @GetMapping("/{id}")
    public RawMaterialResponse get(@PathVariable Long id) {
        return rawMaterialService.get(id);
    }

    /** Lô NVL theo FEFO (HSD gần nhất trước); {@code availableOnly=true} chỉ lô còn tồn. */
    @GetMapping("/{id}/lots")
    public List<RawMaterialLotResponse> lots(
            @PathVariable Long id, @RequestParam(defaultValue = "false") boolean availableOnly) {
        return rawMaterialStockService.lots(id, availableOnly).stream().map(RawMaterialLotResponse::from).toList();
    }

    /** ĐG WAC NVL hiện hành (xuất SX định giá theo số này; chưa có → avgUnitCost null). */
    @GetMapping("/{id}/avg-unit-cost")
    public RawMaterialAvgCostResponse avgUnitCost(@PathVariable Long id) {
        rawMaterialService.get(id); // 404 nếu không có
        return costingService
                .currentRawAvgUnitCost(id)
                .map(c -> new RawMaterialAvgCostResponse(id, c, "WAC"))
                .orElseGet(() -> new RawMaterialAvgCostResponse(id, null, "NONE"));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RawMaterialResponse create(@Valid @RequestBody RawMaterialRequest request) {
        return rawMaterialService.create(request);
    }

    @PutMapping("/{id}")
    public RawMaterialResponse update(@PathVariable Long id, @Valid @RequestBody RawMaterialRequest request) {
        return rawMaterialService.update(id, request);
    }
}
