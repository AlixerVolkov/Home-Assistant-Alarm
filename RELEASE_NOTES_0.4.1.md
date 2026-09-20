# HomePanel v0.4.1

Patch release for the v0.4 feature set.

## Fixed

- Fixed `HomeAssistantRestClient` compilation with OkHttp 5. `HomeAssistantUrl.restUrl()` returns `HttpUrl`, so the authenticated request helper now accepts `HttpUrl` directly instead of `String`.
- Release version increased to `versionCode = 7`, `versionName = 0.4.1`.
- GitHub Actions artifact names updated to `HomePanel-v0.4.1.apk`.

## Features retained from v0.4.0

- Front-camera RTSP server.
- UniFi Hotspot Manager guest voucher creation, code and QR support.
- Android proximity sensor wake from screensaver/sleep.
