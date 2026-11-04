package com.hbsisms.feature.inventory.dto;

/** Result of POST /stocktakes/{id}/complete. */
public record StocktakeCompleteResponse(boolean needsApproval) {}
