package io.github.dqot.pocketspot;

import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/**
 * Tablet side: the quick settings button. Tap to call the phone; while the tablet is on the
 * phone's hotspot the tile stays on, and tapping it then turns the hotspot off.
 * Knowing the current network needs the helper on this tablet (the hotspot name is set in the app).
 */
public final class HotspotTile extends TileService {
    /** With "prefer hotspot" on, the tile stays on as "connecting" until joined or this long. */
    private static final long CONNECT_WAIT_MS = 30_000;
    private static long sendingUntil;
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
        String error;
        if (onHotspot) {
            error = Advertiser.send(this, Beacon.CMD_OFF);
            Status.log(this, error != null ? error : "끄기 신호 보냄 (타일)");
            if (error == null) {
                onHotspot = false;
                sendingUntil = 0;
                new Thread(() -> Helper.cancelConnect(getApplicationContext())).start();
            }
        } else {
            error = Advertiser.callPhone(this, "타일");
            if (error == null) {
                // Joining can take longer than the 10 s call: stay on until joined, so the
                // tile does not blink off in between.
                connecting = Status.preferHotspot(this) && !Status.hotspotSsid(this).isEmpty();
                long wait = connecting ? CONNECT_WAIT_MS : Advertiser.DURATION_MS;
                sendingUntil = System.currentTimeMillis() + wait;
                // Follow the connection while the panel is open: on as soon as we join
                for (long t = 2_000; t <= CONNECT_WAIT_MS; t += 2_000) handler.postDelayed(this::checkConnection, t);
                handler.postDelayed(() -> refresh(null), wait + 100);
            }
        }
        refresh(error);
    }

    @Override
    public void onStopListening() {
        handler.removeCallbacksAndMessages(null);
    }

    private void checkConnection() {
        String ssid = Status.hotspotSsid(this);
        if (ssid.isEmpty()) return;
        new Thread(() -> {
            String current = Helper.connectedSsid(getApplicationContext());
            if (current == null) return;   // no helper: the tile just calls
            onHotspot = current.equals(ssid);
            if (onHotspot) sendingUntil = 0;   // joined: the wait is over
            handler.post(() -> refresh(null));
        }).start();
    }

    private void refresh(String error) {
        Tile tile = getQsTile();
        if (tile == null) return;
        boolean sending = System.currentTimeMillis() < sendingUntil;
        tile.setState(onHotspot || sending ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setSubtitle(error != null ? "확인 필요"
                : onHotspot ? "연결됨 · 눌러서 끄기"
                : sending ? (connecting ? "연결 중" : "부르는 중") : "눌러서 켜기");
        tile.updateTile();
    }
}
