package com.nexus.sidekick;

import android.content.Context;
import android.content.SharedPreferences;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** On-device operational events only. Never log prompts, transcripts, responses or credentials. */
public final class Diagnostics {
    private static final Object LOCK = new Object();
    private static final String STORE = "nexus_diagnostics";
    private static final int MAX_CHARS = 12000;
    private Diagnostics() {}
    public static void record(Context context, String subsystem, String event) {
        synchronized (LOCK) {
            String time = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
            String safe = event == null ? "" : event.replaceAll("[\\r\\n]", " ");
            if (safe.length() > 130) safe = safe.substring(0, 130);
            String line = time + "  " + subsystem + "  " + safe + "\n";
            SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(STORE, Context.MODE_PRIVATE);
            String history = prefs.getString("events", "") + line;
            if (history.length() > MAX_CHARS) {
                int cutoff = history.indexOf('\n', history.length() - MAX_CHARS);
                history = cutoff >= 0 ? history.substring(cutoff + 1) : history.substring(history.length() - MAX_CHARS);
            }
            prefs.edit().putString("events", history).apply();
        }
    }
    public static String read(Context context) {
        synchronized(LOCK) { return context.getApplicationContext().getSharedPreferences(STORE, Context.MODE_PRIVATE).getString("events", ""); }
    }
    public static void clear(Context context) {
        synchronized(LOCK) { context.getApplicationContext().getSharedPreferences(STORE, Context.MODE_PRIVATE).edit().remove("events").apply(); }
    }
}