# HomePanel 0.6.7

## Weather warnings

- Detección automática de `binary_sensor.weather_warning*`.
- Compatibilidad adicional con entidades MeteoAlarm.
- Banner para avisos activos con título, severidad, descripción y expiración cuando Home Assistant lo ofrece.
- `binary_sensor.weather_warning_*` no forma parte del contrato estándar de `weather.*`; depende de la integración/configuración.

## People map

- Nuevo mapa offline de personas basado exclusivamente en `person.*` y `zone.*` de Home Assistant.
- No necesita acceso a servicios de mapas externos.
- Usa `latitude`, `longitude` y `gps_accuracy` cuando están disponibles.
- Si Home Assistant omite coordenadas mientras la persona está en una zona, se usa el centro de `zone.home` u otra zona conocida.

## Forecast

- Nueva tarjeta de previsión de 5 días.
- Condición, temperatura máxima/mínima, probabilidad de precipitación, precipitación en mm y viento cuando el proveedor lo ofrece.
- Métricas actuales de humedad, presión y UV cuando existen en `weather.*`.

## Build

- `versionCode = 18`
- `versionName = 0.6.7`
- `applicationId = dev.homepanel.app`
