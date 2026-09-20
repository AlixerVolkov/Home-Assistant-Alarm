# HomePanel v0.4.2

Panel Android moderno para Home Assistant / Alarmo, pensado para tablets de pared.

## Novedades v0.4.2

- **Borrar vouchers UniFi** desde la ventana de Wi-Fi para visitas, con confirmación antes de ejecutar la acción.
- Detección automática de `button.<config_id>_delete` de **UniFi Hotspot Manager**.
- **RTSP ya no ocupa espacio en la pantalla principal**. La dirección y el estado están solo en **Configuración**.
- Botón **Copiar dirección RTSP** para enviar `rtsp://...` directamente al portapapeles de Android.
- Compatibilidad de actualización desde v0.4.1: si ya existía `button.<config_id>_create`, HomePanel puede derivar automáticamente el botón `*_delete` habitual.
- Mantiene cámara frontal RTSP, Alarmo, QR de invitados, sensor de proximidad Android, tiempo por ubicación automática, idiomas del sistema, UI responsive y salvapantallas/reposo.

## Configuración

1. Introduce la URL y el Long-Lived Access Token de Home Assistant.
2. Pulsa **Detectar dispositivos**.
3. Selecciona `alarm_control_panel`.
4. Opcionalmente selecciona el sensor de movimiento/presencia para despertar.
5. Si se detecta **UniFi Hotspot Manager**, selecciona la red de invitados.
6. Activa/desactiva el sensor de proximidad propio de la tablet.
7. Opcionalmente activa **Cámara frontal RTSP** y elige el puerto (8554 por defecto).
8. Guarda y conecta.

## UniFi Hotspot Manager

HomePanel reconoce:

- `button.<config_id>_create`
- `button.<config_id>_delete`
- `sensor.<config_id>_voucher`
- `image.<config_id>_qr_code`

La entidad `image.*_qr_code` puede venir deshabilitada por defecto en Home Assistant. Si no aparece el QR, actívala desde **Ajustes → Dispositivos y servicios → Entidades**.

Desde la ventana de invitados puedes:

- crear un voucher;
- ver código y QR;
- ver duración/estado;
- borrar el voucher mostrado, con confirmación.

## RTSP

La cámara RTSP usa solo la cámara frontal y no transmite audio. La información RTSP se muestra únicamente en **Configuración**.

Ejemplo:

```text
rtsp://192.168.1.50:8554/
```

Desde esa pantalla puedes copiar la URL al portapapeles con un toque.

RTSP sigue estando pensado para una LAN/VLAN de confianza: esta versión no añade autenticación ni TLS al stream.

## Compilación y actualizaciones

```text
applicationId = dev.homepanel.app
versionCode = 8
versionName = 0.4.2
```

GitHub Actions genera un APK release firmado con los mismos secrets permanentes:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Artefacto esperado:

```text
HomePanel-v0.4.2-signed-apk
└── HomePanel-v0.4.2.apk
```

Mientras conserves la misma clave `.jks`, esta versión podrá instalarse encima de las releases firmadas anteriores.
