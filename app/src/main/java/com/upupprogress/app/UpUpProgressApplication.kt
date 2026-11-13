package com.upupprogress.app

import android.app.Application
import com.upupprogress.app.data.remote.SupabaseClientProvider

class UpUpProgressApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SupabaseClientProvider.initialize(this)
    }
}
