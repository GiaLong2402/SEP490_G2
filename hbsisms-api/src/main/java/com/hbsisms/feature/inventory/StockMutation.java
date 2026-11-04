package com.hbsisms.feature.inventory;

/**
 * Command for a single stock mutation. {@code qty} is always positive; sign is applied by
 * {@link StockService} according to the operation.
 */
public record StockMutation(
        long variantId,
        long lotId,
        long locationId,
        int qty,
        Long toLocationId,
        String refType,
        Long refId,
        String note,
        Long actorId) {

    public static StockMutation of(
            long variantId,
            long lotId,
            long locationId,
            int qty,
            String refType,
            Long refId,
            String note,
            Long actorId) {
        return new StockMutation(variantId, lotId, locationId, qty, null, refType, refId, note, actorId);
    }

    public static StockMutation move(
            long variantId,
            long lotId,
            long fromLocationId,
            long toLocationId,
            int qty,
            String note,
            Long actorId) {
        return new StockMutation(
                variantId, lotId, fromLocationId, qty, toLocationId, null, null, note, actorId);
    }
}
