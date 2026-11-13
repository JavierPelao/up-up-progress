package com.upupprogress.app

import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.upupprogress.app.data.model.*
import com.upupprogress.app.data.remote.SupabaseClientProvider
import com.upupprogress.app.data.repository.TrainingRepository
import com.upupprogress.app.domain.training.TrainingProgressCalculator
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.logging.LogLevel
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URI
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Prueba real, opt-in y de solo lectura. No importa tokens ni crea/edita registros.
 * Ejecutar con verifyTrainingHistory=true y una cuenta de prueba con historial existente.
 * Si el proceso arranca sin sesión, iniciar sesión manualmente en la app (espera hasta 5 minutos).
 */
@RunWith(AndroidJUnit4::class)
class TrainingHistoryAuthenticatedTest {
    private lateinit var client: SupabaseClient
    private lateinit var owner: String
    private var sessionLabels: Map<String, Int> = emptyMap()
    private val tag = "TrainingProgressCheck"

    @Test fun verifyAuthenticatedHistoryWithoutChangingData() = runBlocking {
        assumeTrue("Prueba real desactivada por defecto",
            InstrumentationRegistry.getArguments().getString("verifyTrainingHistory") == "true")
        val activity = ActivityScenario.launch(MainActivity::class.java)
        try {
            client = SupabaseClientProvider.client
            report("INICIO: solo lectura. Si aparece el login, iniciar sesión en la app; no enviar credenciales al test.")
            report("Destino REST: ${URI(BuildConfig.SUPABASE_URL).host}")
            owner = withTimeout(300_000L) {
                while (client.auth.currentUserOrNull() == null) delay(1_000)
                checkNotNull(client.auth.currentUserOrNull()).id
            }
            report("Sesión autenticada disponible; usuario y tokens no se imprimen.")

            // Sin filtro user_id: la separación entre cuentas debe imponerla RLS.
            val sessions = readVisible("workout_sessions", "id,user_id,routine_id,performed_on")
            check(sessions.all { it.text("user_id") == owner }) { "RLS devolvió una sesión ajena" }
            check(sessions.isNotEmpty()) { "Sin sesiones visibles: comprobación funcional inconclusa" }
            val sessionsById = sessions.associateBy { it.text("id") }
            sessionLabels = sessions.sortedWith(compareBy<JsonObject> { it.text("performed_on") }.thenBy { it.text("id") })
                .mapIndexed { index, session -> session.text("id") to index + 1 }.toMap()

            val exercises = readVisible("workout_exercises",
                "id,session_id,exercise_id,completion_status,exercises(name)")
            check(exercises.all { it.text("session_id") in sessionsById }) { "RLS devolvió un ejercicio de otra sesión" }
            val exercisesById = exercises.associateBy { it.text("id") }
            val sets = readVisible("performed_sets", "id,workout_exercise_id,set_number,weight_kg,reps")
            check(sets.all { it.text("workout_exercise_id") in exercisesById }) { "RLS devolvió una serie de otra sesión" }
            check(sets.isNotEmpty()) { "Sin series visibles: se necesita una cuenta de prueba con registros existentes" }
            check(sets.map { it.text("id") }.distinct().size == sets.size) { "Paginación duplicó series" }

            // Referencia independiente: unir localmente tres lecturas planas, sin utilizar la consulta nueva.
            val baseline = sets.map { set ->
                val exercise = exercisesById.getValue(set.text("workout_exercise_id"))
                val session = sessionsById.getValue(exercise.text("session_id"))
                ExerciseSetRecord(
                    owner, session.text("id"), session.optionalText("routine_id"),
                    LocalDate.parse(session.text("performed_on")), exercise.text("id"), exercise.text("exercise_id"),
                    (exercise["exercises"] as? JsonObject)?.optionalText("name") ?: "Ejercicio",
                    exercise.optionalText("completion_status") ?: "pending",
                    RecordedTrainingSet(set.text("id"), set.text("set_number").toInt(),
                        set.text("weight_kg"), set.text("reps").toInt()),
                )
            }
            val from = sessions.minOf { LocalDate.parse(it.text("performed_on")) }
            val through = sessions.maxOf { LocalDate.parse(it.text("performed_on")) }
            val large = TrainingRepository(client, { client.auth.currentUserOrNull()?.id })
            val small = TrainingRepository(client, { client.auth.currentUserOrNull()?.id }, historyPageSize = 2)
            report("RLS sin filtros de propietario: ${sessions.size} sesiones, ${exercises.size} ejercicios realizados, ${sets.size} series propias.")
            report("Rango real consultado: $from a $through. pending con reps > 0: " +
                baseline.count { it.completionStatus == "pending" && it.set.reps > 0 })
            var paginatedExerciseCount = 0
            for ((exerciseId, expected) in baseline.groupBy { it.exerciseId }) {
                val history = large.loadExerciseHistory(exerciseId, from, through)
                val splitHistory = small.loadExerciseHistory(exerciseId, from, through)
                check(canonical(expected) == canonical(history)) {
                    "El historial no coincide con la referencia de tablas"
                }
                check(canonical(history) == canonical(splitHistory)) {
                    "Páginas de 2 y 500 no devolvieron el mismo historial"
                }
                if (history.size > 2) paginatedExerciseCount++
                report("EJERCICIO ${history.first().exerciseName}: ${history.size} series, " +
                    "${history.map { it.sessionId }.distinct().size} sesiones; mismos datos con páginas 2 y 500.")
                history.sortedWith(compareBy<ExerciseSetRecord> { it.performedOn }.thenBy { it.set.number })
                    .take(8).forEach { report("  Serie real: ${source(it)} [${it.completionStatus}]") }
                verifyMetrics(history, expected, exerciseId, from, through)
            }
            check(paginatedExerciseCount > 0) {
                "Datos insuficientes para verificar varias páginas: ningún ejercicio tiene más de 2 series"
            }
            verifyAnonymousReadIsDenied()
            check(client.auth.currentUserOrNull()?.id == owner) { "La cuenta cambió durante la prueba" }
            report("PASS_REAL: datos, fechas, origen, RLS, métricas semanales/mensuales y paginación verificados. " +
                "$paginatedExerciseCount ejercicios con varias páginas. Sin escrituras ni llamadas a IA.")
        } catch (_: TimeoutCancellationException) {
            throw AssertionError("No se inició sesión en la app durante los 5 minutos de espera. " +
                "Repite la prueba e inicia sesión mientras está ejecutándose; no basta hacerlo antes del reinicio.")
        } catch (error: RestException) {
            // No imprimir headers, JWT, correos ni el texto completo de la excepción del SDK.
            throw AssertionError("Consulta real rechazada: HTTP ${error.statusCode}; ${error.error.take(200)}")
        } finally {
            activity.close()
        }
    }

    private suspend fun readVisible(table: String, columns: String): List<JsonObject> {
        val all = mutableListOf<JsonObject>()
        var offset = 0L
        var pages = 0
        do {
            val page = client.from(table).select(Columns.raw(columns)) {
                order("id", Order.ASCENDING)
                range(offset, offset + 199)
            }.decodeList<JsonObject>()
            check(client.auth.currentUserOrNull()?.id == owner) { "La sesión cambió" }
            all.addAll(page)
            offset += page.size
            pages++
        } while (page.isNotEmpty())
        report("GET $table: ${all.size} filas, $pages páginas incluida la vacía final.")
        return all
    }

    private fun verifyMetrics(
        actual: List<ExerciseSetRecord>, baseline: List<ExerciseSetRecord>, exerciseId: String,
        from: LocalDate, through: LocalDate,
    ) {
        val weights = baseline.filter { it.set.reps > 0 }.map { BigDecimal(it.set.weightKg) }.toSortedSet()
        for (period in TrainingPeriod.entries) {
            // Probar todas las cargas históricas, no solo una carga elegida automáticamente.
            val selectedWeights: List<BigDecimal?> = weights.toList().ifEmpty { listOf(null) }
            for (weight in selectedWeights) {
                val result = TrainingProgressCalculator.calculate(actual, owner, exerciseId, from, through, period, weight)
                assertEquals(weights.toList(), result.availableWeightsKg)
                val expectedStarts = mutableListOf<LocalDate>()
                var start = if (period == TrainingPeriod.WEEK)
                    from.minusDays((from.dayOfWeek.value - 1).toLong()) else from.withDayOfMonth(1)
                while (start <= through) {
                    expectedStarts.add(start)
                    start = if (period == TrainingPeriod.WEEK) start.plusDays(7) else start.plusMonths(1)
                }
                assertEquals(expectedStarts, result.points.map { it.periodStart })
                for (point in result.points) {
                    val effective = baseline.filter {
                        it.performedOn >= point.periodStart && it.performedOn <= point.periodEndInclusive &&
                            it.performedOn in from..through && it.set.reps > 0
                    }
                    check(effective.map { it.sessionId }.toSet() == point.sessionIds) {
                        "Las sesiones de frecuencia no coinciden con la referencia"
                    }
                    val maximum = effective.maxOfOrNull { BigDecimal(it.set.weightKg) }
                    assertEquals(maximum?.stripTrailingZeros(), point.maximumLoad?.value?.stripTrailingZeros())
                    val oneRep = effective.mapNotNull { independentOneRepMax(it) }.maxOrNull()
                    if (oneRep == null) assertNull(point.estimatedOneRepMax)
                    else assertEquals(oneRep, point.estimatedOneRepMax!!.value.toDouble(), 1e-7)
                    val reps = effective.filter {
                        weight != null && BigDecimal(it.set.weightKg).compareTo(weight) == 0
                    }.maxOfOrNull { it.set.reps }
                    assertEquals(reps, point.repetitionsAtWeight?.value)
                    listOfNotNull(point.maximumLoad?.source, point.estimatedOneRepMax?.source,
                        point.repetitionsAtWeight?.source).forEach { selected ->
                        check(canonical(listOf(selected)).single() in canonical(effective)) {
                            "El punto no corresponde a una serie original"
                        }
                    }
                    point.estimatedOneRepMax?.let {
                        assertEquals(independentOneRepMax(it.source)!!, it.value.toDouble(), 1e-7)
                    }
                    point.maximumLoad?.let {
                        assertEquals(BigDecimal(it.source.set.weightKg).stripTrailingZeros(), it.value.stripTrailingZeros())
                    }
                    point.repetitionsAtWeight?.let { assertEquals(it.source.set.reps, it.value) }
                    report("$period ${point.periodStart}: carga=${point.maximumLoad?.value ?: "sin datos"} " +
                        "1RM=${point.estimatedOneRepMax?.value?.setScale(2, RoundingMode.HALF_UP) ?: "sin datos"} " +
                        "reps@${weight ?: "sin carga"}=${reps ?: "sin datos"} frecuencia=${point.frequency}")
                }
            }
        }
    }

    private fun independentOneRepMax(record: ExerciseSetRecord): Double? {
        val kg = record.set.weightKg.toDouble()
        val reps = record.set.reps
        return when {
            kg <= 0 || reps !in 1..12 -> null
            reps == 1 -> kg
            else -> kg * (1.0 + reps / 30.0)
        }
    }

    private suspend fun verifyAnonymousReadIsDenied() {
        val anonymous = createSupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_PUBLIC_KEY) {
            osInformation = null
            defaultLogLevel = LogLevel.NONE
            install(Postgrest) { requireValidSession = false }
        }
        try {
            for (table in listOf("workout_sessions", "workout_exercises", "performed_sets")) {
                try {
                    val visible = anonymous.from(table).select(Columns.raw("id")) { limit(1) }.decodeList<JsonObject>()
                    check(visible.isEmpty()) { "Se devolvieron filas personales sin sesión autenticada" }
                } catch (error: RestException) {
                    check(error.statusCode in setOf(401, 403)) { "Prueba anónima falló por otra causa HTTP" }
                }
            }
            report("Sin sesión: ninguna de las tres tablas expuso filas personales.")
        } finally { anonymous.close() }
    }

    private fun canonical(records: List<ExerciseSetRecord>) = records.map {
        it.copy(set = it.set.copy(weightKg = BigDecimal(it.set.weightKg).stripTrailingZeros().toPlainString()))
    }.sortedBy { it.set.id }

    private fun source(record: ExerciseSetRecord) =
        "${record.performedOn}, sesión ${sessionLabels[record.sessionId]}, serie ${record.set.number}: " +
            "${record.set.weightKg} kg x ${record.set.reps} reps"

    private fun JsonObject.text(key: String): String = checkNotNull(optionalText(key)) { "Falta $key" }
    private fun JsonObject.optionalText(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull
    private fun report(text: String) { Log.i(tag, text) }
}
