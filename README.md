# HomePanel for Home Assistant

HomePanel is a native Android wall-panel application for **Home Assistant** and **Alarmo**. It is designed for permanently mounted tablets and provides alarm control, household status, weather, presence, guest Wi-Fi, MQTT telemetry, RTSP camera streaming, kiosk behavior, and in-app updates from GitHub Releases.

## Highlights

- Native Android UI built with Jetpack Compose.
- Direct Home Assistant integration over REST and WebSocket.
- Alarmo / `alarm_control_panel` support for Home, Away, Night, Vacation, Custom Bypass, and Disarm.
- PIN-aware alarm actions and pre-arm validation.
- Household overview for doors, windows, lights, temperature, weather warnings, and presence.
- OpenStreetMap-based presence map with Home Assistant zones and offline tile cache.
- Home Assistant weather entities with forecast support, plus optional Open-Meteo fallback.
- UniFi guest voucher workflow with QR code support.
- Front-camera RTSP server for Frigate / go2rtc.
- MQTT Discovery for HomePanel device telemetry and controls.
- Automatic brightness using the Android ambient-light sensor.
- Proximity and Home Assistant sensor-based wake behavior.
- Immersive kiosk mode with optional settings PIN.
- Persistent event history and crash-safe startup mode.
- Signed APK builds and releases through GitHub Actions.
- Android 7.1.1+ support (API 25+).

## Screens and capabilities

HomePanel is intended to act as a compact smart-home control surface rather than a general Home Assistant dashboard. The main screen prioritizes:

- alarm state and actions,
- relevant household alerts,
- weather and warnings,
- presence and map access,
- guest Wi-Fi access,
- quick access to settings and diagnostics.

The application keeps Home Assistant as the source of truth. MQTT and RTSP are optional integrations and are not required for normal alarm operation.

## Requirements

- Android 7.1.1 or newer.
- A reachable Home Assistant instance.
- A Home Assistant Long-Lived Access Token.
- An `alarm_control_panel.*` entity or Alarmo setup for alarm control.

Optional integrations:

- MQTT broker for MQTT Discovery and telemetry.
- Frigate / go2rtc for the tablet front-camera RTSP stream.
- UniFi guest-voucher entities exposed through Home Assistant.
- A `weather.*` entity for weather and forecasts.

## Installation

Download the latest signed APK from the repository's **Releases** page and install it on the Android device.

For a permanently mounted tablet, it is recommended to:

- keep the device connected to a stable Wi-Fi or Ethernet network,
- disable Android battery restrictions for HomePanel where appropriate,
- disable “pause app activity if unused” / app hibernation,
- use kiosk mode if the tablet is dedicated to HomePanel.

## Initial setup

1. Open HomePanel.
2. Enter the Home Assistant base URL.
3. Provide a Home Assistant Long-Lived Access Token.
4. Discover and select the alarm entity.
5. Configure optional wake sensors, weather source, MQTT, RTSP, and guest Wi-Fi integrations.
6. Save the configuration.

The access token and other secrets are stored encrypted using Android Keystore-backed encryption.

## Home Assistant integration

HomePanel uses:

- REST API for discovery, household summaries, images, weather, and supporting data.
- WebSocket API for real-time alarm state, events, and service calls.

The application does not require a custom Home Assistant integration.

## MQTT Discovery

When MQTT Discovery is enabled, HomePanel can expose entities such as:

- battery level,
- battery temperature,
- charging state,
- proximity,
- ambient light,
- screen state,
- display mode,
- RTSP server state,
- RTSP client count,
- RTSP URL.

Each installation uses a device-specific identifier so multiple HomePanel tablets can coexist in Home Assistant.

HomePanel intentionally uses Android's normal network routing for MQTT instead of binding the MQTT client to a cached Android `Network` object. This avoids stale-network `ENONET` failures after Wi-Fi reconnects or network changes.

## RTSP camera

HomePanel can expose the tablet's front camera as an H.264 RTSP stream for Frigate or go2rtc.

Example endpoint:

```text
rtsp://192.168.1.74:8554/
```

The application uses the Camera1 compatibility backend for older Android devices and selects a resolution reported as supported by the actual front camera instead of assuming a fixed 1280×720 mode.

Example go2rtc / Frigate configuration:

```yaml
go2rtc:
  streams:
    homepanel_front: rtsp://192.168.1.74:8554/

cameras:
  homepanel_front:
    ffmpeg:
      inputs:
        - path: rtsp://127.0.0.1:8554/homepanel_front
          input_args: preset-rtsp-restream
          roles:
            - detect
```

## Maps and privacy

Presence coordinates come from Home Assistant entities and zones. When OpenStreetMap mode is enabled, HomePanel only requests normal map tiles for the visible map area. Person names and Home Assistant entity IDs are not sent to the tile server.

Cached tiles can be used when the tablet is offline.

## Build

Toolchain used by the project:

- JDK 17
- Android SDK 37
- Android Build Tools 36.0.0
- Gradle 9.6.0
- Android Gradle Plugin 9.4.0

Build a release APK with:

```bash
gradle --no-daemon --stacktrace :app:assembleRelease
```

Build output:

```text
app/build/outputs/apk/release/app-release.apk
```

## Signing

Release builds use the following GitHub Actions secrets:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

The project must keep the same application ID and signing key for in-place upgrades:

```text
applicationId = dev.homepanel.app
```

## GitHub Actions

Two workflows are included:

- **Build signed Android APK** — validates the project and uploads a signed APK as a workflow artifact.
- **Publish HomePanel Release** — builds, signs, verifies, hashes, and publishes the APK to GitHub Releases.

Release publishing is tied to the version declared in `app/build.gradle.kts`.

## Security notes

- Home Assistant access tokens are encrypted before being stored locally.
- HTTP is supported for local Home Assistant installations, but HTTPS is recommended.
- HomePanel does not silently disable Android security or hibernation protections.
- Full Android Device Owner / Lock Task provisioning is outside the scope of the normal APK install.

## Project structure

```text
app/src/main/java/dev/homepanel/app/
├── camera/       RTSP camera server
├── data/         settings and persistent event history
├── diagnostics/  crash diagnostics
├── mqtt/         MQTT Discovery and telemetry
├── network/      Home Assistant, weather, map tiles and update clients
├── security/     local secret encryption
└── ui/           Jetpack Compose screens
```

See [ARCHITECTURE.md](ARCHITECTURE.md) for more implementation details and [ROADMAP.md](ROADMAP.md) for planned improvements.

## Contributing

Contributions are welcome. Please read [CONTRIBUTING.md](CONTRIBUTING.md) before opening a pull request.

When reporting a problem, include:

- Android version and device model,
- HomePanel version,
- relevant Home Assistant entity types,
- the exact error message,
- whether the problem reproduces after restarting the app.

Do not include Home Assistant access tokens, passwords, private URLs, or signing secrets in issues.

## License

Licensed under the Apache License 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
