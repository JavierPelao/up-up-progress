package com.upupprogress.app.data.remote

import android.content.Context
import com.upupprogress.app.BuildConfig
import io.github.jan.supabase.logging.LogLevel
import java.security.MessageDigest
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest

object SupabaseClientProvider {
    private lateinit var applicationContext: Context

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
    }

    val client by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_PUBLIC_KEY
        ) {
            defaultLogLevel = LogLevel.NONE
            install(Auth) {
                val namespace = MessageDigest.getInstance("SHA-256")
                    .digest(BuildConfig.SUPABASE_URL.toByteArray()).take(12)
                    .joinToString("") { "%02x".format(it) }
                sessionManager = EncryptedSessionManager(applicationContext, namespace)
                autoSaveToStorage = true
                autoLoadFromStorage = true
                alwaysAutoRefresh = true
                // En 3.8 los callbacks cambian el estado a Initializing al pasar a segundo plano.
                // La sesión continúa renovándose mientras vive el proceso y se restaura al iniciarlo.
                enableLifecycleCallbacks = false
            }
            install(Functions) { requireValidSession = true }
            install(Postgrest)
        }
    }
}