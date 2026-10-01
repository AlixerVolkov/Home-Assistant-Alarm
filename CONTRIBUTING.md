# Contributing to HomePanel

Thank you for considering a contribution to HomePanel.

## Before you start

HomePanel is designed as a native Android wall panel for Home Assistant. Changes should preserve the following principles:

- Home Assistant remains the primary source of truth.
- Alarm control must remain predictable and safe.
- Optional integrations such as MQTT and RTSP must not prevent the core application from starting.
- Compatibility with older wall tablets matters; the minimum supported Android version is Android 7.1.1 (API 25).
- UI changes should remain usable in portrait, landscape, and compact tablet layouts.

## Development environment

Recommended toolchain:

- JDK 17
- Android SDK 37
- Android Build Tools 36.0.0
- Gradle 9.6.0

Build with:

```bash
gradle --no-daemon --stacktrace :app:assembleRelease
```

## Pull requests

Keep pull requests focused and explain:

- what problem is being solved,
- which Home Assistant entities or integrations are involved,
- how the change was tested,
- any compatibility implications.

For UI changes, screenshots are helpful.

For networking, MQTT, RTSP, alarm, or camera changes, include the relevant error message or diagnostic output when possible.

## Coding guidelines

- Prefer clear Kotlin over clever abstractions.
- Guard Android-version-specific APIs with SDK checks.
- Avoid blocking the UI thread.
- Treat network and hardware failures as recoverable.
- Do not let optional modules crash the main alarm UI.
- Preserve secure storage of credentials.
- Never commit tokens, passwords, keystores, or private Home Assistant URLs.

## Home Assistant compatibility

When introducing entity-specific behavior:

- prefer standard Home Assistant domains and attributes,
- avoid relying on installation-specific entity names,
- use entity/device/area registries when location or grouping information is required,
- keep graceful fallbacks for missing or unavailable entities.

## Reporting bugs

Please include:

- HomePanel version,
- Android version,
- device model,
- Home Assistant version if relevant,
- exact error message,
- steps to reproduce.

Remove all secrets before posting logs.
