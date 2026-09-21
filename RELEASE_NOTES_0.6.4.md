# HomePanel 0.6.4

Stability and crash diagnostics release.

- RTSP is now genuinely lazy: when disabled, the RTSP server implementation and camera stack are not instantiated or polled.
- MQTT is also lazy and is not instantiated unless MQTT Discovery is enabled.
- Added a persistent uncaught-crash report. On the next launch HomePanel shows the report and offers a Copy crash report button.
- After any uncaught crash, the next launch enters one-boot Safe Mode. RTSP, MQTT, ambient/proximity sensors and automatic update checks are skipped so the core Home Assistant/alarm UI can start.
- Hardened startup effects (unused-app check, sensors, brightness, kiosk and wake calls) so OEM-specific exceptions do not terminate the app.
- Home Assistant connection setup now catches malformed/stale configuration errors rather than crashing the main coroutine.
- versionCode 15 / versionName 0.6.4.
