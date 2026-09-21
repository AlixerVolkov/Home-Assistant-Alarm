# HomePanel 0.6.3

RTSP stability release.

- RTSP front camera now uses the Camera1 compatibility backend instead of Camera2.
- RTSP-Server 1.4.2 is paired again with RootEncoder 2.8.0, matching the upstream documented dependency combination.
- Camera/encoder startup and RTSP statistics run on a dedicated worker thread instead of the UI thread.
- Added an RTSP startup crash guard. If Android terminates the process while the camera is opening, the next launch blocks automatic RTSP startup instead of entering a crash loop.
- To retry after a safety block: disable RTSP in Settings, save, then enable it again.
- H.264 fallback remains 1280x720 -> 640x480 and the advertised RTSP endpoint remains IPv4-first.

Version: `0.6.3` / versionCode `14`.
