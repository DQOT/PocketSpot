package io.github.dqot.pocketspot;

import android.os.Binder;
import android.os.Parcel;

/**
 * The Shizuku helper: Shizuku runs this class as the shell user and hands its binder to the app
 * only, so no signing is needed here.
 */
public final class ShizukuService extends Binder {
    static final int TX_PING = 1;
    static final int TX_TETHER = 2;
    static final int TX_CONNECT = 3;
    static final int TX_CANCEL = 4;
    static final int TX_SSID = 5;
    private static final int TX_DESTROY = 16777115;   // Shizuku's "destroy user service" code

    public ShizukuService() {
    }

    @Override
    protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
        switch (code) {
            case TX_PING:
                reply.writeString("pong");
                return true;
            case TX_TETHER:
                reply.writeString(PrivilegedOps.tether(data.readInt() == 1));
                return true;
            case TX_CONNECT:
                String ssid = data.readString();
                String password = data.readString();
                reply.writeString(PrivilegedOps.connectSoon(ssid, password));
                return true;
            case TX_CANCEL:
                reply.writeString(PrivilegedOps.cancelConnect());
                return true;
            case TX_SSID:
                reply.writeString("ssid " + PrivilegedOps.connectedSsid());
                return true;
            case TX_DESTROY:
                System.exit(0);
                return true;
            default:
                return false;
        }
    }
}
