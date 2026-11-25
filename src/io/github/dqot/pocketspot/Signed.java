package io.github.dqot.pocketspot;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Signed requests from the app to the ADB helper over 127.0.0.1, so other apps on the device
 * cannot drive it:  "req SLOT NONCE BODYHEX MAC"
 */
final class Signed {
    private static final long SLOT_MS = 30_000;
    private static final int MAX_SKEW_SLOTS = 4;   // two clocks may differ by up to 2 minutes

    private Signed() {
    }

    static int slot() {
        return (int) (System.currentTimeMillis() / SLOT_MS);
    }

    static boolean fresh(int slot) {
        return Math.abs((System.currentTimeMillis() / SLOT_MS) - slot) <= MAX_SKEW_SLOTS;
    }

    static String request(String body, byte[] key) {
        int slot = slot();
        String nonce = Integer.toHexString(new SecureRandom().nextInt());
        String bodyHex = hex(body.getBytes(StandardCharsets.UTF_8));
        return "req " + slot + " " + nonce + " " + bodyHex + " " + mac(slot, nonce, bodyHex, key);
    }

    /** Returns [nonce, body] when the request is fresh and signed with this key; null otherwise. */
    static String[] open(String line, byte[] key) {
        if (key == null || line == null) return null;
        String[] p = line.split(" ");
        if (p.length != 5 || !p[0].equals("req")) return null;
        int slot;
        try {
            slot = Integer.parseInt(p[1]);
        } catch (NumberFormatException e) {
            return null;
        }
        if (!fresh(slot)) return null;
        String expected = mac(slot, p[2], p[3], key);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), p[4].getBytes(StandardCharsets.UTF_8))) {
            return null;
        }
        byte[] body = unhex(p[3]);
        return body == null ? null : new String[]{p[2], new String(body, StandardCharsets.UTF_8)};
    }

    private static String mac(int slot, String nonce, String bodyHex, byte[] key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] out = mac.doFinal(("req|" + slot + "|" + nonce + "|" + bodyHex).getBytes(StandardCharsets.UTF_8));
            return hex(out).substring(0, 32);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte x : bytes) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    static byte[] unhex(String s) {
        if (s == null || s.length() % 2 != 0) return null;
        byte[] out = new byte[s.length() / 2];
        try {
            for (int i = 0; i < out.length; i++) out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        } catch (NumberFormatException e) {
            return null;
        }
        return out;
    }
}
