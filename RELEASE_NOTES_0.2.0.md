# HomePanel 0.2.0

## Main changes

- New wall-panel dashboard interface with large visual alarm modes.
- Distinct visual icons for Disarmed, Home, Away, Night, Vacation and Custom modes.
- Current alarm mode is clearly marked and its own action is disabled.
- Prevents sending invalid/redundant transitions such as `disarmed -> disarmed`, which avoids Alarmo's `Cannot go to state disarmed from state disarmed` warning.
- PIN dialog appears only when the selected alarm action requires a code.
- Numeric alarm codes use a touch-friendly keypad.
- Commands now display a short sending/progress state and actions are locked while a command is in flight.
- Automatic Home Assistant WebSocket reconnect after a dropped connection.
- Screen stays awake while HomePanel is open.
- Current Brussels/Ekeren local time and date.
- Current weather plus 5-day Ekeren forecast, refreshed every 30 minutes and manually via the refresh control.
- Weather data comes from Open-Meteo and is isolated from Home Assistant: a weather service outage does not affect alarm controls.
- Shows Alarmo/Home Assistant `changed_by` when the entity exposes it.

## Alarm command behavior

HomePanel continues to use Home Assistant's standard `alarm_control_panel` actions over the WebSocket API. This keeps it compatible with Alarmo while avoiding an Alarmo-specific API dependency.

Before sending a command HomePanel now checks:

1. The WebSocket is authenticated.
2. The alarm state allows the requested transition.
3. The requested target state is not already active.
4. A PIN is supplied when Home Assistant reports that the action requires one.

## Weather

The weather location is fixed for this release to Ekeren, Antwerp:

- Latitude: 51.2806
- Longitude: 4.4184
- Time zone: Europe/Brussels
- Provider: Open-Meteo

A later release can make the weather location configurable from Settings.
