package com.hbsisms.feature.inventory;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hbsisms.common.exception.ApiException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class RawQuantityRulesTest {

    @Test
    void continuousVsPiece() {
        assertTrue(RawQuantityRules.isContinuousUnit("kg"));
        assertTrue(RawQuantityRules.isContinuousUnit("G"));
        assertFalse(RawQuantityRules.isPieceUnit("kg"));
        assertTrue(RawQuantityRules.isPieceUnit("túi"));
        assertTrue(RawQuantityRules.isPieceUnit("cai"));
    }

    @Test
    void rejectFractionalPiece() {
        assertThrows(
                ApiException.class,
                () -> RawQuantityRules.requireWholeIfPiece("túi", new BigDecimal("23.446"), "Túi hút chân không"));
        assertDoesNotThrow(() -> RawQuantityRules.requireWholeIfPiece("túi", new BigDecimal("24"), "Túi"));
        assertDoesNotThrow(() -> RawQuantityRules.requireWholeIfPiece("kg", new BigDecimal("12.5"), "Tôm"));
        assertDoesNotThrow(() -> RawQuantityRules.requireWholeIfPiece("túi", BigDecimal.ZERO, "Túi"));
    }

    @Test
    void wholeUsableFloors() {
        assertEquals(0, RawQuantityRules.wholeUsable(new BigDecimal("23.446")).compareTo(new BigDecimal("23")));
        assertEquals(0, RawQuantityRules.wholeUsable(new BigDecimal("0.9")).compareTo(BigDecimal.ZERO));
    }

    @Test
    void hasFractionDetectsDust() {
        assertTrue(RawQuantityRules.hasFraction(new BigDecimal("23.446")));
        assertTrue(RawQuantityRules.hasFraction(new BigDecimal("23.446").setScale(3)));
        assertFalse(RawQuantityRules.hasFraction(new BigDecimal("23.000").setScale(3)));
        assertFalse(RawQuantityRules.hasFraction(new BigDecimal("100")));
    }
}
