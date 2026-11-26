package io.github.dqot.pocketspot;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertisingSet;
import android.bluetooth.le.AdvertisingSetCallback;
import android.bluetooth.le.AdvertisingSetParameters;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;

/**
 * Tablet side: broadcasts one signed command for a few seconds. The phone scans in low power
 * mode (about every 5 s), so 10 s gives it two chances; it usually hears it within 1-2 s.
 */
final class Advertiser {
    static final int DURATION_MS = 10_000;
    private static AdvertisingSetCallback current;

    private Advertiser() {
    }

    /** Returns null on success, or why it could not send. */
    static synchronized String send(Context context, int command) {
        byte[] key = Pairing.key(context);
        if (key == null) return context.getString(R.string.error_no_pairing_code);
        BluetoothManager bm = context.getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter = bm == null ? null : bm.getAdapter();
        if (adapter == null || !adapter.isEnabled()) return context.getString(R.string.error_bluetooth_off);
        BluetoothLeAdvertiser advertiser = adapter.getBluetoothLeAdvertiser();
        if (advertiser == null) return context.getString(R.string.error_cannot_advertise);
        try {
            if (current != null) advertiser.stopAdvertisingSet(current);
            AdvertisingSetParameters params = new AdvertisingSetParameters.Builder()
                    .setLegacyMode(true)
                    .setConnectable(false)
                    .setScannable(false)
                    .setInterval(AdvertisingSetParameters.INTERVAL_LOW)
                    .setTxPowerLevel(AdvertisingSetParameters.TX_POWER_HIGH)
                    .build();
            AdvertiseData data = new AdvertiseData.Builder()
                    .addManufacturerData(Beacon.COMPANY_ID, Beacon.build(command, key))
                    .build();
            Context app = context.getApplicationContext();   // the callback outlives a screen
            current = new AdvertisingSetCallback() {
                @Override
                public void onAdvertisingSetStarted(AdvertisingSet set, int txPower, int status) {
                    if (status != ADVERTISE_SUCCESS) Status.log(app, app.getString(R.string.log_advertise_failed, status));
                }
            };
            advertiser.startAdvertisingSet(params, data, null, null, null, DURATION_MS / 10, 0, current);
            return null;
        } catch (SecurityException e) {
            return context.getString(R.string.error_bluetooth_permission);
        }
    }

    /**
     * Sends "on", then (if set up) has the helper join the phone's hotspot as soon as it appears.
     * Returns null on success.
     */
    static String callPhone(Context context, String via) {
        String error = send(context, Beacon.CMD_ON);
        if (error != null) {
            Status.log(context, error);
            return error;
        }
        Status.log(context, context.getString(R.string.log_on_sent, via));
        if (Status.preferHotspot(context) && !Status.hotspotSsid(context).isEmpty()) {
            Context app = context.getApplicationContext();
            new Thread(() -> Status.log(app, app.getString(R.string.log_prefer_hotspot,
                    Helper.connect(app, Status.hotspotSsid(app), Status.hotspotPassword(app))))).start();
        }
        return null;
    }
}
