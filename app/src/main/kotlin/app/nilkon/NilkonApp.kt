package app.nilkon

import android.app.Application
import app.nilkon.data.Covers
import app.nilkon.data.Store
import app.nilkon.online.Online
import app.nilkon.playback.Engine

class NilkonApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        Online.init(this)
        Covers.init(this)
        Engine.init(this)
        val v = Store.int("volume")
        if (v in 0..100) Engine.setVolume(v, save = false)
    }
}
