package com.upupprogress.app.data.remote

import androidx.test.platform.app.InstrumentationRegistry
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.NoSessionFoundException
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.logging.LogLevel
import java.io.File
import java.security.KeyStore
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/** Solo tokens sintéticos y un directorio exclusivo; no abre sesiones reales ni consulta Supabase. */
class EncryptedSessionManagerTest {
    private val id = UUID.randomUUID().toString()
    private val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "session-test-$id")
    private val alias = "upup.session.test.$id"
    private fun manager() = EncryptedSessionManager(directory, alias)
    private fun session(suffix: String = "one") =
        UserSession(accessToken = "synthetic-access-$suffix", refreshToken = "synthetic-refresh-$suffix",
            expiresIn = 3600, tokenType = "Bearer")

    @After fun cleanup() {
        directory.deleteRecursively()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) }
    }

    @Test fun newManagerRestoresTheEncryptedSessionWithoutPlaintextOnDisk() = runBlocking {
        val expected = session()
        manager().saveSession(expected)
        assertEquals(expected, manager().loadSession())
        val bytes = File(directory, "session.enc").readBytes().toString(Charsets.ISO_8859_1)
        assertFalse(bytes.contains(expected.accessToken))
        assertFalse(bytes.contains(expected.refreshToken))
    }

    @Test fun renewalReplacesTheStoredSessionAndUsesAFreshIv() = runBlocking {
        val store = manager()
        store.saveSession(session())
        val first = File(directory, "session.enc").readBytes()
        store.saveSession(session("two"))
        val second = File(directory, "session.enc").readBytes()
        assertFalse(first.copyOfRange(1, 13).contentEquals(second.copyOfRange(1, 13)))
        assertEquals(session("two").refreshToken, manager().loadSession().refreshToken)
    }

    @Test fun deletingTheSessionPreventsRestoration() = runBlocking {
        manager().saveSession(session())
        manager().deleteSession()
        assertFalse(File(directory, "session.enc").exists())
        assertTrue(runCatching { manager().loadSession() }.exceptionOrNull() is NoSessionFoundException)
    }

    @Test fun alteredCiphertextIsRejected() = runBlocking {
        manager().saveSession(session())
        val file = File(directory, "session.enc")
        val bytes = file.readBytes()
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        file.writeBytes(bytes)
        val error = runCatching { manager().loadSession() }.exceptionOrNull()
        assertNotNull(error)
        assertFalse(error!!.message.orEmpty().contains("synthetic-access"))
    }

    @Test fun actualSdkRestoresStorageAndClearSessionRemovesItWithoutNetwork() = runBlocking {
        val expected = session()
        manager().saveSession(expected)
        fun client() = createSupabaseClient("https://example.invalid", "synthetic-key") {
            defaultLogLevel = LogLevel.NONE
            install(Auth) {
                sessionManager = manager()
                autoLoadFromStorage = true
                autoSaveToStorage = true
                alwaysAutoRefresh = false // No HTTP: solo comprobar el almacenamiento con el SDK real.
                enableLifecycleCallbacks = false
            }
        }
        val first = client()
        try {
            withTimeout(10_000) { first.auth.awaitInitialization() }
            assertEquals(expected, first.auth.currentSessionOrNull())
            first.auth.clearSession()
            assertTrue(first.auth.sessionStatus.value is SessionStatus.NotAuthenticated)
        } finally { first.close() }
        val reopened = client()
        try {
            withTimeout(10_000) { reopened.auth.awaitInitialization() }
            assertNull(reopened.auth.currentSessionOrNull())
            assertTrue(reopened.auth.sessionStatus.value is SessionStatus.NotAuthenticated)
        } finally { reopened.close() }
    }
}
