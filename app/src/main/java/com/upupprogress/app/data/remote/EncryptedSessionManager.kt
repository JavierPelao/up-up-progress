package com.upupprogress.app.data.remote

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.exception.NoSessionFoundException
import io.github.jan.supabase.auth.user.UserSession
import java.io.File
import java.io.IOException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/** Tokens cifrados con una clave no exportable; nunca guarda la contraseña ni registra credenciales. */
internal class EncryptedSessionManager internal constructor(
    directory: File,
    private val keyAlias: String,
) : SessionManager {
    constructor(context: Context, namespace: String) : this(
        File(context.noBackupFilesDir, "auth-$namespace"),
        "com.upupprogress.session.$namespace",
    )

    private val directory = directory
    private val file = AtomicFile(File(directory, "session.enc"))
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun saveSession(session: UserSession) = withContext(Dispatchers.IO) {
        mutex.withLock {
            check(directory.exists() || directory.mkdirs()) { "No se pudo preparar el almacenamiento de sesión." }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val encrypted = cipher.doFinal(json.encodeToString(UserSession.serializer(), session).toByteArray(Charsets.UTF_8))
            val bytes = byteArrayOf(1) + cipher.iv + encrypted
            var stream: java.io.FileOutputStream? = null
            try {
                stream = file.startWrite()
                stream.write(bytes)
                file.finishWrite(stream)
            } catch (_: Exception) {
                file.failWrite(stream)
                throw IOException("No se pudo guardar la sesión protegida.")
            }
        }
    }

    override suspend fun loadSession(): UserSession = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!file.baseFile.exists()) throw NoSessionFoundException()
            try {
                val bytes = file.readFully()
                require(bytes.size >= 29 && bytes[0] == 1.toByte())
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(1, 13)))
                val clear = cipher.doFinal(bytes.copyOfRange(13, bytes.size)).toString(Charsets.UTF_8)
                json.decodeFromString(UserSession.serializer(), clear)
            } catch (_: Exception) {
                // No adjuntar excepciones del serializador que puedan contener el JSON de la sesión.
                throw IOException("No se pudo recuperar la sesión protegida.")
            }
        }
    }

    override suspend fun deleteSession() = withContext(Dispatchers.IO) {
        mutex.withLock { file.delete() }
    }

    private fun key(): SecretKey = synchronized(keyLock) {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(keyAlias, null) as? SecretKey) ?: KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                init(KeyGenParameterSpec.Builder(keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256).build())
            }.generateKey()
    }

    private companion object { val keyLock = Any() }
}
