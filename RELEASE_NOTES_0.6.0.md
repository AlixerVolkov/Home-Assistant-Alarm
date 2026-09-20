# HomePanel v0.6.0

HomePanel v0.6.0 convierte el panel de alarma en un panel domestico mas completo y anade las mejoras priorizadas para tablets de pared.

## Novedades

- **RTSP IPv4-first para Frigate/go2rtc**: HomePanel busca primero la IPv4 de la red activa mediante Android ConnectivityManager y solo cae a otras interfaces si hace falta. Se puede fijar manualmente la IPv4/hostname anunciado.
- **Configuracion Frigate lista para copiar** desde Ajustes. El bloque crea `go2rtc.streams.homepanel_front` y usa el restream local `rtsp://127.0.0.1:8554/homepanel_front` para deteccion.
- **Panel de estado de la casa**: puertas, ventanas, luces encendidas, personas en casa y una temperatura interior representativa.
- **Dashboard contextual**: estados `triggered`, `pending`, `arming` y `disarming` reciben un banner prioritario.
- **Sensor de luminosidad Android**: publicado por MQTT Discovery como `sensor.homepanel_illuminance`.
- **Brillo automatico** segun los lux medidos por el panel.
- **Modo visitante a pantalla completa** con QR grande, SSID, codigo, copiar, compartir, crear y borrar voucher UniFi.
- **Modo kiosk**: interfaz inmersiva y PIN opcional para abrir Configuracion. El bloqueo completo tipo Device Owner queda fuera de esta version.
- **Actualizaciones desde la app**: consulta GitHub Releases, descarga el APK y abre el instalador Android.
- **Workflow Publish HomePanel Release** para publicar manualmente un GitHub Release con el APK firmado, necesario para que la actualizacion in-app tenga un asset que descargar.
- **Historial reciente persistente** de cambios de alarma, conexion, puertas/ventanas, luces, presencia y vouchers.

## Version

- `versionCode = 11`
- `versionName = 0.6.0`

## Actualizacion

Si las versiones anteriores y esta se firman con el mismo keystore release, `HomePanel-v0.6.0.apk` se puede instalar directamente encima y conserva la configuracion.
