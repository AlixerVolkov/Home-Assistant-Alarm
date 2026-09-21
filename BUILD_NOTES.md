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
