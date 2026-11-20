package com.punto.launcher.data

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.app.NotificationManagerCompat
import com.punto.launcher.MediaListenerService

/**
 * Mostra e controla a música que está tocando (Spotify, YouTube Music, etc.).
 * Ler o título precisa da permissão "acesso a notificações"; os botões
 * play/pausa/próxima funcionam mesmo sem ela.
 */
class MediaHub(private val ctx: Context) {
    var title by mutableStateOf<String?>(null)
        private set
    var artist by mutableStateOf<String?>(null)
        private set
    var appName by mutableStateOf<String?>(null)
        private set
    var art by mutableStateOf<ImageBitmap?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var hasAccess by mutableStateOf(false)
        private set

    private val sessionManager = ctx.getSystemService(MediaSessionManager::class.java)
    private val audio = ctx.getSystemService(AudioManager::class.java)
    private val component = ComponentName(ctx, MediaListenerService::class.java)
    private var controller: MediaController? = null
    private var listening = false

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = update()
        override fun onPlaybackStateChanged(state: PlaybackState?) = update()
        override fun onSessionDestroyed() {
            select(null)
            refresh()
        }
    }

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { list -> select(pick(list)) }

    fun start() {
        hasAccess = NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
        if (!hasAccess || listening) return
        try {
            sessionManager.addOnActiveSessionsChangedListener(sessionsListener, component)
            listening = true
            refresh()
        } catch (e: SecurityException) {
            hasAccess = false
        }
    }

    fun stop() {
        if (listening) {
            runCatching { sessionManager.removeOnActiveSessionsChangedListener(sessionsListener) }
            listening = false
        }
        select(null)
    }

    private fun refresh() {
        val sessions = runCatching { sessionManager.getActiveSessions(component) }.getOrNull()
        select(pick(sessions))
    }

    private fun pick(list: List<MediaController>?): MediaController? =
        list?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list?.firstOrNull()

    private fun select(c: MediaController?) {
        if (c?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(callback)
            controller = c
            c?.registerCallback(callback)
        }
        update()
    }

    private fun update() {
        val c = controller
        val meta = c?.metadata
        title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
        artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
        val bmp = meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        art = bmp?.asImageBitmap()
        isPlaying = c?.playbackState?.state == PlaybackState.STATE_PLAYING
        appName = c?.packageName?.let { pkg ->
            runCatching {
                val pm = ctx.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            }.getOrNull()
        }
    }

    fun playPause() {
        val c = controller
        if (c != null) {
            if (isPlaying) c.transportControls.pause() else c.transportControls.play()
        } else {
            sendKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        }
    }

    fun play() = controller?.transportControls?.play() ?: sendKey(KeyEvent.KEYCODE_MEDIA_PLAY)
    fun pause() = controller?.transportControls?.pause() ?: sendKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
    fun next() = controller?.transportControls?.skipToNext() ?: sendKey(KeyEvent.KEYCODE_MEDIA_NEXT)
    fun previous() =
        controller?.transportControls?.skipToPrevious() ?: sendKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)

    fun volumeUp() = repeat(3) { i ->
        audio.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            AudioManager.ADJUST_RAISE,
            if (i == 0) AudioManager.FLAG_SHOW_UI else 0,
        )
    }

    fun volumeDown() = repeat(3) { i ->
        audio.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            AudioManager.ADJUST_LOWER,
            if (i == 0) AudioManager.FLAG_SHOW_UI else 0,
        )
    }

    private fun sendKey(code: Int) {
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }
}
