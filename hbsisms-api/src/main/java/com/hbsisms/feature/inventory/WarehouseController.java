package com.hbsisms.feature.inventory;

import com.hbsisms.feature.inventory.dto.LocationRequest;
import com.hbsisms.feature.inventory.dto.WarehouseRequest;
import com.hbsisms.feature.inventory.dto.WarehouseResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasAuthority('inventory')")
public class WarehouseController {

    private final WarehouseService warehouseService;

    public WarehouseController(WarehouseService warehouseService) {
        this.warehouseService = warehouseService;
    }

    @GetMapping("/warehouses")
    public List<WarehouseResponse> list() {
        return warehouseService.list();
    }

    @PostMapping("/warehouses")
    @ResponseStatus(HttpStatus.CREATED)
    public WarehouseResponse create(@Valid @RequestBody WarehouseRequest request) {
        return warehouseService.create(request);
    }

    @PutMapping("/warehouses/{id}")
    public WarehouseResponse update(@PathVariable Long id, @Valid @RequestBody WarehouseRequest request) {
        return warehouseService.update(id, request);
    }

    @PostMapping("/warehouses/{warehouseId}/locations")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addLocation(
            @PathVariable Long warehouseId, @Valid @RequestBody LocationRequest request) {
        warehouseService.addLocation(warehouseId, request);
    }

    @PutMapping("/locations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateLocation(@PathVariable Long id, @Valid @RequestBody LocationRequest request) {
        warehouseService.updateLocation(id, request);
    }

    @DeleteMapping("/locations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteLocation(@PathVariable Long id) {
        warehouseService.deleteLocation(id);
    }
}
