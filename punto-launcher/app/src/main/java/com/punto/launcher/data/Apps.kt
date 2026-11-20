package com.punto.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.graphics.drawable.toBitmap

object Pkg {
    const val SPOTIFY = "com.spotify.music"
    const val YTMUSIC = "com.google.android.apps.youtube.music"
    const val WAZE = "com.waze"
    const val MAPS = "com.google.android.apps.maps"
    const val WHATSAPP = "com.whatsapp"
    const val WHATSAPP_BUSINESS = "com.whatsapp.w4b"
    const val NETFLIX = "com.netflix.mediaclient"
    const val YOUTUBE = "com.google.android.youtube"
}

data class Favorite(
    val label: String,
    val pkg: String,
    val color: Color,
    val fallbackIcon: ImageVector,
)

data class FavoriteState(val fav: Favorite, val installed: Boolean, val icon: ImageBitmap?)

data class AppEntry(
    val label: String,
    val packageName: String,
    val activityName: String,
    val icon: ImageBitmap?,
)

val FAVORITES = listOf(
    Favorite("Spotify", Pkg.SPOTIFY, Color(0xFF1DB954), Icons.Rounded.MusicNote),
    Favorite("YT Music", Pkg.YTMUSIC, Color(0xFFFF0033), Icons.Rounded.LibraryMusic),
    Favorite("Waze", Pkg.WAZE, Color(0xFF33CCFF), Icons.Rounded.Navigation),
    Favorite("Maps", Pkg.MAPS, Color(0xFF34A853), Icons.Rounded.Map),
    Favorite("WhatsApp", Pkg.WHATSAPP, Color(0xFF25D366), Icons.AutoMirrored.Rounded.Chat),
    Favorite("Netflix", Pkg.NETFLIX, Color(0xFFE50914), Icons.Rounded.Movie),
    Favorite("YouTube", Pkg.YOUTUBE, Color(0xFFFF0000), Icons.Rounded.SmartDisplay),
)

object Apps {
    /** Apps de vídeo bloqueados com o carro em movimento. */
    val VIDEO_APPS = setOf(
        Pkg.NETFLIX,
        Pkg.YOUTUBE,
        "com.amazon.avod.thirdpartyclient", // Prime Video
        "com.disney.disneyplus",
        "com.globo.globotv", // Globoplay
        "com.wbd.stream", // Max
        "com.zhiliaoapp.musically", // TikTok
    )

    fun isInstalled(ctx: Context, pkg: String): Boolean = try {
        @Suppress("DEPRECATION")
        ctx.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    fun launch(ctx: Context, pkg: String): Boolean {
        val intent = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { ctx.startActivity(intent) }.isSuccess
    }

    fun launch(ctx: Context, entry: AppEntry): Boolean {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(ComponentName(entry.packageName, entry.activityName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return runCatching { ctx.startActivity(intent) }.isSuccess
    }

    fun openStore(ctx: Context, pkg: String) {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { ctx.startActivity(market) }.isFailure) {
            val web = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=$pkg"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { ctx.startActivity(web) }
        }
    }

    fun icon(ctx: Context, pkg: String, px: Int = 144): ImageBitmap? = runCatching {
        ctx.packageManager.getApplicationIcon(pkg).toBitmap(px, px).asImageBitmap()
    }.getOrNull()

    fun favorites(ctx: Context): List<FavoriteState> = FAVORITES.map { fav ->
        val installed = isInstalled(ctx, fav.pkg)
        FavoriteState(fav, installed, if (installed) icon(ctx, fav.pkg) else null)
    }

    /** Lista todos os apps que aparecem na gaveta do Android. */
    fun loadAll(ctx: Context, withIcons: Boolean = true): List<AppEntry> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val activities = pm.queryIntentActivities(intent, 0)
        return activities
            .filter { it.activityInfo.packageName != ctx.packageName }
            .map { info ->
                AppEntry(
                    label = info.loadLabel(pm).toString(),
                    packageName = info.activityInfo.packageName,
                    activityName = info.activityInfo.name,
                    icon = if (withIcons) {
                        runCatching { info.loadIcon(pm).toBitmap(144, 144).asImageBitmap() }.getOrNull()
                    } else {
                        null
                    },
                )
            }
            .sortedBy { it.label.lowercase() }
    }
}
