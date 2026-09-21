# HomePanel 0.6.2

Network/RTSP reliability release.

- MQTT plain TCP is explicitly bound to the tablet Wi-Fi/Ethernet Android `Network` instead of the process default route. This avoids VPN/default-route issues.
- MQTT DNS resolution is performed through the selected LAN network and MQTT 3.1.1 is selected explicitly.
- MQTT status now shows LAN transport, tablet IPv4, resolved broker address and whether the TCP port is reachable.
- RTSP uses the same LAN selector and keeps IPv4-first advertising.
- RTSP Camera2 startup order follows RootEncoder requirements: prepare H.264 encoder first, select front camera, then start the server/stream.
- RTSP falls back from 1280x720 to 640x480 when the device encoder cannot prepare 720p.
- RootEncoder updated from 2.8.0 to 2.8.1.

Version: `0.6.2` / versionCode `13`.
