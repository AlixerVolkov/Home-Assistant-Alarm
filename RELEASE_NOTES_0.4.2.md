# HomePanel v0.4.2

Usability update for UniFi guest vouchers and RTSP configuration.

## Added

- UniFi voucher deletion from the guest Wi-Fi dialog.
- Automatic discovery of `button.<config_id>_delete` from UniFi Hotspot Manager.
- Confirmation dialog before deleting a voucher.
- Migration fallback that derives the delete button from an existing `*_create` entity, so users upgrading from v0.4.1 do not need to reconfigure the integration in the common case.
- RTSP endpoint shown in Settings with a one-tap **Copy RTSP address** button.

## Changed

- The RTSP status/address card has been removed from the main alarm dashboard. RTSP information now lives only in Settings.
- Guest Wi-Fi UI distinguishes between voucher creation and deletion progress.
- Release version increased to `versionCode = 8`, `versionName = 0.4.2`.

## UniFi implementation

HomePanel uses the Home Assistant entities exposed by UniFi Hotspot Manager:

- `button.<config_id>_create`
- `button.<config_id>_delete`
- `sensor.<config_id>_voucher`
- `image.<config_id>_qr_code`

Creation and deletion are sent through Home Assistant using `button.press`.
