package com.hbsisms.feature.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hbsisms.common.exception.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class OtpSendRateLimitServiceTest {

    private MutableClock clock;
    private OtpSendRateLimitService limiter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-11-28T00:00:00Z"));
        limiter = new OtpSendRateLimitService(clock);
    }

    @Test
    void allowsFiveSendsThenLocks() {
        String phone = "0901234567";
        assertEquals(4, limiter.acquireOrThrow(phone));
        assertEquals(3, limiter.acquireOrThrow(phone));
        assertEquals(2, limiter.acquireOrThrow(phone));
        assertEquals(1, limiter.acquireOrThrow(phone));
        assertEquals(0, limiter.acquireOrThrow(phone));

        ApiException ex = assertThrows(ApiException.class, () -> limiter.acquireOrThrow(phone));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatus());
        assertTrue(ex.getMessage().contains("quá nhiều"));
    }

    @Test
    void unlocksAfterLockDuration() {
        String phone = "0912345678";
        for (int i = 0; i < OtpSendRateLimitService.MAX_SENDS; i++) {
            limiter.acquireOrThrow(phone);
        }
        assertThrows(ApiException.class, () -> limiter.acquireOrThrow(phone));

        clock.advance(OtpSendRateLimitService.LOCK_MINUTES, ChronoUnit.MINUTES);
        assertEquals(4, limiter.acquireOrThrow(phone));
    }

    @Test
    void resetsWindowAfterFifteenMinutesWithoutHittingCap() {
        String phone = "0923456789";
        limiter.acquireOrThrow(phone);
        limiter.acquireOrThrow(phone);
        clock.advance(OtpSendRateLimitService.WINDOW_MINUTES, ChronoUnit.MINUTES);
        assertEquals(4, limiter.acquireOrThrow(phone));
    }

    /** Clock tăng được để test cửa sổ / khoá. */
    private static final class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(long amount, ChronoUnit unit) {
            instant = instant.plus(amount, unit);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return Clock.fixed(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
