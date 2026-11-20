package com.punto.launcher.data

import android.location.Location
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Estado do "carro". Por enquanto vem do GPS do celular.
 * Quando houver leitor OBD2, os dados do motor entram aqui.
 */
class CarState {
    var speedKmh by mutableFloatStateOf(0f)
        private set
    var maxSpeedKmh by mutableFloatStateOf(0f)
        private set
    var tripMeters by mutableDoubleStateOf(0.0)
        private set
    var gpsFix by mutableStateOf(false)
        private set

    var weather by mutableStateOf<Weather?>(null)
    var place by mutableStateOf<String?>(null)

    var lastLocation: Location? = null
    private var lastGps: Location? = null
    private var lastGpsAt = 0L

    fun onGps(loc: Location) {
        lastGpsAt = SystemClock.elapsedRealtime()
        gpsFix = true
        val prev = lastGps
        var kmh = when {
            loc.hasSpeed() -> loc.speed * 3.6f
            prev != null -> {
                val dt = (loc.time - prev.time) / 1000f
                if (dt > 0f) prev.distanceTo(loc) / dt * 3.6f else 0f
            }
            else -> 0f
        }
        if (kmh < 3f) kmh = 0f // ruído do GPS parado
        speedKmh = kmh
        if (kmh > maxSpeedKmh) maxSpeedKmh = kmh
        if (prev != null && kmh > 0f && loc.accuracy < 25f) {
            tripMeters += prev.distanceTo(loc)
        }
        lastGps = loc
        lastLocation = loc
    }

    /** Chamado a cada segundo: se o GPS parou de responder, zera a velocidade. */
    fun tick() {
        if (gpsFix && SystemClock.elapsedRealtime() - lastGpsAt > 5_000) {
            gpsFix = false
            speedKmh = 0f
        }
    }

    fun resetTrip() {
        tripMeters = 0.0
        maxSpeedKmh = 0f
    }
}
