package io.github.mbaliga.clavis

import android.app.Application
import io.github.mbaliga.clavis.platform.AndroidApp

class ClavisApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AndroidApp.context = applicationContext
    }
}
