package io.github.dqot.pocketspot;

import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * What needs the shell user's permissions. Runs inside the ADB helper ({@link Daemon}) or the
 * Shizuku helper ({@link ShizukuService}), never in the app process.
 */
final class PrivilegedOps {
    static final String TAG = "PocketSpotHelper";
    private static final int TETHERING_WIFI = 0;
    private static final String CALLER = "com.android.shell";
    private static final long CONNECT_WAIT_MS = 45_000;
    private static Thread connecting;

    private PrivilegedOps() {
    }

    /** Starts or stops Wi-Fi tethering with the hotspot settings saved on the phone. Returns the result code. */
    static String tether(boolean on) {
        try {
            IBinder b = (IBinder) Class.forName("android.os.ServiceManager")
                    .getMethod("getService", String.class).invoke(null, "tethering");
            Object conn = Class.forName("android.net.ITetheringConnector$Stub")
                    .getMethod("asInterface", IBinder.class).invoke(null, b);
            CountDownLatch done = new CountDownLatch(1);
            int[] result = {-1};
            Binder callback = new Binder() {
                @Override
                protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
                    if (code != IBinder.FIRST_CALL_TRANSACTION) return false;
                    data.enforceInterface("android.net.IIntResultListener");
                    result[0] = data.readInt();
                    done.countDown();
                    return true;
                }
            };
            Class<?> listenerType = Class.forName("android.net.IIntResultListener");
            Object listener = Class.forName("android.net.IIntResultListener$Stub")
                    .getMethod("asInterface", IBinder.class).invoke(null, callback);
            // Android 12+ added an attribution tag argument; Android 11 has the shorter form.
            if (on) {
                Class<?> builderType = Class.forName("android.net.TetheringManager$TetheringRequest$Builder");
                Object builder = builderType.getConstructor(int.class).newInstance(TETHERING_WIFI);
                builderType.getMethod("setShouldShowEntitlementUi", boolean.class).invoke(builder, false);
                Object request = builderType.getMethod("build").invoke(builder);
                Object parcel = request.getClass().getMethod("getParcel").invoke(request);
                Class<?> parcelType = Class.forName("android.net.TetheringRequestParcel");
                try {
                    conn.getClass().getMethod("startTethering", parcelType, String.class, String.class, listenerType)
                            .invoke(conn, parcel, CALLER, null, listener);
                } catch (NoSuchMethodException e) {
                    conn.getClass().getMethod("startTethering", parcelType, String.class, listenerType)
                            .invoke(conn, parcel, CALLER, listener);
                }
            } else {
                try {
                    conn.getClass().getMethod("stopTethering", int.class, String.class, String.class, listenerType)
                            .invoke(conn, TETHERING_WIFI, CALLER, null, listener);
                } catch (NoSuchMethodException e) {
                    conn.getClass().getMethod("stopTethering", int.class, String.class, listenerType)
                            .invoke(conn, TETHERING_WIFI, CALLER, listener);
                }
            }
            return done.await(15, TimeUnit.SECONDS) ? String.valueOf(result[0]) : "timeout";
        } catch (Throwable t) {
            Log.e(TAG, "tethering call failed", t);
            return "error " + t;
        }
    }

    /**
     * Tablet side: waits (in the background) for the phone's hotspot to appear, then joins it,
     * even when the tablet is on another saved network.
     */
    static synchronized String connectSoon(String ssid, String password) {
        cancelConnect();
        connecting = new Thread(() -> Log.i(TAG, "connect " + ssid + " -> " + connect(ssid, password)), "connect");
        connecting.start();
        return "started";
    }

    static synchronized String cancelConnect() {
        if (connecting != null && connecting.isAlive()) connecting.interrupt();
        connecting = null;
        return "cancelled";
    }

    /** The SSID the device is connected to, or "" when none. */
    static String connectedSsid() {
        String status = exec("cmd", "wifi", "status");
        int at = status.indexOf("connected to \"");
        if (at < 0) return "";
        int start = at + "connected to \"".length();
        int end = status.indexOf('"', start);
        return end > start ? status.substring(start, end) : "";
    }

    /**
     * The phone hears the call in about 1.5 s and has the hotspot up at about 4 s, but a full
     * Wi-Fi scan then needs several more seconds to list it. So start joining at 3 s without
     * waiting for a scan: the Wi-Fi chip then probes for that one name and joins as soon as it
     * answers. If that has not worked within 8 s, fall back to scanning and join when it shows up.
     */
    private static String connect(String ssid, String password) {
        long start = System.currentTimeMillis();
        long joinedAt = 0;
        long lastScan = 0;
        int attempts = 0;
        String security = password.isEmpty() ? "open" : "wpa2";   // wpa2 also joins WPA2/WPA3 hotspots
        while (System.currentTimeMillis() - start < CONNECT_WAIT_MS) {
            if (Thread.currentThread().isInterrupted()) return "cancelled";
            long now = System.currentTimeMillis();
            if (ssid.equals(connectedSsid())) {
                return attempts == 0 ? "already connected"
                        : "connected after " + (now - start) + " ms, " + attempts + " attempt(s), " + security;
            }
            String flags = scanFlags(exec("cmd", "wifi", "list-scan-results"), ssid);
            if (flags != null && !password.isEmpty()) {
                security = flags.contains("PSK") ? "wpa2" : flags.contains("SAE") ? "wpa3" : "wpa2";
            }
            boolean firstTry = attempts == 0 && (flags != null || now - start >= 3_000);
            boolean retry = attempts > 0 && flags != null && now - joinedAt >= 8_000;
            if (firstTry || retry) {
                if (password.isEmpty()) exec("cmd", "wifi", "connect-network", ssid, security);
                else exec("cmd", "wifi", "connect-network", ssid, security, password);
                attempts++;
                joinedAt = now;
            } else if (attempts > 0 && now - joinedAt >= 8_000 && now - lastScan >= 4_000) {
                exec("cmd", "wifi", "start-scan");
                lastScan = now;
            }
            try {
                Thread.sleep(400);
            } catch (InterruptedException e) {
                return "cancelled";
            }
        }
        return "not connected after " + attempts + " attempt(s)";
    }

    /** The flags of a recent (under 10 s old) scan line for this SSID, or null. */
    private static String scanFlags(String scan, String ssid) {
        for (String line : scan.split("\n")) {
            int at = line.indexOf(" " + ssid + " ");
            if (at < 0) continue;
            String[] cols = line.trim().split("\\s+");
            try {
                if (cols.length > 3 && Double.parseDouble(cols[3]) > 10) continue;   // Age(sec): stale
            } catch (NumberFormatException ignored) {
            }
            int flags = line.indexOf('[', at + ssid.length());
            if (flags >= 0) return line.substring(flags);
        }
        return null;
    }

    private static String exec(String... command) {
        try {
            Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line).append('\n');
            }
            p.waitFor();
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
