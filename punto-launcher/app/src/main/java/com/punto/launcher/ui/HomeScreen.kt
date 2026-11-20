package com.punto.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Thunderstorm
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material.icons.rounded.WbCloudy
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.punto.launcher.assistant.Voice
import com.punto.launcher.data.AppEntry
import com.punto.launcher.data.CarState
import com.punto.launcher.data.FavoriteState
import com.punto.launcher.data.MediaHub
import com.punto.launcher.data.Prefs
import com.punto.launcher.data.Weather
import com.punto.launcher.data.WeatherApi
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Ações que a tela pede para a Activity executar. */
class LauncherActions(
    val openFavorite: (FavoriteState) -> Unit,
    val openEntry: (AppEntry) -> Unit,
    val listen: () -> Unit,
    val runCommand: (String) -> Unit,
    val loadApps: suspend () -> List<AppEntry>,
    val openNotificationAccess: () -> Unit,
    val openHomeSettings: () -> Unit,
    val openSystemSettings: () -> Unit,
    val resetTrip: () -> Unit,
)

private val ptBR = Locale.forLanguageTag("pt-BR")

@Composable
fun HomeScreen(
    car: CarState,
    media: MediaHub,
    voice: Voice,
    favorites: List<FavoriteState>,
    prefs: Prefs,
    actions: LauncherActions,
) {
    var drawerOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }

    // Na tela inicial o "voltar" não faz nada (como todo launcher).
    BackHandler { }

    Box(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .safeDrawingPadding(),
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .padding(12.dp),
        ) {
            val landscape = maxWidth > maxHeight
            val gap = 12.dp
            if (landscape) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    SpeedPanel(car, actions, Modifier.weight(1.1f).fillMaxHeight())
                    Column(
                        Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(gap),
                    ) {
                        ClockWeatherPanel(car, { settingsOpen = true }, Modifier.fillMaxWidth().weight(1f))
                        MediaPanel(media, actions, Modifier.fillMaxWidth().weight(1f))
                    }
                    Column(
                        Modifier.weight(0.95f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(gap),
                    ) {
                        FavoritesGrid(
                            favorites = favorites,
                            columns = 3,
                            onOpen = actions.openFavorite,
                            onAllApps = { drawerOpen = true },
                            modifier = Modifier.fillMaxWidth().weight(1f),
                        )
                        MicButton(voice.listening, actions.listen, Modifier.fillMaxWidth().height(64.dp))
                    }
                }
            } else {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
                    ClockWeatherPanel(car, { settingsOpen = true }, Modifier.fillMaxWidth().height(136.dp))
                    SpeedPanel(car, actions, Modifier.fillMaxWidth().weight(1.25f))
                    MediaPanel(media, actions, Modifier.fillMaxWidth().height(132.dp))
                    FavoritesGrid(
                        favorites = favorites,
                        columns = 4,
                        onOpen = actions.openFavorite,
                        onAllApps = { drawerOpen = true },
                        modifier = Modifier.fillMaxWidth().weight(0.8f),
                    )
                    MicButton(voice.listening, actions.listen, Modifier.fillMaxWidth().height(64.dp))
                }
            }
        }

        AppDrawer(
            visible = drawerOpen,
            loadApps = actions.loadApps,
            onClose = { drawerOpen = false },
            onOpen = {
                drawerOpen = false
                actions.openEntry(it)
            },
        )
        VoiceOverlay(voice = voice, onListen = actions.listen, onCommand = actions.runCommand)
        if (settingsOpen) {
            SettingsDialog(prefs = prefs, actions = actions, onDismiss = { settingsOpen = false })
        }
    }
}

// ---------------------------------------------------------------- velocidade

@Composable
private fun SpeedPanel(car: CarState, actions: LauncherActions, modifier: Modifier) {
    Panel(modifier) {
        Speedometer(
            speedKmh = car.speedKmh,
            gpsFix = car.gpsFix,
            modifier = Modifier.fillMaxSize(),
        )
        Stat(
            label = "MÁX",
            value = "${car.maxSpeedKmh.roundToInt()} km/h",
            modifier = Modifier.align(Alignment.TopStart),
        )
        Stat(
            label = "VIAGEM",
            value = String.format(ptBR, "%.1f km", car.tripMeters / 1000.0),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = actions.resetTrip),
            alignEnd = true,
        )
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier, alignEnd: Boolean = false) {
    Column(modifier, horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Text(label, color = TextDim, fontSize = 11.sp, letterSpacing = 1.sp)
        Text(value, color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ---------------------------------------------------------------- relógio e clima

@Composable
private fun ClockWeatherPanel(car: CarState, onSettings: () -> Unit, modifier: Modifier) {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val time = remember(now / 60_000) { SimpleDateFormat("HH:mm", ptBR).format(Date(now)) }
    val date = remember(now / 3_600_000) {
        SimpleDateFormat("EEEE, d 'de' MMMM", ptBR).format(Date(now)).replaceFirstChar { it.uppercase() }
    }

    Panel(modifier) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth > 330.dp
            val clock = @Composable { m: Modifier ->
                Column(m) {
                    Text(
                        time,
                        color = TextMain,
                        fontSize = 46.sp,
                        fontWeight = FontWeight.Bold,
                        style = TextStyle(fontFeatureSettings = "tnum"),
                        maxLines = 1,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            date,
                            color = TextDim,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        IconButton(onClick = onSettings, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Rounded.Settings,
                                contentDescription = "Configurações",
                                tint = TextDim,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
            if (wide) {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    clock(Modifier.weight(1f))
                    Spacer(Modifier.width(12.dp))
                    WeatherTall(car.weather, car.place)
                }
            } else {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                    clock(Modifier.fillMaxWidth())
                    WeatherCompact(car.weather, car.place)
                }
            }
        }
    }
}

@Composable
private fun WeatherTemp(weather: Weather) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            weatherIcon(weather.code, weather.isDay),
            contentDescription = null,
            tint = if (weather.isDay) Amber else Cyan,
            modifier = Modifier.size(30.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text("${weather.temperature.roundToInt()}°", color = TextMain, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun Weather.title() = WeatherApi.describe(code).replaceFirstChar { it.uppercase() }

private fun Weather.range(): String {
    val rain = if (rainChance >= 0) " · $rainChance% chuva" else ""
    return "↑${max.roundToInt()}° ↓${min.roundToInt()}°$rain"
}

/** Clima em coluna, alinhado à direita (painel largo). */
@Composable
private fun WeatherTall(weather: Weather?, place: String?) {
    Column(horizontalAlignment = Alignment.End) {
        if (weather == null) {
            Text("Buscando clima…", color = TextDim, fontSize = 14.sp)
            return@Column
        }
        WeatherTemp(weather)
        Text(weather.title(), color = TextMain, fontSize = 13.sp, maxLines = 1)
        if (place != null) Text(place, color = TextDim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(weather.range(), color = TextDim, fontSize = 13.sp, maxLines = 1)
    }
}

/** Clima em uma linha (painel estreito). */
@Composable
private fun WeatherCompact(weather: Weather?, place: String?) {
    if (weather == null) {
        Text("Buscando clima…", color = TextDim, fontSize = 14.sp)
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        WeatherTemp(weather)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                weather.title() + (place?.let { " · $it" } ?: ""),
                color = TextMain,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(weather.range(), color = TextDim, fontSize = 13.sp, maxLines = 1)
        }
    }
}

private fun weatherIcon(code: Int, isDay: Boolean): ImageVector = when (code) {
    0, 1 -> if (isDay) Icons.Rounded.WbSunny else Icons.Rounded.NightsStay
    2 -> if (isDay) Icons.Rounded.WbCloudy else Icons.Rounded.NightsStay
    3, 45, 48 -> Icons.Rounded.Cloud
    in 51..67, in 80..82 -> Icons.Rounded.Umbrella
    in 71..77, 85, 86 -> Icons.Rounded.AcUnit
    in 95..99 -> Icons.Rounded.Thunderstorm
    else -> Icons.Rounded.Cloud
}

// ---------------------------------------------------------------- música

@Composable
private fun MediaPanel(media: MediaHub, actions: LauncherActions, modifier: Modifier) {
    Panel(modifier) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            val artSize = 72.dp
            Box(
                Modifier
                    .size(artSize)
                    .clip(RoundedCornerShape(14.dp))
                    .background(PanelBg2),
                contentAlignment = Alignment.Center,
            ) {
                val art = media.art
                if (art != null) {
                    Image(art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = TextDim, modifier = Modifier.size(32.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                if (media.hasAccess) {
                    Text(
                        media.title ?: "Nada tocando",
                        color = TextMain,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOfNotNull(media.artist, media.appName).joinToString(" · ").ifEmpty { "Peça uma música no microfone" },
                        color = TextDim,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Text("Música", color = TextMain, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Toque para ver o que está tocando",
                        color = Accent,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable(onClick = actions.openNotificationAccess),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MediaButton(Icons.Rounded.SkipPrevious, "Anterior", 40.dp, media::previous)
                    Spacer(Modifier.width(10.dp))
                    MediaButton(
                        if (media.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        "Tocar ou pausar",
                        48.dp,
                        media::playPause,
                        filled = true,
                    )
                    Spacer(Modifier.width(10.dp))
                    MediaButton(Icons.Rounded.SkipNext, "Próxima", 40.dp, media::next)
                }
            }
        }
    }
}

@Composable
private fun MediaButton(icon: ImageVector, description: String, size: Dp, onClick: () -> Unit, filled: Boolean = false) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (filled) Accent else PanelBg2)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(size * 0.6f))
    }
}

// ---------------------------------------------------------------- apps favoritos

@Composable
private fun FavoritesGrid(
    favorites: List<FavoriteState>,
    columns: Int,
    onOpen: (FavoriteState) -> Unit,
    onAllApps: () -> Unit,
    modifier: Modifier,
) {
    // Favoritos + o botão "Todos" no final, distribuídos em linhas iguais.
    val rows = (0..favorites.size).chunked(columns)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { i ->
                    val m = Modifier.weight(1f).fillMaxHeight()
                    if (i < favorites.size) {
                        val fav = favorites[i]
                        FavoriteTile(fav, m) { onOpen(fav) }
                    } else {
                        AllAppsTile(m, onAllApps)
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun TileFrame(modifier: Modifier, onClick: () -> Unit, label: String, sub: String?, icon: @Composable (Dp) -> Unit) {
    BoxWithConstraints(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(PanelBg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val iconSize = min(min(maxWidth, maxHeight) * 0.5f, 56.dp)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            icon(iconSize)
            Spacer(Modifier.height(4.dp))
            Text(label, color = TextMain, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, color = TextDim, fontSize = 10.sp, maxLines = 1)
        }
    }
}

@Composable
private fun FavoriteTile(state: FavoriteState, modifier: Modifier, onClick: () -> Unit) {
    TileFrame(modifier, onClick, state.fav.label, if (state.installed) null else "instalar") { size ->
        val icon = state.icon
        if (icon != null) {
            Image(icon, contentDescription = state.fav.label, modifier = Modifier.size(size))
        } else {
            Box(
                Modifier
                    .size(size)
                    .clip(RoundedCornerShape(size * 0.28f))
                    .background(state.fav.color.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(state.fav.fallbackIcon, contentDescription = null, tint = state.fav.color, modifier = Modifier.size(size * 0.6f))
            }
        }
    }
}

@Composable
private fun AllAppsTile(modifier: Modifier, onClick: () -> Unit) {
    TileFrame(modifier, onClick, "Todos", null) { size ->
        Box(
            Modifier
                .size(size)
                .clip(RoundedCornerShape(size * 0.28f))
                .background(PanelBg2),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Apps, contentDescription = "Todos os apps", tint = TextMain, modifier = Modifier.size(size * 0.6f))
        }
    }
}

// ---------------------------------------------------------------- microfone

@Composable
private fun MicButton(listening: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(32.dp))
            .background(Brush.horizontalGradient(listOf(Accent, Danger)))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Mic, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                if (listening) "Ouvindo…" else "Fale comigo",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
