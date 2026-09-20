package dev.homepanel.app.network

data class DeviceLocation(
    val latitude: Double,
    val longitude: Double,
    val city: String?,
    val region: String?,
    val country: String?,
    val timezoneId: String
) {
    val displayName: String
        get() = listOfNotNull(city, region, country)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(", ")
            .ifBlank { "${"%.3f".format(latitude)}, ${"%.3f".format(longitude)}" }
}
