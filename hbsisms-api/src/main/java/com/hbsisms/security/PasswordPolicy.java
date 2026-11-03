package com.hbsisms.security;

import java.nio.charset.StandardCharsets;

/** BCrypt only uses the first 72 bytes (not characters) of a password. */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_BYTES = 72;

    private PasswordPolicy() {
    }

    public static boolean exceedsMaxBytes(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES;
    }

    public static boolean isAcceptable(String password) {
        return password != null && password.length() >= MIN_LENGTH && !exceedsMaxBytes(password);
    }
}
