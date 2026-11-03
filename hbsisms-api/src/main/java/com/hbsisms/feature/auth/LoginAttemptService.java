package com.hbsisms.feature.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.hbsisms.security.SecurityProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * Limits login attempts per username. An attempt is claimed atomically <em>before</em> the password
 * is checked, so parallel guesses cannot all slip past the limit; a successful login clears it.
 * Entries expire {@code lockDuration} after the last attempt. In-memory, so it is per instance;
 * move it to Redis if the API is scaled horizontally.
 */
@Component
public class LoginAttemptService {

    private static final long MAX_TRACKED_KEYS = 100_000;

    private final Cache<String, Attempts> attempts;
    private final SecurityProperties.Login settings;
    private final Clock clock;

    public LoginAttemptService(SecurityProperties properties, Clock clock) {
        this.settings = properties.login();
        this.clock = clock;
        this.attempts = Caffeine.newBuilder()
                .maximumSize(MAX_TRACKED_KEYS)
                .expireAfterWrite(settings.lockDuration())
                .ticker(() -> TimeUnit.MILLISECONDS.toNanos(clock.millis()))
                .build();
    }

    /** Claims one attempt for {@code key}; returns {@code false} if the key is currently locked. */
    public boolean tryAcquire(String key) {
        Instant now = clock.instant();
        boolean[] allowed = {false};
        attempts.asMap().compute(key, (k, current) -> {
            if (current != null && current.isLockedAt(now)) {
                return current;
            }
            allowed[0] = true;
            // A previous lock that has run out starts a fresh window.
            int count = current == null || current.lockedUntil() != null ? 1 : current.count() + 1;
            Instant lockedUntil = count >= settings.maxFailedAttempts() ? now.plus(settings.lockDuration()) : null;
            return new Attempts(count, lockedUntil);
        });
        return allowed[0];
    }

    public void recordSuccess(String key) {
        attempts.invalidate(key);
    }

    public long lockMinutes() {
        return settings.lockDuration().toMinutes();
    }

    private record Attempts(int count, Instant lockedUntil) {

        boolean isLockedAt(Instant now) {
            return lockedUntil != null && now.isBefore(lockedUntil);
        }
    }
}
