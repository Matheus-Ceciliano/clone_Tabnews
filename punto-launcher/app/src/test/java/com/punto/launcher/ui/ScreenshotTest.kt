package com.punto.launcher.ui

import android.graphics.Bitmap
import android.location.Location
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.punto.launcher.assistant.Voice
import com.punto.launcher.data.CarState
import com.punto.launcher.data.FAVORITES
import com.punto.launcher.data.FavoriteState
import com.punto.launcher.data.MediaHub
import com.punto.launcher.data.Prefs
import com.punto.launcher.data.Weather
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Gera imagens da tela inicial em app/build/screenshots/ sem precisar de celular.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun render(name: String, setup: (Voice) -> Unit = {}) {
        val ctx = compose.activity
        val car = CarState().apply {
            val now = System.currentTimeMillis()
            onGps(Location("gps").apply { latitude = -23.55; longitude = -46.63; time = now - 1000; speed = 60f / 3.6f; accuracy = 5f })
            onGps(Location("gps").apply { latitude = -23.5503; longitude = -46.63; time = now; speed = 72f / 3.6f; accuracy = 5f })
            weather = Weather(27.4, 2, true, 31.0, 19.0, 40, 9.0)
            place = "São Paulo"
        }
        val voice = Voice(ctx) {}.also(setup)
        val actions = LauncherActions({}, {}, {}, {}, { emptyList() }, {}, {}, {}, {})
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PuntoTheme {
                HomeScreen(
                    car = car,
                    media = MediaHub(ctx),
                    voice = voice,
                    favorites = FAVORITES.map { FavoriteState(it, installed = true, icon = null) },
                    prefs = Prefs(ctx),
                    actions = actions,
                )
            }
        }
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    @Config(qualifiers = "w820dp-h390dp-land-xhdpi")
    fun homeLandscape() = render("home_paisagem")

    @Test
    @Config(qualifiers = "w400dp-h860dp-port-xhdpi")
    fun homePortrait() = render("home_retrato")

    @Test
    @Config(qualifiers = "w820dp-h390dp-land-xhdpi")
    fun voiceLandscape() = render("voz_paisagem") {
        it.showHeard("Me leva para a Avenida Paulista")
        it.say("Indo para Avenida Paulista pelo Waze.")
    }
}
