package io.github.dqot.pocketspot;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** After a reboot or an app update: phone side registers the background scan again; both keep the update check. */
public final class EnsureReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action) && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) return;
        Helper.writeStartScript(context);
        UpdateCheck.schedule(context);
        if (Status.isPhone(context)) Status.setScan(context, Scanner.register(context));
    }
}
