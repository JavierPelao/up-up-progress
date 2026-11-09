package com.upupprogress.app.data.repository

import com.upupprogress.app.data.model.OnboardingStatus
import com.upupprogress.app.data.model.OnboardingSubmission
import com.upupprogress.app.data.remote.SupabaseClientProvider
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.serialization.json.*

interface OnboardingGateway {
    suspend fun loadStatus(): OnboardingStatus
    suspend fun complete(submission: OnboardingSubmission): OnboardingStatus
}

class OnboardingRepository : OnboardingGateway {
    override suspend fun loadStatus(): OnboardingStatus = request(buildJsonObject {
        put("action", "onboarding_status")
    })

    override suspend fun complete(submission: OnboardingSubmission): OnboardingStatus =
        request(submission.toRequest())

    private suspend fun request(body: JsonObject): OnboardingStatus {
        // Functions agrega el token de Auth; nunca se utilizan credenciales de PostgreSQL.
        val response = SupabaseClientProvider.client.functions.invoke(
            function = "nutrition-goals",
            body = body,
            headers = Headers.build { append(HttpHeaders.ContentType, "application/json") },
        )
        return OnboardingStatus.fromResponse(Json.parseToJsonElement(response.bodyAsText()).jsonObject)
    }
}
