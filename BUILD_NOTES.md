# Build notes — HomePanel v0.4.0

El workflow incluido compila `:app:assembleRelease` con JDK 17, Gradle 9.6, AGP 9.4 y Android SDK 37.0, restaura la misma clave release desde GitHub Secrets y verifica el APK con `apksigner`.

Nuevas dependencias JitPack:

- `com.github.pedroSG94:RTSP-Server:1.4.2`
- `com.github.pedroSG94.RootEncoder:library:2.8.0`

El contenedor usado para preparar este paquete no dispone del Android SDK/Gradle completo con acceso a repositorios, por lo que la compilación Android final debe validarse con el workflow de GitHub Actions incluido.
