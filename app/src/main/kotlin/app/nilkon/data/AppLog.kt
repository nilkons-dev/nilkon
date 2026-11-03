package app.nilkon.data

import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Small in-memory problem log (last 400 lines). The Settings > Problem log page shows it and can copy it. */
object AppLog {
    private val lines = ArrayList<String>()
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)
    var version by mutableIntStateOf(0)
        private set

    fun add(tag: String, msg: String) {
        val line = fmt.format(Date()) + " [" + tag + "] " + msg.replace('\n', ' ').take(600)
        synchronized(lines) {
            lines += line
            while (lines.size > 400) lines.removeAt(0)
        }
        version++
    }

    fun add(tag: String, msg: String, e: Throwable) {
        val chain = StringBuilder()
        var x: Throwable? = e
        var n = 0
        while (x != null && n < 5) {
            if (n > 0) chain.append(" <- ")
            chain.append(x.javaClass.simpleName).append(": ").append(x.message ?: "")
            x = x.cause; n++
        }
        add(tag, "$msg :: $chain")
    }

    fun clear() { synchronized(lines) { lines.clear() }; version++ }

    fun lines(max: Int = 120): List<String> = synchronized(lines) { lines.takeLast(max).asReversed() }

    fun text(appVersion: String): String {
        val head = "NILKON $appVersion | Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) | ${Build.MANUFACTURER} ${Build.MODEL}"
        return head + "\n" + synchronized(lines) { lines.joinToString("\n") }
    }
}
