# HomePanel 0.6.14

- Multiple Home Assistant wake sensors can be selected at once.
- Searchable multi-select makes Frigate / motion / occupancy / presence entities easy to find.
- New hardware diagnostics dialog with live proximity/light event counters, cameras, battery and memory.
- Alarmo readiness is monitored via `alarmo_ready_to_arm_modes_updated`.
- Arming buttons can show ready/not-ready state when Alarmo readiness data is available.
- If Alarmo reports a mode is not ready, HomePanel warns before sending the arm command and offers normal arm or force/bypass.
- Existing single wake sensor setting is migrated automatically.

Version: `0.6.14` (`versionCode 25`).
