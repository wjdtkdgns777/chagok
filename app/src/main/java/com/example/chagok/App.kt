package com.example.chagok

import android.app.Application

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        Store.load(this)
    }
}
