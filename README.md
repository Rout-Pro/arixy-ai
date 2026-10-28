# ARIXY AI

**Status: experimental test build (v1.0.0-test).** Unit tests and APK structure are verified in the build environment. On-screen UI rendering and device behavior (voice, automation, AI action success) are NOT verified yet - they need a real phone. Treat this as a test build, not a polished final.

A native Android personal AI assistant: premium black liquid-glass UI, natural Hinglish/English conversation, real voice in/out, personality modes, editable memory, immediate stop control, and authorized phone control through real Android APIs.

Built with Kotlin + Jetpack Compose. Talks directly to the Gemini API with **your own API key**, stored encrypted on-device. No hosted server. No API key anywhere in this repo or the APK.

## Download

Get the APK from [Releases](../../releases) (ARIXY-1.0.apk, debug-signed). Enable "install unknown apps" when Android asks. If Play Protect warns about an unknown developer, that is expected for a self-built debug APK - install only if you got it from this repo.

## Setup (2 minutes)

1. Install the APK and open ARIXY.
2. **Settings tab**: paste your free Gemini key from https://aistudio.google.com/apikey and tap Save. Without a key, local commands still work: time, date, battery, torch, volume, opening apps.
3. **Settings tab**: enter your name (optional - used for greetings and reassurance).
4. **Control tab**: grant what you actually want to use:
   - **Accessibility** = screen reading + tap/scroll/type automation
   - **Microphone** = voice commands
   - **Camera** = photo vision in Chat
   - **Contacts** = call/WhatsApp by contact name
   - **Notification access** = message reading + Ghost auto-reply
   - **Overlay** = floating orb on top of other apps
   - **Write settings / DND** = brightness and Do-Not-Disturb control

## The five tabs

- **Home** - animated orb (mirrors the real assistant state), status, quick actions (editable in Settings), mic + text input, and a persistent STOP button whenever anything is active.
- **Chat** - full conversation with context and follow-ups, voice input, camera vision, tap a bubble to copy, Stop and Clear controls.
- **Control** - supported actions, live permission status, and the task log: every automation step (current, done, failed, waiting confirmation).
- **Memory** - long-term memories: view, add, edit, delete, delete-all, or disable entirely. All on-device.
- **Settings** - name, API key, personality mode, playful-jealousy level, voice speed/pitch, wake phrase, floating orb, Ghost auto-reply, quick actions.

## Personality modes

Friendly, Sweet, Mischief, Expressive Companion, Anime-Inspired, Professional, Serious Support. The mode changes the actual system prompt and responses. Professional and Serious Support always disable the playful-jealousy feature. Expressive Companion stays fictional, non-sexual, age-appropriate, and transparent that ARIXY is an AI - it never claims human romantic feelings.

## Stop control

Say or type "Arixy stop", "ruk jao", "bas band karo", "cancel", "stop speaking" - or tap STOP. It interrupts speech, cancels listening and pending automation steps, shows Stopped, and nothing resumes by itself. Stop matching runs locally before anything reaches the AI.

## Honest feature status

**Tested in this build (automated):**
- 17/17 unit tests pass: response JSON parsing (clean, fenced, plain-text), keyless local router (time/date/battery/torch/apps), stop-command matching (including false-positive guards), personality prompt injection (name, memories, mode, jealousy rules), jealousy always off in Professional/Serious, memory encode/decode.
- APK verified: package `com.rout.arixy`, label ARIXY, adaptive + legacy launcher icons included, debug-signed.

**NOT verified (no device/emulator in the build environment):**
- On-screen rendering of the five tabs, orb animations, dialogs and task log (compile-verified only)
- Everything below plus device behavior generally

**Implemented, needs your phone to verify (grants + real device):**
- Voice recognition/TTS (Android SpeechRecognizer + TTS, en-IN)
- Accessibility screen control (tap, scroll, type, screenshot, UI-tree reading)
- Vision re-query: screen capture or camera photo analyzed by Gemini
- Alarms, timers, media control, volume, torch, Wi-Fi/Bluetooth panels, DND
- Calls and WhatsApp sends - always behind an in-app confirmation dialog
- Floating orb overlay + experimental "Hey ARIXY" wake phrase (uses battery; true always-on wake word is NOT implemented and the app says so)
- Ghost auto-reply from notifications (only when the notification has a reply action, 60s per-sender limit)

**Known limitations:**
- Weather/news/live data: no integration - ARIXY will say so instead of inventing data.
- Wake word is best-effort and experimental.
- Some apps block automation; ARIXY reports the failed step in the task log instead of claiming success.
- No continuous/background screen recording: screen access is per-action and permission-gated.

## Build from source

```
export ANDROID_HOME=/path/to/sdk
./gradlew :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest   # unit tests
```

## Privacy

- Your Gemini key lives in EncryptedSharedPreferences on your phone only.
- Memories and settings are on-device; Delete-all removes them instantly.
- Nothing is uploaded anywhere except your own prompts to the Gemini API using your own key.
