# HomePanel v0.6.10

## Fix: selector de sensores de Home Assistant

La v0.6.9 filtraba el combo de despertar por `device_class` y solo aceptaba `motion`, `occupancy` y `presence`. Varias integraciones de Home Assistant usan otra clase o no informan `device_class`, por lo que el combo podía quedar vacío.

Cambios:

- El descubrimiento incluye todos los `binary_sensor.*`.
- Los sensores motion/occupancy/presence se ordenan primero.
- Descubrimiento automático al abrir Configuración cuando ya existen URL y token guardados.
- Indicador del número de sensores encontrados.
- Se mantiene la entrada manual de `entity_id`.

Version: `0.6.10`
Version code: `21`
