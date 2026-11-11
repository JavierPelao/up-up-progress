package com.upupprogress.app.data.repository

import com.upupprogress.app.data.model.*
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.logging.LogLevel
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import java.time.LocalDate
import org.junit.Test
import org.junit.Assert.*

/** SDK real, transporte en memoria; no accede a Supabase. */
class TrainingRepositoryTest {
    @Test
    fun rpcAndReadsUseTheAuthenticatedOwnerStableIdsAndNumericZero() = runTest {
        val user = "00000000-0000-0000-0000-000000000001"
        val routineId = "00000000-0000-0000-0000-000000000002"
        val exerciseId = "00000000-0000-0000-0000-000000000003"
        val itemId = "00000000-0000-0000-0000-000000000004"
        val sessionId = "00000000-0000-0000-0000-000000000005"
        val workoutExerciseId = "00000000-0000-0000-0000-000000000006"
        val date = LocalDate.of(2026,11,10)
        val routine = """{"id":"$routineId","user_id":"$user","name":"Ejemplo","description":null,
            "routine_exercises":[{"id":"$itemId","exercise_id":"$exerciseId","position":1,"planned_sets":1,"planned_reps":10,"exercises":{"name":"Flexiones"}}]}"""
        val session = """{"id":"$sessionId","user_id":"$user","routine_id":"$routineId","performed_on":"$date","routines":{"name":"Ejemplo"},
            "workout_exercises":[{"id":"$workoutExerciseId","exercise_id":"$exerciseId","position":1,"completion_status":"pending",
            "exercises":{"name":"Flexiones"},"performed_sets":[]}]}"""
        val paths = mutableListOf<String>()
        val engine = MockEngine { request ->
            paths.add(request.url.encodedPath)
            val headers = headersOf(HttpHeaders.ContentType,"application/json")
            when (request.url.encodedPath) {
                "/rest/v1/rpc/save_training_routine" -> {
                    assertEquals(HttpMethod.Post,request.method)
                    val body = Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
                    assertEquals(routineId,body["p_routine_id"]!!.jsonPrimitive.content)
                    assertFalse(body.containsKey("user_id"))
                    assertFalse(body["p_exercises"]!!.jsonArray.single().jsonObject.containsKey("weight_kg"))
                    respond("\"$routineId\"",HttpStatusCode.OK,headers)
                }
                "/rest/v1/routines" -> {
                    assertEquals("eq.$user",request.url.parameters["user_id"])
                    respond("[$routine]",HttpStatusCode.OK,headers)
                }
                "/rest/v1/rpc/select_training_routine" -> {
                    val body = Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
                    assertEquals(sessionId,body["p_session_id"]!!.jsonPrimitive.content)
                    assertTrue(body["p_replace_session_id"] is JsonNull)
                    respond("\"$sessionId\"",HttpStatusCode.OK,headers)
                }
                "/rest/v1/workout_sessions" -> {
                    assertEquals("eq.$user",request.url.parameters["user_id"])
                    respond("[$session]",HttpStatusCode.OK,headers)
                }
                "/rest/v1/rpc/save_training_set" -> {
                    val body = Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
                    assertEquals("0",body["p_weight_kg"]!!.jsonPrimitive.content)
                    assertFalse(body["p_weight_kg"]!!.jsonPrimitive.isString)
                    assertEquals(0, body["p_reps"]!!.jsonPrimitive.int)
                    respond("""{"id":"set","set_number":1,"weight_kg":0,"reps":0}""",HttpStatusCode.OK,headers)
                }
                "/rest/v1/rpc/complete_training_exercise" -> {
                    val body = Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
                    assertEquals(workoutExerciseId, body["p_workout_exercise_id"]!!.jsonPrimitive.content)
                    assertFalse(body.containsKey("user_id"))
                    respond("""{"id":"$workoutExerciseId","completion_status":"completed","performed_sets":[{"id":"set","set_number":1,"weight_kg":0,"reps":0}]}""",
                        HttpStatusCode.OK, headers)
                }
                else -> error("Unexpected simulated request")
            }
        }
        val client = createSupabaseClient("https://example.invalid","synthetic-key") {
            httpEngine = engine; osInformation = null; defaultLogLevel = LogLevel.NONE
            install(Postgrest) { requireValidSession = false }
        }
        try {
            val repo = TrainingRepository(client) { user }
            val saved = repo.saveRoutine(RoutineDraft(routineId,"Ejemplo","",
                listOf(PlannedExercise(itemId,exerciseId,"Flexiones",1,10))))
            assertEquals(1,saved.exercises.size)
            val chosen = repo.chooseRoutine(saved,date,sessionId,null)
            assertEquals(workoutExerciseId,chosen.exercises.single().id)
            assertEquals(1,chosen.exercises.single().plannedSets)
            val recorded = repo.saveSet(workoutExerciseId,1,parseTrainingWeight("")!!,0)
            assertEquals("0",recorded.weightKg)
            assertEquals(0, recorded.reps)
            assertEquals(listOf(recorded), repo.completeExercise(workoutExerciseId))
            assertEquals(6,paths.size)
        } finally { client.close() }
    }

    @Test fun editArchiveAndHistoricalTemplateUseTheCorrectRpcAndVersion() = runTest {
        val day = LocalDate.of(2026,11,11)
        val newRow = """{"id":"new","user_id":"owner","name":"Editada","description":null,
            "routine_exercises":[{"id":"new-item","exercise_id":"exercise","position":1,
            "planned_sets":1,"planned_reps":15,"exercises":{"name":"Flexiones"}}]}"""
        val oldPlan = """{"id":"old","name":"Original","description":"Descripción original",
            "routine_exercises":[{"id":"old-item","exercise_id":"exercise","position":1,
            "planned_sets":2,"planned_reps":10,"exercises":{"name":"Flexiones"}}]}"""
        var archived = false
        var replaced = false
        val engine = MockEngine { request ->
            val headers = headersOf(HttpHeaders.ContentType,"application/json")
            when (request.url.encodedPath) {
                "/rest/v1/routines" -> {
                    assertEquals("eq.owner",request.url.parameters["user_id"])
                    if (request.url.parameters["id"] == null) {
                        assertEquals("eq.false",request.url.parameters["is_archived"])
                    }
                    respond("[$newRow]",HttpStatusCode.OK,headers)
                }
                "/rest/v1/rpc/replace_training_routine" -> {
                    val body = Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
                    assertEquals("old",body["p_original_routine_id"]!!.jsonPrimitive.content)
                    assertEquals("new",body["p_routine_id"]!!.jsonPrimitive.content)
                    assertEquals(15,body["p_exercises"]!!.jsonArray.single().jsonObject["planned_reps"]!!.jsonPrimitive.int)
                    replaced = true
                    respond("\"new\"",HttpStatusCode.OK,headers)
                }
                "/rest/v1/rpc/archive_training_routine" -> {
                    val body = Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
                    assertEquals("new",body["p_routine_id"]!!.jsonPrimitive.content)
                    archived = true
                    respond("\"new\"",HttpStatusCode.OK,headers)
                }
                "/rest/v1/workout_sessions" -> {
                    assertEquals("eq.owner",request.url.parameters["user_id"])
                    assertEquals("eq.$day",request.url.parameters["performed_on"])
                    assertTrue(request.url.parameters["select"]!!.contains("routines(id,name,description,routine_exercises"))
                    respond("""[{"id":"session","user_id":"owner","routine_id":"old","performed_on":"$day",
                        "routines":$oldPlan,"workout_exercises":[{"id":"we","exercise_id":"exercise","position":1,
                        "completion_status":"pending","exercises":{"name":"Flexiones"},"performed_sets":[]}]}]""",
                        HttpStatusCode.OK,headers)
                }
                else -> error("Unexpected simulated request")
            }
        }
        val client = createSupabaseClient("https://example.invalid","synthetic-key") {
            httpEngine = engine; osInformation = null; defaultLogLevel = LogLevel.NONE
            install(Postgrest) { requireValidSession = false }
        }
        try {
            val repo = TrainingRepository(client) { "owner" }
            val active = repo.loadRoutines()
            val updated = repo.replaceRoutine("old",RoutineDraft("new","Editada","",
                listOf(PlannedExercise("new-item","exercise","Flexiones",1,15))))
            assertEquals("new",updated.id)
            assertTrue(replaced)
            repo.archiveRoutine(updated.id)
            assertTrue(archived)
            val history = repo.loadSessions(day,active).single()
            assertEquals("Original",history.routineName)
            assertEquals("Descripción original",history.routineDescription)
            assertEquals(2,history.exercises.single().plannedSets)
            assertEquals(10,history.exercises.single().plannedReps)
        } finally { client.close() }
    }
}
