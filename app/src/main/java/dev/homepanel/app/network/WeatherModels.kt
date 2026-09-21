package dev.homepanel.app.network

data class CurrentWeather(
    val temperatureC: Double,
    val apparentTemperatureC: Double,
    val weatherCode: Int,
    val windSpeedKmh: Double,
    val humidityPercent: Int? = null,
    val pressureHpa: Double? = null,
    val uvIndex: Double? = null
)

data class DailyForecast(
    val date: String,
    val weatherCode: Int,
    val minimumC: Double,
    val maximumC: Double,
    val precipitationProbability: Int,
    val precipitationMm: Double? = null,
    val windSpeedKmh: Double? = null
)

data class WeatherForecast(
    val current: CurrentWeather,
    val daily: List<DailyForecast>,
    val locationLabel: String,
    val timezoneId: String,
    val sourceLabel: String = "Open-Meteo"
)
