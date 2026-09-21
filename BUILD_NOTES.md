# HomePanel 0.6.9 build notes

- `versionCode = 19`, `versionName = 0.6.9`.
- Mapa de personas con fondo OpenStreetMap opcional y mapa offline como alternativa.
- Cache HTTP local de tiles OSM, User-Agent identificable y atribucion visible.
- Diagnostico de red incluye `tile.openstreetmap.org:443`.
- Avisos meteorologicos activos despiertan la pantalla y abren modo contextual.
- Structural validation performed locally; final Android compilation remains GitHub Actions.

# HomePanel 0.6.7 build notes

- `versionCode = 18`, `versionName = 0.6.7`.
- Weather warnings are discovered from `binary_sensor.weather_warning*` and MeteoAlarm-like entities.
- People map is offline and uses Home Assistant `person.*` / `zone.*` coordinates only.
- Forecast UI and weather models now include optional humidity, pressure, UV, precipitation amount and forecast wind.
- Structural validation performed locally; final Android compilation remains GitHub Actions.

# Build notes - HomePanel v0.6.0

## Toolchain de GitHub Actions

- Ubuntu 24.04
- JDK 17
- Android SDK `platforms;android-37.0`
- Build Tools 36.0.0
- Gradle 9.6.0
- Android Gradle Plugin 9.4.0

## Version

- applicationId: `dev.homepanel.app`
- versionCode: `11`
- versionName: `0.6.0`

## Firma

Requiere los repository secrets existentes:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

## Workflows

`build-apk.yml` genera un artifact de Actions.

`release-apk.yml` genera el APK firmado y lo publica como GitHub Release. La funcion de actualizacion dentro de HomePanel consulta GitHub Releases, por lo que para probar actualizaciones futuras se debe publicar la nueva version mediante ese workflow.

## Verificacion local realizada en este entorno

- XML de recursos/manifest validado.
- Referencias `R.string` comparadas con `values/strings.xml`.
- Recursos EN/ES/NL/FR sincronizados.
- Estructura repo-root y workflows revisados.

No se ejecuto `assembleRelease` localmente porque este contenedor no dispone de Android SDK/Gradle completos; la compilacion definitiva la valida GitHub Actions.

## v0.6.1 permission hardening

- Runtime permission requests are serialized: Local network -> Camera -> Location.
- RTSP refuses to start on Android 17+ until `ACCESS_LOCAL_NETWORK` is granted.
- MQTT already waits for `ACCESS_LOCAL_NETWORK`; HomePanel retries it after grant.
- Home Assistant LAN/WebSocket connection is retried after grant.
- Unused-app restriction / hibernation status is checked through AndroidX Core and the user can jump to the OS settings page to disable it.


## v0.6.2 LAN diagnostics and routing

MQTT is now bound to the selected Wi-Fi/Ethernet `Network`, and Settings shows the LAN IPv4, broker resolution and TCP reachability. RTSP startup order and encoder fallback were also hardened.

## v0.6.3 RTSP crash hardening

- RTSP moved from Camera2 to the Camera1 compatibility backend for wall-panel stability.
- RootEncoder reverted to 2.8.0 to match the RTSP-Server 1.4.2 upstream documented pairing.
- RTSP startup/stop/stats are serialized on a dedicated worker thread.
- Startup crash guard prevents repeated auto-start crash loops.


## v0.6.4 stability diagnostics
RTSP/MQTT are lazy, a persistent Java/Kotlin crash report is installed, and the next boot after an uncaught crash uses Safe Mode. Native camera-driver crashes or OS process kills may not produce a Java stack trace.


## v0.6.5 MQTT backpressure fix

- Fixes Paho MqttException 32202 (too many publishes in progress).
- MQTT telemetry is now conflated through a single worker.
- Retained state telemetry uses QoS 0 and unchanged values are skipped.
- High-frequency ambient-light callbacks no longer trigger immediate MQTT batches.
- MQTT publish exceptions are contained and reported instead of crashing HomePanel.
- Initial Paho connection setup is de-duplicated to avoid double discovery/state bursts.
- versionCode 16 / versionName 0.6.5.


## v0.6.6 Home Assistant weather + network diagnostics

- versionCode 17 / versionName 0.6.6.
- Home Assistant weather entities are discovered with the existing `/api/states` request.
- Current conditions come from `weather.*` state attributes.
- Daily forecast uses `POST /api/services/weather/get_forecasts?return_response`.
- Open-Meteo remains selectable as a direct Internet fallback.
- Network diagnostics tests LAN, HA, HA weather, Open-Meteo, GitHub, MQTT TCP and local RTSP listener.

## v0.6.10 packaging correction

- Repo ZIP now includes hidden `.github/workflows/*` files and `.gitignore`.
- Build workflow derives artifact filenames from `versionName` instead of a hard-coded version.
- App version remains `0.6.10` / `versionCode 21`; no APK was produced by the broken package.


## v0.6.12
- Alarmo failed-arm dialog with user-confirmed force bypass (`force: true`).


## v0.6.13

- Alarmo context_id is now an integer.
- Proximity wakes on NEAR or a real transition and diagnostics count events.
- Optional ambient-light wave-to-wake fallback for Samsung Palm Proximity devices.

## 0.6.14

- Multiple Home Assistant wake entities with searchable multi-select; all selected `binary_sensor.*` entities can wake HomePanel.
- Frigate/person-related entities are prioritized in the wake selector and can also be added manually.
- Added hardware diagnostics dialog for Android sensors, proximity/light event counters, cameras, battery and available memory.
- Added Alarmo pre-arm readiness monitoring via `alarmo_ready_to_arm_modes_updated`.
- Arming buttons show ready/not-ready when Alarmo has provided readiness data.
- If a requested arm mode is known not ready, HomePanel warns before sending the command and offers normal arm, force/bypass, or cancel.
- Existing single wake sensor setting is migrated automatically to the new multi-select storage.
