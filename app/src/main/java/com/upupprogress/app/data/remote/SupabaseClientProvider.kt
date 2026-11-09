package com.upupprogress.app.data.remote

import com.upupprogress.app.BuildConfig
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest

object SupabaseClientProvider {

    val client by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_PUBLIC_KEY
        ) {
            install(Auth) {
                autoSaveToStorage = false
                autoLoadFromStorage = false
            }
            install(Functions) { requireValidSession = true }
            install(Postgrest)
        }
    }
}