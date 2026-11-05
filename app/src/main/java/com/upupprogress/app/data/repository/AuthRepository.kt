package com.upupprogress.app.data.repository

import com.upupprogress.app.data.remote.SupabaseClientProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.StateFlow

class AuthRepository {

    private val supabase = SupabaseClientProvider.client

    val sessionStatus: StateFlow<SessionStatus>
        get() = supabase.auth.sessionStatus

    suspend fun signIn(
        email: String,
        password: String
    ) {
        supabase.auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }

        check(sessionStatus.value is SessionStatus.Authenticated) {
            "No se obtuvo una sesión de usuario"
        }
    }

    suspend fun signOut() {
        supabase.auth.signOut()
    }
}