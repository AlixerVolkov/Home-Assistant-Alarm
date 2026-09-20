# HomePanel v0.5.1

Panel Android moderno para Home Assistant / Alarmo, pensado para tablets de pared.

## Correcciones v0.5.1

- MQTT espera el permiso de red local de Android 17 antes de conectar.
- MQTT se reintenta automaticamente al conceder el permiso.
- MQTT sin TLS prefiere IPv4 cuando el broker resuelve a IPv4 e IPv6.
- Se hace una prueba TCP previa para distinguir puerto/firewall/VLAN de errores MQTT.
- La URL RTSP mostrada prefiere la IPv4 LAN de la tablet para Frigate/go2rtc.


## Novedades v0.5.0

- **HomePanel puede registrarse como un dispositivo real de Home Assistant mediante MQTT Discovery**.
- La configuracion MQTT es opcional y se realiza desde la propia pantalla de Ajustes.
- La contrasena MQTT se guarda cifrada con Android Keystore, igual que el token de Home Assistant.
- Publica automaticamente disponibilidad y vuelve a enviar el discovery cuando Home Assistant publica `homeassistant/status = online`.
- El sensor de proximidad Android se mantiene activo cuando esta habilitado, de modo que tambien puede verse desde Home Assistant.
- El interruptor `switch.homepanel_screen` permite despertar el panel o enviarlo a reposo desde Home Assistant.
- Mantiene Alarmo, UniFi vouchers, QR, RTSP, tiempo automatico, salvapantallas, reposo e interfaz responsive.

## Entidades MQTT

Una vez activado MQTT Discovery, Home Assistant agrupa estas entidades bajo un unico dispositivo **HomePanel**:

- `sensor.homepanel_battery`
- `sensor.homepanel_battery_temperature`
- `binary_sensor.homepanel_charging`
- `binary_sensor.homepanel_proximity`
- `switch.homepanel_screen`
- `sensor.homepanel_display_mode`
- `binary_sensor.homepanel_rtsp_server`
- `sensor.homepanel_rtsp_clients`
- `sensor.homepanel_front_camera_rtsp`

Home Assistant puede anadir un sufijo al `entity_id` si ya existe una entidad con el mismo nombre. Los `unique_id` y el identificador del dispositivo se derivan del Android ID del panel, por lo que varios paneles pueden convivir en el mismo broker.

### Camara

La v0.5.0 registra la URL RTSP y el estado del servidor como entidades de diagnostico. **No crea todavia una entidad `camera.*` nativa**, porque MQTT Camera espera frames de imagen por MQTT y no una URL RTSP. El stream sigue disponible mediante `rtsp://...` para go2rtc, Frigate, VLC u otra integracion compatible.

## Configuracion MQTT

Necesitas tener configurada la integracion MQTT de Home Assistant y conocer los datos de acceso al broker.

En HomePanel:

1. Abre **Configuracion**.
2. Activa **Registrar HomePanel por MQTT**.
3. Introduce host, puerto, usuario y contrasena del broker.
4. Activa TLS solo si el broker usa un certificado confiable para Android; normalmente TLS usa 8883 y MQTT local sin TLS usa 1883.
5. Guarda.

HomePanel publica discovery retenido y disponibilidad (`online/offline`). Home Assistant MQTT tambien publica su mensaje de nacimiento en `homeassistant/status`, que HomePanel usa para volver a anunciar el dispositivo tras un reinicio.

## Actualizaciones

```text
applicationId = dev.homepanel.app
versionCode = 9
versionName = 0.5.1
```

El APK release se firma con los mismos GitHub Actions secrets de las versiones anteriores. Puede instalarse encima de la v0.4.x/v0.3.1 firmada con la misma clave.

Artefacto esperado:

```text
HomePanel-v0.5.1-signed-apk
└── HomePanel-v0.5.1.apk
```
