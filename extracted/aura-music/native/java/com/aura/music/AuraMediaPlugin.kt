package com.aura.music

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.getcapacitor.JSObject
import com.getcapacitor.PermissionState
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import com.getcapacitor.annotation.Permission
import com.getcapacitor.annotation.PermissionCallback
import com.google.common.util.concurrent.MoreExecutors

@CapacitorPlugin(
    name = "AuraMedia",
    permissions = [
        Permission(alias = "audio", strings = [Manifest.permission.READ_MEDIA_AUDIO]),
        Permission(alias = "audioLegacy", strings = [Manifest.permission.READ_EXTERNAL_STORAGE]),
        Permission(alias = "notifications", strings = [Manifest.permission.POST_NOTIFICATIONS])
    ]
)
class AuraMediaPlugin : Plugin() {
    private val alias get() = if (Build.VERSION.SDK_INT >= 33) "audio" else "audioLegacy"
    private val main = Handler(Looper.getMainLooper())
    private var controller: MediaController? = null
    private val pending = ArrayList<(MediaController) -> Unit>()

    private fun granted() = getPermissionState(alias) == PermissionState.GRANTED

    // ---- permission + scan ----
    @PluginMethod fun checkPermission(call: PluginCall) {
        call.resolve(JSObject().put("granted", granted()))
    }
    @PluginMethod fun requestAudioPermission(call: PluginCall) {
        if (granted()) call.resolve(JSObject().put("granted", true))
        else requestPermissionForAlias(alias, call, "permResult")
    }
    @PermissionCallback private fun permResult(call: PluginCall) {
        call.resolve(JSObject().put("granted", granted()))
    }
    // Android 13+: without this the media notification is not shown
    @PluginMethod fun requestNotificationPermission(call: PluginCall) {
        if (Build.VERSION.SDK_INT < 33 || getPermissionState("notifications") == PermissionState.GRANTED)
            call.resolve(JSObject().put("granted", true))
        else requestPermissionForAlias("notifications", call, "notifResult")
    }
    @PermissionCallback private fun notifResult(call: PluginCall) {
        call.resolve(JSObject().put("granted", getPermissionState("notifications") == PermissionState.GRANTED))
    }
    @PluginMethod fun openSettings(call: PluginCall) {
        val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(i); call.resolve()
    }
    @PluginMethod fun scanLibrary(call: PluginCall) {
        if (!granted()) { call.reject("Audio permission not granted", "PERMISSION_DENIED"); return }
        Thread {
            try {
                val t = MediaScanner.scan(context)
                call.resolve(JSObject().put("tracks", t).put("count", t.length()))
            } catch (e: Exception) { call.reject(e.message ?: "Scan failed", "SCAN_FAILED") }
        }.start()
    }

    // ---- playback (controller -> PlaybackService) ----
    override fun load() {
        main.post {
            try {
                val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
                val f = MediaController.Builder(context, token).buildAsync()
                f.addListener({
                    try {
                        val c = f.get(); controller = c; c.addListener(listener)
                        pending.forEach { it(c) }; pending.clear(); emit()
                    } catch (e: Exception) {
                        notifyListeners("playerError", JSObject().put("message", "Playback service unavailable"))
                    }
                }, MoreExecutors.directExecutor())
            } catch (e: Exception) { /* surfaced on first playback call */ }
        }
    }
    private fun withC(block: (MediaController) -> Unit) = main.post {
        controller?.let(block) ?: pending.add(block)
    }
    private val ticker = object : Runnable {
        override fun run() { emit(); if (controller?.isPlaying == true) main.postDelayed(this, 500) }
    }
    private val listener = object : Player.Listener {
        override fun onEvents(p: Player, e: Player.Events) {
            emit(); main.removeCallbacks(ticker); if (p.isPlaying) main.postDelayed(ticker, 500)
        }
        override fun onPlayerError(err: PlaybackException) {
            notifyListeners("playerError", JSObject()
                .put("code", err.errorCodeName).put("message", err.message ?: "Playback error")
                .put("mediaId", controller?.currentMediaItem?.mediaId))
        }
    }
    private fun state(c: MediaController) = JSObject()
        .put("isPlaying", c.isPlaying).put("mediaId", c.currentMediaItem?.mediaId)
        .put("position", c.currentPosition)
        .put("duration", if (c.duration == C.TIME_UNSET) 0L else c.duration)
        .put("playbackState", c.playbackState)
    private fun emit() { controller?.let { notifyListeners("playerState", state(it)) } }

    @PluginMethod fun playQueue(call: PluginCall) {
        val arr = call.getArray("items") ?: run { call.reject("items required"); return }
        val index = call.getInt("index", 0) ?: 0
        val items = (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            MediaItem.Builder().setMediaId(o.getString("id")).setUri(o.getString("uri"))
                .setMediaMetadata(MediaMetadata.Builder().setTitle(o.optString("title"))
                    .setArtist(o.optString("artist")).setAlbumTitle(o.optString("album")).build()).build()
        }
        withC { c -> c.setMediaItems(items, index, 0L); c.prepare(); c.play() }
        call.resolve()
    }
    @PluginMethod fun toggle(call: PluginCall) { withC { if (it.isPlaying) it.pause() else it.play() }; call.resolve() }
    @PluginMethod fun next(call: PluginCall) { withC { it.seekToNextMediaItem(); it.prepare() }; call.resolve() }
    @PluginMethod fun previous(call: PluginCall) { withC { it.seekToPreviousMediaItem() }; call.resolve() }
    @PluginMethod fun seek(call: PluginCall) {
        val p = call.data.optLong("position", 0L); withC { it.seekTo(p) }; call.resolve()
    }
    @PluginMethod fun getState(call: PluginCall) {
        main.post { call.resolve(controller?.let { state(it) } ?: JSObject().put("isPlaying", false)) }
    }

    override fun handleOnDestroy() { controller?.release(); controller = null }
}
