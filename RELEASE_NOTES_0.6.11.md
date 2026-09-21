# HomePanel 0.6.11

- Fix Alarmo arm mode handling, including **Armed Night**.
- HomePanel now detects Alarmo entities and uses the native `alarmo.arm` service with `mode: home/away/night/vacation/custom`.
- Alarmo disarm uses `alarmo.disarm`.
- Subscribes to `alarmo_failed_to_arm` so failed commands now show the real reason (open sensors, invalid code, or operation not allowed).
- Reads Alarmo `open_sensors` attributes as an additional failure diagnostic.
- Standard Home Assistant alarm panels continue using the normal `alarm_control_panel.*` services.

Version: `0.6.11` (`versionCode 22`).
