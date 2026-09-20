# HomePanel 0.6.1

## Android 17 local-network permission fix

- Serializes runtime permission requests so Android is never asked for Local network, Camera and Location at the same time.
- Shows an explanatory HomePanel dialog before requesting `ACCESS_LOCAL_NETWORK` on Android 17+.
- Retries Home Assistant, MQTT and RTSP immediately after Local network access is granted.
- If Local network access is denied, HomePanel explains that LAN services may time out and provides a direct shortcut to Android App info.
- Camera permission is requested only after Local network access is ready, avoiding overlapping permission dialogs.

## Unused-app / hibernation protection

- Detects Android unused-app restrictions / app hibernation.
- Explains why a permanently mounted HomePanel should be exempt.
- Opens the official Android settings screen where the user can disable “Pause app activity if unused”, “Manage app if unused”, or the equivalent device-specific option.
- HomePanel does not silently change this system setting; Android requires the user to make the choice.

## Version

- `versionCode = 12`
- `versionName = 0.6.1`
