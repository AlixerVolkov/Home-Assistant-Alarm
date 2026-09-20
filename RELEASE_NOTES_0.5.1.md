# HomePanel 0.5.1

Correccion de conectividad LAN/MQTT:

- evita el timeout inicial de MQTT mientras Android 17 espera ACCESS_LOCAL_NETWORK;
- reintenta MQTT en cuanto el permiso es concedido;
- prefiere IPv4 para MQTT local sin TLS;
- prueba TCP del broker antes del handshake MQTT y ofrece errores mas utiles;
- muestra la URL RTSP con IPv4 LAN cuando existe, mejor para Frigate/go2rtc.

Version: `versionCode 10`, `versionName 0.5.1`.
