package com.upupprogress.app.data.repository

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.logging.LogLevel
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class RecordedExercisesRepositoryTest {
    private val through = LocalDate.of(2026, 11, 12)
    private val headers = headersOf(HttpHeaders.ContentType, "application/json")
    private fun row(index: Int, id: String, name: String, date: String, owner: String = "owner") =
        """{"id":"we-$index","session_id":"session-$index","exercise_id":"$id",
            "exercises":{"name":"$name"},"workout_sessions":{"id":"session-$index","user_id":"$owner","performed_on":"$date"},
            "performed_sets":[{"id":"set-$index"}]}"""

    @Test fun selectorReadsOnlyRecordedExercisesAndKeepsEarliestDateAcrossPages() = runTest {
        val rows = listOf(
            row(1, "squat", "Sentadilla", "2026-11-10"),
            row(2, "press", "Press", "2026-11-09"),
            row(3, "squat", "Sentadilla", "2026-10-01"),
        )
        var calls = 0
        val client = createSupabaseClient("https://example.invalid", "synthetic-key") {
            httpEngine = MockEngine { request ->
                assertEquals(HttpMethod.Get, request.method)
                assertEquals("/rest/v1/workout_exercises", request.url.encodedPath)
                assertEquals("eq.owner", request.url.parameters["workout_sessions.user_id"])
                assertEquals("lte.$through", request.url.parameters["workout_sessions.performed_on"])
                assertTrue(request.url.parameters["select"]!!.contains("performed_sets!inner(id)"))
                assertTrue(request.url.parameters["select"]!!.contains("workout_sessions!inner"))
                assertFalse(request.url.parameters.entries().any { it.value.contains("eq.completed") })
                val body = rows.getOrNull(calls++)?.let { "[$it]" } ?: "[]"
                respond(body, HttpStatusCode.OK, headers)
            }
            osInformation = null; defaultLogLevel = LogLevel.NONE
            install(Postgrest) { requireValidSession = false }
        }
        try {
            val exercises = TrainingRepository(client) { "owner" }.loadRecordedExercises(through)
            assertEquals(4, calls)
            assertEquals(listOf("press", "squat"), exercises.map { it.id })
            assertEquals(LocalDate.of(2026, 10, 1), exercises.last().firstPerformedOn)
        } finally { client.close() }
    }

    @Test fun foreignOwnerAndFutureSessionAreNotAcceptedByTheSelector() = runTest {
        for (fixture in listOf(
            row(1, "squat", "Sentadilla", "2026-11-01", owner = "other"),
            row(1, "squat", "Sentadilla", "2026-12-01"),
        )) {
            val client = createSupabaseClient("https://example.invalid", "synthetic-key") {
                httpEngine = MockEngine { respond("[$fixture]", HttpStatusCode.OK, headers) }
                osInformation = null; defaultLogLevel = LogLevel.NONE
                install(Postgrest) { requireValidSession = false }
            }
            try {
                assertTrue(runCatching { TrainingRepository(client) { "owner" }.loadRecordedExercises(through) }.isFailure)
            } finally { client.close() }
        }
    }
}
