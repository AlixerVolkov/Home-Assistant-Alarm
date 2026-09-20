# HomePanel v0.3.1

## Correcciones

- Corrige el fallo de compilacion de `SetupScreen.kt` causado por usar `maxWidth` desde un receptor Compose implicito dentro de una `Column` anidada. Ahora el breakpoint responsive se calcula en el `BoxWithConstraints` y se pasa como valor local.
- `versionCode` sube a 5 y `versionName` a 0.3.1.
- El workflow ahora genera un APK `release` firmado con una clave persistente guardada en GitHub Actions Secrets.
- El APK se publica con nombre `HomePanel-v0.3.1.apk` y se verifica con `apksigner`.

## Cambio necesario para actualizaciones

Las versiones debug previas generadas por runners diferentes pueden estar firmadas con certificados distintos. Si Android rechaza la actualizacion desde una de esas versiones, hay que desinstalarla una sola vez e instalar v0.3.1. Desde ese momento, mientras se conserve la misma clave de release y aumente `versionCode`, las siguientes versiones podran actualizarse encima.
