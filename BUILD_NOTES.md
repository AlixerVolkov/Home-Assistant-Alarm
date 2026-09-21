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
