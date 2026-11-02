# WhatsApp LSPatch experimental build

This is an **isolated branch** of the NEXUS repository. It does not modify the NEXUS app on `main`.

## Source and reproducibility
- Upstream: https://github.com/JingMatrix/LSPatch at commit `4d573639e4192c33afb3f1b76b16fc248dbeb830`
- Build: `.github/workflows/lspatch-whatsapp.yml`
- Changes: `tools/apply_whatsapp_patch.py` applies verifiable exact-match source edits during CI.
- Artifact: `LSPatch-WhatsApp-Experimental` containing the built manager APK.

## What Level 4 actually adds
- The level 1–3 hooks are inherited from upstream.
- Level 4 registers an extra `SigningInfo.getSigningCertificateHistory()` hook.
- Only signing-info objects associated with the currently patched WhatsApp package are affected.
- The Level 4 option and test preset appear only when patching `com.whatsapp` or `com.whatsapp.w4b` on arm64.
- User-facing warning reminds that original signing keys and server-side checks **cannot be spoofed by this in-process hook**.

## Limits and safety
This is experimental, **not a verified fix** for WhatsApp's "Download the official WhatsApp" registration warning.
Do not confuse APK signature spoofing with a valid Meta-signed APK. Do not clear or uninstall WhatsApp without a verified backup. No WhatsApp APK, keys, phone numbers, OTPs, chats, or credentials are committed or collected by this project.

## Diagnostics
- GitHub Actions captures source patch mismatch, compilation failure, and artifact upload errors.
- Runtime hook logging uses the existing LSPatch-SigBypass log tag and never logs secrets.
- To distinguish a client-side hook failure from server-side attestation, further device-side testing would be required.
