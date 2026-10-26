/*
 * Homestead - a replacement home screen for Fire TV.
 * Copyright (C) 2026 Homestead contributors
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. It is distributed in
 * the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details. You should have received a copy of the GNU General
 * Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.homestead.launcher;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.Set;

/**
 * Moves Homestead's settings between installs, from a computer:
 *
 *   adb shell content read  --uri content://com.homestead.launcher.migrate/prefs > homestead.prefs
 *   adb shell "content write --uri content://com.homestead.launcher.migrate/prefs < /data/local/tmp/homestead.prefs"
 *
 * The text format is SettingsTransfer's. A write replaces only the settings it names; the
 * rest are kept. Like BackgroundProvider, only the shell user and root may call it.
 */
public final class MigrateProvider extends ContentProvider {
    static final String AUTHORITY = "com.homestead.launcher.migrate";
    private static final String TAG = "Homestead";
    private static final int SHELL_UID = 2000, ROOT_UID = 0;
    private static final int MAX_BYTES = 1024 * 1024;

    @Override public boolean onCreate() { return true; }

    private static void checkCaller() {
        int uid = Binder.getCallingUid();
        if (uid != SHELL_UID && uid != ROOT_UID) {
            throw new SecurityException("only adb shell may move Homestead's settings");
        }
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        checkCaller();
        if (uri == null || !"/prefs".equals(uri.getPath())) {
            throw new FileNotFoundException("only " + AUTHORITY + "/prefs");
        }
        final SharedPreferences sp = Prefs.store(getContext());
        final ParcelFileDescriptor[] pipe;
        try {
            pipe = ParcelFileDescriptor.createPipe();
        } catch (IOException e) {
            throw new FileNotFoundException(String.valueOf(e));
        }
        if (mode == null || mode.contains("r")) {
            final byte[] text = SettingsTransfer.format(sp.getAll()).getBytes(UTF8);
            new Thread(new Runnable() {
                public void run() {
                    OutputStream out = new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]);
                    try {
                        out.write(text);
                    } catch (IOException e) {
                        Log.w(TAG, "settings export cut off: " + e);
                    } finally {
                        try { out.close(); } catch (Throwable ignored) { }
                    }
                }
            }, "homestead-prefs-out").start();
            return pipe[0];
        }
        new Thread(new Runnable() {
            public void run() {
                InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(pipe[0]);
                try {
                    ByteArrayOutputStream buf = new ByteArrayOutputStream();
                    byte[] b = new byte[8192];
                    int n;
                    while ((n = in.read(b)) > 0) {
                        if (buf.size() + n > MAX_BYTES) throw new IOException("larger than 1 MB");
                        buf.write(b, 0, n);
                    }
                    // Parsed in full before anything is written: a bad line changes nothing.
                    Map<String, Object> all = SettingsTransfer.parse(new String(buf.toByteArray(), UTF8));
                    apply(sp, all);
                    Log.i(TAG, "imported " + all.size() + " settings");
                } catch (Throwable t) {
                    Log.w(TAG, "settings not imported: " + t);
                } finally {
                    try { in.close(); } catch (Throwable ignored) { }
                }
            }
        }, "homestead-prefs-in").start();
        return pipe[1];
    }

    @SuppressWarnings("unchecked")
    private static void apply(SharedPreferences sp, Map<String, Object> all) {
        SharedPreferences.Editor e = sp.edit();
        for (Map.Entry<String, Object> en : all.entrySet()) {
            Object v = en.getValue();
            if (v instanceof Boolean) e.putBoolean(en.getKey(), (Boolean) v);
            else if (v instanceof Integer) e.putInt(en.getKey(), (Integer) v);
            else if (v instanceof Long) e.putLong(en.getKey(), (Long) v);
            else if (v instanceof String) e.putString(en.getKey(), (String) v);
            else if (v instanceof Set) e.putStringSet(en.getKey(), (Set<String>) v);
        }
        e.commit();
    }

    private static final java.nio.charset.Charset UTF8 = java.nio.charset.Charset.forName("UTF-8");

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args,
                        String sortOrder) {
        return null;
    }

    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}
