package dev.homepanel.app.network

data class WeatherWarning(
    val entityId: String,
    val friendlyName: String,
    val active: Boolean,
    val severity: String? = null,
    val title: String? = null,
    val description: String? = null,
    val expiresAt: String? = null
)

data class PersonLocation(
    val entityId: String,
    val friendlyName: String,
    val state: String,
    val latitude: Double?,
    val longitude: Double?,
    val gpsAccuracyMeters: Double? = null,
    val source: String? = null
)

data class HomeZoneLocation(
    val entityId: String,
    val friendlyName: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double = 100.0
)

data class HouseSummary(
    val openDoors: Int = 0,
    val openWindows: Int = 0,
    val lightsOn: Int = 0,
    val personsHome: Int = 0,
    val temperatureC: Double? = null,
    val temperatureName: String? = null,
    val openEntityNames: List<String> = emptyList(),
    val weatherWarnings: List<WeatherWarning> = emptyList(),
    val persons: List<PersonLocation> = emptyList(),
    val zones: List<HomeZoneLocation> = emptyList()
)
