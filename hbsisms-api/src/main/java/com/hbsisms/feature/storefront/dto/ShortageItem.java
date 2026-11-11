package com.hbsisms.feature.storefront.dto;

/** One variant that cannot fulfill requested qty at checkout. */
public record ShortageItem(Long variantId, int requestedQty, int availableQty) {
}
