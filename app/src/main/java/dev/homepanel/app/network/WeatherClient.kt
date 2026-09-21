package dev.homepanel.app.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

class WeatherClient(
    private val client: OkHttpClient
) {
    suspend fun fetchForecast(location: DeviceLocation): WeatherForecast = withContext(Dispatchers.IO) {
        val url = API_URL.toHttpUrl().newBuilder()
            .addQueryParameter("latitude", location.latitude.toString())
            .addQueryParameter("longitude", location.longitude.toString())
            .addQueryParameter(
                "current",
                "temperature_2m,apparent_temperature,weather_code,wind_speed_10m"
            )
            .addQueryParameter(
                "daily",
                "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max"
            )
            .addQueryParameter("timezone", "auto")
            .addQueryParameter("forecast_days", "5")
            .build()

        val request = Request.Builder().url(url).get().build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Weather service returned HTTP ${response.code}")
            }

            val json = JSONObject(response.body.string())
            val currentJson = json.getJSONObject("current")
            val dailyJson = json.getJSONObject("daily")

            val current = CurrentWeather(
                temperatureC = currentJson.getDouble("temperature_2m"),
                apparentTemperatureC = currentJson.getDouble("apparent_temperature"),
                weatherCode = currentJson.getInt("weather_code"),
                windSpeedKmh = currentJson.getDouble("wind_speed_10m")
            )

            val dates = dailyJson.getJSONArray("time")
            val codes = dailyJson.getJSONArray("weather_code")
            val maximums = dailyJson.getJSONArray("temperature_2m_max")
            val minimums = dailyJson.getJSONArray("temperature_2m_min")
            val rain = dailyJson.getJSONArray("precipitation_probability_max")

            val days = buildList {
                val count = minOf(5, dates.length())
                for (index in 0 until count) {
                    add(
                        DailyForecast(
                            date = dates.getString(index),
                            weatherCode = intAt(codes, index),
                            minimumC = doubleAt(minimums, index),
                            maximumC = doubleAt(maximums, index),
                            precipitationProbability = intAt(rain, index)
                        )
                    )
                }
            }

            WeatherForecast(
                current = current,
                daily = days,
                locationLabel = location.displayName,
                timezoneId = json.optString("timezone", location.timezoneId),
                sourceLabel = "Open-Meteo"
            )
        }
    }

    suspend fun probe(): Long = withContext(Dispatchers.IO) {
        val url = API_URL.toHttpUrl().newBuilder()
            .addQueryParameter("latitude", "0")
            .addQueryParameter("longitude", "0")
            .addQueryParameter("current", "temperature_2m")
            .build()
        val started = android.os.SystemClock.elapsedRealtime()
        client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
            if (!response.isSuccessful) error("Open-Meteo returned HTTP ${response.code}")
        }
        android.os.SystemClock.elapsedRealtime() - started
    }

    private fun doubleAt(array: JSONArray, index: Int): Double {
        val value = array.opt(index) ?: return 0.0
        return value.toString().toDoubleOrNull() ?: 0.0
    }

    private fun intAt(array: JSONArray, index: Int): Int {
        val value = array.opt(index) ?: return 0
        return value.toString().toDoubleOrNull()?.toInt() ?: 0
    }

    companion object {
        private const val API_URL = "https://api.open-meteo.com/v1/forecast"
    }
}
