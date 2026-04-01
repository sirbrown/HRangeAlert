package com.hrrangealert

import android.app.Application

class HrrApplication : Application() {

    val bleViewModel: BleViewModel by lazy {
        BleViewModel(this)
    }

    override fun onCreate() {
        super.onCreate()
        // It's better to initiate the auto-connect logic from the MainActivity
        // after permissions are checked and granted.
    }
}