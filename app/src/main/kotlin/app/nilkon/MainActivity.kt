package app.nilkon

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.nilkon.data.Store
import app.nilkon.playback.Effects
import app.nilkon.playback.PlaybackService
import app.nilkon.ui.NilkonRoot
import app.nilkon.ui.T
import com.google.common.util.concurrent.ListenableFuture

class MainActivity : ComponentActivity() {
    private var controller: ListenableFuture<MediaController>? = null
    private lateinit var model: AppModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        @Suppress("DEPRECATION") run { window.statusBarColor = Color.TRANSPARENT; window.navigationBarColor = Color.TRANSPARENT }
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        // theme is known before the first frame, so there is no colour flash
        T.dark = Store.str("mode") != "light"
        T.setAccent(Store.str("accent"), snap = true)

        model = AppModel(applicationContext)
        // Connecting a controller starts the playback service, which keeps music alive in the background
        // and gives the notification panel / lock screen / headset their controls.
        controller = MediaController.Builder(this, SessionToken(this, ComponentName(this, PlaybackService::class.java))).buildAsync()

        setContent {
            val perms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                model.hasAudioPermission = hasAudio(); model.scan()
            }
            val mic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
                if (ok) { Effects.tryVisualizer(); app.nilkon.playback.Engine.say("Real waves are on for online songs") }
            }
            val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { model.addFiles(it) }
            val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { if (it != null) model.addFolder(it) }
            val image = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { if (it != null) model.pickCustomBackground(it) }
            val del = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { model.deleteFinished(it.resultCode == Activity.RESULT_OK) }

            fun requestAudio() {
                val need = ArrayList<String>()
                if (Build.VERSION.SDK_INT >= 33) { need += Manifest.permission.READ_MEDIA_AUDIO; need += Manifest.permission.POST_NOTIFICATIONS }
                else need += Manifest.permission.READ_EXTERNAL_STORAGE
                perms.launch(need.toTypedArray())
            }
            model.pickFiles = { files.launch(arrayOf("audio/*", "application/ogg")) }
            model.pickFolder = { folder.launch(null) }
            model.pickImage = { image.launch(arrayOf("image/*")) }
            model.launchDelete = { sender -> del.launch(IntentSenderRequest.Builder(sender).build()) }
            model.requestAudioAccess = { requestAudio() }
            model.requestMic = { mic.launch(Manifest.permission.RECORD_AUDIO) }
            model.openAppSettings = {
                runCatching {
                    startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
                }
            }
            LaunchedEffect(Unit) {
                model.hasAudioPermission = hasAudio()
                model.hasMic = hasMic()
                val need = ArrayList<String>()
                if (Build.VERSION.SDK_INT >= 33) { need += Manifest.permission.READ_MEDIA_AUDIO; need += Manifest.permission.POST_NOTIFICATIONS }
                else need += Manifest.permission.READ_EXTERNAL_STORAGE
                if (need.any { ContextCompat.checkSelfPermission(this@MainActivity, it) != PackageManager.PERMISSION_GRANTED }) perms.launch(need.toTypedArray())
                else model.scan()
            }
            NilkonRoot(model) { dark ->
                WindowInsetsControllerCompat(window, window.decorView).apply { isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark }
            }
        }
    }

    private fun hasAudio(): Boolean {
        val p = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasMic() = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    override fun onStart() {
        super.onStart()
        if (this::model.isInitialized) { model.hasMic = hasMic(); val a = hasAudio(); if (a != model.hasAudioPermission) { model.hasAudioPermission = a; model.scan() } }
    }

    override fun onStop() { Store.save(); super.onStop() }

    override fun onDestroy() {
        controller?.let { MediaController.releaseFuture(it) }
        super.onDestroy()
    }
}
