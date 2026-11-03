package com.hbsisms.feature.inventory;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/** Stocktake lifecycle. */
public enum StocktakeStatus {
    DRAFT,
    COUNTING,
    DONE,
    CANCELLED;

    @JsonValue
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }
}
