package com.gathin.tvhome;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Kids mode's hour notices and the PIN that guards leaving it (usage_limit). No Android types; tested on the JVM. */
final class UsageLimit {
    static final int PIN_DIGITS = 4;
    /** Hours of use after which the kids' time is announced: one notice at 1 h and one at 2 h. */
    static final int MAX_NOTICE_HOURS = 2;

    private UsageLimit() { }

    /** How many whole hours (up to {@link #MAX_NOTICE_HOURS}) the kids have used today. */
    static int hoursReached(long usedSeconds) {
        return (int) Math.max(0, Math.min(MAX_NOTICE_HOURS, usedSeconds / 3600));
    }

    static boolean validPin(String pin) {
        if (pin == null || pin.length() != PIN_DIGITS) return false;
        for (int i = 0; i < pin.length(); i++) if (pin.charAt(i) < '0' || pin.charAt(i) > '9') return false;
        return true;
    }

    /** The PIN is kept as a salted SHA-256, never in clear. */
    static String hash(String pin, String salt) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest((salt + ":" + pin).getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : digest) out.append(String.format("%02x", b));
            return out.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static boolean matches(String pin, String salt, String storedHash) {
        return validPin(pin) && storedHash != null && hash(pin, salt).equals(storedHash);
    }
}
