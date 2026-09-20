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


## v0.5.1 LAN/MQTT fix

- `ACCESS_LOCAL_NETWORK` is checked before MQTT connection on Android 17+.
- MQTT is retried after the runtime permission grant callback.
- Plain MQTT prefers an IPv4 address when DNS returns dual-stack results.
- A 5-second TCP probe precedes the MQTT handshake and reports reachability problems separately.
- RTSP endpoint display prefers a LAN IPv4 address for Frigate/go2rtc.
- `versionCode=10`, `versionName=0.5.1`.
