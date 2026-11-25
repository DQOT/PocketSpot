package io.github.dqot.pocketspot;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;

/** Gives the pairing key to the ADB helper. Answers only the shell user (and root). */
public final class KeyProvider extends ContentProvider {
    static final String AUTHORITY = "io.github.dqot.pocketspot.key";
    static final String METHOD = "key";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        int uid = Binder.getCallingUid();
        if (!METHOD.equals(method) || (uid != 2000 && uid != 0)) return null;
        byte[] key = Pairing.key(getContext());
        Bundle out = new Bundle();
        if (key != null) out.putString("key", Signed.hex(key));
        return out;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] args) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] args) {
        return 0;
    }
}
