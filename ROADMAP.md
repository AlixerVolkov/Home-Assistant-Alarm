# Roadmap

## v0.1 - Home Assistant alarm MVP

- [x] Compose UI
- [x] Home Assistant URL/token setup
- [x] Alarm discovery
- [x] Secure local token storage
- [x] WebSocket authentication
- [x] Initial state loading
- [x] Real-time state changes
- [x] Arm/disarm actions
- [x] Supported-feature filtering
- [x] English/Spanish resources

## v0.2 - Reliability and authentication

- [ ] OAuth-style Home Assistant authentication flow
- [ ] Automatic reconnect with exponential backoff
- [ ] Network reachability handling
- [ ] Multiple Home Assistant URLs (internal/external)
- [ ] Better service-call result feedback
- [ ] Unit tests with MockWebServer
- [ ] CI build and lint

## v0.3 - MQTT device bridge

- [ ] MQTT over TLS
- [ ] MQTT Discovery
- [ ] Battery sensor
- [ ] Charging state
- [ ] Screen state
- [ ] Brightness control
- [ ] Motion sensor publishing
- [ ] Remote commands

## v0.4 - Wall panel / kiosk

- [ ] Immersive kiosk mode
- [ ] Keep screen awake policy
- [ ] Wake on motion
- [ ] Screensaver clock
- [ ] Day/night brightness profiles
- [ ] Optional Home Assistant dashboard WebView

## v0.5 - Camera

- [ ] CameraX
- [ ] Snapshot endpoint or MQTT transport
- [ ] Local motion detection
- [ ] Privacy controls
- [ ] Home Assistant camera entity integration

## v1.0

- [ ] Guided onboarding
- [ ] Multiple alarm panels
- [ ] Multiple tablet profiles
- [ ] Signed release APK/AAB
- [ ] Upgrade/migration strategy
- [ ] Documentation and screenshots

## v0.5.0 - Home Assistant device registration

- [x] MQTT Discovery device
- [x] Battery percentage and battery temperature
- [x] Charging state
- [x] Android proximity state
- [x] Remote screen switch
- [x] Display mode diagnostics
- [x] RTSP status, client count and URL diagnostics
- [ ] Native `camera.*` entity (requires snapshot or custom HA integration; RTSP URL alone is not an MQTT Camera payload)
