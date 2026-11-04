package com.hbsisms.feature.inventory;

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

    public RawMaterialController(RawMaterialService rawMaterialService) {
        this.rawMaterialService = rawMaterialService;
    }

    @GetMapping
    public List<RawMaterialResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean activeOnly,
            @RequestParam(required = false) Boolean lowStock) {
        return rawMaterialService.list(q, activeOnly, lowStock);
    }

    @GetMapping("/{id}")
    public RawMaterialResponse get(@PathVariable Long id) {
        return rawMaterialService.get(id);
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
