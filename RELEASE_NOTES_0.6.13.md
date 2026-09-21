# HomePanel v0.6.13

## Fixes

- Fix Alarmo `context_id`: Alarmo expects an integer, so HomePanel now sends the WebSocket request id as an integer instead of a string. This fixes the `expected int at context_id` error when arming modes such as Night.
- Proximity wake now reacts to both NEAR and genuine near/far transitions. This helps vendor virtual proximity implementations that do not behave like a classic distance sensor.
- Proximity diagnostics now count received sensor events and warn when a Samsung Palm Proximity virtual sensor is detected.
- Added an optional ambient-light wave fallback. A fast, large light change while the panel is sleeping can wake HomePanel, providing a practical fallback on devices whose proximity sensor is restricted to system/phone use.

## Version

- `versionCode = 24`
- `versionName = 0.6.13`
