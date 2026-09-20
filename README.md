# HomePanel v0.1

Panel Android moderno para controlar una entidad `alarm_control_panel` de Home Assistant.

Este proyecto nace como una reinterpretaci&#243;n moderna de las ideas de:

- https://github.com/thanksmister/androidthings-mqtt-alarm-panel
- https://github.com/thanksmister/android-mqtt-alarm-panel

No reutiliza Android Things. La integraci&#243;n principal es la API de Home Assistant.

## Estado

MVP v0.1.

Incluye:

- Android 8.0+ (`minSdk 26`).
- `compileSdk` / `targetSdk` 37.
- Jetpack Compose.
- Descubrimiento de entidades `alarm_control_panel` mediante `/api/states`.
- WebSocket de Home Assistant en `/api/websocket`.
- Estado en tiempo real mediante eventos `state_changed`.
- Arm Home, Away, Night, Vacation y Custom Bypass seg&#250;n `supported_features`.
- Desarmado con c&#243;digo/PIN.
- Token cifrado localmente con AES/GCM y Android Keystore.
- Ingl&#233;s y espa&#241;ol.
- Soporte de HTTP local para instalaciones de laboratorio, con advertencia visible.

Todav&#237;a no incluye MQTT, c&#225;mara, sensores, kiosk mode ni screensaver. Est&#225;n previstos para las siguientes versiones.

## Stack

- Android Gradle Plugin 9.4.0
- Gradle 9.6.0
- Kotlin integrado en AGP 9
- Kotlin Compose compiler plugin 2.2.10
- Compose BOM 2026.09.00
- DataStore 1.2.1
- OkHttp 5.1.0
- JDK 17

## Abrir en Android Studio

1. Descomprime el proyecto.
2. Abre la carpeta `HomePanel-v0.1` en una versi&#243;n reciente de Android Studio compatible con AGP 9.4.
3. Usa JDK 17 para Gradle.
4. Instala Android SDK 37 si Android Studio lo solicita.
5. Sincroniza Gradle.
6. Ejecuta la aplicaci&#243;n en una tablet o emulador Android 8+.

El archivo `gradle/wrapper/gradle-wrapper.properties` fija Gradle 9.6.0. Si necesitas ejecutar desde terminal y tu IDE no ha generado los scripts del wrapper, ejecuta una vez la tarea Gradle `wrapper` desde Android Studio o desde una instalaci&#243;n local de Gradle 9.6.

## Configuraci&#243;n de Home Assistant

Para el MVP se utiliza un Long-Lived Access Token.

1. Crea un token para el usuario que usar&#225; el panel.
2. Abre HomePanel.
3. Introduce la URL completa de Home Assistant, por ejemplo:

   `https://ha.example.com`

4. Pega el token.
5. Pulsa **Detectar alarmas**.
6. Selecciona una entidad `alarm_control_panel`.
7. Pulsa **Guardar y conectar**.

Tambi&#233;n puedes escribir manualmente un entity ID, por ejemplo:

`alarm_control_panel.casa`

## Seguridad

El token se cifra antes de almacenarse en DataStore. La clave AES se mantiene en Android Keystore y no es exportable por la aplicaci&#243;n.

Para producci&#243;n usa `https://` / `wss://`. La aplicaci&#243;n permite `http://` para Home Assistant local, pero en ese caso el token viaja sin TLS y la UI muestra una advertencia.

La configuraci&#243;n de backup Android est&#225; desactivada en esta versi&#243;n para evitar que el blob cifrado del token se transfiera sin su clave de Keystore.

## Arquitectura

```text
Compose UI
   |
MainViewModel
   |-- SettingsRepository -- DataStore -- Android Keystore
   |
   |-- HomeAssistantRestClient
   |       `-- GET /api/states (descubrimiento)
   |
   `-- HomeAssistantWebSocket
           |-- auth
           |-- get_states
           |-- subscribe_events: state_changed
           `-- call_service: alarm_control_panel.*
```

Consulta `ARCHITECTURE.md` y `ROADMAP.md` para el dise&#241;o de las siguientes versiones.

## Home Assistant API

Referencias:

- https://developers.home-assistant.io/docs/api/websocket/
- https://developers.home-assistant.io/docs/api/rest/
- https://developers.home-assistant.io/docs/core/entity/alarm-control-panel/

## Licencia

Apache License 2.0. Consulta `LICENSE` y `NOTICE`.
