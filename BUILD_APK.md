# Build del APK

## GitHub Actions (recomendado)

El proyecto incluye `.github/workflows/build-apk.yml`.

1. Sube esta carpeta a un repositorio de GitHub.
2. Abre la pestana **Actions**.
3. Selecciona **Build Android APK**.
4. Pulsa **Run workflow**.
5. Al finalizar, descarga el artefacto `HomePanel-v0.1-debug-apk`.

El APK generado se llama `app-debug.apk`.

## Build local

Requisitos:

- JDK 17
- Android SDK Platform 37
- Android Build Tools 36.0.0
- Gradle 9.6.0

Desde la raiz del proyecto:

```bash
gradle --no-daemon :app:assembleDebug
```

Salida:

```text
app/build/outputs/apk/debug/app-debug.apk
```
