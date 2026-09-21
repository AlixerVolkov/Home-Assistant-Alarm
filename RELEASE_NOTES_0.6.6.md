# HomePanel 0.6.6

## Weather via Home Assistant

HomePanel can now use a `weather.*` entity from Home Assistant as its weather source. This is the default for upgraded installs when no previous weather-source preference exists. Current conditions are read from the entity state and daily forecast data is retrieved with `weather.get_forecasts`.

Open-Meteo remains available as an optional direct source. When Home Assistant weather is selected, Android location permission and direct access from the tablet to `api.open-meteo.com` are not required.

## Network diagnostics

Settings now includes a network diagnostics card that tests:

- active LAN / IPv4,
- Home Assistant API,
- Home Assistant weather forecast,
- Open-Meteo HTTPS,
- GitHub release API,
- MQTT broker TCP reachability,
- local RTSP listener.

This makes firewall, DNS, routing and service failures visible from the tablet itself.

## Version

`versionCode = 17`, `versionName = 0.6.6`.
