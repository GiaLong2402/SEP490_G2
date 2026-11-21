package com.hbsisms.feature.catalog;

import com.hbsisms.feature.catalog.dto.UnitOfMeasureRequest;
import com.hbsisms.feature.catalog.dto.UnitOfMeasureResponse;
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
@RequestMapping("/units")
@PreAuthorize("hasAnyAuthority('products','inventory')")
public class UnitOfMeasureController {

    private final UnitOfMeasureService unitService;

    public UnitOfMeasureController(UnitOfMeasureService unitService) {
        this.unitService = unitService;
    }

    @GetMapping
    public List<UnitOfMeasureResponse> list(@RequestParam(required = false) Boolean activeOnly) {
        return unitService.list(activeOnly);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UnitOfMeasureResponse create(@Valid @RequestBody UnitOfMeasureRequest request) {
        return unitService.create(request);
    }

    @PutMapping("/{id}")
    public UnitOfMeasureResponse update(@PathVariable Long id, @Valid @RequestBody UnitOfMeasureRequest request) {
        return unitService.update(id, request);
    }
}
