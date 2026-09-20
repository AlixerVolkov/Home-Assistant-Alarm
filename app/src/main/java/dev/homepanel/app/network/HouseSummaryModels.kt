package dev.homepanel.app.network

data class HouseSummary(
    val openDoors: Int = 0,
    val openWindows: Int = 0,
    val lightsOn: Int = 0,
    val personsHome: Int = 0,
    val temperatureC: Double? = null,
    val temperatureName: String? = null,
    val openEntityNames: List<String> = emptyList()
)
