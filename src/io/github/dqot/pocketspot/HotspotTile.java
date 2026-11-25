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
    private static long sendingUntil;
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
                new Thread(() -> Helper.cancelConnect(getApplicationContext())).start();
            }
        } else {
            error = Advertiser.callPhone(this, "타일");
            if (error == null) {
                sendingUntil = System.currentTimeMillis() + Advertiser.DURATION_MS;
                // Follow the connection while the panel is open: on as soon as we join
                for (int s = 2; s <= 30; s += 2) handler.postDelayed(this::checkConnection, s * 1000L);
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
                : sending ? "부르는 중" : "눌러서 켜기");
        tile.updateTile();
    }
}
