# HomePanel v0.5.0

## Home Assistant device registration

- MQTT Discovery opcional.
- Device Registry con identificador estable por Android ID.
- Battery percentage and battery temperature.
- Charging state.
- Android proximity sensor.
- Display mode.
- Screen switch with bidirectional state.
- RTSP server status, client count and URL.
- MQTT availability with retained Last Will.
- Automatic rediscovery on the Home Assistant MQTT birth topic.
- MQTT password encrypted in Android Keystore.

## Nota sobre camera.*

La camara frontal sigue servida por RTSP. Esta version expone la URL RTSP como entidad de diagnostico, pero no publica JPEGs por MQTT, por lo que no crea aun una entidad MQTT Camera nativa.
