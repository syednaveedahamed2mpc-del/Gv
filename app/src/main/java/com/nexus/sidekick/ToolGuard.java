package com.nexus.sidekick;

import java.util.Locale;

/** Conservative policy: an assistant is not authorized to approve or submit consequential transactions. */
public final class ToolGuard {
    private ToolGuard() {}
    public static boolean isSensitiveTap(String text) {
        String x = (text == null ? "" : text).trim().toLowerCase(Locale.ROOT);
        if (x.isEmpty()) return true;
        for (String bad : new String[]{"send", "pay", "transfer", "purchase", "buy", "checkout",
                "order", "delete", "remove", "submit", "confirm", "approve", "authorize",
                "publish", "post", "subscribe", "install", "uninstall", "reset", "factory",
                "log out", "sign out", "allow", "grant", "share", "withdraw", "upi", "otp"}) {
            if (x.equals(bad) || x.startsWith(bad + " ") || x.endsWith(" " + bad)) return true;
        }
        return false;
    }
    public static String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max);
    }
}
