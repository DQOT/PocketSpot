package io.github.dqot.pocketspot;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Phone side: registers the background scan again after a reboot or an app update. */
public final class EnsureReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action) && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) return;
        Helper.writeStartScript(context);
        if (Status.isPhone(context)) Status.setScan(context, Scanner.register(context));
    }
}
