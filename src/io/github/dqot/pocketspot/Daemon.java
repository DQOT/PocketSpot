package io.github.dqot.pocketspot;

import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The ADB helper: runs as the shell user, started over adb with app_process (see README).
 * The app talks to it over 127.0.0.1:PORT (SELinux blocks app-to-shell local sockets).
 * Requests are signed with the pairing key, which the helper reads from the app's
 * {@link KeyProvider}; the provider answers only the shell user.
 */
public final class Daemon {
    static final int PORT = 47321;
    private static final long KEY_RETRY_MS = 5_000;
    private static final ArrayDeque<String> seen = new ArrayDeque<>();
    private static byte[] key;
    private static long keyFetchedAt;

    private Daemon() {
    }

    public static void main(String[] args) throws Exception {
        ServerSocket server;
        try {
            server = new ServerSocket(PORT, 8, InetAddress.getByName("127.0.0.1"));
        } catch (java.io.IOException e) {
            System.err.println("already running");
            System.exit(1);
            return;
        }
        Log.i(PrivilegedOps.TAG, "ADB helper started on port " + PORT);
        while (true) {
            Socket client = server.accept();
            new Thread(() -> serve(client), "client").start();
        }
    }

    private static void serve(Socket client) {
        try (Socket s = client) {
            s.setSoTimeout(5_000);
            String line = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8)).readLine();
            String reply = handle(line);
            Log.i(PrivilegedOps.TAG, "request -> " + reply);
            s.getOutputStream().write((reply + "\n").getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            Log.w(PrivilegedOps.TAG, "client failed", e);
        }
    }

    private static String handle(String line) {
        if (line == null) return "empty";
        if (line.equals("ping")) return "pong";
        String[] req = Signed.open(line, key(false));
        if (req == null) req = Signed.open(line, key(true));   // the pairing code may have changed
        if (req == null) return "rejected";
        if (!firstTime(req[0])) return "duplicate";
        String[] body = req[1].split("\n", -1);
        switch (body[0]) {
            case "tether":
                return PrivilegedOps.tether(body.length > 1 && body[1].equals("on"));
            case "connect":
                return body.length == 3 ? PrivilegedOps.connectSoon(body[1], body[2]) : "bad request";
            case "cancel":
                return PrivilegedOps.cancelConnect();
            case "ssid":
                return "ssid " + PrivilegedOps.connectedSsid();
            default:
                return "unknown";
        }
    }

    private static synchronized boolean firstTime(String nonce) {
        if (seen.contains(nonce)) return false;
        seen.addFirst(nonce);
        while (seen.size() > 64) seen.removeLast();
        return true;
    }

    /** The pairing key, read from the app with the content command (only the shell user may). */
    private static synchronized byte[] key(boolean refresh) {
        long now = System.currentTimeMillis();
        if (key != null && !refresh) return key;
        if (refresh && now - keyFetchedAt < KEY_RETRY_MS) return key;
        keyFetchedAt = now;
        try {
            Process p = new ProcessBuilder("content", "call", "--uri", "content://" + KeyProvider.AUTHORITY,
                    "--method", KeyProvider.METHOD).redirectErrorStream(true).start();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String l;
                while ((l = r.readLine()) != null) sb.append(l);
            }
            p.waitFor();
            Matcher m = Pattern.compile("key=([0-9a-f]{64})").matcher(sb);
            key = m.find() ? Signed.unhex(m.group(1)) : null;
        } catch (Exception e) {
            Log.w(PrivilegedOps.TAG, "key read failed", e);
        }
        return key;
    }
}
