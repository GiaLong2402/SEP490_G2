package com.hbsisms.feature.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.hbsisms.security.SecurityProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoginAttemptServiceTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    private MutableClock clock;
    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(START);
        SecurityProperties properties = new SecurityProperties(
                new SecurityProperties.Jwt("x".repeat(32), "test", Duration.ofHours(1)),
                new SecurityProperties.Cors(List.of("http://localhost")),
                new SecurityProperties.Login(3, Duration.ofMinutes(15), true));
        service = new LoginAttemptService(properties, clock);
    }

    @Test
    void blocksAfterMaxAttempts() {
        assertThat(service.tryAcquire("bob")).isTrue();
        assertThat(service.tryAcquire("bob")).isTrue();
        assertThat(service.tryAcquire("bob")).isTrue();

        assertThat(service.tryAcquire("bob")).isFalse();
        assertThat(service.tryAcquire("alice")).isTrue();
    }

    @Test
    void unlocksAfterLockDurationWithAFreshWindow() {
        service.tryAcquire("bob");
        service.tryAcquire("bob");
        service.tryAcquire("bob");
        assertThat(service.tryAcquire("bob")).isFalse();

        clock.advance(Duration.ofMinutes(15));

        assertThat(service.tryAcquire("bob")).isTrue();
        assertThat(service.tryAcquire("bob")).isTrue();
    }

    @Test
    void successClearsAttempts() {
        service.tryAcquire("bob");
        service.tryAcquire("bob");
        service.recordSuccess("bob");

        assertThat(service.tryAcquire("bob")).isTrue();
        assertThat(service.tryAcquire("bob")).isTrue();
        assertThat(service.tryAcquire("bob")).isTrue();
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}
