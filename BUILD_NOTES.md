# Build notes - HomePanel v0.5.0

- `applicationId`: `dev.homepanel.app`
- `versionCode`: `9`
- `versionName`: `0.5.0`
- Build task: `:app:assembleRelease`
- Signed by the existing GitHub Actions release keystore secrets.
- Added Eclipse Paho Java MQTT client `1.2.5` for optional MQTT Discovery and screen commands.
- Android 17 local-network permission remains required for local Home Assistant, RTSP and MQTT access.
- Structural validation completed: XML parses, all four locales contain the same 120 strings, and all `R.string` references resolve.
- Full Android compilation still occurs in GitHub Actions.
