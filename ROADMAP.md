# HomePanel roadmap

## Implementado hasta v0.6.9

- Home Assistant WebSocket + Alarmo.
- PIN y estados de alarma con acciones protegidas contra transiciones redundantes.
- Tiempo/ubicacion automatica e interfaz responsive.
- Salvapantallas, reposo, wake por Home Assistant y proximidad Android.
- UniFi Hotspot Manager: crear/borrar voucher, QR, copiar/compartir.
- Camara frontal RTSP H.264 y configuracion Frigate/go2rtc.
- MQTT Discovery: bateria, carga, proximidad, luminosidad, pantalla y RTSP.
- IPv4-first para RTSP y MQTT LAN.
- Brillo automatico por sensor de luz.
- Estado de casa y dashboard contextual.
- Historial persistente.
- Kiosk inmersivo + PIN de configuracion.
- Actualizacion desde GitHub Releases + workflow de publicacion.
- Weather de Home Assistant, forecast mejorado y avisos meteorologicos contextuales.
- Mapa de personas con OpenStreetMap opcional y fallback offline.

## Siguientes candidatos

### v0.6.x
- Mejorar diagnostico MQTT (latencia, IP, RSSI, uptime y version como entidades).
- Permitir seleccionar exactamente las entidades que forman el resumen de casa.
- Presets de vouchers UniFi y cuenta atras de expiracion.
- Mejoras de accesibilidad y tamanos de fuente.

### v0.7.x
- Modo Device Owner opcional para kiosk completo / lock task.
- Lanzamiento robusto despues de reboot mediante provisionamiento kiosk.
- Paginas contextuales configurables (timbre, puerta, camaras, emergencia).
- Integracion mas profunda con Frigate: eventos/detecciones y vista de visitante.
- Assist / voz y TTS.
- Deteccion local de persona con la camara frontal.
