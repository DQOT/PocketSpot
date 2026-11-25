package io.github.dqot.pocketspot;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * The BLE message the tablet sends. It rides in manufacturer data (test company id 0xFFFF):
 *   'P' 'S' | version<<4 | command | 30 s time slot (4) | nonce (4) | HMAC-SHA256 first 8 bytes
 * Signed with the pairing key, so a phone acts only on its own tablet.
 */
final class Beacon {
    static final int COMPANY_ID = 0xFFFF;
    static final byte[] MAGIC = {'P', 'S'};
    static final int CMD_ON = 1;
    static final int CMD_OFF = 2;
    private static final int VERSION = 1;
    private static final int LENGTH = 2 + 1 + 4 + 4 + 8;

    static final class Message {
        final int command;
        final int nonce;

        Message(int command, int nonce) {
            this.command = command;
            this.nonce = nonce;
        }
    }

    private Beacon() {
    }

    static byte[] build(int command, byte[] key) {
        ByteBuffer b = ByteBuffer.allocate(LENGTH);
        b.put(MAGIC).put((byte) ((VERSION << 4) | command));
        b.putInt(Signed.slot());
        b.putInt(new SecureRandom().nextInt());
        byte[] out = b.array();
        System.arraycopy(mac(out, key), 0, out, LENGTH - 8, 8);
        return out;
    }

    /** Returns the message if it is fresh and signed with this key; null otherwise. */
    static Message verify(byte[] data, byte[] key) {
        if (key == null || data == null || data.length != LENGTH) return null;
        if (data[0] != MAGIC[0] || data[1] != MAGIC[1] || ((data[2] >> 4) & 0x0F) != VERSION) return null;
        byte[] expected = Arrays.copyOf(mac(data, key), 8);
        if (!MessageDigest.isEqual(expected, Arrays.copyOfRange(data, LENGTH - 8, LENGTH))) return null;
        ByteBuffer b = ByteBuffer.wrap(data);
        if (!Signed.fresh(b.getInt(3))) return null;
        return new Message(data[2] & 0x0F, b.getInt(7));
    }

    private static byte[] mac(byte[] message, byte[] key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            mac.update(message, 0, LENGTH - 8);
            return mac.doFinal();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
