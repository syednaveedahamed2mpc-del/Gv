# NEXUS Sidekick v0.6 — Android voice and skills agent

**Experimental non-root Android app.** Designed for Motorola and other Android phones.

## What changed
- **No navigation on long-press**: a foreground microphone service hosts its own draggable overlay, mini conversation panel, and listening indicator. Holding the orb starts recognition in the overlay itself; tapping opens its panel. Voice transcripts stay local until sent as a deliberate Gemini prompt.
- **Gemini function calling**: tool descriptions + model-selected commands + local execution + tool results returned to Gemini, with a three-action limit per request. Gemini uses `gemini-3.5-flash-lite` for responsiveness, falling back to `gemini-3.6-flash` on HTTP 404 only.
- **Native tools/skills**: open apps, search YouTube/Google, settings, home, back, recents, notification shade, scroll, tap text, type in ordinary fields. Some require user-enabled Accessibility service. No screen scraping or screenshot capture.
- **Interactive voice**: Android speech-to-text, audible answers via Android TTS, visible mini-panel, speech recognizer reconnect after error 11.
- **Diagnostics**: on-device step and error codes without transcripts, responses, or API keys, plus GitHub Action test/build reports.
- **Gemini connection test**, encrypted key storage with Android Keystore. Existing v0.5 key should remain during normal app update if signature remains the same.

## One-time setup
1. Install updated APK and open NEXUS. Grant microphone permission.
2. Save a Gemini key from https://aistudio.google.com/apikey, and tap **TEST GEMINI CONNECTION**.
3. Tap **ENABLE FLOATING VOICE ASSISTANT**, permit **Display over other apps** and tap Enable again.
4. For UI navigation skills, tap **ENABLE ACCESSIBILITY SKILLS** and explicitly enable the NEXUS service. Android may impose additional security confirmation for sideloaded applications.
5. On any screen, **long-press orb to talk**; no main app redirect should occur. Tap the orb for conversation controls.

## Important limitations
- Android foreground microphone rules require initial start from a visible app with the microphone permission. A system may terminate the background service. Speech Recognition error 11 can still happen when the platform speech service crashes; the overlay will retry only once.
- Generic accessibility click/scroll/text actions are best effort, dependent on visible UI semantics; this is **not** guaranteed control of every app. No screenshots, arbitrary-coordinate taps, bypassing device security, or root access.
- Payment, checkout, sending messages, deleting, account approval, installing, sharing private data and similar sensitive actions are not autonomously executed.
- The Gemini API requires internet; timeout and quota errors remain possible. A compiled APK does not prove on-device functionality until tested.
- GitHub Actions creates debug-signed APKs. The signing certificate can change between builds; if update fails due to certificate mismatch, uninstalling older debug builds may lose locally saved settings and API key.

## Source and build
https://github.com/syednaveedahamed2mpc-del/Gv/actions/workflows/build.yml

An app-side diagnostics log can be copied and shared without including prompts, secrets or speech transcripts.
