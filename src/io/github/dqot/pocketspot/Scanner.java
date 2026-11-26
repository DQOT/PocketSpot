package io.github.dqot.pocketspot;

import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanSettings;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import java.util.Collections;

/**
 * Phone side: a background BLE scan that wakes {@link ScanReceiver} only for our messages.
 * The Bluetooth chip does the filtering, so the app does not run while it waits.
 */
final class Scanner {
    private static final int JOB_ID = 1;

    private Scanner() {
    }

    /** (Re)registers the scan and the periodic check that keeps it registered. */
    static String register(Context context) {
        schedule(context);
        BluetoothManager bm = context.getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter = bm == null ? null : bm.getAdapter();
        if (adapter == null || !adapter.isEnabled()) return context.getString(R.string.scan_bluetooth_off);
        BluetoothLeScanner scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) return context.getString(R.string.scan_no_scanner);
        ScanFilter filter = new ScanFilter.Builder()
                .setManufacturerData(Beacon.COMPANY_ID, Beacon.MAGIC, new byte[]{(byte) 0xFF, (byte) 0xFF})
                .build();
        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .build();
        try {
            PendingIntent pi = pendingIntent(context);
            scanner.stopScan(pi);   // replace, not stack up
            int code = scanner.startScan(Collections.singletonList(filter), settings, pi);
            return code == 0 ? context.getString(R.string.scan_listening)
                    : context.getString(R.string.scan_register_failed, code);
        } catch (SecurityException e) {
            return context.getString(R.string.scan_no_permission);
        }
    }

    static void stop(Context context) {
        context.getSystemService(JobScheduler.class).cancel(JOB_ID);
        BluetoothManager bm = context.getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter = bm == null ? null : bm.getAdapter();
        if (adapter == null || !adapter.isEnabled() || adapter.getBluetoothLeScanner() == null) return;
        try {
            adapter.getBluetoothLeScanner().stopScan(pendingIntent(context));
        } catch (SecurityException ignored) {
        }
    }

    /** Turning Bluetooth off drops the scan, so re-register now and then (cheap, no wakeups of its own). */
    private static void schedule(Context context) {
        JobScheduler js = context.getSystemService(JobScheduler.class);
        if (js.getPendingJob(JOB_ID) != null) return;
        js.schedule(new JobInfo.Builder(JOB_ID, new ComponentName(context, RescanJob.class))
                .setPeriodic(15 * 60_000)
                .setPersisted(true)
                .build());
    }

    private static PendingIntent pendingIntent(Context context) {
        Intent intent = new Intent(context, ScanReceiver.class).setAction("io.github.dqot.pocketspot.SCAN_RESULT");
        return PendingIntent.getBroadcast(context, 1, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
    }
}
