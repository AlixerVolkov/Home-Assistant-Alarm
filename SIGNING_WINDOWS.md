# HomePanel: firma estable para poder actualizar la app

Android solo permite instalar una version nueva encima de una existente si conserva el mismo `applicationId`, esta firmada con la misma clave y usa un `versionCode` superior.

Las compilaciones `debug` de runners efimeros de GitHub Actions pueden terminar usando una clave debug diferente. Para HomePanel se usa desde v0.3.1 una clave de release propia y persistente almacenada como secretos de GitHub.

## Importante: una desinstalacion unica

Si la version que tienes instalada fue un `app-debug.apk` generado por un run anterior, probablemente esta firmada con otra clave. No se puede convertir esa instalacion a la nueva firma.

1. Si necesitas conservar algun dato/configuracion, anotala primero.
2. Desinstala HomePanel una sola vez.
3. Instala `HomePanel-v0.3.1.apk`.
4. A partir de ahi NO cambies ni pierdas la clave `.jks`: futuras versiones firmadas con ella podran instalarse encima conservando datos.

## 1. Crear la clave en Windows

Necesitas `keytool`, incluido con un JDK (Android Studio tambien incluye un JDK/JBR).

Abre PowerShell en una carpeta privada y ejecuta:

```powershell
keytool -genkeypair -v -keystore homepanel-release.jks -alias homepanel -keyalg RSA -keysize 4096 -validity 10000
```

El comando te pedira una contrasena. Guardala. No subas `homepanel-release.jks` al repositorio.

Haz una copia de seguridad offline del archivo `.jks` y de sus contrasenas. Si pierdes esta clave, Android no aceptara futuras actualizaciones sobre instalaciones firmadas con ella.

## 2. Convertir la clave a Base64

En la misma carpeta, PowerShell:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("homepanel-release.jks")) | Set-Content -NoNewline homepanel-release.base64.txt
```

Abre `homepanel-release.base64.txt` y copia todo su contenido.

## 3. Crear secretos en GitHub

En tu repositorio:

`Settings` -> `Secrets and variables` -> `Actions` -> `New repository secret`

Crea exactamente estos cuatro secretos:

- `ANDROID_KEYSTORE_BASE64`: contenido completo de `homepanel-release.base64.txt`
- `ANDROID_KEYSTORE_PASSWORD`: contrasena del keystore
- `ANDROID_KEY_ALIAS`: `homepanel`
- `ANDROID_KEY_PASSWORD`: contrasena de la clave (si usaste la misma, repite la del keystore)

## 4. Compilar

Haz push de v0.3.1 a `main` o ejecuta manualmente:

`Actions` -> `Build signed Android APK` -> `Run workflow`

El artefacto final se llama:

`HomePanel-v0.3.1-signed-apk`

Y contiene:

- `HomePanel-v0.3.1.apk`
- `HomePanel-v0.3.1.apk.sha256`

## Actualizaciones futuras

Para v0.3.2, v0.4.0, etc. deben mantenerse siempre:

- `applicationId = "dev.homepanel.app"`
- la misma clave de firma
- un `versionCode` mayor que el anterior

No cambies los cuatro secretos salvo que estes restaurando exactamente la misma clave y contrasenas.
