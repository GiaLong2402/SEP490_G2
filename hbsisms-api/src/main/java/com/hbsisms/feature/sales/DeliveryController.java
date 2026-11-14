package com.hbsisms.feature.sales;

import com.hbsisms.feature.sales.dto.DeliveryUpdateRequest;
import com.hbsisms.security.AuthUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Delivery legs: cập nhật trạng thái / phân công (FE {@code /deliveries}). */
@RestController
@RequestMapping("/deliveries")
@PreAuthorize("hasAnyAuthority('orders', 'inventory')")
public class DeliveryController {

    private final DeliveryService deliveryService;

    public DeliveryController(DeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @PatchMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void update(
            @PathVariable Long id,
            @Valid @RequestBody DeliveryUpdateRequest request,
            @AuthenticationPrincipal AuthUser actor) {
        deliveryService.update(id, request, actor == null ? null : actor.id());
    }
}
