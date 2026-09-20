# HomePanel v0.4.1

Panel Android moderno para Home Assistant / Alarmo, pensado para tablets de pared.

## Novedades v0.4.1

- **Cámara frontal RTSP** opcional, solo vídeo, por defecto en el puerto `8554`.
- El panel muestra la URL `rtsp://...` y el número de clientes conectados.
- **Wi-Fi para visitas con UniFi Hotspot Manager**: detección automática de las entidades de voucher, botón para crear un vale y ventana con código + QR.
- **Sensor de proximidad Android mejorado**: solo escucha durante salvapantallas/reposo, despierta en el flanco lejos→cerca y aplica debounce para evitar falsos despertares repetidos.
- Solicitud del permiso **ACCESS_LOCAL_NETWORK** en Android 17 / API 37 para Home Assistant y RTSP en la LAN.
- Mantiene todas las funciones de v0.3.x: Alarmo, tiempo por ubicación automática, idiomas del sistema, interfaz responsive, salvapantallas y despertar por sensores de Home Assistant.

## Configuración

1. Introduce la URL y el Long-Lived Access Token de Home Assistant.
2. Pulsa **Detectar dispositivos**.
3. Selecciona `alarm_control_panel`.
4. Opcionalmente selecciona el sensor de movimiento/presencia para despertar.
5. Si se detecta **UniFi Hotspot Manager**, selecciona la red de invitados. Si solo hay una, HomePanel la selecciona automáticamente.
6. Activa/desactiva el sensor de proximidad propio de la tablet.
7. Opcionalmente activa **Cámara frontal RTSP** y elige el puerto (8554 por defecto).
8. Guarda y conecta.

## UniFi Hotspot Manager

HomePanel busca el conjunto de entidades:

- `button.<config_id>_create`
- `sensor.<config_id>_voucher`
- `image.<config_id>_qr_code`

La entidad `image.*_qr_code` puede venir deshabilitada por defecto en Home Assistant. Si no aparece el QR, actívala desde **Ajustes → Dispositivos y servicios → Entidades** y vuelve a abrir/actualizar la pantalla de invitados.

El botón de HomePanel llama a `button.press` sobre `button.<config_id>_create`, usando así los valores de duración/cuota configurados en la propia integración.

## RTSP

Al activarlo, HomePanel solicita permiso de cámara y utiliza **solo la cámara frontal**. No transmite micrófono ni audio.

Ejemplo:

```text
rtsp://192.168.1.50:8554/
```

El RTSP está pensado para la red local. El stream se mantiene mientras HomePanel está ejecutándose como panel; Android puede restringir el acceso a cámara si el usuario manda la app completamente al segundo plano.

## Compilación y actualizaciones

La aplicación conserva:

```text
applicationId = dev.homepanel.app
versionCode = 6
versionName = 0.4.1
```

El workflow de GitHub Actions genera un APK release firmado usando los mismos cuatro secrets de firma de v0.3.1:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Artefacto esperado:

```text
HomePanel-v0.4.1-signed-apk
└── HomePanel-v0.4.1.apk
```

No cambies ni pierdas el `.jks`: las actualizaciones Android requieren la misma clave de firma.

## Seguridad

El token de Home Assistant se almacena cifrado mediante Android Keystore. Usa HTTPS/WSS para Home Assistant siempre que sea posible. RTSP v0.4.1 es un stream local sin TLS ni autenticación; úsalo solo en una LAN/VLAN de confianza.
