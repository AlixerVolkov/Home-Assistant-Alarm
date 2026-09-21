# HomePanel v0.6.9

## Interactive people map and proximity fixes

- OpenStreetMap is now interactive: drag/pan, pinch zoom, +/− zoom controls and a fit/reset button.
- Offline map mode now uses a persistent cache of OpenStreetMap tiles that were previously viewed online. It never performs network requests while Offline cache is selected.
- Adds a clear message when the selected offline area has not yet been cached.
- Proximity wake now prefers Android wake-up proximity sensors and keeps a single listener registered across ACTIVE / SCREENSAVER / SLEEP transitions.
- Proximity wake also remains available during a crash-recovery safe-mode boot.
- Settings now includes live proximity diagnostics: sensor name/vendor, maximum range, wake-up capability, listener registration, raw distance and NEAR/FAR state.
- Home Assistant alarm, weather and wake-sensor discovery lists are now compact dropdown/combo selectors instead of long card lists. Manual entity-ID entry remains available.

Version: `0.6.9` (`versionCode 20`).
