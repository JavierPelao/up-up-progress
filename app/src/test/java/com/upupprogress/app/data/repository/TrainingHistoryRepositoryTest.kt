package com.upupprogress.app.data.repository

import com.upupprogress.app.data.model.TrainingPeriod
import com.upupprogress.app.domain.training.TrainingProgressCalculator
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.logging.LogLevel
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

/** SDK y serialización reales, HTTP simulado: no lee usuarios ni registros de la nube. */
class TrainingHistoryRepositoryTest {
    private val from = LocalDate.of(2026, 11, 2)
    private val through = LocalDate.of(2026, 11, 29)
    private val headers = headersOf(HttpHeaders.ContentType, "application/json")

    private fun row(
        index: Int, user: String = "owner", exercise: String = "squat",
        date: String = "2026-11-05", status: String = "pending", reps: Int = 8,
        weight: String = "60", sessionId: String = "session-$index",
    ): JsonObject = buildJsonObject {
        put("id", "set-${index.toString().padStart(5, '0')}")
        put("workout_exercise_id", "we-$index")
        put("set_number", 1); put("weight_kg", BigDecimal(weight)); put("reps", reps)
        put("workout_exercises", buildJsonObject {
            put("id", "we-$index"); put("session_id", sessionId); put("exercise_id", exercise)
            put("completion_status", status)
            put("exercises", buildJsonObject { put("name", "Sentadilla") })
            put("workout_sessions", buildJsonObject {
                put("id", sessionId); put("user_id", user); put("routine_id", JsonNull)
                put("performed_on", date)
            })
        })
    }

    private fun client(engine: MockEngine): SupabaseClient =
        createSupabaseClient("https://example.invalid", "synthetic-key") {
            httpEngine = engine; osInformation = null; defaultLogLevel = LogLevel.NONE
            install(Postgrest) { requireValidSession = false }
        }

    @Test fun paginatedReadScopesOwnerExerciseAndSessionDateAndFeedsTheCalculator() = runTest {
        val offsets = mutableListOf<Int>()
        val rows = (0..1000).map { row(it, weight = if (it == 1000) "100.01" else "60") }
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/rest/v1/performed_sets", request.url.encodedPath)
            val params = request.url.parameters
            assertEquals("eq.squat", params["workout_exercises.exercise_id"])
            assertEquals("eq.owner", params["workout_exercises.workout_sessions.user_id"])
            assertEquals("(performed_on.gte.$from,performed_on.lte.$through)",
                params["workout_exercises.workout_sessions.and"])
            assertTrue(params["select"]!!.contains("workout_exercises!inner"))
            assertTrue(params["select"]!!.contains("workout_sessions!inner"))
            assertTrue(params["select"]!!.contains("set_number"))
            assertFalse(params["select"]!!.contains("created_at"))
            assertTrue(params["order"]!!.startsWith("id.asc"))
            assertFalse(params.entries().any { it.value.contains("eq.completed") })
            val offset = params["offset"]?.toInt()
                ?: request.headers[HttpHeaders.Range]?.substringAfter('=')?.substringBefore('-')?.toInt() ?: 0
            offsets.add(offset)
            respond(JsonArray(rows.drop(offset).take(500)).toString(), HttpStatusCode.OK, headers)
        }
        val client = client(engine)
        try {
            val history = TrainingRepository(client) { "owner" }.loadExerciseHistory("squat", from, through)
            assertEquals(listOf(0, 500, 1000, 1001), offsets)
            assertEquals(1001, history.size)
            assertTrue(history.all { it.userId == "owner" && it.exerciseId == "squat" && it.completionStatus == "pending" })
            val last = history.last()
            assertEquals("set-01000", last.set.id)
            assertEquals("session-1000", last.sessionId)
            assertEquals("we-1000", last.workoutExerciseId)
            assertEquals(LocalDate.of(2026, 11, 5), last.performedOn)
            assertNull(last.routineId)
            val result = TrainingProgressCalculator.calculate(history, "owner", "squat", from, through, TrainingPeriod.WEEK)
            assertEquals(1001, result.points.first().frequency)
            assertEquals(last, result.points.first().maximumLoad!!.source)
        } finally { client.close() }
    }

    @Test fun shorterServerPageDoesNotTruncateHistoryAndZeroRepsArePreservedForCalculation() = runTest {
        var calls = 0
        val engine = MockEngine {
            val page = when (calls++) {
                0 -> listOf(row(1, status = "skipped", reps = 5))
                1 -> listOf(row(2, status = "completed", reps = 0, weight = "0"))
                else -> emptyList()
            }
            respond(JsonArray(page).toString(), HttpStatusCode.OK, headers)
        }
        val client = client(engine)
        try {
            val history = TrainingRepository(client) { "owner" }.loadExerciseHistory("squat", from, through)
            assertEquals(3, calls)
            assertEquals(listOf(5, 0), history.map { it.set.reps })
            val result = TrainingProgressCalculator.calculate(history, "owner", "squat", from, through, TrainingPeriod.WEEK)
            assertEquals(1, result.points.first().frequency)
            assertEquals("set-00001", result.points.first().maximumLoad!!.source.set.id)
        } finally { client.close() }
    }

    @Test fun unexpectedOwnerExerciseOrDateIsRejectedInsteadOfMixingHistories() = runTest {
        val invalidRows = listOf(
            row(1, user = "other"), row(1, exercise = "other"), row(1, date = "2026-12-01"),
        )
        for (invalid in invalidRows) {
            val client = client(MockEngine { respond(JsonArray(listOf(invalid)).toString(), HttpStatusCode.OK, headers) })
            try {
                val failure = runCatching {
                    TrainingRepository(client) { "owner" }.loadExerciseHistory("squat", from, through)
                }.exceptionOrNull()
                assertTrue(failure is IllegalStateException)
            } finally { client.close() }
        }
    }

    @Test fun accountChangeDuringReadDoesNotExposePreviousAccountHistory() = runTest {
        var owner = "owner"
        val client = client(MockEngine {
            owner = "other"
            respond(JsonArray(listOf(row(1))).toString(), HttpStatusCode.OK, headers)
        })
        try {
            val failure = runCatching {
                TrainingRepository(client) { owner }.loadExerciseHistory("squat", from, through)
            }.exceptionOrNull()
            assertTrue(failure is IllegalStateException)
        } finally { client.close() }
    }

    @Test fun failedLaterPageDoesNotReturnAPartialHistoryAsACompleteResult() = runTest {
        var calls = 0
        val client = client(MockEngine {
            if (calls++ == 0) respond(JsonArray(listOf(row(1))).toString(), HttpStatusCode.OK, headers)
            else respond("""{"message":"synthetic failure"}""", HttpStatusCode.InternalServerError, headers)
        })
        try {
            val result = runCatching {
                TrainingRepository(client) { "owner" }.loadExerciseHistory("squat", from, through)
            }
            assertTrue(result.isFailure)
            assertEquals(2, calls)
        } finally { client.close() }
    }

    @Test fun noAuthenticationOrInvalidRangeSendsNoRequest() = runTest {
        var calls = 0
        val client = client(MockEngine {
            calls++
            respond("[]", HttpStatusCode.OK, headers)
        })
        try {
            assertTrue(runCatching {
                TrainingRepository(client) { null }.loadExerciseHistory("squat", from, through)
            }.isFailure)
            assertTrue(runCatching {
                TrainingRepository(client) { "owner" }.loadExerciseHistory("squat", through, from)
            }.isFailure)
            assertTrue(runCatching {
                TrainingRepository(client) { "owner" }.loadExerciseHistory("", from, through)
            }.isFailure)
            assertEquals(0, calls)
        } finally { client.close() }
    }
}
