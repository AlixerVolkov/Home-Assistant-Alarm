# HomePanel v0.6.8

## v0.6.8: mapa OpenStreetMap + modo contextual de avisos

- El mapa de personas tiene ahora fondo real opcional de OpenStreetMap, con zoom, zonas de Home Assistant y fallback manual al mapa offline.
- Los tiles se solicitan solo para la vista actual, con cache HTTP y atribucion visible de OpenStreetMap.
- El diagnostico de red prueba tambien `tile.openstreetmap.org:443`.
- Los avisos meteorologicos activos despiertan la pantalla, abren una vista contextual y muestran tipo, severidad, descripcion y expiracion cuando estan disponibles.
- Los avisos nuevos y su limpieza se registran en el historial.

## v0.6.7: avisos meteorológicos + mapa de personas + forecast mejorado

- Detecta avisos `binary_sensor.weather_warning*` y MeteoAlarm desde Home Assistant.
- Muestra avisos activos como banner destacado, con severidad/descripción cuando la entidad los expone.
- Añade un mapa offline de `person.*` usando coordenadas y `zone.*`, sin depender de Google Maps/OpenStreetMap ni abrir más firewall.
- Si una persona está en una zona pero HA no expone GPS, HomePanel usa el centro de esa zona para representarla.
- Forecast de 5 días rediseñado con condición, máximas/mínimas, probabilidad y cantidad de precipitación, viento, humedad/presión/UV cuando el proveedor los ofrece.


Panel Android moderno para Home Assistant / Alarmo, pensado para tablets de pared.

## v0.6.6: Weather de Home Assistant + diagnóstico de red

- Nueva fuente de tiempo **Home Assistant** (predeterminada), usando una entidad `weather.*`.
- HomePanel obtiene el estado actual desde la entidad y el pronóstico mediante `weather.get_forecasts`.
- Open-Meteo sigue disponible como opción alternativa directa.
- Con Home Assistant como fuente del tiempo, la tablet no necesita permiso de ubicación ni salida directa a `api.open-meteo.com`.
- Nueva pantalla de **Diagnóstico de red** para probar LAN/IPv4, Home Assistant, weather de HA, Open-Meteo, GitHub, MQTT y el listener RTSP local.

## Funciones principales

- Conexion directa a Home Assistant por WebSocket.
- Alarmo / `alarm_control_panel`: Home, Away, Night, Vacation, Custom Bypass y Disarm con PIN cuando corresponde.
- Estado en tiempo real y proteccion contra enviar una transicion al estado que ya esta activo.
- Tiempo y ubicacion automaticos segun el dispositivo.
- Salvapantallas, reposo y despertar por sensor HA o proximidad Android.
- Interfaz adaptable a telefono/tablet, portrait/landscape y ventanas redimensionables.
- UniFi Hotspot Manager: crear/borrar voucher, QR, copiar y compartir codigo.
- Camara frontal H.264 por RTSP para Frigate/go2rtc.
- MQTT Discovery para registrar HomePanel como dispositivo en Home Assistant.
- Luminosidad ambiente, bateria, carga, proximidad, pantalla y diagnostico RTSP publicados por MQTT.
- Brillo automatico usando el sensor de luz Android.
- Estado resumido de la casa y dashboard contextual de alarma.
- Historial reciente persistente.
- Modo kiosk inmersivo con PIN para configuracion.
- Comprobacion y descarga de actualizaciones desde GitHub Releases.

## RTSP y Frigate

HomePanel v0.6.0 **prefiere IPv4** para la URL que presenta al usuario. Busca la IPv4 de la red activa de Android antes de recorrer otras interfaces. Si la tablet dispone, por ejemplo, de `192.168.1.74`, mostrara:

```text
rtsp://192.168.1.74:8554/
```

En Ajustes > Camara frontal RTSP puedes:

- ver la IPv4 detectada y la interfaz,
- fijar manualmente un host/IP anunciado si Android elige una interfaz incorrecta,
- copiar la URL RTSP,
- copiar un bloque de configuracion listo para Frigate/go2rtc.

Ejemplo:

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
    detect:
      width: 1280
      height: 720
```

La camara es video H.264 1280x720 a 15 fps, sin audio en esta version.

## HomePanel como dispositivo MQTT

Con MQTT Discovery activado se publican, entre otras:

- `sensor.homepanel_battery`
- `sensor.homepanel_battery_temperature`
- `binary_sensor.homepanel_charging`
- `binary_sensor.homepanel_proximity`
- `sensor.homepanel_illuminance`
- `switch.homepanel_screen`
- `sensor.homepanel_display_mode`
- `binary_sensor.homepanel_rtsp_server`
- `sensor.homepanel_rtsp_clients`
- `sensor.homepanel_front_camera_rtsp`

Los `unique_id` incorporan el Android ID para permitir varios paneles.

## Actualizaciones desde HomePanel

La app consulta el ultimo **GitHub Release** de este repositorio. Para que exista un APK descargable debes publicar una release, no solo ejecutar el workflow de build.

Hay dos workflows:

- **Build signed Android APK**: compila y deja un artifact de GitHub Actions.
- **Publish HomePanel Release**: compila el mismo APK firmado y crea/actualiza la release de la version actual en GitHub Releases con su APK firmado.

Ejecuta `Publish HomePanel Release` manualmente desde Actions cuando quieras distribuir una version.

## Firma

Mantener siempre:

```text
applicationId = dev.homepanel.app
mismo keystore de release
versionCode creciente
```

Version actual:

```text
versionCode = 19
versionName = 0.6.8
```

## Kiosk

El modo kiosk de v0.6.0 oculta barras de sistema y puede exigir PIN para entrar en Configuracion. No convierte automaticamente el dispositivo en Android Device Owner; un bloqueo total de cambio de aplicaciones requiere provisionamiento adicional y queda como mejora futura.

## Estabilidad y diagnostico (v0.6.4)

- RTSP y MQTT son modulos lazy: si estan desactivados no se instancian durante el arranque.
- Tras un cierre Java/Kotlin inesperado, el siguiente arranque muestra un informe copiable con el stack trace.
- El primer arranque tras un crash usa Modo seguro durante esa sesion: RTSP, MQTT, sensores y chequeos automaticos de actualizacion quedan temporalmente desactivados para comprobar si el nucleo de alarma es estable.
- Si la aplicacion vuelve a cerrarse y no aparece ningun informe al siguiente arranque, el fallo puede ser nativo/driver o un cierre del proceso por Android; en ese caso necesitaremos logcat.


## Android 17 permissions (v0.6.1)

HomePanel serializes Local network, Camera and Location permission requests. On Android 17+ it first explains and requests `ACCESS_LOCAL_NETWORK`; only after that flow finishes does it request camera/location permissions. MQTT, Home Assistant LAN access and RTSP are retried when Local network access is granted.

HomePanel also checks Android unused-app restrictions. For a permanently mounted panel, disabling **Pause app activity if unused / Manage app if unused** is recommended. Android does not allow a normal app to switch this off silently, so HomePanel opens the official system settings page for the user to confirm it.
