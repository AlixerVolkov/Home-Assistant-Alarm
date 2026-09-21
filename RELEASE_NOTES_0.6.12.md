# HomePanel 0.6.12

## Alarmo bypass confirmation

- When Alarmo rejects arming because protected sensors are open, HomePanel now opens a confirmation dialog listing the blocking sensors.
- The user can close the sensors and retry, cancel, or choose **Bypass and arm**.
- Bypass retry uses Alarmo's documented `alarmo.arm` service with `force: true`, so all sensors that are currently blocking that arm request are bypassed until the alarm is disarmed.
- HomePanel resolves friendly names for blocking sensors when Home Assistant provides them.
- The PIN/code from the failed arm request is kept only in memory long enough to perform the retry; it is not exposed in UI state or persisted.

### Alarmo limitation
Alarmo's public arm service supports `force: true` for all currently open blocking sensors. It does not expose a supported service to selectively bypass only some of those sensors for a single arm operation.
