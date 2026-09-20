package dev.homepanel.app.network

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import androidx.core.content.ContextCompat
import java.util.Locale
import java.util.TimeZone
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class DeviceLocationResolver(
    private val context: Context
) {
    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    suspend fun resolve(): DeviceLocation {
        if (!hasLocationPermission()) error("Location permission not granted")

        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = buildList {
            if (runCatching { manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false)) {
                add(LocationManager.NETWORK_PROVIDER)
            }
            if (runCatching { manager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)) {
                add(LocationManager.GPS_PROVIDER)
            }
            if (isEmpty()) {
                addAll(manager.getProviders(true))
            }
        }.distinct()

        val lastKnown = providers
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }

        val fresh = lastKnown?.takeIf { System.currentTimeMillis() - it.time < LAST_LOCATION_MAX_AGE_MS }
            ?: requestCurrentLocation(manager, providers)
            ?: lastKnown
            ?: error("No device location is available")

        return reverseGeocode(fresh)
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestCurrentLocation(
        manager: LocationManager,
        providers: List<String>
    ): Location? = withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
        suspendCancellableCoroutine { continuation ->
            val provider = providers.firstOrNull() ?: run {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val cancellation = CancellationSignal()
                continuation.invokeOnCancellation { cancellation.cancel() }
                manager.getCurrentLocation(provider, cancellation, context.mainExecutor) { location ->
                    if (continuation.isActive) continuation.resume(location)
                }
            } else {
                @Suppress("DEPRECATION")
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        runCatching { manager.removeUpdates(this) }
                        if (continuation.isActive) continuation.resume(location)
                    }
                }
                continuation.invokeOnCancellation { runCatching { manager.removeUpdates(listener) } }
                @Suppress("DEPRECATION")
                manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            }
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun reverseGeocode(location: Location): DeviceLocation = withContext(Dispatchers.IO) {
        var city: String? = null
        var region: String? = null
        var country: String? = null

        if (Geocoder.isPresent()) {
            runCatching {
                val address = Geocoder(context, Locale.getDefault())
                    .getFromLocation(location.latitude, location.longitude, 1)
                    ?.firstOrNull()
                city = address?.locality
                    ?: address?.subLocality
                    ?: address?.subAdminArea
                region = address?.adminArea
                country = address?.countryName
            }
        }

        if (country.isNullOrBlank()) {
            country = Locale.getDefault().displayCountry.takeIf { it.isNotBlank() }
        }

        DeviceLocation(
            latitude = location.latitude,
            longitude = location.longitude,
            city = city,
            region = region,
            country = country,
            timezoneId = TimeZone.getDefault().id
        )
    }

    companion object {
        private const val LOCATION_TIMEOUT_MS = 12_000L
        private const val LAST_LOCATION_MAX_AGE_MS = 6L * 60L * 60L * 1000L
    }
}
