# HomePanel v0.3.1

A modern Android wall panel for Home Assistant / Alarmo.

## v0.3.1 highlights

- Home Assistant WebSocket alarm control with live state updates.
- Automatic device language (English, Spanish, Dutch and French resources included).
- Automatic city / region / weather coordinates from Android location permission.
- Weather follows the device location and local time zone automatically.
- Responsive Compose UI for phones, tablets, portrait, landscape and resizable windows.
- Configurable screen saver timeout and low-power sleep timeout.
- Screen saver shows clock, date, weather and alarm state and moves periodically to reduce burn-in risk.
- Wake on Home Assistant motion / occupancy / presence sensor.
- Wake on the tablet proximity sensor when hardware supports it.
- Alarm `triggered`, `pending`, `arming` and `disarming` events wake the panel automatically.
- Sleep mode stays connected and uses a nearly black, very dim screen so wake-on-detection remains reliable.

## First start

1. Allow approximate or precise location if you want automatic local weather.
2. Enter the Home Assistant URL and a long-lived access token.
3. Tap **Discover devices**.
4. Select the `alarm_control_panel` entity.
5. Optionally select a `binary_sensor` with device class motion, occupancy or presence to wake the panel.
6. Choose the screen saver and sleep inactivity times.
7. Save and connect.

Existing v0.2.x settings are migrated automatically. The new wake sensor remains optional and default screen saver / sleep timers are 2 and 10 minutes.

## GitHub Actions APK

Push the project to `main`. The included workflow builds the debug APK. Download the artifact named:

`HomePanel-v0.3.1-debug-apk`

The APK inside the artifact is `app-debug.apk`.

## Notes about sleep

HomePanel deliberately uses a low-power in-app sleep mode instead of fully powering the display off. This keeps the WebSocket connection and detection logic alive so the panel can wake immediately when a Home Assistant motion sensor, Alarmo event or local proximity sensor fires.

## Security

The access token is stored encrypted with Android Keystore. Prefer HTTPS/WSS for Home Assistant.


## Firma y actualizaciones

Para generar APKs que puedan actualizar versiones anteriores sin perder datos, configura una firma estable siguiendo `SIGNING_WINDOWS.md`.
