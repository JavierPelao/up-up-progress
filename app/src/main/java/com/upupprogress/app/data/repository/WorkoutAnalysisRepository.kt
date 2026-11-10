package com.upupprogress.app.data.repository

import com.upupprogress.app.data.model.WorkoutAnalysis
import com.upupprogress.app.data.remote.SupabaseClientProvider
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.functions.functions
import io.ktor.client.plugins.timeout
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

interface WorkoutAnalysisGateway {
    suspend fun analyze(): WorkoutAnalysis
}

class WorkoutAnalysisException(message: String) : Exception(message)

class WorkoutAnalysisRepository internal constructor(
    private val client: SupabaseClient,
    private val userId: () -> String?,
) : WorkoutAnalysisGateway {
    constructor() : this(
        SupabaseClientProvider.client,
        { SupabaseClientProvider.client.auth.currentUserOrNull()?.id },
    )

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun analyze(): WorkoutAnalysis {
        val actor = userId() ?: throw WorkoutAnalysisException("Inicia sesión para solicitar un análisis.")
        // El backend toma el historial del usuario identificado por el JWT.
        // Una sola solicitud por pulsación: no reenviar ante errores o respuestas perdidas.
        val response = try {
            client.functions.invoke("analyze-workouts") {
                timeout { requestTimeoutMillis = 120_000L }
            }
        } catch (error: RestException) {
            throw WorkoutAnalysisException(errorMessage(error))
        }
        currentCoroutineContext().ensureActive()
        if (userId() != actor) throw WorkoutAnalysisException("La sesión cambió. Vuelve a iniciar sesión.")
        val analysis = json.decodeFromString<WorkoutAnalysis>(response.bodyAsText())
        check(analysis.ok)
        check(analysis.status in setOf("analyzed", "no_data"))
        check(analysis.summary.isNotBlank())
        check(analysis.suggestions.all { it.isNotBlank() })
        check(analysis.status != "analyzed" || analysis.suggestions.isNotEmpty())
        check(analysis.quota.limit > 0)
        check(analysis.quota.remaining == null || analysis.quota.remaining in 0..analysis.quota.limit)
        return analysis
    }

    private fun errorMessage(error: RestException): String {
        val code = runCatching {
            json.parseToJsonElement(error.error).jsonObject["code"]?.jsonPrimitive?.contentOrNull
        }.getOrNull()
        return when (code) {
            "DAILY_LIMIT_REACHED" -> "Alcanzaste el máximo de 10 análisis diarios. El límite se renueva a las 00:00 UTC."
            "GROQ_RATE_LIMIT" -> "El proveedor de IA alcanzó su límite de solicitudes. Inténtalo más tarde."
            "AUTH_REQUIRED", "INVALID_SESSION" -> "Tu sesión expiró. Vuelve a iniciar sesión."
            "CONFIG_ERROR" -> "El servicio de IA todavía no está configurado en el backend."
            "QUOTA_ERROR" -> "No se pudo comprobar el límite diario de análisis."
            "FOOD_HISTORY_TOO_LARGE" -> "El historial de alimentos supera el máximo que puede analizar el servicio."
            "USER_CONTEXT_ERROR", "FOOD_QUERY_ERROR", "WORKOUT_QUERY_ERROR" ->
                "El backend no pudo consultar tus registros para el análisis."
            "AI_PROVIDER_ERROR", "AI_EMPTY_RESPONSE", "AI_INVALID_RESPONSE", "AI_COMMUNICATION_ERROR" ->
                "La IA no pudo entregar una respuesta válida. El intento puede haber consumido una solicitud."
            else -> when (error.statusCode) {
                401, 403 -> "No se pudo autorizar el análisis. Vuelve a iniciar sesión."
                404 -> "La función de análisis no está disponible."
                429 -> "Se alcanzó un límite de solicitudes. Inténtalo más tarde."
                else -> "No se pudo completar el análisis. El intento puede haber consumido una solicitud."
            }
        }
    }
}
