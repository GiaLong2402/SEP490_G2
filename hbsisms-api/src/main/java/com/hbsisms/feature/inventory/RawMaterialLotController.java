package com.hbsisms.feature.inventory;

import com.hbsisms.feature.inventory.dto.RawLotDiscardRequest;
import com.hbsisms.feature.inventory.dto.RawMaterialLotResponse;
import com.hbsisms.feature.user.AppUser;
import com.hbsisms.feature.user.UserRepository;
import com.hbsisms.security.AuthUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Thao tác trên một lô nguyên liệu (dòng 54: bỏ lô hết hạn / hư hỏng). */
@RestController
@RequestMapping("/raw-material-lots")
@PreAuthorize("hasAuthority('inventory')")
public class RawMaterialLotController {

    private final RawMaterialStockService rawMaterialStockService;
    private final UserRepository userRepository;

    public RawMaterialLotController(RawMaterialStockService rawMaterialStockService, UserRepository userRepository) {
        this.rawMaterialStockService = rawMaterialStockService;
        this.userRepository = userRepository;
    }

    /** Bỏ phần còn lại của lô: số còn về 0, trừ tồn NVL, ghi sổ. Thủ kho / Chủ / Admin, không cần duyệt. */
    @PostMapping("/{id}/discard")
    @PreAuthorize("hasAuthority('inventory') and @roles.warehouseOps()")
    public RawMaterialLotResponse discard(
            @PathVariable Long id, @RequestBody RawLotDiscardRequest request, @AuthenticationPrincipal AuthUser actor) {
        AppUser user = actor == null ? null : userRepository.findById(actor.id()).orElse(null);
        RawMaterialLot lot = rawMaterialStockService.discard(
                id, request == null ? null : request.reason(), request == null ? null : request.note(), user);
        return RawMaterialLotResponse.from(lot);
    }
}
