package com.punto.launcher

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.speech.RecognizerIntent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.punto.launcher.assistant.Assistant
import com.punto.launcher.assistant.Voice
import com.punto.launcher.data.Apps
import com.punto.launcher.data.CarState
import com.punto.launcher.data.FavoriteState
import com.punto.launcher.data.MediaHub
import com.punto.launcher.data.Prefs
import com.punto.launcher.data.WeatherApi
import com.punto.launcher.ui.HomeScreen
import com.punto.launcher.ui.LauncherActions
import com.punto.launcher.ui.PuntoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var prefs: Prefs
    private lateinit var car: CarState
    private lateinit var media: MediaHub
    private lateinit var voice: Voice
    private lateinit var assistant: Assistant
    private lateinit var locationManager: LocationManager

    private val favorites = mutableStateOf<List<FavoriteState>>(emptyList())
    private var locationStarted = false
    private var weatherLoading = false
    private var lastWeatherAt = 0L

    private val permissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { startLocation() }

    private val micPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startListening() else voice.say("Sem o microfone eu não consigo te ouvir.")
        }

    private val contactsPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) voice.say("Pronto! Agora consigo achar seus contatos. Pode repetir o pedido.")
        }

    /** Plano B: tela de voz do Google, se o reconhecimento dentro do app não existir. */
    private val speechActivityLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val text = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!text.isNullOrBlank()) {
                voice.showHeard(text)
                assistant.handle(text)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        prefs = Prefs(this)
        car = CarState()
        media = MediaHub(this)
        voice = Voice(this) { text -> assistant.handle(text) }
        assistant = Assistant(
            ctx = this,
            prefs = prefs,
            media = media,
            car = car,
            voice = voice,
            requestContactsPermission = { contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS) },
            refreshWeather = { refreshWeather(force = true) },
        )
        locationManager = getSystemService(LocationManager::class.java)

        val actions = LauncherActions(
            openFavorite = { fav -> assistant.openPackage(fav.fav.pkg, fav.fav.label) },
            openEntry = { entry -> assistant.openEntry(entry) },
            listen = { startListening() },
            runCommand = { text ->
                voice.showHeard(text)
                assistant.handle(text)
            },
            loadApps = { withContext(Dispatchers.IO) { Apps.loadAll(this@MainActivity) } },
            openNotificationAccess = { safeStart(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
            openHomeSettings = { safeStart(Intent(Settings.ACTION_HOME_SETTINGS)) },
            openSystemSettings = { safeStart(Intent(Settings.ACTION_SETTINGS)) },
            resetTrip = { car.resetTrip() },
        )

        setContent {
            PuntoTheme {
                HomeScreen(
                    car = car,
                    media = media,
                    voice = voice,
                    favorites = favorites.value,
                    prefs = prefs,
                    actions = actions,
                )
            }
        }

        // Relógio interno: detecta GPS perdido e atualiza o clima a cada 15 min.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    car.tick()
                    refreshWeather()
                    delay(1_000)
                }
            }
        }

        requestBasicPermissions()
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        media.start()
        startLocation()
        lifecycleScope.launch {
            favorites.value = withContext(Dispatchers.IO) { Apps.favorites(this@MainActivity) }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        voice.destroy()
        media.stop()
        if (locationStarted) {
            locationManager.removeUpdates(gpsListener)
            locationManager.removeUpdates(networkListener)
        }
    }

    // ------------------------------------------------------------ permissões

    private fun granted(permission: String) =
        checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun requestBasicPermissions() {
        val missing = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.RECORD_AUDIO,
        ).filterNot { granted(it) }
        if (missing.isNotEmpty()) permissionsLauncher.launch(missing.toTypedArray()) else startLocation()
    }

    // ------------------------------------------------------------ voz

    private fun startListening() {
        if (!granted(Manifest.permission.RECORD_AUDIO)) {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        if (voice.inAppRecognitionAvailable) {
            voice.listen()
        } else {
            safeStart(voice.recognizerIntent(), launcher = true)
        }
    }

    // ------------------------------------------------------------ localização

    private val gpsListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            car.onGps(location)
        }

        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    private val networkListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (car.lastLocation == null || !car.gpsFix) car.lastLocation = location
        }

        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    private fun startLocation() {
        if (locationStarted) return
        val fine = granted(Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = granted(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (!fine && !coarse) return
        try {
            val providers = locationManager.allProviders
            if (fine && LocationManager.GPS_PROVIDER in providers) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, 1_000L, 0f, gpsListener, Looper.getMainLooper(),
                )
            }
            if (LocationManager.NETWORK_PROVIDER in providers) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER, 60_000L, 100f, networkListener, Looper.getMainLooper(),
                )
            }
            val last = providers.mapNotNull { p ->
                runCatching { locationManager.getLastKnownLocation(p) }.getOrNull()
            }.maxByOrNull { it.time }
            if (last != null && car.lastLocation == null) car.lastLocation = last
            locationStarted = true
        } catch (e: SecurityException) {
            locationStarted = false
        }
    }

    // ------------------------------------------------------------ clima

    private fun refreshWeather(force: Boolean = false) {
        val loc = car.lastLocation ?: return
        if (weatherLoading) return
        val now = System.currentTimeMillis()
        val fresh = car.weather != null && now - lastWeatherAt < 15 * 60_000
        val retryTooSoon = car.weather == null && now - lastWeatherAt < 30_000
        if (!force && (fresh || retryTooSoon)) return
        weatherLoading = true
        lastWeatherAt = now
        lifecycleScope.launch {
            val weather = withContext(Dispatchers.IO) {
                runCatching { WeatherApi.fetch(loc.latitude, loc.longitude) }.getOrNull()
            }
            if (weather != null) car.weather = weather
            if (car.place == null) {
                car.place = withContext(Dispatchers.IO) { cityName(loc) }
            }
            weatherLoading = false
        }
    }

    private fun cityName(loc: Location): String? = runCatching {
        if (!Geocoder.isPresent()) return null
        @Suppress("DEPRECATION")
        val address = Geocoder(this, Locale.forLanguageTag("pt-BR"))
            .getFromLocation(loc.latitude, loc.longitude, 1)
            ?.firstOrNull()
        address?.locality ?: address?.subAdminArea
    }.getOrNull()

    // ------------------------------------------------------------ util

    private fun safeStart(intent: Intent, launcher: Boolean = false) {
        runCatching {
            if (launcher) speechActivityLauncher.launch(intent) else startActivity(intent)
        }.onFailure {
            voice.say("Não consegui abrir essa tela neste celular.")
        }
    }

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
