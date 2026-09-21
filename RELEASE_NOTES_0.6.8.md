# HomePanel 0.6.8

## Mapa de personas

- Fondo real OpenStreetMap opcional en la vista de `person.*`.
- Selector OpenStreetMap / Offline dentro del mapa.
- Ajuste de zoom con controles + / -.
- Zonas `zone.*` y posiciones `person.*` se dibujan sobre el mapa real.
- Cache HTTP local para no descargar repetidamente los mismos tiles.
- Atribucion visible `© OpenStreetMap contributors`.
- Si OSM no esta accesible, HomePanel mantiene el mapa offline y muestra el host que debe permitirse: `tile.openstreetmap.org:443`.
- Diagnostico de red incluye una prueba OpenStreetMap.

## Avisos meteorologicos

- Un nuevo `binary_sensor.weather_warning*` / MeteoAlarm activo despierta la pantalla.
- Vista contextual de alerta con titulo, severidad, descripcion y hora de expiracion cuando la entidad los proporciona.
- El banner normal permanece visible despues de cerrar el dialogo.
- Inicio/fin de avisos se registra en el historial local.

## Version

- `versionCode = 19`
- `versionName = 0.6.8`
- `applicationId = dev.homepanel.app`
