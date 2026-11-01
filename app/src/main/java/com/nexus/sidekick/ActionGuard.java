package com.nexus.sidekick;
import java.util.regex.Pattern;

/** Independent, testable safety gate for potentially consequential UI actions. */
public final class ActionGuard {
    private static final Pattern DANGEROUS=Pattern.compile("\\b(pay|transfer|checkout|buy|purchase|send|submit|delete|password|pin|otp|verify|reset|authorize|approve|confirm|grant|security|payment|withdraw|install|uninstall|erase|share|post|publish|call)\\b",Pattern.CASE_INSENSITIVE);
    private ActionGuard(){}
    public static boolean isAllowed(String text){return text!=null&&text.length()>0&&text.length()<=300&&!DANGEROUS.matcher(text).find();}
}
