package io.github.dqot.pocketspot;

import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import java.util.List;

/** Phone side: checks the matched message and asks the helper to switch the hotspot. */
public final class ScanReceiver extends BroadcastReceiver {
    private static final String TAG = "PocketSpot";

    @Override
    public void onReceive(Context context, Intent intent) {
        List<ScanResult> results = intent.getParcelableArrayListExtra(
                BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT, ScanResult.class);
        if (results == null || !Status.isPhone(context)) return;
        byte[] key = Pairing.key(context);
        for (ScanResult r : results) {
            ScanRecord record = r.getScanRecord();
            if (record == null) continue;
            Beacon.Message m = Beacon.verify(record.getManufacturerSpecificData(Beacon.COMPANY_ID), key);
            if (m == null || !firstTime(context, m.nonce)) continue;
            if (m.command != Beacon.CMD_ON && m.command != Beacon.CMD_OFF) continue;
            boolean on = m.command == Beacon.CMD_ON;
            Log.i(TAG, "beacon " + (on ? "on" : "off") + " rssi " + r.getRssi());
            PendingResult pending = goAsync();
            new Thread(() -> {
                String reply = Helper.tether(context, on);
                Log.i(TAG, "tether " + (on ? "on" : "off") + " -> " + reply);
                Status.log(context, context.getString(R.string.log_tablet_signal,
                        context.getString(on ? R.string.action_on : R.string.action_off), reply));
                pending.finish();
            }).start();
            return;   // one command per delivery
        }
    }

    /** The tablet repeats a message for a while; act on each one once. */
    private static synchronized boolean firstTime(Context context, int nonce) {
        SharedPreferences p = context.getSharedPreferences("seen", Context.MODE_PRIVATE);
        String key = Integer.toHexString(nonce);
        String seen = p.getString("nonces", "");
        if (("," + seen + ",").contains("," + key + ",")) return false;
        String[] parts = seen.isEmpty() ? new String[0] : seen.split(",");
        StringBuilder sb = new StringBuilder(key);
        for (int i = 0; i < parts.length && i < 31; i++) sb.append(',').append(parts[i]);
        p.edit().putString("nonces", sb.toString()).commit();
        return true;
    }
}
