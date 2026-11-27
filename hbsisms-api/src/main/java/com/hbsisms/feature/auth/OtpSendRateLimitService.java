package com.hbsisms.feature.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.hbsisms.common.exception.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/**
 * Giới hạn gửi OTP theo SĐT trước khi client gọi Firebase.
 * Tối đa {@value #MAX_SENDS} lần / {@value #WINDOW_MINUTES} phút → khoá {@value #LOCK_MINUTES} phút.
 * In-memory (Caffeine), mỗi instance API tự đếm.
 */
@Component
public class OtpSendRateLimitService {

    public static final int MAX_SENDS = 5;
    public static final int WINDOW_MINUTES = 15;
    public static final int LOCK_MINUTES = 30;

    private static final Duration WINDOW = Duration.ofMinutes(WINDOW_MINUTES);
    private static final Duration LOCK = Duration.ofMinutes(LOCK_MINUTES);
    private static final long MAX_TRACKED_KEYS = 100_000;

    private final Cache<String, State> states;
    private final Clock clock;

    public OtpSendRateLimitService(Clock clock) {
        this.clock = clock;
        this.states = Caffeine.newBuilder()
                .maximumSize(MAX_TRACKED_KEYS)
                .expireAfterWrite(LOCK.plus(WINDOW))
                .ticker(() -> TimeUnit.MILLISECONDS.toNanos(clock.millis()))
                .build();
    }

    /**
     * Ghi nhận một lần gửi OTP sắp diễn ra.
     *
     * @return số lần còn được gửi trong cửa sổ hiện tại (sau lần này)
     */
    public int acquireOrThrow(String phoneKey) {
        Instant now = clock.instant();
        AtomicInteger remaining = new AtomicInteger(0);
        AtomicReference<ApiException> denied = new AtomicReference<>();

        states.asMap().compute(phoneKey, (k, current) -> {
            if (current != null && current.lockedUntil() != null && now.isBefore(current.lockedUntil())) {
                long seconds = Math.max(1, Duration.between(now, current.lockedUntil()).getSeconds());
                denied.set(locked(seconds));
                return current;
            }

            State state = current;
            if (state == null
                    || state.windowStart() == null
                    || Duration.between(state.windowStart(), now).compareTo(WINDOW) >= 0) {
                state = new State(0, now, null);
            }

            // Đã đủ 5 lần trong cửa sổ → khoá 30 phút, từ chối lần này.
            if (state.count() >= MAX_SENDS) {
                Instant until = now.plus(LOCK);
                denied.set(locked(LOCK.getSeconds()));
                return new State(state.count(), state.windowStart(), until);
            }

            int next = state.count() + 1;
            remaining.set(MAX_SENDS - next);
            return new State(next, state.windowStart(), null);
        });

        ApiException ex = denied.get();
        if (ex != null) {
            throw ex;
        }
        return remaining.get();
    }

    private static ApiException locked(long retryAfterSeconds) {
        long minutes = Math.max(1, (retryAfterSeconds + 59) / 60);
        return ApiException.tooManyRequests(
                "Số điện thoại này đã gửi OTP quá nhiều lần. Vui lòng thử lại sau khoảng "
                        + minutes
                        + " phút.");
    }

    private record State(int count, Instant windowStart, Instant lockedUntil) {}
}
