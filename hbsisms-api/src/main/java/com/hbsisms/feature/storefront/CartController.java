package com.hbsisms.feature.storefront;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.storefront.dto.CartItemQtyRequest;
import com.hbsisms.feature.storefront.dto.CartItemRequest;
import com.hbsisms.feature.storefront.dto.CartResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/carts/current")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public CartResponse get(@RequestHeader(value = CartService.SESSION_HEADER, required = false) String raw) {
        return cartService.getOrCreate(parseSession(raw));
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.OK)
    public CartResponse addItem(
            @RequestHeader(value = CartService.SESSION_HEADER, required = false) String raw,
            @Valid @RequestBody CartItemRequest request) {
        return cartService.addItem(parseSession(raw), request.variantId(), request.qty());
    }

    @PutMapping("/items/{itemId}")
    public CartResponse updateItem(
            @RequestHeader(value = CartService.SESSION_HEADER, required = false) String raw,
            @PathVariable Long itemId,
            @Valid @RequestBody CartItemQtyRequest request) {
        return cartService.updateItemQty(parseSession(raw), itemId, request.qty());
    }

    @DeleteMapping("/items/{itemId}")
    public CartResponse removeItem(
            @RequestHeader(value = CartService.SESSION_HEADER, required = false) String raw,
            @PathVariable Long itemId) {
        return cartService.removeItem(parseSession(raw), itemId);
    }

    @DeleteMapping("/items")
    public CartResponse clear(@RequestHeader(value = CartService.SESSION_HEADER, required = false) String raw) {
        return cartService.clear(parseSession(raw));
    }

    private static UUID parseSession(String raw) {
        if (raw == null || raw.isBlank()) {
            throw ApiException.badRequest("Thiếu header " + CartService.SESSION_HEADER);
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Header " + CartService.SESSION_HEADER + " không hợp lệ");
        }
    }
}
