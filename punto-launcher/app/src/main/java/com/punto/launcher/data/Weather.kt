package com.punto.launcher.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class Weather(
    val temperature: Double,
    val code: Int,
    val isDay: Boolean,
    val max: Double,
    val min: Double,
    val rainChance: Int,
    val windKmh: Double,
)

/** Previsão do tempo pela API gratuita Open-Meteo (não precisa de chave). */
object WeatherApi {
    fun fetch(lat: Double, lon: Double): Weather {
        val url = URL(
            String.format(
                Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
                    "&current=temperature_2m,weather_code,is_day,wind_speed_10m" +
                    "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
                    "&timezone=auto&forecast_days=1",
                lat,
                lon,
            ),
        )
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        try {
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val current = json.getJSONObject("current")
            val daily = json.getJSONObject("daily")
            return Weather(
                temperature = current.getDouble("temperature_2m"),
                code = current.getInt("weather_code"),
                isDay = current.optInt("is_day", 1) == 1,
                max = daily.getJSONArray("temperature_2m_max").getDouble(0),
                min = daily.getJSONArray("temperature_2m_min").getDouble(0),
                rainChance = daily.getJSONArray("precipitation_probability_max").optInt(0, -1),
                windKmh = current.optDouble("wind_speed_10m", 0.0),
            )
        } finally {
            conn.disconnect()
        }
    }

    fun describe(code: Int): String = when (code) {
        0 -> "céu limpo"
        1 -> "poucas nuvens"
        2 -> "parcialmente nublado"
        3 -> "nublado"
        45, 48 -> "neblina"
        51, 53, 55, 56, 57 -> "garoa"
        61, 63, 66, 80, 81 -> "chuva"
        65, 67, 82 -> "chuva forte"
        71, 73, 75, 77, 85, 86 -> "neve"
        95, 96, 99 -> "tempestade"
        else -> "tempo variável"
    }
}
