# HomePanel v0.4.0

## Cámara frontal RTSP

- Servidor RTSP local opcional con Camera2 / H.264.
- Cámara frontal seleccionada explícitamente.
- Vídeo 1280×720, 15 fps y ~1.5 Mbps por defecto.
- Sin audio/micrófono.
- Puerto configurable, 8554 por defecto.
- Estado, URL y número de clientes visibles en el dashboard.
- Permisos CAMERA y ACCESS_LOCAL_NETWORK gestionados en runtime.

## UniFi Hotspot Manager

- Detección automática de `sensor.*_voucher` + `button.*_create` + `image.*_qr_code`.
- Selección automática cuando solo existe una configuración.
- Botón para crear un voucher desde el panel.
- Código del voucher, SSID, duración, estado y QR en un diálogo amigable.
- Mensaje explícito si la entidad QR está deshabilitada/no disponible.

## Proximidad

- El sensor local Android se registra únicamente en salvapantallas/reposo.
- Despertar por transición FAR→NEAR.
- Debounce de 900 ms para evitar eventos repetidos.
- Se puede desactivar desde Ajustes.

## Actualización

- `versionCode = 6`
- `versionName = 0.4.0`
- Se mantiene `applicationId = dev.homepanel.app` y el mismo esquema de firma release para actualizar sobre v0.3.1.
