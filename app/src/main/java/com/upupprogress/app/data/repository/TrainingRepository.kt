package com.upupprogress.app.data.repository

import com.upupprogress.app.data.model.*
import com.upupprogress.app.data.remote.SupabaseClientProvider
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.*
import java.math.BigDecimal
import java.time.LocalDate

interface TrainingGateway {
    fun currentUserId(): String?
    suspend fun loadRoutines(): List<RoutinePlan>
    suspend fun loadCatalog(): List<ExerciseCatalogItem>
    suspend fun loadSessions(date: LocalDate, routines: List<RoutinePlan>): List<WorkoutSession>
    suspend fun saveRoutine(draft: RoutineDraft): RoutinePlan
    suspend fun replaceRoutine(originalId: String, draft: RoutineDraft): RoutinePlan
    suspend fun archiveRoutine(id: String)
    suspend fun chooseRoutine(routine: RoutinePlan, date: LocalDate, requestId: String, replaceId: String?): WorkoutSession
    suspend fun saveSet(exerciseId: String, number: Int, weight: BigDecimal, reps: Int): RecordedTrainingSet
    suspend fun completeExercise(exerciseId: String): List<RecordedTrainingSet>
    suspend fun setStatus(exerciseId: String, status: String)
}

private const val ROUTINE_COLUMNS = "id,user_id,name,description,routine_exercises(id,exercise_id,position,planned_sets,planned_reps,exercises(name))"
private const val SESSION_COLUMNS = "id,user_id,routine_id,performed_on,routines(id,name,description,routine_exercises(id,exercise_id,position,planned_sets,planned_reps,exercises(name))),workout_exercises(id,exercise_id,position,completion_status,exercises(name),performed_sets(id,set_number,weight_kg,reps))"

class TrainingRepository internal constructor(
    private val client: SupabaseClient,
    private val actorId: () -> String?,
) : TrainingGateway {
    constructor() : this(SupabaseClientProvider.client, { SupabaseClientProvider.client.auth.currentUserOrNull()?.id })
    override fun currentUserId(): String? = actorId()
    private fun actor() = checkNotNull(currentUserId()) { "Inicia sesión para continuar." }
    private suspend fun ensureActor(expected: String) {
        currentCoroutineContext().ensureActive()
        check(actor() == expected) { "La sesión cambió." }
    }

    override suspend fun loadRoutines(): List<RoutinePlan> {
        val owner = actor()
        val rows = mutableListOf<JsonObject>()
        var offset = 0L
        do {
            val page = client.from("routines").select(Columns.raw(ROUTINE_COLUMNS)) {
                filter { eq("user_id", owner); eq("is_archived", false) }
                order("name", Order.ASCENDING); order("id", Order.ASCENDING)
                range(offset, offset + 199)
            }.decodeList<JsonObject>()
            ensureActor(owner)
            check(page.all { it.value("user_id") == owner })
            rows.addAll(page); offset += 200
        } while (page.size == 200)
        return rows.map(::routine)
    }

    override suspend fun loadCatalog(): List<ExerciseCatalogItem> {
        val owner = actor()
        val result = mutableListOf<ExerciseCatalogItem>()
        var offset = 0L
        do {
            // RLS solo permite catálogo común y ejercicios propios.
            val page = client.from("exercises").select(Columns.raw("id,name,created_by")) {
                order("name", Order.ASCENDING); order("id", Order.ASCENDING)
                range(offset, offset + 499)
            }.decodeList<JsonObject>()
            ensureActor(owner)
            check(page.all { it.value("created_by") == null || it.value("created_by") == owner })
            result.addAll(page.map { ExerciseCatalogItem(it.required("id"), it.required("name")) })
            offset += 500
        } while (page.size == 500)
        return result
    }

    override suspend fun loadSessions(date: LocalDate, routines: List<RoutinePlan>): List<WorkoutSession> {
        val owner = actor()
        val rows = mutableListOf<JsonObject>()
        var offset = 0L
        do {
            val page = client.from("workout_sessions").select(Columns.raw(SESSION_COLUMNS)) {
                filter { eq("user_id", owner); eq("performed_on", date.toString()) }
                order("created_at", Order.DESCENDING); order("id", Order.DESCENDING)
                range(offset, offset + 199)
            }.decodeList<JsonObject>()
            ensureActor(owner)
            check(page.all { it.value("user_id") == owner && it.value("performed_on") == date.toString() })
            rows.addAll(page); offset += 200
        } while (page.size == 200)
        return rows.map { session(it, routines) }
    }

    override suspend fun saveRoutine(draft: RoutineDraft): RoutinePlan {
        require(draft.isValid)
        val owner = actor()
        val id = client.postgrest.rpc("save_training_routine", buildJsonObject {
            put("p_routine_id", draft.id); put("p_name", draft.name.trim())
            put("p_description", draft.description.trim())
            put("p_exercises", JsonArray(draft.exercises.map { item -> buildJsonObject {
                put("id", item.id); put("exercise_id", item.exerciseId)
                put("name", item.name.trim()); put("planned_sets", item.plannedSets); put("planned_reps", item.plannedReps)
            } }))
        }).decodeAs<String>()
        ensureActor(owner)
        check(id == draft.id)
        val row = client.from("routines").select(Columns.raw(ROUTINE_COLUMNS)) {
            filter { eq("id", id); eq("user_id", owner) }
        }.decodeSingle<JsonObject>()
        ensureActor(owner)
        return routine(row).also { check(it.id == draft.id && it.exercises.size == draft.exercises.size) }
    }

    override suspend fun replaceRoutine(originalId: String, draft: RoutineDraft): RoutinePlan {
        require(draft.isValid && originalId != draft.id)
        val owner = actor()
        val id = client.postgrest.rpc("replace_training_routine", buildJsonObject {
            put("p_original_routine_id", originalId); put("p_routine_id", draft.id)
            put("p_name", draft.name.trim()); put("p_description", draft.description.trim())
            put("p_exercises", JsonArray(draft.exercises.map { item -> buildJsonObject {
                put("id", item.id); put("exercise_id", item.exerciseId); put("name", item.name.trim())
                put("planned_sets", item.plannedSets); put("planned_reps", item.plannedReps)
            } }))
        }).decodeAs<String>()
        ensureActor(owner)
        check(id == draft.id)
        val row = client.from("routines").select(Columns.raw(ROUTINE_COLUMNS)) {
            filter { eq("id", id); eq("user_id", owner) }
        }.decodeSingle<JsonObject>()
        ensureActor(owner)
        return routine(row).also { check(it.id == draft.id && it.exercises.size == draft.exercises.size) }
    }

    override suspend fun archiveRoutine(id: String) {
        val owner = actor()
        val result = client.postgrest.rpc("archive_training_routine", buildJsonObject {
            put("p_routine_id", id)
        }).decodeAs<String>()
        ensureActor(owner)
        check(result == id)
    }

    override suspend fun chooseRoutine(
        routine: RoutinePlan, date: LocalDate, requestId: String, replaceId: String?,
    ): WorkoutSession {
        val owner = actor()
        val id = client.postgrest.rpc("select_training_routine", buildJsonObject {
            put("p_session_id", requestId); put("p_routine_id", routine.id)
            put("p_performed_on", date.toString()); put("p_replace_session_id", replaceId)
        }).decodeAs<String>()
        ensureActor(owner)
        check(id == requestId)
        val row = client.from("workout_sessions").select(Columns.raw(SESSION_COLUMNS)) {
            filter { eq("id", requestId); eq("user_id", owner) }
        }.decodeSingle<JsonObject>()
        ensureActor(owner)
        return session(row, listOf(routine)).also {
            check(it.id == requestId && it.routineId == routine.id && it.performedOn == date.toString())
        }
    }

    override suspend fun saveSet(exerciseId: String, number: Int, weight: BigDecimal, reps: Int): RecordedTrainingSet {
        require(number in 1..TrainingInputLimits.MAX_SETS && reps in 0..TrainingInputLimits.MAX_REPS)
        require(weight >= BigDecimal.ZERO && weight <= BigDecimal("99999.99") && weight.scale() <= 2)
        val owner = actor()
        val row = client.postgrest.rpc("save_training_set", buildJsonObject {
            put("p_workout_exercise_id", exerciseId); put("p_set_number", number)
            put("p_weight_kg", JsonUnquotedLiteral(weight.toPlainString())); put("p_reps", reps)
        }).decodeAs<JsonObject>()
        ensureActor(owner)
        return recordedSet(row).also { check(it.number == number && it.reps == reps && it.weightKg.toBigDecimal().compareTo(weight) == 0) }
    }

    override suspend fun completeExercise(exerciseId: String): List<RecordedTrainingSet> {
        val owner = actor()
        val row = client.postgrest.rpc("complete_training_exercise", buildJsonObject {
            put("p_workout_exercise_id", exerciseId)
        }).decodeAs<JsonObject>()
        ensureActor(owner)
        check(row.required("id") == exerciseId && row.required("completion_status") == "completed")
        return row.objects("performed_sets").map(::recordedSet).sortedBy { it.number }
    }

    override suspend fun setStatus(exerciseId: String, status: String) {
        require(status in setOf("pending", "completed", "skipped"))
        val owner = actor()
        val id = client.postgrest.rpc("set_training_exercise_status", buildJsonObject {
            put("p_workout_exercise_id", exerciseId); put("p_status", status)
        }).decodeAs<String>()
        ensureActor(owner)
        check(id == exerciseId)
    }

    private fun routine(row: JsonObject): RoutinePlan = RoutinePlan(
        row.required("id"), row.required("name"), row.value("description"),
        row.objects("routine_exercises").sortedBy { it.required("position").toInt() }.map {
            PlannedExercise(it.required("id"), it.required("exercise_id"), it.child("exercises")?.value("name") ?: "Ejercicio",
                it.required("planned_sets").toInt(), it.required("planned_reps").toInt())
        },
    )

    private fun session(row: JsonObject, routines: List<RoutinePlan>): WorkoutSession {
        // Una versión retirada del listado sigue siendo la plantilla de su sesión histórica.
        val storedPlan = row.child("routines")?.takeIf { it.value("id") != null && it["routine_exercises"] is JsonArray }
        val plan = storedPlan?.let(::routine) ?: routines.firstOrNull { it.id == row.value("routine_id") }
        return WorkoutSession(
            row.required("id"), row.required("performed_on"), row.value("routine_id"),
            row.child("routines")?.value("name") ?: "Rutina registrada",
            row.objects("workout_exercises").sortedBy { it.required("position").toInt() }.map { item ->
                val position = item.required("position").toInt()
                val sets = item.objects("performed_sets").map(::recordedSet).sortedBy { it.number }
                val planned = plan?.exercises?.getOrNull(position - 1)?.takeIf { it.exerciseId == item.value("exercise_id") }
                WorkoutExercise(item.required("id"), item.required("exercise_id"),
                    item.child("exercises")?.value("name") ?: "Ejercicio",
                    position, planned?.plannedSets ?: (sets.maxOfOrNull { it.number } ?: 0),
                    planned?.plannedReps ?: 0, item.value("completion_status") ?: "pending", sets)
            },
            routineDescription = row.child("routines")?.value("description") ?: plan?.description,
        )
    }

    private fun recordedSet(row: JsonObject) = RecordedTrainingSet(
        row.required("id"), row.required("set_number").toInt(), row.required("weight_kg"), row.required("reps").toInt(),
    )
}

private fun JsonObject.value(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull
private fun JsonObject.required(key: String): String = requireNotNull(value(key)) { "Respuesta de entrenamiento incompleta." }
private fun JsonObject.objects(key: String) = (get(key) as? JsonArray)?.map { it.jsonObject }.orEmpty()
private fun JsonObject.child(key: String): JsonObject? = when (val child = get(key)) {
    is JsonObject -> child
    is JsonArray -> child.firstOrNull() as? JsonObject
    else -> null
}
