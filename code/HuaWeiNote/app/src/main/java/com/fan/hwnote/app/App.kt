package com.fan.hwnote.app

import android.app.Application
import com.fan.hwnote.app.model.NoteRepository

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        NoteRepository.init(this)
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
