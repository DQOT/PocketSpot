package io.github.dqot.pocketspot;

import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/**
 * Tablet side: the quick settings button. Tap to call the phone; while the tablet is on the
 * phone's hotspot the tile stays on, and tapping it then turns the hotspot off.
 * Knowing the current network needs the helper on this tablet (the hotspot name is set in the app).
 *
 * Both ways take a few seconds (the phone hears the call within about 1-5 s), so the tile shows
 * the request at once ("연결 중" / "끄는 중") and only settles when the network really changed.
 */
public final class HotspotTile extends TileService {
    /** With "prefer hotspot" on, the tile stays on as "connecting" until joined or this long. */
    private static final long CONNECT_WAIT_MS = 30_000;
    /** After "off", the tile shows "turning off" until the tablet left the hotspot or this long. */
    private static final long DISCONNECT_WAIT_MS = 20_000;
    private static final long CHECK_EVERY_MS = 1_500;
    private static long sendingUntil;
    private static long leavingUntil;
    private static boolean connecting;   // waiting to join (prefer hotspot), not just calling
    private static volatile boolean onHotspot;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public void onStartListening() {
        refresh(null);
        checkConnection();
    }

    @Override
    public void onClick() {
        handler.removeCallbacksAndMessages(null);   // drop checks left from the last tap
        String error;
        if (onHotspot) {
            error = Advertiser.send(this, Beacon.CMD_OFF);
            Status.log(this, error != null ? error : getString(R.string.log_off_sent_via, getString(R.string.via_tile)));
            if (error == null) {
                onHotspot = false;
                sendingUntil = 0;
                leavingUntil = System.currentTimeMillis() + DISCONNECT_WAIT_MS;
                follow(DISCONNECT_WAIT_MS);
                new Thread(() -> Helper.cancelConnect(getApplicationContext())).start();
            }
        } else {
            error = Advertiser.callPhone(this, getString(R.string.via_tile));
            if (error == null) {
                // Joining can take longer than the 10 s call: stay on until joined, so the
                // tile does not blink off in between.
                connecting = Status.preferHotspot(this) && !Status.hotspotSsid(this).isEmpty();
                long wait = connecting ? CONNECT_WAIT_MS : Advertiser.DURATION_MS;
                sendingUntil = System.currentTimeMillis() + wait;
                leavingUntil = 0;
                follow(wait);
            }
        }
        refresh(error);
    }

    @Override
    public void onStopListening() {
        handler.removeCallbacksAndMessages(null);
    }

    /** Re-checks the network while the panel is open, then settles the tile when the wait ends. */
    private void follow(long wait) {
        for (long t = CHECK_EVERY_MS; t <= wait; t += CHECK_EVERY_MS) handler.postDelayed(this::checkConnection, t);
        handler.postDelayed(() -> refresh(null), wait + 100);
    }

    private void checkConnection() {
        String ssid = Status.hotspotSsid(this);
        if (ssid.isEmpty()) return;
        new Thread(() -> {
            String current = Helper.connectedSsid(getApplicationContext());
            if (current == null) return;   // no helper: the tile just calls
            boolean joined = current.equals(ssid);
            long now = System.currentTimeMillis();
            if (now < leavingUntil) {
                // Turning off: still on the hotspot only means the phone has not acted yet.
                if (!joined) leavingUntil = 0;
                onHotspot = false;
            } else {
                onHotspot = joined;
                if (joined) sendingUntil = 0;   // joined: the wait is over
            }
            handler.post(() -> refresh(null));
        }).start();
    }

    private void refresh(String error) {
        Tile tile = getQsTile();
        if (tile == null) return;
        long now = System.currentTimeMillis();
        boolean sending = now < sendingUntil;
        boolean leaving = now < leavingUntil;
        tile.setState(onHotspot || sending ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setSubtitle(getString(error != null ? R.string.tile_needs_attention
                : leaving ? R.string.tile_turning_off
                : onHotspot ? R.string.tile_connected
                : sending ? (connecting ? R.string.tile_connecting : R.string.tile_calling)
                : R.string.tile_tap_to_turn_on));
        tile.updateTile();
    }
}
