package dev.homepanel.app.network

data class CurrentWeather(
    val temperatureC: Double,
    val apparentTemperatureC: Double,
    val weatherCode: Int,
    val windSpeedKmh: Double
)

data class DailyForecast(
    val date: String,
    val weatherCode: Int,
    val minimumC: Double,
    val maximumC: Double,
    val precipitationProbability: Int
)

data class WeatherForecast(
    val current: CurrentWeather,
    val daily: List<DailyForecast>
)
