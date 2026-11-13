package com.upupprogress.app.data.repository

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.logging.LogLevel
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.milliseconds
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AuthSessionPersistenceTest {
    @Test fun expiredSessionSurvivesNetworkFailureAndRenewsWhenConnectionReturns() = runBlocking {
        val storage = MemorySessionManager()
        storage.saveSession(UserSession("synthetic-expired", "synthetic-refresh",
            expiresIn = 3600, tokenType = "Bearer", expiresAt = Clock.System.now() - 1.seconds))
        val online = AtomicBoolean(false)
        val client = createSupabaseClient("https://example.invalid", "synthetic-key") {
            defaultLogLevel = LogLevel.NONE
            osInformation = null
            httpEngine = MockEngine {
                if (!online.get()) throw IOException("synthetic offline")
                respond("""{"access_token":"synthetic-renewed","refresh_token":"synthetic-renewed-refresh","expires_in":3600,"token_type":"Bearer"}""",
                    HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
            install(Auth) {
                enableLifecycleCallbacks = false
                sessionManager = storage
                codeVerifierCache = MemoryCodeVerifierCache()
                autoLoadFromStorage = true
                alwaysAutoRefresh = true
                retryDelay = 50.milliseconds
            }
        }
        try {
            withTimeout(10.seconds) { client.auth.sessionStatus.first { it is SessionStatus.RefreshFailure } }
            assertEquals("synthetic-refresh", storage.loadSession().refreshToken)
            online.set(true)
            withTimeout(10.seconds) { client.auth.sessionStatus.first { it is SessionStatus.Authenticated } }
            assertEquals("synthetic-renewed", storage.loadSession().accessToken)
        } finally { client.close() }
    }

    @Test fun explicitSignOutRemovesLocalSessionEvenWhenTheNetworkFails() = runBlocking {
        val storage = MemorySessionManager()
        val client = createSupabaseClient("https://example.invalid", "synthetic-key") {
            defaultLogLevel = LogLevel.NONE
            osInformation = null
            httpEngine = MockEngine { throw IOException("synthetic network failure") }
            install(Auth) {
                enableLifecycleCallbacks = false
                codeVerifierCache = MemoryCodeVerifierCache()
                autoLoadFromStorage = false
                alwaysAutoRefresh = false
                sessionManager = storage
            }
        }
        try {
            withTimeout(10.seconds) { client.auth.awaitInitialization() }
            client.auth.importSession(UserSession("synthetic-access", "synthetic-refresh",
                expiresIn = 3600, tokenType = "Bearer"), autoRefresh = false)
            assertNotNull(storage.loadSessionOrNull())
            assertTrue(runCatching { AuthRepository(client).signOut() }.isFailure)
            assertNull(storage.loadSessionOrNull())
            assertTrue(client.auth.sessionStatus.value is SessionStatus.NotAuthenticated)
        } finally { client.close() }
    }

    @Test fun successfulSignOutRemovesTheSessionAndRepeatedSignOutIsSafe() = runBlocking {
        val storage = MemorySessionManager()
        val client = createSupabaseClient("https://example.invalid", "synthetic-key") {
            defaultLogLevel = LogLevel.NONE
            osInformation = null
            httpEngine = MockEngine { respond("", HttpStatusCode.NoContent) }
            install(Auth) {
                enableLifecycleCallbacks = false
                codeVerifierCache = MemoryCodeVerifierCache()
                autoLoadFromStorage = false
                alwaysAutoRefresh = false
                sessionManager = storage
            }
        }
        try {
            withTimeout(10.seconds) { client.auth.awaitInitialization() }
            client.auth.importSession(UserSession("synthetic-access", "synthetic-refresh",
                expiresIn = 3600, tokenType = "Bearer"), autoRefresh = false)
            val repository = AuthRepository(client)
            repository.signOut()
            repository.signOut()
            assertNull(storage.loadSessionOrNull())
            assertNull(client.auth.currentSessionOrNull())
        } finally { client.close() }
    }
}
