package io.github.dqot.pocketspot;

import android.content.Context;
import android.content.SharedPreferences;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * The pairing code both devices share. The phone makes a random one; the user types the same
 * code on the tablet (or sets their own on both). It is stored, so it survives reboots.
 * The signing key is derived from it.
 */
final class Pairing {
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";   // no 0/O, 1/I
    static final int MIN_LENGTH = 8;

    private Pairing() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("pairing", Context.MODE_PRIVATE);
    }

    static String code(Context c) {
        return prefs(c).getString("code", "");
    }

    /** Saves a code. Returns false when it is too short to be safe. */
    static boolean setCode(Context c, String code) {
        String n = normalize(code);
        if (n.length() < MIN_LENGTH) return false;
        prefs(c).edit().putString("code", format(n)).commit();
        return true;
    }

    static String generate() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 16; i++) sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        return format(sb.toString());
    }

    /** The HMAC key, or null when no code is set yet. */
    static byte[] key(Context c) {
        String n = normalize(code(c));
        if (n.length() < MIN_LENGTH) return null;
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(("PocketSpot1:" + n).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static String normalize(String code) {
        StringBuilder sb = new StringBuilder();
        for (char ch : code.toUpperCase(java.util.Locale.ROOT).toCharArray()) {
            if ((ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9')) sb.append(ch);
        }
        return sb.toString();
    }

    private static String format(String n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n.length(); i++) {
            if (i > 0 && i % 4 == 0) sb.append('-');
            sb.append(n.charAt(i));
        }
        return sb.toString();
    }
}
