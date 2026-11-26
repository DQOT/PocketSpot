package io.github.dqot.pocketspot;

import android.content.Context;
import android.content.SharedPreferences;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Settings and the last few events, shown in the app. */
final class Status {
    private Status() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("status", Context.MODE_PRIVATE);
    }

    static boolean isPhone(Context c) {
        return prefs(c).getBoolean("phone", false);
    }

    static void setPhone(Context c, boolean phone) {
        prefs(c).edit().putBoolean("phone", phone).commit();
    }

    static String scan(Context c) {
        return prefs(c).getString("scan", c.getString(R.string.scan_not_registered));
    }

    static void setScan(Context c, String s) {
        prefs(c).edit().putString("scan", s + " (" + time() + ")").apply();
    }

    // Tablet: join the phone's hotspot right away, even over another saved network
    static boolean preferHotspot(Context c) {
        return prefs(c).getBoolean("prefer", false);
    }

    static String hotspotSsid(Context c) {
        return prefs(c).getString("ssid", "");
    }

    static String hotspotPassword(Context c) {
        return prefs(c).getString("password", "");
    }

    static void setHotspot(Context c, boolean prefer, String ssid, String password) {
        prefs(c).edit().putBoolean("prefer", prefer).putString("ssid", ssid).putString("password", password).apply();
    }

    static synchronized void log(Context c, String line) {
        String old = prefs(c).getString("log", "");
        String all = time() + "  " + line + (old.isEmpty() ? "" : "\n" + old);
        String[] lines = all.split("\n");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length && i < 12; i++) {
            if (i > 0) sb.append('\n');
            sb.append(lines[i]);
        }
        prefs(c).edit().putString("log", sb.toString()).commit();
    }

    static String log(Context c) {
        return prefs(c).getString("log", "");
    }

    private static String time() {
        return new SimpleDateFormat("M/d HH:mm:ss", Locale.KOREA).format(new Date());
    }
}
