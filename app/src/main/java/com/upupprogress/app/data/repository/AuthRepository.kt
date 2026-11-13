package com.upupprogress.app.data.repository

import com.upupprogress.app.data.remote.SupabaseClientProvider
import com.upupprogress.app.data.model.AuthenticatedAccount
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import io.github.jan.supabase.SupabaseClient

interface RegistrationAuthGateway {
    fun currentAccount(): AuthenticatedAccount?
    suspend fun signUp(email: String, password: String): RegistrationResult
}

class AuthRepository internal constructor(private val supabase: SupabaseClient) : RegistrationAuthGateway {
    constructor() : this(SupabaseClientProvider.client)

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

    override fun currentAccount(): AuthenticatedAccount? = supabase.auth.currentUserOrNull()?.let {
        AuthenticatedAccount(it.id, it.email)
    }

    override suspend fun signUp(email: String, password: String): RegistrationResult {
        val user = supabase.auth.signUpWith(Email) {
            this.email = email.trim()
            this.password = password
        }

        if (user != null) {
            return RegistrationResult.EmailConfirmationRequired
        }

        check(sessionStatus.value is SessionStatus.Authenticated) {
            "No se obtuvo una sesión después del registro"
        }
        return RegistrationResult.SessionStarted
    }

    suspend fun signOut() {
        try {
            supabase.auth.signOut()
        } finally {
            // Cerrar sesión debe retirar también el acceso local si no hay conexión.
            withContext(NonCancellable) { supabase.auth.clearSession() }
        }
    }
}

sealed interface RegistrationResult {
    data object EmailConfirmationRequired : RegistrationResult
    data object SessionStarted : RegistrationResult
}