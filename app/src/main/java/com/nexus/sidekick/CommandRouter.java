package com.nexus.sidekick;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Deterministic, offline commands. Unrecognized text is handed to chat, never executed. */
public final class CommandRouter {
    public enum Type { YOUTUBE_SEARCH, GOOGLE_SEARCH, OPEN_APP, OPEN_SETTINGS, OPEN_CAMERA, CHAT, EMPTY }
    public static final class Command {
        public final Type type;
        public final String value;
        Command(Type type, String value) { this.type = type; this.value = value; }
    }
    private static final String YT = "(?:youtube|you\\s+tube)";
    private static final Pattern COMBINED = Pattern.compile("^open\\s+"+YT+"\\s+(?:and\\s+)?(?:search|find|look\\s+up|play)\\s+(?:for\\s+)?(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PREFIX = Pattern.compile("^(?:search|find|look\\s+up|play)\\s+(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern YT_FIRST = Pattern.compile("^(?:on\\s+)?"+YT+"\\s+(?:for\\s+)?(.+)$",Pattern.CASE_INSENSITIVE);
    private static final Pattern GOOGLE_FIRST = Pattern.compile("^(?:on\\s+)?(?:google|the\\s+web|web)\\s+(?:for\\s+)?(.+)$",Pattern.CASE_INSENSITIVE);
    private static final Pattern YT_LAST = Pattern.compile("^(.+?)\\s+(?:on|in)\\s+"+YT+"$", Pattern.CASE_INSENSITIVE);
    private static final Pattern GOOGLE_LAST = Pattern.compile("^(.+?)\\s+(?:on|in)\\s+(?:google|the\\s+web|web)$",Pattern.CASE_INSENSITIVE);
    private CommandRouter() {}
    public static Command parse(String raw) {
        String q = raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
        if (q.isEmpty()) return new Command(Type.EMPTY, "");
        String l = q.toLowerCase(Locale.ROOT);
        Matcher combined = COMBINED.matcher(q);
        if (combined.matches()) return new Command(Type.YOUTUBE_SEARCH, stripFor(combined.group(1)));
        if (l.equals("settings") || l.equals("open settings")) return new Command(Type.OPEN_SETTINGS, "");
        if (l.equals("camera") || l.equals("open camera")) return new Command(Type.OPEN_CAMERA, "");
        if (l.startsWith("open ")) {
            String app = q.substring(5).trim();
            if (!app.isEmpty()) return new Command(Type.OPEN_APP, app);
        }
        Matcher m = PREFIX.matcher(q);
        if (m.matches()) {
            String target = m.group(1);
            boolean play = l.startsWith("play ");
            Matcher a = YT_FIRST.matcher(target);
            if (a.matches()) return new Command(Type.YOUTUBE_SEARCH, stripFor(a.group(1)));
            a = GOOGLE_FIRST.matcher(target);
            if (a.matches()) return new Command(Type.GOOGLE_SEARCH, stripFor(a.group(1)));
            a = YT_LAST.matcher(target);
            if (a.matches()) return new Command(Type.YOUTUBE_SEARCH, stripFor(a.group(1)));
            a = GOOGLE_LAST.matcher(target);
            if (a.matches()) return new Command(Type.GOOGLE_SEARCH, stripFor(a.group(1)));
            return new Command(play ? Type.YOUTUBE_SEARCH : Type.GOOGLE_SEARCH, stripFor(target));
        }
        return new Command(Type.CHAT, q);
    }
    private static String stripFor(String text) { return text.replaceFirst("(?i)^for\\s+", "").trim(); }
}