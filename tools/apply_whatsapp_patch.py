#!/usr/bin/env python3
"""Apply reproducible WhatsApp-specific experiments to the pinned JingMatrix LSPatch source.

Level 4 is NOT a WhatsApp verification bypass guarantee.
It extends the existing app-local signing-info interception to the certificate-history API.
No OTPs, messages, account data, signing keys, or secrets are collected.
"""
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("Usage: apply_whatsapp_patch.py PATH_TO_UPSTREAM_LSPATCH")
root = Path(sys.argv[1]).resolve()

def patch(path, edits):
    file = root / path
    text = file.read_text(encoding="utf-8")
    for before, after in edits:
        count = text.count(before)
        if count != 1:
            raise RuntimeError(f"Patch mismatch: {path}: expected 1 anchor, got {count}: {before[:90]!r}")
        text = text.replace(before, after, 1)
    file.write_text(text, encoding="utf-8")
    print("PATCHED", path, flush=True)

patch("share/java/src/main/java/org/lsposed/lspatch/share/Constants.java", [
    ("    final static public int SIGBYPASS_LV_MAX = 4;",
     "    // Experimental: add coverage for in-process SigningInfo certificate-history reads.\n"
     "    final static public int SIGBYPASS_LV_PM_HISTORY = 4;\n"
     "    final static public int SIGBYPASS_LV_MAX = 5;"),
])

loader = "patch-loader/src/main/java/org/lsposed/lspatch/loader/SigBypass.java"
patch(loader, [
    ("import android.content.pm.Signature;\n",
     "import android.content.pm.Signature;\nimport android.content.pm.SigningInfo;\n"),
    ("import java.util.Map;\n",
     "import java.util.Map;\nimport java.util.Set;\nimport java.util.WeakHashMap;\n"),
    ("    private static String getReplacement(Context context, String packageName) {",
     "    // Only SigningInfo instances known to belong to THIS patched app may be rewritten.\n"
     "    // Weak references avoid keeping framework PackageInfo structures alive indefinitely.\n"
     "    private static final Set<SigningInfo> selfSigningInfos =\n"
     "            java.util.Collections.newSetFromMap(\n"
     "                    java.util.Collections.synchronizedMap(new WeakHashMap<>()));\n\n"
     "    private static String getReplacement(Context context, String packageName) {"),
    ("                Signature original = new Signature(replacement);\n",
     "                Signature original = new Signature(replacement);\n"
     "                if (packageName.equals(context.getPackageName()) && packageInfo.signingInfo != null) {\n"
     "                    selfSigningInfos.add(packageInfo.signingInfo);\n"
     "                }\n"),
    ("    static void doSigBypass(Context context, int sigBypassLevel) throws IOException {",
     """    /**
     * Android's SigningInfo exposes certificate history separately from the current signer.
     * Existing PackageInfo hooks update current signatures and backing SigningDetails, but
     * some ROMs return a copy or calculate history through another path. Restrict this hook
     * to SigningInfo instances already observed for the patched app, never other packages.
     *
     * This can only affect reads in the patched process. It cannot forge the APK signature,
     * Google Play Integrity attestation or any server-side WhatsApp registration decision.
     */
    private static void hookSelfSigningHistory(Context context) {
        try {
            XposedBridge.hookAllMethods(SigningInfo.class,
                    "getSigningCertificateHistory", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!(param.thisObject instanceof SigningInfo)) return;
                    if (!selfSigningInfos.contains((SigningInfo) param.thisObject)) return;
                    String replacement = getReplacement(context, context.getPackageName());
                    if (replacement == null) return;
                    param.setResult(new Signature[]{new Signature(replacement)});
                    XLog.d(TAG, "Level 4: self SigningInfo history redirected");
                }
            });
            XLog.d(TAG, "Level 4: WhatsApp SigningInfo history hook registered");
        } catch (Throwable t) {
            Log.w(TAG, "Level 4 signing history unavailable on this Android version", t);
        }
    }

    static void doSigBypass(Context context, int sigBypassLevel) throws IOException {"""),
    ("            org.matrix.vector.nativebridge.SigBypass.enableSvcRedirect();\n        }\n    }\n}",
     "            org.matrix.vector.nativebridge.SigBypass.enableSvcRedirect();\n"
     "        }\n"
     "        if (sigBypassLevel >= Constants.SIGBYPASS_LV_PM_HISTORY) {\n"
     "            String packageName = context.getPackageName();\n"
     "            if (\"com.whatsapp\".equals(packageName) || \"com.whatsapp.w4b\".equals(packageName)) {\n"
     "                hookSelfSigningHistory(context);\n"
     "            } else {\n"
     "                XLog.d(TAG, \"Level 4 intentionally limited to WhatsApp packages\");\n"
     "            }\n"
     "        }\n"
     "    }\n}"),
])

ui = "manager/src/main/java/org/lsposed/lspatch/ui/page/NewPatchScreen.kt"
patch(ui, [
    ("        TargetStrip(request)\n\n        SectionLabel(stringResource(R.string.patch_mode))",
     """        TargetStrip(request)

        if (request.packageName == "com.whatsapp" || request.packageName == "com.whatsapp.w4b") {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "WhatsApp experimental compatibility test",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Level 4 adds an in-process signing-certificate-history hook. " +
                            "It cannot reproduce Meta's private signing key, bypass Google " +
                            "attestation, or guarantee registration after OTP. Back up chats " +
                            "before installing any patched WhatsApp.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(onClick = { onSigBypass(4) }) {
                        Text("Use experimental Level 4")
                    }
                }
            }
        }

        SectionLabel(stringResource(R.string.patch_mode))"""),
    ("                SigBypassRow(level = request.sigBypassLevel, onSelect = onSigBypass)",
     "                SigBypassRow(level = request.sigBypassLevel, packageName = request.packageName, onSelect = onSigBypass)"),
    ("private fun SigBypassRow(level: Int, onSelect: (Int) -> Unit) {\n    val maxLevel = remember { if (\"arm64-v8a\" in Build.SUPPORTED_ABIS) 3 else 2 }",
     """private fun SigBypassRow(level: Int, packageName: String, onSelect: (Int) -> Unit) {
    val maxLevel = remember(packageName) {
        if ("arm64-v8a" !in Build.SUPPORTED_ABIS) 2
        else if (packageName == "com.whatsapp" || packageName == "com.whatsapp.w4b") 4
        else 3
    }"""),
    ("""            val delta =
                stringResource(
                    when (threshold) {
                        1 -> R.string.patch_sigbypasslv1
                        2 -> R.string.patch_sigbypasslv2
                        else -> R.string.patch_sigbypasslv3
                    }
                )""",
     """            val delta =
                if (threshold == 4) {
                    "WhatsApp-only: SigningInfo certificate history (experimental)"
                } else {
                    stringResource(
                        when (threshold) {
                            1 -> R.string.patch_sigbypasslv1
                            2 -> R.string.patch_sigbypasslv2
                            else -> R.string.patch_sigbypasslv3
                        }
                    )
                }"""),
])

print("WhatsApp experimental Level 4 patch applied. APK registration is NOT guaranteed.")
