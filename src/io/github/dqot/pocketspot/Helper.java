package io.github.dqot.pocketspot;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import rikka.shizuku.Shizuku;

/**
 * App side of the privileged helper. Uses the ADB helper when it is running, otherwise Shizuku.
 * Every call blocks: call off the main thread.
 */
final class Helper {
    enum Kind { ADB, SHIZUKU }

    private static IBinder shizukuService;

    private Helper() {
    }

    /** Which helper is usable right now, or null. */
    static Kind find(Context c) {
        if ("pong".equals(adb("ping"))) return Kind.ADB;
        if (shizukuBinder(c) != null) return Kind.SHIZUKU;
        return null;
    }

    static String tether(Context c, boolean on) {
        return call(c, "tether\n" + (on ? "on" : "off"), ShizukuService.TX_TETHER, on ? 1 : 0, null, null);
    }

    static String connect(Context c, String ssid, String password) {
        return call(c, "connect\n" + ssid + "\n" + password, ShizukuService.TX_CONNECT, 0, ssid, password);
    }

    static String cancelConnect(Context c) {
        return call(c, "cancel", ShizukuService.TX_CANCEL, 0, null, null);
    }

    /** The Wi-Fi network this device is on ("" for none), or null when no helper can tell. */
    static String connectedSsid(Context c) {
        String reply = call(c, "ssid", ShizukuService.TX_SSID, 0, null, null);
        int at = reply.indexOf(": ssid ");
        if (at >= 0) return reply.substring(at + ": ssid ".length());
        return reply.endsWith(": ssid") ? "" : null;
    }

    private static String call(Context c, String body, int tx, int arg, String s1, String s2) {
        Kind kind = find(c);
        if (kind == null) return c.getString(R.string.helper_none);
        if (kind == Kind.ADB) {
            byte[] key = Pairing.key(c);
            if (key == null) return c.getString(R.string.helper_no_pairing_code);
            String reply = adb(Signed.request(body, key));
            return "ADB: " + (reply == null ? c.getString(R.string.helper_no_reply) : reply);
        }
        IBinder b = shizukuBinder(c);
        if (b == null) return c.getString(R.string.helper_shizuku_connect_failed);
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            if (tx == ShizukuService.TX_CONNECT) {
                data.writeString(s1);
                data.writeString(s2);
            } else if (tx == ShizukuService.TX_TETHER) {
                data.writeInt(arg);
            }
            b.transact(tx, data, reply, 0);
            return "Shizuku: " + reply.readString();
        } catch (Exception e) {
            shizukuService = null;
            return c.getString(R.string.helper_shizuku_error, e.getMessage());
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    // ADB helper over 127.0.0.1

    static String adb(String line) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("127.0.0.1", Daemon.PORT), 1_000);
            s.setSoTimeout(20_000);
            s.getOutputStream().write((line + "\n").getBytes(StandardCharsets.UTF_8));
            return new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8)).readLine();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * The command that starts the ADB helper. The app keeps the script in its external files
     * folder, which adb can read, so the command has no quotes and pastes into any PC shell.
     */
    static String adbCommand(Context c) {
        return "adb shell sh " + startScript(c).getAbsolutePath();
    }

    static java.io.File startScript(Context c) {
        return new java.io.File(c.getExternalFilesDir(null), "start.sh");
    }

    static void writeStartScript(Context c) {
        String script = "#!/system/bin/sh\n"
                + "# Starts the PocketSpot ADB helper as the shell user. Run again after a reboot.\n"
                + "P=$(pm path " + c.getPackageName() + " | head -1 | cut -d: -f2)\n"
                + "[ -n \"$P\" ] || { echo 'PocketSpot is not installed'; exit 1; }\n"
                + "pkill -f pocketspotd 2>/dev/null\n"
                + "# Keep the app's 127.0.0.1 link to the helper open in Doze\n"
                + "cmd deviceidle whitelist +" + c.getPackageName() + " >/dev/null 2>&1\n"
                + "sleep 1\n"
                + "setsid nohup app_process -Djava.class.path=\"$P\" /system/bin --nice-name=pocketspotd "
                + Daemon.class.getName() + " >/dev/null 2>&1 </dev/null &\n"
                + "sleep 2\n"
                + "if pgrep -f pocketspotd >/dev/null; then echo 'PocketSpot helper running'; "
                + "else echo 'PocketSpot helper failed to start'; fi\n";
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(startScript(c))) {
            out.write(script.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }

    // Shizuku

    /** Shizuku state for the screen. */
    static String shizukuState(Context c) {
        if (!waitForShizuku(1_000)) return c.getString(R.string.shizuku_not_running);
        if (Shizuku.isPreV11()) return c.getString(R.string.shizuku_too_old);
        return c.getString(Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                ? R.string.shizuku_granted : R.string.shizuku_needs_permission);
    }

    private static boolean waitForShizuku(long timeoutMs) {
        if (Shizuku.pingBinder()) return true;
        CountDownLatch got = new CountDownLatch(1);
        Shizuku.OnBinderReceivedListener l = got::countDown;
        Shizuku.addBinderReceivedListenerSticky(l, new Handler(Looper.getMainLooper()));
        try {
            got.await(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignored) {
        } finally {
            Shizuku.removeBinderReceivedListener(l);
        }
        return Shizuku.pingBinder();
    }

    private static synchronized IBinder shizukuBinder(Context c) {
        if (shizukuService != null && shizukuService.pingBinder()) return shizukuService;
        shizukuService = null;
        if (!waitForShizuku(3_000) || Shizuku.isPreV11()
                || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            return null;
        }
        CountDownLatch connected = new CountDownLatch(1);
        IBinder[] out = {null};
        ServiceConnection conn = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                out[0] = service;
                connected.countDown();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                shizukuService = null;
            }
        };
        Shizuku.UserServiceArgs args = new Shizuku.UserServiceArgs(
                new ComponentName(c.getPackageName(), ShizukuService.class.getName()))
                .daemon(true)
                .processNameSuffix("helper")
                .debuggable(false)
                .version(versionCode(c));
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                Shizuku.bindUserService(args, conn);
            } catch (Exception e) {
                connected.countDown();
            }
        });
        try {
            connected.await(8, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
        }
        shizukuService = out[0];
        return shizukuService;
    }

    /** A new app version restarts the Shizuku helper with the new code. */
    private static int versionCode(Context c) {
        try {
            return (int) c.getPackageManager().getPackageInfo(c.getPackageName(), 0).getLongVersionCode();
        } catch (PackageManager.NameNotFoundException e) {
            return 1;
        }
    }
}
