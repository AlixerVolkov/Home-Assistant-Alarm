# HomePanel 0.3.0

## Added

- System locale based UI; EN/ES/NL/FR resources.
- Automatic location permission flow.
- Reverse geocoding for city, region and country.
- Weather by current device coordinates and automatic time zone.
- Configurable screen saver and sleep inactivity timers.
- Burn-in-aware moving screen saver with clock, weather and alarm state.
- Wake sensor discovery for Home Assistant motion, occupancy and presence binary sensors.
- Wake on selected HA sensor, Alarmo transitional/trigger states and local proximity sensor.
- Hardware screen wake pulse using Android wake APIs when required.
- Responsive portrait/landscape layouts and removal of forced landscape orientation.

## Migration

Existing HomePanel v0.2 settings remain valid. New settings use defaults:

- Wake sensor: none
- Screen saver: 2 minutes
- Sleep: 10 minutes

Open Settings and run device discovery again to select a Home Assistant motion/presence sensor.
