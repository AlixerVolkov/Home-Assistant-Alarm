# HomePanel 0.6.5

## MQTT stability

This release fixes the crash reported as Paho `MqttException(32202)` / too many publishes in progress.

- Conflates telemetry so only the newest snapshot is queued.
- Uses retained QoS 0 for state telemetry, preserving the latest Home Assistant state without filling the QoS 1 in-flight window.
- Skips unchanged state values.
- Stops ambient-light sensor callbacks from publishing a full MQTT batch on every sensor event.
- Contains MQTT publish failures so broker backpressure cannot terminate the Android process.
- Prevents duplicate MQTT session initialization when Paho's callback races the initial connect completion.

`versionCode = 16`, `versionName = 0.6.5`.
