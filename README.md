# NEXUS Sidekick — Android alpha v0.5

A non-root Android assistant with a floating shortcut, permission-based microphone, deterministic app/search actions and Gemini chat.

## Commands
- `open YouTube and search MrBeast`
- `search YouTube for cats`
- `search cats on YouTube`
- `search Google for Android 17`
- `open settings`, `open camera`, `open WhatsApp`
- Other questions use Gemini only when a key has been configured.

## What's improved
- Tap or hold the orb for microphone input. The floating orb is draggable; holding it for ~650 ms opens the assistant's foreground screen and requests speech recognition. Android may restrict background launches on some devices; the in-app Talk orb works without that transition.
- Responsive cyan listening waveform drawn natively, with speech result/error feedback.
- Gemini 3.8 Flash, with 3.6 Flash fallback only when the preferred model returns HTTP 404. Network/auth/quota errors are not retried as another model.
- Android Keystore encrypted Gemini key with migration of the original app's unencrypted preference.
- Safe on-device diagnostics; opt-in copy/share, no prompts/transcripts/keys/responses in logs.
- Optional read-aloud using installed Android TTS.
- Unit tests for commands, versioned builds, build summaries and failure reports.

## Build / APK
Open [GitHub Actions](https://github.com/syednaveedahamed2mpc-del/Gv/actions/workflows/build.yml), select the newest green run, download `NEXUS-SIDEKICK-APK` from Artifacts, unzip and install the included `app-debug.apk`. The APK is debug-signed, intended for testing only.

## Permissions & privacy
Voice recognition depends on the Android recognition provider, which can process speech online. The app requests microphone permission on first use. Floating windows require Android's display-over-other-apps permission. Gemini messages leave the phone only when Gemini is invoked. The local app's encrypted key depends on Android Keystore and isn't committed to GitHub. Avoid sharing the decrypted key or publishing real credentials.

## Limitations
This is **not yet a general autonomous phone controller**. It does not have AccessibilityService automation, background always-on listening, arbitrary tap control, payment actions or multi-step agent execution. Phone/OEM restrictions can prevent floating windows from launching activities in the background; use the in-app orb if that happens. Success of a CI build is not proof of device-level behavior. Logs are intended for debugging, not a guarantee of capturing system-level crashes.

## Diagnostics
In NEXUS, expand **Show Activity Log**. Reproduce the problem. Tap **Copy Safe Log** and share it for debugging. The app records step names, timestamps and numeric error codes; it intentionally excludes API keys and user conversation content. GitHub Actions failure runs have build logs and test reports.